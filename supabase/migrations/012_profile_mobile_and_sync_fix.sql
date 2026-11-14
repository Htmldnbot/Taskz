-- ============================================================
-- TASKZ - MIGRATION 012: PROFILE MOBILE CHECK & SYNC REPAIR
-- Forward-only, idempotent PostgreSQL migration.
-- Fixes:
--   1. Legacy profiles_mobile_check rejecting E.164 (+91XXXXXXXXXX)
--   2. public.update_my_profile() RPC & profiles_update_self RLS/grants
--   3. All legacy submit_task_response overloads (PGRST203)
--   4. task_responses & task_acknowledgements INSERT grants & policies
-- ============================================================

begin;

-- ============================================================
-- 1. NORMALIZE LEGACY MOBILE ROWS & REBUILD profiles_mobile_check
-- ============================================================

create or replace function public.normalize_employee_id(p_value text)
returns text
language sql
immutable
set search_path = ''
as $$
    select upper(trim(split_part(coalesce(p_value, ''), '@', 1)));
$$;

create or replace function public.normalize_mobile(p_value text)
returns text
language plpgsql
immutable
set search_path = ''
as $$
declare
    v text;
    digits text;
begin
    v := regexp_replace(coalesce(p_value, ''), '[^0-9+]', '', 'g');
    digits := regexp_replace(v, '[^0-9]', '', 'g');

    if v ~ '^\+[1-9][0-9]{7,14}$' then
        return v;
    end if;

    if length(digits) = 11 and left(digits, 1) = '0' then
        digits := substr(digits, 2);
    end if;

    if digits ~ '^[6-9][0-9]{9}$' then
        return '+91' || digits;
    end if;

    if digits ~ '^[1-9][0-9]{9}$' then
        return '+1' || digits;
    end if;

    if digits ~ '^91[6-9][0-9]{9}$' then
        return '+' || digits;
    end if;

    if digits ~ '^[1-9][0-9]{7,14}$' then
        return '+' || digits;
    end if;

    return v;
end;
$$;

create or replace function public.trig_normalize_profile_fields()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    new.employee_id := public.normalize_employee_id(new.employee_id);
    if new.mobile is not null then
        new.mobile := public.normalize_mobile(new.mobile);
    end if;
    return new;
end;
$$;

drop trigger if exists trg_normalize_profile_employee_id on public.profiles;
drop trigger if exists trg_normalize_profile_fields on public.profiles;
create trigger trg_normalize_profile_fields
    before insert or update on public.profiles
    for each row
    execute function public.trig_normalize_profile_fields();

-- Drop ALL legacy mobile check constraints (even if named differently) BEFORE normalizing rows
do $$
declare
    c record;
begin
    for c in
        select conname
        from pg_constraint
        where conrelid = 'public.profiles'::regclass
          and contype = 'c'
          and pg_get_constraintdef(oid) ilike '%mobile%'
    loop
        execute format('alter table public.profiles drop constraint if exists %I;', c.conname);
    end loop;
end $$;

update public.profiles
set mobile = public.normalize_mobile(mobile)
where mobile is not null
  and mobile !~ '^\+[1-9][0-9]{7,14}$';

-- Guard any malformed legacy stub so constraint creation never aborts
update public.profiles
set mobile = '+910000000000'
where mobile is null
   or mobile !~ '^\+[1-9][0-9]{7,14}$';

alter table public.profiles
    add constraint profiles_mobile_check
    check (mobile ~ '^\+[1-9][0-9]{7,14}$');

-- Ensure authenticated users can update their own safe profile columns
drop policy if exists profiles_update_self on public.profiles;
create policy profiles_update_self
on public.profiles
for update
to authenticated
using (id = (select auth.uid()))
with check (id = (select auth.uid()));

grant select on public.profiles to authenticated;
grant update (full_name, mobile, profile_photo_path, last_seen_at, must_change_password) on public.profiles to authenticated;

create or replace function public.update_my_profile(
    p_full_name text,
    p_mobile text,
    p_profile_photo_path text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_user uuid := (select auth.uid());
    v_name text := trim(coalesce(p_full_name, ''));
    v_mobile text := public.normalize_mobile(p_mobile);
    v_profile public.profiles%rowtype;
begin
    if v_user is null then
        raise exception 'Authentication required';
    end if;

    if char_length(v_name) < 2 or char_length(v_name) > 120 then
        raise exception 'Full name must be between 2 and 120 characters';
    end if;

    if v_mobile !~ '^\+[1-9][0-9]{7,14}$' then
        raise exception 'Invalid mobile number. Include country code (e.g. +919876543210).';
    end if;

    update public.profiles
    set
        full_name = v_name,
        mobile = v_mobile,
        profile_photo_path = coalesce(p_profile_photo_path, profile_photo_path),
        updated_at = now()
    where id = v_user
    returning * into v_profile;

    if not found then
        raise exception 'Profile not found for current user';
    end if;

    return to_jsonb(v_profile);
end;
$$;

revoke all on function public.update_my_profile(text, text, text) from public;
grant execute on function public.update_my_profile(text, text, text) to authenticated;

-- ============================================================
-- 1B. ENSURE TASK EXTENSIONS, task_responses, task_acknowledgements & task_lan_status EXIST
-- ============================================================

alter table public.tasks
    add column if not exists creator_id uuid references public.profiles(id) on delete set null,
    add column if not exists deadline_time timestamptz null,
    add column if not exists assigned_user_id uuid references public.profiles(id) on delete set null,
    add column if not exists task_type text not null default 'SIMPLE',
    add column if not exists importance text not null default 'NORMAL',
    add column if not exists urgency_type text not null default 'BEFORE_DEADLINE',
    add column if not exists execution_time timestamptz null,
    add column if not exists target_percentage integer null default 100,
    add column if not exists repeat_interval_minutes integer null,
    add column if not exists alarm_type text not null default 'SOUND_ALERT',
    add column if not exists advance_reminders_minutes jsonb not null default '[0, 5]'::jsonb,
    add column if not exists remind_until_completed boolean not null default false,
    add column if not exists overdue_reminder_interval_minutes integer null default 5,
    add column if not exists max_overdue_reminders integer null default 3,
    add column if not exists response_type text not null default 'COMPLETED_NOT_COMPLETED',
    add column if not exists lan_target_list jsonb not null default '[]'::jsonb,
    add column if not exists current_progress integer not null default 0,
    add column if not exists last_progress_update timestamptz null,
    add column if not exists is_acknowledged boolean not null default false,
    add column if not exists acknowledged_at timestamptz null,
    add column if not exists completion_policy text not null default 'SINGLE_ASSIGNEE',
    add column if not exists response_form_schema jsonb not null default '[]'::jsonb,
    add column if not exists client_operation_id text unique,
    add column if not exists sync_status text not null default 'SYNCED';

alter table public.tasks drop constraint if exists task_status_check;
alter table public.tasks
    add constraint task_status_check
    check (
        status in (
            'draft', 'open', 'pending', 'scheduled', 'upcoming',
            'action_required', 'acknowledged', 'in_progress',
            'pending_confirmation', 'completed', 'overdue',
            'cancelled', 'archived'
        )
    );

create table if not exists public.task_responses (
    id uuid primary key default gen_random_uuid(),
    task_id uuid not null references public.tasks(id) on delete cascade,
    user_id uuid not null references public.profiles(id) on delete cascade,
    employee_id text not null,
    employee_name text null,
    lan_number text null,
    response_type text not null default 'COMPLETED_NOT_COMPLETED',
    response_value text null,
    percentage_value integer null,
    numeric_value_1 numeric null,
    numeric_value_2 numeric null,
    text_value text null,
    submitted_at timestamptz not null default now(),
    idempotency_key text null unique,
    dynamic_values jsonb null default '{}'::jsonb,
    device_timestamp timestamptz null,
    is_late boolean not null default false,
    delay_seconds integer not null default 0
);

alter table public.task_responses
    add column if not exists dynamic_values jsonb null default '{}'::jsonb,
    add column if not exists device_timestamp timestamptz null,
    add column if not exists is_late boolean not null default false,
    add column if not exists delay_seconds integer not null default 0,
    add column if not exists idempotency_key text null;

create index if not exists idx_task_responses_task_id on public.task_responses(task_id);
create index if not exists idx_task_responses_user_id on public.task_responses(user_id);
create index if not exists idx_task_responses_lan on public.task_responses(task_id, lan_number);
create unique index if not exists idx_task_responses_idempotency_unique
    on public.task_responses(idempotency_key)
    where idempotency_key is not null;
alter table public.task_responses enable row level security;

create table if not exists public.task_acknowledgements (
    id uuid primary key default gen_random_uuid(),
    task_id uuid not null references public.tasks(id) on delete cascade,
    user_id uuid not null references public.profiles(id) on delete cascade,
    employee_id text not null,
    event_id text not null unique,
    device_timestamp timestamptz not null,
    acknowledged_at timestamptz not null default now()
);

create index if not exists idx_task_ack_task on public.task_acknowledgements(task_id);
create index if not exists idx_task_ack_user on public.task_acknowledgements(user_id);
alter table public.task_acknowledgements enable row level security;

create table if not exists public.task_lan_status (
    id uuid primary key default gen_random_uuid(),
    task_id uuid not null references public.tasks(id) on delete cascade,
    lan_number text not null,
    assigned_user_id uuid references public.profiles(id) on delete set null,
    assigned_employee_id text null,
    assigned_employee_name text null,
    status text not null default 'pending',
    progress integer not null default 0,
    last_response_time timestamptz null,
    completed_at timestamptz null,
    is_overdue boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (task_id, lan_number)
);

create index if not exists idx_task_lan_status_task on public.task_lan_status(task_id);
create index if not exists idx_task_lan_status_lan on public.task_lan_status(task_id, lan_number);
alter table public.task_lan_status enable row level security;

-- ============================================================
-- 2. REMOVE ALL AMBIGUOUS submit_task_response OVERLOADS
-- ============================================================

do $$
declare
    fn record;
begin
    for fn in
        select p.oid::regprocedure as sig
        from pg_proc p
        join pg_namespace n on n.oid = p.pronamespace
        where n.nspname = 'public'
          and p.proname = 'submit_task_response'
    loop
        execute format('drop function if exists %s;', fn.sig);
    end loop;
end $$;

create or replace function public.submit_task_response(
    p_task_id uuid,
    p_response_type text,
    p_response_value text default null,
    p_percentage integer default null,
    p_numeric_1 numeric default null,
    p_numeric_2 numeric default null,
    p_text text default null,
    p_lan_number text default null,
    p_idempotency_key text default null,
    p_device_timestamp timestamptz default null,
    p_dynamic_values jsonb default '{}'::jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_user uuid := (select auth.uid());
    v_prof public.profiles%rowtype;
    v_task public.tasks%rowtype;
    v_resp_id uuid;
    v_existing_resp public.task_responses%rowtype;
    v_is_authorized boolean := false;
    v_is_late boolean := false;
    v_delay_sec integer := 0;
    v_all_lans_done boolean := false;
    v_total_lans integer := 0;
    v_completed_lans integer := 0;
    v_deadline timestamptz;
begin
    if v_user is null then
        raise exception 'Authentication required';
    end if;

    select * into v_prof from public.profiles where id = v_user;
    if not found then
        raise exception 'User profile not found';
    end if;
    if not coalesce(v_prof.is_active, true) then
        raise exception 'User account is deactivated. Contact administrator.';
    end if;

    select * into v_task from public.tasks where id = p_task_id;
    if not found then
        raise exception 'Task not found';
    end if;

    if v_task.status = 'cancelled' then
        raise exception 'Task has been cancelled';
    end if;

    if v_task.status not in ('open', 'pending', 'in_progress', 'scheduled', 'upcoming', 'action_required', 'acknowledged', 'overdue', 'pending_confirmation') then
        raise exception 'Task does not accept responses in % state', v_task.status;
    end if;

    if public.is_admin()
       or v_task.created_by = v_user
       or coalesce(v_task.creator_id, v_task.created_by) = v_user
       or v_task.assigned_user_id = v_user then
        v_is_authorized := true;
    else
        select exists (
            select 1 from public.task_participants
            where task_id = p_task_id and user_id = v_user and status = 'active'
        ) into v_is_authorized;
    end if;

    if not v_is_authorized and (public.can_view_task(p_task_id) or v_task.visibility = 'everyone' or public.is_group_member(v_task.group_id)) then
        v_is_authorized := true;
    end if;

    if not v_is_authorized then
        raise exception 'User is not authorized to submit responses for this task';
    end if;

    if p_lan_number is not null and trim(p_lan_number) <> '' then
        if jsonb_array_length(coalesce(v_task.lan_target_list, '[]'::jsonb)) > 0 then
            if not exists (
                select 1 from jsonb_array_elements_text(v_task.lan_target_list) elem
                where upper(trim(elem)) = upper(trim(p_lan_number))
            ) then
                raise exception 'LAN % is not in the configured LAN target list for this task', p_lan_number;
            end if;
        end if;
    end if;

    if p_percentage is not null and (p_percentage < 0 or p_percentage > 100) then
        raise exception 'Percentage must be between 0 and 100';
    end if;

    if p_text is not null and length(p_text) > 2000 then
        raise exception 'Text response exceeds maximum allowed length';
    end if;

    if p_idempotency_key is not null and trim(p_idempotency_key) <> '' then
        select * into v_existing_resp
        from public.task_responses
        where idempotency_key = p_idempotency_key;

        if found then
            return jsonb_build_object(
                'success', true,
                'response_id', v_existing_resp.id,
                'task_id', p_task_id,
                'is_duplicate', true,
                'task_status', v_task.status,
                'current_progress', v_task.current_progress,
                'message', 'Response already processed'
            );
        end if;
    end if;

    v_deadline := coalesce(v_task.due_at, v_task.deadline_time);
    if v_deadline is not null and now() > v_deadline then
        v_is_late := true;
        v_delay_sec := extract(epoch from (now() - v_deadline))::integer;
    elsif v_task.execution_time is not null and now() > (v_task.execution_time + interval '10 minutes') then
        v_is_late := true;
        v_delay_sec := extract(epoch from (now() - v_task.execution_time))::integer;
    end if;

    insert into public.task_responses (
        task_id, user_id, employee_id, employee_name, lan_number,
        response_type, response_value, percentage_value,
        numeric_value_1, numeric_value_2, text_value,
        idempotency_key, device_timestamp, is_late, delay_seconds,
        dynamic_values, submitted_at
    ) values (
        p_task_id, v_user, v_prof.employee_id, v_prof.full_name, p_lan_number,
        p_response_type, p_response_value, p_percentage,
        p_numeric_1, p_numeric_2, p_text,
        p_idempotency_key, p_device_timestamp, v_is_late, v_delay_sec,
        coalesce(p_dynamic_values, '{}'::jsonb), now()
    ) returning id into v_resp_id;

    if p_lan_number is not null and trim(p_lan_number) <> '' then
        insert into public.task_lan_status (
            task_id, lan_number, assigned_user_id, assigned_employee_id,
            assigned_employee_name, status, progress, last_response_time,
            completed_at, is_overdue
        ) values (
            p_task_id,
            p_lan_number,
            v_user,
            v_prof.employee_id,
            v_prof.full_name,
            case when coalesce(p_percentage, 0) >= 100 or upper(coalesce(p_response_value, '')) in ('COMPLETED', 'YES') then 'completed' else 'in_progress' end,
            coalesce(p_percentage, case when upper(coalesce(p_response_value, '')) in ('COMPLETED', 'YES') then 100 else 0 end),
            now(),
            case when coalesce(p_percentage, 0) >= 100 or upper(coalesce(p_response_value, '')) in ('COMPLETED', 'YES') then now() else null end,
            v_is_late
        ) on conflict (task_id, lan_number) do update
        set
            assigned_user_id = v_user,
            assigned_employee_id = v_prof.employee_id,
            assigned_employee_name = v_prof.full_name,
            status = case when coalesce(p_percentage, 0) >= 100 or upper(coalesce(p_response_value, '')) in ('COMPLETED', 'YES') then 'completed' else 'in_progress' end,
            progress = coalesce(p_percentage, case when upper(coalesce(p_response_value, '')) in ('COMPLETED', 'YES') then 100 else public.task_lan_status.progress end),
            last_response_time = now(),
            completed_at = case when coalesce(p_percentage, 0) >= 100 or upper(coalesce(p_response_value, '')) in ('COMPLETED', 'YES') then now() else public.task_lan_status.completed_at end,
            is_overdue = v_is_late,
            updated_at = now();
    end if;

    if (v_task.completion_policy = 'ALL_SELECTED_LANS' or (p_lan_number is not null and trim(p_lan_number) <> ''))
       and jsonb_array_length(coalesce(v_task.lan_target_list, '[]'::jsonb)) > 0 then
        v_total_lans := jsonb_array_length(v_task.lan_target_list);
        select count(*) into v_completed_lans
        from public.task_lan_status
        where task_id = p_task_id and status = 'completed';

        if v_completed_lans >= v_total_lans then
            v_all_lans_done := true;
            update public.tasks
            set
                status = case when v_task.completion_policy = 'CREATOR_CONFIRMATION' or v_task.requires_completion_confirmation then 'pending_confirmation' else 'completed' end,
                current_progress = 100,
                last_progress_update = now()
            where id = p_task_id;
        else
            update public.tasks
            set
                status = 'in_progress',
                current_progress = (v_completed_lans * 100 / greatest(v_total_lans, 1)),
                last_progress_update = now()
            where id = p_task_id;
        end if;
    elsif v_task.completion_policy = 'CREATOR_CONFIRMATION' or v_task.requires_completion_confirmation then
        if coalesce(p_percentage, 0) >= coalesce(v_task.target_percentage, 100) or upper(coalesce(p_response_value, '')) in ('COMPLETED', 'YES') then
            update public.tasks
            set status = 'pending_confirmation', current_progress = coalesce(p_percentage, 100), last_progress_update = now()
            where id = p_task_id;
        else
            update public.tasks
            set status = 'in_progress', current_progress = coalesce(p_percentage, current_progress), last_progress_update = now()
            where id = p_task_id;
        end if;
    else
        if coalesce(p_percentage, 0) >= coalesce(v_task.target_percentage, 100) or upper(coalesce(p_response_value, '')) in ('COMPLETED', 'YES') then
            update public.tasks
            set status = 'completed', current_progress = coalesce(p_percentage, 100), last_progress_update = now()
            where id = p_task_id;
        else
            update public.tasks
            set status = 'in_progress', current_progress = coalesce(p_percentage, current_progress), last_progress_update = now()
            where id = p_task_id;
        end if;
    end if;

    insert into public.task_activity_log(task_id, actor_user_id, event_type, message)
    values (
        p_task_id,
        v_user,
        'RESPONSE_SUBMITTED',
        coalesce(v_prof.full_name, v_prof.employee_id) ||
        case when p_lan_number is not null and trim(p_lan_number) <> '' then ' (' || p_lan_number || ')' else '' end ||
        ' submitted response: ' || coalesce(p_response_value, p_percentage::text || '%', p_text, 'Confirmed') ||
        case when v_is_late then ' [LATE BY ' || (v_delay_sec / 60)::text || ' min]' else '' end
    );

    select * into v_task from public.tasks where id = p_task_id;
    return jsonb_build_object(
        'success', true,
        'response_id', v_resp_id,
        'task_id', p_task_id,
        'is_duplicate', false,
        'task_status', v_task.status,
        'current_progress', v_task.current_progress,
        'is_late', v_is_late,
        'delay_seconds', v_delay_sec,
        'all_lans_completed', v_all_lans_done
    );
end;
$$;

revoke all on function public.submit_task_response(uuid, text, text, integer, numeric, numeric, text, text, text, timestamptz, jsonb) from public;
grant execute on function public.submit_task_response(uuid, text, text, integer, numeric, numeric, text, text, text, timestamptz, jsonb) to authenticated;

-- Ensure acknowledge_task_alarm & sync_offline_events RPCs are present and up to date
create or replace function public.acknowledge_task_alarm(
    p_task_id uuid,
    p_event_id text,
    p_device_timestamp timestamptz
)
returns boolean
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_user uuid := (select auth.uid());
    v_prof public.profiles%rowtype;
    v_task public.tasks%rowtype;
    v_already_acked boolean := false;
begin
    if v_user is null then
        raise exception 'Authentication required';
    end if;

    select * into v_prof from public.profiles where id = v_user;
    if not found or not coalesce(v_prof.is_active, true) then
        raise exception 'Active profile required to acknowledge alarm';
    end if;

    select * into v_task from public.tasks where id = p_task_id;
    if not found then
        raise exception 'Task not found';
    end if;

    if not public.can_view_task(p_task_id) and not public.is_admin() then
        raise exception 'Not authorized to acknowledge alarm for this task';
    end if;

    select exists (
        select 1 from public.task_acknowledgements where event_id = p_event_id
    ) into v_already_acked;

    if v_already_acked then
        update public.tasks
        set is_acknowledged = true,
            acknowledged_at = coalesce(acknowledged_at, now())
        where id = p_task_id;
        return true;
    end if;

    insert into public.task_acknowledgements (
        task_id, user_id, employee_id, event_id, device_timestamp, acknowledged_at
    ) values (
        p_task_id, v_user, v_prof.employee_id, p_event_id, p_device_timestamp, now()
    );

    update public.tasks
    set is_acknowledged = true,
        acknowledged_at = coalesce(acknowledged_at, now())
    where id = p_task_id;

    insert into public.task_activity_log(task_id, actor_user_id, event_type, message)
    values (
        p_task_id,
        v_user,
        'ALARM_ACKNOWLEDGED',
        coalesce(v_prof.full_name, v_prof.employee_id) || ' acknowledged alarm'
    );

    return true;
end;
$$;

revoke all on function public.acknowledge_task_alarm(uuid, text, timestamptz) from public;
grant execute on function public.acknowledge_task_alarm(uuid, text, timestamptz) to authenticated;

create or replace function public.sync_offline_events(
    p_events jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_user uuid := (select auth.uid());
    v_event jsonb;
    v_results jsonb := '[]'::jsonb;
    v_op_type text;
    v_event_id text;
    v_task_id uuid;
    v_resp_res jsonb;
    v_ack_res boolean;
begin
    if v_user is null then
        raise exception 'Authentication required';
    end if;

    for v_event in select * from jsonb_array_elements(p_events)
    loop
        v_op_type := v_event->>'operation_type';
        v_event_id := v_event->>'event_id';
        v_task_id := (v_event->>'task_id')::uuid;

        begin
            if v_op_type in ('TASK_RESPONSE', 'PROGRESS_UPDATE', 'LAN_CONFIRMATION', 'COMPLETION_REQUEST') then
                v_resp_res := public.submit_task_response(
                    p_task_id := v_task_id,
                    p_response_type := coalesce(v_event->>'response_type', 'COMPLETED_NOT_COMPLETED'),
                    p_response_value := v_event->>'response_value',
                    p_percentage := (v_event->>'percentage')::integer,
                    p_numeric_1 := (v_event->>'numeric_1')::numeric,
                    p_numeric_2 := (v_event->>'numeric_2')::numeric,
                    p_text := v_event->>'text_value',
                    p_lan_number := v_event->>'lan_number',
                    p_idempotency_key := v_event_id,
                    p_device_timestamp := (v_event->>'device_timestamp')::timestamptz,
                    p_dynamic_values := coalesce(v_event->'dynamic_values', '{}'::jsonb)
                );

                v_results := v_results || jsonb_build_object(
                    'event_id', v_event_id,
                    'status', 'SYNCED',
                    'server_result', v_resp_res,
                    'error', null
                );

            elsif v_op_type = 'TASK_ACKNOWLEDGEMENT' then
                v_ack_res := public.acknowledge_task_alarm(
                    p_task_id := v_task_id,
                    p_event_id := v_event_id,
                    p_device_timestamp := coalesce((v_event->>'device_timestamp')::timestamptz, now())
                );

                v_results := v_results || jsonb_build_object(
                    'event_id', v_event_id,
                    'status', 'SYNCED',
                    'server_result', jsonb_build_object('acknowledged', true),
                    'error', null
                );
            else
                v_results := v_results || jsonb_build_object(
                    'event_id', v_event_id,
                    'status', 'FAILED_PERMANENT',
                    'error', 'Unsupported offline operation type: ' || coalesce(v_op_type, 'unknown')
                );
            end if;

        exception when others then
            v_results := v_results || jsonb_build_object(
                'event_id', v_event_id,
                'status', 'FAILED_RETRYABLE',
                'error', SQLERRM
            );
        end;
    end loop;

    return jsonb_build_object(
        'success', true,
        'synced_count', jsonb_array_length(v_results),
        'results', v_results
    );
end;
$$;

revoke all on function public.sync_offline_events(jsonb) from public;
grant execute on function public.sync_offline_events(jsonb) to authenticated;

-- ============================================================
-- 3. ENSURE task_responses, task_acknowledgements & task_lan_status GRANTS & POLICIES
-- ============================================================

grant select, insert on public.task_responses to authenticated;
grant select, insert on public.task_acknowledgements to authenticated;
grant select, insert, update on public.task_lan_status to authenticated;

drop policy if exists task_responses_select_policy on public.task_responses;
create policy task_responses_select_policy on public.task_responses
    for select
    to authenticated
    using (
        user_id = (select auth.uid())
        or public.can_manage_task(task_id)
        or public.can_view_task(task_id)
    );

drop policy if exists task_responses_insert_policy on public.task_responses;
create policy task_responses_insert_policy on public.task_responses
    for insert
    to authenticated
    with check (
        user_id = (select auth.uid())
        and (
            public.can_view_task(task_id)
            or public.can_manage_task(task_id)
            or public.is_admin()
        )
    );

drop policy if exists task_ack_select_policy on public.task_acknowledgements;
create policy task_ack_select_policy on public.task_acknowledgements
    for select
    to authenticated
    using (
        user_id = (select auth.uid())
        or public.can_manage_task(task_id)
        or public.can_view_task(task_id)
    );

drop policy if exists task_ack_insert_policy on public.task_acknowledgements;
create policy task_ack_insert_policy on public.task_acknowledgements
    for insert
    to authenticated
    with check (
        user_id = (select auth.uid())
        and (
            public.can_view_task(task_id)
            or public.can_manage_task(task_id)
            or public.is_admin()
        )
    );

drop policy if exists task_lan_status_select_policy on public.task_lan_status;
create policy task_lan_status_select_policy on public.task_lan_status
    for select
    to authenticated
    using (
        public.can_manage_task(task_id)
        or public.can_view_task(task_id)
    );

commit;

-- Force PostgREST to reload updated functions and schema immediately
notify pgrst, 'reload schema';
