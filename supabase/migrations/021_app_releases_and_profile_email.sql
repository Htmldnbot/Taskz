-- ============================================================================
-- Taskz Migration 021: App Releases Governance & Real Profile Email Sync
-- Forward-only, idempotent PostgreSQL migration.
-- ============================================================================

begin;

-- ----------------------------------------------------------------------------
-- 1. PROFILE EMAIL SANITIZATION, BACKFILL & AUTO-SYNC TRIGGER
-- ----------------------------------------------------------------------------

alter table public.profiles
    add column if not exists email text;

create or replace function public.clean_real_email(p_email text)
returns text
language sql
immutable
set search_path = ''
as $$
    select case
        when p_email is null or btrim(p_email) = '' then null
        when lower(btrim(p_email)) ~ '@taskzapp\.(org|com)$' then null
        else lower(btrim(p_email))
    end;
$$;

create or replace function public.handle_new_auth_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_employee text;
    v_name text;
    v_mobile text;
    v_email text;
begin
    v_email := public.clean_real_email(new.email);

    v_employee := public.normalize_employee_id(
        coalesce(
            new.raw_user_meta_data ->> 'employee_id',
            new.raw_user_meta_data ->> 'employeeId',
            ''
        )
    );

    -- Google OAuth / ID-token signups and bare auth accounts without employee_id yet:
    -- Let the user complete onboarding via complete_my_profile().
    if v_employee = '' then
        return new;
    end if;

    v_name := trim(
        coalesce(
            new.raw_user_meta_data ->> 'full_name',
            new.raw_user_meta_data ->> 'fullName',
            new.raw_user_meta_data ->> 'name',
            ''
        )
    );
    if char_length(v_name) < 2 then
        raise exception 'Full name must be at least 2 characters';
    end if;

    v_mobile := public.normalize_mobile(
        coalesce(
            new.raw_user_meta_data ->> 'mobile',
            new.raw_user_meta_data ->> 'phone',
            ''
        )
    );
    if v_mobile !~ '^\+[1-9][0-9]{7,14}$' then
        raise exception 'Invalid mobile number format. Include country code (e.g. +919876543210).';
    end if;

    if exists (
        select 1
        from public.profiles p
        where lower(p.employee_id) = lower(v_employee)
          and p.id <> new.id
    ) then
        raise exception 'Employee ID % is already registered to another account', v_employee;
    end if;

    insert into public.profiles (
        id,
        employee_id,
        full_name,
        mobile,
        email,
        app_role,
        is_active
    )
    values (
        new.id,
        v_employee,
        v_name,
        v_mobile,
        v_email,
        'employee',
        true
    )
    on conflict (id) do update
    set
        employee_id = excluded.employee_id,
        full_name = excluded.full_name,
        mobile = excluded.mobile,
        email = coalesce(public.clean_real_email(public.profiles.email), excluded.email),
        updated_at = now();

    return new;
end;
$$;

create or replace function public.complete_my_profile(
    p_employee_id text,
    p_full_name text,
    p_mobile text
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_user uuid := auth.uid();
    v_emp text := public.normalize_employee_id(p_employee_id);
    v_name text := trim(coalesce(p_full_name, ''));
    v_mobile text := public.normalize_mobile(p_mobile);
    v_auth_email text;
    v_profile public.profiles%rowtype;
begin
    if v_user is null then
        raise exception 'Authentication required';
    end if;

    if v_emp !~ '^[A-Z0-9._-]{2,40}$' then
        raise exception 'Employee ID must be 2 to 40 letters, numbers, dots, underscores, or hyphens';
    end if;

    if char_length(v_name) < 2 or char_length(v_name) > 120 then
        raise exception 'Full name must be between 2 and 120 characters';
    end if;

    if v_mobile !~ '^\+[1-9][0-9]{7,14}$' then
        raise exception 'Invalid mobile number. Enter a valid number with country code (e.g. +919876543210)';
    end if;

    if exists (
        select 1
        from public.profiles p
        where lower(p.employee_id) = lower(v_emp)
          and p.id <> v_user
    ) then
        raise exception 'Employee ID % is already linked to another account', v_emp;
    end if;

    select public.clean_real_email(u.email)
    into v_auth_email
    from auth.users u
    where u.id = v_user;

    insert into public.profiles (
        id,
        employee_id,
        full_name,
        mobile,
        email,
        app_role,
        is_active
    )
    values (
        v_user,
        v_emp,
        v_name,
        v_mobile,
        v_auth_email,
        'employee',
        true
    )
    on conflict (id) do update
    set
        employee_id = case
            when public.profiles.employee_id is null or public.profiles.employee_id = '' then excluded.employee_id
            else public.profiles.employee_id
        end,
        full_name = excluded.full_name,
        mobile = excluded.mobile,
        email = coalesce(public.clean_real_email(public.profiles.email), excluded.email),
        updated_at = now()
    returning * into v_profile;

    return to_jsonb(v_profile);
end;
$$;

create or replace function public.sync_profile_email_from_auth()
returns trigger
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_clean text := public.clean_real_email(new.email);
begin
    if v_clean is not null then
        perform set_config('taskz.bypass_rbac_guard', '1', true);
        update public.profiles
        set
            email = v_clean,
            updated_at = now()
        where id = new.id
          and (email is distinct from v_clean);
    end if;
    return new;
end;
$$;

drop trigger if exists trg_sync_profile_email_from_auth on auth.users;
create trigger trg_sync_profile_email_from_auth
    after insert or update of email on auth.users
    for each row
    execute function public.sync_profile_email_from_auth();

-- Backfill real emails on existing profiles without overwriting existing valid emails
select set_config('taskz.bypass_rbac_guard', '1', true);
update public.profiles p
set
    email = public.clean_real_email(u.email),
    updated_at = now()
from auth.users u
where p.id = u.id
  and public.clean_real_email(u.email) is not null
  and (
      p.email is null
      or btrim(p.email) = ''
      or public.clean_real_email(p.email) is null
  );

-- ----------------------------------------------------------------------------
-- 2. APP RELEASES TABLE, RLS POLICIES & ADMIN MANAGEMENT RPCs
-- ----------------------------------------------------------------------------

create table if not exists public.app_releases (
    id uuid primary key default gen_random_uuid(),
    version_name text not null check (char_length(btrim(version_name)) between 1 and 40),
    version_code integer not null unique check (version_code > 0),
    download_url text not null check (download_url ~* '^https://'),
    release_notes text not null default '',
    release_date timestamptz not null default now(),
    minimum_supported_version text,
    minimum_supported_version_code integer check (minimum_supported_version_code is null or minimum_supported_version_code > 0),
    sha256_checksum text check (sha256_checksum is null or sha256_checksum ~* '^[0-9a-f]{64}$'),
    is_latest boolean not null default false,
    is_critical boolean not null default false,
    is_active boolean not null default true,
    force_update boolean not null default false,
    created_by uuid references auth.users(id) on delete set null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_app_releases_active_version
    on public.app_releases (is_active, version_code desc);

create unique index if not exists idx_app_releases_single_active_latest
    on public.app_releases (is_latest)
    where is_latest = true and is_active = true;

alter table public.app_releases enable row level security;

drop policy if exists app_releases_select_public on public.app_releases;
create policy app_releases_select_public
on public.app_releases
for select
to anon, authenticated
using (is_active = true or public.is_super_admin());

drop policy if exists app_releases_super_admin_insert on public.app_releases;
create policy app_releases_super_admin_insert
on public.app_releases
for insert
to authenticated
with check (public.is_super_admin());

drop policy if exists app_releases_super_admin_update on public.app_releases;
create policy app_releases_super_admin_update
on public.app_releases
for update
to authenticated
using (public.is_super_admin())
with check (public.is_super_admin());

drop policy if exists app_releases_super_admin_delete on public.app_releases;
create policy app_releases_super_admin_delete
on public.app_releases
for delete
to authenticated
using (public.is_super_admin());

create or replace function public.get_latest_app_release()
returns jsonb
language sql
stable
security definer
set search_path = public, pg_temp
as $$
    select to_jsonb(r)
    from public.app_releases r
    where r.is_active = true
    order by r.is_latest desc, r.version_code desc, r.release_date desc
    limit 1;
$$;

revoke all on function public.get_latest_app_release() from public;
grant execute on function public.get_latest_app_release() to anon, authenticated;

create or replace function public.admin_list_app_releases()
returns jsonb
language plpgsql
stable
security definer
set search_path = public, pg_temp
as $$
begin
    if not public.is_super_admin() then
        raise exception 'Forbidden: only Super Admin can manage app releases';
    end if;

    return coalesce(
        (
            select jsonb_agg(to_jsonb(r) order by r.version_code desc, r.created_at desc)
            from public.app_releases r
        ),
        '[]'::jsonb
    );
end;
$$;

revoke all on function public.admin_list_app_releases() from public;
grant execute on function public.admin_list_app_releases() to authenticated;

create or replace function public.admin_upsert_app_release(
    p_id uuid default null,
    p_version_name text default '',
    p_version_code integer default 0,
    p_download_url text default '',
    p_release_notes text default '',
    p_minimum_supported_version text default null,
    p_minimum_supported_version_code integer default null,
    p_sha256_checksum text default null,
    p_is_latest boolean default true,
    p_is_critical boolean default false,
    p_is_active boolean default true,
    p_force_update boolean default false,
    p_release_date timestamptz default null
)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_user uuid := auth.uid();
    v_name text := btrim(coalesce(p_version_name, ''));
    v_url text := btrim(coalesce(p_download_url, ''));
    v_notes text := coalesce(p_release_notes, '');
    v_min_ver text := nullif(btrim(coalesce(p_minimum_supported_version, '')), '');
    v_sha text := nullif(lower(btrim(coalesce(p_sha256_checksum, ''))), '');
    v_effective_latest boolean := coalesce(p_is_latest, false) and coalesce(p_is_active, true);
    v_row public.app_releases%rowtype;
begin
    if not public.is_super_admin() then
        raise exception 'Forbidden: only Super Admin can create or update app releases';
    end if;

    if v_name = '' or char_length(v_name) > 40 then
        raise exception 'Version name must be 1 to 40 characters (e.g. 1.0.25)';
    end if;

    if p_version_code is null or p_version_code <= 0 then
        raise exception 'Version code must be a positive integer';
    end if;

    if v_url !~* '^https://' then
        raise exception 'Download URL must use HTTPS (e.g. GitHub Releases asset URL)';
    end if;

    if v_sha is not null and v_sha !~ '^[0-9a-f]{64}$' then
        raise exception 'SHA-256 checksum must be a valid 64-character hex digest';
    end if;

    if v_effective_latest then
        update public.app_releases
        set is_latest = false,
            updated_at = now()
        where is_latest = true
          and (p_id is null or id <> p_id);
    end if;

    if p_id is not null then
        update public.app_releases
        set
            version_name = v_name,
            version_code = p_version_code,
            download_url = v_url,
            release_notes = v_notes,
            release_date = coalesce(p_release_date, release_date, now()),
            minimum_supported_version = v_min_ver,
            minimum_supported_version_code = p_minimum_supported_version_code,
            sha256_checksum = v_sha,
            is_latest = v_effective_latest,
            is_critical = coalesce(p_is_critical, false),
            is_active = coalesce(p_is_active, true),
            force_update = coalesce(p_force_update, false),
            updated_at = now()
        where id = p_id
        returning * into v_row;

        if not found then
            raise exception 'Release record not found';
        end if;
    else
        insert into public.app_releases (
            version_name,
            version_code,
            download_url,
            release_notes,
            release_date,
            minimum_supported_version,
            minimum_supported_version_code,
            sha256_checksum,
            is_latest,
            is_critical,
            is_active,
            force_update,
            created_by
        )
        values (
            v_name,
            p_version_code,
            v_url,
            v_notes,
            coalesce(p_release_date, now()),
            v_min_ver,
            p_minimum_supported_version_code,
            v_sha,
            v_effective_latest,
            coalesce(p_is_critical, false),
            coalesce(p_is_active, true),
            coalesce(p_force_update, false),
            v_user
        )
        on conflict (version_code) do update
        set
            version_name = excluded.version_name,
            download_url = excluded.download_url,
            release_notes = excluded.release_notes,
            release_date = excluded.release_date,
            minimum_supported_version = excluded.minimum_supported_version,
            minimum_supported_version_code = excluded.minimum_supported_version_code,
            sha256_checksum = excluded.sha256_checksum,
            is_latest = excluded.is_latest,
            is_critical = excluded.is_critical,
            is_active = excluded.is_active,
            force_update = excluded.force_update,
            updated_at = now()
        returning * into v_row;
    end if;

    if to_regclass('public.admin_audit_logs') is not null then
        insert into public.admin_audit_logs (admin_user_id, action, entity_type, metadata)
        values (
            v_user,
            'UPSERT_APP_RELEASE',
            'app_release',
            jsonb_build_object(
                'release_id', v_row.id,
                'version_name', v_row.version_name,
                'version_code', v_row.version_code,
                'is_latest', v_row.is_latest,
                'is_critical', v_row.is_critical,
                'force_update', v_row.force_update,
                'is_active', v_row.is_active
            )
        );
    end if;

    return to_jsonb(v_row);
end;
$$;

revoke all on function public.admin_upsert_app_release(uuid, text, integer, text, text, text, integer, text, boolean, boolean, boolean, boolean, timestamptz) from public;
grant execute on function public.admin_upsert_app_release(uuid, text, integer, text, text, text, integer, text, boolean, boolean, boolean, boolean, timestamptz) to authenticated;

commit;
