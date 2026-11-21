package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.core.datastore.AppThemeMode
import com.example.core.datastore.NotificationPreferences
import com.example.core.datastore.UserPreferencesDataStore
import com.example.core.model.AppReleaseInfo
import com.example.core.model.AuthorizationContext
import com.example.core.model.OrgScope
import com.example.core.model.TaskzRole
import com.example.core.model.UserProfile
import com.example.core.model.VersionComparator
import com.example.core.network.SupabaseConfig
import com.example.ui.settings.ADMIN_SUPPORT_EMAIL
import com.example.ui.settings.ContactAdminScreen
import com.example.ui.settings.PrivacyPolicyScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SupportCategory
import com.example.ui.settings.buildSupportMailtoUri
import com.example.ui.settings.defaultSupportSubject
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppUpdateAndSettingsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var prefs: UserPreferencesDataStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs = UserPreferencesDataStore(context)
    }

    @Test
    fun versionComparator_handlesNumericVersionCodesAnd48HourThrottle() {
        val newerOptional = AppReleaseInfo(
            id = "rel-26",
            versionName = "1.0.26",
            versionCode = 26,
            downloadUrl = "https://github.com/example/taskz/releases/download/v1.0.26/Taskz-v1.0.26.apk",
            releaseNotes = "Bug fixes",
            isLatest = true,
            isActive = true
        )
        assertTrue(VersionComparator.isUpdateAvailable(installedVersionCode = 24, release = newerOptional))
        assertFalse(VersionComparator.isUpdateAvailable(installedVersionCode = 26, release = newerOptional))
        assertFalse(VersionComparator.isUpdateAvailable(installedVersionCode = 27, release = newerOptional))
        assertFalse(VersionComparator.isMandatoryUpdate(installedVersionCode = 24, release = newerOptional))

        val criticalUpdate = newerOptional.copy(isCritical = true)
        assertTrue(VersionComparator.isMandatoryUpdate(installedVersionCode = 24, release = criticalUpdate))

        val belowMinSupported = newerOptional.copy(minimumSupportedVersionCode = 25)
        assertTrue(VersionComparator.isMandatoryUpdate(installedVersionCode = 24, release = belowMinSupported))
        assertFalse(VersionComparator.isMandatoryUpdate(installedVersionCode = 25, release = belowMinSupported))

        // 48h check throttle
        val now = 200_000_000_000L
        val fortyEightHoursMs = 48L * 60L * 60L * 1000L
        assertEquals(fortyEightHoursMs, VersionComparator.DEFAULT_CHECK_INTERVAL_MS)
        assertTrue(VersionComparator.isCheckDue(lastCheckedAtMs = 0L, nowMs = now))
        assertFalse(VersionComparator.isCheckDue(lastCheckedAtMs = now - (fortyEightHoursMs - 60_000L), nowMs = now))
        assertTrue(VersionComparator.isCheckDue(lastCheckedAtMs = now - fortyEightHoursMs, nowMs = now))
    }

    @Test
    fun sanitizeDisplayEmail_rejectsSyntheticDomainsAndNormalizesRealEmails() {
        assertNull(SupabaseConfig.sanitizeDisplayEmail(null))
        assertNull(SupabaseConfig.sanitizeDisplayEmail(""))
        assertNull(SupabaseConfig.sanitizeDisplayEmail("10023@taskzapp.org"))
        assertNull(SupabaseConfig.sanitizeDisplayEmail("USER@TASKZAPP.COM"))
        assertNull(SupabaseConfig.sanitizeDisplayEmail("not-an-email"))
        assertEquals("munesh.y@tcs.com", SupabaseConfig.sanitizeDisplayEmail("  Munesh.Y@TCS.COM "))
    }

    @Test
    fun userPreferences_persistsThemeNotificationPrefsAndUpdateCache() = runTest {
        prefs.saveThemeMode(AppThemeMode.DARK)
        assertEquals(AppThemeMode.DARK, prefs.themeModeFlow.first())

        val updatedNotif = NotificationPreferences(
            taskAlertsEnabled = false,
            incidentAlertsEnabled = true,
            soundEnabled = false,
            vibrationEnabled = true
        )
        prefs.updateNotificationPreferences(
            taskAlertsEnabled = false,
            incidentAlertsEnabled = true,
            soundEnabled = false,
            vibrationEnabled = true
        )
        assertEquals(updatedNotif, prefs.notificationPreferencesFlow.first())

        val release = AppReleaseInfo(
            id = "rel-2",
            versionName = "1.0.2",
            versionCode = 2,
            downloadUrl = "https://github.com/example/taskz/releases/download/v1.0.2/Taskz-v1.0.2.apk",
            releaseNotes = "Performance release"
        )
        prefs.saveUpdateCheckResult(checkedAtMs = 1_700_000_000_000L, release = release)
        assertEquals(1_700_000_000_000L, prefs.lastUpdateCheckAtFlow.first())
        assertEquals(2, prefs.cachedLatestReleaseFlow.first()?.versionCode)

        prefs.setDismissedUpdateVersionCode(2)
        assertEquals(2, prefs.dismissedUpdateVersionCodeFlow.first())
    }

    @Test
    fun supportMailtoBuilder_targetsAdminEmailAndDiagnosticsToggle() {
        assertEquals("munesh.y@tcs.com", ADMIN_SUPPORT_EMAIL)
        val subj = defaultSupportSubject(SupportCategory.GENERAL_SUPPORT)
        assertTrue(subj.startsWith("Taskz Support - Version "))

        val withDiag = buildSupportMailtoUri(
            category = SupportCategory.BUG_REPORT,
            subject = subj,
            message = "Sync stuck",
            includeDiagnostics = true,
            diagnosticsText = "App Version: 1.0.0\nDevice: Pixel"
        )
        assertEquals("mailto", withDiag.scheme)
        assertEquals("munesh.y@tcs.com", withDiag.schemeSpecificPart.substringBefore('?'))
        assertTrue(withDiag.toString().contains("Sync%20stuck"))
        assertTrue(withDiag.toString().contains("Device%3A%20Pixel"))

        val withoutDiag = buildSupportMailtoUri(
            category = SupportCategory.GENERAL_SUPPORT,
            subject = subj,
            message = "Help needed",
            includeDiagnostics = false,
            diagnosticsText = "SECRET_SHOULD_NOT_APPEAR"
        )
        assertFalse(withoutDiag.toString().contains("SECRET_SHOULD_NOT_APPEAR"))
    }

    @Test
    fun settingsContactAndPrivacyScreens_renderInLightAndDarkThemes() {
        val sampleProfile = UserProfile(
            id = "user-123",
            employeeId = "EMP104",
            fullName = "Test Operator",
            email = "operator@tcs.com",
            mobile = "+919999999999",
            activeRoleCode = "IT_MANAGER",
            assignedRoleCodes = listOf("IT_MANAGER"),
            isActive = true
        )
        val sampleContext = AuthorizationContext(
            userId = "user-123",
            activeRole = TaskzRole.IT_MANAGER,
            scope = OrgScope(centreCode = "141", assignedLan = "LAN 1")
        )
        val updateManager = TaskzApplication.instance.appUpdateManager

        // Render Settings in Light theme
        composeRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                SettingsScreen(
                    profile = sampleProfile,
                    authContext = sampleContext,
                    userPrefs = prefs,
                    appUpdateManager = updateManager,
                    onOpenProfile = {},
                    onOpenAlarmReliability = {},
                    onOpenUpdates = {},
                    onOpenContactAdmin = {},
                    onOpenPrivacyPolicy = {},
                    onOpenDisclaimer = {},
                    onOpenTermsOfUse = {},
                    onOpenDataAndPermissions = {},
                    onOpenLicenses = {},
                    onOpenAppInfo = {},
                    onSignOut = {},
                    onBack = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_updates_row").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("settings_contact_admin_row").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun contactAdminScreen_rendersInDarkThemeWithAdminEmail() {
        // Render Contact Admin in Dark theme
        composeRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                ContactAdminScreen(
                    initialCategory = SupportCategory.APP_UPDATE,
                    employeeId = "EMP104",
                    userEmail = "operator@tcs.com",
                    roleName = "IT Manager",
                    onBack = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("contact_admin_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("copy_admin_email_button").assertIsDisplayed()
        composeRule.onNodeWithText("munesh.y@tcs.com").assertIsDisplayed()
    }

    @Test
    fun privacyPolicyScreen_rendersTruthfulDataSectionsInDarkTheme() {
        composeRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                PrivacyPolicyScreen(
                    onContactAdmin = {},
                    onBack = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("privacy_policy_screen").assertIsDisplayed()
        composeRule.onNodeWithText("1. Account & Identity Information").assertIsDisplayed()
    }
}
