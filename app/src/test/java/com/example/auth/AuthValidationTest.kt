package com.example.auth

import com.example.model.RoleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure auth security rules. These run on the JVM
 * (`gradlew testDebugUnitTest`) with no device.
 */
class AuthValidationTest {

    @Test
    fun `roleToSlug produces the canonical database slugs`() {
        assertEquals("informal_collector", AuthValidation.roleToSlug(RoleType.INFORMAL_COLLECTOR))
        assertEquals("formal_recycler", AuthValidation.roleToSlug(RoleType.FORMAL_RECYCLER))
        assertEquals("government_admin", AuthValidation.roleToSlug(RoleType.GOVERNMENT_ADMIN))
    }

    @Test
    fun `slugToRole maps canonical and legacy slugs`() {
        assertEquals(RoleType.INFORMAL_COLLECTOR, AuthValidation.slugToRole("informal_collector"))
        assertEquals(RoleType.FORMAL_RECYCLER, AuthValidation.slugToRole("formal_recycler"))
        assertEquals(RoleType.GOVERNMENT_ADMIN, AuthValidation.slugToRole("government_admin"))
        assertEquals(RoleType.INFORMAL_COLLECTOR, AuthValidation.slugToRole("collector"))
        assertEquals(RoleType.FORMAL_RECYCLER, AuthValidation.slugToRole("recycler"))
        assertEquals(RoleType.GOVERNMENT_ADMIN, AuthValidation.slugToRole("admin"))
        assertEquals(RoleType.GOVERNMENT_ADMIN, AuthValidation.slugToRole("  ADMIN  "))
    }

    @Test
    fun `slugToRole never defaults to a privileged role`() {
        assertNull(AuthValidation.slugToRole(null))
        assertNull(AuthValidation.slugToRole(""))
        assertNull(AuthValidation.slugToRole("   "))
        assertNull(AuthValidation.slugToRole("root"))
        assertNull(AuthValidation.slugToRole("superuser"))
        assertNull(AuthValidation.slugToRole("formal_recycler_admin"))
    }

    @Test
    fun `normalizeIndianMobile accepts common Indian formats`() {
        assertEquals("9876543210", AuthValidation.normalizeIndianMobile("9876543210"))
        assertEquals("9876543210", AuthValidation.normalizeIndianMobile("+91 98765 43210"))
        assertEquals("9876543210", AuthValidation.normalizeIndianMobile("919876543210"))
        assertEquals("9876543210", AuthValidation.normalizeIndianMobile("09876543210"))
        assertEquals("9876543210", AuthValidation.normalizeIndianMobile("+91-98765-43210"))
        assertEquals("6123456789", AuthValidation.normalizeIndianMobile("6123456789"))
    }

    @Test
    fun `normalizeIndianMobile rejects invalid numbers`() {
        assertNull(AuthValidation.normalizeIndianMobile(null))
        assertNull(AuthValidation.normalizeIndianMobile(""))
        assertNull(AuthValidation.normalizeIndianMobile("12345"))
        assertNull(AuthValidation.normalizeIndianMobile("1234567890")) // must start 6-9
        assertNull(AuthValidation.normalizeIndianMobile("123456789012345"))
    }

    @Test
    fun `isValidIndianMobile mirrors normalizeIndianMobile`() {
        assertTrue(AuthValidation.isValidIndianMobile("+91 98765 43210"))
        assertFalse(AuthValidation.isValidIndianMobile("1234567890"))
    }

    @Test
    fun `isValidOtpFormat requires exactly six digits`() {
        assertTrue(AuthValidation.isValidOtpFormat("000000"))
        assertTrue(AuthValidation.isValidOtpFormat("123456"))
        assertFalse(AuthValidation.isValidOtpFormat("12345"))
        assertFalse(AuthValidation.isValidOtpFormat("1234567"))
        assertFalse(AuthValidation.isValidOtpFormat("12345a"))
        assertFalse(AuthValidation.isValidOtpFormat(" 123456"))
        assertFalse(AuthValidation.isValidOtpFormat(null))
    }

    @Test
    fun `otpMatches only accepts an exact code`() {
        assertTrue(AuthValidation.otpMatches("123456", "123456"))
        assertFalse(AuthValidation.otpMatches("123456", "123457"))
        assertFalse(AuthValidation.otpMatches("123456", "12345"))
        assertFalse(AuthValidation.otpMatches("", ""))
        assertFalse(AuthValidation.otpMatches(null, "123456"))
        assertFalse(AuthValidation.otpMatches("123456", null))
    }
}
