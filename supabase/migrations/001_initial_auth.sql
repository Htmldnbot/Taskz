-- ============================================================================
-- Taskz Phase 1: Database Migration 001_initial_auth.sql
-- Purpose: Profiles table, password reset requests, storage bucket, RLS policies
-- ============================================================================

-- 1. Helper function for updating updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 2. Profiles Table
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    auth_user_id UUID NOT NULL UNIQUE REFERENCES auth.users(id) ON DELETE CASCADE,
    employee_id TEXT NOT NULL,
    full_name TEXT NOT NULL,
    mobile TEXT NOT NULL,
    profile_photo_url TEXT,
    app_role TEXT NOT NULL DEFAULT 'employee' CHECK (app_role IN ('employee', 'admin')),
    is_active BOOLEAN NOT NULL DEFAULT true,
    must_change_password BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Indexes for fast querying & uniqueness (case-insensitive for employee_id)
CREATE UNIQUE INDEX IF NOT EXISTS idx_profiles_employee_id_unique 
    ON public.profiles (LOWER(TRIM(employee_id)));

CREATE INDEX IF NOT EXISTS idx_profiles_auth_user_id 
    ON public.profiles (auth_user_id);

CREATE INDEX IF NOT EXISTS idx_profiles_app_role 
    ON public.profiles (app_role);

CREATE INDEX IF NOT EXISTS idx_profiles_is_active 
    ON public.profiles (is_active);

-- Trigger for automatic updated_at refresh
DROP TRIGGER IF EXISTS trigger_profiles_updated_at ON public.profiles;
CREATE TRIGGER trigger_profiles_updated_at
    BEFORE UPDATE ON public.profiles
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Enable Row Level Security
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

-- Helper function to check if requesting user is active admin
CREATE OR REPLACE FUNCTION public.is_admin()
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.profiles
        WHERE auth_user_id = auth.uid()
          AND app_role = 'admin'
          AND is_active = true
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Profiles Policies
-- 1) Any user can read their own profile
CREATE POLICY "Users can read own profile"
    ON public.profiles
    FOR SELECT
    USING (auth.uid() = auth_user_id OR public.is_admin());

-- 2) Users can update safe fields of their own profile
CREATE POLICY "Users can update own safe fields"
    ON public.profiles
    FOR UPDATE
    USING (auth.uid() = auth_user_id)
    WITH CHECK (
        auth.uid() = auth_user_id
        AND app_role = (SELECT p.app_role FROM public.profiles p WHERE p.auth_user_id = auth.uid())
        AND is_active = (SELECT p.is_active FROM public.profiles p WHERE p.auth_user_id = auth.uid())
        AND LOWER(TRIM(employee_id)) = (SELECT LOWER(TRIM(p.employee_id)) FROM public.profiles p WHERE p.auth_user_id = auth.uid())
    );

-- 3) Admin can update any profile (e.g., toggle is_active, change app_role)
CREATE POLICY "Admins can update all profiles"
    ON public.profiles
    FOR UPDATE
    USING (public.is_admin())
    WITH CHECK (public.is_admin());

-- 4) Profile creation during signup
CREATE POLICY "Users can insert own profile"
    ON public.profiles
    FOR INSERT
    WITH CHECK (
        auth.uid() = auth_user_id
        AND app_role = 'employee'
    );

-- ============================================================================
-- 3. Password Reset Requests Table
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.password_reset_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id TEXT NOT NULL,
    mobile TEXT NOT NULL,
    message TEXT,
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'resolved', 'rejected')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ,
    resolved_by UUID REFERENCES auth.users(id)
);

CREATE INDEX IF NOT EXISTS idx_password_reset_requests_status 
    ON public.password_reset_requests (status);

CREATE INDEX IF NOT EXISTS idx_password_reset_requests_employee_id 
    ON public.password_reset_requests (LOWER(TRIM(employee_id)));

-- Enable RLS
ALTER TABLE public.password_reset_requests ENABLE ROW LEVEL SECURITY;

-- Anyone (even unauthenticated) can insert a password reset request
CREATE POLICY "Anyone can create password reset request"
    ON public.password_reset_requests
    FOR INSERT
    WITH CHECK (true);

-- Only admins can view requests
CREATE POLICY "Admins can view password reset requests"
    ON public.password_reset_requests
    FOR SELECT
    USING (public.is_admin());

-- Only admins can update requests
CREATE POLICY "Admins can update password reset requests"
    ON public.password_reset_requests
    FOR UPDATE
    USING (public.is_admin())
    WITH CHECK (public.is_admin());

-- ============================================================================
-- 4. Storage Bucket Setup (profile-photos)
-- ============================================================================
INSERT INTO storage.buckets (id, name, public)
VALUES ('profile-photos', 'profile-photos', true)
ON CONFLICT (id) DO NOTHING;

-- Storage RLS: Public read, Authenticated write to own folder
CREATE POLICY "Public profile photo read"
    ON storage.objects FOR SELECT
    USING (bucket_id = 'profile-photos');

CREATE POLICY "Authenticated user upload profile photo"
    ON storage.objects FOR INSERT
    WITH CHECK (
        bucket_id = 'profile-photos'
        AND auth.role() = 'authenticated'
    );

CREATE POLICY "User update own profile photo"
    ON storage.objects FOR UPDATE
    USING (
        bucket_id = 'profile-photos'
        AND auth.uid()::text = (storage.foldername(name))[1]
    );

-- ============================================================================
-- End of Migration 001
-- ============================================================================
