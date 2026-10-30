package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The demo OTP is the only code the app generates. It must be 6 digits and
 * unpredictable (SecureRandom, never kotlin.random.Random).
 */
class OtpManagerTest {

    @Test
    fun `generateOtp returns six digits`() {
        repeat(500) {
            val otp = OtpManager.generateOtp()
            assertEquals("OTP must be 6 digits: $otp", 6, otp.length)
            assertTrue("OTP must be numeric: $otp", otp.all { c -> c.isDigit() })
        }
    }

    @Test
    fun `generateOtp never produces a leading short value`() {
        // 100000..999999: no zero-padding surprises, always a full 6 digits.
        repeat(500) {
            val value = OtpManager.generateOtp().toInt()
            assertTrue("OTP out of range: $value", value in 100_000..999_999)
        }
    }

    @Test
    fun `generateOtp produces varying codes`() {
        val codes = (1..200).map { OtpManager.generateOtp() }.toSet()
        // With 900k possibilities, 200 draws should not collapse to a handful.
        assertTrue("Expected varied OTPs, got ${codes.size} unique", codes.size > 150)
    }

    @Test
    fun `demo dispatch result is never reported as a sent SMS`() {
        // No Context is needed to inspect the default flag contract; the data
        // class default must stay false so no caller can claim an SMS was sent.
        val result = OtpDeliveryResult(otp = "123456")
        assertFalse(result.isSmsDispatched)
        assertFalse(result.isNotificationPosted)
        assertEquals("", result.detailMessage)
    }
}
