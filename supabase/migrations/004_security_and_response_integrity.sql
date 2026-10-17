-- ============================================================
-- TASKZ - MIGRATION 004: SECURITY AND RESPONSE INTEGRITY
-- Supabase PostgreSQL Migration
-- ============================================================

begin;

-- 1. ADD COMPLETION POLICY AND DYNAMIC SCHEMA COLUMNS TO TASKS
alter table public.tasks
    add column if not exists completion_policy text not null default 'SINGLE_ASSIGNEE',
    add column if not exists response_form_schema jsonb not null default '[]'::jsonb;

-- 2. EXTEND TASK RESPONSES TABLE WITH TIMESTAMPS AND DYNAMIC VALUES
alter table public.task_responses
    add column if not exists dynamic_values jsonb null default '{}'::jsonb,
    add column if not exists device_timestamp timestamptz null,
    add column if not exists is_late boolean not null default false,
    add column if not exists delay_seconds integer not null default 0;

-- 3. CREATE TASK LAN STATUS TABLE FOR INDEPENDENT LAN TRACKING
create table if not exists public.task_lan_status (
    id uuid primary key default gen_random_uuid(),
    task_id uuid not null references public.tasks(id) on delete cascade,
    lan_number text not null,
    assigned_user_id uuid references public.profiles(id) on delete set null,
    assigned_employee_id text null,
    assigned_employee_name text null,
    status text not null default 'pending', -- pending, in_progress, completed, overdue
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

-- Enable RLS on task_lan_status
alter table public.task_lan_status enable row level security;

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
              and (t.creator_id = auth.uid() or t.assigned_user_id = auth.uid())
        )
    );

-- 4. SECURE, AUTHORIZED & IDEMPOTENT TASK RESPONSE SUBMISSION RPC
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

    -- 5. Verify Task Accepts Responses in Current State
    if v_task.status not in ('pending', 'in_progress', 'scheduled', 'overdue', 'pending_confirmation') then
        raise exception 'Task does not accept responses in % state', v_task.status;
    end if;

    -- 6. Verify User is Authorized Task Participant
    if v_task.creator_id = v_user or v_task.assigned_user_id = v_user then
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
        -- Also check group membership if task is open within group
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
            -- Return existing response without duplicate insert or duplicate activity log
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

    -- 10. Compute Overdue Status
    if v_task.deadline_time is not null and now() > v_task.deadline_time then
        v_is_late := true;
        v_delay_sec := extract(epoch from (now() - v_task.deadline_time))::integer;
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

    -- 12. Handle Multi-LAN Tracking & Completion Policy (PART 2)
    if p_lan_number is not null and trim(p_lan_number) <> '' then
        -- Update or insert individual LAN status
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
            completed_at = case
                when coalesce(p_percentage, 0) >= 100 or p_response_value in ('COMPLETED', 'YES') then now()
                else public.task_lan_status.completed_at
            end,
            is_overdue = v_is_late,
            updated_at = now();

        -- Check Multi-LAN completion policy
        v_total_lans := jsonb_array_length(coalesce(v_task.lan_target_list, '[]'::jsonb));
        if v_total_lans > 0 then
            select count(*) into v_completed_lans
            from public.task_lan_status
            where task_id = p_task_id
              and status = 'completed';

            if v_completed_lans >= v_total_lans then
                v_all_lans_done := true;
            end if;
        end if;
    end if;

    -- 13. Update Task-Level Progress & Status based on Policy
    if v_task.task_type = 'PROGRESS' and (p_lan_number is null or trim(p_lan_number) = '') then
        -- Single progress task update
        update public.tasks
        set
            current_progress = coalesce(p_percentage, current_progress),
            last_progress_update = now(),
            status = case
                when coalesce(p_percentage, 0) >= coalesce(v_task.target_percentage, 100) then
                    case when v_task.completion_policy = 'CREATOR_CONFIRMATION' then 'pending_confirmation' else 'completed' end
                else 'in_progress'
            end
        where id = p_task_id;
    elsif v_task.task_type = 'MULTI_CONFIRMATION' or (p_lan_number is not null and trim(p_lan_number) <> '') then
        -- Multi-LAN / Multi-confirmation task: ONLY mark complete if policy criteria met!
        if v_all_lans_done then
            update public.tasks
            set
                status = case when v_task.completion_policy = 'CREATOR_CONFIRMATION' then 'pending_confirmation' else 'completed' end,
                current_progress = 100,
                last_progress_update = now()
            where id = p_task_id;
        else
            update public.tasks
            set
                status = 'in_progress',
                last_progress_update = now(),
                current_progress = case when v_total_lans > 0 then (v_completed_lans * 100 / v_total_lans) else current_progress end
            where id = p_task_id;
        end if;
    else
        -- Standard / Simple task completion
        if p_response_value in ('COMPLETED', 'YES') then
            update public.tasks
            set
                status = case when v_task.completion_policy = 'CREATOR_CONFIRMATION' then 'pending_confirmation' else 'completed' end,
                last_progress_update = now()
            where id = p_task_id;
        end if;
    end if;

    -- Refresh updated task status
    select status into v_task.status from public.tasks where id = p_task_id;

    -- 14. Activity Log (Fresh Submission Only)
    insert into public.task_activity_log(
        task_id,
        actor_user_id,
        event_type,
        message
    )
    values (
        p_task_id,
        v_user,
        'RESPONSE_SUBMITTED',
        coalesce(v_prof.full_name, v_prof.employee_id) ||
        case when p_lan_number is not null and trim(p_lan_number) <> '' then ' (' || p_lan_number || ')' else '' end ||
        ' submitted response: ' || coalesce(p_response_value, p_percentage::text || '%', p_text, 'Confirmed') ||
        case when v_is_late then ' [LATE BY ' || (v_delay_sec / 60)::text || ' min]' else '' end
    );

    return jsonb_build_object(
        'success', true,
        'response_id', v_resp_id,
        'task_id', p_task_id,
        'is_duplicate', false,
        'task_status', v_task.status,
        'is_late', v_is_late,
        'delay_seconds', v_delay_sec,
        'all_lans_completed', v_all_lans_done
    );
end;
$$;

revoke all on function public.submit_task_response(uuid,text,text,integer,numeric,numeric,text,text,text,timestamptz,jsonb) from public;
grant execute on function public.submit_task_response(uuid,text,text,integer,numeric,numeric,text,text,text,timestamptz,jsonb) to authenticated;

-- 5. SECURE, IDEMPOTENT ALARM ACKNOWLEDGEMENT RPC
create or replace function public.acknowledge_task_alarm(
    p_task_id uuid,
    p_event_id text,
    p_device_timestamp timestamptz
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user uuid := (select auth.uid());
    v_prof public.profiles%rowtype;
    v_task public.tasks%rowtype;
    v_already_acked boolean := false;
begin
    -- 1. Check Authentication
    if v_user is null then
        raise exception 'Authentication required';
    end if;

    -- 2. Check Active Profile
    select * into v_prof from public.profiles where id = v_user;
    if not found or not coalesce(v_prof.is_active, true) then
        raise exception 'Active profile required to acknowledge alarm';
    end if;

    -- 3. Check Task Exists
    select * into v_task from public.tasks where id = p_task_id;
    if not found then
        raise exception 'Task not found';
    end if;

    -- 4. Check Idempotency
    select exists (
        select 1 from public.task_acknowledgements
        where event_id = p_event_id
    ) into v_already_acked;

    if v_already_acked then
        -- Return true immediately without duplicate activity log
        return true;
    end if;

    -- 5. Insert Acknowledgement
    insert into public.task_acknowledgements (
        task_id,
        user_id,
        employee_id,
        event_id,
        device_timestamp,
        acknowledged_at
    )
    values (
        p_task_id,
        v_user,
        v_prof.employee_id,
        p_event_id,
        p_device_timestamp,
        now()
    );

    -- 6. Log Acknowledgement Once
    insert into public.task_activity_log(
        task_id,
        actor_user_id,
        event_type,
        message
    )
    values (
        p_task_id,
        v_user,
        'ALARM_ACKNOWLEDGED',
        coalesce(v_prof.full_name, v_prof.employee_id) || ' acknowledged alarm'
    );

    return true;
end;
$$;

revoke all on function public.acknowledge_task_alarm(uuid,text,timestamptz) from public;
grant execute on function public.acknowledge_task_alarm(uuid,text,timestamptz) to authenticated;

commit;
