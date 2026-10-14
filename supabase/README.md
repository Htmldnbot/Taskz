# Taskz Supabase Backend Setup Guide (Phase 1)

This directory contains the database migration scripts and Edge Functions for the Taskz application.

---

## 1. Quick Setup Instructions

### Step 1: Run the Database Migration
1. Go to your **Supabase Dashboard** -> **SQL Editor**.
2. Click **New Query**.
3. Copy and paste the entire contents of `supabase/migrations/001_initial_auth.sql`.
4. Click **Run**.
   - This creates the `profiles` table, the `password_reset_requests` table, indexes, updated_at triggers, RLS policies, and configures the `profile-photos` storage bucket.

---

### Step 2: Deploy the Edge Function (`admin-reset-password`)
Privileged administrative actions (like resetting an employee's password on Supabase Auth) run strictly server-side using the `SUPABASE_SERVICE_ROLE_KEY` inside Supabase Edge Functions.

Using the Supabase CLI:
```bash
# Link to your Supabase project (first time only)
supabase link --project-ref your-project-id

# Deploy the admin password reset function
supabase functions deploy admin-reset-password --no-verify-jwt
```
*(JWT is verified internally by the function code via caller's Bearer token)*

The Edge Function automatically inherits `SUPABASE_URL`, `SUPABASE_ANON_KEY`, and `SUPABASE_SERVICE_ROLE_KEY` from your Supabase project environment.

---

### Step 3: Configure Android App Environment
1. In your project root, copy `.env.example` to `.env`:
   ```bash
   cp .env.example .env
   ```
2. Replace with your actual project credentials:
   ```env
   SUPABASE_URL=https://<your-project-id>.supabase.co
   SUPABASE_ANON_KEY=<your-anon-public-key>
   ```
3. In Google AI Studio, you can also add these under the **Secrets panel** for production cloud builds.
4. **NEVER** expose `SUPABASE_SERVICE_ROLE_KEY` to the Android client.

---

### Step 4: Create the Initial Admin Account Safely
To bootstrap the first administrator:
1. Open the Taskz Android app.
2. Tap **Sign Up**.
3. Fill in the details:
   - **Employee ID**: `ADMIN01`
   - **Full Name**: `System Administrator`
   - **Mobile**: `9876543210`
   - **Password**: `<Your Secure Password>`
4. Tap **Create Account**. This creates the initial account with default role `employee`.
5. Now grant `admin` privileges safely from the Supabase SQL Editor:
   ```sql
   UPDATE public.profiles
   SET app_role = 'admin'
   WHERE LOWER(TRIM(employee_id)) = 'admin01';
   ```
6. The user is now recognized as an Admin across the entire application! The app will immediately grant access to the Admin tab, User Management, and Password Reset resolution.

---

## 2. Architecture & Security Notes

- **Employee ID Deterministic Auth**: Employees only log in with their Employee ID and password. Under the hood, authentication maps deterministically to `${employee_id.toLowerCase().trim()}@taskz.internal`.
- **Zero-Knowledge Passwords**: Plaintext passwords are never stored in Room, DataStore, or PostgreSQL tables. All credentials pass directly through Supabase Auth.
- **Row Level Security**: Employees can only read/update their own profile information and cannot escalate their role.
- **Deactivated Accounts**: If `is_active` is toggled to `false` by an admin, the client immediately terminates session access upon the next synchronization or refresh with a clear explanatory dialog.
