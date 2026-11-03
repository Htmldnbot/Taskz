-- ============================================================
-- TASKZ — MIGRATION 010: BATCH 1 HARDENING & ATOMIC INTEGRITY
-- Forward-only migration repairing Batch 1 server persistence
-- ============================================================

-- 1. Ensure idempotency column and sync status on tasks
ALTER TABLE public.tasks ADD COLUMN IF NOT EXISTS client_operation_id text;
CREATE UNIQUE INDEX IF NOT EXISTS idx_tasks_client_operation_id
    ON public.tasks(client_operation_id)
    WHERE client_operation_id IS NOT NULL;

ALTER TABLE public.tasks ADD COLUMN IF NOT EXISTS sync_status text NOT NULL DEFAULT 'SYNCED';

-- 2. HARDENED ATOMIC TASK CREATION RPC (No exception swallowing)
CREATE OR REPLACE FUNCTION public.create_task_atomic(p_task jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth, pg_temp
AS $$
DECLARE
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
BEGIN
    -- 1. Authentication & Active User check
    IF v_caller IS NULL THEN
        RAISE EXCEPTION 'Authentication required to create tasks';
    END IF;

    IF NOT public.is_active_user() THEN
        RAISE EXCEPTION 'Active user account required to create tasks';
    END IF;

    -- 2. Idempotency Check via client_operation_id
    v_client_op_id := nullif(trim(p_task->>'client_operation_id'), '');
    IF v_client_op_id IS NOT NULL THEN
        SELECT * INTO v_existing_task
        FROM public.tasks
        WHERE client_operation_id = v_client_op_id;

        IF FOUND THEN
            RETURN jsonb_build_object(
                'success', true,
                'is_duplicate', true,
                'task_id', v_existing_task.id,
                'task', to_jsonb(v_existing_task)
            );
        END IF;
    END IF;

    -- 3. Extract & Validate Core Parameters
    v_task_id := coalesce(nullif(p_task->>'id', '')::uuid, gen_random_uuid());
    v_group_id := nullif(p_task->>'group_id', '')::uuid;
    v_shift_id := nullif(p_task->>'shift_id', '')::uuid;
    v_title := trim(coalesce(p_task->>'title', ''));

    IF length(v_title) < 1 THEN
        RAISE EXCEPTION 'Task title cannot be blank';
    END IF;

    -- Ensure valid group exists or fallback to user default operational group
    IF v_group_id IS NULL OR NOT EXISTS (SELECT 1 FROM public.task_groups WHERE id = v_group_id) THEN
        SELECT id INTO v_group_id
        FROM public.task_groups
        WHERE created_by = v_caller
        ORDER BY created_at DESC
        LIMIT 1;

        IF v_group_id IS NULL THEN
            INSERT INTO public.task_groups(
                title, code, description, status, visibility, allow_self_join, join_requires_approval, created_by
            ) VALUES (
                'General Operations', 'GEN-OPS', 'Default operational group', 'active', 'everyone', true, false, v_caller
            ) RETURNING id INTO v_group_id;

            INSERT INTO public.group_members(group_id, user_id, source, status, added_by)
            VALUES (v_group_id, v_caller, 'assigned', 'active', v_caller)
            ON CONFLICT DO NOTHING;
        END IF;
    END IF;

    -- Verify shift if specified
    IF v_shift_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM public.shifts WHERE id = v_shift_id) THEN
        RAISE EXCEPTION 'Specified shift % does not exist', v_shift_id;
    END IF;

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

    IF p_task ? 'response_form_schema' AND jsonb_typeof(p_task->'response_form_schema') = 'array' THEN
        v_form_schema := p_task->'response_form_schema';
    ELSE
        v_form_schema := '[]'::jsonb;
    END IF;

    -- 4. Insert Task record
    INSERT INTO public.tasks(
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
    ) VALUES (
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
    ) RETURNING * INTO v_created_task;

    SELECT full_name INTO v_caller_name
    FROM public.profiles
    WHERE id = v_caller;

    -- 5. Add Creator as active participant so task appears in creator's active list
    INSERT INTO public.task_participants(
        task_id,
        user_id,
        source,
        status,
        assigned_by
    ) VALUES (
        v_task_id,
        v_caller,
        'assigned',
        'active',
        v_caller
    ) ON CONFLICT (task_id, user_id) DO NOTHING;

    -- 6. Add Assigned Employees (STRICT VALIDATION: NO SILENT FAILING)
    IF p_task ? 'assigned_user_ids' AND jsonb_typeof(p_task->'assigned_user_ids') = 'array' THEN
        FOR v_assigned_user_id IN SELECT jsonb_array_elements_text(p_task->'assigned_user_ids')
        LOOP
            -- Check UUID format
            BEGIN
                v_assigned_uuid := v_assigned_user_id::uuid;
            EXCEPTION WHEN OTHERS THEN
                RAISE EXCEPTION 'Assigned employee identifier "%" is not a valid UUID', v_assigned_user_id;
            END;

            -- Check active profile exists
            SELECT is_active INTO v_is_active_emp
            FROM public.profiles
            WHERE id = v_assigned_uuid;

            IF v_is_active_emp IS NULL THEN
                RAISE EXCEPTION 'Assigned user "%" does not exist in profiles', v_assigned_user_id;
            END IF;

            IF NOT v_is_active_emp THEN
                RAISE EXCEPTION 'Assigned user "%" is marked inactive in profiles', v_assigned_user_id;
            END IF;

            -- Insert active participant
            INSERT INTO public.task_participants(
                task_id,
                user_id,
                source,
                status,
                assigned_by
            ) VALUES (
                v_task_id,
                v_assigned_uuid,
                'assigned',
                'active',
                v_caller
            ) ON CONFLICT (task_id, user_id) DO UPDATE SET status = 'active';

            -- In-app notification
            PERFORM public.create_notification(
                v_assigned_uuid,
                'task_assigned',
                'New task assigned: ' || v_title,
                coalesce(v_caller_name, 'Supervisor') || ' assigned you to task "' || v_title || '"',
                v_task_id,
                v_group_id
            );

            -- Restricted task visibility mapping
            IF v_visibility = 'restricted' THEN
                INSERT INTO public.task_visible_users(task_id, user_id, added_by)
                VALUES (v_task_id, v_assigned_uuid, v_caller)
                ON CONFLICT DO NOTHING;
            END IF;
        END LOOP;
    END IF;

    -- 7. Persist Role Requirements (STRICT VALIDATION: NO SILENT FAILING)
    IF p_task ? 'requirements' AND jsonb_typeof(p_task->'requirements') = 'array' THEN
        FOR v_req IN SELECT * FROM jsonb_array_elements(p_task->'requirements')
        LOOP
            BEGIN
                v_role_id := (v_req->>'role_id')::uuid;
            EXCEPTION WHEN OTHERS THEN
                RAISE EXCEPTION 'Required role ID "%" is not a valid UUID', (v_req->>'role_id');
            END;

            IF v_role_id IS NOT NULL THEN
                IF NOT EXISTS (SELECT 1 FROM public.work_roles WHERE id = v_role_id) THEN
                    RAISE EXCEPTION 'Required role "%" does not exist in work_roles', v_role_id;
                END IF;

                v_req_count := coalesce((v_req->>'required_count')::integer, (v_req->>'count')::integer, 1);
                IF v_req_count < 1 THEN
                    RAISE EXCEPTION 'Required count for role "%" must be at least 1', v_role_id;
                END IF;

                INSERT INTO public.task_role_requirements(
                    task_id,
                    role_id,
                    required_count
                ) VALUES (
                    v_task_id,
                    v_role_id,
                    v_req_count
                ) ON CONFLICT (task_id, role_id)
                DO UPDATE SET required_count = v_req_count;
            END IF;
        END LOOP;
    END IF;

    -- 8. Activity Log
    INSERT INTO public.task_activity_log(
        task_id,
        actor_user_id,
        event_type,
        message
    ) VALUES (
        v_task_id,
        v_caller,
        'TASK_CREATED',
        'Task created by ' || coalesce(v_caller_name, 'Creator') || ' (' || v_task_type || ', ' || v_importance || ')'
    );

    RETURN jsonb_build_object(
        'success', true,
        'task_id', v_task_id,
        'task', to_jsonb(v_created_task)
    );
END;
$$;

-- 3. HARDENED ASSIGN USER TO TASK RPC (Strict Validation)
CREATE OR REPLACE FUNCTION public.assign_user_to_task(
    p_task_id uuid,
    p_user_id uuid,
    p_role_id uuid DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth, pg_temp
AS $$
DECLARE
    v_caller uuid := auth.uid();
    v_caller_name text;
    v_task public.tasks%rowtype;
    v_is_active boolean;
    v_role_name text := NULL;
BEGIN
    IF v_caller IS NULL THEN
        RAISE EXCEPTION 'Authentication required to assign users';
    END IF;

    -- Check task exists
    SELECT * INTO v_task
    FROM public.tasks
    WHERE id = p_task_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Task % not found', p_task_id;
    END IF;

    -- Check authorization
    IF NOT public.can_manage_task(p_task_id) THEN
        RAISE EXCEPTION 'Not authorized to assign users to this task';
    END IF;

    -- Validate target user exists and is active
    SELECT is_active INTO v_is_active
    FROM public.profiles
    WHERE id = p_user_id;

    IF v_is_active IS NULL THEN
        RAISE EXCEPTION 'Target user % does not exist in profiles', p_user_id;
    END IF;

    IF NOT v_is_active THEN
        RAISE EXCEPTION 'Target user % is inactive', p_user_id;
    END IF;

    -- Validate role if provided
    IF p_role_id IS NOT NULL THEN
        SELECT name INTO v_role_name
        FROM public.work_roles
        WHERE id = p_role_id;

        IF v_role_name IS NULL THEN
            RAISE EXCEPTION 'Specified role % does not exist in work_roles', p_role_id;
        END IF;
    END IF;

    SELECT full_name INTO v_caller_name
    FROM public.profiles
    WHERE id = v_caller;

    -- Insert or update participant
    INSERT INTO public.task_participants(
        task_id,
        user_id,
        role_id,
        source,
        status,
        assigned_by,
        joined_at
    ) VALUES (
        p_task_id,
        p_user_id,
        p_role_id,
        'assigned',
        'active',
        v_caller,
        now()
    ) ON CONFLICT (task_id, user_id)
    DO UPDATE SET
        role_id = coalesce(excluded.role_id, task_participants.role_id),
        status = 'active',
        left_at = NULL,
        left_reason = NULL;

    -- Notify user
    PERFORM public.create_notification(
        p_user_id,
        'task_assigned',
        'Assigned to task: ' || v_task.title,
        coalesce(v_caller_name, 'Supervisor') || ' assigned you to task "' || v_task.title || '"',
        p_task_id,
        v_task.group_id
    );

    -- Restricted visibility mapping
    IF v_task.visibility = 'restricted' THEN
        INSERT INTO public.task_visible_users(task_id, user_id, added_by)
        VALUES (p_task_id, p_user_id, v_caller)
        ON CONFLICT DO NOTHING;
    END IF;

    -- Activity log
    INSERT INTO public.task_activity_log(
        task_id,
        actor_user_id,
        event_type,
        message
    ) VALUES (
        p_task_id,
        v_caller,
        'USER_ASSIGNED',
        coalesce(v_caller_name, 'Supervisor') || ' assigned user to task'
    );

    RETURN jsonb_build_object(
        'success', true,
        'task_id', p_task_id,
        'user_id', p_user_id,
        'role_id', p_role_id
    );
END;
$$;

-- 4. GRANT PROPER RLS AND ACCESS PERMISSIONS
GRANT SELECT, INSERT, UPDATE ON public.task_groups TO authenticated;
GRANT SELECT, INSERT, UPDATE ON public.shifts TO authenticated;
GRANT SELECT, INSERT, UPDATE ON public.tasks TO authenticated;
GRANT SELECT, INSERT, UPDATE ON public.group_members TO authenticated;
GRANT SELECT, INSERT, UPDATE ON public.task_participants TO authenticated;
GRANT SELECT, INSERT, UPDATE ON public.task_role_requirements TO authenticated;
GRANT SELECT, INSERT, UPDATE ON public.task_join_requests TO authenticated;

GRANT EXECUTE ON FUNCTION public.create_task_atomic(jsonb) TO authenticated;
GRANT EXECUTE ON FUNCTION public.assign_user_to_task(uuid, uuid, uuid) TO authenticated;
