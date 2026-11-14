-- ============================================================
-- TASKZ - MIGRATION 011: COMPLETE AUTH, SYNC, RLS & RPC AUDIT FIX
-- Forward-only, idempotent PostgreSQL migration.
-- Preserves all existing tables, profiles, groups, shifts, tasks, and incidents.
-- ============================================================

begin;

-- ============================================================
-- 1. PROFILES: MOBILE NORMALIZATION TRIGGER & SAFE GOOGLE/EMAIL ONBOARDING
-- ============================================================

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

-- Safely create auto-profile ONLY when auth.users has explicit employee_id metadata.
-- Google Sign-In users without employee_id metadata are left without a fake "+910000000000" stub
-- so the Android app presents GoogleProfileCompletionDialog and calls public.complete_my_profile().
create or replace function public.handle_new_auth_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_employee text;
    v_name text;
    v_mobile text;
begin
    v_employee := public.normalize_employee_id(
        coalesce(
            new.raw_user_meta_data ->> 'employee_id',
            new.raw_user_meta_data ->> 'employeeId',
            ''
        )
    );

    -- Google OAuth / ID-token signups and bare auth accounts have no employee_id yet:
    -- Let the user complete onboarding via complete_my_profile().
    if v_employee = '' then
        return new;
    end if;

    v_name := trim(
        coalesce(
            new.raw_user_meta_data ->> 'full_name',
            new.raw_user_meta_data ->> 'fullName',
            new.raw_user_meta_data ->> 'name',
            ''
        )
    );
    if char_length(v_name) < 2 then
        raise exception 'Full name must be at least 2 characters';
    end if;

    v_mobile := public.normalize_mobile(
        coalesce(
            new.raw_user_meta_data ->> 'mobile',
            new.raw_user_meta_data ->> 'phone',
            ''
        )
    );
    if v_mobile !~ '^\+[1-9][0-9]{7,14}$' then
        raise exception 'Invalid mobile number format. Include country code (e.g. +919876543210).';
    end if;

    if exists (
        select 1
        from public.profiles p
        where lower(p.employee_id) = lower(v_employee)
          and p.id <> new.id
    ) then
        raise exception 'Employee ID % is already registered to another account', v_employee;
    end if;

    insert into public.profiles (
        id,
        employee_id,
        full_name,
        mobile,
        app_role,
        is_active
    )
    values (
        new.id,
        v_employee,
        v_name,
        v_mobile,
        'employee',
        true
    )
    on conflict (id) do update
    set
        employee_id = excluded.employee_id,
        full_name = excluded.full_name,
        mobile = excluded.mobile,
        updated_at = now();

    return new;
end;
$$;

-- Secure profile completion / upsert RPC for Google Sign-In and self-onboarding users.
-- Never accepts elevated app_role from client parameters; preserves any existing admin role.
create or replace function public.complete_my_profile(
    p_employee_id text,
    p_full_name text,
    p_mobile text
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_user uuid := auth.uid();
    v_emp text := public.normalize_employee_id(p_employee_id);
    v_name text := trim(coalesce(p_full_name, ''));
    v_mobile text := public.normalize_mobile(p_mobile);
    v_profile public.profiles%rowtype;
begin
    if v_user is null then
        raise exception 'Authentication required';
    end if;

    if v_emp !~ '^[A-Z0-9._-]{2,40}$' then
        raise exception 'Employee ID must be 2 to 40 letters, numbers, dots, underscores, or hyphens';
    end if;

    if char_length(v_name) < 2 or char_length(v_name) > 120 then
        raise exception 'Full name must be between 2 and 120 characters';
    end if;

    if v_mobile !~ '^\+[1-9][0-9]{7,14}$' then
        raise exception 'Invalid mobile number. Enter a valid number with country code (e.g. +919876543210)';
    end if;

    if exists (
        select 1
        from public.profiles p
        where lower(p.employee_id) = lower(v_emp)
          and p.id <> v_user
    ) then
        raise exception 'Employee ID % is already linked to another account', v_emp;
    end if;

    insert into public.profiles (
        id,
        employee_id,
        full_name,
        mobile,
        app_role,
        is_active
    )
    values (
        v_user,
        v_emp,
        v_name,
        v_mobile,
        'employee',
        true
    )
    on conflict (id) do update
    set
        employee_id = case
            when public.profiles.employee_id is null or public.profiles.employee_id = '' then excluded.employee_id
            else public.profiles.employee_id
        end,
        full_name = excluded.full_name,
        mobile = excluded.mobile,
        updated_at = now()
    returning * into v_profile;

    return to_jsonb(v_profile);
end;
$$;

revoke all on function public.complete_my_profile(text, text, text) from public;
grant execute on function public.complete_my_profile(text, text, text) to authenticated;

-- Allow first-time authenticated users without profile row to insert their own 'employee' profile if needed via REST
drop policy if exists profiles_insert_self on public.profiles;
create policy profiles_insert_self
on public.profiles
for insert
to authenticated
with check (
    id = (select auth.uid())
    and app_role = 'employee'
    and is_active = true
);
grant insert (id, employee_id, full_name, mobile, profile_photo_path, app_role, is_active) on public.profiles to authenticated;

-- ============================================================
-- 2. SEED CANONICAL WORK ROLES (MATCHES ANDROID UUIDS)
-- ============================================================

insert into public.work_roles (id, name, code, description, is_active, sort_order)
values
    ('11111111-1111-4111-8111-111111111101', 'Invigilator', 'INV', 'Examination hall monitor & invigilation', true, 1),
    ('11111111-1111-4111-8111-111111111102', 'IT Manager', 'ITM', 'Technical support and workstation maintenance', true, 2),
    ('11111111-1111-4111-8111-111111111103', 'Security Guard', 'SEC', 'Entry/exit security & perimeter monitoring', true, 3),
    ('11111111-1111-4111-8111-111111111104', 'Floor Supervisor', 'SUP', 'Operational floor oversight and coordination', true, 4),
    ('11111111-1111-4111-8111-111111111105', 'Event Coordinator', 'CRD', 'Event management and participant assistance', true, 5),
    ('11111111-1111-4111-8111-111111111106', 'VCO / Venue Head', 'VCO', 'Venue Commanding Officer / Venue Head', true, 6),
    ('11111111-1111-4111-8111-111111111107', 'Staff / Assignee', 'STAFF', 'General task assignee', true, 7)
on conflict (lower(name)) do update
set
    code = excluded.code,
    description = excluded.description,
    is_active = true;

-- ============================================================
-- 3. FIX RLS POLICIES FOR MULTI-USER TASK & RESPONSE VISIBILITY
-- ============================================================

-- Allow active users who can view/join an active group (not only admins/group creators) to create tasks in that group
drop policy if exists tasks_insert on public.tasks;
create policy tasks_insert
on public.tasks
for insert
to authenticated
with check (
    public.is_active_user()
    and created_by = (select auth.uid())
    and (
        public.can_manage_group(group_id)
        or public.is_group_member(group_id)
        or public.can_view_group(group_id)
    )
);

-- Ensure extended tasks columns & response tables exist before policies/functions reference them
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

-- Allow all users who can view a task (creator, assigned participant, group/everyone viewer, admin) to view responses
drop policy if exists task_responses_select_policy on public.task_responses;
create policy task_responses_select_policy on public.task_responses
    for select
    to authenticated
    using (
        user_id = (select auth.uid())
        or public.can_manage_task(task_id)
        or public.can_view_task(task_id)
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

drop policy if exists task_lan_status_select_policy on public.task_lan_status;
create policy task_lan_status_select_policy on public.task_lan_status
    for select
    to authenticated
    using (
        public.can_manage_task(task_id)
        or public.can_view_task(task_id)
    );

-- ============================================================
-- 4. FIX PARTIAL-INDEX ON CONFLICT BUGS IN ATOMIC TASK / ASSIGN / JOIN / COPY SHIFT
-- ============================================================

drop function if exists public.create_task_atomic(jsonb);
create or replace function public.create_task_atomic(p_task jsonb)
returns jsonb
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_caller uuid := auth.uid();
    v_client_op_id text;
    v_existing_task public.tasks%rowtype;
    v_task_id uuid;
    v_group_id uuid;
    v_shift_id uuid;
    v_title text;
    v_desc text;
    v_location text;
    v_starts_at timestamptz;
    v_due_at timestamptz;
    v_priority text;
    v_visibility text;
    v_allow_self_join boolean;
    v_join_requires_approval boolean;
    v_notify_creator_on_join boolean;
    v_requires_completion_confirmation boolean;
    v_max_participants integer;
    v_task_type text;
    v_importance text;
    v_urgency_type text;
    v_execution_time timestamptz;
    v_target_percentage integer;
    v_repeat_interval_minutes integer;
    v_alarm_type text;
    v_advance_reminders jsonb;
    v_remind_until_completed boolean;
    v_overdue_reminder_interval integer;
    v_max_overdue_reminders integer;
    v_response_type text;
    v_lan_target_list jsonb;
    v_completion_policy text;
    v_form_schema jsonb;
    v_assigned_user_id text;
    v_assigned_uuid uuid;
    v_req jsonb;
    v_role_id uuid;
    v_req_count integer;
    v_created_task public.tasks%rowtype;
    v_caller_name text;
    v_is_active_emp boolean;
begin
    if v_caller is null then
        raise exception 'Authentication required to create tasks';
    end if;

    if not public.is_active_user() then
        raise exception 'Active user account required to create tasks';
    end if;

    v_client_op_id := nullif(trim(p_task->>'client_operation_id'), '');
    if v_client_op_id is not null then
        select * into v_existing_task
        from public.tasks
        where client_operation_id = v_client_op_id;

        if found then
            return jsonb_build_object(
                'success', true,
                'is_duplicate', true,
                'task_id', v_existing_task.id,
                'task', to_jsonb(v_existing_task)
            );
        end if;
    end if;

    v_task_id := coalesce(nullif(p_task->>'id', '')::uuid, gen_random_uuid());
    v_group_id := nullif(p_task->>'group_id', '')::uuid;
    v_shift_id := nullif(p_task->>'shift_id', '')::uuid;
    v_title := trim(coalesce(p_task->>'title', ''));

    if length(v_title) < 1 then
        raise exception 'Task title cannot be blank';
    end if;

    -- Resolve valid group or shared General Operations ('GEN-OPS') station visible to all active users
    if v_group_id is null or not exists (select 1 from public.task_groups where id = v_group_id) then
        select id into v_group_id
        from public.task_groups
        where code = 'GEN-OPS' and visibility = 'everyone' and status = 'active'
        order by created_at asc
        limit 1;

        if v_group_id is null then
            select id into v_group_id
            from public.task_groups
            where created_by = v_caller and status = 'active'
            order by created_at desc
            limit 1;
        end if;

        if v_group_id is null then
            insert into public.task_groups(
                title, code, description, status, visibility, allow_self_join, join_requires_approval, created_by
            ) values (
                'General Operations', 'GEN-OPS', 'Default operational group', 'active', 'everyone', true, false, v_caller
            ) returning id into v_group_id;
        end if;
    end if;

    insert into public.group_members(group_id, user_id, source, status, added_by)
    values (v_group_id, v_caller, 'assigned', 'active', v_caller)
    on conflict (group_id, user_id) where status = 'active' do nothing;

    if v_shift_id is not null and not exists (select 1 from public.shifts where id = v_shift_id) then
        v_shift_id := null;
    end if;

    v_desc := nullif(trim(p_task->>'description'), '');
    v_location := nullif(trim(p_task->>'location'), '');
    v_starts_at := nullif(p_task->>'starts_at', '')::timestamptz;
    v_due_at := nullif(p_task->>'due_at', '')::timestamptz;
    v_priority := coalesce(nullif(lower(p_task->>'priority'), ''), 'normal');
    v_visibility := coalesce(nullif(lower(p_task->>'visibility'), ''), 'everyone');
    v_allow_self_join := coalesce((p_task->>'allow_self_join')::boolean, true);
    v_join_requires_approval := coalesce((p_task->>'join_requires_approval')::boolean, true);
    v_notify_creator_on_join := coalesce((p_task->>'notify_creator_on_join')::boolean, true);
    v_requires_completion_confirmation := coalesce((p_task->>'requires_completion_confirmation')::boolean, false);
    v_max_participants := nullif(p_task->>'max_participants', '')::integer;

    v_task_type := coalesce(nullif(upper(p_task->>'task_type'), ''), 'SIMPLE');
    v_importance := coalesce(nullif(upper(p_task->>'importance'), ''), 'NORMAL');
    v_urgency_type := coalesce(nullif(upper(p_task->>'urgency_type'), ''), 'BEFORE_DEADLINE');
    v_execution_time := nullif(p_task->>'execution_time', '')::timestamptz;
    v_target_percentage := coalesce(nullif(p_task->>'target_percentage', '')::integer, 100);
    v_repeat_interval_minutes := nullif(p_task->>'repeat_interval_minutes', '')::integer;
    v_alarm_type := coalesce(nullif(upper(p_task->>'alarm_type'), ''), 'SOUND_ALERT');
    v_advance_reminders := coalesce(p_task->'advance_reminders_minutes', '[0, 5]'::jsonb);
    v_remind_until_completed := coalesce((p_task->>'remind_until_completed')::boolean, false);
    v_overdue_reminder_interval := coalesce(nullif(p_task->>'overdue_reminder_interval_minutes', '')::integer, 5);
    v_max_overdue_reminders := coalesce(nullif(p_task->>'max_overdue_reminders', '')::integer, 3);
    v_response_type := coalesce(nullif(upper(p_task->>'response_type'), ''), 'COMPLETED_NOT_COMPLETED');
    v_lan_target_list := coalesce(p_task->'lan_target_list', '[]'::jsonb);
    v_completion_policy := coalesce(nullif(upper(p_task->>'completion_policy'), ''), 'SINGLE_ASSIGNEE');

    if p_task ? 'response_form_schema' and jsonb_typeof(p_task->'response_form_schema') = 'array' then
        v_form_schema := p_task->'response_form_schema';
    else
        v_form_schema := '[]'::jsonb;
    end if;

    insert into public.tasks(
        id, group_id, shift_id, title, description, location, starts_at, due_at,
        priority, status, visibility, allow_self_join, join_requires_approval,
        notify_creator_on_join, requires_completion_confirmation, max_participants,
        created_by, creator_id, deadline_time, task_type, importance, urgency_type,
        execution_time, target_percentage, repeat_interval_minutes, alarm_type,
        advance_reminders_minutes, remind_until_completed, overdue_reminder_interval_minutes,
        max_overdue_reminders, response_type, lan_target_list, completion_policy,
        response_form_schema, client_operation_id, sync_status
    ) values (
        v_task_id, v_group_id, v_shift_id, v_title, v_desc, v_location, v_starts_at, v_due_at,
        v_priority, 'open', v_visibility, v_allow_self_join, v_join_requires_approval,
        v_notify_creator_on_join, v_requires_completion_confirmation, v_max_participants,
        v_caller, v_caller, v_due_at, v_task_type, v_importance, v_urgency_type,
        v_execution_time, v_target_percentage, v_repeat_interval_minutes, v_alarm_type,
        v_advance_reminders, v_remind_until_completed, v_overdue_reminder_interval,
        v_max_overdue_reminders, v_response_type, v_lan_target_list, v_completion_policy,
        v_form_schema, v_client_op_id, 'SYNCED'
    ) returning * into v_created_task;

    select full_name into v_caller_name from public.profiles where id = v_caller;

    -- FIXED: partial unique index predicate WHERE status = 'active'
    insert into public.task_participants(
        task_id, user_id, source, status, assigned_by
    ) values (
        v_task_id, v_caller, 'assigned', 'active', v_caller
    ) on conflict (task_id, user_id) where status = 'active' do nothing;

    if p_task ? 'assigned_user_ids' and jsonb_typeof(p_task->'assigned_user_ids') = 'array' then
        for v_assigned_user_id in select jsonb_array_elements_text(p_task->'assigned_user_ids')
        loop
            begin
                v_assigned_uuid := v_assigned_user_id::uuid;
            exception when others then
                raise exception 'Assigned employee identifier "%" is not a valid UUID', v_assigned_user_id;
            end;

            select is_active into v_is_active_emp from public.profiles where id = v_assigned_uuid;
            if v_is_active_emp is null then
                raise exception 'Assigned user "%" does not exist in profiles', v_assigned_user_id;
            end if;
            if not v_is_active_emp then
                raise exception 'Assigned user "%" is marked inactive in profiles', v_assigned_user_id;
            end if;

            -- FIXED: partial unique index predicate WHERE status = 'active'
            insert into public.task_participants(
                task_id, user_id, source, status, assigned_by
            ) values (
                v_task_id, v_assigned_uuid, 'assigned', 'active', v_caller
            ) on conflict (task_id, user_id) where status = 'active'
            do update set
                status = 'active',
                assigned_by = v_caller,
                joined_at = now();

            perform public.create_notification(
                v_assigned_uuid,
                'task_assigned',
                'New task assigned: ' || v_title,
                coalesce(v_caller_name, 'Supervisor') || ' assigned you to task "' || v_title || '"',
                v_task_id,
                v_group_id
            );

            if v_visibility = 'restricted' then
                insert into public.task_visible_users(task_id, user_id, added_by)
                values (v_task_id, v_assigned_uuid, v_caller)
                on conflict (task_id, user_id) do nothing;
            end if;
        end loop;
    end if;

    if p_task ? 'requirements' and jsonb_typeof(p_task->'requirements') = 'array' then
        for v_req in select * from jsonb_array_elements(p_task->'requirements')
        loop
            begin
                v_role_id := (v_req->>'role_id')::uuid;
            exception when others then
                raise exception 'Required role ID "%" is not a valid UUID', (v_req->>'role_id');
            end;

            if v_role_id is not null then
                if not exists (select 1 from public.work_roles where id = v_role_id) then
                    raise exception 'Required role "%" does not exist in work_roles', v_role_id;
                end if;

                v_req_count := coalesce((v_req->>'required_count')::integer, (v_req->>'count')::integer, 1);
                if v_req_count < 1 then
                    raise exception 'Required count for role "%" must be at least 1', v_role_id;
                end if;

                insert into public.task_role_requirements(task_id, role_id, required_count)
                values (v_task_id, v_role_id, v_req_count)
                on conflict (task_id, role_id)
                do update set required_count = v_req_count;
            end if;
        end loop;
    end if;

    insert into public.task_activity_log(task_id, actor_user_id, event_type, message)
    values (
        v_task_id,
        v_caller,
        'TASK_CREATED',
        'Task created by ' || coalesce(v_caller_name, 'Creator') || ' (' || v_task_type || ', ' || v_importance || ')'
    );

    return jsonb_build_object(
        'success', true,
        'task_id', v_task_id,
        'task', to_jsonb(v_created_task)
    );
end;
$$;

revoke all on function public.create_task_atomic(jsonb) from public;
grant execute on function public.create_task_atomic(jsonb) to authenticated;


drop function if exists public.assign_user_to_task(uuid, uuid, uuid);
create or replace function public.assign_user_to_task(
    p_task_id uuid,
    p_user_id uuid,
    p_role_id uuid default null
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_caller uuid := auth.uid();
    v_caller_name text;
    v_task public.tasks%rowtype;
    v_is_active boolean;
    v_role_name text := null;
begin
    if v_caller is null then
        raise exception 'Authentication required to assign users';
    end if;

    select * into v_task from public.tasks where id = p_task_id;
    if not found then
        raise exception 'Task % not found', p_task_id;
    end if;

    if not public.can_manage_task(p_task_id) then
        raise exception 'Not authorized to assign users to this task';
    end if;

    select is_active into v_is_active from public.profiles where id = p_user_id;
    if v_is_active is null then
        raise exception 'Target user % does not exist in profiles', p_user_id;
    end if;
    if not v_is_active then
        raise exception 'Target user % is inactive', p_user_id;
    end if;

    if p_role_id is not null then
        select name into v_role_name from public.work_roles where id = p_role_id;
        if v_role_name is null then
            raise exception 'Specified role % does not exist in work_roles', p_role_id;
        end if;
    end if;

    select full_name into v_caller_name from public.profiles where id = v_caller;

    -- FIXED: partial unique index predicate WHERE status = 'active'
    insert into public.task_participants(
        task_id, user_id, role_id, source, status, assigned_by, joined_at
    ) values (
        p_task_id, p_user_id, p_role_id, 'assigned', 'active', v_caller, now()
    ) on conflict (task_id, user_id) where status = 'active'
    do update set
        role_id = coalesce(excluded.role_id, public.task_participants.role_id),
        status = 'active',
        assigned_by = v_caller,
        left_at = null,
        left_reason = null;

    perform public.create_notification(
        p_user_id,
        'task_assigned',
        'Assigned to task: ' || v_task.title,
        coalesce(v_caller_name, 'Supervisor') || ' assigned you to task "' || v_task.title || '"',
        p_task_id,
        v_task.group_id
    );

    if v_task.visibility = 'restricted' then
        insert into public.task_visible_users(task_id, user_id, added_by)
        values (p_task_id, p_user_id, v_caller)
        on conflict (task_id, user_id) do nothing;
    end if;

    insert into public.task_activity_log(task_id, actor_user_id, target_user_id, event_type, message)
    values (
        p_task_id,
        v_caller,
        p_user_id,
        'USER_ASSIGNED',
        coalesce(v_caller_name, 'Supervisor') || ' assigned user to task'
    );

    return jsonb_build_object(
        'success', true,
        'task_id', p_task_id,
        'user_id', p_user_id,
        'role_id', p_role_id
    );
end;
$$;

revoke all on function public.assign_user_to_task(uuid, uuid, uuid) from public;
grant execute on function public.assign_user_to_task(uuid, uuid, uuid) to authenticated;


create or replace function public.request_task_join(
    p_task uuid,
    p_role uuid default null,
    p_message text default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user uuid := (select auth.uid());
    v_task public.tasks%rowtype;
    v_current integer;
begin
    if not public.is_active_user() then
        raise exception 'Account inactive';
    end if;

    select * into v_task from public.tasks where id = p_task;
    if not found then
        raise exception 'Task not found';
    end if;

    if not public.can_view_task(p_task) then
        raise exception 'Task not available';
    end if;

    if not v_task.allow_self_join then
        raise exception 'Self joining is not allowed';
    end if;

    if v_task.status not in ('open', 'in_progress', 'scheduled', 'upcoming') then
        raise exception 'Task is not open for joining';
    end if;

    if exists (
        select 1 from public.task_participants
        where task_id = p_task and user_id = v_user and status = 'active'
    ) then
        return jsonb_build_object('status', 'already_joined');
    end if;

    if v_task.max_participants is not null then
        select count(*) into v_current
        from public.task_participants
        where task_id = p_task and status = 'active';
        if v_current >= v_task.max_participants then
            raise exception 'Task is full';
        end if;
    end if;

    if exists (select 1 from public.task_role_requirements where task_id = p_task) then
        if p_role is null then
            raise exception 'Role required';
        end if;
        if not exists (
            select 1 from public.task_role_requirements
            where task_id = p_task and role_id = p_role
        ) then
            raise exception 'Invalid role';
        end if;
    end if;

    if v_task.join_requires_approval then
        insert into public.task_join_requests(task_id, user_id, role_id, message)
        values (p_task, v_user, p_role, nullif(trim(p_message), ''))
        on conflict (task_id, user_id) where status = 'pending'
        do update set
            role_id = coalesce(excluded.role_id, public.task_join_requests.role_id),
            message = coalesce(excluded.message, public.task_join_requests.message);

        insert into public.task_activity_log(task_id, actor_user_id, target_user_id, event_type, message)
        values (p_task, v_user, v_user, 'JOIN_REQUESTED', 'User requested to join task');

        perform public.create_notification(
            v_task.created_by,
            'task_join_request',
            'New task join request',
            'An employee wants to join your task.',
            p_task,
            v_task.group_id
        );

        return jsonb_build_object('status', 'pending_approval');
    else
        insert into public.task_participants(task_id, user_id, role_id, source, status, assigned_by)
        values (p_task, v_user, p_role, 'self_join', 'active', v_user)
        on conflict (task_id, user_id) where status = 'active' do nothing;

        insert into public.task_activity_log(task_id, actor_user_id, target_user_id, event_type, message)
        values (p_task, v_user, v_user, 'USER_JOINED', 'User joined task');

        if v_task.notify_creator_on_join then
            perform public.create_notification(
                v_task.created_by,
                'task_joined',
                'Employee joined task',
                'An employee has joined your task.',
                p_task,
                v_task.group_id
            );
        end if;

        return jsonb_build_object('status', 'joined');
    end if;
end;
$$;

revoke all on function public.request_task_join(uuid, uuid, text) from public;
grant execute on function public.request_task_join(uuid, uuid, text) to authenticated;


create or replace function public.review_task_join(
    p_request uuid,
    p_approve boolean
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_request public.task_join_requests%rowtype;
    v_task public.tasks%rowtype;
begin
    select * into v_request
    from public.task_join_requests
    where id = p_request and status = 'pending';

    if not found then
        raise exception 'Join request not found';
    end if;

    if not public.can_manage_task(v_request.task_id) then
        raise exception 'Unauthorized';
    end if;

    select * into v_task from public.tasks where id = v_request.task_id;

    if p_approve then
        insert into public.task_participants(task_id, user_id, role_id, source, status, assigned_by)
        values (v_request.task_id, v_request.user_id, v_request.role_id, 'approved_join', 'active', (select auth.uid()))
        on conflict (task_id, user_id) where status = 'active'
        do update set
            role_id = coalesce(excluded.role_id, public.task_participants.role_id),
            status = 'active',
            assigned_by = (select auth.uid());

        update public.task_join_requests
        set status = 'approved', reviewed_by = (select auth.uid()), reviewed_at = now()
        where id = p_request;

        insert into public.task_activity_log(task_id, actor_user_id, target_user_id, event_type, message)
        values (v_request.task_id, (select auth.uid()), v_request.user_id, 'JOIN_APPROVED', 'Task join request approved');

        perform public.create_notification(
            v_request.user_id,
            'task_join_approved',
            'Task join approved',
            'Your request to join the task was approved.',
            v_request.task_id,
            v_task.group_id
        );
    else
        update public.task_join_requests
        set status = 'rejected', reviewed_by = (select auth.uid()), reviewed_at = now()
        where id = p_request;

        insert into public.task_activity_log(task_id, actor_user_id, target_user_id, event_type, message)
        values (v_request.task_id, (select auth.uid()), v_request.user_id, 'JOIN_REJECTED', 'Task join request rejected');

        perform public.create_notification(
            v_request.user_id,
            'task_join_rejected',
            'Task join request declined',
            'Your request to join the task was declined.',
            v_request.task_id,
            v_task.group_id
        );
    end if;

    return true;
end;
$$;

revoke all on function public.review_task_join(uuid, boolean) from public;
grant execute on function public.review_task_join(uuid, boolean) to authenticated;


create or replace function public.copy_shift(
    p_source_shift uuid,
    p_new_name text,
    p_offset_minutes integer,
    p_copy_manpower boolean default true,
    p_copy_assignments boolean default false
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_source public.shifts%rowtype;
    v_new_shift uuid;
    v_old_task record;
    v_new_task uuid;
    v_caller uuid := (select auth.uid());
begin
    select * into v_source from public.shifts where id = p_source_shift;
    if not found then
        raise exception 'Source shift not found';
    end if;

    if not public.can_manage_group(v_source.group_id) then
        raise exception 'Unauthorized to manage shifts in this group';
    end if;

    insert into public.shifts(
        group_id, name, shift_number, starts_at, ends_at, notes, status, copied_from_shift_id, created_by
    ) values (
        v_source.group_id,
        trim(p_new_name),
        coalesce(v_source.shift_number, 0) + 1,
        v_source.starts_at + make_interval(mins => p_offset_minutes),
        v_source.ends_at + make_interval(mins => p_offset_minutes),
        'Copied from ' || v_source.name,
        'scheduled',
        v_source.id,
        v_caller
    ) returning id into v_new_shift;

    for v_old_task in
        select * from public.tasks
        where shift_id = p_source_shift
        order by starts_at nulls last, created_at
    loop
        insert into public.tasks(
            group_id, shift_id, title, description, location, starts_at, due_at,
            priority, status, visibility, allow_self_join, join_requires_approval,
            notify_creator_on_join, requires_completion_confirmation, max_participants,
            created_by, creator_id, deadline_time, copied_from_task_id, task_type,
            importance, urgency_type, execution_time, target_percentage,
            repeat_interval_minutes, alarm_type, advance_reminders_minutes,
            remind_until_completed, overdue_reminder_interval_minutes, max_overdue_reminders,
            response_type, lan_target_list, completion_policy, response_form_schema
        ) values (
            v_old_task.group_id,
            v_new_shift,
            v_old_task.title,
            v_old_task.description,
            v_old_task.location,
            case when v_old_task.starts_at is null then null else v_old_task.starts_at + make_interval(mins => p_offset_minutes) end,
            case when v_old_task.due_at is null then null else v_old_task.due_at + make_interval(mins => p_offset_minutes) end,
            v_old_task.priority,
            'open',
            v_old_task.visibility,
            v_old_task.allow_self_join,
            v_old_task.join_requires_approval,
            v_old_task.notify_creator_on_join,
            v_old_task.requires_completion_confirmation,
            v_old_task.max_participants,
            v_caller,
            v_caller,
            case when v_old_task.due_at is null then null else v_old_task.due_at + make_interval(mins => p_offset_minutes) end,
            v_old_task.id,
            v_old_task.task_type,
            v_old_task.importance,
            v_old_task.urgency_type,
            case when v_old_task.execution_time is null then null else v_old_task.execution_time + make_interval(mins => p_offset_minutes) end,
            v_old_task.target_percentage,
            v_old_task.repeat_interval_minutes,
            v_old_task.alarm_type,
            v_old_task.advance_reminders_minutes,
            v_old_task.remind_until_completed,
            v_old_task.overdue_reminder_interval_minutes,
            v_old_task.max_overdue_reminders,
            v_old_task.response_type,
            v_old_task.lan_target_list,
            v_old_task.completion_policy,
            v_old_task.response_form_schema
        ) returning id into v_new_task;

        if p_copy_manpower then
            insert into public.task_role_requirements(task_id, role_id, required_count, notes)
            select v_new_task, role_id, required_count, notes
            from public.task_role_requirements
            where task_id = v_old_task.id;
        end if;

        if p_copy_assignments then
            insert into public.task_participants(task_id, user_id, role_id, source, status, assigned_by)
            select v_new_task, user_id, role_id, 'copied_assignment', 'active', v_caller
            from public.task_participants
            where task_id = v_old_task.id and status = 'active'
            on conflict (task_id, user_id) where status = 'active' do nothing;
        else
            insert into public.task_participants(task_id, user_id, source, status, assigned_by)
            values (v_new_task, v_caller, 'assigned', 'active', v_caller)
            on conflict (task_id, user_id) where status = 'active' do nothing;
        end if;
    end loop;

    return v_new_shift;
end;
$$;

revoke all on function public.copy_shift(uuid, text, integer, boolean, boolean) from public;
grant execute on function public.copy_shift(uuid, text, integer, boolean, boolean) to authenticated;

-- ============================================================
-- 5. REPAIR SUBMIT_TASK_RESPONSE & ACKNOWLEDGE_TASK_ALARM RPCs
-- ============================================================

-- Remove legacy 9-parameter overload from migration 003 so PostgREST resolves cleanly
drop function if exists public.submit_task_response(uuid, text, text, integer, numeric, numeric, text, text, text);

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

    -- Authorization: creator, assignee, active participant, admin, or active user on a visible 'everyone' / member group task
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

    if not v_is_authorized and public.can_view_task(p_task_id) and (v_task.visibility = 'everyone' or public.is_group_member(v_task.group_id)) then
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

    -- Idempotency check
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

-- ============================================================
-- 6. SYSTEM INCIDENTS RLS, DELETE POLICY, GRANTS & VERSION TRIGGER
-- ============================================================

create table if not exists public.system_incidents (
    id uuid primary key default gen_random_uuid(),
    submission_id text not null unique,
    centre_id uuid null references public.task_groups(id) on delete set null,
    exam_session_id uuid null references public.shifts(id) on delete set null,
    state_name text not null default '',
    district_name text not null default '',
    centre_code text not null default '',
    centre_name text not null default '',
    exam_date date not null default current_date,
    exam_name text not null default '',
    shift_name text not null default '',
    lan_name text not null default '',
    mac_address text not null,
    ip_address text not null,
    locked_system_number text not null,
    newly_allocated_system_number text null,
    candidate_shifted boolean not null default false,
    incident_time timestamptz not null default now(),
    reason text not null,
    it_manager_name text not null default '',
    created_by uuid not null references public.profiles(id) on delete cascade,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    updated_by uuid null references public.profiles(id) on delete set null,
    version bigint not null default 1
);
alter table public.system_incidents enable row level security;

create table if not exists public.system_incident_audit (
    id uuid primary key default gen_random_uuid(),
    incident_id uuid not null references public.system_incidents(id) on delete cascade,
    changed_by uuid not null references public.profiles(id),
    changed_at timestamptz not null default now(),
    action text not null,
    previous_values jsonb null,
    new_values jsonb null
);
alter table public.system_incident_audit enable row level security;

drop policy if exists system_incidents_delete_policy on public.system_incidents;
create policy system_incidents_delete_policy on public.system_incidents
    for delete
    to authenticated
    using (
        created_by = (select auth.uid())
        or public.is_admin()
    );

drop trigger if exists system_incidents_version_trigger on public.system_incidents;
create trigger system_incidents_version_trigger
    before update on public.system_incidents
    for each row
    execute function public.bump_version();

grant select, insert, update, delete on public.system_incidents to authenticated;
grant select, insert on public.system_incident_audit to authenticated;
grant select on public.task_responses to authenticated;
grant select on public.task_acknowledgements to authenticated;
grant select on public.task_lan_status to authenticated;

commit;
