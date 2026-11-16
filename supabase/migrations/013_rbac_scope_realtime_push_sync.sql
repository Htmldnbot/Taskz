-- ============================================================================
-- Taskz Migration 013: RBAC, Multi-Role, Org Hierarchy Scope, Automatic Task
-- Propagation, Scoped Incidents & Escalation, Realtime, Push & Delta Sync
-- Additive & idempotent migration — preserves all existing tables and RPCs.
-- ============================================================================

begin;

-- 1. ORGANIZATIONAL HIERARCHY TABLES (ZONE -> STATE -> CITY -> CENTRE)
create table if not exists public.org_zones (
    id uuid primary key default gen_random_uuid(),
    code text not null unique,
    name text not null,
    is_active boolean not null default true,
    created_at timestamptz not null default now()
);

create table if not exists public.org_states (
    id uuid primary key default gen_random_uuid(),
    zone_id uuid not null references public.org_zones(id) on delete cascade,
    code text not null unique,
    name text not null,
    is_active boolean not null default true,
    created_at timestamptz not null default now()
);

create table if not exists public.org_cities (
    id uuid primary key default gen_random_uuid(),
    state_id uuid not null references public.org_states(id) on delete cascade,
    zone_id uuid not null references public.org_zones(id) on delete cascade,
    code text not null unique,
    name text not null,
    is_active boolean not null default true,
    created_at timestamptz not null default now()
);

create table if not exists public.org_centres (
    id uuid primary key default gen_random_uuid(),
    external_code text unique,
    city_id uuid not null references public.org_cities(id) on delete cascade,
    state_id uuid not null references public.org_states(id) on delete cascade,
    zone_id uuid not null references public.org_zones(id) on delete cascade,
    code text not null unique,
    name text not null,
    district_name text,
    address text,
    is_active boolean not null default true,
    created_at timestamptz not null default now()
);

create index if not exists idx_org_states_zone on public.org_states(zone_id);
create index if not exists idx_org_cities_state on public.org_cities(state_id);
create index if not exists idx_org_cities_zone on public.org_cities(zone_id);
create index if not exists idx_org_centres_city on public.org_centres(city_id);
create index if not exists idx_org_centres_state on public.org_centres(state_id);
create index if not exists idx_org_centres_zone on public.org_centres(zone_id);
create index if not exists idx_org_centres_ext_code on public.org_centres(external_code);

-- NOTE: Sample dev/test org hierarchy seed moved to supabase/seeds/dev_sample_org_seed.sql
-- to ensure production org_zones/org_states/org_cities/org_centres rows are never overwritten.

-- 2. REUSE AND EXTEND public.work_roles + MULTI-ROLE public.user_roles
alter table public.work_roles
    add column if not exists canonical_code text,
    add column if not exists hierarchy_level integer not null default 1,
    add column if not exists default_scope_level text not null default 'CENTRE',
    add column if not exists is_self_assignable boolean not null default false;

do $$
declare
    v_existing_id uuid;
    v_role record;
begin
    -- Map single canonical IT_MANAGER row without duplicate canonical_code conflicts
    select id into v_existing_id
    from public.work_roles
    where id = '11111111-1111-4111-8111-111111111102'
       or canonical_code = 'IT_MANAGER'
       or upper(coalesce(code, '')) in ('ITM', 'IT_MANAGER')
    order by (id = '11111111-1111-4111-8111-111111111102') desc, sort_order asc
    limit 1;

    if v_existing_id is not null then
        update public.work_roles
        set canonical_code = null
        where canonical_code = 'IT_MANAGER' and id <> v_existing_id;

        update public.work_roles
        set canonical_code = 'IT_MANAGER', hierarchy_level = 10, default_scope_level = 'CENTRE', is_self_assignable = true
        where id = v_existing_id;
    else
        insert into public.work_roles (id, name, code, canonical_code, description, is_active, sort_order, hierarchy_level, default_scope_level, is_self_assignable)
        values ('11111111-1111-4111-8111-111111111102', 'IT Manager', 'ITM', 'IT_MANAGER', 'Technical support and workstation maintenance', true, 2, 10, 'CENTRE', true)
        on conflict (lower(name)) do update
        set canonical_code = excluded.canonical_code,
            hierarchy_level = excluded.hierarchy_level,
            default_scope_level = excluded.default_scope_level,
            is_self_assignable = excluded.is_self_assignable,
            is_active = true;
    end if;

    -- Map single canonical VCO row without duplicate canonical_code conflicts
    select id into v_existing_id
    from public.work_roles
    where id = '11111111-1111-4111-8111-111111111106'
       or canonical_code = 'VCO'
       or upper(coalesce(code, '')) = 'VCO'
    order by (id = '11111111-1111-4111-8111-111111111106') desc, sort_order asc
    limit 1;

    if v_existing_id is not null then
        update public.work_roles
        set canonical_code = null
        where canonical_code = 'VCO' and id <> v_existing_id;

        update public.work_roles
        set canonical_code = 'VCO', hierarchy_level = 20, default_scope_level = 'CITY', is_self_assignable = true
        where id = v_existing_id;
    else
        insert into public.work_roles (id, name, code, canonical_code, description, is_active, sort_order, hierarchy_level, default_scope_level, is_self_assignable)
        values ('11111111-1111-4111-8111-111111111106', 'VCO / Venue Head', 'VCO', 'VCO', 'Venue Commanding Officer / Venue Head', true, 6, 20, 'CITY', true)
        on conflict (lower(name)) do update
        set canonical_code = excluded.canonical_code,
            hierarchy_level = excluded.hierarchy_level,
            default_scope_level = excluded.default_scope_level,
            is_self_assignable = excluded.is_self_assignable,
            is_active = true;
    end if;

    -- Upsert higher organizational roles conflict-safely across id, lower(name), and code
    for v_role in
        select * from (
            values
                ('11111111-1111-4111-8111-111111111108'::uuid, 'City Head / HOM', 'CITY_HEAD', 'CITY_HEAD', 'City-wide operations supervision and review', 8, 30, 'CITY', false),
                ('11111111-1111-4111-8111-111111111109'::uuid, 'ROM (Regional Manager)', 'ROM', 'ROM', 'State / Regional Operations Manager', 9, 40, 'STATE', false),
                ('11111111-1111-4111-8111-111111111110'::uuid, 'ZOM (Zonal Manager)', 'ZOM', 'ZOM', 'Zonal Operations Manager', 10, 50, 'ZONE', false),
                ('11111111-1111-4111-8111-111111111111'::uuid, 'Command Center / SPOC', 'COMMAND_CENTER', 'COMMAND_CENTER', 'Central monitoring & operational coordination', 11, 60, 'GLOBAL', false),
                ('11111111-1111-4111-8111-111111111112'::uuid, 'Super Admin', 'SUPER_ADMIN', 'SUPER_ADMIN', 'Global administrative & RBAC governance', 12, 100, 'GLOBAL', false)
        ) as t(id, name, code, canonical_code, description, sort_order, hierarchy_level, default_scope_level, is_self_assignable)
    loop
        select id into v_existing_id
        from public.work_roles
        where id = v_role.id
           or lower(name) = lower(v_role.name)
           or canonical_code = v_role.canonical_code
           or upper(coalesce(code, '')) = v_role.code
        order by (id = v_role.id) desc, sort_order asc
        limit 1;

        if v_existing_id is not null then
            update public.work_roles
            set canonical_code = null
            where canonical_code = v_role.canonical_code and id <> v_existing_id;

            update public.work_roles
            set canonical_code = v_role.canonical_code,
                code = v_role.code,
                hierarchy_level = v_role.hierarchy_level,
                default_scope_level = v_role.default_scope_level,
                is_self_assignable = v_role.is_self_assignable,
                is_active = true
            where id = v_existing_id;
        else
            insert into public.work_roles (id, name, code, canonical_code, description, is_active, sort_order, hierarchy_level, default_scope_level, is_self_assignable)
            values (v_role.id, v_role.name, v_role.code, v_role.canonical_code, v_role.description, true, v_role.sort_order, v_role.hierarchy_level, v_role.default_scope_level, v_role.is_self_assignable);
        end if;
    end loop;
end $$;

create unique index if not exists idx_work_roles_canonical_code
    on public.work_roles(canonical_code)
    where canonical_code is not null;

create table if not exists public.user_roles (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.profiles(id) on delete cascade,
    role_id uuid not null references public.work_roles(id) on delete cascade,
    role_code text not null,
    is_primary boolean not null default false,
    assigned_by uuid references public.profiles(id) on delete set null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (user_id, role_id)
);

create index if not exists idx_user_roles_user on public.user_roles(user_id);
create index if not exists idx_user_roles_code on public.user_roles(role_code);
create index if not exists idx_user_roles_user_code on public.user_roles(user_id, role_code);

-- 3. EXTEND public.profiles WITH ACTIVE ROLE, ORG SCOPE & VERSIONS
alter table public.profiles
    add column if not exists active_role_code text,
    add column if not exists primary_role_code text,
    add column if not exists zone_id uuid references public.org_zones(id) on delete set null,
    add column if not exists state_id uuid references public.org_states(id) on delete set null,
    add column if not exists city_id uuid references public.org_cities(id) on delete set null,
    add column if not exists centre_id uuid references public.org_centres(id) on delete set null,
    add column if not exists centre_code text,
    add column if not exists city_name text,
    add column if not exists state_name text,
    add column if not exists zone_name text,
    add column if not exists email text,
    add column if not exists role_version bigint not null default 1,
    add column if not exists scope_version bigint not null default 1;

create index if not exists idx_profiles_centre on public.profiles(centre_id);
create index if not exists idx_profiles_city on public.profiles(city_id);
create index if not exists idx_profiles_state on public.profiles(state_id);
create index if not exists idx_profiles_zone on public.profiles(zone_id);
create index if not exists idx_profiles_active_role on public.profiles(active_role_code);

-- Backfill user_roles for existing profiles safely using resolved work_roles.id
insert into public.user_roles (user_id, role_id, role_code, is_primary)
select
    p.id,
    case
        when p.app_role = 'admin' then (select id from public.work_roles where canonical_code = 'SUPER_ADMIN' limit 1)
        else (select id from public.work_roles where canonical_code = 'IT_MANAGER' limit 1)
    end,
    case when p.app_role = 'admin' then 'SUPER_ADMIN' else 'IT_MANAGER' end,
    true
from public.profiles p
where not exists (select 1 from public.user_roles ur where ur.user_id = p.id)
on conflict (user_id, role_id) do nothing;

select set_config('taskz.bypass_rbac_guard', '1', true);

update public.profiles p
set active_role_code = coalesce(
        p.active_role_code,
        case when p.app_role = 'admin' then 'SUPER_ADMIN' else 'IT_MANAGER' end
    ),
    primary_role_code = coalesce(
        p.primary_role_code,
        case when p.app_role = 'admin' then 'SUPER_ADMIN' else 'IT_MANAGER' end
    ),
    email = coalesce(p.email, u.email::text)
from auth.users u
where u.id = p.id
  and (p.active_role_code is null or p.primary_role_code is null or p.email is null);

-- 4. ROLE & SCOPE RESOLUTION + PROTECTION TRIGGER
create or replace function public.is_super_admin()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.profiles p
        where p.id = (select auth.uid())
          and p.is_active = true
          and (
              p.app_role = 'admin'
              or exists (
                  select 1
                  from public.user_roles ur
                  where ur.user_id = p.id
                    and ur.role_code = 'SUPER_ADMIN'
              )
          )
    );
$$;

create or replace function public.is_admin()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select public.is_super_admin();
$$;

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
begin
    if p_user is null then
        return 'IT_MANAGER';
    end if;

    select * into v_profile from public.profiles where id = p_user;
    if not found then
        return 'IT_MANAGER';
    end if;

    -- Verify active_role_code against server-side user_roles (never trust stale/unassigned role)
    if v_profile.active_role_code is not null then
        if exists (
            select 1 from public.user_roles ur
            where ur.user_id = p_user and ur.role_code = upper(v_profile.active_role_code)
        ) or (upper(v_profile.active_role_code) = 'SUPER_ADMIN' and v_profile.app_role = 'admin') then
            return upper(v_profile.active_role_code);
        end if;
    end if;

    select ur.role_code into v_role
    from public.user_roles ur
    where ur.user_id = p_user
    order by ur.is_primary desc, ur.created_at asc
    limit 1;

    if v_role is not null then
        return upper(v_role);
    end if;

    if v_profile.app_role = 'admin' then
        return 'SUPER_ADMIN';
    end if;
    return 'IT_MANAGER';
end;
$$;

-- Protect RBAC/scope columns from unauthorized REST direct UPDATEs (allows SECURITY DEFINER / service_role / super_admin RPCs)
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

    -- Non-super-admin authenticated users cannot escalate role or tamper scope via REST PATCH
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

drop trigger if exists trg_protect_profile_rbac_columns on public.profiles;
create trigger trg_protect_profile_rbac_columns
before update on public.profiles
for each row
execute function public.protect_profile_rbac_columns();

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
set search_path = public, auth, pg_temp
as $$
declare
    v_role text;
    v_profile public.profiles%rowtype;
    v_scope text := upper(coalesce(nullif(trim(p_scope_level), ''), 'CENTRE'));
begin
    if p_user is null then
        return false;
    end if;

    select * into v_profile from public.profiles where id = p_user;
    if not found or not v_profile.is_active then
        return false;
    end if;

    v_role := public.get_effective_role_code(p_user);

    if v_role in ('SUPER_ADMIN', 'COMMAND_CENTER') then
        return true;
    end if;

    if v_scope = 'GLOBAL' then
        return true;
    end if;

    -- Zone role accesses everything within assigned zone
    if v_role = 'ZOM' then
        if v_profile.zone_id is null then
            return p_zone_id is null;
        end if;
        if p_zone_id is not null then
            return p_zone_id = v_profile.zone_id;
        end if;
        if p_state_id is not null then
            return exists (select 1 from public.org_states s where s.id = p_state_id and s.zone_id = v_profile.zone_id);
        end if;
        if p_city_id is not null then
            return exists (select 1 from public.org_cities c where c.id = p_city_id and c.zone_id = v_profile.zone_id);
        end if;
        if p_centre_id is not null then
            return exists (select 1 from public.org_centres ct where ct.id = p_centre_id and ct.zone_id = v_profile.zone_id);
        end if;
        return false;
    end if;

    -- State/Region role accesses state + its cities/centres + ancestor zone tasks
    if v_role = 'ROM' then
        if v_profile.state_id is null then
            return false;
        end if;
        if v_scope = 'ZONE' then
            return p_zone_id = v_profile.zone_id;
        end if;
        if p_state_id is not null then
            return p_state_id = v_profile.state_id;
        end if;
        if p_city_id is not null then
            return exists (select 1 from public.org_cities c where c.id = p_city_id and c.state_id = v_profile.state_id);
        end if;
        if p_centre_id is not null then
            return exists (select 1 from public.org_centres ct where ct.id = p_centre_id and ct.state_id = v_profile.state_id);
        end if;
        return false;
    end if;

    -- City Head & VCO: city + centres in city + ancestor state/zone tasks
    if v_role in ('CITY_HEAD', 'VCO') then
        if v_role = 'VCO' and p_centre_id is not null and v_profile.centre_id is not null and p_centre_id = v_profile.centre_id then
            return true;
        end if;
        if v_profile.city_id is null then
            return p_centre_id is not null and p_centre_id = v_profile.centre_id;
        end if;
        if v_scope = 'ZONE' then
            return p_zone_id = v_profile.zone_id;
        end if;
        if v_scope = 'STATE' then
            return p_state_id = v_profile.state_id;
        end if;
        if p_city_id is not null then
            return p_city_id = v_profile.city_id;
        end if;
        if p_centre_id is not null then
            return exists (select 1 from public.org_centres ct where ct.id = p_centre_id and ct.city_id = v_profile.city_id);
        end if;
        return false;
    end if;

    -- IT_MANAGER: own centre + ancestor city/state/zone tasks matching centre hierarchy
    if v_profile.centre_id is null then
        return p_zone_id is null and p_state_id is null and p_city_id is null and p_centre_id is null;
    end if;
    if v_scope = 'ZONE' then
        return p_zone_id = v_profile.zone_id;
    elsif v_scope = 'STATE' then
        return p_state_id = v_profile.state_id;
    elsif v_scope = 'CITY' then
        return p_city_id = v_profile.city_id;
    end if;
    return p_centre_id = v_profile.centre_id;
end;
$$;

-- 5. EXTEND TASKS FOR SINGLE-LOGICAL-ROW ORG SCOPE & ROLE TARGETING
alter table public.tasks
    add column if not exists scope_level text not null default 'CENTRE',
    add column if not exists zone_id uuid references public.org_zones(id) on delete set null,
    add column if not exists state_id uuid references public.org_states(id) on delete set null,
    add column if not exists city_id uuid references public.org_cities(id) on delete set null,
    add column if not exists centre_id uuid references public.org_centres(id) on delete set null,
    add column if not exists target_role_codes jsonb not null default '[]'::jsonb,
    add column if not exists deleted_at timestamptz null;

create index if not exists idx_tasks_scope_level on public.tasks(scope_level);
create index if not exists idx_tasks_zone on public.tasks(zone_id);
create index if not exists idx_tasks_state on public.tasks(state_id);
create index if not exists idx_tasks_city on public.tasks(city_id);
create index if not exists idx_tasks_centre on public.tasks(centre_id);
create index if not exists idx_tasks_updated_at on public.tasks(updated_at desc);
create index if not exists idx_tasks_target_roles_gin on public.tasks using gin(target_role_codes);

create or replace function public.bump_task_version()
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

drop trigger if exists trg_bump_task_version on public.tasks;
create trigger trg_bump_task_version
before update on public.tasks
for each row
execute function public.bump_task_version();

-- Distinguish VIEW vs MANAGE (Edit/Assign/Reassign/Delete) permissions at database level
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
          and (
              public.is_super_admin()
              or t.created_by = (select auth.uid())
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
                  public.can_access_org_scope(t.scope_level, t.zone_id, t.state_id, t.city_id, t.centre_id, (select auth.uid()))
                  and (
                      t.visibility = 'everyone'
                      or jsonb_array_length(coalesce(t.target_role_codes, '[]'::jsonb)) = 0
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
          and (
              public.is_super_admin()
              or t.created_by = (select auth.uid())
              or (
                  public.get_effective_role_code((select auth.uid())) = 'COMMAND_CENTER'
                  and public.can_access_org_scope(t.scope_level, t.zone_id, t.state_id, t.city_id, t.centre_id, (select auth.uid()))
              )
          )
    );
$$;

-- 6. EXTEND system_incidents WITH SCOPE, STATUS & ESCALATION CHAIN
alter table public.system_incidents
    drop constraint if exists system_incidents_centre_id_fkey;

alter table public.system_incidents
    add column if not exists scope_level text not null default 'CENTRE',
    add column if not exists zone_id uuid references public.org_zones(id) on delete set null,
    add column if not exists state_id uuid references public.org_states(id) on delete set null,
    add column if not exists city_id uuid references public.org_cities(id) on delete set null,
    add column if not exists status text not null default 'OPEN',
    add column if not exists escalation_level text not null default 'IT_MANAGER',
    add column if not exists assigned_to uuid references public.profiles(id) on delete set null,
    add column if not exists escalated_by uuid references public.profiles(id) on delete set null,
    add column if not exists escalated_at timestamptz null,
    add column if not exists resolved_at timestamptz null,
    add column if not exists resolved_by uuid references public.profiles(id) on delete set null,
    add column if not exists deleted_at timestamptz null,
    add column if not exists version bigint not null default 1;

alter table public.system_incident_audit
    add column if not exists changed_by_name text;

create index if not exists idx_incidents_scope on public.system_incidents(scope_level);
create index if not exists idx_incidents_zone on public.system_incidents(zone_id);
create index if not exists idx_incidents_state on public.system_incidents(state_id);
create index if not exists idx_incidents_city on public.system_incidents(city_id);
create index if not exists idx_incidents_status on public.system_incidents(status);
create index if not exists idx_incidents_escalation on public.system_incidents(escalation_level);
create index if not exists idx_incidents_updated on public.system_incidents(updated_at desc);

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
        where si.id = p_incident
          and si.deleted_at is null
          and (
              public.is_super_admin()
              or si.created_by = (select auth.uid())
              or si.assigned_to = (select auth.uid())
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
                  (select auth.uid())
              )
          )
    );
$$;

drop policy if exists system_incidents_select_policy on public.system_incidents;
create policy system_incidents_select_policy on public.system_incidents
for select to authenticated
using (
    public.is_active_user() and public.can_view_incident(id)
);

drop policy if exists system_incidents_insert_policy on public.system_incidents;
create policy system_incidents_insert_policy on public.system_incidents
for insert to authenticated
with check (
    public.is_active_user() and created_by = (select auth.uid())
);

drop policy if exists system_incidents_update_policy on public.system_incidents;
create policy system_incidents_update_policy on public.system_incidents
for update to authenticated
using (
    public.is_active_user()
    and (
        public.is_super_admin()
        or created_by = (select auth.uid())
        or assigned_to = (select auth.uid())
        or (
            public.get_effective_role_code((select auth.uid())) in ('VCO', 'CITY_HEAD', 'ROM', 'ZOM', 'COMMAND_CENTER')
            and public.can_view_incident(id)
        )
    )
)
with check (
    public.is_active_user()
    and (
        public.is_super_admin()
        or created_by = (select auth.uid())
        or assigned_to = (select auth.uid())
        or (
            public.get_effective_role_code((select auth.uid())) in ('VCO', 'CITY_HEAD', 'ROM', 'ZOM', 'COMMAND_CENTER')
            and public.can_view_incident(id)
        )
    )
);

-- 7. NOTIFICATIONS, DEDUP, DEEP LINKS & DEVICE TOKENS
alter table public.notifications
    add column if not exists event_key text,
    add column if not exists category text not null default 'task_update',
    add column if not exists deep_link text,
    add column if not exists scope_level text,
    add column if not exists incident_id uuid references public.system_incidents(id) on delete cascade,
    add column if not exists push_dispatched_at timestamptz null;

create unique index if not exists idx_notifications_user_event_key_uniq
    on public.notifications(user_id, event_key)
    where event_key is not null;

create index if not exists idx_notifications_category on public.notifications(category, created_at desc);

alter table public.device_tokens
    add column if not exists device_id text,
    add column if not exists app_version text;

create unique index if not exists idx_device_tokens_user_device_uniq
    on public.device_tokens(user_id, device_id)
    where device_id is not null;

create index if not exists idx_device_tokens_active_user
    on public.device_tokens(user_id, is_active);

-- 8. USER SYNC STATE & CLIENT EVENTS IDEMPOTENCY EXTENSIONS
alter table public.user_sync_state
    add column if not exists sync_version bigint not null default 1,
    add column if not exists last_delta_sync_at timestamptz null,
    add column if not exists active_role_code text;

alter table public.client_events
    add column if not exists client_operation_id text,
    add column if not exists target_id uuid;

create unique index if not exists idx_client_events_client_operation_uniq
    on public.client_events(client_operation_id);

create unique index if not exists group_member_active_unique
    on public.group_members(group_id, user_id)
    where status = 'active';

create unique index if not exists task_participant_active_unique
    on public.task_participants(task_id, user_id)
    where status = 'active';

-- 9. NOTIFICATION HELPER WITH IDEMPOTENCY KEY
create or replace function public.create_scoped_notification(
    p_user uuid,
    p_type text,
    p_category text,
    p_title text,
    p_body text default null,
    p_task uuid default null,
    p_incident uuid default null,
    p_group uuid default null,
    p_event_key text default null,
    p_deep_link text default null,
    p_scope_level text default null,
    p_data jsonb default '{}'::jsonb
)
returns uuid
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_id uuid;
begin
    if p_user is null then
        return null;
    end if;

    if p_event_key is not null then
        insert into public.notifications(
            user_id, type, category, title, body, task_id, incident_id, group_id,
            event_key, deep_link, scope_level, data
        ) values (
            p_user, p_type, coalesce(nullif(p_category, ''), 'task_update'), p_title, p_body,
            p_task, p_incident, p_group, p_event_key, p_deep_link, p_scope_level, coalesce(p_data, '{}'::jsonb)
        )
        on conflict (user_id, event_key) where event_key is not null do nothing
        returning id into v_id;
    else
        insert into public.notifications(
            user_id, type, category, title, body, task_id, incident_id, group_id,
            deep_link, scope_level, data
        ) values (
            p_user, p_type, coalesce(nullif(p_category, ''), 'task_update'), p_title, p_body,
            p_task, p_incident, p_group, p_deep_link, p_scope_level, coalesce(p_data, '{}'::jsonb)
        )
        returning id into v_id;
    end if;

    return v_id;
end;
$$;

-- 10. INITIAL ONBOARDING ROLE RPC (RESTRICTED STRICTLY TO IT_MANAGER OR VCO)
create or replace function public.set_initial_onboarding_role(
    p_role_code text,
    p_centre_code text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_caller uuid := auth.uid();
    v_code text := upper(coalesce(trim(p_role_code), 'IT_MANAGER'));
    v_role_id uuid;
    v_centre record;
    v_has_higher boolean;
begin
    if v_caller is null then
        raise exception 'Authentication required';
    end if;

    if v_code not in ('IT_MANAGER', 'VCO') then
        raise exception 'Forbidden: onboarding role must be IT_MANAGER or VCO (cannot self-assign %)', v_code;
    end if;

    select exists (
        select 1 from public.user_roles ur
        where ur.user_id = v_caller and ur.role_code not in ('IT_MANAGER', 'VCO')
    ) into v_has_higher;

    select id into v_role_id
    from public.work_roles
    where canonical_code = v_code or upper(coalesce(code, '')) = v_code
    limit 1;

    if v_role_id is null then
        v_role_id := case when v_code = 'VCO' then '11111111-1111-4111-8111-111111111106'::uuid else '11111111-1111-4111-8111-111111111102'::uuid end;
    end if;

    perform set_config('Taskz.bypass_rbac_guard', '1', true);

    insert into public.user_roles (user_id, role_id, role_code, is_primary, assigned_by)
    values (v_caller, v_role_id, v_code, true, v_caller)
    on conflict (user_id, role_id) do update set is_primary = true;

    if p_centre_code is not null and trim(p_centre_code) <> '' then
        select c.id as centre_id, c.code as centre_code, c.city_id, ct.name as city_name,
               c.state_id, st.name as state_name, c.zone_id, zn.name as zone_name
        into v_centre
        from public.org_centres c
        join public.org_cities ct on ct.id = c.city_id
        join public.org_states st on st.id = c.state_id
        join public.org_zones zn on zn.id = c.zone_id
        where upper(c.code) = upper(trim(p_centre_code)) or c.external_code = trim(p_centre_code)
        limit 1;
    end if;

    update public.profiles
    set active_role_code = case when v_has_higher then active_role_code else v_code end,
        primary_role_code = case when v_has_higher then primary_role_code else v_code end,
        centre_id = coalesce(centre_id, v_centre.centre_id),
        centre_code = coalesce(centre_code, v_centre.centre_code),
        city_id = coalesce(city_id, v_centre.city_id),
        city_name = coalesce(city_name, v_centre.city_name),
        state_id = coalesce(state_id, v_centre.state_id),
        state_name = coalesce(state_name, v_centre.state_name),
        zone_id = coalesce(zone_id, v_centre.zone_id),
        zone_name = coalesce(zone_name, v_centre.zone_name),
        role_version = role_version + 1
    where id = v_caller;

    return jsonb_build_object('success', true, 'assigned_role', v_code);
end;
$$;

revoke all on function public.set_initial_onboarding_role(text, text) from public;
grant execute on function public.set_initial_onboarding_role(text, text) to authenticated;

-- 11. AUTHORIZATION CONTEXT & MULTI-ROLE SWITCHING RPCs
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
            'centre_code', v_profile.centre_code
        ),
        'role_version', v_profile.role_version,
        'scope_version', v_profile.scope_version,
        'server_now', now()
    );
end;
$$;

revoke all on function public.get_my_authorization_context() from public;
grant execute on function public.get_my_authorization_context() to authenticated;

create or replace function public.switch_active_role(p_role_code text)
returns jsonb
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_caller uuid := auth.uid();
    v_code text := upper(trim(coalesce(p_role_code, '')));
    v_allowed boolean;
begin
    if v_caller is null then
        raise exception 'Authentication required';
    end if;

    select exists (
        select 1 from public.user_roles ur
        where ur.user_id = v_caller and ur.role_code = v_code
    ) or (v_code = 'SUPER_ADMIN' and exists (
        select 1 from public.profiles p where p.id = v_caller and p.app_role = 'admin'
    )) into v_allowed;

    if not v_allowed then
        raise exception 'Forbidden: role % is not assigned to your account', v_code;
    end if;

    perform set_config('Taskz.bypass_rbac_guard', '1', true);

    update public.profiles
    set active_role_code = v_code,
        role_version = role_version + 1
    where id = v_caller;

    update public.user_sync_state
    set active_role_code = v_code,
        sync_version = sync_version + 1,
        updated_at = now()
    where user_id = v_caller;

    return public.get_my_authorization_context();
end;
$$;

revoke all on function public.switch_active_role(text) from public;
grant execute on function public.switch_active_role(text) to authenticated;

-- 12. ORG HIERARCHY FETCH RPC
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
        'centres', coalesce((select jsonb_agg(jsonb_build_object('id', id, 'external_code', external_code, 'city_id', city_id, 'state_id', state_id, 'zone_id', zone_id, 'code', code, 'name', name, 'district_name', district_name) order by code) from public.org_centres where is_active = true), '[]'::jsonb)
    );
$$;

revoke all on function public.get_org_hierarchy() from public;
grant execute on function public.get_org_hierarchy() to authenticated;

-- 13. UPDATE create_task_atomic WITH SCOPE PROPAGATION & ROLE TARGETING (SINGLE LOGICAL TASK ROW)
create or replace function public.create_task_atomic(p_task jsonb)
returns jsonb
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_caller uuid := auth.uid();
    v_caller_role text;
    v_caller_profile public.profiles%rowtype;
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
    v_scope_level text;
    v_zone_id uuid;
    v_state_id uuid;
    v_city_id uuid;
    v_centre_id uuid;
    v_target_role_codes jsonb;
    v_assigned_user_id text;
    v_assigned_uuid uuid;
    v_req jsonb;
    v_role_id uuid;
    v_req_count integer;
    v_created_task public.tasks%rowtype;
    v_caller_name text;
    v_is_active_emp boolean;
    v_recipient record;
    v_notified_count integer := 0;
begin
    if v_caller is null then
        raise exception 'Authentication required to create tasks';
    end if;

    select * into v_caller_profile from public.profiles where id = v_caller;
    if not found or not v_caller_profile.is_active then
        raise exception 'Active user account required to create tasks';
    end if;

    v_caller_role := public.get_effective_role_code(v_caller);

    v_client_op_id := nullif(trim(p_task->>'client_operation_id'), '');
    if v_client_op_id is not null then
        select * into v_existing_task from public.tasks where client_operation_id = v_client_op_id;
        if found then
            return jsonb_build_object(
                'success', true,
                'is_duplicate', true,
                'task_id', v_existing_task.id,
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

    -- Scope authorization enforcement
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

    -- Derive missing ancestor org IDs cleanly
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
        v_state_id := null; v_city_id := null; v_centre_id := null;
    elsif v_scope_level = 'STATE' then
        v_city_id := null; v_centre_id := null;
    elsif v_scope_level = 'CITY' then
        v_centre_id := null;
    end if;

    if not public.can_access_org_scope(v_scope_level, v_zone_id, v_state_id, v_city_id, v_centre_id, v_caller) then
        raise exception 'Forbidden: caller scope does not authorize target % scope', v_scope_level;
    end if;

    v_target_role_codes := case
        when p_task ? 'target_role_codes' and jsonb_typeof(p_task->'target_role_codes') = 'array' then p_task->'target_role_codes'
        else '[]'::jsonb
    end;

    -- Resolve valid group or shared General Operations ('GEN-OPS') station
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

    v_caller_name := v_caller_profile.full_name;

    insert into public.task_participants(task_id, user_id, source, status, assigned_by)
    values (v_task_id, v_caller, 'assigned', 'active', v_caller)
    on conflict (task_id, user_id) where status = 'active' do nothing;

    -- Explicit assigned users (validated active)
    if p_task ? 'assigned_user_ids' and jsonb_typeof(p_task->'assigned_user_ids') = 'array' then
        for v_assigned_user_id in select jsonb_array_elements_text(p_task->'assigned_user_ids')
        loop
            v_assigned_uuid := v_assigned_user_id::uuid;
            select is_active into v_is_active_emp from public.profiles where id = v_assigned_uuid;
            if v_is_active_emp is null or not v_is_active_emp then
                raise exception 'Assigned user "%" is not active', v_assigned_user_id;
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
                coalesce(v_caller_name, 'Supervisor') || ' assigned you to "' || v_title || '" (' || v_scope_level || ')',
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

    -- Automatic org-scope + role propagation (SINGLE LOGICAL TASK ROW; notifies eligible authorized users only)
    for v_recipient in
        select distinct p.id
        from public.profiles p
        join public.user_roles ur on ur.user_id = p.id
        where p.is_active = true
          and p.id <> v_caller
          and (
              jsonb_array_length(v_target_role_codes) = 0
              or v_target_role_codes ? ur.role_code
          )
          and (
              v_scope_level = 'GLOBAL'
              or (v_scope_level = 'ZONE' and p.zone_id = v_zone_id)
              or (v_scope_level = 'STATE' and p.state_id = v_state_id)
              or (v_scope_level = 'CITY' and p.city_id = v_city_id)
              or (v_scope_level = 'CENTRE' and p.centre_id = v_centre_id)
          )
        limit 250
    loop
        perform public.create_scoped_notification(
            v_recipient.id,
            'task_assigned',
            'task_assignment',
            'New ' || v_scope_level || ' Task: ' || v_title,
            coalesce(v_caller_name, 'Operations') || ' published "' || v_title || '" for your scope',
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
        'Task created by ' || coalesce(v_caller_name, 'Creator') || ' [' || v_scope_level || ']'
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

-- 14. SCOPED INCIDENT ESCALATION RPC (IT_MANAGER -> VCO -> CITY_HEAD -> ROM -> ZOM -> SUPER_ADMIN)
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
    v_caller uuid := auth.uid();
    v_target text := upper(trim(coalesce(p_target_level, '')));
    v_inc public.system_incidents%rowtype;
    v_caller_name text;
    v_recipient record;
    v_notified integer := 0;
begin
    if v_caller is null or not public.is_active_user() then
        raise exception 'Active authentication required';
    end if;

    if v_target not in ('IT_MANAGER', 'VCO', 'CITY_HEAD', 'ROM', 'ZOM', 'SUPER_ADMIN') then
        raise exception 'Invalid escalation target: %', v_target;
    end if;

    if p_client_operation_id is not null then
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

    select full_name into v_caller_name from public.profiles where id = v_caller;

    update public.system_incidents
    set escalation_level = v_target,
        status = case when status = 'RESOLVED' then status else 'ESCALATED' end,
        escalated_by = v_caller,
        escalated_at = now(),
        updated_at = now(),
        updated_by = v_caller,
        version = coalesce(version, 1) + 1
    where id = p_incident_id
    returning * into v_inc;

    insert into public.system_incident_audit(incident_id, changed_by, changed_by_name, action, previous_values, new_values)
    values (
        p_incident_id, v_caller, v_caller_name, 'ESCALATED',
        jsonb_build_object('previous_level', v_inc.escalation_level),
        jsonb_build_object('target_level', v_target, 'reason', p_reason)
    );

    -- Notify only authorized recipients in scope matching target escalation level
    for v_recipient in
        select distinct p.id
        from public.profiles p
        join public.user_roles ur on ur.user_id = p.id
        where p.is_active = true
          and p.id <> v_caller
          and ur.role_code = v_target
          and (
              v_target = 'SUPER_ADMIN'
              or (v_target = 'ZOM' and (v_inc.zone_id is null or p.zone_id = v_inc.zone_id))
              or (v_target = 'ROM' and (v_inc.state_id is null or p.state_id = v_inc.state_id))
              or (v_target in ('CITY_HEAD', 'VCO') and (v_inc.city_id is null or p.city_id = v_inc.city_id))
              or (v_target = 'IT_MANAGER' and (v_inc.centre_code = p.centre_code))
          )
        limit 100
    loop
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
        'escalation_level', v_target,
        'notified_recipients', v_notified,
        'version', v_inc.version
    );
end;
$$;

revoke all on function public.escalate_system_incident(uuid, text, text, text) from public;
grant execute on function public.escalate_system_incident(uuid, text, text, text) to authenticated;

-- 15. SERVER-AUTHORIZED SCOPED MEMBER SEARCH RPC
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

    return query
    select
        p.id,
        p.employee_id,
        p.full_name,
        coalesce(p.email, u.email)::text as email,
        case
            when v_caller_role in ('SUPER_ADMIN', 'COMMAND_CENTER', 'CITY_HEAD', 'ROM', 'ZOM', 'VCO')
                 or p.id = v_caller
                 or p.centre_id = v_me.centre_id
            then p.mobile
            else null
        end as mobile,
        p.app_role,
        public.get_effective_role_code(p.id) as active_role_code,
        coalesce((select array_agg(ur.role_code order by ur.is_primary desc, ur.role_code)
                  from public.user_roles ur where ur.user_id = p.id), array['IT_MANAGER']) as assigned_roles,
        p.centre_id,
        p.centre_code,
        p.city_id,
        p.city_name,
        p.state_id,
        p.state_name,
        p.zone_id,
        p.zone_name,
        p.is_active
    from public.profiles p
    left join auth.users u on u.id = p.id
    where (v_caller_role = 'SUPER_ADMIN' or p.is_active = true)
      and (
          v_caller_role in ('SUPER_ADMIN', 'COMMAND_CENTER')
          or (v_caller_role = 'ZOM' and v_me.zone_id is not null and p.zone_id = v_me.zone_id)
          or (v_caller_role = 'ROM' and v_me.state_id is not null and p.state_id = v_me.state_id)
          or (v_caller_role in ('CITY_HEAD', 'VCO') and v_me.city_id is not null and p.city_id = v_me.city_id)
          or (v_caller_role = 'IT_MANAGER' and ((v_me.centre_id is not null and p.centre_id = v_me.centre_id) or p.id = v_caller))
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
          or coalesce(p.centre_code, '') ilike '%' || v_q || '%'
          or coalesce(p.city_name, '') ilike '%' || v_q || '%'
      )
    order by p.full_name asc
    limit least(greatest(coalesce(p_limit, 40), 1), 100);
end;
$$;

revoke all on function public.search_scoped_members(text, text, uuid, integer) from public;
grant execute on function public.search_scoped_members(text, text, uuid, integer) to authenticated;

-- 16. SUPER ADMIN RBAC + SCOPE GOVERNANCE RPC + AUDIT LOG
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
    v_caller uuid := auth.uid();
    v_old public.profiles%rowtype;
    v_old_roles text[];
    v_clean_roles text[];
    v_code text;
    v_role_id uuid;
    v_primary text;
    v_zone_id uuid := p_zone_id;
    v_state_id uuid := p_state_id;
    v_city_id uuid := p_city_id;
    v_centre_id uuid := p_centre_id;
    v_zone_name text;
    v_state_name text;
    v_city_name text;
    v_centre_code text;
    v_ver bigint;
begin
    if not public.is_super_admin() then
        raise exception 'Forbidden: only Super Admin can manage user roles and scope';
    end if;

    select * into v_old from public.profiles where id = p_target_user_id;
    if not found then
        raise exception 'Target user not found';
    end if;

    select array_agg(role_code) into v_old_roles from public.user_roles where user_id = p_target_user_id;

    select coalesce(array_agg(distinct upper(trim(r))), array['IT_MANAGER'])
    into v_clean_roles
    from unnest(coalesce(p_role_codes, array['IT_MANAGER'])) r
    where upper(trim(r)) in ('IT_MANAGER', 'VCO', 'CITY_HEAD', 'ROM', 'ZOM', 'COMMAND_CENTER', 'SUPER_ADMIN');

    if array_length(v_clean_roles, 1) is null or array_length(v_clean_roles, 1) = 0 then
        v_clean_roles := array['IT_MANAGER'];
    end if;

    v_primary := upper(coalesce(nullif(trim(p_active_role_code), ''), v_clean_roles[1]));
    if not (v_primary = any(v_clean_roles)) then
        v_primary := v_clean_roles[1];
    end if;

    -- Resolve org names cleanly from hierarchy
    if v_centre_id is not null then
        select c.code, c.city_id, ct.name, c.state_id, st.name, c.zone_id, zn.name
        into v_centre_code, v_city_id, v_city_name, v_state_id, v_state_name, v_zone_id, v_zone_name
        from public.org_centres c
        join public.org_cities ct on ct.id = c.city_id
        join public.org_states st on st.id = c.state_id
        join public.org_zones zn on zn.id = c.zone_id
        where c.id = v_centre_id;
    elsif v_city_id is not null then
        select ct.name, ct.state_id, st.name, ct.zone_id, zn.name
        into v_city_name, v_state_id, v_state_name, v_zone_id, v_zone_name
        from public.org_cities ct
        join public.org_states st on st.id = ct.state_id
        join public.org_zones zn on zn.id = ct.zone_id
        where ct.id = v_city_id;
    elsif v_state_id is not null then
        select st.name, st.zone_id, zn.name
        into v_state_name, v_zone_id, v_zone_name
        from public.org_states st
        join public.org_zones zn on zn.id = st.zone_id
        where st.id = v_state_id;
    elsif v_zone_id is not null then
        select name into v_zone_name from public.org_zones where id = v_zone_id;
    end if;

    perform set_config('Taskz.bypass_rbac_guard', '1', true);

    delete from public.user_roles
    where user_id = p_target_user_id
      and not (role_code = any(v_clean_roles));

    foreach v_code in array v_clean_roles
    loop
        select id into v_role_id from public.work_roles where canonical_code = v_code or upper(coalesce(code, '')) = v_code limit 1;
        if v_role_id is not null then
            insert into public.user_roles(user_id, role_id, role_code, is_primary, assigned_by)
            values (p_target_user_id, v_role_id, v_code, v_code = v_primary, v_caller)
            on conflict (user_id, role_id) do update
            set is_primary = (v_code = v_primary), assigned_by = v_caller, updated_at = now();
        end if;
    end loop;

    update public.profiles
    set active_role_code = v_primary,
        primary_role_code = v_primary,
        app_role = case when 'SUPER_ADMIN' = any(v_clean_roles) then 'admin' else 'employee' end,
        zone_id = v_zone_id,
        zone_name = v_zone_name,
        state_id = v_state_id,
        state_name = v_state_name,
        city_id = v_city_id,
        city_name = v_city_name,
        centre_id = v_centre_id,
        centre_code = v_centre_code,
        is_active = coalesce(p_is_active, is_active),
        role_version = role_version + 1,
        scope_version = scope_version + 1
    where id = p_target_user_id
    returning role_version into v_ver;

    insert into public.admin_audit_log(admin_user_id, target_user_id, action, entity_type, entity_id, metadata)
    values (
        v_caller,
        p_target_user_id,
        'RBAC_SCOPE_UPDATED',
        'profiles',
        p_target_user_id,
        jsonb_build_object(
            'old_roles', v_old_roles,
            'new_roles', v_clean_roles,
            'active_role', v_primary,
            'zone_id', v_zone_id,
            'state_id', v_state_id,
            'city_id', v_city_id,
            'centre_id', v_centre_id,
            'is_active', coalesce(p_is_active, v_old.is_active)
        )
    );

    perform public.create_scoped_notification(
        p_target_user_id,
        'role_changed',
        'admin_alert',
        'Access & Role Updated',
        'Your assigned roles (' || array_to_string(v_clean_roles, ', ') || ') or operational scope were updated.',
        null, null, null,
        'role_changed:' || p_target_user_id::text || ':' || v_ver::text,
        'taskz://profile/roles',
        null,
        jsonb_build_object('roles', v_clean_roles, 'active_role', v_primary, 'role_version', v_ver)
    );

    return jsonb_build_object('success', true, 'user_id', p_target_user_id, 'roles', v_clean_roles, 'role_version', v_ver);
end;
$$;

revoke all on function public.admin_update_user_access(uuid, text[], text, uuid, uuid, uuid, uuid, boolean) from public;
grant execute on function public.admin_update_user_access(uuid, text[], text, uuid, uuid, uuid, uuid, boolean) to authenticated;

create or replace function public.get_admin_audit_logs(p_limit integer default 50)
returns jsonb
language plpgsql
stable
security definer
set search_path = public, pg_temp
as $$
begin
    if not public.is_super_admin() then
        raise exception 'Forbidden: only Super Admin can inspect audit logs';
    end if;

    return coalesce((
        select jsonb_agg(jsonb_build_object(
            'id', a.id,
            'admin_user_id', a.admin_user_id,
            'admin_name', adm.full_name,
            'target_user_id', a.target_user_id,
            'target_name', tgt.full_name,
            'target_employee_id', tgt.employee_id,
            'action', a.action,
            'entity_type', a.entity_type,
            'metadata', a.metadata,
            'created_at', a.created_at
        ) order by a.created_at desc)
        from (
            select * from public.admin_audit_log order by created_at desc limit least(greatest(coalesce(p_limit, 50), 1), 200)
        ) a
        left join public.profiles adm on adm.id = a.admin_user_id
        left join public.profiles tgt on tgt.id = a.target_user_id
    ), '[]'::jsonb);
end;
$$;

revoke all on function public.get_admin_audit_logs(integer) from public;
grant execute on function public.get_admin_audit_logs(integer) to authenticated;

-- 17. SERVER-AUTHORIZED DASHBOARD COUNTS & DELTA SYNC PULL RPCs
create or replace function public.get_scoped_dashboard_summary()
returns jsonb
language plpgsql
stable
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_caller uuid := auth.uid();
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

    if v_role in ('SUPER_ADMIN', 'COMMAND_CENTER', 'ZOM', 'ROM', 'CITY_HEAD') then
        select count(*) into v_active_users from public.profiles where is_active = true;
        select count(*) into v_total_centres from public.org_centres where is_active = true;
    end if;

    return jsonb_build_object(
        'active_role', v_role,
        'pending_tasks', coalesce(v_pending_tasks, 0),
        'overdue_tasks', coalesce(v_overdue_tasks, 0),
        'assigned_tasks', coalesce(v_assigned_tasks, 0),
        'open_incidents', coalesce(v_open_incidents, 0),
        'escalated_incidents', coalesce(v_escalated_incidents, 0),
        'unread_notifications', coalesce(v_unread_notifications, 0),
        'active_users', v_active_users,
        'total_centres', v_total_centres,
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
    v_caller uuid := auth.uid();
    v_auth_ctx jsonb;
    v_profile public.profiles%rowtype;
    v_effective_since timestamptz := p_since;
    v_tasks jsonb;
    v_incidents jsonb;
    v_notifications jsonb;
    v_summary jsonb;
begin
    if v_caller is null then
        raise exception 'Authentication required';
    end if;

    select * into v_profile from public.profiles where id = v_caller;
    if not found or not v_profile.is_active then
        raise exception 'Active profile required';
    end if;

    -- If role or scope changed while offline, invalidate p_since to fetch full authorized snapshot for new role/scope
    if p_known_role_version is not null and (v_profile.role_version + v_profile.scope_version) <> p_known_role_version then
        v_effective_since := null;
    end if;

    v_auth_ctx := public.get_my_authorization_context();
    v_summary := public.get_scoped_dashboard_summary();

    select coalesce(jsonb_agg(to_jsonb(t) order by t.updated_at desc), '[]'::jsonb)
    into v_tasks
    from public.tasks t
    where public.can_view_task(t.id)
      and (v_effective_since is null or t.updated_at >= v_effective_since)
    limit 200;

    select coalesce(jsonb_agg(to_jsonb(si) order by si.updated_at desc), '[]'::jsonb)
    into v_incidents
    from public.system_incidents si
    where public.can_view_incident(si.id)
      and (v_effective_since is null or si.updated_at >= v_effective_since)
    limit 200;

    select coalesce(jsonb_agg(to_jsonb(n) order by n.created_at desc), '[]'::jsonb)
    into v_notifications
    from public.notifications n
    where n.user_id = v_caller
      and (v_effective_since is null or n.created_at >= v_effective_since)
    limit 100;

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
        'server_now', now()
    );
end;
$$;

revoke all on function public.delta_sync_pull(timestamptz, bigint) from public;
grant execute on function public.delta_sync_pull(timestamptz, bigint) to authenticated;

-- 18. PER-DEVICE PUSH TOKEN REGISTRATION & LOGOUT CLEANUP
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
    v_caller uuid := auth.uid();
begin
    if v_caller is null then
        raise exception 'Authentication required';
    end if;

    -- Deactivate same token if previously associated with another user on shared phone
    update public.device_tokens
    set is_active = false
    where token = p_token and user_id <> v_caller;

    insert into public.device_tokens(user_id, device_id, token, platform, device_name, app_version, is_active, last_seen_at)
    values (v_caller, p_device_id, p_token, coalesce(nullif(p_platform, ''), 'android'), p_device_name, p_app_version, true, now())
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

create or replace function public.deactivate_device_token(p_device_id text)
returns jsonb
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_caller uuid := auth.uid();
begin
    if v_caller is null then
        return jsonb_build_object('success', false);
    end if;

    -- Only deactivates THIS device; Phone 2 stays untouched
    update public.device_tokens
    set is_active = false, last_seen_at = now()
    where user_id = v_caller and device_id = p_device_id;

    return jsonb_build_object('success', true, 'device_id', p_device_id);
end;
$$;

revoke all on function public.deactivate_device_token(text) from public;
grant execute on function public.deactivate_device_token(text) to authenticated;

create or replace function public.mark_notification_read(p_notification_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_caller uuid := auth.uid();
begin
    if v_caller is null then
        raise exception 'Authentication required';
    end if;

    update public.notifications
    set is_read = true, read_at = coalesce(read_at, now())
    where id = p_notification_id and user_id = v_caller;

    return jsonb_build_object('success', true, 'id', p_notification_id);
end;
$$;

revoke all on function public.mark_notification_read(uuid) from public;
grant execute on function public.mark_notification_read(uuid) to authenticated;

-- 19. SAFE RLS FOR NEW TABLES & AUDIT/SYNC TABLES (NO UNSAFE USING (true) MUTATIONS)
alter table public.org_zones enable row level security;
alter table public.org_states enable row level security;
alter table public.org_cities enable row level security;
alter table public.org_centres enable row level security;
alter table public.user_roles enable row level security;
alter table public.admin_audit_log enable row level security;
alter table public.user_sync_state enable row level security;

drop policy if exists org_zones_read on public.org_zones;
create policy org_zones_read on public.org_zones for select to authenticated using (public.is_active_user());
drop policy if exists org_states_read on public.org_states;
create policy org_states_read on public.org_states for select to authenticated using (public.is_active_user());
drop policy if exists org_cities_read on public.org_cities;
create policy org_cities_read on public.org_cities for select to authenticated using (public.is_active_user());
drop policy if exists org_centres_read on public.org_centres;
create policy org_centres_read on public.org_centres for select to authenticated using (public.is_active_user());

drop policy if exists user_roles_select on public.user_roles;
create policy user_roles_select on public.user_roles for select to authenticated
using (user_id = (select auth.uid()) or public.is_super_admin());

drop policy if exists user_roles_admin_write on public.user_roles;
create policy user_roles_admin_write on public.user_roles for all to authenticated
using (public.is_super_admin())
with check (public.is_super_admin());

drop policy if exists admin_audit_select on public.admin_audit_log;
create policy admin_audit_select on public.admin_audit_log for select to authenticated
using (public.is_super_admin());

drop policy if exists user_sync_state_self on public.user_sync_state;
create policy user_sync_state_self on public.user_sync_state for all to authenticated
using (user_id = (select auth.uid()))
with check (user_id = (select auth.uid()));

-- 20. ENABLE REALTIME PUBLICATION SAFELY
do $$
begin
    if exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
        begin alter publication supabase_realtime add table public.tasks; exception when duplicate_object then null; end;
        begin alter publication supabase_realtime add table public.system_incidents; exception when duplicate_object then null; end;
        begin alter publication supabase_realtime add table public.notifications; exception when duplicate_object then null; end;
        begin alter publication supabase_realtime add table public.user_roles; exception when duplicate_object then null; end;
        begin alter publication supabase_realtime add table public.profiles; exception when duplicate_object then null; end;
    end if;
end $$;

commit;

notify pgrst, 'reload schema';
