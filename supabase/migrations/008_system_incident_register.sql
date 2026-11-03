-- ============================================================
-- 008: SYSTEM INCIDENT REGISTER MODULE
-- Technical incident tracking at examination centres
-- ============================================================

create table if not exists public.system_incidents (
    id uuid primary key default gen_random_uuid(),
    submission_id text not null unique,
    centre_id uuid null references public.task_groups(id) on delete set null,
    exam_session_id uuid null references public.shifts(id) on delete set null,
    state_name text not null default '',
    district_name text not null default '',
    centre_code text not null default '',
    centre_name text not null default '',
    exam_date date not null default current_date,
    exam_name text not null default '',
    shift_name text not null default '',
    lan_name text not null default '',
    mac_address text not null,
    ip_address text not null,
    locked_system_number text not null,
    newly_allocated_system_number text null,
    candidate_shifted boolean not null default false,
    incident_time timestamptz not null default now(),
    reason text not null,
    it_manager_name text not null default '',
    created_by uuid not null references public.profiles(id) on delete cascade,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    updated_by uuid null references public.profiles(id) on delete set null,
    version bigint not null default 1,

    constraint system_number_format_check
        check (locked_system_number ~ '^C[0-9]{3}$'),

    constraint new_system_number_format_check
        check (newly_allocated_system_number is null or newly_allocated_system_number ~ '^C[0-9]{3}$'),

    constraint different_system_numbers_check
        check (newly_allocated_system_number is null or locked_system_number <> newly_allocated_system_number),

    constraint candidate_shifted_consistency_check
        check (
            (candidate_shifted = true and newly_allocated_system_number is not null) or
            (candidate_shifted = false and newly_allocated_system_number is null)
        ),

    constraint mac_address_format_check
        check (mac_address ~* '^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$')
);

-- Indexes for high performance querying and filtering
create index if not exists idx_system_incidents_centre on public.system_incidents(centre_id);
create index if not exists idx_system_incidents_centre_code on public.system_incidents(centre_code);
create index if not exists idx_system_incidents_exam_date on public.system_incidents(exam_date);
create index if not exists idx_system_incidents_exam_name on public.system_incidents(exam_name);
create index if not exists idx_system_incidents_shift on public.system_incidents(shift_name);
create index if not exists idx_system_incidents_lan on public.system_incidents(lan_name);
create index if not exists idx_system_incidents_locked_sys on public.system_incidents(locked_system_number);
create index if not exists idx_system_incidents_created_by on public.system_incidents(created_by);
create index if not exists idx_system_incidents_created_at on public.system_incidents(created_at desc);

-- Audit Table for Modifications
create table if not exists public.system_incident_audit (
    id uuid primary key default gen_random_uuid(),
    incident_id uuid not null references public.system_incidents(id) on delete cascade,
    changed_by uuid not null references public.profiles(id),
    changed_at timestamptz not null default now(),
    action text not null, -- 'INSERT', 'UPDATE', 'DELETE'
    previous_values jsonb null,
    new_values jsonb null
);

create index if not exists idx_system_incident_audit_incident on public.system_incident_audit(incident_id);
create index if not exists idx_system_incident_audit_changed_at on public.system_incident_audit(changed_at desc);

-- Row Level Security (RLS)
alter table public.system_incidents enable row level security;
alter table public.system_incident_audit enable row level security;

-- Policy: Select Incidents
create policy system_incidents_select_policy on public.system_incidents
    for select
    using (
        created_by = auth.uid()
        or exists (
            select 1 from public.profiles
            where id = auth.uid() and app_role = 'admin'
        )
        or (
            centre_id is not null and exists (
                select 1 from public.group_members
                where group_id = system_incidents.centre_id and user_id = auth.uid()
            )
        )
    );

-- Policy: Insert Incidents
create policy system_incidents_insert_policy on public.system_incidents
    for insert
    with check (
        created_by = auth.uid()
    );

-- Policy: Update Incidents
create policy system_incidents_update_policy on public.system_incidents
    for update
    using (
        created_by = auth.uid()
        or exists (
            select 1 from public.profiles
            where id = auth.uid() and app_role = 'admin'
        )
    )
    with check (
        created_by = auth.uid()
        or exists (
            select 1 from public.profiles
            where id = auth.uid() and app_role = 'admin'
        )
    );

-- Policy: Audit Select
create policy system_incident_audit_select_policy on public.system_incident_audit
    for select
    using (
        changed_by = auth.uid()
        or exists (
            select 1 from public.profiles
            where id = auth.uid() and app_role = 'admin'
        )
    );

-- Policy: Audit Insert
create policy system_incident_audit_insert_policy on public.system_incident_audit
    for insert
    with check (
        changed_by = auth.uid()
    );
