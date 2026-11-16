-- ============================================================================
-- Taskz Migration 015: Final Task Scope Security, Immutable Scope Guard,
-- Restricted Task Visibility Fix & Operational RLS Audit Hardening
-- Forward-only, idempotent migration (does NOT destructively rewrite 014).
-- Fixes:
--   A. Fail-closed direct INSERT RLS on public.tasks (can_create_task_in_scope).
--   B. Immutable scope/creator guard trigger on public.tasks UPDATE (allows
--      SUPER_ADMIN and trusted server RPCs; blocks direct scope/ownership tampering).
--   C/D. Tightened ancestor-consistent hierarchy validation & propagation in
--      can_access_org_scope() and create_task_atomic(jsonb).
--   E. Fixed restricted task visibility leak in can_view_task(uuid) + active-user
--      enforcement in can_manage_task(uuid), keeping view-only oversight vs actionability.
--   F/G. Incident audit RLS hardening, device_tokens REST token hygiene,
--      and scope-filtered deleted IDs in delta_sync_pull().
-- ============================================================================

begin;

-- ----------------------------------------------------------------------------
-- 0. BACKFILL COMPATIBILITY CREATOR_ID WHERE NULL (SAFE IDEMPOTENT SYNC)
-- ----------------------------------------------------------------------------
update public.tasks
set creator_id = created_by
where creator_id is null
  and created_by is not null;

-- ----------------------------------------------------------------------------
-- 1. FAIL-CLOSED ORG SCOPE CHECK WITH STRICT ANCESTOR CONSISTENCY
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
    v_target_zone uuid := p_zone_id;
    v_target_state uuid := p_state_id;
    v_target_city uuid := p_city_id;
    v_prof_city uuid;
    v_prof_state uuid;
    v_prof_zone uuid;
begin
    if p_user is null or v_scope not in ('CENTRE', 'CITY', 'STATE', 'ZONE', 'GLOBAL') then
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

    -- GLOBAL records are strictly global only when zero narrower org IDs exist
    if v_scope = 'GLOBAL' then
        return p_zone_id is null
           and p_state_id is null
           and p_city_id is null
           and p_centre_id is null;
    end if;

    -- Verify hierarchy consistency and resolve canonical ancestors from the lowest node present.
    -- Never let a spoofed parent ID override an actual child node's parent hierarchy.
    if p_centre_id is not null then
        select ct.city_id, ct.state_id, ct.zone_id
        into v_target_city, v_target_state, v_target_zone
        from public.org_centres ct
        where ct.id = p_centre_id;

        if not found then
            return false;
        end if;
        if (p_city_id is not null and p_city_id <> v_target_city)
           or (p_state_id is not null and p_state_id <> v_target_state)
           or (p_zone_id is not null and p_zone_id <> v_target_zone) then
            return false;
        end if;
    elsif p_city_id is not null then
        select c.state_id, c.zone_id
        into v_target_state, v_target_zone
        from public.org_cities c
        where c.id = p_city_id;

        if not found then
            return false;
        end if;
        if (p_state_id is not null and p_state_id <> v_target_state)
           or (p_zone_id is not null and p_zone_id <> v_target_zone) then
            return false;
        end if;
    elsif p_state_id is not null then
        select s.zone_id
        into v_target_zone
        from public.org_states s
        where s.id = p_state_id;

        if not found then
            return false;
        end if;
        if p_zone_id is not null and p_zone_id <> v_target_zone then
            return false;
        end if;
    elsif p_zone_id is not null then
        if not exists (select 1 from public.org_zones z where z.id = p_zone_id) then
            return false;
        end if;
    end if;

    -- Derive missing ancestor IDs on caller profile conservatively from existing lower IDs
    v_prof_city := v_profile.city_id;
    v_prof_state := v_profile.state_id;
    v_prof_zone := v_profile.zone_id;

    if v_profile.centre_id is not null and (v_prof_city is null or v_prof_state is null or v_prof_zone is null) then
        select
            coalesce(v_prof_city, oc.city_id),
            coalesce(v_prof_state, oc.state_id),
            coalesce(v_prof_zone, oc.zone_id)
        into v_prof_city, v_prof_state, v_prof_zone
        from public.org_centres oc
        where oc.id = v_profile.centre_id;
    elsif v_prof_city is not null and (v_prof_state is null or v_prof_zone is null) then
        select
            coalesce(v_prof_state, c.state_id),
            coalesce(v_prof_zone, c.zone_id)
        into v_prof_state, v_prof_zone
        from public.org_cities c
        where c.id = v_prof_city;
    elsif v_prof_state is not null and v_prof_zone is null then
        select coalesce(v_prof_zone, s.zone_id)
        into v_prof_zone
        from public.org_states s
        where s.id = v_prof_state;
    end if;

    -- ZOM: requires non-null zone_id matching target zone
    if v_role = 'ZOM' then
        if v_prof_zone is null then
            return false;
        end if;
        return v_target_zone is not null and v_target_zone = v_prof_zone;
    end if;

    -- ROM: requires non-null state_id; accesses state/descendants or own ancestor ZONE task
    if v_role = 'ROM' then
        if v_prof_state is null then
            return false;
        end if;
        if v_scope = 'ZONE' then
            return v_prof_zone is not null
               and v_target_zone is not null
               and v_target_zone = v_prof_zone;
        end if;
        return v_target_state is not null and v_target_state = v_prof_state;
    end if;

    -- CITY_HEAD / VCO: city + centres in city + matching ancestor state/zone tasks
    if v_role in ('CITY_HEAD', 'VCO') then
        if v_prof_city is null then
            if v_role = 'VCO' and v_profile.centre_id is not null then
                if v_scope = 'ZONE' then
                    return v_prof_zone is not null and v_target_zone is not null and v_target_zone = v_prof_zone;
                elsif v_scope = 'STATE' then
                    return v_prof_state is not null and v_target_state is not null and v_target_state = v_prof_state;
                end if;
                return p_centre_id is not null and p_centre_id = v_profile.centre_id;
            end if;
            return false;
        end if;

        if v_scope = 'ZONE' then
            return v_prof_zone is not null and v_target_zone is not null and v_target_zone = v_prof_zone;
        elsif v_scope = 'STATE' then
            return v_prof_state is not null and v_target_state is not null and v_target_state = v_prof_state;
        end if;

        return v_target_city is not null and v_target_city = v_prof_city;
    end if;

    -- IT_MANAGER: requires non-null centre_id; accesses own centre or own ancestor city/state/zone
    if v_profile.centre_id is null then
        return false;
    end if;

    if v_scope = 'ZONE' then
        return v_prof_zone is not null and v_target_zone is not null and v_target_zone = v_prof_zone;
    elsif v_scope = 'STATE' then
        return v_prof_state is not null and v_target_state is not null and v_target_state = v_prof_state;
    elsif v_scope = 'CITY' then
        return v_prof_city is not null and v_target_city is not null and v_target_city = v_prof_city;
    end if;

    return p_centre_id is not null and p_centre_id = v_profile.centre_id;
end;
$$;

revoke all on function public.can_access_org_scope(text, uuid, uuid, uuid, uuid, uuid) from public;
grant execute on function public.can_access_org_scope(text, uuid, uuid, uuid, uuid, uuid) to authenticated;

-- ----------------------------------------------------------------------------
-- 2. DIRECT TASK CREATION SCOPE AUTHORIZATION (FAIL-CLOSED, NO NULL WILDCARDS)
-- ----------------------------------------------------------------------------
create or replace function public.can_create_task_in_scope(
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
    v_scope text := upper(coalesce(nullif(trim(p_scope_level), ''), ''));
    v_st public.org_states%rowtype;
    v_ct public.org_cities%rowtype;
    v_oc public.org_centres%rowtype;
begin
    if p_user is null or v_scope not in ('GLOBAL', 'ZONE', 'STATE', 'CITY', 'CENTRE') then
        return false;
    end if;

    select * into v_profile from public.profiles where id = p_user;
    if not found or not coalesce(v_profile.is_active, false) then
        return false;
    end if;

    v_role := public.get_effective_role_code(p_user);

    if v_scope = 'GLOBAL' then
        return v_role in ('SUPER_ADMIN', 'COMMAND_CENTER')
           and p_zone_id is null
           and p_state_id is null
           and p_city_id is null
           and p_centre_id is null;
    end if;

    if v_scope = 'ZONE' then
        if v_role not in ('ZOM', 'COMMAND_CENTER', 'SUPER_ADMIN') then
            return false;
        end if;
        if p_zone_id is null or p_state_id is not null or p_city_id is not null or p_centre_id is not null then
            return false;
        end if;
        if not exists (select 1 from public.org_zones z where z.id = p_zone_id and z.is_active = true) then
            return false;
        end if;
        return public.can_access_org_scope('ZONE', p_zone_id, null, null, null, p_user);
    end if;

    if v_scope = 'STATE' then
        if v_role not in ('ROM', 'ZOM', 'COMMAND_CENTER', 'SUPER_ADMIN') then
            return false;
        end if;
        if p_state_id is null or p_city_id is not null or p_centre_id is not null then
            return false;
        end if;
        select * into v_st from public.org_states s where s.id = p_state_id and s.is_active = true;
        if not found then
            return false;
        end if;
        if p_zone_id is not null and p_zone_id <> v_st.zone_id then
            return false;
        end if;
        return public.can_access_org_scope('STATE', v_st.zone_id, v_st.id, null, null, p_user);
    end if;

    if v_scope = 'CITY' then
        if v_role not in ('VCO', 'CITY_HEAD', 'ROM', 'ZOM', 'COMMAND_CENTER', 'SUPER_ADMIN') then
            return false;
        end if;
        if p_city_id is null or p_centre_id is not null then
            return false;
        end if;
        select * into v_ct from public.org_cities c where c.id = p_city_id and c.is_active = true;
        if not found then
            return false;
        end if;
        if (p_state_id is not null and p_state_id <> v_ct.state_id)
           or (p_zone_id is not null and p_zone_id <> v_ct.zone_id) then
            return false;
        end if;
        return public.can_access_org_scope('CITY', v_ct.zone_id, v_ct.state_id, v_ct.id, null, p_user);
    end if;

    -- CENTRE scope
    if v_role not in ('IT_MANAGER', 'VCO', 'CITY_HEAD', 'ROM', 'ZOM', 'COMMAND_CENTER', 'SUPER_ADMIN') then
        return false;
    end if;
    if p_centre_id is null then
        return false;
    end if;
    select * into v_oc from public.org_centres oc where oc.id = p_centre_id and oc.is_active = true;
    if not found then
        return false;
    end if;
    if (p_city_id is not null and p_city_id <> v_oc.city_id)
       or (p_state_id is not null and p_state_id <> v_oc.state_id)
       or (p_zone_id is not null and p_zone_id <> v_oc.zone_id) then
        return false;
    end if;

    return public.can_access_org_scope('CENTRE', v_oc.zone_id, v_oc.state_id, v_oc.city_id, v_oc.id, p_user);
end;
$$;

revoke all on function public.can_create_task_in_scope(text, uuid, uuid, uuid, uuid, uuid) from public;
grant execute on function public.can_create_task_in_scope(text, uuid, uuid, uuid, uuid, uuid) to authenticated;

-- ----------------------------------------------------------------------------
-- 3. FIX VISIBILITY VS ACTIONABILITY & ACTIVE CALLER CHECKS ON TASKS
-- ----------------------------------------------------------------------------
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
          and t.deleted_at is null
          and public.is_active_user()
          and (
              public.is_super_admin()
              or t.created_by = (select auth.uid())
              or coalesce(t.creator_id, t.created_by) = (select auth.uid())
              or t.assigned_user_id = (select auth.uid())
              or exists (
                  select 1 from public.task_participants tp
                  where tp.task_id = t.id
                    and tp.user_id = (select auth.uid())
                    and tp.status = 'active'
              )
              or exists (
                  select 1 from public.task_visible_users tv
                  where tv.task_id = t.id
                    and tv.user_id = (select auth.uid())
              )
              or (
                  public.get_effective_role_code((select auth.uid())) = 'COMMAND_CENTER'
                  and public.can_access_org_scope(t.scope_level, t.zone_id, t.state_id, t.city_id, t.centre_id, (select auth.uid()))
              )
              or (
                  t.visibility = 'everyone'
                  and public.can_access_org_scope(t.scope_level, t.zone_id, t.state_id, t.city_id, t.centre_id, (select auth.uid()))
                  and (
                      jsonb_array_length(coalesce(t.target_role_codes, '[]'::jsonb)) = 0
                      or t.target_role_codes ? public.get_effective_role_code((select auth.uid()))
                      or public.get_effective_role_code((select auth.uid())) in ('CITY_HEAD', 'ROM', 'ZOM', 'COMMAND_CENTER', 'SUPER_ADMIN')
                  )
              )
          )
    );
$$;

create or replace function public.can_manage_task(p_task uuid)
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
          and t.deleted_at is null
          and public.is_active_user()
          and (
              public.is_super_admin()
              or t.created_by = (select auth.uid())
              or coalesce(t.creator_id, t.created_by) = (select auth.uid())
              or (
                  public.get_effective_role_code((select auth.uid())) = 'COMMAND_CENTER'
                  and public.can_access_org_scope(t.scope_level, t.zone_id, t.state_id, t.city_id, t.centre_id, (select auth.uid()))
              )
          )
    );
$$;

-- ----------------------------------------------------------------------------
-- 4. REPLACE DIRECT TASKS RLS POLICIES (INSERT / UPDATE / DELETE / SELECT)
-- ----------------------------------------------------------------------------
drop policy if exists tasks_read on public.tasks;
create policy tasks_read
on public.tasks
for select
to authenticated
using (
    public.is_active_user()
    and public.can_view_task(id)
);

drop policy if exists tasks_insert on public.tasks;
create policy tasks_insert
on public.tasks
for insert
to authenticated
with check (
    public.is_active_user()
    and created_by = (select auth.uid())
    and coalesce(creator_id, created_by) = (select auth.uid())
    and (
        public.can_manage_group(group_id)
        or public.is_group_member(group_id)
        or public.can_view_group(group_id)
    )
    and public.can_create_task_in_scope(
        scope_level,
        zone_id,
        state_id,
        city_id,
        centre_id,
        (select auth.uid())
    )
);

drop policy if exists tasks_update on public.tasks;
create policy tasks_update
on public.tasks
for update
to authenticated
using (
    public.is_active_user()
    and public.can_manage_task(id)
)
with check (
    public.is_active_user()
    and public.can_manage_task(id)
);

drop policy if exists tasks_delete on public.tasks;
create policy tasks_delete
on public.tasks
for delete
to authenticated
using (
    public.is_active_user()
    and public.can_manage_task(id)
);

-- ----------------------------------------------------------------------------
-- 5. IMMUTABLE SCOPE & OWNERSHIP TRIGGER ON public.tasks UPDATE
-- ----------------------------------------------------------------------------
create or replace function public.protect_task_immutable_columns()
returns trigger
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_creator_backfill boolean;
begin
    if current_setting('taskz.bypass_task_guard', true) = '1'
       or coalesce(auth.role(), '') = 'service_role'
       or public.is_super_admin() then
        return new;
    end if;

    v_creator_backfill := (old.creator_id is null and new.creator_id is not distinct from old.created_by);

    if new.created_by is distinct from old.created_by
       or (new.creator_id is distinct from old.creator_id and not v_creator_backfill)
       or new.scope_level is distinct from old.scope_level
       or new.zone_id is distinct from old.zone_id
       or new.state_id is distinct from old.state_id
       or new.city_id is distinct from old.city_id
       or new.centre_id is distinct from old.centre_id
       or new.target_role_codes is distinct from old.target_role_codes then
        raise exception 'Forbidden: task creator, org scope, and target roles cannot be modified directly';
    end if;

    return new;
end;
$$;

drop trigger if exists trg_protect_task_immutable_columns on public.tasks;
create trigger trg_protect_task_immutable_columns
before update on public.tasks
for each row
execute function public.protect_task_immutable_columns();

-- ----------------------------------------------------------------------------
-- 6. HARDENED create_task_atomic(jsonb): CANONICAL HIERARCHY & FULL PROPAGATION
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
    v_raw_zone_id uuid;
    v_raw_state_id uuid;
    v_raw_city_id uuid;
    v_raw_centre_id uuid;
    v_zone_id uuid;
    v_state_id uuid;
    v_city_id uuid;
    v_centre_id uuid;
    v_oc public.org_centres%rowtype;
    v_ct public.org_cities%rowtype;
    v_st public.org_states%rowtype;
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
        raise exception 'Forbidden: invalid scope_level %', v_scope_level;
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

    v_raw_centre_id := nullif(p_task->>'centre_id', '')::uuid;
    v_raw_city_id := nullif(p_task->>'city_id', '')::uuid;
    v_raw_state_id := nullif(p_task->>'state_id', '')::uuid;
    v_raw_zone_id := nullif(p_task->>'zone_id', '')::uuid;

    if v_scope_level = 'GLOBAL' then
        v_zone_id := null;
        v_state_id := null;
        v_city_id := null;
        v_centre_id := null;
    elsif v_scope_level = 'ZONE' then
        v_zone_id := coalesce(v_raw_zone_id, v_caller_profile.zone_id);
        if v_zone_id is null or not exists (select 1 from public.org_zones z where z.id = v_zone_id and z.is_active = true) then
            raise exception 'Forbidden: ZONE task requires a valid active zone_id';
        end if;
        v_state_id := null;
        v_city_id := null;
        v_centre_id := null;
    elsif v_scope_level = 'STATE' then
        v_state_id := coalesce(v_raw_state_id, v_caller_profile.state_id);
        if v_state_id is null then
            raise exception 'Forbidden: STATE task requires a valid state_id';
        end if;
        select * into v_st from public.org_states s where s.id = v_state_id and s.is_active = true;
        if not found then
            raise exception 'Forbidden: invalid or inactive state_id %', v_state_id;
        end if;
        if v_raw_zone_id is not null and v_raw_zone_id <> v_st.zone_id then
            raise exception 'Forbidden: supplied zone_id does not match state hierarchy';
        end if;
        v_zone_id := v_st.zone_id;
        v_city_id := null;
        v_centre_id := null;
    elsif v_scope_level = 'CITY' then
        v_city_id := coalesce(v_raw_city_id, v_caller_profile.city_id);
        if v_city_id is null then
            raise exception 'Forbidden: CITY task requires a valid city_id';
        end if;
        select * into v_ct from public.org_cities c where c.id = v_city_id and c.is_active = true;
        if not found then
            raise exception 'Forbidden: invalid or inactive city_id %', v_city_id;
        end if;
        if (v_raw_state_id is not null and v_raw_state_id <> v_ct.state_id)
           or (v_raw_zone_id is not null and v_raw_zone_id <> v_ct.zone_id) then
            raise exception 'Forbidden: supplied state_id/zone_id does not match city hierarchy';
        end if;
        v_state_id := v_ct.state_id;
        v_zone_id := v_ct.zone_id;
        v_centre_id := null;
    elsif v_scope_level = 'CENTRE' then
        v_centre_id := coalesce(v_raw_centre_id, v_caller_profile.centre_id);
        if v_centre_id is null then
            raise exception 'Forbidden: CENTRE task requires a valid centre_id';
        end if;
        select * into v_oc from public.org_centres oc where oc.id = v_centre_id and oc.is_active = true;
        if not found then
            raise exception 'Forbidden: invalid or inactive centre_id %', v_centre_id;
        end if;
        if (v_raw_city_id is not null and v_raw_city_id <> v_oc.city_id)
           or (v_raw_state_id is not null and v_raw_state_id <> v_oc.state_id)
           or (v_raw_zone_id is not null and v_raw_zone_id <> v_oc.zone_id) then
            raise exception 'Forbidden: supplied parent org IDs do not match centre hierarchy';
        end if;
        v_city_id := v_oc.city_id;
        v_state_id := v_oc.state_id;
        v_zone_id := v_oc.zone_id;
    end if;

    if not public.can_create_task_in_scope(v_scope_level, v_zone_id, v_state_id, v_city_id, v_centre_id, v_caller) then
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
    elsif not (public.can_manage_group(v_group_id) or public.is_group_member(v_group_id) or public.can_view_group(v_group_id)) then
        raise exception 'Forbidden: caller does not have access to target task group';
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
                if not public.can_access_org_scope(v_scope_level, v_zone_id, v_state_id, v_city_id, v_centre_id, v_assigned_uuid) then
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

    -- Single-row keyset-paginated propagation notifying ALL eligible scoped users (no 250 truncation)
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
                      or (v_scope_level = 'ZONE' and v_zone_id is not null and (
                          p.zone_id = v_zone_id
                          or exists (select 1 from public.org_states s where s.id = p.state_id and s.zone_id = v_zone_id)
                          or exists (select 1 from public.org_cities c where c.id = p.city_id and c.zone_id = v_zone_id)
                          or exists (select 1 from public.org_centres oc where oc.id = p.centre_id and oc.zone_id = v_zone_id)
                      ))
                      or (v_scope_level = 'STATE' and v_state_id is not null and (
                          p.state_id = v_state_id
                          or exists (select 1 from public.org_cities c where c.id = p.city_id and c.state_id = v_state_id)
                          or exists (select 1 from public.org_centres oc where oc.id = p.centre_id and oc.state_id = v_state_id)
                      ))
                      or (v_scope_level = 'CITY' and v_city_id is not null and (
                          p.city_id = v_city_id
                          or exists (select 1 from public.org_centres oc where oc.id = p.centre_id and oc.city_id = v_city_id)
                      ))
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
-- 7. OPERATIONAL TABLE RLS HYGIENE (INCIDENT AUDIT & DEVICE TOKENS)
-- ----------------------------------------------------------------------------
drop policy if exists system_incident_audit_select_policy on public.system_incident_audit;
create policy system_incident_audit_select_policy on public.system_incident_audit
for select to authenticated
using (
    public.is_active_user()
    and (
        public.is_super_admin()
        or public.can_view_incident(incident_id)
    )
);

drop policy if exists system_incident_audit_insert_policy on public.system_incident_audit;
create policy system_incident_audit_insert_policy on public.system_incident_audit
for insert to authenticated
with check (
    public.is_active_user()
    and changed_by = (select auth.uid())
    and (
        public.is_super_admin()
        or public.can_view_incident(incident_id)
    )
);

drop policy if exists device_tokens_own on public.device_tokens;
create policy device_tokens_own on public.device_tokens
for all to authenticated
using (
    user_id = (select auth.uid())
)
with check (
    public.is_active_user()
    and user_id = (select auth.uid())
    and trim(coalesce(token, '')) <> ''
    and token not like 'local:%'
);

-- ----------------------------------------------------------------------------
-- 8. SCOPE-FILTERED DELETED IDS IN delta_sync_pull()
-- ----------------------------------------------------------------------------
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
          and t.deleted_at >= v_effective_since
          and (
              public.is_super_admin()
              or t.created_by = v_caller
              or coalesce(t.creator_id, t.created_by) = v_caller
              or t.assigned_user_id = v_caller
              or public.can_access_org_scope(t.scope_level, t.zone_id, t.state_id, t.city_id, t.centre_id, v_caller)
          );

        select coalesce(jsonb_agg(si.id), '[]'::jsonb)
        into v_deleted_incident_ids
        from public.system_incidents si
        where si.deleted_at is not null
          and si.deleted_at >= v_effective_since
          and (
              public.is_super_admin()
              or si.created_by = v_caller
              or si.assigned_to = v_caller
              or public.can_access_org_scope(
                  si.scope_level,
                  si.zone_id,
                  si.state_id,
                  si.city_id,
                  case when si.centre_id ~ '^[0-9a-fA-F-]{36}$' then si.centre_id::uuid
                       else (select oc.id from public.org_centres oc where oc.external_code = si.centre_id or upper(oc.code) = upper(si.centre_code) limit 1)
                  end,
                  v_caller
              )
          );
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

commit;

notify pgrst, 'reload schema';
