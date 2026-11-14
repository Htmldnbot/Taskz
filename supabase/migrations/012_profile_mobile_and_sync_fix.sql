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

-- Safely migrate any existing profiles to canonical E.164 format BEFORE recreating constraint
alter table public.profiles drop constraint if exists profiles_mobile_check;

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

-- ============================================================
-- 3. ENSURE task_responses & task_acknowledgements INSERT GRANTS & POLICIES
-- ============================================================

grant select, insert on public.task_responses to authenticated;
grant select, insert on public.task_acknowledgements to authenticated;
grant select, insert, update on public.task_lan_status to authenticated;

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

commit;

-- Force PostgREST to reload updated functions and schema immediately
notify pgrst, 'reload schema';
