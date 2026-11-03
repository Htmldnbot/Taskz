-- ============================================================
-- TASKZ - MIGRATION 009: BATCH 1 MASTER PRODUCTION REPAIR
-- Real Server-Backed Task Creation, Groups, Shifts & Multi-User Sync
-- ============================================================

begin;

-- ============================================================
-- 1. EXTEND TASKS TABLE FOR IDEMPOTENCY & SYNC STATUS
-- ============================================================

alter table public.tasks
    add column if not exists client_operation_id text unique,
    add column if not exists sync_status text not null default 'SYNCED';

create index if not exists idx_tasks_client_operation_id
    on public.tasks(client_operation_id);

-- ============================================================
-- 2. ATOMIC TASK CREATION RPC (WITH IDEMPOTENCY & ROBUST PERMISSIONS)
-- ============================================================

create or replace function public.create_task_atomic(
    p_task jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_caller uuid := (select auth.uid());
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
begin
    -- 1. Authorization check
    if v_caller is null then
        raise exception 'Authentication required to create tasks';
    end if;

    if not public.is_active_user() then
        raise exception 'Active user account required to create tasks';
    end if;

    -- 2. Idempotency Check via client_operation_id
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

    -- 3. Extract & Validate Parameters
    v_task_id := coalesce(nullif(p_task->>'id', '')::uuid, gen_random_uuid());
    v_group_id := (p_task->>'group_id')::uuid;
    v_shift_id := nullif(p_task->>'shift_id', '')::uuid;
    v_title := trim(coalesce(p_task->>'title', ''));

    if length(v_title) < 1 then
        raise exception 'Task title cannot be blank';
    end if;

    -- Guarantee group exists or fallback to user default group
    if v_group_id is null or not exists (select 1 from public.task_groups where id = v_group_id) then
        select id into v_group_id
        from public.task_groups
        where created_by = v_caller
        order by created_at desc
        limit 1;

        if v_group_id is null then
            insert into public.task_groups(
                title,
                code,
                description,
                status,
                visibility,
                allow_self_join,
                join_requires_approval,
                created_by
            ) values (
                'General Operations',
                'GEN-OPS',
                'Default operational group',
                'active',
                'everyone',
                true,
                false,
                v_caller
            )
            returning id into v_group_id;

            insert into public.group_members(group_id, user_id, source, status, added_by)
            values (v_group_id, v_caller, 'assigned', 'active', v_caller)
            on conflict do nothing;
        end if;
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

    -- 4. Insert Task record
    insert into public.tasks(
        id,
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
        creator_id,
        deadline_time,
        task_type,
        importance,
        urgency_type,
        execution_time,
        target_percentage,
        repeat_interval_minutes,
        alarm_type,
        advance_reminders_minutes,
        remind_until_completed,
        overdue_reminder_interval_minutes,
        max_overdue_reminders,
        response_type,
        lan_target_list,
        completion_policy,
        response_form_schema,
        client_operation_id,
        sync_status
    ) values (
        v_task_id,
        v_group_id,
        v_shift_id,
        v_title,
        v_desc,
        v_location,
        v_starts_at,
        v_due_at,
        v_priority,
        'open',
        v_visibility,
        v_allow_self_join,
        v_join_requires_approval,
        v_notify_creator_on_join,
        v_requires_completion_confirmation,
        v_max_participants,
        v_caller,
        v_caller,
        v_due_at,
        v_task_type,
        v_importance,
        v_urgency_type,
        v_execution_time,
        v_target_percentage,
        v_repeat_interval_minutes,
        v_alarm_type,
        v_advance_reminders,
        v_remind_until_completed,
        v_overdue_reminder_interval,
        v_max_overdue_reminders,
        v_response_type,
        v_lan_target_list,
        v_completion_policy,
        v_form_schema,
        v_client_op_id,
        'SYNCED'
    )
    returning * into v_created_task;

    select full_name into v_caller_name
    from public.profiles
    where id = v_caller;

    -- 5. Add Creator as active participant so task is in creator's active list
    insert into public.task_participants(
        task_id,
        user_id,
        source,
        status,
        assigned_by
    ) values (
        v_task_id,
        v_caller,
        'assigned',
        'active',
        v_caller
    ) on conflict (task_id, user_id) do nothing;

    -- 6. Add Assigned Employees if provided
    if p_task ? 'assigned_user_ids' and jsonb_typeof(p_task->'assigned_user_ids') = 'array' then
        for v_assigned_user_id in select jsonb_array_elements_text(p_task->'assigned_user_ids')
        loop
            begin
                v_assigned_uuid := v_assigned_user_id::uuid;
                if v_assigned_uuid <> v_caller and exists (select 1 from public.profiles where id = v_assigned_uuid and is_active = true) then
                    insert into public.task_participants(
                        task_id,
                        user_id,
                        source,
                        status,
                        assigned_by
                    ) values (
                        v_task_id,
                        v_assigned_uuid,
                        'assigned',
                        'active',
                        v_caller
                    ) on conflict (task_id, user_id) do update set status = 'active';

                    -- Notification for assigned employee
                    perform public.create_notification(
                        v_assigned_uuid,
                        'task_assigned',
                        'New task assigned: ' || v_title,
                        coalesce(v_caller_name, 'Supervisor') || ' assigned you to task "' || v_title || '"',
                        v_task_id,
                        v_group_id
                    );

                    -- If task visibility is restricted, ensure user is visible
                    if v_visibility = 'restricted' then
                        insert into public.task_visible_users(task_id, user_id, added_by)
                        values (v_task_id, v_assigned_uuid, v_caller)
                        on conflict do nothing;
                    end if;
                end if;
            exception when others then
                -- Skip individual invalid assignment without failing entire task creation
            end;
        end loop;
    end if;

    -- 7. Persist Role Requirements if provided
    if p_task ? 'requirements' and jsonb_typeof(p_task->'requirements') = 'array' then
        for v_req in select * from jsonb_array_elements(p_task->'requirements')
        loop
            begin
                v_role_id := (v_req->>'role_id')::uuid;
                v_req_count := coalesce((v_req->>'required_count')::integer, (v_req->>'count')::integer, 1);
                if v_role_id is not null and v_req_count > 0 then
                    insert into public.task_role_requirements(
                        task_id,
                        role_id,
                        required_count
                    ) values (
                        v_task_id,
                        v_role_id,
                        v_req_count
                    ) on conflict (task_id, role_id)
                    do update set required_count = v_req_count;
                end if;
            exception when others then
                -- Ignore requirement error
            end;
        end loop;
    end if;

    -- 8. Activity Log Entry
    insert into public.task_activity_log(
        task_id,
        actor_user_id,
        event_type,
        message
    ) values (
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

-- ============================================================
-- 3. ENHANCED SHIFT COPY (PRESERVING TIME-CRITICAL CONFIGURATIONS & OFFSETS)
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
    v_caller uuid := (select auth.uid());
begin
    select *
    into v_source
    from public.shifts
    where id = p_source_shift;

    if not found then
        raise exception 'Source shift not found';
    end if;

    if not public.can_manage_group(v_source.group_id) then
        raise exception 'Unauthorized to manage shifts in this group';
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
        (coalesce(v_source.shift_number, 0) + 1),
        v_source.starts_at + make_interval(mins => p_offset_minutes),
        v_source.ends_at + make_interval(mins => p_offset_minutes),
        'Copied from ' || v_source.name,
        'scheduled',
        v_source.id,
        v_caller
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
            creator_id,
            deadline_time,
            copied_from_task_id,
            task_type,
            importance,
            urgency_type,
            execution_time,
            target_percentage,
            repeat_interval_minutes,
            alarm_type,
            advance_reminders_minutes,
            remind_until_completed,
            overdue_reminder_interval_minutes,
            max_overdue_reminders,
            response_type,
            lan_target_list,
            completion_policy,
            response_form_schema
        )
        values(
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
        )
        returning id
        into v_new_task;

        -- Copy Manpower Requirements
        if p_copy_manpower then
            insert into public.task_role_requirements(task_id, role_id, required_count, notes)
            select v_new_task, role_id, required_count, notes
            from public.task_role_requirements
            where task_id = v_old_task.id;
        end if;

        -- Copy Assigned Employees (Default false)
        if p_copy_assignments then
            insert into public.task_participants(task_id, user_id, role_id, source, status, assigned_by)
            select v_new_task, user_id, role_id, 'copied_assignment', 'active', v_caller
            from public.task_participants
            where task_id = v_old_task.id and status = 'active';
        else
            -- Ensure creator is active participant
            insert into public.task_participants(task_id, user_id, source, status, assigned_by)
            values (v_new_task, v_caller, 'assigned', 'active', v_caller)
            on conflict do nothing;
        end if;
    end loop;

    return v_new_shift;
end;
$$;

revoke all on function public.copy_shift(uuid,text,integer,boolean,boolean) from public;
grant execute on function public.copy_shift(uuid,text,integer,boolean,boolean) to authenticated;

-- ============================================================
-- 4. PERMISSIONS & RLS GRANTS FOR GROUPS, SHIFTS, PARTICIPANTS
-- ============================================================

grant select, insert, update on public.task_groups to authenticated;
grant select, insert, update on public.shifts to authenticated;
grant select, insert, update on public.group_members to authenticated;
grant select, insert, update on public.task_participants to authenticated;
grant select, insert, update on public.task_role_requirements to authenticated;
grant select, insert, update on public.task_join_requests to authenticated;

commit;
