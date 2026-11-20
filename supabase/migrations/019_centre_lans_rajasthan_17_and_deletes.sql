-- ============================================================================
-- Taskz Migration 019:
--   1. Centre-specific LAN arrays (available_lans) + sub_hub + address on org_centres
--   2. Complete 17-centre Rajasthan dataset (exact TC codes, sub-hubs, LANs)
--   3. Updated get_org_hierarchy() + upsert_org_centre() RPC for future centres/LANs
--   4. Atomic RPCs: delete_task_group(), delete_shift(), delete_task_safe()
-- Forward-only, idempotent migration.
-- ============================================================================

begin;

-- ----------------------------------------------------------------------------
-- 1. ADD sub_hub, address & available_lans TO public.org_centres
-- ----------------------------------------------------------------------------
alter table public.org_centres
    add column if not exists sub_hub text,
    add column if not exists address text,
    add column if not exists available_lans jsonb not null default '["LAN 1","LAN 2"]'::jsonb;

-- ----------------------------------------------------------------------------
-- 2. UPSERT ALL 17 RAJASTHAN EXAMINATION CENTRES WITH EXACT LAN CONFIGURATIONS
-- ----------------------------------------------------------------------------
do $$
declare
    v_zone_id uuid;
    v_state_id uuid;
    v_city_id uuid;
    v_rec record;
begin
    -- Ensure North Zone (NZ)
    select id into v_zone_id
    from public.org_zones
    where upper(code) = 'NZ' or lower(name) = 'north zone'
    order by created_at asc
    limit 1;

    if v_zone_id is null then
        insert into public.org_zones (code, name, is_active)
        values ('NZ', 'North Zone', true)
        returning id into v_zone_id;
    end if;

    -- Ensure Rajasthan (RJ)
    select id into v_state_id
    from public.org_states
    where upper(code) = 'RJ' or lower(name) = 'rajasthan'
    order by created_at asc
    limit 1;

    if v_state_id is null then
        insert into public.org_states (zone_id, code, name, is_active)
        values (v_zone_id, 'RJ', 'Rajasthan', true)
        returning id into v_state_id;
    end if;

    -- Ensure 7 Rajasthan cities for the 17 centres
    for v_rec in
        select * from (
            values
                ('AJM', 'Ajmer'),
                ('BKN', 'Bikaner'),
                ('JAI', 'Jaipur'),
                ('JDP', 'Jodhpur'),
                ('KOT', 'Kota'),
                ('SIK', 'Sikar'),
                ('UDR', 'Udaipur')
        ) as c(code, name)
    loop
        select id into v_city_id
        from public.org_cities
        where upper(code) = v_rec.code or lower(name) = lower(v_rec.name)
        order by (upper(code) = v_rec.code) desc, created_at asc
        limit 1;

        if v_city_id is null then
            insert into public.org_cities (state_id, zone_id, code, name, is_active)
            values (v_state_id, v_zone_id, v_rec.code, v_rec.name, true);
        else
            update public.org_cities
            set state_id = v_state_id,
                zone_id = v_zone_id,
                is_active = true
            where id = v_city_id;
        end if;
    end loop;

    -- Deactivate old generic placeholder centres in cities now covered by real TC codes
    update public.org_centres
    set is_active = false
    where upper(code) in ('AJM-01', 'BKN-01', 'JDP-01', 'KOT-01', 'SIK-01', 'UDR-01', '142', '143', '144', '145');

    -- Upsert all 17 Rajasthan centres
    for v_rec in
        select * from (
            values
                ('8451',  'Ajmer',   'Ajmer',                   'iON Digital Zone iDZ Gegal Industrial Area',                    'Balaji Cyber Services, Plot No. F 31 C, Near Canara Bank, RIICO Industrial Area Geegal, Ajmer-305023',                            '["LAN 1","LAN 2"]'::jsonb),
                ('8665',  'Bikaner', 'Bikaner',                 'iON Digital Zone iDZ Naino Ka Bas',                              'M N Institute Campus, Jodhpur bypass road, near garden city, Ridmalsar, Post Udasar, Bikaner-334022',                              '["LAN 1","LAN 2"]'::jsonb),
                ('37201', 'Bikaner', 'Bikaner',                 'Dr Tanveer Malawat Nursing College Bikaner',                     'Jaipur Jodhpur Bypass NH-89 Near Toll Plaza Bikaner, Bikaner-334022',                                                              '["LAN 1","LAN 2"]'::jsonb),
                ('8571',  'Jaipur',  'Jaipur 2 (Jhotwara)',     'Sri Balaji College Of Engineering And Technology',               'Benad Road, Near Dadi ka Phatak, Jaipur-302013',                                                                                   '["LAN 1","LAN 2"]'::jsonb),
                ('41682', 'Jaipur',  'Jaipur 2 (Sitapura)',     'iON Digital Zone iDZ Sitapura 1',                                'Gate No 1, SP-1013 b i, 3rd Phase Industrial Area, Sitapura, Jaipur-302022',                                                       '["LAN 1","LAN 2","LAN 3"]'::jsonb),
                ('41683', 'Jaipur',  'Jaipur 2 (Sitapura)',     'iON Digital Zone iDZ Sitapura 2',                                'Gate No 2, SP-1013 b i, 3rd Phase Industrial Area, Sitapura, Jaipur-302022',                                                       '["LAN 4","LAN 5","LAN 6","LAN 7","LAN 8","LAN 9","LAN 10","LAN 11"]'::jsonb),
                ('141',   'Jaipur',  'Jaipur 2 (Sitapura) YIT', 'Yagyavalkya Institute of Technology',                            'YIT Lane, RIICO Industrial Area, Opp. Chokhi Dhani, Tonk Road, Sitapura, Jaipur-302022',                                           '["LAN 1","LAN 2"]'::jsonb),
                ('1118',  'Jaipur',  'Jaipur 1 (Kukas)',        'Jaipur Engineering College',                                     'SP-43, RIICO Industrial Area, Delhi Road, Kukas, Jaipur-302028',                                                                   '["LAN 1","LAN 2"]'::jsonb),
                ('24264', 'Jaipur',  'Jaipur',                  'Akashdeep Girls PG College',                                     'Akashdeep Girls PG College, Jaipur',                                                                                               '["LAN 1"]'::jsonb),
                ('8450',  'Jodhpur', 'Jodhpur',                 'iON Digital Zone iDZ Laxman Nagar',                              'Ms Tanwar Infotech Laxman Nagar Blok No 8 9 10, Ramjan Ji Ka Hatha, Banar Road, Jodhpur-342015',                                   '["LAN 1","LAN 2"]'::jsonb),
                ('33978', 'Jodhpur', 'Jodhpur',                 'Shree Vinayak Institute',                                        'Kh 116, Plot No 01, Digari Kallan, Shikargarh Road, Jodhpur-342015',                                                               '["LAN 1","LAN 2"]'::jsonb),
                ('10624', 'Kota',    'Kota',                    'Shiv Jyoti International School',                                'IPC - 15, RIICO Institutional Area, Ranpur, In front of Gurukul Institute of Technology, Kota-325003',                             '["LAN 1","LAN 2"]'::jsonb),
                ('42599', 'Kota',    'Kota_IDZ',                'iON Digital Zone iDZ Modi Kota (iON Digital Zone iDZ Nayagaon)', 'Raghav Modi, Village - Daulat Ganj, Nayagaon, Rawatbhata Road, Kota - Rajasthan, Pin - 324010',                                   '["LAN 1","LAN 2"]'::jsonb),
                ('1275',  'Sikar',   'Sikar',                   'Bhartiya Institute of Engineering and Technology',               'Bhartiya Public School, Near Sanwali Circle, Vill-Chandpura, PO-Bajagram Sanwali, Sikar-332021',                                   '["LAN 1","LAN 2"]'::jsonb),
                ('7850',  'Sikar',   'Sikar',                   'iON Digital Zone iDZ Chandpura',                                 'Bhartiya Public School, Near Sanwali Circle, Village Chandpura, PO-Bajagram Sanwali, Sikar-332021',                                '["LAN 1","LAN 2"]'::jsonb),
                ('24271', 'Sikar',   'Sikar',                   'Khwaja Gharib Nawaz College of Nursing',                         'Near Sabalpura Power House, Fathehpur Road, N.H. 11, Sikar-332001',                                                                '["LAN 1","LAN 2"]'::jsonb),
                ('8449',  'Udaipur', 'Udaipur',                 'iON Digital Zone iDZ Dholi Magri',                               'Raghav Infotech D C Tak Compound Adarsh Nagar, Opposite Pacific University Near Transport Nagar, Air Port Road, Udaipur-313024',    '["LAN 1","LAN 2"]'::jsonb)
        ) as t(code, city_name, sub_hub, name, address, lans)
    loop
        select id into v_city_id
        from public.org_cities
        where lower(name) = lower(v_rec.city_name)
          and state_id = v_state_id
        order by created_at asc
        limit 1;

        if exists (
            select 1 from public.org_centres oc
            where upper(oc.code) = upper(v_rec.code)
               or upper(coalesce(oc.external_code, '')) = upper(v_rec.code)
        ) then
            update public.org_centres
            set external_code = v_rec.code,
                code = v_rec.code,
                name = v_rec.name,
                city_id = coalesce(v_city_id, city_id),
                state_id = v_state_id,
                zone_id = v_zone_id,
                district_name = v_rec.city_name,
                sub_hub = v_rec.sub_hub,
                address = v_rec.address,
                available_lans = v_rec.lans,
                is_active = true
            where upper(code) = upper(v_rec.code)
               or upper(coalesce(external_code, '')) = upper(v_rec.code);
        else
            insert into public.org_centres (
                external_code, city_id, state_id, zone_id, code, name, district_name, sub_hub, address, available_lans, is_active
            ) values (
                v_rec.code, v_city_id, v_state_id, v_zone_id, v_rec.code, v_rec.name, v_rec.city_name, v_rec.sub_hub, v_rec.address, v_rec.lans, true
            );
        end if;
    end loop;
end $$;

-- ----------------------------------------------------------------------------
-- 3. UPDATE get_org_hierarchy() TO INCLUDE address, sub_hub & available_lans
-- ----------------------------------------------------------------------------
create or replace function public.get_org_hierarchy()
returns jsonb
language sql
stable
security definer
set search_path = public, pg_temp
as $$
    select jsonb_build_object(
        'zones', coalesce((select jsonb_agg(jsonb_build_object('id', id, 'code', code, 'name', name) order by name) from public.org_zones where is_active = true), '[]'::jsonb),
        'states', coalesce((select jsonb_agg(jsonb_build_object('id', id, 'zone_id', zone_id, 'code', code, 'name', name) order by name) from public.org_states where is_active = true), '[]'::jsonb),
        'cities', coalesce((select jsonb_agg(jsonb_build_object('id', id, 'state_id', state_id, 'zone_id', zone_id, 'code', code, 'name', name) order by name) from public.org_cities where is_active = true), '[]'::jsonb),
        'centres', coalesce((
            select jsonb_agg(
                jsonb_build_object(
                    'id', id,
                    'external_code', external_code,
                    'city_id', city_id,
                    'state_id', state_id,
                    'zone_id', zone_id,
                    'code', code,
                    'name', name,
                    'district_name', district_name,
                    'address', address,
                    'sub_hub', sub_hub,
                    'available_lans', coalesce(available_lans, '["LAN 1","LAN 2"]'::jsonb)
                ) order by code
            )
            from public.org_centres
            where is_active = true
        ), '[]'::jsonb)
    );
$$;

revoke all on function public.get_org_hierarchy() from public;
grant execute on function public.get_org_hierarchy() to authenticated;

-- ----------------------------------------------------------------------------
-- 4. ADD upsert_org_centre() RPC SO ADMINS/MANAGERS CAN ADD OR EDIT CENTRES & LANS
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

    -- Resolve city -> state -> zone
    if p_city_id is not null then
        select * into v_city from public.org_cities where id = p_city_id;
        if found then
            v_city_id := v_city.id;
            v_state_id := v_city.state_id;
            v_zone_id := v_city.zone_id;
            if v_district is null then v_district := v_city.name; end if;
        end if;
    end if;

    if v_city_id is null and v_district is not null then
        select * into v_city
        from public.org_cities
        where lower(name) = lower(v_district)
        order by created_at asc
        limit 1;
        if found then
            v_city_id := v_city.id;
            v_state_id := v_city.state_id;
            v_zone_id := v_city.zone_id;
        end if;
    end if;

    if v_state_id is null and nullif(trim(coalesce(p_state_name, '')), '') is not null then
        select * into v_state
        from public.org_states
        where lower(name) = lower(trim(p_state_name))
        order by created_at asc
        limit 1;
        if found then
            v_state_id := v_state.id;
            v_zone_id := v_state.zone_id;
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

    if v_city_id is null then
        if v_district is not null then
            insert into public.org_cities (state_id, zone_id, code, name, is_active)
            values (
                v_state_id,
                v_zone_id,
                upper(regexp_replace(left(v_district, 4), '[^A-Za-z0-9]', '', 'g')) || '_' || substr(md5(v_district || now()::text), 1, 3),
                v_district,
                true
            )
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

-- ----------------------------------------------------------------------------
-- 5. ATOMIC DELETE RPCs FOR EXAM GROUPS, SHIFTS & TASKS
-- ----------------------------------------------------------------------------
create or replace function public.delete_task_group(p_group_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_caller uuid := (select auth.uid());
    v_role text;
    v_group public.task_groups%rowtype;
begin
    if v_caller is null then
        raise exception 'Authentication required';
    end if;

    select * into v_group from public.task_groups where id = p_group_id;
    if not found then
        return jsonb_build_object('success', true, 'deleted_id', p_group_id);
    end if;

    v_role := public.get_effective_role_code(v_caller);
    if v_group.created_by <> v_caller
       and v_role not in ('SUPER_ADMIN', 'COMMAND_CENTER', 'ZOM', 'ROM', 'CITY_HEAD', 'VCO')
       and not public.can_manage_group(p_group_id) then
        raise exception 'Forbidden: you do not have permission to delete this exam group';
    end if;

    -- Clean child task relations first to avoid FK restriction
    delete from public.task_responses where task_id in (select id from public.tasks where group_id = p_group_id);
    delete from public.task_acknowledgements where task_id in (select id from public.tasks where group_id = p_group_id);
    delete from public.task_join_requests where task_id in (select id from public.tasks where group_id = p_group_id);
    delete from public.task_participants where task_id in (select id from public.tasks where group_id = p_group_id);
    delete from public.tasks where group_id = p_group_id;
    delete from public.shifts where group_id = p_group_id;
    delete from public.group_members where group_id = p_group_id;
    delete from public.group_join_requests where group_id = p_group_id;
    delete from public.task_groups where id = p_group_id;

    return jsonb_build_object('success', true, 'deleted_id', p_group_id);
end;
$$;

revoke all on function public.delete_task_group(uuid) from public;
grant execute on function public.delete_task_group(uuid) to authenticated;

create or replace function public.delete_shift(p_shift_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_caller uuid := (select auth.uid());
    v_role text;
    v_shift public.shifts%rowtype;
begin
    if v_caller is null then
        raise exception 'Authentication required';
    end if;

    select * into v_shift from public.shifts where id = p_shift_id;
    if not found then
        return jsonb_build_object('success', true, 'deleted_id', p_shift_id);
    end if;

    v_role := public.get_effective_role_code(v_caller);
    if v_shift.created_by <> v_caller
       and v_role not in ('SUPER_ADMIN', 'COMMAND_CENTER', 'ZOM', 'ROM', 'CITY_HEAD', 'VCO')
       and not public.can_manage_group(v_shift.group_id) then
        raise exception 'Forbidden: you do not have permission to delete this shift';
    end if;

    delete from public.task_responses where task_id in (select id from public.tasks where shift_id = p_shift_id);
    delete from public.task_acknowledgements where task_id in (select id from public.tasks where shift_id = p_shift_id);
    delete from public.task_join_requests where task_id in (select id from public.tasks where shift_id = p_shift_id);
    delete from public.task_participants where task_id in (select id from public.tasks where shift_id = p_shift_id);
    delete from public.tasks where shift_id = p_shift_id;
    delete from public.shifts where id = p_shift_id;

    return jsonb_build_object('success', true, 'deleted_id', p_shift_id);
end;
$$;

revoke all on function public.delete_shift(uuid) from public;
grant execute on function public.delete_shift(uuid) to authenticated;

notify pgrst, 'reload schema';

commit;
