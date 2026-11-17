-- ============================================================================
-- Taskz Migration 016: City-Level Visibility for IT_MANAGER / VCO / CITY_HEAD,
-- Onboarding State/City/Centre + LAN Setup, Scoped Directory & Incident Sync,
-- and Seeding Rajasthan Cities & Exam Centres
-- Forward-only, idempotent migration (does NOT rewrite 014 or 015).
-- ============================================================================

begin;

-- ----------------------------------------------------------------------------
-- 1. ADD assigned_lan COLUMN ON public.profiles FOR IT MANAGER LAN TRACKING
-- ----------------------------------------------------------------------------
alter table public.profiles
    add column if not exists assigned_lan text;

-- ----------------------------------------------------------------------------
-- 2. IDEMPOTENT ORG HIERARCHY SEEDING: NORTH ZONE -> RAJASTHAN -> 14 CITIES + CENTRES
-- ----------------------------------------------------------------------------
do $$
declare
    v_zone_id uuid;
    v_state_id uuid;
    v_city_id uuid;
    v_jaipur_id uuid;
    v_city_rec record;
    v_centre_rec record;
begin
    -- Ensure Zone: North Zone (NZ)
    select id into v_zone_id
    from public.org_zones
    where upper(code) = 'NZ' or lower(name) = 'north zone'
    order by created_at asc
    limit 1;

    if v_zone_id is null then
        insert into public.org_zones (code, name, is_active)
        values ('NZ', 'North Zone', true)
        returning id into v_zone_id;
    else
        update public.org_zones
        set is_active = true
        where id = v_zone_id;
    end if;

    -- Ensure State: Rajasthan (RJ)
    select id into v_state_id
    from public.org_states
    where upper(code) = 'RJ' or lower(name) = 'rajasthan'
    order by created_at asc
    limit 1;

    if v_state_id is null then
        insert into public.org_states (zone_id, code, name, is_active)
        values (v_zone_id, 'RJ', 'Rajasthan', true)
        returning id into v_state_id;
    else
        update public.org_states
        set zone_id = v_zone_id,
            is_active = true
        where id = v_state_id;
    end if;

    -- Ensure all 14 Rajasthan Cities + a default active Examination Centre for each city
    for v_city_rec in
        select * from (
            values
                ('JAI', 'Jaipur', 'Jaipur Exam Centre 1'),
                ('JDP', 'Jodhpur', 'Jodhpur Exam Centre 1'),
                ('AJM', 'Ajmer', 'Ajmer Exam Centre 1'),
                ('KOT', 'Kota', 'Kota Exam Centre 1'),
                ('BKN', 'Bikaner', 'Bikaner Exam Centre 1'),
                ('ALW', 'Alwar', 'Alwar Exam Centre 1'),
                ('UDR', 'Udaipur', 'Udaipur Exam Centre 1'),
                ('SGNR', 'Sri Ganganagar', 'Sri Ganganagar Exam Centre 1'),
                ('DAU', 'Dausa', 'Dausa Exam Centre 1'),
                ('BHR', 'Bharatpur', 'Bharatpur Exam Centre 1'),
                ('BHL', 'Bhilwara', 'Bhilwara Exam Centre 1'),
                ('HMH', 'Hanumangarh', 'Hanumangarh Exam Centre 1'),
                ('SIK', 'Sikar', 'Sikar Exam Centre 1'),
                ('TNK', 'Tonk', 'Tonk Exam Centre 1')
        ) as t(code, name, default_centre_name)
    loop
        select id into v_city_id
        from public.org_cities
        where upper(code) = v_city_rec.code
           or lower(name) = lower(v_city_rec.name)
        order by (upper(code) = v_city_rec.code) desc, created_at asc
        limit 1;

        if v_city_id is null then
            insert into public.org_cities (state_id, zone_id, code, name, is_active)
            values (v_state_id, v_zone_id, v_city_rec.code, v_city_rec.name, true)
            returning id into v_city_id;
        else
            update public.org_cities
            set state_id = v_state_id,
                zone_id = v_zone_id,
                is_active = true
            where id = v_city_id;
        end if;

        if v_city_rec.code = 'JAI' then
            v_jaipur_id := v_city_id;
        else
            -- Seed default active exam centre for every non-Jaipur city idempotently
            if exists (
                select 1 from public.org_centres oc
                where upper(oc.code) = upper(v_city_rec.code || '-01')
                   or upper(coalesce(oc.external_code, '')) = upper(v_city_rec.code || '-01')
            ) then
                update public.org_centres
                set city_id = v_city_id,
                    state_id = v_state_id,
                    zone_id = v_zone_id,
                    district_name = v_city_rec.name,
                    is_active = true
                where upper(code) = upper(v_city_rec.code || '-01')
                   or upper(coalesce(external_code, '')) = upper(v_city_rec.code || '-01');
            elsif not exists (
                select 1 from public.org_centres oc
                where oc.city_id = v_city_id and oc.is_active = true
            ) then
                insert into public.org_centres (external_code, city_id, state_id, zone_id, code, name, district_name, is_active)
                values (
                    v_city_rec.code || '-01',
                    v_city_id,
                    v_state_id,
                    v_zone_id,
                    v_city_rec.code || '-01',
                    v_city_rec.default_centre_name,
                    v_city_rec.name,
                    true
                );
            end if;
        end if;
    end loop;

    -- Seed core Jaipur examination centres (141..145) idempotently
    if v_jaipur_id is not null then
        for v_centre_rec in
            select * from (
                values
                    ('141', 'Yagyavalkya Institute of Technology', 'Sitapura Industrial Area, Jaipur'),
                    ('142', 'Arya College of Engineering & IT', 'Kukas Industrial Area, Jaipur'),
                    ('143', 'JECRC Foundation & University', 'Tonk Road, Sitapura, Jaipur'),
                    ('144', 'Poornima Institute of Engineering & Technology', 'Sitapura, Jaipur'),
                    ('145', 'Swami Keshvanand Institute of Technology', 'Jagatpura, Jaipur')
            ) as jc(code, name, address)
        loop
            if not exists (
                select 1 from public.org_centres oc
                where upper(oc.code) = upper(v_centre_rec.code)
                   or upper(coalesce(oc.external_code, '')) = upper(v_centre_rec.code)
            ) then
                insert into public.org_centres (external_code, city_id, state_id, zone_id, code, name, district_name, address, is_active)
                values (v_centre_rec.code, v_jaipur_id, v_state_id, v_zone_id, v_centre_rec.code, v_centre_rec.name, 'Jaipur', v_centre_rec.address, true);
            else
                update public.org_centres
                set city_id = v_jaipur_id,
                    state_id = v_state_id,
                    zone_id = v_zone_id,
                    district_name = 'Jaipur',
                    is_active = true
                where upper(code) = upper(v_centre_rec.code)
                   or upper(coalesce(external_code, '')) = upper(v_centre_rec.code);
            end if;
        end loop;
    end if;
end $$;

-- ----------------------------------------------------------------------------
-- 3. GRANT CITY-LEVEL READ VISIBILITY TO IT_MANAGER, VCO, AND CITY_HEAD
--    (Keeps strict cross-city, cross-state, and cross-zone isolation)
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

    -- Resolve canonical hierarchy from lowest target node and reject mismatched ancestors
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

    -- Derive missing ancestor IDs on caller profile conservatively from lower IDs
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
            return v_prof_zone is not null and v_target_zone is not null and v_target_zone = v_prof_zone;
        end if;
        return v_target_state is not null and v_target_state = v_prof_state;
    end if;

    -- CITY_HEAD, VCO, IT_MANAGER: City-level access (assigned city + all centres in city + own ancestor state/zone)
    if v_role in ('CITY_HEAD', 'VCO', 'IT_MANAGER') then
        if v_prof_city is null then
            -- Strict fallback to own centre_id if city_id is not set yet (never allow NULL wildcard)
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

-- ----------------------------------------------------------------------------
-- 4. TASK VIEW VISIBILITY: ALLOW PUBLIC ('everyone') SCOPED TASKS TO BE SEEN
--    AND JOINED BY AUTHORIZED PEERS IN THE SAME CITY / SCOPE
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
              )
          )
    );
$$;

-- ----------------------------------------------------------------------------
-- 5. UPDATE get_my_authorization_context() TO INCLUDE assigned_lan & CITY SCOPE
-- ----------------------------------------------------------------------------
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

    select coalesce(jsonb_agg(jsonb_build_object(
        'role_id', ur.role_id,
        'role_code', ur.role_code,
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
            'role_name', v_active_role,
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
        'active_role_code', v_active_role,
        'primary_role_code', coalesce(v_profile.primary_role_code, v_active_role),
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

-- ----------------------------------------------------------------------------
-- 6. SAFE OPERATIONAL LOCATION & DUTY SETUP RPC (State -> City -> Centre + LAN)
--    Allows self-selection of location and IT_MANAGER / VCO without privilege escalation.
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

    -- 1. Role guard: IT_MANAGER and VCO are self-assignable operational roles.
    --    Higher roles (CITY_HEAD, ROM, ZOM, COMMAND_CENTER, SUPER_ADMIN) require prior server assignment.
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

    -- 2. Resolve organizational hierarchy from lowest provided node
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

        -- If operational user chose a city without a centre, auto-select city's primary active centre
        if v_final_role in ('IT_MANAGER', 'VCO') then
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

revoke all on function public.set_my_operational_location(text, uuid, uuid, uuid, text, text) from public;
grant execute on function public.set_my_operational_location(text, uuid, uuid, uuid, text, text) to authenticated;

-- ----------------------------------------------------------------------------
-- 7. NORMALIZE & PROPAGATE SYSTEM INCIDENTS TO CITY/CENTRE PEERS + REALTIME PUSH
-- ----------------------------------------------------------------------------
create or replace function public.normalize_incident_org_scope()
returns trigger
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_oc public.org_centres%rowtype;
    v_ct public.org_cities%rowtype;
    v_prof public.profiles%rowtype;
    v_clean_code text := upper(nullif(trim(coalesce(new.centre_code, '')), ''));
begin
    new.scope_level := upper(coalesce(nullif(trim(new.scope_level), ''), 'CENTRE'));

    -- 1. Try matching org_centres by centre_id UUID
    if new.centre_id is not null then
        select * into v_oc from public.org_centres where id = new.centre_id limit 1;
    end if;

    -- 2. Try matching org_centres by centre_code or trailing digits
    if v_oc.id is null and v_clean_code is not null then
        select * into v_oc
        from public.org_centres oc
        where upper(oc.code) = v_clean_code
           or upper(coalesce(oc.external_code, '')) = v_clean_code
        limit 1;
    end if;

    -- 3. Fallback to creator profile's org assignment
    if v_oc.id is null and new.created_by is not null then
        select * into v_prof from public.profiles where id = new.created_by;
        if v_prof.centre_id is not null then
            select * into v_oc from public.org_centres where id = v_prof.centre_id limit 1;
        end if;
    end if;

    if v_oc.id is not null then
        new.centre_id := coalesce(new.centre_id, v_oc.id);
        new.city_id := coalesce(new.city_id, v_oc.city_id);
        new.state_id := coalesce(new.state_id, v_oc.state_id);
        new.zone_id := coalesce(new.zone_id, v_oc.zone_id);
    else
        -- 4. Try matching org_cities by district_name or creator city
        if new.city_id is null and nullif(trim(coalesce(new.district_name, '')), '') is not null then
            select * into v_ct
            from public.org_cities c
            where lower(trim(c.name)) = lower(trim(new.district_name))
               or upper(trim(c.code)) = upper(trim(new.district_name))
            limit 1;
            if found then
                new.city_id := v_ct.id;
                new.state_id := coalesce(new.state_id, v_ct.state_id);
                new.zone_id := coalesce(new.zone_id, v_ct.zone_id);
            end if;
        end if;

        if new.created_by is not null and v_prof.id is not null then
            new.city_id := coalesce(new.city_id, v_prof.city_id);
            new.state_id := coalesce(new.state_id, v_prof.state_id);
            new.zone_id := coalesce(new.zone_id, v_prof.zone_id);
        end if;
    end if;

    return new;
end;
$$;

drop trigger if exists trg_normalize_incident_org_scope on public.system_incidents;
create trigger trg_normalize_incident_org_scope
before insert on public.system_incidents
for each row
execute function public.normalize_incident_org_scope();

create or replace function public.notify_scoped_incident_created()
returns trigger
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_recipient record;
begin
    if new.deleted_at is not null then
        return new;
    end if;

    for v_recipient in
        select distinct p.id
        from public.profiles p
        where p.is_active = true
          and p.id <> new.created_by
          and (
              (new.city_id is not null and coalesce(p.city_id, (select oc.city_id from public.org_centres oc where oc.id = p.centre_id)) = new.city_id)
              or (new.centre_id is not null and p.centre_id = new.centre_id)
          )
        limit 150
    loop
        perform public.create_scoped_notification(
            v_recipient.id,
            'incident_reported',
            'incident',
            'Incident Reported: ' || coalesce(nullif(new.centre_code, ''), 'Centre'),
            'System ' || new.locked_system_number || ' (' || coalesce(nullif(new.lan_name, ''), 'LAN') || ') — ' || coalesce(nullif(new.reason, ''), 'Hardware lock'),
            null,
            new.id,
            null,
            'incident_created:' || new.id::text || ':' || v_recipient.id::text,
            'taskz://incident/' || new.id::text,
            coalesce(new.scope_level, 'CENTRE'),
            jsonb_build_object('incident_id', new.id, 'centre_code', new.centre_code, 'city_id', new.city_id)
        );
    end loop;

    return new;
end;
$$;

drop trigger if exists trg_notify_scoped_incident_created on public.system_incidents;
create trigger trg_notify_scoped_incident_created
after insert on public.system_incidents
for each row
execute function public.notify_scoped_incident_created();

-- Backfill existing incidents with resolved centre/city/state/zone IDs so colleagues immediately see them
select set_config('taskz.bypass_incident_guard', '1', true);

update public.system_incidents si
set centre_id = coalesce(
        si.centre_id,
        (select oc.id from public.org_centres oc
         where nullif(trim(si.centre_code), '') is not null
           and (upper(oc.code) = upper(trim(si.centre_code)) or upper(coalesce(oc.external_code, '')) = upper(trim(si.centre_code)))
         limit 1),
        (select p.centre_id from public.profiles p where p.id = si.created_by)
    ),
    city_id = coalesce(
        si.city_id,
        (select oc.city_id from public.org_centres oc
         where oc.id = si.centre_id
            or (nullif(trim(si.centre_code), '') is not null and (upper(oc.code) = upper(trim(si.centre_code)) or upper(coalesce(oc.external_code, '')) = upper(trim(si.centre_code))))
         limit 1),
        (select ct.id from public.org_cities ct where lower(trim(ct.name)) = lower(trim(si.district_name)) limit 1),
        (select p.city_id from public.profiles p where p.id = si.created_by)
    ),
    state_id = coalesce(
        si.state_id,
        (select oc.state_id from public.org_centres oc
         where oc.id = si.centre_id
            or (nullif(trim(si.centre_code), '') is not null and (upper(oc.code) = upper(trim(si.centre_code)) or upper(coalesce(oc.external_code, '')) = upper(trim(si.centre_code))))
         limit 1),
        (select ct.state_id from public.org_cities ct where lower(trim(ct.name)) = lower(trim(si.district_name)) limit 1),
        (select p.state_id from public.profiles p where p.id = si.created_by)
    ),
    zone_id = coalesce(
        si.zone_id,
        (select oc.zone_id from public.org_centres oc
         where oc.id = si.centre_id
            or (nullif(trim(si.centre_code), '') is not null and (upper(oc.code) = upper(trim(si.centre_code)) or upper(coalesce(oc.external_code, '')) = upper(trim(si.centre_code))))
         limit 1),
        (select ct.zone_id from public.org_cities ct where lower(trim(ct.name)) = lower(trim(si.district_name)) limit 1),
        (select p.zone_id from public.profiles p where p.id = si.created_by)
    )
where si.deleted_at is null
  and (si.city_id is null or si.state_id is null or si.zone_id is null or si.centre_id is null);

create or replace function public.can_view_incident(p_incident uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.system_incidents si
        left join public.profiles creator on creator.id = si.created_by
        where si.id = p_incident
          and si.deleted_at is null
          and (
              public.is_super_admin()
              or si.created_by = (select auth.uid())
              or si.assigned_to = (select auth.uid())
              or public.can_access_org_scope(
                  si.scope_level,
                  coalesce(si.zone_id, creator.zone_id),
                  coalesce(si.state_id, creator.state_id),
                  coalesce(si.city_id, creator.city_id),
                  coalesce(
                      si.centre_id,
                      (select oc.id from public.org_centres oc
                       where nullif(trim(si.centre_code), '') is not null
                         and (
                             upper(oc.code) = upper(trim(si.centre_code))
                             or upper(coalesce(oc.external_code, '')) = upper(trim(si.centre_code))
                         )
                       limit 1),
                      creator.centre_id
                  ),
                  (select auth.uid())
              )
          )
    );
$$;

-- ----------------------------------------------------------------------------
-- 8. SCOPED MEMBER SEARCH: ALLOW IT_MANAGER, VCO, CITY_HEAD TO SEARCH CITY PEERS
-- ----------------------------------------------------------------------------
create or replace function public.search_scoped_members(
    p_query text default '',
    p_role_code text default null,
    p_centre_id uuid default null,
    p_limit integer default 40
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

    -- Derive missing ancestor IDs on caller profile from org_centres / org_cities
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
    left join public.org_centres poc on poc.id = p.centre_id
    left join public.org_cities pct on pct.id = coalesce(p.city_id, poc.city_id)
    left join public.org_states pst on pst.id = coalesce(p.state_id, poc.state_id, pct.state_id)
    left join public.org_zones pzn on pzn.id = coalesce(p.zone_id, poc.zone_id, pct.zone_id, pst.zone_id)
    where (v_caller_role = 'SUPER_ADMIN' or p.is_active = true)
      and (
          v_caller_role in ('SUPER_ADMIN', 'COMMAND_CENTER')
          or (v_caller_role = 'ZOM' and v_me_zone is not null and coalesce(p.zone_id, poc.zone_id, pct.zone_id, pst.zone_id) = v_me_zone)
          or (v_caller_role = 'ROM' and v_me_state is not null and coalesce(p.state_id, poc.state_id, pct.state_id) = v_me_state)
          or (
              v_caller_role in ('CITY_HEAD', 'VCO', 'IT_MANAGER') and (
                  (v_me_city is not null and coalesce(p.city_id, poc.city_id) = v_me_city)
                  or (v_me_city is null and v_me.centre_id is not null and p.centre_id = v_me.centre_id)
                  or p.id = v_caller
              )
          )
      )
      and (p_centre_id is null or p.centre_id = p_centre_id)
      and (v_role_filter is null or exists (
          select 1 from public.user_roles ur where ur.user_id = p.id and ur.role_code = v_role_filter
      ))
      and (
          v_q = ''
          or p.full_name ilike '%' || v_q || '%'
          or p.employee_id ilike '%' || v_q || '%'
          or coalesce(p.email, u.email, '') ilike '%' || v_q || '%'
          or coalesce(p.centre_code, poc.code, '') ilike '%' || v_q || '%'
          or coalesce(p.city_name, pct.name, '') ilike '%' || v_q || '%'
      )
    order by (p.id = v_caller) asc, p.full_name asc
    limit least(greatest(coalesce(p_limit, 40), 1), 100);
end;
$$;

revoke all on function public.search_scoped_members(text, text, uuid, integer) from public;
grant execute on function public.search_scoped_members(text, text, uuid, integer) to authenticated;

-- ----------------------------------------------------------------------------
-- 9. ENHANCE create_task_atomic(jsonb): RESOLVE CENTRE BY CODE IF NEEDED AND
--    PROPAGATE NOTIFICATIONS TO CITY COLLEAGUES
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
    elsif v_scope_level = 'STATE' and v_caller_role not in ('ROM', 'ZOM', 'COMMAND_CENTER', 'SUPER_ADMIN') then
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

    -- Notify all active scoped peers (including city-level colleagues on CENTRE tasks)
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
-- 10. UPGRADE request_task_join NOTIFICATION DELIVERY WITH DEEP LINK & PUSH
-- ----------------------------------------------------------------------------
create or replace function public.request_task_join(
    p_task uuid,
    p_role uuid default null,
    p_message text default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user uuid := (select auth.uid());
    v_task public.tasks%rowtype;
    v_user_name text;
    v_current integer;
begin
    if not public.is_active_user() then
        raise exception 'Account inactive';
    end if;

    select * into v_task from public.tasks where id = p_task and deleted_at is null;
    if not found then
        raise exception 'Task not found';
    end if;

    if not public.can_view_task(p_task) then
        raise exception 'Task not available in your authorized scope';
    end if;

    if not v_task.allow_self_join then
        raise exception 'Self joining is not allowed for this task';
    end if;

    if v_task.status not in ('open', 'in_progress', 'scheduled', 'upcoming') then
        raise exception 'Task is not open for joining';
    end if;

    if exists (
        select 1 from public.task_participants
        where task_id = p_task and user_id = v_user and status = 'active'
    ) then
        return jsonb_build_object('status', 'already_joined');
    end if;

    if v_task.max_participants is not null then
        select count(*) into v_current
        from public.task_participants
        where task_id = p_task and status = 'active';
        if v_current >= v_task.max_participants then
            raise exception 'Task participant limit reached';
        end if;
    end if;

    if exists (select 1 from public.task_role_requirements where task_id = p_task) then
        if p_role is null then
            raise exception 'Role required';
        end if;
        if not exists (
            select 1 from public.task_role_requirements
            where task_id = p_task and role_id = p_role
        ) then
            raise exception 'Invalid role';
        end if;
    end if;

    select full_name into v_user_name from public.profiles where id = v_user;

    if v_task.join_requires_approval then
        insert into public.task_join_requests(task_id, user_id, role_id, message)
        values (p_task, v_user, p_role, nullif(trim(p_message), ''))
        on conflict (task_id, user_id) where status = 'pending'
        do update set
            role_id = coalesce(excluded.role_id, public.task_join_requests.role_id),
            message = coalesce(excluded.message, public.task_join_requests.message);

        insert into public.task_activity_log(task_id, actor_user_id, target_user_id, event_type, message)
        values (p_task, v_user, v_user, 'JOIN_REQUESTED', coalesce(v_user_name, 'Employee') || ' requested to join task');

        if v_task.created_by <> v_user then
            perform public.create_scoped_notification(
                v_task.created_by,
                'task_join_request',
                'task_update',
                'Join Request: ' || v_task.title,
                coalesce(v_user_name, 'An employee') || ' requested to join "' || v_task.title || '"',
                p_task,
                null,
                v_task.group_id,
                'task_join_req:' || p_task::text || ':' || v_user::text,
                'taskz://task/' || p_task::text,
                v_task.scope_level,
                jsonb_build_object('task_id', p_task, 'requester_id', v_user)
            );
        end if;

        return jsonb_build_object('status', 'pending_approval');
    else
        insert into public.task_participants(task_id, user_id, role_id, source, status, assigned_by)
        values (p_task, v_user, p_role, 'self_join', 'active', v_user)
        on conflict (task_id, user_id) where status = 'active' do nothing;

        insert into public.task_activity_log(task_id, actor_user_id, target_user_id, event_type, message)
        values (p_task, v_user, v_user, 'USER_JOINED', coalesce(v_user_name, 'Employee') || ' joined task');

        if v_task.notify_creator_on_join and v_task.created_by <> v_user then
            perform public.create_scoped_notification(
                v_task.created_by,
                'task_joined',
                'task_update',
                'Teammate Joined: ' || v_task.title,
                coalesce(v_user_name, 'An employee') || ' joined "' || v_task.title || '"',
                p_task,
                null,
                v_task.group_id,
                'task_joined:' || p_task::text || ':' || v_user::text,
                'taskz://task/' || p_task::text,
                v_task.scope_level,
                jsonb_build_object('task_id', p_task, 'joined_user_id', v_user)
            );
        end if;

        return jsonb_build_object('status', 'joined');
    end if;
end;
$$;

revoke all on function public.request_task_join(uuid, uuid, text) from public;
grant execute on function public.request_task_join(uuid, uuid, text) to authenticated;

commit;

notify pgrst, 'reload schema';
