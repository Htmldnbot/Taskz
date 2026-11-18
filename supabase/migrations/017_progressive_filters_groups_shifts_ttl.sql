-- ============================================================================
-- Migration 017: Progressive Tasks, SO Role, State-Wide Assignment,
-- Shift 1 -> Shift 2 Copy Preserve Scope, Group Batch Sync, and 24h Task TTL
-- ============================================================================

begin;

-- ----------------------------------------------------------------------------
-- 1. SEED SO (SITE OFFICER) ROLE SAFELY
-- ----------------------------------------------------------------------------
do $$
declare
    v_existing_id uuid;
begin
    select id into v_existing_id
    from public.work_roles
    where id = '11111111-1111-4111-8111-111111111113'::uuid
       or canonical_code = 'SO'
       or upper(coalesce(code, '')) = 'SO'
       or lower(name) = lower('SO (Site Officer)')
    order by (id = '11111111-1111-4111-8111-111111111113'::uuid) desc, sort_order asc
    limit 1;

    if v_existing_id is not null then
        update public.work_roles
        set canonical_code = null
        where canonical_code = 'SO' and id <> v_existing_id;

        update public.work_roles
        set canonical_code = 'SO',
            code = 'SO',
            name = 'SO (Site Officer)',
            description = 'Site Officer supervising venue operations and lab coordinators',
            hierarchy_level = 15,
            default_scope_level = 'CENTRE',
            is_self_assignable = true,
            is_active = true
        where id = v_existing_id;
    else
        insert into public.work_roles (
            id, name, code, canonical_code, description, is_active, sort_order,
            hierarchy_level, default_scope_level, is_self_assignable
        ) values (
            '11111111-1111-4111-8111-111111111113'::uuid,
            'SO (Site Officer)',
            'SO',
            'SO',
            'Site Officer supervising venue operations and lab coordinators',
            true,
            13,
            15,
            'CENTRE',
            true
        );
    end if;
end $$;

-- ----------------------------------------------------------------------------
-- 2. UPDATE can_access_org_scope() FOR SO ROLE + CLEAN ANCESTOR DERIVATION
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

    -- Resolve target hierarchy from org tables
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

    if v_role in ('CITY_HEAD', 'VCO', 'SO', 'IT_MANAGER') then
        if v_prof_city is null then
            if v_role in ('VCO', 'SO', 'IT_MANAGER') and v_profile.centre_id is not null then
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

-- ----------------------------------------------------------------------------
-- 3. ALLOW STATE TASK CREATION BY VCO & CITY_HEAD + CENTRE TASKS BY SO
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
    if v_role not in ('SO', 'IT_MANAGER', 'VCO', 'CITY_HEAD', 'ROM', 'ZOM', 'COMMAND_CENTER', 'SUPER_ADMIN') then
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
-- 4. SAFE OPERATIONAL LOCATION SETUP FOR SO + IT_MANAGER + VCO
-- ----------------------------------------------------------------------------
create or replace function public.set_my_operational_location(
    p_role_code text default null,
    p_state_id uuid default null,
    p_city_id uuid default null,
    p_centre_id uuid default null,
    p_centre_code text default null,
    p_lan_number text default null
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
    v_zone_id uuid;
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

    v_final_role := coalesce(v_requested_role, public.get_effective_role_code(v_caller));

    if v_requested_role is not null then
        if v_requested_role in ('IT_MANAGER', 'SO', 'VCO') then
            select id into v_role_id
            from public.work_roles
            where canonical_code = v_requested_role or upper(coalesce(code, '')) = v_requested_role
            order by sort_order asc
            limit 1;

            if v_role_id is null then
                v_role_id := case
                    when v_requested_role = 'VCO' then '11111111-1111-4111-8111-111111111106'::uuid
                    when v_requested_role = 'SO' then '11111111-1111-4111-8111-111111111113'::uuid
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
          and (
              upper(c.code) = v_clean_centre_code
              or upper(coalesce(c.external_code, '')) = v_clean_centre_code
          )
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

        if v_final_role in ('IT_MANAGER', 'SO', 'VCO') then
            select c.id, c.code
            into v_centre_id, v_centre_code
            from public.org_centres c
            where c.city_id = v_city_id and c.is_active = true
            order by c.code asc
            limit 1;
        end if;
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

    perform set_config('taskz.bypass_rbac_guard', '1', true);

    if v_requested_role in ('IT_MANAGER', 'SO', 'VCO') and v_role_id is not null then
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
        zone_id = coalesce(v_zone_id, zone_id),
        zone_name = coalesce(v_zone_name, zone_name),
        state_id = coalesce(v_state_id, state_id),
        state_name = coalesce(v_state_name, state_name),
        city_id = case
            when v_city_id is not null then v_city_id
            when p_state_id is not null and p_state_id is distinct from state_id then null
            else city_id
        end,
        city_name = case
            when v_city_id is not null then v_city_name
            when p_state_id is not null and p_state_id is distinct from state_id then null
            else city_name
        end,
        centre_id = case
            when v_centre_id is not null then v_centre_id
            when v_city_id is not null and v_city_id is distinct from city_id then null
            else centre_id
        end,
        centre_code = case
            when v_centre_code is not null then v_centre_code
            when v_clean_centre_code is not null then v_clean_centre_code
            when v_city_id is not null and v_city_id is distinct from city_id then null
            else centre_code
        end,
        assigned_lan = case
            when v_final_role in ('IT_MANAGER', 'SO') then coalesce(v_clean_lan, assigned_lan)
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

revoke all on function public.set_my_operational_location(text, uuid, uuid, uuid, text, text) from public;
grant execute on function public.set_my_operational_location(text, uuid, uuid, uuid, text, text) to authenticated;

-- ----------------------------------------------------------------------------
-- 5. UPGRADE search_scoped_members TO SUPPORT STATE & CITY FILTERS + VCO STATE
--    (Drop 4-param overload cleanly first to avoid PostgREST PGRST203 ambiguity)
-- ----------------------------------------------------------------------------
drop function if exists public.search_scoped_members(text, text, uuid, integer);

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
    is_active boolean
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
            when v_caller_role in ('SUPER_ADMIN', 'COMMAND_CENTER', 'CITY_HEAD', 'ROM', 'ZOM', 'VCO', 'SO', 'IT_MANAGER')
                 or p.id = v_caller
            then p.mobile
            else null
        end as mobile,
        p.app_role,
        public.get_effective_role_code(p.id) as active_role_code,
        coalesce((select array_agg(ur.role_code order by ur.is_primary desc, ur.role_code)
                  from public.user_roles ur where ur.user_id = p.id), array['IT_MANAGER']) as assigned_roles,
        p.centre_id,
        coalesce(p.centre_code, poc.code) as centre_code,
        coalesce(p.city_id, poc.city_id) as city_id,
        coalesce(p.city_name, pct.name) as city_name,
        coalesce(p.state_id, poc.state_id, pct.state_id) as state_id,
        coalesce(p.state_name, pst.name) as state_name,
        coalesce(p.zone_id, poc.zone_id, pct.zone_id, pst.zone_id) as zone_id,
        coalesce(p.zone_name, pzn.name) as zone_name,
        p.is_active
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
              v_caller_role in ('SO', 'IT_MANAGER') and (
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
          or exists (select 1 from public.user_roles ur where ur.user_id = p.id and ur.role_code = v_role_filter)
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
    order by (p.id = v_caller) asc, p.full_name asc
    limit least(greatest(coalesce(p_limit, 100), 1), 250);
end;
$$;

revoke all on function public.search_scoped_members(text, text, uuid, uuid, uuid, integer) from public;
grant execute on function public.search_scoped_members(text, text, uuid, uuid, uuid, integer) to authenticated;

-- ----------------------------------------------------------------------------
-- 6. UPGRADE create_task_atomic(jsonb) FOR STATE ASSIGNMENT (VCO / SO / ITM)
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
    v_raw_centre_code text;
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
        v_zone_id := v_st.zone_id;
        v_city_id := null;
        v_centre_id := null;
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
        v_state_id := v_ct.state_id;
        v_zone_id := v_ct.zone_id;
        v_centre_id := null;
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
-- 7. UPGRADE copy_shift() TO PRESERVE ORG SCOPE & ROLES (SHIFT 1 -> SHIFT 2)
-- ----------------------------------------------------------------------------
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
                select distinct user_id, role_id
                from public.task_participants
                where task_id = v_old_task.id and status = 'active'
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

-- ----------------------------------------------------------------------------
-- 8. TASK GROUPS BATCH ASSIGNMENT & AUTOMATIC GROUP <-> TASK SYNC TRIGGERS
--    (Custom groups excluding fallback 'GEN-OPS' sync members & tasks)
-- ----------------------------------------------------------------------------
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
begin
    if new.status <> 'active' then
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
                'Added to group — task "' || v_task.title || '" assigned automatically',
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

drop trigger if exists trg_sync_group_member_to_tasks on public.group_members;
create trigger trg_sync_group_member_to_tasks
after insert or update of status on public.group_members
for each row
when (new.status = 'active')
execute function public.sync_group_member_to_active_tasks();

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
        join public.profiles p on p.id = gm.user_id and p.is_active = true
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
                'New task "' || new.title || '" added to your group',
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

drop trigger if exists trg_sync_task_group_to_members on public.tasks;
create trigger trg_sync_task_group_to_members
after insert or update of group_id on public.tasks
for each row
when (new.deleted_at is null and new.group_id is not null)
execute function public.sync_task_to_group_members();

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
        raise exception 'Forbidden: only group managers or admins can batch-assign members';
    end if;

    if p_user_ids is not null then
        foreach v_uid in array p_user_ids
        loop
            if exists (select 1 from public.profiles where id = v_uid and is_active = true) then
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
            select t.id, t.visibility
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

create or replace function public.assign_tasks_to_group_and_sync(
    p_group_id uuid,
    p_task_ids uuid[]
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_caller uuid := (select auth.uid());
    v_group public.task_groups%rowtype;
    v_tid uuid;
    v_moved_tasks integer := 0;
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
        raise exception 'Forbidden: only group managers or admins can assign tasks to this group';
    end if;

    perform set_config('taskz.bypass_task_guard', '1', true);

    if p_task_ids is not null then
        foreach v_tid in array p_task_ids
        loop
            if public.can_view_task(v_tid) then
                update public.tasks
                set group_id = p_group_id,
                    updated_at = now(),
                    version = version + 1
                where id = v_tid
                  and deleted_at is null
                  and group_id is distinct from p_group_id;
                get diagnostics v_rows = row_count;
                v_moved_tasks := v_moved_tasks + v_rows;
            end if;
        end loop;
    end if;

    return jsonb_build_object(
        'success', true,
        'group_id', p_group_id,
        'moved_tasks', v_moved_tasks
    );
end;
$$;

revoke all on function public.assign_tasks_to_group_and_sync(uuid, uuid[]) from public;
grant execute on function public.assign_tasks_to_group_and_sync(uuid, uuid[]) to authenticated;

-- ----------------------------------------------------------------------------
-- 9. AUTO-DELETE TASKS OLDER THAN 24 HOURS + ENFORCE IN delta_sync_pull()
-- ----------------------------------------------------------------------------
create or replace function public.purge_expired_tasks_24h()
returns integer
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_purged integer := 0;
begin
    perform set_config('taskz.bypass_task_guard', '1', true);

    update public.tasks
    set deleted_at = now(),
        updated_at = now(),
        version = coalesce(version, 1) + 1
    where deleted_at is null
      and created_at < now() - interval '24 hours'
      and coalesce(due_at, execution_time, starts_at, created_at) < now() - interval '24 hours';

    get diagnostics v_purged = row_count;
    return v_purged;
end;
$$;

revoke all on function public.purge_expired_tasks_24h() from public;
grant execute on function public.purge_expired_tasks_24h() to authenticated;

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
    v_purged_count integer := 0;
begin
    if v_caller is null then
        raise exception 'Authentication required';
    end if;

    select * into v_profile from public.profiles where id = v_caller;
    if not found or not coalesce(v_profile.is_active, false) then
        raise exception 'Active profile required';
    end if;

    -- Auto-soft-delete any tasks older than 24 hours across the platform
    v_purged_count := public.purge_expired_tasks_24h();

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
          and (
              t.created_at >= now() - interval '24 hours'
              or coalesce(t.due_at, t.execution_time, t.starts_at, t.created_at) >= now() - interval '24 hours'
          )
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
                  coalesce(
                      (select oc.id from public.org_centres oc where oc.id::text = si.centre_id::text limit 1),
                      (select oc.id from public.org_centres oc
                       where nullif(trim(si.centre_code), '') is not null
                         and (
                             upper(oc.code) = upper(trim(si.centre_code))
                             or upper(coalesce(oc.external_code, '')) = upper(trim(si.centre_code))
                             or oc.external_code = si.centre_id::text
                         )
                       limit 1)
                  ),
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
        'purged_expired_tasks_count', v_purged_count,
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
