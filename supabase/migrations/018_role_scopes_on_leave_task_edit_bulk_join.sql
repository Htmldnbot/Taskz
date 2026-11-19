-- ============================================================================
-- Migration 018:
--   1. Merge SO / Site Officer / System Operator into IT_MANAGER + short role names
--   2. On Leave status (is_on_leave) blocking assignments, sync & notifications
--   3. Strict role-specific operational location scopes (ZOM=Zone, ROM=State,
--      CITY_HEAD=City, VCO=Centre, IT_MANAGER=Centre+LAN)
--   4. Editable task details & execution/alarm times after creation
--   5. 1-click bulk self-join for Exam Group & Shift tasks
-- ============================================================================

begin;

select set_config('taskz.bypass_rbac_guard', '1', true);
select set_config('taskz.bypass_task_guard', '1', true);

-- ----------------------------------------------------------------------------
-- 1. MERGE SO / SITE OFFICER / SYSTEM OPERATOR INTO IT_MANAGER + SHORT NAMES
-- ----------------------------------------------------------------------------
do $$
declare
    v_itm_id uuid;
    v_so_id uuid;
begin
    select id into v_itm_id
    from public.work_roles
    where id = '11111111-1111-4111-8111-111111111102'::uuid
       or canonical_code = 'IT_MANAGER'
       or upper(coalesce(code, '')) in ('IT_MANAGER', 'ITM')
    order by (id = '11111111-1111-4111-8111-111111111102'::uuid) desc, sort_order asc
    limit 1;

    if v_itm_id is null then
        insert into public.work_roles (
            id, name, code, canonical_code, description, is_active, sort_order,
            hierarchy_level, default_scope_level, is_self_assignable
        ) values (
            '11111111-1111-4111-8111-111111111102'::uuid,
            'IT Manager',
            'ITM',
            'IT_MANAGER',
            'Technical operations & workstation management',
            true,
            2,
            10,
            'CENTRE',
            true
        ) returning id into v_itm_id;
    else
        update public.work_roles
        set canonical_code = 'IT_MANAGER',
            code = 'ITM',
            name = 'IT Manager',
            is_active = true,
            is_self_assignable = true
        where id = v_itm_id;
    end if;

    -- Migrate any SO / SITE_OFFICER / SYSTEM_OPERATOR role rows cleanly
    for v_so_id in
        select id
        from public.work_roles
        where id <> v_itm_id
          and (
              id = '11111111-1111-4111-8111-111111111113'::uuid
              or upper(coalesce(canonical_code, '')) in ('SO', 'SITE_OFFICER', 'SYSTEM_OPERATOR')
              or upper(coalesce(code, '')) in ('SO', 'SITE_OFFICER', 'SYSTEM_OPERATOR')
          )
    loop
        -- Preserve primary flag when user holds both SO and IT_MANAGER
        update public.user_roles itm_ur
        set is_primary = true,
            role_code = 'IT_MANAGER',
            updated_at = now()
        from public.user_roles so_ur
        where so_ur.user_id = itm_ur.user_id
          and so_ur.role_id = v_so_id
          and so_ur.is_primary = true
          and itm_ur.role_id = v_itm_id
          and itm_ur.is_primary = false;

        delete from public.user_roles so_ur
        where so_ur.role_id = v_so_id
          and exists (
              select 1 from public.user_roles itm_ur
              where itm_ur.user_id = so_ur.user_id and itm_ur.role_id = v_itm_id
          );

        update public.user_roles
        set role_id = v_itm_id,
            role_code = 'IT_MANAGER',
            updated_at = now()
        where role_id = v_so_id;

        -- Migrate task_role_requirements
        delete from public.task_role_requirements tr_so
        where tr_so.role_id = v_so_id
          and exists (
              select 1 from public.task_role_requirements tr_itm
              where tr_itm.task_id = tr_so.task_id and tr_itm.role_id = v_itm_id
          );

        update public.task_role_requirements
        set role_id = v_itm_id
        where role_id = v_so_id;

        update public.task_participants
        set role_id = v_itm_id
        where role_id = v_so_id;

        update public.task_join_requests
        set requested_role_id = v_itm_id
        where requested_role_id = v_so_id;

        update public.work_roles
        set canonical_code = null,
            code = 'LEGACY_SO_' || substr(replace(v_so_id::text, '-', ''), 1, 6),
            is_active = false,
            is_self_assignable = false
        where id = v_so_id;
    end loop;

    -- Normalize remaining user_roles strings
    delete from public.user_roles dup
    where upper(coalesce(dup.role_code, '')) in ('SO', 'SITE_OFFICER', 'SYSTEM_OPERATOR', 'ITM')
      and dup.role_id <> v_itm_id
      and exists (
          select 1 from public.user_roles keep
          where keep.user_id = dup.user_id and keep.role_id = v_itm_id
      );

    update public.user_roles
    set role_id = v_itm_id,
        role_code = 'IT_MANAGER',
        updated_at = now()
    where upper(coalesce(role_code, '')) in ('SO', 'SITE_OFFICER', 'SYSTEM_OPERATOR', 'ITM')
      and role_code is distinct from 'IT_MANAGER';

    -- Normalize profiles role codes
    update public.profiles
    set active_role_code = case
            when upper(coalesce(active_role_code, '')) in ('SO', 'SITE_OFFICER', 'SYSTEM_OPERATOR', 'ITM', '') then 'IT_MANAGER'
            else upper(active_role_code)
        end,
        primary_role_code = case
            when upper(coalesce(primary_role_code, '')) in ('SO', 'SITE_OFFICER', 'SYSTEM_OPERATOR', 'ITM', '') then 'IT_MANAGER'
            else upper(primary_role_code)
        end,
        updated_at = now()
    where upper(coalesce(active_role_code, '')) in ('SO', 'SITE_OFFICER', 'SYSTEM_OPERATOR', 'ITM', '')
       or upper(coalesce(primary_role_code, '')) in ('SO', 'SITE_OFFICER', 'SYSTEM_OPERATOR', 'ITM', '');

    -- Normalize tasks.target_role_codes arrays
    update public.tasks t
    set target_role_codes = coalesce((
        select jsonb_agg(distinct mapped_code)
        from (
            select case
                when upper(trim(val)) in ('SO', 'SITE_OFFICER', 'SYSTEM_OPERATOR', 'ITM') then 'IT_MANAGER'
                else upper(trim(val))
            end as mapped_code
            from jsonb_array_elements_text(t.target_role_codes) as val
            where trim(val) <> ''
        ) s
    ), '[]'::jsonb)
    where jsonb_typeof(t.target_role_codes) = 'array'
      and (
          t.target_role_codes ? 'SO'
          or t.target_role_codes ? 'SITE_OFFICER'
          or t.target_role_codes ? 'SYSTEM_OPERATOR'
          or t.target_role_codes ? 'ITM'
      );

    -- Apply short role names safely
    update public.work_roles set name = 'VCO', is_self_assignable = true
    where canonical_code = 'VCO'
      and not exists (select 1 from public.work_roles w2 where lower(w2.name) = 'vco' and w2.id <> work_roles.id);

    update public.work_roles set name = 'City Head', is_self_assignable = false
    where canonical_code = 'CITY_HEAD'
      and not exists (select 1 from public.work_roles w2 where lower(w2.name) = 'city head' and w2.id <> work_roles.id);

    update public.work_roles set name = 'ROM', is_self_assignable = false
    where canonical_code = 'ROM'
      and not exists (select 1 from public.work_roles w2 where lower(w2.name) = 'rom' and w2.id <> work_roles.id);

    update public.work_roles set name = 'ZOM', is_self_assignable = false
    where canonical_code = 'ZOM'
      and not exists (select 1 from public.work_roles w2 where lower(w2.name) = 'zom' and w2.id <> work_roles.id);

    update public.work_roles set name = 'SPOC', is_self_assignable = false
    where canonical_code = 'COMMAND_CENTER'
      and not exists (select 1 from public.work_roles w2 where lower(w2.name) = 'spoc' and w2.id <> work_roles.id);

    update public.work_roles set name = 'Admin', is_self_assignable = false
    where canonical_code = 'SUPER_ADMIN'
      and not exists (select 1 from public.work_roles w2 where lower(w2.name) = 'admin' and w2.id <> work_roles.id);
end $$;

-- ----------------------------------------------------------------------------
-- 2. ADD ON LEAVE COLUMNS ON profiles + HELPER RPC + AUTH CONTEXT
-- ----------------------------------------------------------------------------
alter table public.profiles
    add column if not exists is_on_leave boolean not null default false,
    add column if not exists leave_updated_at timestamptz null;

create index if not exists idx_profiles_is_on_leave on public.profiles(is_on_leave);

create or replace function public.get_effective_role_code(p_user uuid default auth.uid())
returns text
language plpgsql
stable
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_profile public.profiles%rowtype;
    v_role text;
    v_raw text;
begin
    if p_user is null then
        return 'IT_MANAGER';
    end if;

    select * into v_profile from public.profiles where id = p_user;
    if not found then
        return 'IT_MANAGER';
    end if;

    v_raw := upper(nullif(trim(coalesce(v_profile.active_role_code, '')), ''));
    if v_raw in ('SO', 'SITE_OFFICER', 'SYSTEM_OPERATOR', 'ITM') then
        v_raw := 'IT_MANAGER';
    end if;

    if v_raw is not null then
        if exists (
            select 1 from public.user_roles ur
            where ur.user_id = p_user
              and (
                  ur.role_code = v_raw
                  or (v_raw = 'IT_MANAGER' and ur.role_code in ('SO', 'SITE_OFFICER', 'SYSTEM_OPERATOR', 'ITM'))
              )
        ) or (v_raw = 'SUPER_ADMIN' and v_profile.app_role = 'admin') then
            return v_raw;
        end if;
    end if;

    select ur.role_code into v_role
    from public.user_roles ur
    where ur.user_id = p_user
    order by ur.is_primary desc, ur.created_at asc
    limit 1;

    if v_role is not null then
        v_role := upper(v_role);
        if v_role in ('SO', 'SITE_OFFICER', 'SYSTEM_OPERATOR', 'ITM') then
            return 'IT_MANAGER';
        end if;
        return v_role;
    end if;

    if v_profile.app_role = 'admin' then
        return 'SUPER_ADMIN';
    end if;
    return 'IT_MANAGER';
end;
$$;

create or replace function public.get_my_authorization_context()
returns jsonb
language plpgsql
stable
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_caller uuid := auth.uid();
    v_profile public.profiles%rowtype;
    v_active_role text;
    v_primary_role text;
    v_roles jsonb;
begin
    if v_caller is null then
        raise exception 'Authentication required';
    end if;

    select * into v_profile from public.profiles where id = v_caller;
    if not found then
        raise exception 'Profile not found';
    end if;

    v_active_role := public.get_effective_role_code(v_caller);
    v_primary_role := upper(coalesce(nullif(trim(v_profile.primary_role_code), ''), v_active_role));
    if v_primary_role in ('SO', 'SITE_OFFICER', 'SYSTEM_OPERATOR', 'ITM') then
        v_primary_role := 'IT_MANAGER';
    end if;

    select coalesce(jsonb_agg(jsonb_build_object(
        'role_id', ur.role_id,
        'role_code', case
            when upper(ur.role_code) in ('SO', 'SITE_OFFICER', 'SYSTEM_OPERATOR', 'ITM') then 'IT_MANAGER'
            else upper(ur.role_code)
        end,
        'role_name', wr.name,
        'hierarchy_level', wr.hierarchy_level,
        'default_scope_level', wr.default_scope_level,
        'is_primary', ur.is_primary
    ) order by wr.hierarchy_level desc, ur.is_primary desc), '[]'::jsonb)
    into v_roles
    from public.user_roles ur
    join public.work_roles wr on wr.id = ur.role_id
    where ur.user_id = v_caller;

    if jsonb_array_length(v_roles) = 0 then
        v_roles := jsonb_build_array(jsonb_build_object(
            'role_id', case when v_active_role = 'SUPER_ADMIN' then '11111111-1111-4111-8111-111111111112' else '11111111-1111-4111-8111-111111111102' end,
            'role_code', v_active_role,
            'role_name', case v_active_role
                when 'IT_MANAGER' then 'IT Manager'
                when 'VCO' then 'VCO'
                when 'CITY_HEAD' then 'City Head'
                when 'ROM' then 'ROM'
                when 'ZOM' then 'ZOM'
                when 'COMMAND_CENTER' then 'SPOC'
                when 'SUPER_ADMIN' then 'Admin'
                else v_active_role
            end,
            'hierarchy_level', case when v_active_role = 'SUPER_ADMIN' then 100 else 10 end,
            'default_scope_level', case when v_active_role = 'SUPER_ADMIN' then 'GLOBAL' else 'CENTRE' end,
            'is_primary', true
        ));
    end if;

    return jsonb_build_object(
        'user_id', v_caller,
        'employee_id', v_profile.employee_id,
        'full_name', v_profile.full_name,
        'is_active', v_profile.is_active,
        'is_on_leave', coalesce(v_profile.is_on_leave, false),
        'needs_scope_review', coalesce(v_profile.needs_scope_review, false),
        'active_role_code', v_active_role,
        'primary_role_code', v_primary_role,
        'assigned_roles', v_roles,
        'scope', jsonb_build_object(
            'effective_scope_level', case
                when v_active_role in ('SUPER_ADMIN', 'COMMAND_CENTER') then 'GLOBAL'
                when v_active_role = 'ZOM' then 'ZONE'
                when v_active_role = 'ROM' then 'STATE'
                when v_active_role in ('CITY_HEAD', 'VCO') then 'CITY'
                else 'CENTRE'
            end,
            'zone_id', v_profile.zone_id,
            'zone_name', v_profile.zone_name,
            'state_id', v_profile.state_id,
            'state_name', v_profile.state_name,
            'city_id', v_profile.city_id,
            'city_name', v_profile.city_name,
            'centre_id', v_profile.centre_id,
            'centre_code', v_profile.centre_code,
            'assigned_lan', v_profile.assigned_lan
        ),
        'role_version', v_profile.role_version,
        'scope_version', v_profile.scope_version,
        'server_now', now()
    );
end;
$$;

revoke all on function public.get_my_authorization_context() from public;
grant execute on function public.get_my_authorization_context() to authenticated;

create or replace function public.set_my_leave_status(p_is_on_leave boolean)
returns jsonb
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_caller uuid := (select auth.uid());
    v_profile public.profiles%rowtype;
    v_leave boolean := coalesce(p_is_on_leave, false);
begin
    if v_caller is null then
        raise exception 'Authentication required';
    end if;

    select * into v_profile from public.profiles where id = v_caller;
    if not found or not coalesce(v_profile.is_active, false) then
        raise exception 'Active profile required';
    end if;

    perform set_config('taskz.bypass_rbac_guard', '1', true);

    update public.profiles
    set is_on_leave = v_leave,
        leave_updated_at = now(),
        scope_version = coalesce(scope_version, 1) + 1,
        updated_at = now()
    where id = v_caller;

    insert into public.user_sync_state(user_id, active_role_code, sync_version, updated_at)
    values (v_caller, public.get_effective_role_code(v_caller), 1, now())
    on conflict (user_id) do update
    set sync_version = public.user_sync_state.sync_version + 1,
        updated_at = now();

    return public.get_my_authorization_context();
end;
$$;

revoke all on function public.set_my_leave_status(boolean) from public;
grant execute on function public.set_my_leave_status(boolean) to authenticated;

-- ----------------------------------------------------------------------------
-- 3. SCOPE FUNCTIONS WITHOUT SO (CONSOLIDATED TO IT_MANAGER)
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
    v_target_city uuid := p_city_id;
    v_target_state uuid := p_state_id;
    v_target_zone uuid := p_zone_id;
    v_prof_city uuid;
    v_prof_state uuid;
    v_prof_zone uuid;
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

    if v_scope = 'GLOBAL' then
        return p_zone_id is null
           and p_state_id is null
           and p_city_id is null
           and p_centre_id is null;
    end if;

    if p_centre_id is not null then
        select c.city_id, c.state_id, c.zone_id
        into v_target_city, v_target_state, v_target_zone
        from public.org_centres c
        where c.id = p_centre_id;

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

    if v_role = 'ZOM' then
        if v_prof_zone is null then
            return false;
        end if;
        return v_target_zone is not null and v_target_zone = v_prof_zone;
    end if;

    if v_role = 'ROM' then
        if v_prof_state is null then
            return false;
        end if;
        if v_scope = 'ZONE' then
            return v_prof_zone is not null and v_target_zone is not null and v_target_zone = v_prof_zone;
        end if;
        return v_target_state is not null and v_target_state = v_prof_state;
    end if;

    if v_role in ('CITY_HEAD', 'VCO', 'IT_MANAGER') then
        if v_prof_city is null then
            if v_role in ('VCO', 'IT_MANAGER') and v_profile.centre_id is not null then
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

    return false;
end;
$$;

revoke all on function public.can_access_org_scope(text, uuid, uuid, uuid, uuid, uuid) from public;
grant execute on function public.can_access_org_scope(text, uuid, uuid, uuid, uuid, uuid) to authenticated;

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
        if v_role not in ('VCO', 'CITY_HEAD', 'ROM', 'ZOM', 'COMMAND_CENTER', 'SUPER_ADMIN') then
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
-- 4. STRICT ROLE-SPECIFIC OPERATIONAL LOCATION (ZOM=ZONE, ROM=STATE,
--    CITY_HEAD=CITY, VCO=CENTRE, IT_MANAGER=CENTRE+LAN)
-- ----------------------------------------------------------------------------
drop function if exists public.set_my_operational_location(text, uuid, uuid, uuid, text, text);
drop function if exists public.set_my_operational_location(text, uuid, uuid, uuid, text, text, uuid);

create or replace function public.set_my_operational_location(
    p_role_code text default null,
    p_state_id uuid default null,
    p_city_id uuid default null,
    p_centre_id uuid default null,
    p_centre_code text default null,
    p_lan_number text default null,
    p_zone_id uuid default null
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_caller uuid := (select auth.uid());
    v_profile public.profiles%rowtype;
    v_requested_role text := upper(nullif(trim(coalesce(p_role_code, '')), ''));
    v_final_role text;
    v_role_id uuid;
    v_has_assigned_role boolean := false;
    v_zone_id uuid := p_zone_id;
    v_zone_name text;
    v_state_id uuid := p_state_id;
    v_state_name text;
    v_city_id uuid := p_city_id;
    v_city_name text;
    v_centre_id uuid := p_centre_id;
    v_centre_code text;
    v_clean_centre_code text := upper(nullif(trim(coalesce(p_centre_code, '')), ''));
    v_clean_lan text := nullif(trim(coalesce(p_lan_number, '')), '');
begin
    if v_caller is null then
        raise exception 'Authentication required';
    end if;

    select * into v_profile from public.profiles where id = v_caller;
    if not found or not coalesce(v_profile.is_active, false) then
        raise exception 'Active profile required';
    end if;

    if v_requested_role in ('SO', 'SITE_OFFICER', 'SYSTEM_OPERATOR', 'ITM') then
        v_requested_role := 'IT_MANAGER';
    end if;

    v_final_role := coalesce(v_requested_role, public.get_effective_role_code(v_caller));

    if v_requested_role is not null then
        if v_requested_role in ('IT_MANAGER', 'VCO') then
            select id into v_role_id
            from public.work_roles
            where canonical_code = v_requested_role or upper(coalesce(code, '')) = v_requested_role
            order by sort_order asc
            limit 1;

            if v_role_id is null then
                v_role_id := case when v_requested_role = 'VCO'
                    then '11111111-1111-4111-8111-111111111106'::uuid
                    else '11111111-1111-4111-8111-111111111102'::uuid
                end;
            end if;
        else
            select exists (
                select 1 from public.user_roles ur
                where ur.user_id = v_caller and ur.role_code = v_requested_role
            ) or (v_requested_role = 'SUPER_ADMIN' and v_profile.app_role = 'admin')
            into v_has_assigned_role;

            if not v_has_assigned_role then
                raise exception 'Forbidden: cannot self-assign privileged role %', v_requested_role;
            end if;
        end if;
    end if;

    -- Resolve strictly according to the effective role
    if v_final_role = 'ZOM' then
        v_zone_id := coalesce(p_zone_id, v_profile.zone_id);
        if v_zone_id is null then
            raise exception 'Zone selection is required for ZOM';
        end if;
        select zn.id, zn.name into v_zone_id, v_zone_name
        from public.org_zones zn where zn.id = v_zone_id and zn.is_active = true limit 1;
        if not found then
            raise exception 'Invalid or inactive zone selected';
        end if;
        v_state_id := null; v_state_name := null;
        v_city_id := null; v_city_name := null;
        v_centre_id := null; v_centre_code := null;
        v_clean_lan := null;

    elsif v_final_role = 'ROM' then
        v_state_id := coalesce(p_state_id, v_profile.state_id);
        if v_state_id is null then
            raise exception 'State selection is required for ROM';
        end if;
        select st.id, st.name, st.zone_id, zn.name
        into v_state_id, v_state_name, v_zone_id, v_zone_name
        from public.org_states st
        join public.org_zones zn on zn.id = st.zone_id
        where st.id = v_state_id and st.is_active = true
        limit 1;
        if not found then
            raise exception 'Invalid or inactive state selected';
        end if;
        v_city_id := null; v_city_name := null;
        v_centre_id := null; v_centre_code := null;
        v_clean_lan := null;

    elsif v_final_role = 'CITY_HEAD' then
        v_city_id := coalesce(p_city_id, v_profile.city_id);
        if v_city_id is null then
            raise exception 'City selection is required for City Head';
        end if;
        select ct.id, ct.name, ct.state_id, st.name, ct.zone_id, zn.name
        into v_city_id, v_city_name, v_state_id, v_state_name, v_zone_id, v_zone_name
        from public.org_cities ct
        join public.org_states st on st.id = ct.state_id
        join public.org_zones zn on zn.id = ct.zone_id
        where ct.id = v_city_id and ct.is_active = true
        limit 1;
        if not found then
            raise exception 'Invalid or inactive city selected';
        end if;
        v_centre_id := null; v_centre_code := null;
        v_clean_lan := null;

    elsif v_final_role in ('VCO', 'IT_MANAGER') then
        if v_centre_id is not null then
            select c.id, c.code, c.city_id, ct.name, c.state_id, st.name, c.zone_id, zn.name
            into v_centre_id, v_centre_code, v_city_id, v_city_name, v_state_id, v_state_name, v_zone_id, v_zone_name
            from public.org_centres c
            join public.org_cities ct on ct.id = c.city_id
            join public.org_states st on st.id = c.state_id
            join public.org_zones zn on zn.id = c.zone_id
            where c.id = v_centre_id and c.is_active = true
            limit 1;
            if not found then
                raise exception 'Invalid or inactive centre selected';
            end if;
        elsif v_clean_centre_code is not null then
            select c.id, c.code, c.city_id, ct.name, c.state_id, st.name, c.zone_id, zn.name
            into v_centre_id, v_centre_code, v_city_id, v_city_name, v_state_id, v_state_name, v_zone_id, v_zone_name
            from public.org_centres c
            join public.org_cities ct on ct.id = c.city_id
            join public.org_states st on st.id = c.state_id
            join public.org_zones zn on zn.id = c.zone_id
            where c.is_active = true
              and (upper(c.code) = v_clean_centre_code or upper(coalesce(c.external_code, '')) = v_clean_centre_code)
            limit 1;
        end if;

        if v_centre_id is null and v_city_id is not null then
            select ct.id, ct.name, ct.state_id, st.name, ct.zone_id, zn.name
            into v_city_id, v_city_name, v_state_id, v_state_name, v_zone_id, v_zone_name
            from public.org_cities ct
            join public.org_states st on st.id = ct.state_id
            join public.org_zones zn on zn.id = ct.zone_id
            where ct.id = v_city_id and ct.is_active = true
            limit 1;
            if not found then
                raise exception 'Invalid or inactive city selected';
            end if;

            select c.id, c.code
            into v_centre_id, v_centre_code
            from public.org_centres c
            where c.city_id = v_city_id and c.is_active = true
            order by c.code asc
            limit 1;
        elsif v_centre_id is null and v_city_id is null and v_state_id is not null then
            select st.id, st.name, st.zone_id, zn.name
            into v_state_id, v_state_name, v_zone_id, v_zone_name
            from public.org_states st
            join public.org_zones zn on zn.id = st.zone_id
            where st.id = v_state_id and st.is_active = true
            limit 1;
            if not found then
                raise exception 'Invalid or inactive state selected';
            end if;
        end if;

        if v_final_role = 'VCO' then
            v_clean_lan := null;
        end if;
    else
        -- COMMAND_CENTER / SUPER_ADMIN: Global access, no LAN needed
        v_clean_lan := null;
    end if;

    perform set_config('taskz.bypass_rbac_guard', '1', true);

    if v_requested_role in ('IT_MANAGER', 'VCO') and v_role_id is not null then
        insert into public.user_roles (user_id, role_id, role_code, is_primary, assigned_by)
        values (v_caller, v_role_id, v_requested_role, true, v_caller)
        on conflict (user_id, role_id) do update
        set role_code = excluded.role_code,
            is_primary = true,
            updated_at = now();
    end if;

    update public.profiles
    set active_role_code = v_final_role,
        primary_role_code = case
            when primary_role_code in ('CITY_HEAD', 'ROM', 'ZOM', 'COMMAND_CENTER', 'SUPER_ADMIN') then primary_role_code
            else v_final_role
        end,
        zone_id = case
            when v_final_role in ('COMMAND_CENTER', 'SUPER_ADMIN') then zone_id
            else coalesce(v_zone_id, zone_id)
        end,
        zone_name = case
            when v_final_role in ('COMMAND_CENTER', 'SUPER_ADMIN') then zone_name
            else coalesce(v_zone_name, zone_name)
        end,
        state_id = case
            when v_final_role = 'ZOM' then null
            when v_final_role in ('COMMAND_CENTER', 'SUPER_ADMIN') then state_id
            else coalesce(v_state_id, state_id)
        end,
        state_name = case
            when v_final_role = 'ZOM' then null
            when v_final_role in ('COMMAND_CENTER', 'SUPER_ADMIN') then state_name
            else coalesce(v_state_name, state_name)
        end,
        city_id = case
            when v_final_role in ('ZOM', 'ROM') then null
            when v_final_role in ('COMMAND_CENTER', 'SUPER_ADMIN') then city_id
            when v_city_id is not null then v_city_id
            when p_state_id is not null and p_state_id is distinct from state_id then null
            else city_id
        end,
        city_name = case
            when v_final_role in ('ZOM', 'ROM') then null
            when v_final_role in ('COMMAND_CENTER', 'SUPER_ADMIN') then city_name
            when v_city_id is not null then v_city_name
            when p_state_id is not null and p_state_id is distinct from state_id then null
            else city_name
        end,
        centre_id = case
            when v_final_role in ('ZOM', 'ROM', 'CITY_HEAD') then null
            when v_final_role in ('COMMAND_CENTER', 'SUPER_ADMIN') then centre_id
            when v_centre_id is not null then v_centre_id
            when v_city_id is not null and v_city_id is distinct from city_id then null
            else centre_id
        end,
        centre_code = case
            when v_final_role in ('ZOM', 'ROM', 'CITY_HEAD') then null
            when v_final_role in ('COMMAND_CENTER', 'SUPER_ADMIN') then centre_code
            when v_centre_code is not null then v_centre_code
            when v_clean_centre_code is not null then v_clean_centre_code
            when v_city_id is not null and v_city_id is distinct from city_id then null
            else centre_code
        end,
        assigned_lan = case
            when v_final_role = 'IT_MANAGER' then coalesce(v_clean_lan, assigned_lan)
            else null
        end,
        needs_scope_review = false,
        role_version = coalesce(role_version, 1) + 1,
        scope_version = coalesce(scope_version, 1) + 1,
        updated_at = now()
    where id = v_caller;

    insert into public.user_sync_state(user_id, active_role_code, sync_version, updated_at)
    values (v_caller, v_final_role, 1, now())
    on conflict (user_id) do update
    set active_role_code = excluded.active_role_code,
        sync_version = public.user_sync_state.sync_version + 1,
        updated_at = now();

    return public.get_my_authorization_context();
end;
$$;

revoke all on function public.set_my_operational_location(text, uuid, uuid, uuid, text, text, uuid) from public;
grant execute on function public.set_my_operational_location(text, uuid, uuid, uuid, text, text, uuid) to authenticated;

-- ----------------------------------------------------------------------------
-- 5. UPGRADE search_scoped_members TO INCLUDE is_on_leave & CONSOLIDATED ROLES
-- ----------------------------------------------------------------------------
drop function if exists public.search_scoped_members(text, text, uuid, uuid, uuid, integer);

create or replace function public.search_scoped_members(
    p_query text default '',
    p_role_code text default null,
    p_centre_id uuid default null,
    p_state_id uuid default null,
    p_city_id uuid default null,
    p_limit integer default 100
)
returns table (
    id uuid,
    employee_id text,
    full_name text,
    email text,
    mobile text,
    app_role text,
    active_role_code text,
    assigned_roles text[],
    centre_id uuid,
    centre_code text,
    city_id uuid,
    city_name text,
    state_id uuid,
    state_name text,
    zone_id uuid,
    zone_name text,
    is_active boolean,
    is_on_leave boolean
)
language plpgsql
stable
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_caller uuid := auth.uid();
    v_caller_role text;
    v_me public.profiles%rowtype;
    v_me_city uuid;
    v_me_state uuid;
    v_me_zone uuid;
    v_q text := trim(coalesce(p_query, ''));
    v_role_filter text := upper(nullif(trim(coalesce(p_role_code, '')), ''));
begin
    if v_caller is null then
        raise exception 'Authentication required';
    end if;
    select * into v_me from public.profiles where profiles.id = v_caller;
    if not found or not v_me.is_active then
        raise exception 'Active profile required';
    end if;

    v_caller_role := public.get_effective_role_code(v_caller);
    if v_role_filter in ('SO', 'SITE_OFFICER', 'SYSTEM_OPERATOR', 'ITM') then
        v_role_filter := 'IT_MANAGER';
    end if;

    v_me_city := v_me.city_id;
    v_me_state := v_me.state_id;
    v_me_zone := v_me.zone_id;
    if v_me.centre_id is not null and (v_me_city is null or v_me_state is null or v_me_zone is null) then
        select coalesce(v_me_city, oc.city_id), coalesce(v_me_state, oc.state_id), coalesce(v_me_zone, oc.zone_id)
        into v_me_city, v_me_state, v_me_zone
        from public.org_centres oc where oc.id = v_me.centre_id;
    elsif v_me_city is not null and (v_me_state is null or v_me_zone is null) then
        select coalesce(v_me_state, c.state_id), coalesce(v_me_zone, c.zone_id)
        into v_me_state, v_me_zone
        from public.org_cities c where c.id = v_me_city;
    end if;

    return query
    select
        p.id,
        p.employee_id,
        p.full_name,
        coalesce(p.email, u.email)::text as email,
        case
            when v_caller_role in ('SUPER_ADMIN', 'COMMAND_CENTER', 'CITY_HEAD', 'ROM', 'ZOM', 'VCO', 'IT_MANAGER')
                 or p.id = v_caller
            then p.mobile
            else null
        end as mobile,
        p.app_role,
        public.get_effective_role_code(p.id) as active_role_code,
        coalesce((select array_agg(
            case when upper(ur.role_code) in ('SO', 'SITE_OFFICER', 'SYSTEM_OPERATOR', 'ITM') then 'IT_MANAGER' else upper(ur.role_code) end
            order by ur.is_primary desc, ur.role_code)
                  from public.user_roles ur where ur.user_id = p.id), array['IT_MANAGER']) as assigned_roles,
        p.centre_id,
        coalesce(p.centre_code, poc.code) as centre_code,
        coalesce(p.city_id, poc.city_id) as city_id,
        coalesce(p.city_name, pct.name) as city_name,
        coalesce(p.state_id, poc.state_id, pct.state_id) as state_id,
        coalesce(p.state_name, pst.name) as state_name,
        coalesce(p.zone_id, poc.zone_id, pct.zone_id, pst.zone_id) as zone_id,
        coalesce(p.zone_name, pzn.name) as zone_name,
        p.is_active,
        coalesce(p.is_on_leave, false) as is_on_leave
    from public.profiles p
    left join auth.users u on u.id = p.id
    left join public.org_centres poc on poc.id = p.centre_id or (
        p.centre_id is null
        and nullif(trim(coalesce(p.centre_code, '')), '') is not null
        and (
            upper(poc.code) = upper(trim(p.centre_code))
            or upper(coalesce(poc.external_code, '')) = upper(trim(p.centre_code))
        )
    )
    left join public.org_cities pct on pct.id = coalesce(p.city_id, poc.city_id)
    left join public.org_states pst on pst.id = coalesce(p.state_id, poc.state_id, pct.state_id)
    left join public.org_zones pzn on pzn.id = coalesce(p.zone_id, poc.zone_id, pct.zone_id, pst.zone_id)
    where (v_caller_role = 'SUPER_ADMIN' or p.is_active = true)
      and (
          v_caller_role in ('SUPER_ADMIN', 'COMMAND_CENTER')
          or (v_caller_role = 'ZOM' and v_me_zone is not null and coalesce(p.zone_id, poc.zone_id, pct.zone_id, pst.zone_id) = v_me_zone)
          or (v_caller_role in ('ROM', 'CITY_HEAD', 'VCO') and (
              (v_me_state is not null and coalesce(p.state_id, poc.state_id, pct.state_id) = v_me_state)
              or (v_me_city is not null and coalesce(p.city_id, poc.city_id) = v_me_city)
              or p.id = v_caller
          ))
          or (
              v_caller_role = 'IT_MANAGER' and (
                  (v_me_city is not null and coalesce(p.city_id, poc.city_id) = v_me_city)
                  or (v_me_city is null and v_me.centre_id is not null and p.centre_id = v_me.centre_id)
                  or (coalesce(p.city_id, poc.city_id) is null and p.centre_id is null)
                  or p.id = v_caller
              )
          )
      )
      and (p_state_id is null or coalesce(p.state_id, poc.state_id, pct.state_id) = p_state_id)
      and (p_city_id is null or coalesce(p.city_id, poc.city_id) = p_city_id)
      and (p_centre_id is null or coalesce(p.centre_id, poc.id) = p_centre_id)
      and (v_role_filter is null or (
          public.get_effective_role_code(p.id) = v_role_filter
          or exists (
              select 1 from public.user_roles ur
              where ur.user_id = p.id
                and (
                    ur.role_code = v_role_filter
                    or (v_role_filter = 'IT_MANAGER' and ur.role_code in ('SO', 'SITE_OFFICER', 'SYSTEM_OPERATOR', 'ITM'))
                )
          )
      ))
      and (
          v_q = ''
          or p.full_name ilike '%' || v_q || '%'
          or p.employee_id ilike '%' || v_q || '%'
          or coalesce(p.email, u.email, '') ilike '%' || v_q || '%'
          or coalesce(p.centre_code, poc.code, '') ilike '%' || v_q || '%'
          or coalesce(p.city_name, pct.name, '') ilike '%' || v_q || '%'
          or coalesce(p.state_name, pst.name, '') ilike '%' || v_q || '%'
      )
    order by coalesce(p.is_on_leave, false) asc, (p.id = v_caller) asc, p.full_name asc
    limit least(greatest(coalesce(p_limit, 100), 1), 250);
end;
$$;

revoke all on function public.search_scoped_members(text, text, uuid, uuid, uuid, integer) from public;
grant execute on function public.search_scoped_members(text, text, uuid, uuid, uuid, integer) to authenticated;

-- ----------------------------------------------------------------------------
-- 6. ENFORCE STRICT LOWER-SCOPE CLEARING IN admin_update_user_access
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
        raise exception 'Forbidden: only Admin can manage user roles and scope';
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
        if v_role_code in ('SO', 'SITE_OFFICER', 'SYSTEM_OPERATOR', 'ITM') then
            v_role_code := 'IT_MANAGER';
        end if;
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
    if v_active_role in ('SO', 'SITE_OFFICER', 'SYSTEM_OPERATOR', 'ITM') then
        v_active_role := 'IT_MANAGER';
    end if;
    if not (v_active_role = any(v_clean_roles)) then
        v_active_role := v_primary_role;
    end if;
    v_is_super := ('SUPER_ADMIN' = any(v_clean_roles));

    if v_active_role = 'ZOM' then
        v_state_id := null; v_city_id := null; v_centre_id := null;
    elsif v_active_role = 'ROM' then
        v_city_id := null; v_centre_id := null;
    elsif v_active_role = 'CITY_HEAD' then
        v_centre_id := null;
    elsif v_active_role in ('COMMAND_CENTER', 'SUPER_ADMIN') then
        v_zone_id := null; v_state_id := null; v_city_id := null; v_centre_id := null;
    end if;

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
        centre_code = case when v_centre_id is not null then v_centre_code else null end,
        assigned_lan = case when v_active_role = 'IT_MANAGER' then assigned_lan else null end,
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
        'Role or Scope Updated',
        'Active role is now ' || v_active_role || '.',
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
-- 7. ON LEAVE BLOCKING IN TASK ASSIGNMENT, CREATION, SHIFT COPY & GROUP SYNC
-- ----------------------------------------------------------------------------
create or replace function public.assign_user_to_task(
    p_task_id uuid,
    p_user_id uuid,
    p_role_id uuid default null
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_caller uuid := auth.uid();
    v_caller_name text;
    v_task public.tasks%rowtype;
    v_target public.profiles%rowtype;
    v_role_name text := null;
begin
    if v_caller is null then
        raise exception 'Authentication required to assign users';
    end if;

    select * into v_task from public.tasks where id = p_task_id;
    if not found then
        raise exception 'Task % not found', p_task_id;
    end if;

    if not public.can_manage_task(p_task_id) then
        raise exception 'Not authorized to assign users to this task';
    end if;

    select * into v_target from public.profiles where id = p_user_id;
    if not found then
        raise exception 'Target user % does not exist in profiles', p_user_id;
    end if;
    if not coalesce(v_target.is_active, false) then
        raise exception 'Target user % is inactive', p_user_id;
    end if;
    if coalesce(v_target.is_on_leave, false) then
        raise exception 'User "%" is currently on leave and cannot be assigned', coalesce(v_target.full_name, v_target.employee_id);
    end if;

    if p_role_id is not null then
        select name into v_role_name from public.work_roles where id = p_role_id;
        if v_role_name is null then
            raise exception 'Specified role % does not exist in work_roles', p_role_id;
        end if;
    end if;

    select full_name into v_caller_name from public.profiles where id = v_caller;

    insert into public.task_participants(
        task_id, user_id, role_id, source, status, assigned_by, joined_at
    ) values (
        p_task_id, p_user_id, p_role_id, 'assigned', 'active', v_caller, now()
    ) on conflict (task_id, user_id) where status = 'active'
    do update set
        role_id = coalesce(excluded.role_id, public.task_participants.role_id),
        status = 'active',
        assigned_by = v_caller,
        left_at = null,
        left_reason = null;

    perform public.create_scoped_notification(
        p_user_id,
        'task_assigned',
        'task_assignment',
        'Assigned: ' || v_task.title,
        coalesce(v_caller_name, 'Manager') || ' assigned you to "' || v_task.title || '"',
        p_task_id,
        null,
        v_task.group_id,
        'task_assigned:' || p_task_id::text || ':' || p_user_id::text,
        'taskz://task/' || p_task_id::text,
        coalesce(v_task.scope_level, 'CENTRE'),
        jsonb_build_object('task_id', p_task_id)
    );

    if v_task.visibility = 'restricted' then
        insert into public.task_visible_users(task_id, user_id, added_by)
        values (p_task_id, p_user_id, v_caller)
        on conflict (task_id, user_id) do nothing;
    end if;

    insert into public.task_activity_log(task_id, actor_user_id, target_user_id, event_type, message)
    values (
        p_task_id,
        v_caller,
        p_user_id,
        'USER_ASSIGNED',
        coalesce(v_caller_name, 'Manager') || ' assigned ' || coalesce(v_target.full_name, 'member')
    );

    return jsonb_build_object(
        'success', true,
        'task_id', p_task_id,
        'user_id', p_user_id,
        'role_id', p_role_id
    );
end;
$$;

revoke all on function public.assign_user_to_task(uuid, uuid, uuid) from public;
grant execute on function public.assign_user_to_task(uuid, uuid, uuid) to authenticated;

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
    v_raw_centre_code text;
    v_zone_id uuid;
    v_state_id uuid;
    v_city_id uuid;
    v_centre_id uuid;
    v_oc public.org_centres%rowtype;
    v_ct public.org_cities%rowtype;
    v_st public.org_states%rowtype;
    v_target_role_codes jsonb := '[]'::jsonb;
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
    elsif v_scope_level = 'STATE' and v_caller_role not in ('VCO', 'CITY_HEAD', 'ROM', 'ZOM', 'COMMAND_CENTER', 'SUPER_ADMIN') then
        raise exception 'Forbidden: role % cannot create STATE tasks', v_caller_role;
    elsif v_scope_level = 'CITY' and v_caller_role not in ('VCO', 'CITY_HEAD', 'ROM', 'ZOM', 'COMMAND_CENTER', 'SUPER_ADMIN') then
        raise exception 'Forbidden: role % cannot create CITY tasks', v_caller_role;
    end if;

    v_raw_centre_id := nullif(p_task->>'centre_id', '')::uuid;
    v_raw_centre_code := upper(nullif(trim(p_task->>'centre_code'), ''));
    v_raw_city_id := nullif(p_task->>'city_id', '')::uuid;
    v_raw_state_id := nullif(p_task->>'state_id', '')::uuid;
    v_raw_zone_id := nullif(p_task->>'zone_id', '')::uuid;

    if v_scope_level = 'GLOBAL' then
        v_zone_id := null; v_state_id := null; v_city_id := null; v_centre_id := null;
    elsif v_scope_level = 'ZONE' then
        v_zone_id := coalesce(v_raw_zone_id, v_caller_profile.zone_id);
        if v_zone_id is null or not exists (select 1 from public.org_zones z where z.id = v_zone_id and z.is_active = true) then
            raise exception 'Forbidden: ZONE task requires a valid active zone_id';
        end if;
        v_state_id := null; v_city_id := null; v_centre_id := null;
    elsif v_scope_level = 'STATE' then
        v_state_id := coalesce(v_raw_state_id, v_caller_profile.state_id);
        if v_state_id is null and v_caller_profile.city_id is not null then
            select c.state_id into v_state_id from public.org_cities c where c.id = v_caller_profile.city_id;
        end if;
        if v_state_id is null and v_caller_profile.centre_id is not null then
            select oc.state_id into v_state_id from public.org_centres oc where oc.id = v_caller_profile.centre_id;
        end if;
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
        v_zone_id := v_st.zone_id; v_city_id := null; v_centre_id := null;
    elsif v_scope_level = 'CITY' then
        v_city_id := coalesce(v_raw_city_id, v_caller_profile.city_id);
        if v_city_id is null and v_caller_profile.centre_id is not null then
            select oc.city_id into v_city_id from public.org_centres oc where oc.id = v_caller_profile.centre_id;
        end if;
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
        v_state_id := v_ct.state_id; v_zone_id := v_ct.zone_id; v_centre_id := null;
    elsif v_scope_level = 'CENTRE' then
        v_centre_id := v_raw_centre_id;
        if v_centre_id is null and v_raw_centre_code is not null then
            select oc.id into v_centre_id
            from public.org_centres oc
            where oc.is_active = true
              and (upper(oc.code) = v_raw_centre_code or upper(coalesce(oc.external_code, '')) = v_raw_centre_code)
            limit 1;
        end if;
        v_centre_id := coalesce(v_centre_id, v_caller_profile.centre_id);
        if v_centre_id is null and nullif(trim(coalesce(v_caller_profile.centre_code, '')), '') is not null then
            select oc.id into v_centre_id
            from public.org_centres oc
            where oc.is_active = true
              and (
                  upper(oc.code) = upper(trim(v_caller_profile.centre_code))
                  or upper(coalesce(oc.external_code, '')) = upper(trim(v_caller_profile.centre_code))
              )
            limit 1;
        end if;
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
        v_city_id := v_oc.city_id; v_state_id := v_oc.state_id; v_zone_id := v_oc.zone_id;
    end if;

    if not public.can_create_task_in_scope(v_scope_level, v_zone_id, v_state_id, v_city_id, v_centre_id, v_caller) then
        raise exception 'Forbidden: caller scope does not authorize target % scope', v_scope_level;
    end if;

    if p_task ? 'target_role_codes' and jsonb_typeof(p_task->'target_role_codes') = 'array' then
        select coalesce(jsonb_agg(distinct case
            when upper(trim(val)) in ('SO', 'SITE_OFFICER', 'SYSTEM_OPERATOR', 'ITM') then 'IT_MANAGER'
            else upper(trim(val))
        end), '[]'::jsonb)
        into v_target_role_codes
        from jsonb_array_elements_text(p_task->'target_role_codes') as val
        where trim(val) <> '';
    end if;

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
    if v_task_type = 'PROGRESS' then
        v_response_type := 'PERCENTAGE';
    end if;
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

    if p_task ? 'assigned_user_ids' and jsonb_typeof(p_task->'assigned_user_ids') = 'array' then
        for v_assigned_user_id in select jsonb_array_elements_text(p_task->'assigned_user_ids')
        loop
            v_assigned_uuid := v_assigned_user_id::uuid;
            select * into v_assigned_prof from public.profiles where id = v_assigned_uuid;
            if not found or not coalesce(v_assigned_prof.is_active, false) then
                raise exception 'Assigned user "%" is not active', v_assigned_user_id;
            end if;
            if coalesce(v_assigned_prof.is_on_leave, false) then
                raise exception 'User "%" is currently on leave and cannot be assigned', coalesce(v_assigned_prof.full_name, v_assigned_user_id);
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
                'New Task: ' || v_title,
                coalesce(v_caller_profile.full_name, 'Manager') || ' assigned you to "' || v_title || '"',
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

    if v_visibility = 'everyone' then
        loop
            v_batch_rows := 0;
            for v_recipient in
                select p.id
                from public.profiles p
                where p.is_active = true
                  and coalesce(p.is_on_leave, false) = false
                  and p.id <> v_caller
                  and p.id > v_last_recipient_id
                  and (
                      jsonb_array_length(v_target_role_codes) = 0
                      or v_target_role_codes ? public.get_effective_role_code(p.id)
                      or exists (
                          select 1 from public.user_roles ur
                          where ur.user_id = p.id
                            and v_target_role_codes ? (
                                case when upper(ur.role_code) in ('SO', 'SITE_OFFICER', 'SYSTEM_OPERATOR', 'ITM') then 'IT_MANAGER' else upper(ur.role_code) end
                            )
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
                      or (v_scope_level = 'CENTRE' and (
                          (v_centre_id is not null and p.centre_id = v_centre_id)
                          or (v_city_id is not null and (
                              p.city_id = v_city_id
                              or exists (select 1 from public.org_centres oc where oc.id = p.centre_id and oc.city_id = v_city_id)
                          ))
                      ))
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
                    coalesce(v_caller_profile.full_name, 'Operations') || ' published "' || v_title || '"',
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

-- Exclude on-leave users in copy_shift and group/task sync triggers
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
set search_path = public, auth, pg_temp
as $$
declare
    v_source public.shifts%rowtype;
    v_new_shift uuid;
    v_old_task record;
    v_new_task uuid;
    v_caller uuid := (select auth.uid());
    v_assignee record;
begin
    select * into v_source from public.shifts where id = p_source_shift;
    if not found then
        raise exception 'Source shift not found';
    end if;

    if not public.can_manage_group(v_source.group_id) then
        raise exception 'Unauthorized to manage shifts in this group';
    end if;

    insert into public.shifts(
        group_id, name, shift_number, starts_at, ends_at, notes, status, copied_from_shift_id, created_by
    ) values (
        v_source.group_id,
        trim(p_new_name),
        coalesce(v_source.shift_number, 0) + 1,
        v_source.starts_at + make_interval(mins => p_offset_minutes),
        v_source.ends_at + make_interval(mins => p_offset_minutes),
        'Copied from ' || v_source.name,
        'scheduled',
        v_source.id,
        v_caller
    ) returning id into v_new_shift;

    for v_old_task in
        select * from public.tasks
        where shift_id = p_source_shift
          and deleted_at is null
        order by starts_at nulls last, created_at
    loop
        insert into public.tasks(
            group_id, shift_id, title, description, location, starts_at, due_at,
            priority, status, visibility, allow_self_join, join_requires_approval,
            notify_creator_on_join, requires_completion_confirmation, max_participants,
            created_by, creator_id, deadline_time, copied_from_task_id, task_type,
            importance, urgency_type, execution_time, target_percentage,
            repeat_interval_minutes, alarm_type, advance_reminders_minutes,
            remind_until_completed, overdue_reminder_interval_minutes, max_overdue_reminders,
            response_type, lan_target_list, completion_policy, response_form_schema,
            scope_level, zone_id, state_id, city_id, centre_id, target_role_codes,
            version, sync_status
        ) values (
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
            v_old_task.response_form_schema,
            coalesce(v_old_task.scope_level, 'CENTRE'),
            v_old_task.zone_id,
            v_old_task.state_id,
            v_old_task.city_id,
            v_old_task.centre_id,
            coalesce(v_old_task.target_role_codes, '[]'::jsonb),
            1,
            'SYNCED'
        ) returning id into v_new_task;

        if p_copy_manpower then
            insert into public.task_role_requirements(task_id, role_id, required_count, notes)
            select v_new_task, role_id, required_count, notes
            from public.task_role_requirements
            where task_id = v_old_task.id;
        end if;

        if p_copy_assignments then
            for v_assignee in
                select distinct tp.user_id, tp.role_id
                from public.task_participants tp
                join public.profiles p on p.id = tp.user_id and p.is_active = true
                where tp.task_id = v_old_task.id
                  and tp.status = 'active'
                  and (coalesce(p.is_on_leave, false) = false or tp.user_id = v_caller)
            loop
                insert into public.task_participants(task_id, user_id, role_id, source, status, assigned_by)
                values (v_new_task, v_assignee.user_id, v_assignee.role_id, 'copied_assignment', 'active', v_caller)
                on conflict (task_id, user_id) where status = 'active' do nothing;

                if v_assignee.user_id <> v_caller then
                    perform public.create_scoped_notification(
                        v_assignee.user_id,
                        'task_assigned',
                        'task_assignment',
                        'Repeated Shift Task: ' || v_old_task.title,
                        'Task repeated to "' || trim(p_new_name) || '"',
                        v_new_task,
                        null,
                        v_old_task.group_id,
                        'shift_copy_task:' || v_new_task::text || ':' || v_assignee.user_id::text,
                        'taskz://task/' || v_new_task::text,
                        coalesce(v_old_task.scope_level, 'CENTRE'),
                        jsonb_build_object('task_id', v_new_task, 'shift_id', v_new_shift)
                    );
                end if;
            end loop;
        else
            insert into public.task_participants(task_id, user_id, source, status, assigned_by)
            values (v_new_task, v_caller, 'assigned', 'active', v_caller)
            on conflict (task_id, user_id) where status = 'active' do nothing;
        end if;
    end loop;

    return v_new_shift;
end;
$$;

revoke all on function public.copy_shift(uuid, text, integer, boolean, boolean) from public;
grant execute on function public.copy_shift(uuid, text, integer, boolean, boolean) to authenticated;

create or replace function public.sync_group_member_to_active_tasks()
returns trigger
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_group_code text;
    v_task record;
    v_actor uuid := coalesce(new.added_by, new.user_id);
    v_on_leave boolean := false;
begin
    if new.status <> 'active' then
        return new;
    end if;

    select coalesce(is_on_leave, false) into v_on_leave
    from public.profiles where id = new.user_id;
    if v_on_leave then
        return new;
    end if;

    select code into v_group_code from public.task_groups where id = new.group_id;
    if coalesce(upper(v_group_code), '') = 'GEN-OPS' then
        return new;
    end if;

    for v_task in
        select t.id, t.title, t.visibility, t.scope_level, t.group_id
        from public.tasks t
        where t.group_id = new.group_id
          and t.deleted_at is null
          and t.status in ('open', 'in_progress', 'scheduled', 'upcoming')
    loop
        insert into public.task_participants(task_id, user_id, source, status, assigned_by)
        values (v_task.id, new.user_id, 'assigned', 'active', v_actor)
        on conflict (task_id, user_id) where status = 'active' do nothing;

        if v_task.visibility = 'restricted' then
            insert into public.task_visible_users(task_id, user_id, added_by)
            values (v_task.id, new.user_id, v_actor)
            on conflict (task_id, user_id) do nothing;
        end if;

        if new.user_id <> v_actor then
            perform public.create_scoped_notification(
                new.user_id,
                'task_assigned',
                'task_assignment',
                'Group Task Assigned: ' || v_task.title,
                'Task "' || v_task.title || '" assigned via group',
                v_task.id,
                null,
                v_task.group_id,
                'group_member_sync:' || v_task.id::text || ':' || new.user_id::text,
                'taskz://task/' || v_task.id::text,
                coalesce(v_task.scope_level, 'CENTRE'),
                jsonb_build_object('task_id', v_task.id, 'group_id', v_task.group_id)
            );
        end if;
    end loop;

    return new;
end;
$$;

create or replace function public.sync_task_to_group_members()
returns trigger
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_group_code text;
    v_member record;
begin
    if new.deleted_at is not null or new.group_id is null then
        return new;
    end if;

    select code into v_group_code from public.task_groups where id = new.group_id;
    if coalesce(upper(v_group_code), '') = 'GEN-OPS' then
        return new;
    end if;

    if new.status not in ('open', 'in_progress', 'scheduled', 'upcoming') then
        return new;
    end if;

    for v_member in
        select distinct gm.user_id
        from public.group_members gm
        join public.profiles p on p.id = gm.user_id
            and p.is_active = true
            and coalesce(p.is_on_leave, false) = false
        where gm.group_id = new.group_id
          and gm.status = 'active'
    loop
        insert into public.task_participants(task_id, user_id, source, status, assigned_by)
        values (new.id, v_member.user_id, 'assigned', 'active', coalesce(new.created_by, v_member.user_id))
        on conflict (task_id, user_id) where status = 'active' do nothing;

        if new.visibility = 'restricted' then
            insert into public.task_visible_users(task_id, user_id, added_by)
            values (new.id, v_member.user_id, coalesce(new.created_by, v_member.user_id))
            on conflict (task_id, user_id) do nothing;
        end if;

        if v_member.user_id <> coalesce(new.created_by, '00000000-0000-0000-0000-000000000000'::uuid) then
            perform public.create_scoped_notification(
                v_member.user_id,
                'task_assigned',
                'task_assignment',
                'Group Task Assigned: ' || new.title,
                'Task "' || new.title || '" added to your group',
                new.id,
                null,
                new.group_id,
                'task_group_sync:' || new.id::text || ':' || v_member.user_id::text,
                'taskz://task/' || new.id::text,
                coalesce(new.scope_level, 'CENTRE'),
                jsonb_build_object('task_id', new.id, 'group_id', new.group_id)
            );
        end if;
    end loop;

    return new;
end;
$$;

create or replace function public.add_group_members_and_sync_tasks(
    p_group_id uuid,
    p_user_ids uuid[]
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_caller uuid := (select auth.uid());
    v_group public.task_groups%rowtype;
    v_uid uuid;
    v_added_members integer := 0;
    v_assigned_links integer := 0;
    v_rows integer := 0;
begin
    if v_caller is null then
        raise exception 'Authentication required';
    end if;

    select * into v_group from public.task_groups where id = p_group_id;
    if not found then
        raise exception 'Task group not found';
    end if;

    if not (public.can_manage_group(p_group_id) or public.is_super_admin()) then
        raise exception 'Forbidden: only group managers or admins can add members';
    end if;

    if p_user_ids is not null then
        foreach v_uid in array p_user_ids
        loop
            if exists (
                select 1 from public.profiles
                where id = v_uid
                  and is_active = true
                  and coalesce(is_on_leave, false) = false
            ) then
                insert into public.group_members(group_id, user_id, source, status, added_by)
                values (p_group_id, v_uid, 'assigned', 'active', v_caller)
                on conflict (group_id, user_id) where status = 'active' do nothing;
                get diagnostics v_rows = row_count;
                v_added_members := v_added_members + v_rows;
            end if;
        end loop;
    end if;

    if coalesce(upper(v_group.code), '') <> 'GEN-OPS' then
        with active_tasks as (
            select t.id
            from public.tasks t
            where t.group_id = p_group_id
              and t.deleted_at is null
              and t.status in ('open', 'in_progress', 'scheduled', 'upcoming')
        ),
        inserted_parts as (
            insert into public.task_participants(task_id, user_id, source, status, assigned_by)
            select at.id, gm.user_id, 'assigned', 'active', v_caller
            from active_tasks at
            join public.group_members gm on gm.group_id = p_group_id and gm.status = 'active'
            join public.profiles p on p.id = gm.user_id and p.is_active = true and coalesce(p.is_on_leave, false) = false
            on conflict (task_id, user_id) where status = 'active' do nothing
            returning task_id
        )
        select count(*) into v_assigned_links from inserted_parts;
    end if;

    return jsonb_build_object(
        'success', true,
        'group_id', p_group_id,
        'added_members', v_added_members,
        'synced_task_assignments', v_assigned_links
    );
end;
$$;

revoke all on function public.add_group_members_and_sync_tasks(uuid, uuid[]) from public;
grant execute on function public.add_group_members_and_sync_tasks(uuid, uuid[]) to authenticated;

-- ----------------------------------------------------------------------------
-- 8. EDIT TASK DETAILS & EXECUTION/ALARM TIME AFTER CREATION
-- ----------------------------------------------------------------------------
create or replace function public.update_task_details(
    p_task_id uuid,
    p_payload jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_caller uuid := (select auth.uid());
    v_caller_profile public.profiles%rowtype;
    v_existing public.tasks%rowtype;
    v_updated public.tasks%rowtype;
    v_title text;
    v_part record;
begin
    if v_caller is null then
        raise exception 'Authentication required';
    end if;

    select * into v_caller_profile from public.profiles where id = v_caller;
    if not found or not coalesce(v_caller_profile.is_active, false) then
        raise exception 'Active profile required';
    end if;

    select * into v_existing from public.tasks where id = p_task_id and deleted_at is null;
    if not found then
        raise exception 'Task not found';
    end if;

    if not public.can_manage_task(p_task_id) then
        raise exception 'Forbidden: only the task creator, SPOC, or Admin can edit this task';
    end if;

    v_title := coalesce(nullif(trim(p_payload->>'title'), ''), v_existing.title);
    if length(v_title) < 1 then
        raise exception 'Task title cannot be blank';
    end if;

    perform set_config('taskz.bypass_task_guard', '1', true);

    update public.tasks
    set title = v_title,
        description = case
            when p_payload ? 'description' then nullif(trim(p_payload->>'description'), '')
            else description
        end,
        location = case
            when p_payload ? 'location' then nullif(trim(p_payload->>'location'), '')
            else location
        end,
        starts_at = case
            when p_payload ? 'starts_at' then nullif(trim(p_payload->>'starts_at'), '')::timestamptz
            else starts_at
        end,
        due_at = case
            when p_payload ? 'due_at' then nullif(trim(p_payload->>'due_at'), '')::timestamptz
            else due_at
        end,
        deadline_time = case
            when p_payload ? 'due_at' then nullif(trim(p_payload->>'due_at'), '')::timestamptz
            else deadline_time
        end,
        execution_time = case
            when p_payload ? 'execution_time' then nullif(trim(p_payload->>'execution_time'), '')::timestamptz
            else execution_time
        end,
        priority = case
            when p_payload ? 'priority' then coalesce(nullif(lower(trim(p_payload->>'priority')), ''), priority)
            else priority
        end,
        importance = case
            when p_payload ? 'importance' then coalesce(nullif(upper(trim(p_payload->>'importance')), ''), importance)
            else importance
        end,
        alarm_type = case
            when p_payload ? 'alarm_type' then coalesce(nullif(upper(trim(p_payload->>'alarm_type')), ''), alarm_type)
            else alarm_type
        end,
        overdue_reminder_interval_minutes = case
            when p_payload ? 'overdue_reminder_interval_minutes'
                 and nullif(trim(coalesce(p_payload->>'overdue_reminder_interval_minutes', '')), '') is not null
            then greatest(1, (p_payload->>'overdue_reminder_interval_minutes')::integer)
            else overdue_reminder_interval_minutes
        end,
        target_percentage = case
            when p_payload ? 'target_percentage'
                 and nullif(trim(coalesce(p_payload->>'target_percentage', '')), '') is not null
            then least(100, greatest(1, (p_payload->>'target_percentage')::integer))
            else target_percentage
        end,
        sync_status = 'SYNCED',
        updated_at = now()
    where id = p_task_id
    returning * into v_updated;

    insert into public.task_activity_log(task_id, actor_user_id, event_type, message)
    values (
        p_task_id,
        v_caller,
        'TASK_UPDATED',
        coalesce(v_caller_profile.full_name, 'Manager') || ' updated task details & schedule'
    );

    for v_part in
        select distinct tp.user_id
        from public.task_participants tp
        join public.profiles p on p.id = tp.user_id and p.is_active = true and coalesce(p.is_on_leave, false) = false
        where tp.task_id = p_task_id
          and tp.status = 'active'
          and tp.user_id <> v_caller
    loop
        perform public.create_scoped_notification(
            v_part.user_id,
            'task_updated',
            'task_update',
            'Task Updated: ' || v_updated.title,
            coalesce(v_caller_profile.full_name, 'Manager') || ' updated schedule or details',
            p_task_id,
            null,
            v_updated.group_id,
            'task_updated:' || p_task_id::text || ':v' || coalesce(v_updated.version, 1)::text || ':' || v_part.user_id::text,
            'taskz://task/' || p_task_id::text,
            coalesce(v_updated.scope_level, 'CENTRE'),
            jsonb_build_object('task_id', p_task_id, 'version', coalesce(v_updated.version, 1))
        );
    end loop;

    return to_jsonb(v_updated);
end;
$$;

revoke all on function public.update_task_details(uuid, jsonb) from public;
grant execute on function public.update_task_details(uuid, jsonb) to authenticated;

-- ----------------------------------------------------------------------------
-- 9. 1-CLICK BULK SELF-JOIN FOR EXAM GROUPS & SHIFTS
-- ----------------------------------------------------------------------------
create or replace function public.join_group_tasks_bulk(p_group_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_caller uuid := (select auth.uid());
    v_profile public.profiles%rowtype;
    v_group public.task_groups%rowtype;
    v_joined_tasks integer := 0;
begin
    if v_caller is null then
        raise exception 'Authentication required';
    end if;

    select * into v_profile from public.profiles where id = v_caller;
    if not found or not coalesce(v_profile.is_active, false) then
        raise exception 'Active profile required';
    end if;
    if coalesce(v_profile.is_on_leave, false) then
        raise exception 'Turn off On Leave status before joining tasks';
    end if;

    select * into v_group from public.task_groups where id = p_group_id;
    if not found then
        raise exception 'Exam group not found';
    end if;

    if not (public.can_manage_group(p_group_id) or public.is_super_admin() or public.can_view_group(p_group_id)) then
        raise exception 'Forbidden: group not accessible';
    end if;

    if not coalesce(v_group.allow_self_join, true)
       and not public.can_manage_group(p_group_id)
       and not public.is_super_admin() then
        raise exception 'Self-join is disabled for this exam group';
    end if;

    insert into public.group_members(group_id, user_id, source, status, added_by)
    values (p_group_id, v_caller, 'self_join', 'active', v_caller)
    on conflict (group_id, user_id) where status = 'active' do nothing;

    with eligible_tasks as (
        select t.id, t.visibility
        from public.tasks t
        where t.group_id = p_group_id
          and t.deleted_at is null
          and t.status in ('open', 'in_progress', 'scheduled', 'upcoming')
          and (
              coalesce(t.allow_self_join, true) = true
              or public.can_manage_group(p_group_id)
              or public.is_super_admin()
          )
    ),
    ins_parts as (
        insert into public.task_participants(task_id, user_id, source, status, assigned_by)
        select et.id, v_caller, 'self_join', 'active', v_caller
        from eligible_tasks et
        on conflict (task_id, user_id) where status = 'active' do nothing
        returning task_id
    )
    select count(*) into v_joined_tasks from ins_parts;

    insert into public.task_visible_users(task_id, user_id, added_by)
    select t.id, v_caller, v_caller
    from public.tasks t
    where t.group_id = p_group_id
      and t.deleted_at is null
      and t.visibility = 'restricted'
      and t.status in ('open', 'in_progress', 'scheduled', 'upcoming')
      and exists (
          select 1 from public.task_participants tp
          where tp.task_id = t.id and tp.user_id = v_caller and tp.status = 'active'
      )
    on conflict (task_id, user_id) do nothing;

    return jsonb_build_object(
        'success', true,
        'group_id', p_group_id,
        'joined_tasks_count', v_joined_tasks
    );
end;
$$;

revoke all on function public.join_group_tasks_bulk(uuid) from public;
grant execute on function public.join_group_tasks_bulk(uuid) to authenticated;

create or replace function public.join_shift_tasks_bulk(p_shift_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_caller uuid := (select auth.uid());
    v_profile public.profiles%rowtype;
    v_shift public.shifts%rowtype;
    v_group public.task_groups%rowtype;
    v_joined_tasks integer := 0;
begin
    if v_caller is null then
        raise exception 'Authentication required';
    end if;

    select * into v_profile from public.profiles where id = v_caller;
    if not found or not coalesce(v_profile.is_active, false) then
        raise exception 'Active profile required';
    end if;
    if coalesce(v_profile.is_on_leave, false) then
        raise exception 'Turn off On Leave status before joining shift tasks';
    end if;

    select * into v_shift from public.shifts where id = p_shift_id;
    if not found then
        raise exception 'Shift not found';
    end if;

    select * into v_group from public.task_groups where id = v_shift.group_id;
    if not found then
        raise exception 'Exam group not found for shift';
    end if;

    if not (public.can_manage_group(v_group.id) or public.is_super_admin() or public.can_view_group(v_group.id)) then
        raise exception 'Forbidden: shift group not accessible';
    end if;

    if not coalesce(v_group.allow_self_join, true)
       and not public.can_manage_group(v_group.id)
       and not public.is_super_admin() then
        raise exception 'Self-join is disabled for this exam group';
    end if;

    insert into public.group_members(group_id, user_id, source, status, added_by)
    values (v_group.id, v_caller, 'self_join', 'active', v_caller)
    on conflict (group_id, user_id) where status = 'active' do nothing;

    with eligible_tasks as (
        select t.id
        from public.tasks t
        where t.shift_id = p_shift_id
          and t.deleted_at is null
          and t.status in ('open', 'in_progress', 'scheduled', 'upcoming')
          and (
              coalesce(t.allow_self_join, true) = true
              or public.can_manage_group(v_group.id)
              or public.is_super_admin()
          )
    ),
    ins_parts as (
        insert into public.task_participants(task_id, user_id, source, status, assigned_by)
        select et.id, v_caller, 'self_join', 'active', v_caller
        from eligible_tasks et
        on conflict (task_id, user_id) where status = 'active' do nothing
        returning task_id
    )
    select count(*) into v_joined_tasks from ins_parts;

    insert into public.task_visible_users(task_id, user_id, added_by)
    select t.id, v_caller, v_caller
    from public.tasks t
    where t.shift_id = p_shift_id
      and t.deleted_at is null
      and t.visibility = 'restricted'
      and t.status in ('open', 'in_progress', 'scheduled', 'upcoming')
      and exists (
          select 1 from public.task_participants tp
          where tp.task_id = t.id and tp.user_id = v_caller and tp.status = 'active'
      )
    on conflict (task_id, user_id) do nothing;

    return jsonb_build_object(
        'success', true,
        'shift_id', p_shift_id,
        'group_id', v_group.id,
        'joined_tasks_count', v_joined_tasks
    );
end;
$$;

revoke all on function public.join_shift_tasks_bulk(uuid) from public;
grant execute on function public.join_shift_tasks_bulk(uuid) to authenticated;

commit;

notify pgrst, 'reload schema';
