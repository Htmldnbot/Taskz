-- ============================================================
-- TASKZ - MIGRATION 006: OPERATIONS MONITORING & DASHBOARD
-- Supabase PostgreSQL Migration
-- ============================================================

begin;

-- REAL-TIME OPERATIONS MONITORING RPC FOR TASK CREATOR & ADMINS
create or replace function public.get_task_operations_dashboard(
    p_task_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user uuid := (select auth.uid());
    v_task public.tasks%rowtype;
    v_lans jsonb := '[]'::jsonb;
    v_responses jsonb := '[]'::jsonb;
    v_total_lans integer := 0;
    v_completed_lans integer := 0;
    v_pending_lans integer := 0;
    v_overdue_lans integer := 0;
    v_total_parts integer := 0;
    v_acked_count integer := 0;
    v_lan_record record;
    v_resp_record record;
begin
    if v_user is null then
        raise exception 'Authentication required';
    end if;

    select * into v_task from public.tasks where id = p_task_id;
    if not found then
        raise exception 'Task not found';
    end if;

    -- Aggregate LAN statistics
    for v_lan_record in (
        select
            tls.lan_number,
            tls.status,
            tls.progress,
            tls.assigned_employee_id,
            tls.assigned_employee_name,
            tls.last_response_time,
            tls.completed_at,
            tls.is_overdue
        from public.task_lan_status tls
        where tls.task_id = p_task_id
        order by tls.lan_number asc
    )
    loop
        v_total_lans := v_total_lans + 1;
        if v_lan_record.status = 'completed' then
            v_completed_lans := v_completed_lans + 1;
        elsif v_lan_record.is_overdue or v_lan_record.status = 'overdue' then
            v_overdue_lans := v_overdue_lans + 1;
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

    -- If no records in task_lan_status yet but task has configured lan_target_list
    if v_total_lans = 0 and jsonb_array_length(coalesce(v_task.lan_target_list, '[]'::jsonb)) > 0 then
        for v_lan_record in select elem as lan_number from jsonb_array_elements_text(v_task.lan_target_list) elem
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
                'is_overdue', (v_task.deadline_time is not null and now() > v_task.deadline_time)
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
        'deadline_time', v_task.deadline_time,
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

revoke all on function public.get_task_operations_dashboard(uuid) from public;
grant execute on function public.get_task_operations_dashboard(uuid) to authenticated;

commit;
