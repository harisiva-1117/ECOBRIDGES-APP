package com.example.auth

import com.example.model.RoleType

/**
 * Pure, dependency-free validation helpers shared by the auth flow.
 *
 * Everything here is deterministic and JVM-testable so the security rules
 * (role mapping, Indian mobile normalisation, OTP shape) can be unit tested
 * without an Android device. Keep it free of `Context`, `BuildConfig` and
 * network access.
 */
object AuthValidation {

    /** Canonical database slug for a role. */
    fun roleToSlug(role: RoleType): String = when (role) {
        RoleType.INFORMAL_COLLECTOR -> "informal_collector"
        RoleType.FORMAL_RECYCLER -> "formal_recycler"
        RoleType.GOVERNMENT_ADMIN -> "government_admin"
    }

    /**
     * Maps a stored slug back to a [RoleType]. Accepts the legacy short forms
     * only; anything else (including empty/null) returns `null` so callers
     * cannot default into a privileged role.
     */
    fun slugToRole(value: String?): RoleType? = when (value?.trim()?.lowercase()) {
        "informal_collector", "collector" -> RoleType.INFORMAL_COLLECTOR
        "formal_recycler", "recycler" -> RoleType.FORMAL_RECYCLER
        "government_admin", "admin" -> RoleType.GOVERNMENT_ADMIN
        else -> null
    }

    /**
     * Reduces any user-supplied phone representation to the 10-digit Indian
     * national number, or `null` when it is not a valid mobile number.
     *
     * Accepts `+91 98765 43210`, `919876543210`, `09876543210`-style input by
     * stripping every non-digit and taking the last 10 digits, then requires
     * the Indian mobile prefix `[6-9]`.
     */
    fun normalizeIndianMobile(raw: String?): String? {
        var digits = raw?.filter { it.isDigit() }.orEmpty()
        when {
            digits.length == 12 && digits.startsWith("91") -> digits = digits.substring(2)
            digits.length == 11 && digits.startsWith("0") -> digits = digits.substring(1)
        }
        if (digits.length != 10) return null
        return if (digits.first() in '6'..'9') digits else null
    }

    fun isValidIndianMobile(raw: String?): Boolean = normalizeIndianMobile(raw) != null

    /** A 6-digit numeric code, nothing else. */
    fun isValidOtpFormat(code: String?): Boolean =
        code != null && code.length == 6 && code.all { it.isDigit() }

    /**
     * Constant-time-ish comparison for user-entered OTPs. Lengths are equal
     * for any valid input, so this avoids early-exit character comparison.
     */
    fun otpMatches(expected: String?, entered: String?): Boolean {
        val a = expected.orEmpty()
        val b = entered.orEmpty()
        if (a.isEmpty() || a.length != b.length) return false
        var diff = 0
        for (i in a.indices) {
            diff = diff or (a[i].code xor b[i].code)
        }
        return diff == 0
    }
}
