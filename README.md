# Taskz — Enterprise Workforce Task & Operations Platform

> **Phase 1: Project Foundation, Authentication & Governance Architecture**

Taskz is a native Android application built for internal enterprise workforce operations, task assignment, join approvals, offline execution, and real-time synchronization.

---

## Architecture Overview

Taskz is structured following Clean Architecture and MVVM (Model-View-ViewModel) with strict layer separation:

- **UI Layer**: Jetpack Compose + Material 3, Single-Activity architecture with Navigation Compose. Responsive design adhering to 8.dp grids and accessible touch targets.
- **Domain Layer**: Reactive Repositories (`AuthRepository`, `ProfileRepository`, `SyncRepository`, `AdminRepository`) exposing Kotlin `Flow` and `StateFlow`.
- **Data Layer**:
  - **Local Persistence (Room)**: `TaskzDatabase` providing cached user profile, persistent sync metadata (`SyncMetadataEntity`), and an offline outbox queue (`OutboxEventEntity`).
  - **Preferences**: Jetpack DataStore (`UserPreferencesDataStore`) managing encrypted access tokens and session states.
  - **Remote Cloud (Supabase)**:
    - Deterministic Employee-ID based Supabase Authentication.
    - PostgreSQL database with Row Level Security (RLS) enforcement.
    - Supabase Storage (`profile-photos` bucket) for compressed avatar uploads.
    - Supabase Edge Functions (`admin-reset-password`) for privileged administrative actions.

---

## Project Structure

```
├── app/
│   ├── src/main/java/com/example/
│   │   ├── TaskzApplication.kt            # DI container & application lifecycle
│   │   ├── MainActivity.kt                # Single-activity Compose NavHost
│   │   ├── core/
│   │   │   ├── datastore/                 # DataStore session & configuration
│   │   │   ├── model/                     # UserProfile, SyncMetadata, ResetRequest
│   │   │   └── network/                   # SupabaseConfig, NetworkMonitor
│   │   ├── data/
│   │   │   ├── local/                     # Room DB, ProfileDao, SyncMetadataDao, OutboxDao
│   │   │   └── remote/                    # SupabaseClient (Auth, REST, Storage, Functions)
│   │   ├── domain/
│   │   │   └── repository/                # AuthRepository, ProfileRepository, SyncRepository, AdminRepository
│   │   └── ui/
│   │       ├── admin/                     # AdminScreen & AdminViewModel (User Governance & Reset)
│   │       ├── auth/                      # AuthScreen & AuthViewModel (Login, Signup, Contact Admin)
│   │       ├── common/                    # Custom components, SyncStatusCard, Dialogs
│   │       ├── home/                      # HomeScreen & HomeViewModel (Sync card & module tiles)
│   │       ├── main/                      # MainShell with responsive M3 NavigationBar
│   │       ├── profile/                   # ProfileScreen & ProfileViewModel
│   │       ├── splash/                    # SplashScreen (Session restoration)
│   │       ├── tasks/                     # TasksPlaceholderScreen (Phase 2 foundation)
│   │       └── theme/                     # Material 3 Navy & Electric Cyan palette
├── supabase/
│   ├── migrations/
│   │   └── 001_initial_auth.sql           # Database schema, indexes, RLS policies, storage bucket
│   ├── functions/
│   │   └── admin-reset-password/          # Deno TypeScript Edge Function for privileged resets
│   └── README.md                          # Supabase deployment instructions
├── .env.example                           # Template for backend credentials
└── README.md
```

---

## Configuration & Setup

### 1. Database & Backend Setup
Follow the step-by-step guide in [supabase/README.md](supabase/README.md):
1. Execute `supabase/migrations/001_initial_auth.sql` in your Supabase SQL Editor.
2. Deploy the `admin-reset-password` Edge Function using the Supabase CLI.

### 2. Configure Credentials
1. Copy `.env.example` to `.env`:
   ```bash
   cp .env.example .env
   ```
2. Enter your Supabase credentials:
   ```env
   SUPABASE_URL=https://your-project-id.supabase.co
   SUPABASE_ANON_KEY=your-anon-client-key
   ```
*(You can also configure or test these directly in the app at runtime using the settings gear icon on the authentication screen)*.

### 3. Bootstrap the First Admin Account
1. Open Taskz on your Android device or emulator.
2. Sign up with your details (e.g., Employee ID: `ADMIN01`).
3. Run the following SQL query in the Supabase SQL Editor:
   ```sql
   UPDATE public.profiles
   SET app_role = 'admin'
   WHERE LOWER(TRIM(employee_id)) = 'admin01';
   ```
4. You now have full access to the **Admin Console** tab with Employee Governance and Password Reset capabilities!

---

## How to Build and Run

```bash
# Build debug APK
gradle assembleDebug

# Run Unit Tests
gradle :app:testDebugUnitTest
```

---

## Phase 1 Acceptance Test Summary

- **Test 1**: Fresh install launches Splash screen, restores session if valid, otherwise displays Login/Signup without briefly exposing Home.
- **Test 2**: Employee registers with Employee ID, Name, Mobile, Password, and optional Photo. Account and profile created in Supabase with local Room caching.
- **Test 3**: Duplicate Employee IDs are rejected with clean inline feedback.
- **Test 4**: Employees log in using Employee ID + Password without needing an email address.
- **Test 5**: Invalid credentials display a user-friendly error without crashing.
- **Test 6**: Session persists across restarts via DataStore token caching and background validation.
- **Test 7**: Profile updates (Full Name, Mobile, Photo) persist to Supabase; Employee ID remains locked.
- **Test 8**: Logout terminates session, clears sensitive local caches, and prevents Back navigation from reopening authenticated views.
- **Test 9**: "Contact Admin" allows employees to submit password reset requests.
- **Test 10 & 11**: Administrators can review pending requests and issue temporary passwords via the server-side Edge Function.
- **Test 12**: Deactivated accounts are blocked upon synchronization with clear guidance.
- **Test 13 & 14**: Offline operation displays appropriate status indicators; manual refresh never falsely updates "Last Successful Sync" if server requests fail.
