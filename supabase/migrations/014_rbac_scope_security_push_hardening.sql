-- ============================================================================
-- Taskz Migration 014: Security, Scope, Actionability, Push Queue & Sync Hardening
-- Forward-only, idempotent migration. Fixes:
--   1. Fail-closed org scope checks (eliminates ALL NULL = wildcard bypasses).
--   2. Task actionability boundary (can_act_on_task vs oversight can_view_task).
--   3. Unbounded batched task & incident notification propagation (no 250/100 cap).
--   4. Immutable incident scope/creator guard trigger + sequential escalation chain.
--   5. Conservative org scope backfill + needs_scope_review tracking.
--   6. Scoped dashboard counts & fixed delta_sync_pull subqueries + deleted IDs.
--   7. Durable push_dispatch_queue + notification insert trigger + optional pg_net.
--   8. Rejects fake 'local:*' device tokens + performance indexes & RLS hygiene.
-- ============================================================================

begin;

-- ----------------------------------------------------------------------------
-- 1. CONSERVATIVE PROFILE SCOPE REVIEW TRACKING & BACKFILL
-- ----------------------------------------------------------------------------
alter table public.profiles
    add column if not exists needs_scope_review boolean not null default false;

alter table public.system_incident_audit
    add column if not exists changed_by_name text;

alter table public.client_events
    add column if not exists client_operation_id text,
    add column if not exists target_id uuid;

create unique index if not exists idx_client_events_client_operation_uniq
    on public.client_events(client_operation_id);

-- Ensure profile guard permits migration/SQL-editor context before profile scope backfill
create or replace function public.protect_profile_rbac_columns()
returns trigger
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
begin
    if current_setting('taskz.bypass_rbac_guard', true) = '1'
       or current_setting('taskz.bypass_profile_guard', true) = '1' then
        return new;
    end if;

    if coalesce(auth.role(), '') = 'service_role'
       or (session_user in ('postgres', 'supabase_admin', 'supabase_auth_admin') and nullif(current_setting('request.jwt.claims', true), '') is null)
       or public.is_super_admin() then
        return new;
    end if;

    if new.app_role is distinct from old.app_role
       or new.primary_role_code is distinct from old.primary_role_code
       or new.zone_id is distinct from old.zone_id
       or new.state_id is distinct from old.state_id
       or new.city_id is distinct from old.city_id
       or new.centre_id is distinct from old.centre_id
       or new.is_active is distinct from old.is_active
       or new.employee_id is distinct from old.employee_id then
        raise exception 'Forbidden: only Super Admin can modify role, scope, employee_id, or active status';
    end if;

    if new.active_role_code is distinct from old.active_role_code then
        if not exists (
            select 1 from public.user_roles ur
            where ur.user_id = old.id and ur.role_code = upper(new.active_role_code)
        ) then
            raise exception 'Forbidden: role % is not assigned to user', new.active_role_code;
        end if;
    end if;

    return new;
end;
$$;

select set_config('taskz.bypass_rbac_guard', '1', true);

-- Conservatively populate missing hierarchy IDs ONLY when profiles.centre_code
-- unambiguously matches an active org_centres code/external_code.
update public.profiles p
set
    centre_id = oc.id,
    centre_code = coalesce(nullif(trim(p.centre_code), ''), oc.code),
    city_id = oc.city_id,
    state_id = oc.state_id,
    zone_id = oc.zone_id,
    city_name = coalesce(p.city_name, ct.name),
    state_name = coalesce(p.state_name, st.name),
    zone_name = coalesce(p.zone_name, zn.name)
from public.org_centres oc
join public.org_cities ct on ct.id = oc.city_id
join public.org_states st on st.id = oc.state_id
join public.org_zones zn on zn.id = oc.zone_id
where p.centre_id is null
  and p.centre_code is not null
  and trim(p.centre_code) <> ''
  and (
      upper(oc.code) = upper(trim(p.centre_code))
      or upper(coalesce(oc.external_code, '')) = upper(trim(p.centre_code))
  );

-- Derive missing parent city/state/zone when child org ID exists
update public.profiles p
set
    city_id = coalesce(p.city_id, oc.city_id),
    state_id = coalesce(p.state_id, oc.state_id),
    zone_id = coalesce(p.zone_id, oc.zone_id),
    centre_code = coalesce(nullif(trim(p.centre_code), ''), oc.code)
from public.org_centres oc
where p.centre_id = oc.id
  and (p.city_id is null or p.state_id is null or p.zone_id is null or p.centre_code is null);

update public.profiles p
set
    state_id = coalesce(p.state_id, ct.state_id),
    zone_id = coalesce(p.zone_id, ct.zone_id)
from public.org_cities ct
where p.city_id = ct.id
  and (p.state_id is null or p.zone_id is null);

update public.profiles p
set
    zone_id = coalesce(p.zone_id, st.zone_id)
from public.org_states st
where p.state_id = st.id
  and p.zone_id is null;

-- Flag active profiles lacking required org scope for Super Admin review (never guess!)
update public.profiles p
set needs_scope_review = case
    when p.is_active = false then false
    when coalesce(p.active_role_code, case when p.app_role = 'admin' then 'SUPER_ADMIN' else 'IT_MANAGER' end) in ('SUPER_ADMIN', 'COMMAND_CENTER') then false
    when coalesce(p.active_role_code, 'IT_MANAGER') = 'ZOM' then (p.zone_id is null)
    when coalesce(p.active_role_code, 'IT_MANAGER') = 'ROM' then (p.state_id is null)
    when coalesce(p.active_role_code, 'IT_MANAGER') = 'CITY_HEAD' then (p.city_id is null)
    when coalesce(p.active_role_code, 'IT_MANAGER') = 'VCO' then (p.city_id is null and p.centre_id is null)
    else (p.centre_id is null)
end;

create or replace function public.get_unmapped_scope_users()
returns table (
    id uuid,
    employee_id text,
    full_name text,
    email text,
    active_role_code text,
    centre_code text,
    needs_scope_review boolean
)
language plpgsql
stable
security definer
set search_path = public, auth, pg_temp
as $$
begin
    if not public.is_super_admin() then
        raise exception 'Forbidden: only Super Admin can inspect unmapped scope users';
    end if;

    return query
    select
        p.id,
        p.employee_id,
        p.full_name,
        p.email,
        coalesce(p.active_role_code, 'IT_MANAGER') as active_role_code,
        p.centre_code,
        p.needs_scope_review
    from public.profiles p
    where p.is_active = true
      and p.needs_scope_review = true
    order by p.full_name asc;
end;
$$;

revoke all on function public.get_unmapped_scope_users() from public;
grant execute on function public.get_unmapped_scope_users() to authenticated;

-- ----------------------------------------------------------------------------
-- 2. STRICT FAIL-CLOSED ORG SCOPE CHECK (ZERO NULL = WILDCARD BYPASSES)
-- ----------------------------------------------------------------------------
create or replace function public.can_access_org_scope(
    p_scope_level text,
    p_zone_id uuid,
    p_state_id uuid,
    p_city_id uuid,
    p_centre_id uuid,
    p_user uuid default auth.uid()
)
returns boolean
language plpgsql
stable
security definer
set search_path = public, pg_temp
as $$
declare
    v_role text;
    v_profile public.profiles%rowtype;
    v_scope text := upper(coalesce(nullif(trim(p_scope_level), ''), 'CENTRE'));
    v_target_zone uuid;
    v_target_state uuid;
    v_target_city uuid;
begin
    if p_user is null then
        return false;
    end if;

    select * into v_profile from public.profiles where id = p_user;
    if not found or not coalesce(v_profile.is_active, false) then
        return false;
    end if;

    v_role := public.get_effective_role_code(p_user);

    if v_role in ('SUPER_ADMIN', 'COMMAND_CENTER') then
        return true;
    end if;

    -- GLOBAL records are only global if no narrower org ID was attached to bypass checks
    if v_scope = 'GLOBAL' then
        return p_zone_id is null
           and p_state_id is null
           and p_city_id is null
           and p_centre_id is null;
    end if;

    -- Resolve missing ancestor IDs cleanly from target hierarchy if available
    v_target_city := p_city_id;
    v_target_state := p_state_id;
    v_target_zone := p_zone_id;

    if p_centre_id is not null and (v_target_city is null or v_target_state is null or v_target_zone is null) then
        select
            coalesce(v_target_city, ct.city_id),
            coalesce(v_target_state, ct.state_id),
            coalesce(v_target_zone, ct.zone_id)
        into v_target_city, v_target_state, v_target_zone
        from public.org_centres ct
        where ct.id = p_centre_id;
    elsif v_target_city is not null and (v_target_state is null or v_target_zone is null) then
        select
            coalesce(v_target_state, c.state_id),
            coalesce(v_target_zone, c.zone_id)
        into v_target_state, v_target_zone
        from public.org_cities c
        where c.id = v_target_city;
    elsif v_target_state is not null and v_target_zone is null then
        select coalesce(v_target_zone, s.zone_id)
        into v_target_zone
        from public.org_states s
        where s.id = v_target_state;
    end if;

    -- ZOM: requires non-null zone_id matching target zone
    if v_role = 'ZOM' then
        if v_profile.zone_id is null then
            return false;
        end if;
        return v_target_zone is not null and v_target_zone = v_profile.zone_id;
    end if;

    -- ROM: requires non-null state_id; accesses state/descendants or own ancestor ZONE task
    if v_role = 'ROM' then
        if v_profile.state_id is null then
            return false;
        end if;
        if v_scope = 'ZONE' then
            return v_profile.zone_id is not null
               and p_zone_id is not null
               and p_zone_id = v_profile.zone_id;
        end if;
        return v_target_state is not null and v_target_state = v_profile.state_id;
    end if;

    -- CITY_HEAD / VCO: city + centres in city + matching ancestor state/zone tasks
    if v_role in ('CITY_HEAD', 'VCO') then
        if v_profile.city_id is null then
            if v_role = 'VCO' and v_profile.centre_id is not null then
                if v_scope = 'ZONE' then
                    return v_profile.zone_id is not null and p_zone_id is not null and p_zone_id = v_profile.zone_id;
                elsif v_scope = 'STATE' then
                    return v_profile.state_id is not null and p_state_id is not null and p_state_id = v_profile.state_id;
                end if;
                return p_centre_id is not null and p_centre_id = v_profile.centre_id;
            end if;
            return false;
        end if;

        if v_scope = 'ZONE' then
            return v_profile.zone_id is not null and p_zone_id is not null and p_zone_id = v_profile.zone_id;
        elsif v_scope = 'STATE' then
            return v_profile.state_id is not null and p_state_id is not null and p_state_id = v_profile.state_id;
        end if;

        return v_target_city is not null and v_target_city = v_profile.city_id;
    end if;

    -- IT_MANAGER: requires non-null centre_id; accesses own centre or own ancestor city/state/zone
    if v_profile.centre_id is null then
        return false;
    end if;

    if v_scope = 'ZONE' then
        return v_profile.zone_id is not null and p_zone_id is not null and p_zone_id = v_profile.zone_id;
    elsif v_scope = 'STATE' then
        return v_profile.state_id is not null and p_state_id is not null and p_state_id = v_profile.state_id;
    elsif v_scope = 'CITY' then
        return v_profile.city_id is not null and p_city_id is not null and p_city_id = v_profile.city_id;
    end if;

    return p_centre_id is not null and p_centre_id = v_profile.centre_id;
end;
$$;

-- ----------------------------------------------------------------------------
-- 3. TASK ACTIONABILITY VS READ-ONLY OVERSIGHT
-- ----------------------------------------------------------------------------
create or replace function public.can_act_on_task(p_task uuid)
returns boolean
language plpgsql
stable
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_uid uuid := auth.uid();
    v_role text;
    v_task public.tasks%rowtype;
    v_scope text;
begin
    if v_uid is null or not public.is_active_user() then
        return false;
    end if;

    if public.is_super_admin() then
        return true;
    end if;

    select * into v_task from public.tasks where id = p_task and deleted_at is null;
    if not found then
        return false;
    end if;

    if v_task.created_by = v_uid
       or coalesce(v_task.creator_id, v_task.created_by) = v_uid
       or v_task.assigned_user_id = v_uid then
        return true;
    end if;

    if exists (
        select 1 from public.task_participants tp
        where tp.task_id = v_task.id
          and tp.user_id = v_uid
          and tp.status = 'active'
    ) then
        return true;
    end if;

    v_role := public.get_effective_role_code(v_uid);
    if v_role = 'COMMAND_CENTER' then
        return true;
    end if;

    -- Must pass org scope intersection first (never allow out-of-scope group/everyone mutation)
    if not public.can_access_org_scope(v_task.scope_level, v_task.zone_id, v_task.state_id, v_task.city_id, v_task.centre_id, v_uid) then
        return false;
    end if;

    if v_task.visibility <> 'everyone' then
        return false;
    end if;

    if jsonb_array_length(coalesce(v_task.target_role_codes, '[]'::jsonb)) > 0 then
        return v_task.target_role_codes ? v_role;
    end if;

    -- When target_role_codes is empty, higher oversight roles inspecting lower-scope tasks
    -- get read-only view via can_view_task(), NOT automatic write/completion capability.
    v_scope := upper(coalesce(nullif(trim(v_task.scope_level), ''), 'CENTRE'));
    return case
        when v_scope = 'GLOBAL' then true
        when v_scope = 'ZONE' then v_role = 'ZOM'
        when v_scope = 'STATE' then v_role in ('ROM', 'ZOM')
        when v_scope = 'CITY' then v_role in ('CITY_HEAD', 'VCO')
        else v_role in ('IT_MANAGER', 'VCO')
    end;
end;
$$;

revoke all on function public.can_act_on_task(uuid) from public;
grant execute on function public.can_act_on_task(uuid) to authenticated;

-- Update submit_task_response (preserving exact 012 signature) to enforce can_act_on_task
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
    v_is_late boolean := false;
    v_delay_sec integer := 0;
    v_deadline timestamptz;
    v_total_lans integer := 0;
    v_completed_lans integer := 0;
    v_all_lans_done boolean := false;
begin
    if v_user is null then
        raise exception 'Authentication required';
    end if;

    select * into v_prof from public.profiles where id = v_user;
    if not found then
        raise exception 'User profile not found';
    end if;
    if not coalesce(v_prof.is_active, false) then
        raise exception 'User account is deactivated. Contact administrator.';
    end if;

    select * into v_task from public.tasks where id = p_task_id and deleted_at is null;
    if not found then
        raise exception 'Task not found';
    end if;

    if v_task.status = 'cancelled' then
        raise exception 'Task has been cancelled';
    end if;

    if v_task.status not in ('open', 'pending', 'in_progress', 'scheduled', 'upcoming', 'action_required', 'acknowledged', 'overdue', 'pending_confirmation') then
        raise exception 'Task does not accept responses in % state', v_task.status;
    end if;

    if not public.can_act_on_task(p_task_id) then
        raise exception 'Forbidden: active role or scope is not authorized to submit responses for this task';
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
    if not found or not coalesce(v_prof.is_active, false) then
        raise exception 'Active profile required to acknowledge alarm';
    end if;

    select * into v_task from public.tasks where id = p_task_id and deleted_at is null;
    if not found then
        raise exception 'Task not found';
    end if;

    if not public.can_act_on_task(p_task_id) then
        raise exception 'Forbidden: not authorized to acknowledge alarm for this task';
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

drop policy if exists task_responses_insert_policy on public.task_responses;
create policy task_responses_insert_policy on public.task_responses
    for insert to authenticated
    with check (
        user_id = (select auth.uid())
        and (public.can_act_on_task(task_id) or public.can_manage_task(task_id))
    );

drop policy if exists task_ack_insert_policy on public.task_acknowledgements;
create policy task_ack_insert_policy on public.task_acknowledgements
    for insert to authenticated
    with check (
        user_id = (select auth.uid())
        and (public.can_act_on_task(task_id) or public.can_manage_task(task_id))
    );

-- ----------------------------------------------------------------------------
-- 4. UNBOUNDED BATCHED create_task_atomic PROPAGATION & ASSIGNEE SCOPE CHECK
-- ----------------------------------------------------------------------------
create or replace function public.create_task_atomic(p_task jsonb)
returns jsonb
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_caller uuid := (select auth.uid());
    v_caller_profile public.profiles%rowtype;
    v_caller_role text;
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
    v_client_op_id text;
    v_scope_level text;
    v_zone_id uuid;
    v_state_id uuid;
    v_city_id uuid;
    v_centre_id uuid;
    v_target_role_codes jsonb;
    v_req jsonb;
    v_role_id uuid;
    v_req_count integer;
    v_assigned_user_id text;
    v_assigned_uuid uuid;
    v_assigned_prof public.profiles%rowtype;
    v_existing_task public.tasks%rowtype;
    v_created_task public.tasks%rowtype;
    v_recipient record;
    v_notified_count integer := 0;
    v_last_recipient_id uuid := '00000000-0000-0000-0000-000000000000'::uuid;
    v_batch_rows integer := 0;
begin
    if v_caller is null then
        raise exception 'Authentication required';
    end if;

    select * into v_caller_profile from public.profiles where id = v_caller;
    if not found or not coalesce(v_caller_profile.is_active, false) then
        raise exception 'Active user profile required to create tasks';
    end if;

    v_caller_role := public.get_effective_role_code(v_caller);
    v_client_op_id := nullif(trim(p_task->>'client_operation_id'), '');

    if v_client_op_id is not null then
        select * into v_existing_task
        from public.tasks
        where client_operation_id = v_client_op_id and created_by = v_caller
        limit 1;
        if found then
            return jsonb_build_object(
                'success', true,
                'task_id', v_existing_task.id,
                'is_duplicate', true,
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

    v_scope_level := upper(coalesce(nullif(trim(p_task->>'scope_level'), ''), 'CENTRE'));
    if v_scope_level not in ('CENTRE', 'CITY', 'STATE', 'ZONE', 'GLOBAL') then
        v_scope_level := 'CENTRE';
    end if;

    if v_scope_level = 'GLOBAL' and v_caller_role not in ('SUPER_ADMIN', 'COMMAND_CENTER') then
        raise exception 'Forbidden: role % cannot create GLOBAL tasks', v_caller_role;
    elsif v_scope_level = 'ZONE' and v_caller_role not in ('ZOM', 'COMMAND_CENTER', 'SUPER_ADMIN') then
        raise exception 'Forbidden: role % cannot create ZONE tasks', v_caller_role;
    elsif v_scope_level = 'STATE' and v_caller_role not in ('ROM', 'ZOM', 'COMMAND_CENTER', 'SUPER_ADMIN') then
        raise exception 'Forbidden: role % cannot create STATE tasks', v_caller_role;
    elsif v_scope_level = 'CITY' and v_caller_role not in ('VCO', 'CITY_HEAD', 'ROM', 'ZOM', 'COMMAND_CENTER', 'SUPER_ADMIN') then
        raise exception 'Forbidden: role % cannot create CITY tasks', v_caller_role;
    end if;

    v_centre_id := coalesce(nullif(p_task->>'centre_id', '')::uuid, v_caller_profile.centre_id);
    v_city_id := coalesce(nullif(p_task->>'city_id', '')::uuid, v_caller_profile.city_id);
    v_state_id := coalesce(nullif(p_task->>'state_id', '')::uuid, v_caller_profile.state_id);
    v_zone_id := coalesce(nullif(p_task->>'zone_id', '')::uuid, v_caller_profile.zone_id);

    if v_centre_id is not null and (v_city_id is null or v_state_id is null or v_zone_id is null) then
        select c.city_id, c.state_id, c.zone_id into v_city_id, v_state_id, v_zone_id
        from public.org_centres c where c.id = v_centre_id;
    elsif v_city_id is not null and (v_state_id is null or v_zone_id is null) then
        select ct.state_id, ct.zone_id into v_state_id, v_zone_id
        from public.org_cities ct where ct.id = v_city_id;
    elsif v_state_id is not null and v_zone_id is null then
        select st.zone_id into v_zone_id
        from public.org_states st where st.id = v_state_id;
    end if;

    if v_scope_level = 'GLOBAL' then
        v_zone_id := null; v_state_id := null; v_city_id := null; v_centre_id := null;
    elsif v_scope_level = 'ZONE' then
        if v_zone_id is null then raise exception 'Forbidden: ZONE task requires a valid zone_id'; end if;
        v_state_id := null; v_city_id := null; v_centre_id := null;
    elsif v_scope_level = 'STATE' then
        if v_state_id is null then raise exception 'Forbidden: STATE task requires a valid state_id'; end if;
        v_city_id := null; v_centre_id := null;
    elsif v_scope_level = 'CITY' then
        if v_city_id is null then raise exception 'Forbidden: CITY task requires a valid city_id'; end if;
        v_centre_id := null;
    elsif v_scope_level = 'CENTRE' then
        if v_centre_id is null then raise exception 'Forbidden: CENTRE task requires a valid centre_id'; end if;
    end if;

    if not public.can_access_org_scope(v_scope_level, v_zone_id, v_state_id, v_city_id, v_centre_id, v_caller) then
        raise exception 'Forbidden: caller scope does not authorize target % scope', v_scope_level;
    end if;

    v_target_role_codes := case
        when p_task ? 'target_role_codes' and jsonb_typeof(p_task->'target_role_codes') = 'array' then p_task->'target_role_codes'
        else '[]'::jsonb
    end;

    if v_group_id is null or not exists (select 1 from public.task_groups where id = v_group_id) then
        select id into v_group_id from public.task_groups
        where code = 'GEN-OPS' and visibility = 'everyone' and status = 'active'
        order by created_at asc limit 1;
        if v_group_id is null then
            insert into public.task_groups(title, code, description, status, visibility, allow_self_join, join_requires_approval, created_by)
            values ('General Operations', 'GEN-OPS', 'Default operational group', 'active', 'everyone', true, false, v_caller)
            returning id into v_group_id;
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
    v_visibility := case when lower(coalesce(p_task->>'visibility', 'everyone')) = 'restricted' then 'restricted' else 'everyone' end;
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
    v_form_schema := case when p_task ? 'response_form_schema' and jsonb_typeof(p_task->'response_form_schema') = 'array' then p_task->'response_form_schema' else '[]'::jsonb end;

    insert into public.tasks(
        id, group_id, shift_id, title, description, location, starts_at, due_at,
        priority, status, visibility, allow_self_join, join_requires_approval,
        notify_creator_on_join, requires_completion_confirmation, max_participants,
        created_by, creator_id, deadline_time, task_type, importance, urgency_type,
        execution_time, target_percentage, repeat_interval_minutes, alarm_type,
        advance_reminders_minutes, remind_until_completed, overdue_reminder_interval_minutes,
        max_overdue_reminders, response_type, lan_target_list, completion_policy,
        response_form_schema, client_operation_id, sync_status,
        scope_level, zone_id, state_id, city_id, centre_id, target_role_codes
    ) values (
        v_task_id, v_group_id, v_shift_id, v_title, v_desc, v_location, v_starts_at, v_due_at,
        v_priority, 'open', v_visibility, v_allow_self_join, v_join_requires_approval,
        v_notify_creator_on_join, v_requires_completion_confirmation, v_max_participants,
        v_caller, v_caller, v_due_at, v_task_type, v_importance, v_urgency_type,
        v_execution_time, v_target_percentage, v_repeat_interval_minutes, v_alarm_type,
        v_advance_reminders, v_remind_until_completed, v_overdue_reminder_interval,
        v_max_overdue_reminders, v_response_type, v_lan_target_list, v_completion_policy,
        v_form_schema, v_client_op_id, 'SYNCED',
        v_scope_level, v_zone_id, v_state_id, v_city_id, v_centre_id, v_target_role_codes
    ) returning * into v_created_task;

    insert into public.task_participants(task_id, user_id, source, status, assigned_by)
    values (v_task_id, v_caller, 'assigned', 'active', v_caller)
    on conflict (task_id, user_id) where status = 'active' do nothing;

    -- Explicit assigned users: validate active AND within requested task org scope for non-global callers
    if p_task ? 'assigned_user_ids' and jsonb_typeof(p_task->'assigned_user_ids') = 'array' then
        for v_assigned_user_id in select jsonb_array_elements_text(p_task->'assigned_user_ids')
        loop
            v_assigned_uuid := v_assigned_user_id::uuid;
            select * into v_assigned_prof from public.profiles where id = v_assigned_uuid;
            if not found or not coalesce(v_assigned_prof.is_active, false) then
                raise exception 'Assigned user "%" is not active', v_assigned_user_id;
            end if;

            if v_caller_role not in ('SUPER_ADMIN', 'COMMAND_CENTER') then
                if not (
                    v_scope_level = 'GLOBAL'
                    or (v_scope_level = 'ZONE' and v_assigned_prof.zone_id = v_zone_id)
                    or (v_scope_level = 'STATE' and v_assigned_prof.state_id = v_state_id)
                    or (v_scope_level = 'CITY' and v_assigned_prof.city_id = v_city_id)
                    or (v_scope_level = 'CENTRE' and v_assigned_prof.centre_id = v_centre_id)
                ) then
                    raise exception 'Forbidden: assigned user "%" is outside target % scope', v_assigned_user_id, v_scope_level;
                end if;
            end if;

            insert into public.task_participants(task_id, user_id, source, status, assigned_by)
            values (v_task_id, v_assigned_uuid, 'assigned', 'active', v_caller)
            on conflict (task_id, user_id) where status = 'active'
            do update set status = 'active', assigned_by = v_caller, joined_at = now();

            if v_visibility = 'restricted' then
                insert into public.task_visible_users(task_id, user_id, added_by)
                values (v_task_id, v_assigned_uuid, v_caller)
                on conflict (task_id, user_id) do nothing;
            end if;

            perform public.create_scoped_notification(
                v_assigned_uuid,
                'task_assigned',
                'task_assignment',
                'New Task Assigned: ' || v_title,
                coalesce(v_caller_profile.full_name, 'Supervisor') || ' assigned you to "' || v_title || '" (' || v_scope_level || ')',
                v_task_id,
                null,
                v_group_id,
                'task_assigned:' || v_task_id::text || ':' || v_assigned_uuid::text,
                'taskz://task/' || v_task_id::text,
                v_scope_level,
                jsonb_build_object('task_id', v_task_id, 'scope_level', v_scope_level, 'importance', v_importance)
            );
            v_notified_count := v_notified_count + 1;
        end loop;
    end if;

    -- Keyset-paginated batch propagation notifying ALL eligible users (no 250 truncation)
    if v_visibility = 'everyone' then
        loop
            v_batch_rows := 0;
            for v_recipient in
                select p.id
                from public.profiles p
                where p.is_active = true
                  and p.id <> v_caller
                  and p.id > v_last_recipient_id
                  and (
                      jsonb_array_length(v_target_role_codes) = 0
                      or exists (
                          select 1 from public.user_roles ur
                          where ur.user_id = p.id
                            and v_target_role_codes ? ur.role_code
                      )
                  )
                  and (
                      v_scope_level = 'GLOBAL'
                      or (v_scope_level = 'ZONE' and v_zone_id is not null and p.zone_id = v_zone_id)
                      or (v_scope_level = 'STATE' and v_state_id is not null and p.state_id = v_state_id)
                      or (v_scope_level = 'CITY' and v_city_id is not null and p.city_id = v_city_id)
                      or (v_scope_level = 'CENTRE' and v_centre_id is not null and p.centre_id = v_centre_id)
                  )
                order by p.id asc
                limit 200
            loop
                v_batch_rows := v_batch_rows + 1;
                v_last_recipient_id := v_recipient.id;
                perform public.create_scoped_notification(
                    v_recipient.id,
                    'task_assigned',
                    'task_assignment',
                    'New ' || v_scope_level || ' Task: ' || v_title,
                    coalesce(v_caller_profile.full_name, 'Operations') || ' published "' || v_title || '" for your scope',
                    v_task_id,
                    null,
                    v_group_id,
                    'task_scope_created:' || v_task_id::text || ':' || v_recipient.id::text,
                    'taskz://task/' || v_task_id::text,
                    v_scope_level,
                    jsonb_build_object('task_id', v_task_id, 'scope_level', v_scope_level, 'importance', v_importance)
                );
                v_notified_count := v_notified_count + 1;
            end loop;

            exit when v_batch_rows < 200;
        end loop;
    end if;

    if p_task ? 'requirements' and jsonb_typeof(p_task->'requirements') = 'array' then
        for v_req in select * from jsonb_array_elements(p_task->'requirements')
        loop
            v_role_id := nullif(v_req->>'role_id', '')::uuid;
            if v_role_id is not null and exists (select 1 from public.work_roles where id = v_role_id) then
                v_req_count := greatest(1, coalesce((v_req->>'required_count')::integer, (v_req->>'count')::integer, 1));
                insert into public.task_role_requirements(task_id, role_id, required_count)
                values (v_task_id, v_role_id, v_req_count)
                on conflict (task_id, role_id) do update set required_count = v_req_count;
            end if;
        end loop;
    end if;

    insert into public.task_activity_log(task_id, actor_user_id, event_type, message)
    values (
        v_task_id, v_caller, 'TASK_CREATED',
        'Task created by ' || coalesce(v_caller_profile.full_name, 'Creator') || ' [' || v_scope_level || ']'
    );

    return jsonb_build_object(
        'success', true,
        'task_id', v_task_id,
        'notified_recipients', v_notified_count,
        'task', to_jsonb(v_created_task)
    );
end;
$$;

revoke all on function public.create_task_atomic(jsonb) from public;
grant execute on function public.create_task_atomic(jsonb) to authenticated;

-- ----------------------------------------------------------------------------
-- 5. INCIDENT SCOPE INSERT POLICY, IMMUTABLE COLUMNS TRIGGER & SEQUENTIAL ESCALATION
-- ----------------------------------------------------------------------------
drop policy if exists system_incidents_insert_policy on public.system_incidents;
create policy system_incidents_insert_policy on public.system_incidents
for insert to authenticated
with check (
    public.is_active_user()
    and created_by = (select auth.uid())
    and public.can_access_org_scope(
        scope_level,
        zone_id,
        state_id,
        city_id,
        coalesce(
            (select oc.id from public.org_centres oc where oc.id::text = system_incidents.centre_id::text limit 1),
            (select oc.id from public.org_centres oc
             where nullif(trim(system_incidents.centre_code), '') is not null
               and (
                   upper(oc.code) = upper(trim(system_incidents.centre_code))
                   or upper(coalesce(oc.external_code, '')) = upper(trim(system_incidents.centre_code))
                   or oc.external_code = system_incidents.centre_id::text
               )
             limit 1)
        ),
        (select auth.uid())
    )
);

create or replace function public.protect_incident_immutable_columns()
returns trigger
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
begin
    if current_setting('taskz.bypass_incident_guard', true) = '1'
       or coalesce(auth.role(), '') = 'service_role'
       or (session_user in ('postgres', 'supabase_admin', 'supabase_auth_admin') and nullif(current_setting('request.jwt.claims', true), '') is null)
       or public.is_super_admin() then
        return new;
    end if;

    if new.created_by is distinct from old.created_by
       or new.submission_id is distinct from old.submission_id
       or new.scope_level is distinct from old.scope_level
       or new.zone_id is distinct from old.zone_id
       or new.state_id is distinct from old.state_id
       or new.city_id is distinct from old.city_id
       or new.centre_id is distinct from old.centre_id
       or new.escalation_level is distinct from old.escalation_level
       or new.escalated_by is distinct from old.escalated_by then
        raise exception 'Forbidden: incident scope, creator, submission_id, and escalation metadata can only be modified by Super Admin or server escalation RPC';
    end if;

    return new;
end;
$$;

drop trigger if exists trg_protect_incident_immutable_columns on public.system_incidents;
create trigger trg_protect_incident_immutable_columns
before update on public.system_incidents
for each row
execute function public.protect_incident_immutable_columns();

create or replace function public.bump_incident_version()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    new.version := coalesce(old.version, 1) + 1;
    new.updated_at := now();
    return new;
end;
$$;

drop trigger if exists trg_bump_incident_version on public.system_incidents;
create trigger trg_bump_incident_version
before update on public.system_incidents
for each row
execute function public.bump_incident_version();

create or replace function public.escalate_system_incident(
    p_incident_id uuid,
    p_target_level text,
    p_reason text default null,
    p_client_operation_id text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_caller uuid := (select auth.uid());
    v_caller_role text;
    v_target text := upper(trim(coalesce(p_target_level, '')));
    v_inc public.system_incidents%rowtype;
    v_prev_level text;
    v_curr_rank integer;
    v_target_rank integer;
    v_caller_name text;
    v_eff_zone_id uuid;
    v_eff_state_id uuid;
    v_eff_city_id uuid;
    v_eff_centre_id uuid;
    v_recipient record;
    v_last_recipient_id uuid := '00000000-0000-0000-0000-000000000000'::uuid;
    v_batch_rows integer := 0;
    v_notified integer := 0;
begin
    if v_caller is null or not public.is_active_user() then
        raise exception 'Active authentication required';
    end if;

    v_caller_role := public.get_effective_role_code(v_caller);

    if v_target not in ('IT_MANAGER', 'VCO', 'CITY_HEAD', 'ROM', 'ZOM', 'SUPER_ADMIN') then
        raise exception 'Invalid escalation target: %', v_target;
    end if;

    if p_client_operation_id is not null and trim(p_client_operation_id) <> '' then
        if exists (select 1 from public.client_events where client_operation_id = p_client_operation_id) then
            return jsonb_build_object('success', true, 'is_duplicate', true, 'incident_id', p_incident_id);
        end if;
    end if;

    select * into v_inc from public.system_incidents where id = p_incident_id and deleted_at is null;
    if not found then
        raise exception 'Incident % not found', p_incident_id;
    end if;

    if not public.can_view_incident(p_incident_id) then
        raise exception 'Forbidden: incident % is outside your authorized scope', p_incident_id;
    end if;

    if upper(coalesce(v_inc.status, 'OPEN')) = 'RESOLVED' then
        raise exception 'Forbidden: cannot escalate a resolved incident';
    end if;

    v_prev_level := upper(coalesce(nullif(trim(v_inc.escalation_level), ''), 'IT_MANAGER'));
    v_curr_rank := case v_prev_level
        when 'IT_MANAGER' then 1
        when 'VCO' then 2
        when 'CITY_HEAD' then 3
        when 'ROM' then 4
        when 'ZOM' then 5
        when 'SUPER_ADMIN' then 6
        else 1
    end;
    v_target_rank := case v_target
        when 'IT_MANAGER' then 1
        when 'VCO' then 2
        when 'CITY_HEAD' then 3
        when 'ROM' then 4
        when 'ZOM' then 5
        when 'SUPER_ADMIN' then 6
        else 0
    end;

    if v_target_rank <= v_curr_rank then
        raise exception 'Invalid escalation: target % must be strictly higher than current level %', v_target, v_prev_level;
    end if;
    if v_caller_role <> 'SUPER_ADMIN' and v_target_rank <> (v_curr_rank + 1) then
        raise exception 'Sequential escalation required: % must escalate next to tier %, not %', v_prev_level, v_curr_rank + 1, v_target;
    end if;

    -- Resolve missing org hierarchy conservatively from org_centres
    v_eff_zone_id := v_inc.zone_id;
    v_eff_state_id := v_inc.state_id;
    v_eff_city_id := v_inc.city_id;
    v_eff_centre_id := coalesce(
        (select oc.id from public.org_centres oc where oc.id::text = v_inc.centre_id::text limit 1),
        (select oc.id from public.org_centres oc
         where nullif(trim(v_inc.centre_code), '') is not null
           and (
               upper(oc.code) = upper(trim(v_inc.centre_code))
               or upper(coalesce(oc.external_code, '')) = upper(trim(v_inc.centre_code))
               or oc.external_code = v_inc.centre_id::text
           )
         limit 1)
    );
    if v_eff_centre_id is not null and (v_eff_zone_id is null or v_eff_state_id is null or v_eff_city_id is null) then
        select
            coalesce(v_eff_zone_id, oc.zone_id),
            coalesce(v_eff_state_id, oc.state_id),
            coalesce(v_eff_city_id, oc.city_id)
        into v_eff_zone_id, v_eff_state_id, v_eff_city_id
        from public.org_centres oc where oc.id = v_eff_centre_id;
    end if;

    -- Fail closed if target escalation tier lacks required org scope ID (no NULL wildcard broadcast)
    if v_target = 'ZOM' and v_eff_zone_id is null then
        raise exception 'Cannot escalate to ZOM: incident lacks resolved zone_id';
    elsif v_target = 'ROM' and v_eff_state_id is null then
        raise exception 'Cannot escalate to ROM: incident lacks resolved state_id';
    elsif v_target in ('CITY_HEAD', 'VCO') and v_eff_city_id is null and v_eff_centre_id is null then
        raise exception 'Cannot escalate to %: incident lacks resolved city_id/centre_id', v_target;
    end if;

    select full_name into v_caller_name from public.profiles where id = v_caller;

    perform set_config('taskz.bypass_incident_guard', '1', true);
    update public.system_incidents
    set escalation_level = v_target,
        status = 'ESCALATED',
        escalated_by = v_caller,
        escalated_at = now(),
        updated_by = v_caller,
        zone_id = coalesce(zone_id, v_eff_zone_id),
        state_id = coalesce(state_id, v_eff_state_id),
        city_id = coalesce(city_id, v_eff_city_id)
    where id = p_incident_id
    returning * into v_inc;

    insert into public.system_incident_audit(incident_id, changed_by, changed_by_name, action, previous_values, new_values)
    values (
        p_incident_id, v_caller, v_caller_name, 'ESCALATED',
        jsonb_build_object('previous_level', v_prev_level, 'previous_status', 'OPEN'),
        jsonb_build_object('target_level', v_target, 'reason', p_reason)
    );

    -- Notify ALL authorized recipients in target scope tier via keyset pagination (no 100 truncation)
    loop
        v_batch_rows := 0;
        for v_recipient in
            select p.id
            from public.profiles p
            where p.is_active = true
              and p.id <> v_caller
              and p.id > v_last_recipient_id
              and exists (
                  select 1 from public.user_roles ur
                  where ur.user_id = p.id and ur.role_code = v_target
              )
              and (
                  v_target = 'SUPER_ADMIN'
                  or (v_target = 'ZOM' and v_eff_zone_id is not null and p.zone_id = v_eff_zone_id)
                  or (v_target = 'ROM' and v_eff_state_id is not null and p.state_id = v_eff_state_id)
                  or (v_target = 'CITY_HEAD' and v_eff_city_id is not null and p.city_id = v_eff_city_id)
                  or (v_target = 'VCO' and (
                      (v_eff_city_id is not null and p.city_id = v_eff_city_id)
                      or (v_eff_centre_id is not null and p.centre_id = v_eff_centre_id)
                  ))
                  or (v_target = 'IT_MANAGER' and (
                      (v_eff_centre_id is not null and p.centre_id = v_eff_centre_id)
                      or (nullif(trim(v_inc.centre_code), '') is not null and upper(trim(p.centre_code)) = upper(trim(v_inc.centre_code)))
                  ))
              )
            order by p.id asc
            limit 200
        loop
            v_batch_rows := v_batch_rows + 1;
            v_last_recipient_id := v_recipient.id;
            perform public.create_scoped_notification(
                v_recipient.id,
                'incident_escalated',
                'incident_escalation',
                'Escalated Incident [' || v_target || ']: ' || v_inc.centre_code,
                'System ' || v_inc.locked_system_number || ' (' || v_inc.lan_name || ') — ' || coalesce(nullif(p_reason, ''), v_inc.reason),
                null,
                p_incident_id,
                null,
                'incident_escalated:' || p_incident_id::text || ':' || v_target || ':' || v_recipient.id::text || ':' || v_inc.version::text,
                'taskz://incident/' || p_incident_id::text,
                v_inc.scope_level,
                jsonb_build_object('incident_id', p_incident_id, 'escalation_level', v_target, 'centre_code', v_inc.centre_code)
            );
            v_notified := v_notified + 1;
        end loop;

        exit when v_batch_rows < 200;
    end loop;

    if p_client_operation_id is not null and trim(p_client_operation_id) <> '' then
        insert into public.client_events(transaction_id, user_id, client_operation_id, event_type, entity_type, entity_id, target_id, payload, status)
        values (
            coalesce(
                case when p_client_operation_id ~ '^[0-9a-fA-F-]{36}$' then p_client_operation_id::uuid else null end,
                gen_random_uuid()
            ),
            v_caller,
            p_client_operation_id,
            'INCIDENT_ESCALATED',
            'system_incident',
            p_incident_id,
            p_incident_id,
            jsonb_build_object('target_level', v_target),
            'processed'
        )
        on conflict (client_operation_id) do nothing;
    end if;

    return jsonb_build_object(
        'success', true,
        'incident_id', p_incident_id,
        'previous_level', v_prev_level,
        'escalation_level', v_target,
        'notified_recipients', v_notified,
        'version', v_inc.version
    );
end;
$$;

revoke all on function public.escalate_system_incident(uuid, text, text, text) from public;
grant execute on function public.escalate_system_incident(uuid, text, text, text) to authenticated;

-- ----------------------------------------------------------------------------
-- 6. SCOPED DASHBOARD COUNTS & FIXED delta_sync_pull SUBQUERIES
-- ----------------------------------------------------------------------------
create or replace function public.get_scoped_dashboard_summary()
returns jsonb
language plpgsql
stable
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_caller uuid := (select auth.uid());
    v_profile public.profiles%rowtype;
    v_role text;
    v_pending_tasks integer;
    v_overdue_tasks integer;
    v_assigned_tasks integer;
    v_open_incidents integer;
    v_escalated_incidents integer;
    v_unread_notifications integer;
    v_active_users integer := 0;
    v_total_centres integer := 0;
begin
    if v_caller is null then
        raise exception 'Authentication required';
    end if;

    select * into v_profile from public.profiles where id = v_caller;
    if not found or not coalesce(v_profile.is_active, false) then
        raise exception 'Active profile required';
    end if;

    v_role := public.get_effective_role_code(v_caller);

    select
        count(*) filter (where t.status in ('open', 'in_progress')),
        count(*) filter (where t.status in ('open', 'in_progress') and t.due_at is not null and t.due_at < now()),
        count(*) filter (where exists (select 1 from public.task_participants tp where tp.task_id = t.id and tp.user_id = v_caller and tp.status = 'active'))
    into v_pending_tasks, v_overdue_tasks, v_assigned_tasks
    from public.tasks t
    where t.deleted_at is null and public.can_view_task(t.id);

    select
        count(*) filter (where coalesce(si.status, 'OPEN') in ('OPEN', 'IN_PROGRESS', 'ESCALATED')),
        count(*) filter (where coalesce(si.status, 'OPEN') = 'ESCALATED')
    into v_open_incidents, v_escalated_incidents
    from public.system_incidents si
    where si.deleted_at is null and public.can_view_incident(si.id);

    select count(*) into v_unread_notifications
    from public.notifications n
    where n.user_id = v_caller and n.is_read = false;

    if v_role in ('SUPER_ADMIN', 'COMMAND_CENTER') then
        select count(*) into v_active_users from public.profiles where is_active = true;
        select count(*) into v_total_centres from public.org_centres where is_active = true;
    elsif v_role = 'ZOM' and v_profile.zone_id is not null then
        select count(*) into v_active_users from public.profiles where is_active = true and zone_id = v_profile.zone_id;
        select count(*) into v_total_centres from public.org_centres where is_active = true and zone_id = v_profile.zone_id;
    elsif v_role = 'ROM' and v_profile.state_id is not null then
        select count(*) into v_active_users from public.profiles where is_active = true and state_id = v_profile.state_id;
        select count(*) into v_total_centres from public.org_centres where is_active = true and state_id = v_profile.state_id;
    elsif v_role in ('CITY_HEAD', 'VCO') and v_profile.city_id is not null then
        select count(*) into v_active_users from public.profiles where is_active = true and city_id = v_profile.city_id;
        select count(*) into v_total_centres from public.org_centres where is_active = true and city_id = v_profile.city_id;
    elsif v_profile.centre_id is not null then
        select count(*) into v_active_users from public.profiles where is_active = true and centre_id = v_profile.centre_id;
        select count(*) into v_total_centres from public.org_centres where is_active = true and id = v_profile.centre_id;
    end if;

    return jsonb_build_object(
        'active_role', v_role,
        'pending_tasks', coalesce(v_pending_tasks, 0),
        'overdue_tasks', coalesce(v_overdue_tasks, 0),
        'assigned_tasks', coalesce(v_assigned_tasks, 0),
        'open_incidents', coalesce(v_open_incidents, 0),
        'escalated_incidents', coalesce(v_escalated_incidents, 0),
        'unread_notifications', coalesce(v_unread_notifications, 0),
        'active_users', coalesce(v_active_users, 0),
        'total_centres', coalesce(v_total_centres, 0),
        'server_now', now()
    );
end;
$$;

revoke all on function public.get_scoped_dashboard_summary() from public;
grant execute on function public.get_scoped_dashboard_summary() to authenticated;

create or replace function public.delta_sync_pull(
    p_since timestamptz default null,
    p_known_role_version bigint default null
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_caller uuid := (select auth.uid());
    v_auth_ctx jsonb;
    v_profile public.profiles%rowtype;
    v_effective_since timestamptz := p_since;
    v_tasks jsonb;
    v_incidents jsonb;
    v_notifications jsonb;
    v_deleted_task_ids jsonb;
    v_deleted_incident_ids jsonb;
    v_summary jsonb;
begin
    if v_caller is null then
        raise exception 'Authentication required';
    end if;

    select * into v_profile from public.profiles where id = v_caller;
    if not found or not coalesce(v_profile.is_active, false) then
        raise exception 'Active profile required';
    end if;

    if p_known_role_version is not null and (v_profile.role_version + v_profile.scope_version) <> p_known_role_version then
        v_effective_since := null;
    end if;

    v_auth_ctx := public.get_my_authorization_context();
    v_summary := public.get_scoped_dashboard_summary();

    -- Subquery applies ORDER BY + LIMIT cleanly BEFORE jsonb_agg
    select coalesce(jsonb_agg(to_jsonb(t) order by t.updated_at desc), '[]'::jsonb)
    into v_tasks
    from (
        select t.*
        from public.tasks t
        where t.deleted_at is null
          and public.can_view_task(t.id)
          and (v_effective_since is null or t.updated_at >= v_effective_since)
        order by t.updated_at desc
        limit 200
    ) t;

    select coalesce(jsonb_agg(to_jsonb(si) order by si.updated_at desc), '[]'::jsonb)
    into v_incidents
    from (
        select si.*
        from public.system_incidents si
        where si.deleted_at is null
          and public.can_view_incident(si.id)
          and (v_effective_since is null or si.updated_at >= v_effective_since)
        order by si.updated_at desc
        limit 200
    ) si;

    select coalesce(jsonb_agg(to_jsonb(n) order by n.created_at desc), '[]'::jsonb)
    into v_notifications
    from (
        select n.*
        from public.notifications n
        where n.user_id = v_caller
          and (v_effective_since is null or n.created_at >= v_effective_since)
        order by n.created_at desc
        limit 100
    ) n;

    if v_effective_since is not null then
        select coalesce(jsonb_agg(t.id), '[]'::jsonb)
        into v_deleted_task_ids
        from public.tasks t
        where t.deleted_at is not null
          and t.deleted_at >= v_effective_since;

        select coalesce(jsonb_agg(si.id), '[]'::jsonb)
        into v_deleted_incident_ids
        from public.system_incidents si
        where si.deleted_at is not null
          and si.deleted_at >= v_effective_since;
    else
        v_deleted_task_ids := '[]'::jsonb;
        v_deleted_incident_ids := '[]'::jsonb;
    end if;

    insert into public.user_sync_state(user_id, last_server_fetch_at, last_delta_sync_at, active_role_code, sync_version, updated_at)
    values (v_caller, now(), now(), v_auth_ctx->>'active_role_code', 1, now())
    on conflict (user_id) do update
    set last_server_fetch_at = now(),
        last_delta_sync_at = now(),
        active_role_code = excluded.active_role_code,
        sync_version = public.user_sync_state.sync_version + 1,
        updated_at = now();

    return jsonb_build_object(
        'authorization', v_auth_ctx,
        'dashboard', v_summary,
        'is_full_refresh', (v_effective_since is null),
        'tasks', v_tasks,
        'incidents', v_incidents,
        'notifications', v_notifications,
        'deleted_task_ids', coalesce(v_deleted_task_ids, '[]'::jsonb),
        'deleted_incident_ids', coalesce(v_deleted_incident_ids, '[]'::jsonb),
        'server_now', now()
    );
end;
$$;

revoke all on function public.delta_sync_pull(timestamptz, bigint) from public;
grant execute on function public.delta_sync_pull(timestamptz, bigint) to authenticated;

-- ----------------------------------------------------------------------------
-- 7. ADMIN UPDATE ACCESS: KEEP needs_scope_review IN SYNC
-- ----------------------------------------------------------------------------
create or replace function public.admin_update_user_access(
    p_target_user_id uuid,
    p_role_codes text[],
    p_active_role_code text default null,
    p_zone_id uuid default null,
    p_state_id uuid default null,
    p_city_id uuid default null,
    p_centre_id uuid default null,
    p_is_active boolean default null
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_caller uuid := (select auth.uid());
    v_role_code text;
    v_role_id uuid;
    v_clean_roles text[] := array[]::text[];
    v_primary_role text;
    v_active_role text;
    v_zone_id uuid := p_zone_id;
    v_state_id uuid := p_state_id;
    v_city_id uuid := p_city_id;
    v_centre_id uuid := p_centre_id;
    v_centre_code text;
    v_city_name text;
    v_state_name text;
    v_zone_name text;
    v_is_super boolean := false;
    v_needs_review boolean := false;
begin
    if not public.is_super_admin() then
        raise exception 'Forbidden: only Super Admin can manage user roles and scope';
    end if;

    if not exists (select 1 from public.profiles where id = p_target_user_id) then
        raise exception 'Target user profile % not found', p_target_user_id;
    end if;

    if p_role_codes is null or array_length(p_role_codes, 1) is null then
        raise exception 'At least one valid role code is required';
    end if;

    foreach v_role_code in array p_role_codes
    loop
        v_role_code := upper(trim(v_role_code));
        if v_role_code in ('IT_MANAGER', 'VCO', 'CITY_HEAD', 'ROM', 'ZOM', 'COMMAND_CENTER', 'SUPER_ADMIN') then
            if not (v_role_code = any(v_clean_roles)) then
                v_clean_roles := array_append(v_clean_roles, v_role_code);
            end if;
        end if;
    end loop;

    if array_length(v_clean_roles, 1) is null then
        raise exception 'No valid role codes provided';
    end if;

    v_primary_role := v_clean_roles[1];
    v_active_role := upper(coalesce(nullif(trim(p_active_role_code), ''), v_primary_role));
    if not (v_active_role = any(v_clean_roles)) then
        v_active_role := v_primary_role;
    end if;
    v_is_super := ('SUPER_ADMIN' = any(v_clean_roles));

    if v_centre_id is not null then
        select c.code, ct.id, ct.name, st.id, st.name, zn.id, zn.name
        into v_centre_code, v_city_id, v_city_name, v_state_id, v_state_name, v_zone_id, v_zone_name
        from public.org_centres c
        join public.org_cities ct on ct.id = c.city_id
        join public.org_states st on st.id = c.state_id
        join public.org_zones zn on zn.id = c.zone_id
        where c.id = v_centre_id;
    elsif v_city_id is not null then
        select ct.name, st.id, st.name, zn.id, zn.name
        into v_city_name, v_state_id, v_state_name, v_zone_id, v_zone_name
        from public.org_cities ct
        join public.org_states st on st.id = ct.state_id
        join public.org_zones zn on zn.id = ct.zone_id
        where ct.id = v_city_id;
    elsif v_state_id is not null then
        select st.name, zn.id, zn.name
        into v_state_name, v_zone_id, v_zone_name
        from public.org_states st
        join public.org_zones zn on zn.id = st.zone_id
        where st.id = v_state_id;
    elsif v_zone_id is not null then
        select zn.name into v_zone_name from public.org_zones zn where zn.id = v_zone_id;
    end if;

    v_needs_review := case
        when coalesce(p_is_active, true) = false then false
        when v_active_role in ('SUPER_ADMIN', 'COMMAND_CENTER') then false
        when v_active_role = 'ZOM' then (v_zone_id is null)
        when v_active_role = 'ROM' then (v_state_id is null)
        when v_active_role = 'CITY_HEAD' then (v_city_id is null)
        when v_active_role = 'VCO' then (v_city_id is null and v_centre_id is null)
        else (v_centre_id is null)
    end;

    perform set_config('taskz.bypass_rbac_guard', '1', true);

    delete from public.user_roles
    where user_id = p_target_user_id
      and role_code <> all(v_clean_roles);

    foreach v_role_code in array v_clean_roles
    loop
        select id into v_role_id from public.work_roles
        where canonical_code = v_role_code or upper(coalesce(code, '')) = v_role_code
        order by sort_order asc limit 1;

        if v_role_id is not null then
            insert into public.user_roles(user_id, role_id, role_code, is_primary, assigned_by)
            values (p_target_user_id, v_role_id, v_role_code, (v_role_code = v_primary_role), v_caller)
            on conflict (user_id, role_id) do update
            set role_code = excluded.role_code,
                is_primary = excluded.is_primary,
                assigned_by = excluded.assigned_by,
                updated_at = now();
        end if;
    end loop;

    update public.profiles
    set app_role = case when v_is_super then 'admin' else 'employee' end,
        active_role_code = v_active_role,
        primary_role_code = v_primary_role,
        zone_id = v_zone_id,
        zone_name = v_zone_name,
        state_id = v_state_id,
        state_name = v_state_name,
        city_id = v_city_id,
        city_name = v_city_name,
        centre_id = v_centre_id,
        centre_code = coalesce(v_centre_code, centre_code),
        is_active = coalesce(p_is_active, is_active),
        needs_scope_review = v_needs_review,
        role_version = coalesce(role_version, 1) + 1,
        scope_version = coalesce(scope_version, 1) + 1,
        updated_at = now()
    where id = p_target_user_id;

    if p_is_active = false then
        update public.device_tokens set is_active = false where user_id = p_target_user_id;
    end if;

    insert into public.admin_audit_log(admin_user_id, target_user_id, action, entity_type, entity_id, metadata)
    values (
        v_caller, p_target_user_id, 'ADMIN_UPDATE_USER_ACCESS', 'profile', p_target_user_id,
        jsonb_build_object(
            'roles', to_jsonb(v_clean_roles),
            'active_role', v_active_role,
            'zone_id', v_zone_id,
            'state_id', v_state_id,
            'city_id', v_city_id,
            'centre_id', v_centre_id,
            'is_active', p_is_active,
            'needs_scope_review', v_needs_review
        )
    );

    perform public.create_scoped_notification(
        p_target_user_id,
        'role_changed',
        'admin_alert',
        'Your Role or Scope Was Updated',
        'Active role is now ' || v_active_role || '. Synced permissions applied.',
        null,
        null,
        null,
        'role_updated:' || p_target_user_id::text || ':' || extract(epoch from now())::bigint::text,
        'taskz://profile/roles',
        'CENTRE',
        jsonb_build_object('active_role', v_active_role, 'roles', to_jsonb(v_clean_roles))
    );

    return public.get_my_authorization_context();
end;
$$;

revoke all on function public.admin_update_user_access(uuid, text[], text, uuid, uuid, uuid, uuid, boolean) from public;
grant execute on function public.admin_update_user_access(uuid, text[], text, uuid, uuid, uuid, uuid, boolean) to authenticated;

-- ----------------------------------------------------------------------------
-- 8. REAL DEVICE TOKEN VALIDATION + PUSH DISPATCH QUEUE & TRIGGER
-- ----------------------------------------------------------------------------
create or replace function public.register_device_token(
    p_device_id text,
    p_token text,
    p_platform text default 'android',
    p_device_name text default null,
    p_app_version text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_caller uuid := (select auth.uid());
    v_clean_token text := trim(coalesce(p_token, ''));
begin
    if v_caller is null then
        raise exception 'Authentication required';
    end if;
    if v_clean_token = '' or v_clean_token like 'local:%' then
        raise exception 'Valid external FCM token is required';
    end if;

    update public.device_tokens
    set is_active = false
    where token = v_clean_token and user_id <> v_caller;

    insert into public.device_tokens(user_id, device_id, token, platform, device_name, app_version, is_active, last_seen_at)
    values (v_caller, p_device_id, v_clean_token, coalesce(nullif(p_platform, ''), 'android'), p_device_name, p_app_version, true, now())
    on conflict (user_id, device_id) where device_id is not null
    do update set
        token = excluded.token,
        platform = excluded.platform,
        device_name = excluded.device_name,
        app_version = excluded.app_version,
        is_active = true,
        last_seen_at = now();

    return jsonb_build_object('success', true, 'device_id', p_device_id);
end;
$$;

revoke all on function public.register_device_token(text, text, text, text, text) from public;
grant execute on function public.register_device_token(text, text, text, text, text) to authenticated;

-- Remove any legacy fake 'local:' tokens so server never attempts FCM send against them
update public.device_tokens
set is_active = false
where token like 'local:%' and is_active = true;

create table if not exists public.push_dispatch_queue (
    id uuid primary key default gen_random_uuid(),
    notification_id uuid not null unique references public.notifications(id) on delete cascade,
    user_id uuid not null references public.profiles(id) on delete cascade,
    event_key text,
    category text not null default 'task_update',
    payload jsonb not null,
    status text not null default 'PENDING', -- PENDING, PROCESSING, SENT, FAILED_RETRYABLE, FAILED_PERMANENT
    attempt_count integer not null default 0,
    next_attempt_at timestamptz not null default now(),
    locked_at timestamptz,
    dispatched_at timestamptz,
    last_error text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

alter table public.push_dispatch_queue enable row level security;
revoke all on public.push_dispatch_queue from public, anon, authenticated;
grant select, insert, update, delete on public.push_dispatch_queue to service_role;

create index if not exists idx_push_dispatch_pending
    on public.push_dispatch_queue(status, next_attempt_at)
    where status in ('PENDING', 'FAILED_RETRYABLE');

create or replace function public.enqueue_push_on_notification_insert()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_has_fcm_token boolean := false;
    v_payload jsonb;
    v_fn_url text;
    v_webhook_secret text;
    v_importance text;
begin
    select exists (
        select 1 from public.device_tokens dt
        where dt.user_id = new.user_id
          and dt.is_active = true
          and dt.token is not null
          and dt.token <> ''
          and dt.token not like 'local:%'
    ) into v_has_fcm_token;

    if not v_has_fcm_token then
        return new;
    end if;

    v_importance := case
        when coalesce(new.category, '') in ('incident_escalation', 'system_alert', 'task_reminder') then 'HIGH'
        when upper(coalesce(new.data->>'importance', '')) in ('HIGH', 'CRITICAL', 'URGENT') then 'HIGH'
        else 'NORMAL'
    end;

    v_payload := jsonb_build_object(
        'notification_id', new.id,
        'user_ids', jsonb_build_array(new.user_id),
        'category', coalesce(nullif(new.category, ''), 'task_update'),
        'title', coalesce(new.title, 'Taskz Notification'),
        'body', coalesce(new.body, ''),
        'event_key', coalesce(nullif(new.event_key, ''), 'notif:' || new.id::text),
        'deep_link', coalesce(new.deep_link, ''),
        'task_id', coalesce(new.task_id::text, ''),
        'incident_id', coalesce(new.incident_id::text, ''),
        'scope_level', coalesce(new.scope_level, ''),
        'importance', v_importance
    );

    insert into public.push_dispatch_queue(
        notification_id, user_id, event_key, category, payload, status, next_attempt_at
    ) values (
        new.id, new.user_id, new.event_key, coalesce(new.category, 'task_update'), v_payload, 'PENDING', now()
    ) on conflict (notification_id) do nothing;

    -- Non-blocking instant Edge Function webhook when pg_net & settings are configured
    begin
        v_fn_url := nullif(trim(coalesce(current_setting('app.settings.edge_function_url', true), '')), '');
        v_webhook_secret := nullif(trim(coalesce(current_setting('app.settings.push_webhook_secret', true), '')), '');
        if v_fn_url is not null and v_webhook_secret is not null and to_regprocedure('net.http_post(text,jsonb,jsonb,jsonb,integer)') is not null then
            perform net.http_post(
                url := v_fn_url,
                headers := jsonb_build_object(
                    'Content-Type', 'application/json',
                    'x-push-webhook-secret', v_webhook_secret
                ),
                body := v_payload,
                timeout_milliseconds := 3000
            );
        end if;
    exception when others then
        -- Never fail notification creation if async HTTP dispatch fails; queue remains PENDING for worker drain
        null;
    end;

    return new;
end;
$$;

drop trigger if exists trg_enqueue_push_on_notification on public.notifications;
create trigger trg_enqueue_push_on_notification
after insert on public.notifications
for each row
execute function public.enqueue_push_on_notification_insert();

create or replace function public.claim_pending_push_dispatches(p_batch_size integer default 25)
returns setof public.push_dispatch_queue
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    return query
    with candidate as (
        select q.id
        from public.push_dispatch_queue q
        where (
            q.status in ('PENDING', 'FAILED_RETRYABLE')
            and q.next_attempt_at <= now()
        ) or (
            q.status = 'PROCESSING'
            and q.locked_at < (now() - interval '2 minutes')
        )
        order by q.next_attempt_at asc, q.created_at asc
        limit least(greatest(coalesce(p_batch_size, 25), 1), 100)
        for update skip locked
    )
    update public.push_dispatch_queue q
    set status = 'PROCESSING',
        locked_at = now(),
        attempt_count = q.attempt_count + 1,
        updated_at = now()
    from candidate c
    where q.id = c.id
    returning q.*;
end;
$$;

revoke all on function public.claim_pending_push_dispatches(integer) from public, anon, authenticated;
grant execute on function public.claim_pending_push_dispatches(integer) to service_role;

create or replace function public.complete_push_dispatch(
    p_queue_id uuid,
    p_status text,
    p_error text default null,
    p_retry_delay_seconds integer default 60
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    update public.push_dispatch_queue
    set status = p_status,
        last_error = left(p_error, 1000),
        dispatched_at = case when p_status = 'SENT' then now() else dispatched_at end,
        next_attempt_at = case
            when p_status = 'FAILED_RETRYABLE' then now() + make_interval(secs => greatest(coalesce(p_retry_delay_seconds, 60), 10))
            else next_attempt_at
        end,
        updated_at = now()
    where id = p_queue_id;
end;
$$;

revoke all on function public.complete_push_dispatch(uuid, text, text, integer) from public, anon, authenticated;
grant execute on function public.complete_push_dispatch(uuid, text, text, integer) to service_role;

-- ----------------------------------------------------------------------------
-- 9. ADDITIONAL PERFORMANCE INDEXES & RLS VERIFICATION
-- ----------------------------------------------------------------------------
create index if not exists idx_notifications_user_created_desc
    on public.notifications(user_id, created_at desc);
create index if not exists idx_device_tokens_user_active
    on public.device_tokens(user_id, is_active);
create index if not exists idx_client_events_user_op
    on public.client_events(user_id, client_operation_id);

-- Drop any legacy permissive INSERT/UPDATE policies if present on operational tables
drop policy if exists "Enable insert for authenticated users only" on public.tasks;
drop policy if exists "Enable read access for all users" on public.tasks;
drop policy if exists "Enable insert for authenticated users only" on public.system_incidents;
drop policy if exists "Enable read access for all users" on public.system_incidents;
drop policy if exists "Enable read access for all users" on public.notifications;

commit;

notify pgrst, 'reload schema';
