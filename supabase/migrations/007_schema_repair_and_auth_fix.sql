-- ============================================================
-- TASKZ - MIGRATION 007: SCHEMA REPAIR & AUTHENTICATION FIX
-- Supabase PostgreSQL Forward-Only Repair Migration
-- ============================================================

begin;

-- ============================================================
-- 1. REPAIR & NORMALIZE EMPLOYEE ID FUNCTIONS & TRIGGERS
-- ============================================================

-- Robust Employee ID normalization: trims, extracts local part if email was passed, and uppercases.
create or replace function public.normalize_employee_id(p_value text)
returns text
language sql
immutable
set search_path = ''
as $$
    select upper(trim(split_part(coalesce(p_value, ''), '@', 1)));
$$;

-- Trigger function to guarantee employee_id is always normalized to uppercase before constraints
create or replace function public.trig_normalize_profile_employee_id()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    new.employee_id := public.normalize_employee_id(new.employee_id);
    return new;
end;
$$;

drop trigger if exists trg_normalize_profile_employee_id on public.profiles;
create trigger trg_normalize_profile_employee_id
    before insert or update on public.profiles
    for each row
    execute function public.trig_normalize_profile_employee_id();

-- Update check constraint on profiles to accept normalized employee IDs cleanly
alter table public.profiles
    drop constraint if exists profiles_employee_id_check;

alter table public.profiles
    add constraint profiles_employee_id_check
    check (
        employee_id ~ '^[A-Z0-9._-]{2,40}$'
    );

-- Update handle_new_auth_user to be resilient, handle metadata and email fallbacks, and reject duplicates cleanly
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
    -- 1. Extract employee ID with fallbacks
    v_employee := public.normalize_employee_id(
        coalesce(
            new.raw_user_meta_data ->> 'employee_id',
            new.raw_user_meta_data ->> 'employeeId',
            split_part(coalesce(new.email, ''), '@', 1)
        )
    );

    -- 2. Extract full name
    v_name := trim(
        coalesce(
            new.raw_user_meta_data ->> 'full_name',
            new.raw_user_meta_data ->> 'fullName',
            new.raw_user_meta_data ->> 'name',
            'Employee ' || v_employee
        )
    );

    -- 3. Extract and normalize mobile number
    v_mobile := public.normalize_mobile(
        coalesce(
            new.raw_user_meta_data ->> 'mobile',
            new.raw_user_meta_data ->> 'phone',
            ''
        )
    );

    -- If employee ID is still empty, allow admin-created users without metadata
    if v_employee = '' then
        return new;
    end if;

    if char_length(v_name) < 2 then
        v_name := 'Employee ' || v_employee;
    end if;

    if v_mobile !~ '^\+[1-9][0-9]{7,14}$' then
        v_mobile := '+910000000000';
    end if;

    -- Check for duplicate Employee ID belonging to a different auth account
    if exists (
        select 1 from public.profiles
        where lower(employee_id) = lower(v_employee)
          and id != new.id
    ) then
        raise exception 'Employee ID % is already registered to another account', v_employee;
    end if;

    -- Insert profile safely (never grant admin automatically)
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
        mobile = case when excluded.mobile != '+910000000000' then excluded.mobile else public.profiles.mobile end,
        updated_at = now();

    return new;
end;
$$;


-- ============================================================
-- 2. REPAIR TASKS TABLE COLUMN MISMATCHES & ALIASES
-- ============================================================

-- Add creator_id, deadline_time, and assigned_user_id columns for total schema compatibility
alter table public.tasks
    add column if not exists creator_id uuid references public.profiles(id) on delete set null,
    add column if not exists deadline_time timestamptz null,
    add column if not exists assigned_user_id uuid references public.profiles(id) on delete set null;

-- Sync existing data
update public.tasks
set creator_id = created_by
where creator_id is null and created_by is not null;

update public.tasks
set deadline_time = due_at
where deadline_time is null and due_at is not null;

-- Trigger to keep creator_id/created_by and due_at/deadline_time synchronized automatically
create or replace function public.trig_sync_task_compat_columns()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    -- Sync creator
    if new.creator_id is null and new.created_by is not null then
        new.creator_id := new.created_by;
    elsif new.created_by is null and new.creator_id is not null then
        new.created_by := new.creator_id;
    end if;

    -- Sync deadline
    if new.deadline_time is null and new.due_at is not null then
        new.deadline_time := new.due_at;
    elsif new.due_at is null and new.deadline_time is not null then
        new.due_at := new.deadline_time;
    end if;

    return new;
end;
$$;

drop trigger if exists trg_sync_task_compat_columns on public.tasks;
create trigger trg_sync_task_compat_columns
    before insert or update on public.tasks
    for each row
    execute function public.trig_sync_task_compat_columns();

-- ============================================================
-- 3. EXPAND TASK STATUS CONSTRAINT FOR ALL DOMAIN STATES
-- ============================================================

alter table public.tasks
    drop constraint if exists task_status_check;

alter table public.tasks
    add constraint task_status_check
    check (
        status in (
            'draft',
            'open',
            'scheduled',
            'upcoming',
            'action_required',
            'acknowledged',
            'in_progress',
            'pending_confirmation',
            'completed',
            'overdue',
            'cancelled',
            'archived'
        )
    );

-- ============================================================
-- 4. REPAIR TASK LAN STATUS RLS POLICY
-- ============================================================

drop policy if exists task_lan_status_select_policy on public.task_lan_status;
create policy task_lan_status_select_policy on public.task_lan_status
    for select
    to authenticated
    using (
        public.can_manage_task(task_id)
        or exists (
            select 1 from public.task_participants tp
            where tp.task_id = task_lan_status.task_id
              and tp.user_id = auth.uid()
              and tp.status = 'active'
        )
        or exists (
            select 1 from public.tasks t
            where t.id = task_lan_status.task_id
              and (
                  t.created_by = auth.uid()
                  or coalesce(t.creator_id, t.created_by) = auth.uid()
                  or t.assigned_user_id = auth.uid()
              )
        )
    );

-- ============================================================
-- 5. REPAIR SUBMIT_TASK_RESPONSE RPC
-- ============================================================

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
set search_path = ''
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
    v_lan_item text;
    v_deadline timestamptz;
begin
    -- 1. Verify User Authentication
    if v_user is null then
        raise exception 'Authentication required';
    end if;

    -- 2. Verify User Profile & Active Account Status
    select * into v_prof from public.profiles where id = v_user;
    if not found then
        raise exception 'User profile not found';
    end if;

    if not coalesce(v_prof.is_active, true) then
        raise exception 'User account is deactivated. Contact administrator.';
    end if;

    -- 3. Verify Task Exists
    select * into v_task from public.tasks where id = p_task_id;
    if not found then
        raise exception 'Task not found';
    end if;

    -- 4. Verify Task is Not Cancelled
    if v_task.status = 'cancelled' then
        raise exception 'Task has been cancelled';
    end if;

    -- 5. Verify Task Accepts Responses in Current State (Accept 'open' and all operational states)
    if v_task.status not in ('open', 'pending', 'in_progress', 'scheduled', 'upcoming', 'action_required', 'acknowledged', 'overdue', 'pending_confirmation') then
        raise exception 'Task does not accept responses in % state', v_task.status;
    end if;

    -- 6. Verify User is Authorized Task Participant or Creator
    if v_task.created_by = v_user
       or coalesce(v_task.creator_id, v_task.created_by) = v_user
       or v_task.assigned_user_id = v_user then
        v_is_authorized := true;
    else
        select exists (
            select 1 from public.task_participants
            where task_id = p_task_id
              and user_id = v_user
              and status = 'active'
        ) into v_is_authorized;
    end if;

    if not v_is_authorized and v_task.visibility = 'group' then
        select exists (
            select 1 from public.group_members
            where group_id = v_task.group_id
              and user_id = v_user
              and status = 'active'
        ) into v_is_authorized;
    end if;

    if not v_is_authorized then
        raise exception 'User is not authorized to submit responses for this task';
    end if;

    -- 7. Verify LAN Permission and Configuration
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

    -- 8. Validate Response Values
    if p_percentage is not null then
        if p_percentage < 0 or p_percentage > 100 then
            raise exception 'Percentage must be between 0 and 100';
        end if;
    end if;

    if p_text is not null and length(p_text) > 2000 then
        raise exception 'Text response exceeds maximum allowed length';
    end if;

    -- 9. Check Idempotency Key
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
                'message', 'Response already processed'
            );
        end if;
    end if;

    -- 10. Compute Overdue Status using coalesce(due_at, deadline_time)
    v_deadline := coalesce(v_task.due_at, v_task.deadline_time);
    if v_deadline is not null and now() > v_deadline then
        v_is_late := true;
        v_delay_sec := extract(epoch from (now() - v_deadline))::integer;
    elsif v_task.execution_time is not null and now() > (v_task.execution_time + interval '10 minutes') then
        v_is_late := true;
        v_delay_sec := extract(epoch from (now() - v_task.execution_time))::integer;
    end if;

    -- 11. Insert Authorized Task Response
    insert into public.task_responses (
        task_id,
        user_id,
        employee_id,
        employee_name,
        lan_number,
        response_type,
        response_value,
        percentage_value,
        numeric_value_1,
        numeric_value_2,
        text_value,
        idempotency_key,
        device_timestamp,
        is_late,
        delay_seconds,
        dynamic_values,
        submitted_at
    )
    values (
        p_task_id,
        v_user,
        v_prof.employee_id,
        v_prof.full_name,
        p_lan_number,
        p_response_type,
        p_response_value,
        p_percentage,
        p_numeric_1,
        p_numeric_2,
        p_text,
        p_idempotency_key,
        p_device_timestamp,
        v_is_late,
        v_delay_sec,
        coalesce(p_dynamic_values, '{}'::jsonb),
        now()
    )
    returning id into v_resp_id;

    -- 12. Handle Multi-LAN Tracking & Completion Policy
    if p_lan_number is not null and trim(p_lan_number) <> '' then
        insert into public.task_lan_status (
            task_id,
            lan_number,
            assigned_user_id,
            assigned_employee_id,
            assigned_employee_name,
            status,
            progress,
            last_response_time,
            completed_at,
            is_overdue
        )
        values (
            p_task_id,
            p_lan_number,
            v_user,
            v_prof.employee_id,
            v_prof.full_name,
            case
                when coalesce(p_percentage, 0) >= 100 or p_response_value in ('COMPLETED', 'YES') then 'completed'
                else 'in_progress'
            end,
            coalesce(p_percentage, case when p_response_value in ('COMPLETED', 'YES') then 100 else 0 end),
            now(),
            case when coalesce(p_percentage, 0) >= 100 or p_response_value in ('COMPLETED', 'YES') then now() else null end,
            v_is_late
        )
        on conflict (task_id, lan_number) do update
        set
            assigned_user_id = v_user,
            assigned_employee_id = v_prof.employee_id,
            assigned_employee_name = v_prof.full_name,
            status = case
                when coalesce(p_percentage, 0) >= 100 or p_response_value in ('COMPLETED', 'YES') then 'completed'
                else 'in_progress'
            end,
            progress = coalesce(p_percentage, case when p_response_value in ('COMPLETED', 'YES') then 100 else public.task_lan_status.progress end),
            last_response_time = now(),
            completed_at = case when coalesce(p_percentage, 0) >= 100 or p_response_value in ('COMPLETED', 'YES') then now() else public.task_lan_status.completed_at end,
            is_overdue = v_is_late,
            updated_at = now();
    end if;

    -- 13. Evaluate Overall Task Status Based on Completion Policy
    if v_task.completion_policy = 'ALL_SELECTED_LANS' and jsonb_array_length(coalesce(v_task.lan_target_list, '[]'::jsonb)) > 0 then
        v_total_lans := jsonb_array_length(v_task.lan_target_list);
        select count(*) into v_completed_lans
        from public.task_lan_status
        where task_id = p_task_id
          and status = 'completed';

        if v_completed_lans >= v_total_lans then
            v_all_lans_done := true;
        end if;

        if v_all_lans_done then
            update public.tasks
            set
                status = 'completed',
                current_progress = 100,
                last_progress_update = now(),
                updated_at = now()
            where id = p_task_id;
        else
            update public.tasks
            set
                status = 'in_progress',
                current_progress = case when v_total_lans > 0 then (v_completed_lans * 100 / v_total_lans) else current_progress end,
                last_progress_update = now(),
                updated_at = now()
            where id = p_task_id;
        end if;

    elsif v_task.completion_policy = 'CREATOR_CONFIRMATION' then
        if coalesce(p_percentage, 0) >= coalesce(v_task.target_percentage, 100) or p_response_value in ('COMPLETED', 'YES') then
            update public.tasks
            set
                status = 'pending_confirmation',
                current_progress = coalesce(p_percentage, 100),
                last_progress_update = now(),
                updated_at = now()
            where id = p_task_id;
        else
            update public.tasks
            set
                status = 'in_progress',
                current_progress = coalesce(p_percentage, current_progress),
                last_progress_update = now(),
                updated_at = now()
            where id = p_task_id;
        end if;

    else
        -- Standard SINGLE_ASSIGNEE
        if coalesce(p_percentage, 0) >= coalesce(v_task.target_percentage, 100) or p_response_value in ('COMPLETED', 'YES') then
            update public.tasks
            set
                status = 'completed',
                current_progress = coalesce(p_percentage, 100),
                last_progress_update = now(),
                updated_at = now()
            where id = p_task_id;
        else
            update public.tasks
            set
                status = 'in_progress',
                current_progress = coalesce(p_percentage, current_progress),
                last_progress_update = now(),
                updated_at = now()
            where id = p_task_id;
        end if;
    end if;

    -- Return JSON result
    select * into v_task from public.tasks where id = p_task_id;
    return jsonb_build_object(
        'success', true,
        'response_id', v_resp_id,
        'task_id', p_task_id,
        'task_status', v_task.status,
        'current_progress', v_task.current_progress,
        'is_late', v_is_late,
        'delay_seconds', v_delay_sec
    );
end;
$$;

revoke all on function public.submit_task_response from public;
grant execute on function public.submit_task_response to authenticated;

-- ============================================================
-- 6. REPAIR GET_TASK_OPERATIONS_DASHBOARD RPC
-- ============================================================

create or replace function public.get_task_operations_dashboard(p_task_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user uuid := (select auth.uid());
    v_task public.tasks%rowtype;
    v_total_lans integer := 0;
    v_completed_lans integer := 0;
    v_pending_lans integer := 0;
    v_overdue_lans integer := 0;
    v_total_parts integer := 0;
    v_acked_count integer := 0;
    v_lans jsonb := '[]'::jsonb;
    v_responses jsonb := '[]'::jsonb;
    v_lan_record record;
    v_resp_record record;
    v_deadline timestamptz;
begin
    if v_user is null then
        raise exception 'Authentication required';
    end if;

    select * into v_task from public.tasks where id = p_task_id;
    if not found then
        raise exception 'Task not found';
    end if;

    v_deadline := coalesce(v_task.due_at, v_task.deadline_time);

    -- Aggregate LAN statuses
    for v_lan_record in (
        select
            tls.lan_number,
            tls.status,
            tls.progress,
            tls.assigned_employee_name,
            tls.assigned_employee_id,
            tls.last_response_time,
            tls.completed_at,
            tls.is_overdue
        from public.task_lan_status tls
        where tls.task_id = p_task_id
        order by tls.lan_number
    )
    loop
        v_total_lans := v_total_lans + 1;
        if v_lan_record.status = 'completed' then
            v_completed_lans := v_completed_lans + 1;
        elsif v_lan_record.status in ('overdue') or v_lan_record.is_overdue then
            v_overdue_lans := v_overdue_lans + 1;
            v_pending_lans := v_pending_lans + 1;
        else
            v_pending_lans := v_pending_lans + 1;
        end if;

        v_lans := v_lans || jsonb_build_object(
            'lan_number', v_lan_record.lan_number,
            'status', v_lan_record.status,
            'progress', v_lan_record.progress,
            'employee_name', v_lan_record.assigned_employee_name,
            'employee_id', v_lan_record.assigned_employee_id,
            'last_response_time', v_lan_record.last_response_time,
            'completed_at', v_lan_record.completed_at,
            'is_overdue', v_lan_record.is_overdue
        );
    end loop;

    -- If no task_lan_status entries exist yet, populate from configured lan_target_list
    if v_total_lans = 0 and jsonb_array_length(coalesce(v_task.lan_target_list, '[]'::jsonb)) > 0 then
        for v_lan_record in (
            select elem as lan_number
            from jsonb_array_elements_text(v_task.lan_target_list) elem
            order by elem
        )
        loop
            v_total_lans := v_total_lans + 1;
            v_pending_lans := v_pending_lans + 1;
            v_lans := v_lans || jsonb_build_object(
                'lan_number', v_lan_record.lan_number,
                'status', 'pending',
                'progress', 0,
                'employee_name', null,
                'employee_id', null,
                'last_response_time', null,
                'completed_at', null,
                'is_overdue', (v_deadline is not null and now() > v_deadline)
            );
        end loop;
    end if;

    -- Aggregate Acknowledgements count
    select count(*) into v_acked_count
    from public.task_acknowledgements
    where task_id = p_task_id;

    -- Aggregate Participants count
    select count(*) into v_total_parts
    from public.task_participants
    where task_id = p_task_id
      and status = 'active';

    -- Recent responses (last 10)
    for v_resp_record in (
        select
            tr.id,
            tr.employee_id,
            tr.employee_name,
            tr.lan_number,
            tr.response_type,
            tr.response_value,
            tr.percentage_value,
            tr.submitted_at,
            tr.is_late,
            tr.delay_seconds
        from public.task_responses tr
        where tr.task_id = p_task_id
        order by tr.submitted_at desc
        limit 10
    )
    loop
        v_responses := v_responses || jsonb_build_object(
            'id', v_resp_record.id,
            'employee_id', v_resp_record.employee_id,
            'employee_name', v_resp_record.employee_name,
            'lan_number', v_resp_record.lan_number,
            'response_type', v_resp_record.response_type,
            'response_value', v_resp_record.response_value,
            'percentage', v_resp_record.percentage_value,
            'submitted_at', v_resp_record.submitted_at,
            'is_late', v_resp_record.is_late,
            'delay_seconds', v_resp_record.delay_seconds
        );
    end loop;

    return jsonb_build_object(
        'task_id', v_task.id,
        'title', v_task.title,
        'task_type', v_task.task_type,
        'importance', v_task.importance,
        'status', v_task.status,
        'completion_policy', v_task.completion_policy,
        'execution_time', v_task.execution_time,
        'due_at', v_task.due_at,
        'deadline_time', coalesce(v_task.due_at, v_task.deadline_time, v_task.execution_time),
        'current_progress', v_task.current_progress,
        'total_lans', v_total_lans,
        'completed_lans', v_completed_lans,
        'pending_lans', v_pending_lans,
        'overdue_lans', v_overdue_lans,
        'total_participants', v_total_parts,
        'acknowledged_count', v_acked_count,
        'lan_statuses', v_lans,
        'recent_responses', v_responses
    );
end;
$$;

revoke all on function public.get_task_operations_dashboard from public;
grant execute on function public.get_task_operations_dashboard to authenticated;

commit;
