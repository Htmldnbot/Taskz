-- ============================================================
-- TASKZ - PHASE 2 TASK ENGINE EXTENSIONS
-- Supabase PostgreSQL
-- ============================================================

begin;

-- ============================================================
-- 1. REMOVE TASK PARTICIPANT (Creator/Admin)
-- ============================================================

create or replace function public.remove_task_participant(
    p_participant uuid,
    p_reason text default null
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_part public.task_participants%rowtype;
    v_task public.tasks%rowtype;
    v_caller uuid := (select auth.uid());
begin
    select *
    into v_part
    from public.task_participants
    where id = p_participant
      and status = 'active';

    if not found then
        raise exception 'Active participant record not found';
    end if;

    select *
    into v_task
    from public.tasks
    where id = v_part.task_id;

    if not public.can_manage_task(v_part.task_id) then
        raise exception 'Unauthorized to manage task participants';
    end if;

    update public.task_participants
    set
        status = 'removed',
        left_at = now(),
        left_reason = nullif(trim(p_reason), ''),
        removed_by = v_caller
    where id = p_participant;

    insert into public.task_activity_log(
        task_id,
        actor_user_id,
        target_user_id,
        event_type,
        message
    )
    values(
        v_part.task_id,
        v_caller,
        v_part.user_id,
        'USER_REMOVED',
        coalesce(nullif(trim(p_reason), ''), 'Participant removed from task by manager')
    );

    perform public.create_notification(
        v_part.user_id,
        'task_member_removed',
        'Removed from task',
        'You have been removed from the task by the administrator.',
        v_part.task_id,
        v_task.group_id
    );

    return true;
end;
$$;

revoke all on function public.remove_task_participant(uuid,text) from public;
grant execute on function public.remove_task_participant(uuid,text) to authenticated;

-- ============================================================
-- 2. DASHBOARD SUMMARY STATS RPC
-- ============================================================

create or replace function public.get_home_dashboard_stats()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user uuid := (select auth.uid());
    v_my_tasks_today integer := 0;
    v_upcoming_tasks integer := 0;
    v_pending_requests integer := 0;
    v_vacancies integer := 0;
begin
    if not public.is_active_user() then
        return jsonb_build_object(
            'my_tasks_today', 0,
            'upcoming_tasks', 0,
            'pending_requests', 0,
            'vacancies', 0
        );
    end if;

    -- My tasks today (active participant on tasks scheduled today)
    select count(*)
    into v_my_tasks_today
    from public.task_participants tp
    join public.tasks t on t.id = tp.task_id
    where tp.user_id = v_user
      and tp.status = 'active'
      and t.status not in ('completed', 'cancelled')
      and (
          t.starts_at is null
          or (t.starts_at >= current_date and t.starts_at < current_date + interval '1 day')
          or (t.due_at >= current_date and t.due_at < current_date + interval '1 day')
      );

    -- Upcoming tasks
    select count(*)
    into v_upcoming_tasks
    from public.task_participants tp
    join public.tasks t on t.id = tp.task_id
    where tp.user_id = v_user
      and tp.status = 'active'
      and t.status not in ('completed', 'cancelled')
      and (t.starts_at > now() or t.due_at > now());

    -- Pending join requests on tasks created by the user (or admin)
    select count(*)
    into v_pending_requests
    from public.task_join_requests jr
    join public.tasks t on t.id = jr.task_id
    where jr.status = 'pending'
      and (t.created_by = v_user or public.is_admin());

    -- Vacancies in tasks created by user
    select coalesce(sum(greatest(0, tr.required_count - (
        select count(*)
        from public.task_participants tp
        where tp.task_id = tr.task_id
          and tp.role_id = tr.role_id
          and tp.status = 'active'
    ))), 0)
    into v_vacancies
    from public.task_role_requirements tr
    join public.tasks t on t.id = tr.task_id
    where t.status in ('open', 'in_progress')
      and (t.created_by = v_user or public.is_admin());

    return jsonb_build_object(
        'my_tasks_today', v_my_tasks_today,
        'upcoming_tasks', v_upcoming_tasks,
        'pending_requests', v_pending_requests,
        'vacancies', v_vacancies
    );
end;
$$;

revoke all on function public.get_home_dashboard_stats() from public;
grant execute on function public.get_home_dashboard_stats() to authenticated;

commit;
