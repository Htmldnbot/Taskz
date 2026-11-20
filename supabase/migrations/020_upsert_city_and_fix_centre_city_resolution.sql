-- ============================================================================
-- Taskz Migration 020: Add upsert_org_city() RPC (with auto-create State/Zone)
-- and harden upsert_org_centre() City/State resolution for adding new cities
-- and centres seamlessly from the app.
-- ============================================================================

begin;

-- ----------------------------------------------------------------------------
-- 1. ADD upsert_org_city() RPC
-- ----------------------------------------------------------------------------
create or replace function public.upsert_org_city(
    p_id uuid default null,
    p_code text default null,
    p_name text default null,
    p_state_id uuid default null,
    p_state_name text default null,
    p_zone_id uuid default null,
    p_zone_name text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_caller uuid := (select auth.uid());
    v_caller_profile public.profiles%rowtype;
    v_role text;
    v_name text := nullif(trim(coalesce(p_name, '')), '');
    v_raw_code text := upper(regexp_replace(trim(coalesce(p_code, '')), '[^A-Za-z0-9_-]', '', 'g'));
    v_state_name text := nullif(trim(coalesce(p_state_name, '')), '');
    v_zone_name text := nullif(trim(coalesce(p_zone_name, '')), '');
    v_zone_id uuid := p_zone_id;
    v_state_id uuid := p_state_id;
    v_state public.org_states%rowtype;
    v_zone public.org_zones%rowtype;
    v_city public.org_cities%rowtype;
    v_code text;
    v_state_code text;
    v_zone_code text;
begin
    if v_caller is null then
        raise exception 'Authentication required';
    end if;

    select * into v_caller_profile from public.profiles where id = v_caller;
    if not found or not coalesce(v_caller_profile.is_active, false) then
        raise exception 'Active user profile required';
    end if;

    v_role := public.get_effective_role_code(v_caller);
    if v_role not in ('SUPER_ADMIN', 'COMMAND_CENTER', 'ZOM', 'ROM', 'CITY_HEAD', 'VCO') then
        raise exception 'Forbidden: only VCO, City Head, ROM, ZOM, Command Center, or Super Admin can add or update cities';
    end if;

    if v_name is null then
        raise exception 'City name is required';
    end if;

    -- Resolve zone if specified
    if v_zone_id is not null then
        select * into v_zone from public.org_zones where id = v_zone_id;
        if not found then
            v_zone_id := null;
        end if;
    end if;

    if v_zone_id is null and v_zone_name is not null then
        select * into v_zone
        from public.org_zones
        where lower(name) = lower(v_zone_name) or upper(code) = upper(v_zone_name)
        order by created_at asc
        limit 1;
        if found then
            v_zone_id := v_zone.id;
        else
            v_zone_code := upper(regexp_replace(left(v_zone_name, 8), '[^A-Za-z0-9]', '', 'g'));
            if coalesce(v_zone_code, '') = '' or exists (select 1 from public.org_zones where code = v_zone_code) then
                v_zone_code := coalesce(nullif(v_zone_code, ''), 'ZONE') || '_' || substr(md5(v_zone_name || now()::text), 1, 3);
            end if;
            insert into public.org_zones (code, name, is_active)
            values (v_zone_code, v_zone_name, true)
            returning id into v_zone_id;
        end if;
    end if;

    -- Resolve state
    if v_state_id is not null then
        select * into v_state from public.org_states where id = v_state_id;
        if found then
            if v_state_name is not null and lower(v_state.name) <> lower(v_state_name) then
                -- Caller explicitly entered a different state name
                v_state_id := null;
            else
                v_state_id := v_state.id;
                v_zone_id := coalesce(v_zone_id, v_state.zone_id);
            end if;
        else
            v_state_id := null;
        end if;
    end if;

    if v_state_id is null and v_state_name is not null then
        select * into v_state
        from public.org_states
        where lower(name) = lower(v_state_name) or upper(code) = upper(v_state_name)
        order by created_at asc
        limit 1;
        if found then
            v_state_id := v_state.id;
            v_zone_id := coalesce(v_zone_id, v_state.zone_id);
        else
            if v_zone_id is null then
                v_zone_id := coalesce(
                    v_caller_profile.zone_id,
                    (select id from public.org_zones order by created_at asc limit 1)
                );
            end if;
            if v_zone_id is null then
                insert into public.org_zones (code, name, is_active)
                values ('NORTH3', 'North 3', true)
                on conflict (code) do update set is_active = true
                returning id into v_zone_id;
            end if;
            v_state_code := upper(regexp_replace(left(v_state_name, 3), '[^A-Za-z0-9]', '', 'g'));
            if coalesce(v_state_code, '') = '' or exists (select 1 from public.org_states where code = v_state_code) then
                v_state_code := coalesce(nullif(upper(regexp_replace(left(v_state_name, 4), '[^A-Za-z0-9]', '', 'g')), ''), 'ST')
                    || '_' || substr(md5(v_state_name || now()::text), 1, 3);
            end if;
            insert into public.org_states (zone_id, code, name, is_active)
            values (v_zone_id, v_state_code, v_state_name, true)
            returning id into v_state_id;
        end if;
    end if;

    if v_state_id is null then
        v_state_id := coalesce(
            v_caller_profile.state_id,
            (select id from public.org_states where upper(code) = 'RJ' or lower(name) = 'rajasthan' limit 1),
            (select id from public.org_states order by created_at asc limit 1)
        );
    end if;

    if v_zone_id is null then
        select zone_id into v_zone_id from public.org_states where id = v_state_id;
        v_zone_id := coalesce(v_zone_id, v_caller_profile.zone_id, (select id from public.org_zones order by created_at asc limit 1));
    end if;

    -- Resolve or generate city code
    v_code := nullif(v_raw_code, '');
    if v_code is null then
        v_code := upper(regexp_replace(left(v_name, 3), '[^A-Za-z0-9]', '', 'g'));
        if coalesce(v_code, '') = '' then
            v_code := 'CTY';
        end if;
        if exists (
            select 1 from public.org_cities
            where upper(code) = v_code
              and lower(name) <> lower(v_name)
              and (p_id is null or id <> p_id)
        ) then
            v_code := upper(regexp_replace(left(v_name, 4), '[^A-Za-z0-9]', '', 'g')) || '_' || substr(md5(v_name || now()::text), 1, 3);
        end if;
    end if;

    if p_id is not null and exists (select 1 from public.org_cities where id = p_id) then
        update public.org_cities
        set code = coalesce(v_code, code),
            name = v_name,
            state_id = coalesce(v_state_id, state_id),
            zone_id = coalesce(v_zone_id, zone_id),
            is_active = true
        where id = p_id
        returning * into v_city;
    else
        select * into v_city
        from public.org_cities
        where lower(name) = lower(v_name)
          and state_id = v_state_id
        order by created_at asc
        limit 1;

        if found then
            update public.org_cities
            set code = coalesce(nullif(v_raw_code, ''), code),
                zone_id = coalesce(v_zone_id, zone_id),
                is_active = true
            where id = v_city.id
            returning * into v_city;
        else
            insert into public.org_cities (state_id, zone_id, code, name, is_active)
            values (v_state_id, v_zone_id, v_code, v_name, true)
            on conflict (code) do update
            set name = excluded.name,
                state_id = excluded.state_id,
                zone_id = excluded.zone_id,
                is_active = true
            returning * into v_city;
        end if;
    end if;

    return jsonb_build_object(
        'id', v_city.id,
        'state_id', v_city.state_id,
        'zone_id', v_city.zone_id,
        'code', v_city.code,
        'name', v_city.name
    );
end;
$$;

revoke all on function public.upsert_org_city(uuid, text, text, uuid, text, uuid, text) from public;
grant execute on function public.upsert_org_city(uuid, text, text, uuid, text, uuid, text) to authenticated;

-- ----------------------------------------------------------------------------
-- 2. HARDEN upsert_org_centre() SO TYPING A NEW CITY/STATE NEVER SILENTLY
--    BINDS TO A DEFAULT CITY_ID
-- ----------------------------------------------------------------------------
create or replace function public.upsert_org_centre(
    p_id uuid default null,
    p_code text default null,
    p_name text default null,
    p_city_id uuid default null,
    p_district_name text default null,
    p_address text default null,
    p_sub_hub text default null,
    p_available_lans jsonb default null,
    p_state_name text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_caller uuid := (select auth.uid());
    v_role text;
    v_caller_profile public.profiles%rowtype;
    v_city public.org_cities%rowtype;
    v_state public.org_states%rowtype;
    v_zone_id uuid;
    v_state_id uuid;
    v_city_id uuid;
    v_code text := nullif(trim(coalesce(p_code, '')), '');
    v_name text := nullif(trim(coalesce(p_name, '')), '');
    v_district text := nullif(trim(coalesce(p_district_name, '')), '');
    v_state_name text := nullif(trim(coalesce(p_state_name, '')), '');
    v_state_code text;
    v_city_code text;
    v_lans jsonb;
    v_centre public.org_centres%rowtype;
begin
    if v_caller is null then
        raise exception 'Authentication required';
    end if;

    select * into v_caller_profile from public.profiles where id = v_caller;
    if not found or not coalesce(v_caller_profile.is_active, false) then
        raise exception 'Active user profile required';
    end if;

    v_role := public.get_effective_role_code(v_caller);
    if v_role not in ('SUPER_ADMIN', 'COMMAND_CENTER', 'ZOM', 'ROM', 'CITY_HEAD', 'VCO') then
        raise exception 'Forbidden: only VCO, City Head, ROM, ZOM, Command Center, or Super Admin can add or update centres';
    end if;

    if v_code is null or v_name is null then
        raise exception 'Centre code and name are required';
    end if;

    -- Resolve state first if explicitly supplied
    if v_state_name is not null then
        select * into v_state
        from public.org_states
        where lower(name) = lower(v_state_name) or upper(code) = upper(v_state_name)
        order by created_at asc
        limit 1;
        if found then
            v_state_id := v_state.id;
            v_zone_id := v_state.zone_id;
        end if;
    end if;

    -- Resolve city from p_city_id ONLY if v_district is empty or matches that city's name
    if p_city_id is not null then
        select * into v_city from public.org_cities where id = p_city_id;
        if found then
            if (v_district is null or lower(v_district) = lower(v_city.name))
               and (v_state_id is null or v_city.state_id = v_state_id) then
                v_city_id := v_city.id;
                v_state_id := v_city.state_id;
                v_zone_id := v_city.zone_id;
                if v_district is null then v_district := v_city.name; end if;
            end if;
        end if;
    end if;

    -- Resolve city by district name (prefer matching state if resolved)
    if v_city_id is null and v_district is not null then
        select * into v_city
        from public.org_cities
        where lower(name) = lower(v_district)
          and (v_state_id is null or state_id = v_state_id)
        order by created_at asc
        limit 1;
        if found then
            v_city_id := v_city.id;
            v_state_id := v_city.state_id;
            v_zone_id := v_city.zone_id;
        end if;
    end if;

    -- Auto-create state if a new state name was supplied
    if v_state_id is null and v_state_name is not null then
        v_zone_id := coalesce(
            v_zone_id,
            v_caller_profile.zone_id,
            (select id from public.org_zones order by created_at asc limit 1)
        );
        if v_zone_id is null then
            insert into public.org_zones (code, name, is_active)
            values ('NORTH3', 'North 3', true)
            on conflict (code) do update set is_active = true
            returning id into v_zone_id;
        end if;
        v_state_code := upper(regexp_replace(left(v_state_name, 3), '[^A-Za-z0-9]', '', 'g'));
        if coalesce(v_state_code, '') = '' or exists (select 1 from public.org_states where code = v_state_code) then
            v_state_code := coalesce(nullif(upper(regexp_replace(left(v_state_name, 4), '[^A-Za-z0-9]', '', 'g')), ''), 'ST')
                || '_' || substr(md5(v_state_name || now()::text), 1, 3);
        end if;
        insert into public.org_states (zone_id, code, name, is_active)
        values (v_zone_id, v_state_code, v_state_name, true)
        returning id into v_state_id;
    end if;

    if v_state_id is null then
        v_state_id := coalesce(
            v_caller_profile.state_id,
            (select id from public.org_states where upper(code) = 'RJ' or lower(name) = 'rajasthan' limit 1),
            (select id from public.org_states order by created_at asc limit 1)
        );
    end if;

    if v_zone_id is null then
        select zone_id into v_zone_id from public.org_states where id = v_state_id;
        v_zone_id := coalesce(v_zone_id, v_caller_profile.zone_id, (select id from public.org_zones order by created_at asc limit 1));
    end if;

    -- Auto-create city if still unresolved
    if v_city_id is null then
        if v_district is not null then
            v_city_code := upper(regexp_replace(left(v_district, 3), '[^A-Za-z0-9]', '', 'g'));
            if coalesce(v_city_code, '') = '' or exists (select 1 from public.org_cities where code = v_city_code) then
                v_city_code := coalesce(nullif(upper(regexp_replace(left(v_district, 4), '[^A-Za-z0-9]', '', 'g')), ''), 'CTY')
                    || '_' || substr(md5(v_district || now()::text), 1, 3);
            end if;
            insert into public.org_cities (state_id, zone_id, code, name, is_active)
            values (v_state_id, v_zone_id, v_city_code, v_district, true)
            on conflict (code) do update set is_active = true
            returning id into v_city_id;
        else
            v_city_id := coalesce(
                v_caller_profile.city_id,
                (select id from public.org_cities where state_id = v_state_id order by created_at asc limit 1)
            );
            select name into v_district from public.org_cities where id = v_city_id;
        end if;
    end if;

    if p_available_lans is not null and jsonb_typeof(p_available_lans) = 'array' and jsonb_array_length(p_available_lans) > 0 then
        v_lans := p_available_lans;
    else
        v_lans := '["LAN 1","LAN 2"]'::jsonb;
    end if;

    if p_id is not null and exists (select 1 from public.org_centres where id = p_id) then
        update public.org_centres
        set code = v_code,
            external_code = v_code,
            name = v_name,
            city_id = coalesce(v_city_id, city_id),
            state_id = coalesce(v_state_id, state_id),
            zone_id = coalesce(v_zone_id, zone_id),
            district_name = coalesce(v_district, district_name),
            address = coalesce(nullif(trim(coalesce(p_address, '')), ''), address),
            sub_hub = coalesce(nullif(trim(coalesce(p_sub_hub, '')), ''), sub_hub),
            available_lans = v_lans,
            is_active = true
        where id = p_id
        returning * into v_centre;
    else
        insert into public.org_centres (
            external_code, city_id, state_id, zone_id, code, name, district_name, sub_hub, address, available_lans, is_active
        ) values (
            v_code, v_city_id, v_state_id, v_zone_id, v_code, v_name, v_district,
            nullif(trim(coalesce(p_sub_hub, '')), ''),
            nullif(trim(coalesce(p_address, '')), ''),
            v_lans,
            true
        )
        on conflict (code) do update
        set external_code = excluded.external_code,
            name = excluded.name,
            city_id = coalesce(excluded.city_id, public.org_centres.city_id),
            state_id = coalesce(excluded.state_id, public.org_centres.state_id),
            zone_id = coalesce(excluded.zone_id, public.org_centres.zone_id),
            district_name = coalesce(excluded.district_name, public.org_centres.district_name),
            sub_hub = coalesce(excluded.sub_hub, public.org_centres.sub_hub),
            address = coalesce(excluded.address, public.org_centres.address),
            available_lans = excluded.available_lans,
            is_active = true
        returning * into v_centre;
    end if;

    return jsonb_build_object(
        'id', v_centre.id,
        'external_code', v_centre.external_code,
        'city_id', v_centre.city_id,
        'state_id', v_centre.state_id,
        'zone_id', v_centre.zone_id,
        'code', v_centre.code,
        'name', v_centre.name,
        'district_name', v_centre.district_name,
        'sub_hub', v_centre.sub_hub,
        'address', v_centre.address,
        'available_lans', v_centre.available_lans
    );
end;
$$;

revoke all on function public.upsert_org_centre(uuid, text, text, uuid, text, text, text, jsonb, text) from public;
grant execute on function public.upsert_org_centre(uuid, text, text, uuid, text, text, text, jsonb, text) to authenticated;

notify pgrst, 'reload schema';

commit;
