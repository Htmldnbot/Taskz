-- ============================================================
-- TASKZ - FINAL PRODUCTION DATABASE SCHEMA
-- Supabase PostgreSQL
-- ============================================================

begin;

create extension if not exists pgcrypto;


-- ============================================================
-- 1. COMMON FUNCTIONS
-- ============================================================

create or replace function public.normalize_employee_id(p_value text)
returns text
language sql
immutable
set search_path = ''
as $$
    select upper(trim(coalesce(p_value, '')));
$$;


create or replace function public.normalize_mobile(p_value text)
returns text
language plpgsql
immutable
set search_path = ''
as $$
declare
    v text;
begin
    v := regexp_replace(coalesce(p_value, ''), '[^0-9+]', '', 'g');

    if v ~ '^[6-9][0-9]{9}$' then
        return '+91' || v;
    end if;

    if v ~ '^91[6-9][0-9]{9}$' then
        return '+' || v;
    end if;

    return v;
end;
$$;


create or replace function public.set_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    new.updated_at := now();
    return new;
end;
$$;


create or replace function public.bump_version()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    new.updated_at := now();
    new.version := old.version + 1;
    return new;
end;
$$;


-- ============================================================
-- 2. PROFILES / USERS
-- ============================================================

create table public.profiles (

    id uuid primary key
        references auth.users(id)
        on delete cascade,

    employee_id text not null,

    full_name text not null,

    mobile text not null,

    profile_photo_path text null,

    app_role text not null default 'employee',

    is_active boolean not null default true,

    must_change_password boolean not null default false,

    last_seen_at timestamptz null,

    created_at timestamptz not null default now(),

    updated_at timestamptz not null default now(),

    constraint profiles_employee_id_check
        check (
            employee_id ~ '^[A-Z0-9._-]{2,40}$'
        ),

    constraint profiles_name_check
        check (
            char_length(trim(full_name)) between 2 and 120
        ),

    constraint profiles_mobile_check
        check (
            mobile ~ '^\+[1-9][0-9]{7,14}$'
        ),

    constraint profiles_role_check
        check (
            app_role in ('employee', 'admin')
        )
);


create unique index profiles_employee_id_unique
on public.profiles(lower(employee_id));

create index profiles_name_idx
on public.profiles(lower(full_name));

create index profiles_mobile_idx
on public.profiles(mobile);

create index profiles_active_idx
on public.profiles(is_active);

create index profiles_role_idx
on public.profiles(app_role);


create trigger profiles_updated_at
before update on public.profiles
for each row
execute function public.set_updated_at();


-- ============================================================
-- 3. AUTO PROFILE AFTER AUTH SIGNUP
-- ============================================================

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

    v_employee :=
        public.normalize_employee_id(
            new.raw_user_meta_data ->> 'employee_id'
        );

    v_name :=
        trim(
            coalesce(
                new.raw_user_meta_data ->> 'full_name',
                ''
            )
        );

    v_mobile :=
        public.normalize_mobile(
            new.raw_user_meta_data ->> 'mobile'
        );

    -- Allows admin-created auth users without metadata.
    if v_employee = '' then
        return new;
    end if;

    if char_length(v_name) < 2 then
        raise exception 'Invalid full name';
    end if;

    if v_mobile !~ '^\+[1-9][0-9]{7,14}$' then
        raise exception 'Invalid mobile number';
    end if;

    insert into public.profiles (
        id,
        employee_id,
        full_name,
        mobile
    )
    values (
        new.id,
        v_employee,
        v_name,
        v_mobile
    );

    return new;
end;
$$;


create trigger on_auth_user_created
after insert on auth.users
for each row
execute function public.handle_new_auth_user();


-- ============================================================
-- 4. BASIC SECURITY FUNCTIONS
-- ============================================================

create or replace function public.is_active_user()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.profiles p
        where p.id = (select auth.uid())
          and p.is_active = true
    );
$$;


create or replace function public.is_admin()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.profiles p
        where p.id = (select auth.uid())
          and p.app_role = 'admin'
          and p.is_active = true
    );
$$;


revoke all on function public.is_active_user() from public;
revoke all on function public.is_admin() from public;

grant execute on function public.is_active_user() to authenticated;
grant execute on function public.is_admin() to authenticated;


-- ============================================================
-- 5. WORK / MANPOWER ROLES
-- ============================================================

create table public.work_roles (

    id uuid primary key default gen_random_uuid(),

    name text not null,

    code text null,

    description text null,

    is_active boolean not null default true,

    sort_order integer not null default 0,

    created_by uuid null
        references public.profiles(id),

    created_at timestamptz not null default now(),

    updated_at timestamptz not null default now(),

    constraint work_role_name_check
        check (char_length(trim(name)) between 2 and 100)
);


create unique index work_roles_name_unique
on public.work_roles(lower(name));

create trigger work_roles_updated_at
before update on public.work_roles
for each row
execute function public.set_updated_at();


-- ============================================================
-- 6. EXAM / GROUP
-- ============================================================

create table public.task_groups (

    id uuid primary key default gen_random_uuid(),

    title text not null,

    code text null,

    description text null,

    start_date date null,

    end_date date null,

    status text not null default 'active',

    visibility text not null default 'everyone',

    allow_self_join boolean not null default false,

    join_requires_approval boolean not null default true,

    notify_creator_on_join boolean not null default true,

    max_members integer null,

    created_by uuid not null
        references public.profiles(id),

    copied_from_group_id uuid null
        references public.task_groups(id),

    version bigint not null default 1,

    created_at timestamptz not null default now(),

    updated_at timestamptz not null default now(),

    constraint group_status_check
        check (
            status in (
                'draft',
                'active',
                'completed',
                'cancelled',
                'archived'
            )
        ),

    constraint group_visibility_check
        check (
            visibility in ('everyone', 'restricted')
        ),

    constraint group_member_count_check
        check (
            max_members is null or max_members > 0
        )
);


create index groups_status_idx
on public.task_groups(status);

create index groups_created_by_idx
on public.task_groups(created_by);

create index groups_dates_idx
on public.task_groups(start_date, end_date);


create trigger groups_version_trigger
before update on public.task_groups
for each row
execute function public.bump_version();


-- ============================================================
-- 7. RESTRICTED GROUP VISIBILITY
-- ============================================================

create table public.group_visible_users (

    group_id uuid not null
        references public.task_groups(id)
        on delete cascade,

    user_id uuid not null
        references public.profiles(id)
        on delete cascade,

    added_by uuid not null
        references public.profiles(id),

    created_at timestamptz not null default now(),

    primary key (group_id, user_id)
);


-- ============================================================
-- 8. GROUP MEMBERS
-- ============================================================

create table public.group_members (

    id uuid primary key default gen_random_uuid(),

    group_id uuid not null
        references public.task_groups(id)
        on delete cascade,

    user_id uuid not null
        references public.profiles(id)
        on delete cascade,

    source text not null default 'assigned',

    status text not null default 'active',

    joined_at timestamptz not null default now(),

    left_at timestamptz null,

    left_reason text null,

    added_by uuid null
        references public.profiles(id),

    created_at timestamptz not null default now(),

    constraint group_member_source_check
        check (
            source in (
                'assigned',
                'self_join',
                'approved_join',
                'copied'
            )
        ),

    constraint group_member_status_check
        check (
            status in (
                'active',
                'left',
                'removed'
            )
        )
);


create unique index group_member_active_unique
on public.group_members(group_id, user_id)
where status = 'active';


create index group_members_user_idx
on public.group_members(user_id);


-- ============================================================
-- 9. GROUP JOIN REQUESTS
-- ============================================================

create table public.group_join_requests (

    id uuid primary key default gen_random_uuid(),

    group_id uuid not null
        references public.task_groups(id)
        on delete cascade,

    user_id uuid not null
        references public.profiles(id)
        on delete cascade,

    message text null,

    status text not null default 'pending',

    reviewed_by uuid null
        references public.profiles(id),

    reviewed_at timestamptz null,

    created_at timestamptz not null default now(),

    constraint group_join_status_check
        check (
            status in (
                'pending',
                'approved',
                'rejected',
                'cancelled'
            )
        )
);


create unique index group_join_pending_unique
on public.group_join_requests(group_id, user_id)
where status = 'pending';


-- ============================================================
-- 10. SHIFTS
-- ============================================================

create table public.shifts (

    id uuid primary key default gen_random_uuid(),

    group_id uuid not null
        references public.task_groups(id)
        on delete cascade,

    name text not null,

    shift_number integer null,

    starts_at timestamptz not null,

    ends_at timestamptz not null,

    notes text null,

    status text not null default 'scheduled',

    copied_from_shift_id uuid null
        references public.shifts(id),

    created_by uuid not null
        references public.profiles(id),

    version bigint not null default 1,

    created_at timestamptz not null default now(),

    updated_at timestamptz not null default now(),

    constraint shift_time_check
        check (ends_at > starts_at),

    constraint shift_status_check
        check (
            status in (
                'scheduled',
                'active',
                'completed',
                'cancelled'
            )
        )
);


create index shifts_group_idx
on public.shifts(group_id);

create index shifts_time_idx
on public.shifts(starts_at, ends_at);


create trigger shifts_version_trigger
before update on public.shifts
for each row
execute function public.bump_version();


-- ============================================================
-- 11. TASKS
-- ============================================================

create table public.tasks (

    id uuid primary key default gen_random_uuid(),

    group_id uuid not null
        references public.task_groups(id)
        on delete cascade,

    shift_id uuid null
        references public.shifts(id)
        on delete set null,

    title text not null,

    description text null,

    location text null,

    starts_at timestamptz null,

    due_at timestamptz null,

    priority text not null default 'normal',

    status text not null default 'open',

    visibility text not null default 'everyone',

    allow_self_join boolean not null default false,

    join_requires_approval boolean not null default true,

    notify_creator_on_join boolean not null default true,

    requires_completion_confirmation boolean not null default false,

    max_participants integer null,

    created_by uuid not null
        references public.profiles(id),

    copied_from_task_id uuid null
        references public.tasks(id),

    version bigint not null default 1,

    created_at timestamptz not null default now(),

    updated_at timestamptz not null default now(),

    constraint task_title_check
        check (
            char_length(trim(title)) between 1 and 200
        ),

    constraint task_time_check
        check (
            due_at is null
            or starts_at is null
            or due_at >= starts_at
        ),

    constraint task_priority_check
        check (
            priority in (
                'low',
                'normal',
                'high',
                'urgent'
            )
        ),

    constraint task_status_check
        check (
            status in (
                'draft',
                'open',
                'in_progress',
                'completed',
                'cancelled',
                'archived'
            )
        ),

    constraint task_visibility_check
        check (
            visibility in (
                'everyone',
                'restricted'
            )
        ),

    constraint task_max_check
        check (
            max_participants is null
            or max_participants > 0
        )
);


create index tasks_group_idx
on public.tasks(group_id);

create index tasks_shift_idx
on public.tasks(shift_id);

create index tasks_status_idx
on public.tasks(status);

create index tasks_creator_idx
on public.tasks(created_by);

create index tasks_schedule_idx
on public.tasks(starts_at, due_at);


create trigger tasks_version_trigger
before update on public.tasks
for each row
execute function public.bump_version();


-- ============================================================
-- 12. RESTRICTED TASK VISIBILITY
-- ============================================================

create table public.task_visible_users (

    task_id uuid not null
        references public.tasks(id)
        on delete cascade,

    user_id uuid not null
        references public.profiles(id)
        on delete cascade,

    added_by uuid not null
        references public.profiles(id),

    created_at timestamptz not null default now(),

    primary key(task_id, user_id)
);


-- ============================================================
-- 13. TASK MANPOWER REQUIREMENTS
-- ============================================================

create table public.task_role_requirements (

    id uuid primary key default gen_random_uuid(),

    task_id uuid not null
        references public.tasks(id)
        on delete cascade,

    role_id uuid not null
        references public.work_roles(id),

    required_count integer not null default 1,

    notes text null,

    created_at timestamptz not null default now(),

    constraint required_count_check
        check (required_count > 0),

    unique(task_id, role_id)
);


-- ============================================================
-- 14. TASK PARTICIPANTS / ASSIGNMENTS
-- ============================================================

create table public.task_participants (

    id uuid primary key default gen_random_uuid(),

    task_id uuid not null
        references public.tasks(id)
        on delete cascade,

    user_id uuid not null
        references public.profiles(id)
        on delete cascade,

    role_id uuid null
        references public.work_roles(id),

    source text not null default 'assigned',

    status text not null default 'active',

    assigned_by uuid null
        references public.profiles(id),

    joined_at timestamptz not null default now(),

    completed_at timestamptz null,

    left_at timestamptz null,

    left_reason text null,

    removed_by uuid null
        references public.profiles(id),

    created_at timestamptz not null default now(),

    constraint participant_source_check
        check (
            source in (
                'assigned',
                'self_join',
                'approved_join',
                'copied_assignment'
            )
        ),

    constraint participant_status_check
        check (
            status in (
                'active',
                'completed',
                'left',
                'removed'
            )
        )
);


create unique index task_participant_active_unique
on public.task_participants(task_id, user_id)
where status = 'active';


create index task_participant_user_idx
on public.task_participants(user_id);

create index task_participant_role_idx
on public.task_participants(task_id, role_id);


-- ============================================================
-- 15. TASK JOIN REQUESTS
-- ============================================================

create table public.task_join_requests (

    id uuid primary key default gen_random_uuid(),

    task_id uuid not null
        references public.tasks(id)
        on delete cascade,

    user_id uuid not null
        references public.profiles(id)
        on delete cascade,

    role_id uuid null
        references public.work_roles(id),

    message text null,

    status text not null default 'pending',

    reviewed_by uuid null
        references public.profiles(id),

    reviewed_at timestamptz null,

    created_at timestamptz not null default now(),

    constraint task_join_status_check
        check (
            status in (
                'pending',
                'approved',
                'rejected',
                'cancelled'
            )
        )
);


create unique index task_join_pending_unique
on public.task_join_requests(task_id, user_id)
where status = 'pending';


-- ============================================================
-- 16. TASK COMPLETION REQUESTS
-- ============================================================

create table public.task_completion_requests (

    id uuid primary key default gen_random_uuid(),

    task_id uuid not null
        references public.tasks(id)
        on delete cascade,

    requested_by uuid not null
        references public.profiles(id),

    note text null,

    status text not null default 'pending',

    reviewed_by uuid null
        references public.profiles(id),

    reviewed_at timestamptz null,

    created_at timestamptz not null default now(),

    constraint completion_status_check
        check (
            status in (
                'pending',
                'confirmed',
                'rejected',
                'cancelled'
            )
        )
);


create unique index task_completion_pending_unique
on public.task_completion_requests(task_id)
where status = 'pending';


-- ============================================================
-- 17. TASK COMMENTS
-- ============================================================

create table public.task_comments (

    id uuid primary key default gen_random_uuid(),

    task_id uuid not null
        references public.tasks(id)
        on delete cascade,

    created_by uuid not null
        references public.profiles(id),

    body text not null,

    is_edited boolean not null default false,

    is_deleted boolean not null default false,

    created_at timestamptz not null default now(),

    updated_at timestamptz not null default now(),

    constraint task_comment_check
        check (
            char_length(trim(body)) between 1 and 5000
        )
);


create index task_comments_task_idx
on public.task_comments(task_id, created_at);


create trigger task_comments_updated_at
before update on public.task_comments
for each row
execute function public.set_updated_at();


-- ============================================================
-- 18. TASK ATTACHMENTS
-- ============================================================

create table public.task_attachments (

    id uuid primary key default gen_random_uuid(),

    task_id uuid not null
        references public.tasks(id)
        on delete cascade,

    comment_id uuid null
        references public.task_comments(id)
        on delete cascade,

    uploaded_by uuid not null
        references public.profiles(id),

    storage_path text not null unique,

    original_file_name text not null,

    mime_type text null,

    file_size bigint null,

    created_at timestamptz not null default now(),

    constraint file_size_check
        check (
            file_size is null
            or file_size >= 0
        )
);


create index attachments_task_idx
on public.task_attachments(task_id);


-- ============================================================
-- 19. TASK ACTIVITY / TRANSPARENCY HISTORY
-- ============================================================

create table public.task_activity_log (

    id bigint generated always as identity primary key,

    task_id uuid not null
        references public.tasks(id)
        on delete cascade,

    actor_user_id uuid null
        references public.profiles(id)
        on delete set null,

    target_user_id uuid null
        references public.profiles(id)
        on delete set null,

    event_type text not null,

    message text null,

    metadata jsonb not null default '{}'::jsonb,

    created_at timestamptz not null default now()
);


create index activity_task_idx
on public.task_activity_log(task_id, created_at desc);

create index activity_actor_idx
on public.task_activity_log(actor_user_id);


-- ============================================================
-- 20. NOTIFICATIONS
-- ============================================================

create table public.notifications (

    id uuid primary key default gen_random_uuid(),

    user_id uuid not null
        references public.profiles(id)
        on delete cascade,

    type text not null,

    title text not null,

    body text null,

    task_id uuid null
        references public.tasks(id)
        on delete cascade,

    group_id uuid null
        references public.task_groups(id)
        on delete cascade,

    data jsonb not null default '{}'::jsonb,

    is_read boolean not null default false,

    read_at timestamptz null,

    created_at timestamptz not null default now()
);


create index notifications_user_idx
on public.notifications(user_id, is_read, created_at desc);


-- ============================================================
-- 21. DEVICE TOKENS FOR PUSH NOTIFICATIONS
-- ============================================================

create table public.device_tokens (

    id uuid primary key default gen_random_uuid(),

    user_id uuid not null
        references public.profiles(id)
        on delete cascade,

    token text not null,

    platform text not null default 'android',

    device_name text null,

    is_active boolean not null default true,

    last_seen_at timestamptz not null default now(),

    created_at timestamptz not null default now(),

    unique(token)
);


create index device_tokens_user_idx
on public.device_tokens(user_id);


-- ============================================================
-- 22. TASK REMINDERS
-- ============================================================

create table public.task_reminders (

    id uuid primary key default gen_random_uuid(),

    task_id uuid not null
        references public.tasks(id)
        on delete cascade,

    remind_at timestamptz not null,

    target_scope text not null default 'participants',

    target_user_id uuid null
        references public.profiles(id),

    title text null,

    message text null,

    created_by uuid not null
        references public.profiles(id),

    sent_at timestamptz null,

    cancelled_at timestamptz null,

    created_at timestamptz not null default now(),

    constraint reminder_scope_check
        check (
            target_scope in (
                'participants',
                'creator',
                'specific_user'
            )
        ),

    constraint reminder_target_check
        check (
            target_scope <> 'specific_user'
            or target_user_id is not null
        )
);


create index reminders_due_idx
on public.task_reminders(remind_at)
where sent_at is null and cancelled_at is null;


-- ============================================================
-- 23. PASSWORD RESET REQUESTS
-- ============================================================

create table public.password_reset_requests (

    id uuid primary key default gen_random_uuid(),

    target_user_id uuid null
        references public.profiles(id)
        on delete set null,

    employee_id text not null,

    mobile text not null,

    message text null,

    status text not null default 'pending',

    resolved_by uuid null
        references public.profiles(id),

    resolved_at timestamptz null,

    created_at timestamptz not null default now(),

    constraint password_reset_status_check
        check (
            status in (
                'pending',
                'resolved',
                'rejected'
            )
        )
);


create unique index password_reset_pending_unique
on public.password_reset_requests(target_user_id)
where status = 'pending' and target_user_id is not null;


-- ============================================================
-- 24. ADMIN AUDIT LOG
-- ============================================================

create table public.admin_audit_log (

    id bigint generated always as identity primary key,

    admin_user_id uuid null
        references public.profiles(id)
        on delete set null,

    target_user_id uuid null
        references public.profiles(id)
        on delete set null,

    action text not null,

    entity_type text null,

    entity_id uuid null,

    metadata jsonb not null default '{}'::jsonb,

    created_at timestamptz not null default now()
);


create index admin_audit_created_idx
on public.admin_audit_log(created_at desc);


-- ============================================================
-- 25. APP SETTINGS
-- ============================================================

create table public.app_settings (

    key text primary key,

    value jsonb not null,

    public_read boolean not null default false,

    updated_by uuid null
        references public.profiles(id),

    updated_at timestamptz not null default now()
);


insert into public.app_settings(key, value, public_read)
values (
    'app_info',
    '{"app_name":"Taskz"}'::jsonb,
    true
)
on conflict (key) do nothing;


-- ============================================================
-- 26. OFFLINE / IDEMPOTENCY EVENT LEDGER
-- ============================================================

create table public.client_events (

    id uuid primary key default gen_random_uuid(),

    transaction_id uuid not null unique,

    user_id uuid not null
        references public.profiles(id)
        on delete cascade,

    event_type text not null,

    entity_type text null,

    entity_id uuid null,

    payload jsonb not null default '{}'::jsonb,

    status text not null default 'received',

    server_result jsonb null,

    received_at timestamptz not null default now(),

    processed_at timestamptz null,

    acknowledged_at timestamptz null,

    constraint client_event_status_check
        check (
            status in (
                'received',
                'processed',
                'rejected'
            )
        )
);


create index client_events_user_idx
on public.client_events(user_id, received_at desc);


-- ============================================================
-- 27. SERVER SYNC STATE
-- ============================================================

create table public.user_sync_state (

    user_id uuid primary key
        references public.profiles(id)
        on delete cascade,

    last_server_fetch_at timestamptz null,

    last_server_ack_at timestamptz null,

    last_event_id uuid null,

    updated_at timestamptz not null default now()
);


-- ============================================================
-- 28. GROUP SECURITY FUNCTIONS
-- ============================================================

create or replace function public.is_group_member(p_group uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.group_members gm
        where gm.group_id = p_group
          and gm.user_id = (select auth.uid())
          and gm.status = 'active'
    );
$$;


create or replace function public.can_manage_group(p_group uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select
        public.is_admin()
        or exists (
            select 1
            from public.task_groups g
            where g.id = p_group
              and g.created_by = (select auth.uid())
        );
$$;


create or replace function public.can_view_group(p_group uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select
        public.is_admin()

        or exists (
            select 1
            from public.task_groups g
            where g.id = p_group
              and g.created_by = (select auth.uid())
        )

        or exists (
            select 1
            from public.group_members gm
            where gm.group_id = p_group
              and gm.user_id = (select auth.uid())
              and gm.status = 'active'
        )

        or exists (
            select 1
            from public.task_groups g
            where g.id = p_group
              and g.visibility = 'everyone'
              and public.is_active_user()
        )

        or exists (
            select 1
            from public.group_visible_users gv
            where gv.group_id = p_group
              and gv.user_id = (select auth.uid())
        );
$$;


-- ============================================================
-- 29. TASK SECURITY FUNCTIONS
-- ============================================================

create or replace function public.is_task_participant(p_task uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.task_participants tp
        where tp.task_id = p_task
          and tp.user_id = (select auth.uid())
          and tp.status = 'active'
    );
$$;


create or replace function public.can_manage_task(p_task uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select
        public.is_admin()
        or exists (
            select 1
            from public.tasks t
            where t.id = p_task
              and t.created_by = (select auth.uid())
        );
$$;


create or replace function public.can_view_task(p_task uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (

        select 1
        from public.tasks t

        where t.id = p_task

        and (

            public.is_admin()

            or t.created_by = (select auth.uid())

            or exists (
                select 1
                from public.task_participants tp
                where tp.task_id = t.id
                  and tp.user_id = (select auth.uid())
                  and tp.status = 'active'
            )

            or (

                public.can_view_group(t.group_id)

                and (

                    t.visibility = 'everyone'

                    or exists (
                        select 1
                        from public.task_visible_users tv
                        where tv.task_id = t.id
                          and tv.user_id = (select auth.uid())
                    )

                )

            )

        )

    );
$$;


revoke all on function public.is_group_member(uuid) from public;
revoke all on function public.can_manage_group(uuid) from public;
revoke all on function public.can_view_group(uuid) from public;
revoke all on function public.is_task_participant(uuid) from public;
revoke all on function public.can_manage_task(uuid) from public;
revoke all on function public.can_view_task(uuid) from public;

grant execute on function public.is_group_member(uuid) to authenticated;
grant execute on function public.can_manage_group(uuid) to authenticated;
grant execute on function public.can_view_group(uuid) to authenticated;
grant execute on function public.is_task_participant(uuid) to authenticated;
grant execute on function public.can_manage_task(uuid) to authenticated;
grant execute on function public.can_view_task(uuid) to authenticated;


-- ============================================================
-- 30. PROFILE RLS
-- ============================================================

alter table public.profiles enable row level security;

drop policy if exists profiles_read_self_admin on public.profiles;
create policy profiles_read_self_admin
on public.profiles
for select
to authenticated
using (
    id = (select auth.uid())
    or public.is_admin()
);

drop policy if exists profiles_update_self on public.profiles;
create policy profiles_update_self
on public.profiles
for update
to authenticated
using (
    id = (select auth.uid())
)
with check (
    id = (select auth.uid())
);


revoke all on public.profiles from anon, authenticated;

grant select on public.profiles to authenticated;

grant update(
    full_name,
    mobile,
    profile_photo_path,
    last_seen_at
)
on public.profiles
to authenticated;


-- ============================================================
-- 31. SAFE EMPLOYEE DIRECTORY SEARCH
-- ============================================================

create or replace function public.search_employee_directory(
    p_query text default ''
)
returns table (
    id uuid,
    employee_id text,
    full_name text,
    profile_photo_path text
)
language sql
stable
security definer
set search_path = ''
as $$
    select
        p.id,
        p.employee_id,
        p.full_name,
        p.profile_photo_path

    from public.profiles p

    where public.is_active_user()
      and p.is_active = true
      and (
            trim(coalesce(p_query, '')) = ''
            or p.employee_id ilike '%' || trim(p_query) || '%'
            or p.full_name ilike '%' || trim(p_query) || '%'
      )

    order by p.full_name

    limit 100;
$$;


revoke all
on function public.search_employee_directory(text)
from public;

grant execute
on function public.search_employee_directory(text)
to authenticated;


-- ============================================================
-- 32. WORK ROLE RLS
-- ============================================================

alter table public.work_roles enable row level security;

create policy roles_read
on public.work_roles
for select
to authenticated
using (
    public.is_active_user()
);

create policy roles_admin_insert
on public.work_roles
for insert
to authenticated
with check (
    public.is_admin()
);

create policy roles_admin_update
on public.work_roles
for update
to authenticated
using (
    public.is_admin()
)
with check (
    public.is_admin()
);

create policy roles_admin_delete
on public.work_roles
for delete
to authenticated
using (
    public.is_admin()
);

grant select, insert, update, delete
on public.work_roles
to authenticated;


-- ============================================================
-- 33. GROUP RLS
-- ============================================================

alter table public.task_groups enable row level security;

create policy groups_read
on public.task_groups
for select
to authenticated
using (
    public.can_view_group(id)
);

create policy groups_create
on public.task_groups
for insert
to authenticated
with check (
    public.is_active_user()
    and created_by = (select auth.uid())
);

create policy groups_update
on public.task_groups
for update
to authenticated
using (
    public.can_manage_group(id)
)
with check (
    public.can_manage_group(id)
);

create policy groups_delete
on public.task_groups
for delete
to authenticated
using (
    public.can_manage_group(id)
);

grant select, insert, update, delete
on public.task_groups
to authenticated;


-- ============================================================
-- 34. GROUP VISIBILITY RLS
-- ============================================================

alter table public.group_visible_users enable row level security;

create policy group_visible_read
on public.group_visible_users
for select
to authenticated
using (
    user_id = (select auth.uid())
    or public.can_manage_group(group_id)
);

create policy group_visible_insert
on public.group_visible_users
for insert
to authenticated
with check (
    public.can_manage_group(group_id)
);

create policy group_visible_delete
on public.group_visible_users
for delete
to authenticated
using (
    public.can_manage_group(group_id)
);

grant select, insert, delete
on public.group_visible_users
to authenticated;


-- ============================================================
-- 35. SHIFTS RLS
-- ============================================================

alter table public.shifts enable row level security;

create policy shifts_read
on public.shifts
for select
to authenticated
using (
    public.can_view_group(group_id)
);

create policy shifts_insert
on public.shifts
for insert
to authenticated
with check (
    public.can_manage_group(group_id)
);

create policy shifts_update
on public.shifts
for update
to authenticated
using (
    public.can_manage_group(group_id)
)
with check (
    public.can_manage_group(group_id)
);

create policy shifts_delete
on public.shifts
for delete
to authenticated
using (
    public.can_manage_group(group_id)
);

grant select, insert, update, delete
on public.shifts
to authenticated;


-- ============================================================
-- 36. TASK RLS
-- ============================================================

alter table public.tasks enable row level security;

create policy tasks_read
on public.tasks
for select
to authenticated
using (
    public.can_view_task(id)
);

create policy tasks_insert
on public.tasks
for insert
to authenticated
with check (
    public.is_active_user()
    and created_by = (select auth.uid())
    and public.can_manage_group(group_id)
);

create policy tasks_update
on public.tasks
for update
to authenticated
using (
    public.can_manage_task(id)
)
with check (
    public.can_manage_task(id)
);

create policy tasks_delete
on public.tasks
for delete
to authenticated
using (
    public.can_manage_task(id)
);

grant select, insert, update, delete
on public.tasks
to authenticated;


-- ============================================================
-- 37. TASK VISIBILITY RLS
-- ============================================================

alter table public.task_visible_users enable row level security;

create policy task_visible_read
on public.task_visible_users
for select
to authenticated
using (
    user_id = (select auth.uid())
    or public.can_manage_task(task_id)
);

create policy task_visible_insert
on public.task_visible_users
for insert
to authenticated
with check (
    public.can_manage_task(task_id)
);

create policy task_visible_delete
on public.task_visible_users
for delete
to authenticated
using (
    public.can_manage_task(task_id)
);

grant select, insert, delete
on public.task_visible_users
to authenticated;


-- ============================================================
-- 38. TASK ROLE REQUIREMENT RLS
-- ============================================================

alter table public.task_role_requirements
enable row level security;

create policy requirement_read
on public.task_role_requirements
for select
to authenticated
using (
    public.can_view_task(task_id)
);

create policy requirement_insert
on public.task_role_requirements
for insert
to authenticated
with check (
    public.can_manage_task(task_id)
);

create policy requirement_update
on public.task_role_requirements
for update
to authenticated
using (
    public.can_manage_task(task_id)
)
with check (
    public.can_manage_task(task_id)
);

create policy requirement_delete
on public.task_role_requirements
for delete
to authenticated
using (
    public.can_manage_task(task_id)
);

grant select, insert, update, delete
on public.task_role_requirements
to authenticated;


-- ============================================================
-- 39. PARTICIPANTS READ SECURITY
-- Direct modification will happen through RPC functions.
-- ============================================================

alter table public.task_participants enable row level security;

create policy participant_read
on public.task_participants
for select
to authenticated
using (
    user_id = (select auth.uid())
    or public.can_view_task(task_id)
);

grant select
on public.task_participants
to authenticated;


-- ============================================================
-- 40. JOIN REQUEST RLS
-- ============================================================

alter table public.task_join_requests enable row level security;

create policy join_request_read
on public.task_join_requests
for select
to authenticated
using (
    user_id = (select auth.uid())
    or public.can_manage_task(task_id)
);

grant select
on public.task_join_requests
to authenticated;


alter table public.group_join_requests enable row level security;

create policy group_join_request_read
on public.group_join_requests
for select
to authenticated
using (
    user_id = (select auth.uid())
    or public.can_manage_group(group_id)
);

grant select
on public.group_join_requests
to authenticated;


alter table public.group_members enable row level security;

create policy group_members_read
on public.group_members
for select
to authenticated
using (
    user_id = (select auth.uid())
    or public.can_view_group(group_id)
);

grant select
on public.group_members
to authenticated;


-- ============================================================
-- 41. COMMENTS RLS
-- ============================================================

alter table public.task_comments enable row level security;

create policy comments_read
on public.task_comments
for select
to authenticated
using (
    public.can_view_task(task_id)
);

create policy comments_insert
on public.task_comments
for insert
to authenticated
with check (
    created_by = (select auth.uid())
    and (
        public.is_task_participant(task_id)
        or public.can_manage_task(task_id)
    )
);

create policy comments_update
on public.task_comments
for update
to authenticated
using (
    created_by = (select auth.uid())
    or public.can_manage_task(task_id)
);

create policy comments_delete
on public.task_comments
for delete
to authenticated
using (
    created_by = (select auth.uid())
    or public.can_manage_task(task_id)
);

grant select, insert, update, delete
on public.task_comments
to authenticated;


-- ============================================================
-- 42. ATTACHMENT RLS
-- ============================================================

alter table public.task_attachments enable row level security;

create policy attachments_read
on public.task_attachments
for select
to authenticated
using (
    public.can_view_task(task_id)
);

create policy attachments_insert
on public.task_attachments
for insert
to authenticated
with check (
    uploaded_by = (select auth.uid())
    and (
        public.is_task_participant(task_id)
        or public.can_manage_task(task_id)
    )
);

create policy attachments_delete
on public.task_attachments
for delete
to authenticated
using (
    uploaded_by = (select auth.uid())
    or public.can_manage_task(task_id)
);

grant select, insert, delete
on public.task_attachments
to authenticated;


-- ============================================================
-- 43. ACTIVITY LOG
-- ============================================================

alter table public.task_activity_log enable row level security;

create policy activity_read
on public.task_activity_log
for select
to authenticated
using (
    public.can_view_task(task_id)
);

grant select
on public.task_activity_log
to authenticated;


-- ============================================================
-- 44. COMPLETION REQUEST RLS
-- ============================================================

alter table public.task_completion_requests enable row level security;

create policy completion_request_read
on public.task_completion_requests
for select
to authenticated
using (
    requested_by = (select auth.uid())
    or public.can_manage_task(task_id)
);

grant select
on public.task_completion_requests
to authenticated;


-- ============================================================
-- 45. NOTIFICATION RLS
-- ============================================================

alter table public.notifications enable row level security;

create policy notification_read
on public.notifications
for select
to authenticated
using (
    user_id = (select auth.uid())
);

create policy notification_update
on public.notifications
for update
to authenticated
using (
    user_id = (select auth.uid())
)
with check (
    user_id = (select auth.uid())
);

create policy notification_delete
on public.notifications
for delete
to authenticated
using (
    user_id = (select auth.uid())
);

grant select, update, delete
on public.notifications
to authenticated;


-- ============================================================
-- 46. DEVICE TOKEN RLS
-- ============================================================

alter table public.device_tokens enable row level security;

create policy device_tokens_own
on public.device_tokens
for all
to authenticated
using (
    user_id = (select auth.uid())
)
with check (
    user_id = (select auth.uid())
);

grant select, insert, update, delete
on public.device_tokens
to authenticated;


-- ============================================================
-- 47. REMINDER RLS
-- ============================================================

alter table public.task_reminders enable row level security;

create policy reminders_read
on public.task_reminders
for select
to authenticated
using (
    public.can_view_task(task_id)
);

create policy reminders_insert
on public.task_reminders
for insert
to authenticated
with check (
    public.can_manage_task(task_id)
);

create policy reminders_update
on public.task_reminders
for update
to authenticated
using (
    public.can_manage_task(task_id)
);

create policy reminders_delete
on public.task_reminders
for delete
to authenticated
using (
    public.can_manage_task(task_id)
);

grant select, insert, update, delete
on public.task_reminders
to authenticated;


-- ============================================================
-- 48. PASSWORD RESET SECURITY
-- ============================================================

alter table public.password_reset_requests
enable row level security;

create policy password_reset_admin_read
on public.password_reset_requests
for select
to authenticated
using (
    public.is_admin()
);

grant select
on public.password_reset_requests
to authenticated;


-- ============================================================
-- 49. ADMIN AUDIT RLS
-- ============================================================

alter table public.admin_audit_log enable row level security;

create policy admin_audit_read
on public.admin_audit_log
for select
to authenticated
using (
    public.is_admin()
);

grant select
on public.admin_audit_log
to authenticated;


-- ============================================================
-- 50. APP SETTINGS RLS
-- ============================================================

alter table public.app_settings enable row level security;

create policy settings_public_read
on public.app_settings
for select
to anon, authenticated
using (
    public_read = true
    or public.is_admin()
);

create policy settings_admin_manage
on public.app_settings
for all
to authenticated
using (
    public.is_admin()
)
with check (
    public.is_admin()
);

grant select
on public.app_settings
to anon, authenticated;

grant insert, update, delete
on public.app_settings
to authenticated;


-- ============================================================
-- 51. CLIENT EVENTS / OFFLINE SYNC
-- ============================================================

alter table public.client_events enable row level security;

create policy events_read_own
on public.client_events
for select
to authenticated
using (
    user_id = (select auth.uid())
);

create policy events_insert_own
on public.client_events
for insert
to authenticated
with check (
    user_id = (select auth.uid())
);

grant select, insert
on public.client_events
to authenticated;


alter table public.user_sync_state enable row level security;

create policy sync_state_read_own
on public.user_sync_state
for select
to authenticated
using (
    user_id = (select auth.uid())
);

grant select
on public.user_sync_state
to authenticated;


-- ============================================================
-- 52. INTERNAL NOTIFICATION FUNCTION
-- ============================================================

create or replace function public.create_notification(
    p_user uuid,
    p_type text,
    p_title text,
    p_body text default null,
    p_task uuid default null,
    p_group uuid default null,
    p_data jsonb default '{}'::jsonb
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin

    insert into public.notifications(
        user_id,
        type,
        title,
        body,
        task_id,
        group_id,
        data
    )
    values(
        p_user,
        p_type,
        p_title,
        p_body,
        p_task,
        p_group,
        coalesce(p_data, '{}'::jsonb)
    );

end;
$$;


revoke all
on function public.create_notification(
    uuid,text,text,text,uuid,uuid,jsonb
)
from public, anon, authenticated;


-- ============================================================
-- 53. TASK JOIN
-- ============================================================

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


    select *
    into v_task
    from public.tasks
    where id = p_task;


    if not found then
        raise exception 'Task not found';
    end if;


    if not public.can_view_task(p_task) then
        raise exception 'Task not available';
    end if;


    if not v_task.allow_self_join then
        raise exception 'Self joining is not allowed';
    end if;


    if v_task.status not in ('open', 'in_progress') then
        raise exception 'Task is not open for joining';
    end if;


    if exists (
        select 1
        from public.task_participants
        where task_id = p_task
          and user_id = v_user
          and status = 'active'
    ) then

        return jsonb_build_object(
            'status',
            'already_joined'
        );

    end if;


    if v_task.max_participants is not null then

        select count(*)
        into v_current
        from public.task_participants
        where task_id = p_task
          and status = 'active';

        if v_current >= v_task.max_participants then
            raise exception 'Task is full';
        end if;

    end if;


    if exists (
        select 1
        from public.task_role_requirements
        where task_id = p_task
    ) then

        if p_role is null then
            raise exception 'Role required';
        end if;

        if not exists (
            select 1
            from public.task_role_requirements
            where task_id = p_task
              and role_id = p_role
        ) then
            raise exception 'Invalid role';
        end if;

    end if;


    if v_task.join_requires_approval then

        insert into public.task_join_requests(
            task_id,
            user_id,
            role_id,
            message
        )
        values(
            p_task,
            v_user,
            p_role,
            nullif(trim(p_message), '')
        );


        insert into public.task_activity_log(
            task_id,
            actor_user_id,
            target_user_id,
            event_type,
            message
        )
        values(
            p_task,
            v_user,
            v_user,
            'JOIN_REQUESTED',
            'User requested to join task'
        );


        perform public.create_notification(
            v_task.created_by,
            'task_join_request',
            'New task join request',
            'An employee wants to join your task.',
            p_task,
            v_task.group_id
        );


        return jsonb_build_object(
            'status',
            'pending_approval'
        );

    else

        insert into public.task_participants(
            task_id,
            user_id,
            role_id,
            source,
            status,
            assigned_by
        )
        values(
            p_task,
            v_user,
            p_role,
            'self_join',
            'active',
            v_user
        );


        insert into public.task_activity_log(
            task_id,
            actor_user_id,
            target_user_id,
            event_type,
            message
        )
        values(
            p_task,
            v_user,
            v_user,
            'USER_JOINED',
            'User joined task'
        );


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


        return jsonb_build_object(
            'status',
            'joined'
        );

    end if;

end;
$$;


revoke all on function public.request_task_join(uuid,uuid,text)
from public;

grant execute on function public.request_task_join(uuid,uuid,text)
to authenticated;


-- ============================================================
-- 54. APPROVE / REJECT TASK JOIN
-- ============================================================

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

    select *
    into v_request
    from public.task_join_requests
    where id = p_request
      and status = 'pending';


    if not found then
        raise exception 'Join request not found';
    end if;


    if not public.can_manage_task(v_request.task_id) then
        raise exception 'Unauthorized';
    end if;


    select *
    into v_task
    from public.tasks
    where id = v_request.task_id;


    if p_approve then

        insert into public.task_participants(
            task_id,
            user_id,
            role_id,
            source,
            status,
            assigned_by
        )
        values(
            v_request.task_id,
            v_request.user_id,
            v_request.role_id,
            'approved_join',
            'active',
            (select auth.uid())
        );


        update public.task_join_requests
        set
            status = 'approved',
            reviewed_by = (select auth.uid()),
            reviewed_at = now()
        where id = p_request;


        insert into public.task_activity_log(
            task_id,
            actor_user_id,
            target_user_id,
            event_type,
            message
        )
        values(
            v_request.task_id,
            (select auth.uid()),
            v_request.user_id,
            'JOIN_APPROVED',
            'Task join request approved'
        );


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
        set
            status = 'rejected',
            reviewed_by = (select auth.uid()),
            reviewed_at = now()
        where id = p_request;


        insert into public.task_activity_log(
            task_id,
            actor_user_id,
            target_user_id,
            event_type,
            message
        )
        values(
            v_request.task_id,
            (select auth.uid()),
            v_request.user_id,
            'JOIN_REJECTED',
            'Task join request rejected'
        );


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


revoke all
on function public.review_task_join(uuid,boolean)
from public;

grant execute
on function public.review_task_join(uuid,boolean)
to authenticated;


-- ============================================================
-- 55. MANUAL TASK ASSIGNMENT
-- ============================================================

create or replace function public.assign_user_to_task(
    p_task uuid,
    p_user uuid,
    p_role uuid default null
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
begin

    if not public.can_manage_task(p_task) then
        raise exception 'Unauthorized';
    end if;


    if not exists (
        select 1
        from public.profiles
        where id = p_user
          and is_active = true
    ) then
        raise exception 'User unavailable';
    end if;


    if exists (
        select 1
        from public.task_participants
        where task_id = p_task
          and user_id = p_user
          and status = 'active'
    ) then
        raise exception 'User already assigned';
    end if;


    insert into public.task_participants(
        task_id,
        user_id,
        role_id,
        source,
        status,
        assigned_by
    )
    values(
        p_task,
        p_user,
        p_role,
        'assigned',
        'active',
        (select auth.uid())
    );


    insert into public.task_activity_log(
        task_id,
        actor_user_id,
        target_user_id,
        event_type,
        message
    )
    values(
        p_task,
        (select auth.uid()),
        p_user,
        'USER_ASSIGNED',
        'User assigned to task'
    );


    perform public.create_notification(
        p_user,
        'task_assigned',
        'New task assigned',
        'You have been assigned to a task.',
        p_task
    );


    return true;

end;
$$;


revoke all
on function public.assign_user_to_task(uuid,uuid,uuid)
from public;

grant execute
on function public.assign_user_to_task(uuid,uuid,uuid)
to authenticated;


-- ============================================================
-- 56. LEAVE TASK
-- ============================================================

create or replace function public.leave_task(
    p_task uuid,
    p_reason text default null
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare

    v_user uuid := (select auth.uid());

    v_task public.tasks%rowtype;

begin

    select *
    into v_task
    from public.tasks
    where id = p_task;


    update public.task_participants

    set
        status = 'left',
        left_at = now(),
        left_reason = nullif(trim(p_reason), '')

    where task_id = p_task
      and user_id = v_user
      and status = 'active';


    if not found then
        raise exception 'Active task membership not found';
    end if;


    insert into public.task_activity_log(
        task_id,
        actor_user_id,
        target_user_id,
        event_type,
        message
    )
    values(
        p_task,
        v_user,
        v_user,
        'USER_LEFT',
        'User left task'
    );


    perform public.create_notification(
        v_task.created_by,
        'task_member_left',
        'Employee left task',
        'An employee left this task. Backfill may be required.',
        p_task,
        v_task.group_id
    );


    insert into public.notifications(
        user_id,
        type,
        title,
        body,
        task_id,
        group_id
    )
    select
        p.id,
        'task_member_left',
        'Task manpower changed',
        'An employee left a task. Backfill may be required.',
        p_task,
        v_task.group_id

    from public.profiles p

    where p.app_role = 'admin'
      and p.is_active = true
      and p.id <> v_task.created_by;


    return true;

end;
$$;


revoke all
on function public.leave_task(uuid,text)
from public;

grant execute
on function public.leave_task(uuid,text)
to authenticated;


-- ============================================================
-- 57. GROUP JOIN
-- ============================================================

create or replace function public.request_group_join(
    p_group uuid,
    p_message text default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare

    v_user uuid := (select auth.uid());

    v_group public.task_groups%rowtype;

begin

    if not public.is_active_user() then
        raise exception 'Account inactive';
    end if;


    select *
    into v_group
    from public.task_groups
    where id = p_group;


    if not found then
        raise exception 'Group not found';
    end if;


    if not public.can_view_group(p_group) then
        raise exception 'Group not available';
    end if;


    if not v_group.allow_self_join then
        raise exception 'Self joining disabled';
    end if;


    if exists (
        select 1
        from public.group_members
        where group_id = p_group
          and user_id = v_user
          and status = 'active'
    ) then

        return jsonb_build_object(
            'status',
            'already_joined'
        );

    end if;


    if v_group.join_requires_approval then

        insert into public.group_join_requests(
            group_id,
            user_id,
            message
        )
        values(
            p_group,
            v_user,
            nullif(trim(p_message), '')
        );


        perform public.create_notification(
            v_group.created_by,
            'group_join_request',
            'New group join request',
            'An employee wants to join the group.',
            null,
            p_group
        );


        return jsonb_build_object(
            'status',
            'pending_approval'
        );

    else

        insert into public.group_members(
            group_id,
            user_id,
            source,
            status,
            added_by
        )
        values(
            p_group,
            v_user,
            'self_join',
            'active',
            v_user
        );


        if v_group.notify_creator_on_join then

            perform public.create_notification(
                v_group.created_by,
                'group_joined',
                'Employee joined group',
                'An employee joined your group.',
                null,
                p_group
            );

        end if;


        return jsonb_build_object(
            'status',
            'joined'
        );

    end if;

end;
$$;


revoke all
on function public.request_group_join(uuid,text)
from public;

grant execute
on function public.request_group_join(uuid,text)
to authenticated;


-- ============================================================
-- 58. GROUP JOIN REVIEW
-- ============================================================

create or replace function public.review_group_join(
    p_request uuid,
    p_approve boolean
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare

    v public.group_join_requests%rowtype;

begin

    select *
    into v
    from public.group_join_requests
    where id = p_request
      and status = 'pending';


    if not found then
        raise exception 'Join request not found';
    end if;


    if not public.can_manage_group(v.group_id) then
        raise exception 'Unauthorized';
    end if;


    if p_approve then

        insert into public.group_members(
            group_id,
            user_id,
            source,
            status,
            added_by
        )
        values(
            v.group_id,
            v.user_id,
            'approved_join',
            'active',
            (select auth.uid())
        );


        update public.group_join_requests
        set
            status = 'approved',
            reviewed_by = (select auth.uid()),
            reviewed_at = now()
        where id = p_request;


        perform public.create_notification(
            v.user_id,
            'group_join_approved',
            'Group join approved',
            'Your group join request was approved.',
            null,
            v.group_id
        );

    else

        update public.group_join_requests
        set
            status = 'rejected',
            reviewed_by = (select auth.uid()),
            reviewed_at = now()
        where id = p_request;


        perform public.create_notification(
            v.user_id,
            'group_join_rejected',
            'Group join declined',
            'Your group join request was declined.',
            null,
            v.group_id
        );

    end if;


    return true;

end;
$$;


revoke all
on function public.review_group_join(uuid,boolean)
from public;

grant execute
on function public.review_group_join(uuid,boolean)
to authenticated;


-- ============================================================
-- 59. LEAVE GROUP
-- ============================================================

create or replace function public.leave_group(
    p_group uuid,
    p_reason text default null
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare

    v_group public.task_groups%rowtype;

begin

    select *
    into v_group
    from public.task_groups
    where id = p_group;


    update public.group_members
    set
        status = 'left',
        left_at = now(),
        left_reason = nullif(trim(p_reason), '')

    where group_id = p_group
      and user_id = (select auth.uid())
      and status = 'active';


    if not found then
        raise exception 'Active group membership not found';
    end if;


    perform public.create_notification(
        v_group.created_by,
        'group_member_left',
        'Employee left group',
        'An employee left the group.',
        null,
        p_group
    );


    return true;

end;
$$;


revoke all
on function public.leave_group(uuid,text)
from public;

grant execute
on function public.leave_group(uuid,text)
to authenticated;


-- ============================================================
-- 60. TASK COMPLETION
-- ============================================================

create or replace function public.request_task_completion(
    p_task uuid,
    p_note text default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare

    v_task public.tasks%rowtype;

begin

    select *
    into v_task
    from public.tasks
    where id = p_task;


    if not found then
        raise exception 'Task not found';
    end if;


    if not (
        public.is_task_participant(p_task)
        or public.can_manage_task(p_task)
    ) then
        raise exception 'Unauthorized';
    end if;


    if v_task.requires_completion_confirmation
       and not public.can_manage_task(p_task) then

        insert into public.task_completion_requests(
            task_id,
            requested_by,
            note
        )
        values(
            p_task,
            (select auth.uid()),
            nullif(trim(p_note), '')
        );


        perform public.create_notification(
            v_task.created_by,
            'completion_requested',
            'Task completion requested',
            'An employee marked the task as completed.',
            p_task,
            v_task.group_id
        );


        return jsonb_build_object(
            'status',
            'pending_confirmation'
        );

    else

        update public.tasks
        set status = 'completed'
        where id = p_task;


        insert into public.task_activity_log(
            task_id,
            actor_user_id,
            event_type,
            message
        )
        values(
            p_task,
            (select auth.uid()),
            'TASK_COMPLETED',
            'Task completed'
        );


        return jsonb_build_object(
            'status',
            'completed'
        );

    end if;

end;
$$;


revoke all
on function public.request_task_completion(uuid,text)
from public;

grant execute
on function public.request_task_completion(uuid,text)
to authenticated;


-- ============================================================
-- 61. REVIEW TASK COMPLETION
-- ============================================================

create or replace function public.review_task_completion(
    p_request uuid,
    p_confirm boolean
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare

    v public.task_completion_requests%rowtype;

begin

    select *
    into v
    from public.task_completion_requests
    where id = p_request
      and status = 'pending';


    if not found then
        raise exception 'Completion request not found';
    end if;


    if not public.can_manage_task(v.task_id) then
        raise exception 'Unauthorized';
    end if;


    if p_confirm then

        update public.task_completion_requests
        set
            status = 'confirmed',
            reviewed_by = (select auth.uid()),
            reviewed_at = now()
        where id = p_request;


        update public.tasks
        set status = 'completed'
        where id = v.task_id;


        perform public.create_notification(
            v.requested_by,
            'completion_confirmed',
            'Completion confirmed',
            'Task completion has been confirmed.',
            v.task_id
        );

    else

        update public.task_completion_requests
        set
            status = 'rejected',
            reviewed_by = (select auth.uid()),
            reviewed_at = now()
        where id = p_request;


        perform public.create_notification(
            v.requested_by,
            'completion_rejected',
            'Completion not confirmed',
            'Task completion requires further action.',
            v.task_id
        );

    end if;


    return true;

end;
$$;


revoke all
on function public.review_task_completion(uuid,boolean)
from public;

grant execute
on function public.review_task_completion(uuid,boolean)
to authenticated;


-- ============================================================
-- 62. PASSWORD RESET REQUEST
-- ============================================================

create or replace function public.submit_password_reset_request(
    p_employee_id text,
    p_mobile text,
    p_message text default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare

    v_employee text;
    v_mobile text;
    v_user uuid;

begin

    v_employee :=
        public.normalize_employee_id(p_employee_id);

    v_mobile :=
        public.normalize_mobile(p_mobile);


    select id
    into v_user
    from public.profiles
    where employee_id = v_employee
      and mobile = v_mobile
    limit 1;


    if v_user is not null then

        begin

            insert into public.password_reset_requests(
                target_user_id,
                employee_id,
                mobile,
                message
            )
            values(
                v_user,
                v_employee,
                v_mobile,
                nullif(trim(p_message), '')
            );

        exception
            when unique_violation then
                null;
        end;

    end if;


    -- Always identical response.
    return jsonb_build_object(
        'accepted',
        true
    );

end;
$$;


revoke all
on function public.submit_password_reset_request(text,text,text)
from public;

grant execute
on function public.submit_password_reset_request(text,text,text)
to anon, authenticated;


-- ============================================================
-- 63. ADMIN USER ACTIVE / INACTIVE
-- ============================================================

create or replace function public.admin_set_user_active(
    p_user uuid,
    p_active boolean
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
begin

    if not public.is_admin() then
        raise exception 'Unauthorized';
    end if;


    if p_user = (select auth.uid())
       and p_active = false then
        raise exception 'Cannot deactivate own admin account';
    end if;


    update public.profiles
    set is_active = p_active
    where id = p_user;


    if not found then
        raise exception 'User not found';
    end if;


    insert into public.admin_audit_log(
        admin_user_id,
        target_user_id,
        action,
        entity_type,
        entity_id
    )
    values(
        (select auth.uid()),
        p_user,
        case
            when p_active
                then 'USER_ACTIVATED'
            else
                'USER_DEACTIVATED'
        end,
        'user',
        p_user
    );


    return true;

end;
$$;


revoke all
on function public.admin_set_user_active(uuid,boolean)
from public;

grant execute
on function public.admin_set_user_active(uuid,boolean)
to authenticated;


-- ============================================================
-- 64. ADMIN PASSWORD FLAG
-- Actual Auth password reset will be done by Edge Function.
-- ============================================================

create or replace function public.admin_set_password_change_required(
    p_user uuid,
    p_value boolean
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
begin

    if not public.is_admin() then
        raise exception 'Unauthorized';
    end if;


    update public.profiles
    set must_change_password = p_value
    where id = p_user;


    if not found then
        raise exception 'User not found';
    end if;


    return true;

end;
$$;


revoke all
on function public.admin_set_password_change_required(uuid,boolean)
from public;

grant execute
on function public.admin_set_password_change_required(uuid,boolean)
to authenticated;


-- ============================================================
-- 65. SHIFT COPY
-- ============================================================

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

begin

    select *
    into v_source
    from public.shifts
    where id = p_source_shift;


    if not found then
        raise exception 'Source shift not found';
    end if;


    if not public.can_manage_group(v_source.group_id) then
        raise exception 'Unauthorized';
    end if;


    insert into public.shifts(
        group_id,
        name,
        shift_number,
        starts_at,
        ends_at,
        notes,
        status,
        copied_from_shift_id,
        created_by
    )
    values(
        v_source.group_id,
        trim(p_new_name),
        null,
        v_source.starts_at
            + make_interval(mins => p_offset_minutes),
        v_source.ends_at
            + make_interval(mins => p_offset_minutes),
        v_source.notes,
        'scheduled',
        v_source.id,
        (select auth.uid())
    )
    returning id
    into v_new_shift;


    for v_old_task in

        select *
        from public.tasks
        where shift_id = p_source_shift
        order by starts_at nulls last, created_at

    loop

        insert into public.tasks(
            group_id,
            shift_id,
            title,
            description,
            location,
            starts_at,
            due_at,
            priority,
            status,
            visibility,
            allow_self_join,
            join_requires_approval,
            notify_creator_on_join,
            requires_completion_confirmation,
            max_participants,
            created_by,
            copied_from_task_id
        )
        values(
            v_old_task.group_id,
            v_new_shift,
            v_old_task.title,
            v_old_task.description,
            v_old_task.location,

            case
                when v_old_task.starts_at is null
                then null
                else v_old_task.starts_at
                    + make_interval(
                        mins => p_offset_minutes
                    )
            end,

            case
                when v_old_task.due_at is null
                then null
                else v_old_task.due_at
                    + make_interval(
                        mins => p_offset_minutes
                    )
            end,

            v_old_task.priority,
            'open',
            v_old_task.visibility,
            v_old_task.allow_self_join,
            v_old_task.join_requires_approval,
            v_old_task.notify_creator_on_join,
            v_old_task.requires_completion_confirmation,
            v_old_task.max_participants,
            (select auth.uid()),
            v_old_task.id
        )
        returning id
        into v_new_task;


        -- Copy restricted visibility

        insert into public.task_visible_users(
            task_id,
            user_id,
            added_by
        )
        select
            v_new_task,
            user_id,
            (select auth.uid())

        from public.task_visible_users

        where task_id = v_old_task.id;


        -- Copy manpower requirements

        if p_copy_manpower then

            insert into public.task_role_requirements(
                task_id,
                role_id,
                required_count,
                notes
            )
            select
                v_new_task,
                role_id,
                required_count,
                notes

            from public.task_role_requirements

            where task_id = v_old_task.id;

        end if;


        -- Optional actual employee assignment copy

        if p_copy_assignments then

            insert into public.task_participants(
                task_id,
                user_id,
                role_id,
                source,
                status,
                assigned_by
            )
            select
                v_new_task,
                user_id,
                role_id,
                'copied_assignment',
                'active',
                (select auth.uid())

            from public.task_participants

            where task_id = v_old_task.id
              and status = 'active';

        end if;

    end loop;


    return v_new_shift;

end;
$$;


revoke all
on function public.copy_shift(
    uuid,text,integer,boolean,boolean
)
from public;

grant execute
on function public.copy_shift(
    uuid,text,integer,boolean,boolean
)
to authenticated;


-- ============================================================
-- 66. RECORD REAL SERVER FETCH
-- ============================================================

create or replace function public.record_server_fetch()
returns timestamptz
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_now timestamptz := now();
begin

    insert into public.user_sync_state(
        user_id,
        last_server_fetch_at,
        updated_at
    )
    values(
        (select auth.uid()),
        v_now,
        v_now
    )

    on conflict(user_id)
    do update set

        last_server_fetch_at = excluded.last_server_fetch_at,
        updated_at = excluded.updated_at;


    return v_now;

end;
$$;


revoke all
on function public.record_server_fetch()
from public;

grant execute
on function public.record_server_fetch()
to authenticated;


-- ============================================================
-- 67. STORAGE BUCKETS
-- ============================================================

insert into storage.buckets(
    id,
    name,
    public,
    file_size_limit,
    allowed_mime_types
)
values(
    'profile-photos',
    'profile-photos',
    false,
    5242880,
    array[
        'image/jpeg',
        'image/png',
        'image/webp'
    ]
)
on conflict(id)
do update set
    public = false,
    file_size_limit = 5242880;


insert into storage.buckets(
    id,
    name,
    public,
    file_size_limit
)
values(
    'task-files',
    'task-files',
    false,
    20971520
)
on conflict(id)
do update set
    public = false,
    file_size_limit = 20971520;


-- ============================================================
-- 68. PROFILE PHOTO STORAGE RLS
-- Path:
-- USER_UUID/timestamp.webp
-- ============================================================

create policy profile_photo_read
on storage.objects
for select
to authenticated
using (
    bucket_id = 'profile-photos'
    and public.is_active_user()
);


create policy profile_photo_insert
on storage.objects
for insert
to authenticated
with check (
    bucket_id = 'profile-photos'
    and (storage.foldername(name))[1]
        = (select auth.uid())::text
);


create policy profile_photo_update
on storage.objects
for update
to authenticated
using (
    bucket_id = 'profile-photos'
    and (storage.foldername(name))[1]
        = (select auth.uid())::text
)
with check (
    bucket_id = 'profile-photos'
    and (storage.foldername(name))[1]
        = (select auth.uid())::text
);


create policy profile_photo_delete
on storage.objects
for delete
to authenticated
using (
    bucket_id = 'profile-photos'
    and (storage.foldername(name))[1]
        = (select auth.uid())::text
);


-- ============================================================
-- 69. TASK FILE STORAGE RLS
--
-- Path:
-- TASK_UUID/USER_UUID/filename
-- ============================================================

create policy task_files_read
on storage.objects
for select
to authenticated
using (

    bucket_id = 'task-files'

    and exists (

        select 1
        from public.tasks t

        where t.id::text =
            (storage.foldername(name))[1]

        and public.can_view_task(t.id)

    )

);


create policy task_files_insert
on storage.objects
for insert
to authenticated
with check (

    bucket_id = 'task-files'

    and (storage.foldername(name))[2]
        = (select auth.uid())::text

    and exists (

        select 1
        from public.tasks t

        where t.id::text =
            (storage.foldername(name))[1]

        and (
            public.is_task_participant(t.id)
            or public.can_manage_task(t.id)
        )

    )

);


create policy task_files_delete
on storage.objects
for delete
to authenticated
using (

    bucket_id = 'task-files'

    and (

        (storage.foldername(name))[2]
            = (select auth.uid())::text

        or exists (

            select 1
            from public.tasks t

            where t.id::text =
                (storage.foldername(name))[1]

            and public.can_manage_task(t.id)

        )

    )

);


-- ============================================================
-- 70. REMOVE ANONYMOUS ACCESS
-- ============================================================

revoke all on public.profiles from anon;
revoke all on public.task_groups from anon;
revoke all on public.shifts from anon;
revoke all on public.tasks from anon;
revoke all on public.task_participants from anon;
revoke all on public.task_comments from anon;
revoke all on public.task_attachments from anon;
revoke all on public.notifications from anon;
revoke all on public.admin_audit_log from anon;
revoke all on public.client_events from anon;

commit;
