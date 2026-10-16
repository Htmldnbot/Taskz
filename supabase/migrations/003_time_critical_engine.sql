-- ============================================================
-- TASKZ - PHASE 3: TIME-CRITICAL TASK ENGINE & ALARMS
-- Supabase PostgreSQL Migration
-- ============================================================

begin;

-- 1. EXTEND TASKS TABLE WITH TIME-CRITICAL ATTRIBUTES
alter table public.tasks
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
    add column if not exists acknowledged_at timestamptz null;

-- 2. CREATE TASK RESPONSES TABLE (For Multi-Confirmation, Progress, LAN-Wise, Numeric & Responses)
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
    idempotency_key text null unique
);

create index if not exists idx_task_responses_task_id on public.task_responses(task_id);
create index if not exists idx_task_responses_user_id on public.task_responses(user_id);
create index if not exists idx_task_responses_lan on public.task_responses(task_id, lan_number);

-- 3. CREATE TASK ACKNOWLEDGEMENTS TABLE
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

-- 4. ENABLE RLS
alter table public.task_responses enable row level security;
alter table public.task_acknowledgements enable row level security;

-- 5. RLS POLICIES FOR TASK RESPONSES
drop policy if exists task_responses_select_policy on public.task_responses;
create policy task_responses_select_policy on public.task_responses
    for select
    to authenticated
    using (
        user_id = auth.uid()
        or public.can_manage_task(task_id)
        or exists (
            select 1 from public.task_participants tp
            where tp.task_id = task_responses.task_id
              and tp.user_id = auth.uid()
              and tp.status = 'active'
        )
    );

drop policy if exists task_responses_insert_policy on public.task_responses;
create policy task_responses_insert_policy on public.task_responses
    for insert
    to authenticated
    with check (
        user_id = auth.uid()
    );

-- 6. RLS POLICIES FOR TASK ACKNOWLEDGEMENTS
drop policy if exists task_ack_select_policy on public.task_acknowledgements;
create policy task_ack_select_policy on public.task_acknowledgements
    for select
    to authenticated
    using (
        user_id = auth.uid()
        or public.can_manage_task(task_id)
    );

drop policy if exists task_ack_insert_policy on public.task_acknowledgements;
create policy task_ack_insert_policy on public.task_acknowledgements
    for insert
    to authenticated
    with check (
        user_id = auth.uid()
    );

-- 7. SUBMIT TASK PROGRESS / RESPONSE RPC
create or replace function public.submit_task_response(
    p_task_id uuid,
    p_response_type text,
    p_response_value text default null,
    p_percentage integer default null,
    p_numeric_1 numeric default null,
    p_numeric_2 numeric default null,
    p_text text default null,
    p_lan_number text default null,
    p_idempotency_key text default null
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
begin
    if v_user is null then
        raise exception 'Authentication required';
    end if;

    select * into v_prof from public.profiles where id = v_user;
    select * into v_task from public.tasks where id = p_task_id;

    if not found then
        raise exception 'Task not found';
    end if;

    -- Insert response
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
        idempotency_key
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
        p_idempotency_key
    )
    returning id into v_resp_id;

    -- If this is a progress task, update current_progress on tasks table
    if p_percentage is not null then
        update public.tasks
        set
            current_progress = p_percentage,
            last_progress_update = now(),
            status = case when p_percentage >= coalesce(v_task.target_percentage, 100) then 'completed' else 'in_progress' end
        where id = p_task_id;
    end if;

    -- Add activity log entry
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
        coalesce(v_prof.full_name, v_prof.employee_id) || ' submitted response: ' || coalesce(p_response_value, p_percentage::text, p_text, 'Confirmed')
    );

    return jsonb_build_object(
        'success', true,
        'response_id', v_resp_id,
        'task_id', p_task_id
    );
end;
$$;

revoke all on function public.submit_task_response(uuid,text,text,integer,numeric,numeric,text,text,text) from public;
grant execute on function public.submit_task_response(uuid,text,text,integer,numeric,numeric,text,text,text) to authenticated;

-- 8. ACKNOWLEDGE ALARM RPC
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
begin
    if v_user is null then
        raise exception 'Authentication required';
    end if;

    select * into v_prof from public.profiles where id = v_user;

    insert into public.task_acknowledgements (
        task_id,
        user_id,
        employee_id,
        event_id,
        device_timestamp
    )
    values (
        p_task_id,
        v_user,
        v_prof.employee_id,
        p_event_id,
        p_device_timestamp
    )
    on conflict (event_id) do nothing;

    -- Log acknowledgement
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
