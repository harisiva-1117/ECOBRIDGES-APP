package com.example.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.RoleType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Demo login must open the correct role dashboard and must never allow a
 * client-selected Admin role. Runs against the real debug BuildConfig.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DemoRoleAccessTest {

    private fun authService(): SupabaseAuthService {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return SupabaseAuthService.getInstance(context)
    }

    private fun loginDemo(phone: String, role: RoleType): AuthResult = runBlocking {
        val service = authService()
        service.signOut()
        service.rememberLocalRegistration(phone, role)
        val sent = service.generateAndSendOtp(phoneNumber = phone, targetRole = role)
        assertTrue("demo OTP should be issued for $role", sent.isSuccess)
        val result = service.verifyOtpAndLogin(
            phoneNumber = phone,
            enteredOtp = DemoAuthConfig.otpCode,
            targetRole = role
        )
        service.signOut()
        result
    }

    @org.junit.Before
    fun clearRegistrations() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("registered_user_roles", Context.MODE_PRIVATE)
            .edit().clear().commit()
        authService().signOut()
    }

    @org.junit.After
    fun resetAuth() {
        clearRegistrations()
    }

    @Test
    fun `a recycler demo account opens only the recycler profile`() {
        val result = loginDemo("9876543211", RoleType.FORMAL_RECYCLER)
        assertTrue(result.isSuccess)
        val profile = result.userProfile
        assertNotNull(profile)
        assertEquals(RoleType.FORMAL_RECYCLER, profile?.role)
        assertTrue(profile?.isDemoAccount == true)
        assertTrue(profile?.statutoryIdentifier?.startsWith("DEMO-") == true)
        assertFalse(profile?.statutoryIdentifier.orEmpty().startsWith("CPCB"))
    }

    @Test
    fun `an explicitly configured admin demo account opens the admin portal`() {
        val result = loginDemo("9876543212", RoleType.GOVERNMENT_ADMIN)
        assertTrue(result.isSuccess)
        val profile = result.userProfile
        assertEquals(RoleType.GOVERNMENT_ADMIN, profile?.role)
        assertTrue(profile?.isDemoAccount == true)
        assertTrue(profile?.statutoryIdentifier?.startsWith("DEMO-") == true)
        assertTrue(profile?.statutoryIdentifier?.contains("NOT-AUTHORISED") == true)
    }

    @Test
    fun `an arbitrary phone can never request an admin demo session`() = runBlocking {
        val service = authService()
        service.signOut()
        val phone = "9111122233"
        service.rememberLocalRegistration(phone, RoleType.GOVERNMENT_ADMIN)
        val sent = service.generateAndSendOtp(
            phoneNumber = phone,
            targetRole = RoleType.GOVERNMENT_ADMIN
        )
        assertFalse(sent.isSuccess)
        service.signOut()

        // Even with a local registration, an arbitrary number cannot become an
        // admin: the role must be listed in the build's DEMO_ACCOUNTS.
        service.rememberLocalRegistration("9222233344", RoleType.GOVERNMENT_ADMIN)
        val unregistered = service.generateAndSendOtp(
            phoneNumber = "9222233344",
            targetRole = RoleType.GOVERNMENT_ADMIN
        )
        assertFalse(unregistered.isSuccess)
        service.signOut()
    }

    @Test
    fun `a wrong demo code fails and does not create a session`() = runBlocking {
        val service = authService()
        service.signOut()
        val phone = "9876543210"
        service.rememberLocalRegistration(phone, RoleType.INFORMAL_COLLECTOR)
        assertTrue(
            service.generateAndSendOtp(phoneNumber = phone, targetRole = RoleType.INFORMAL_COLLECTOR)
                .isSuccess
        )
        val wrong = service.verifyOtpAndLogin(
            phoneNumber = phone,
            enteredOtp = "000000",
            targetRole = RoleType.INFORMAL_COLLECTOR
        )
        assertFalse(wrong.isSuccess)
        assertEquals(null, service.authenticatedUser.value)
        service.signOut()
    }
}
