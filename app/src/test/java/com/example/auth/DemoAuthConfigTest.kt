package com.example.auth

import com.example.model.RoleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the demo-login configuration and the offline demo-code
 * checker. They prove the demo flow is opt-in, clearly bounded, and that Admin
 * can never be self-selected.
 */
class DemoAuthConfigTest {

    private fun settings(
        enabled: Boolean = true,
        allowRelease: Boolean = false,
        isDebug: Boolean = true,
        otp: String? = "123456",
        admin: Boolean = true,
        accounts: String? = "9876543210:collector,9876543211:recycler,9876543212:admin"
    ) = DemoAuthConfig.parse(
        enabled = enabled,
        allowRelease = allowRelease,
        isDebugBuild = isDebug,
        otpCodeRaw = otp,
        adminEnabled = admin,
        accountsRaw = accounts
    )

    @Test
    fun `demo login is enabled by default only in debug builds`() {
        assertTrue(settings(isDebug = true).available)
        assertFalse(settings(isDebug = false).available)
    }

    @Test
    fun `release build requires the explicit release opt-in`() {
        assertFalse(settings(enabled = true, allowRelease = false, isDebug = false).available)
        assertTrue(settings(enabled = true, allowRelease = true, isDebug = false).available)
        assertFalse(settings(enabled = false, allowRelease = true, isDebug = false).available)
    }

    @Test
    fun `demo code defaults to 123456 and can be configured`() {
        assertEquals("123456", settings(otp = null).otpCode)
        assertEquals("123456", settings(otp = "").otpCode)
        assertEquals("123456", settings(otp = "abc").otpCode)
        assertEquals("654321", settings(otp = "654321").otpCode)
    }

    @Test
    fun `configured demo accounts map phones to roles`() {
        val config = settings()
        assertEquals(RoleType.INFORMAL_COLLECTOR, config.configuredRoleFor("9876543210"))
        assertEquals(RoleType.FORMAL_RECYCLER, config.configuredRoleFor("+91 98765 43211"))
        assertEquals(RoleType.GOVERNMENT_ADMIN, config.configuredRoleFor("919876543212"))
        assertNull(config.configuredRoleFor("9999999999"))
        assertNull(config.configuredRoleFor(null))
    }

    @Test
    fun `admin demo requires the build flag and a configured admin phone`() {
        val enabled = settings(admin = true)
        assertTrue(enabled.canUseRole(RoleType.GOVERNMENT_ADMIN, "9876543212"))
        assertFalse(enabled.canUseRole(RoleType.GOVERNMENT_ADMIN, "9876543210"))
        assertFalse(enabled.canUseRole(RoleType.GOVERNMENT_ADMIN, "9999999999"))

        val disabled = settings(admin = false)
        assertFalse(disabled.canUseRole(RoleType.GOVERNMENT_ADMIN, "9876543212"))
        assertTrue(disabled.canUseRole(RoleType.INFORMAL_COLLECTOR, "9999999999"))
        assertTrue(disabled.canUseRole(RoleType.FORMAL_RECYCLER, "9999999999"))
    }

    @Test
    fun `demo code accepted only when it matches the configured or issued code`() {
        assertEquals(
            DemoOtpVerification.SUCCESS,
            DemoOtpVerifier.verify("123456", "123456", "123456")
        )
        assertEquals(
            DemoOtpVerification.SUCCESS,
            DemoOtpVerifier.verify("123456", null, " 123456 ")
        )
        assertEquals(
            DemoOtpVerification.MISMATCH,
            DemoOtpVerifier.verify("123456", "123456", "123457")
        )
        assertEquals(
            DemoOtpVerification.MALFORMED,
            DemoOtpVerifier.verify("123456", "123456", "12345")
        )
        assertEquals(
            DemoOtpVerification.NO_ACTIVE_CHALLENGE,
            DemoOtpVerifier.verify(null, null, "123456")
        )
    }
}
