package com.example.auth

import com.example.BuildConfig
import com.example.model.RoleType

/**
 * Parsed demo-login configuration.
 *
 * Demo login exists for hackathon demonstrations only. It is an on-device,
 * clearly-labelled stand-in for a real SMS/OTP provider and must never be
 * described as identity verification:
 *
 *  - no message is transmitted anywhere;
 *  - the code is a build-time constant shown in the UI;
 *  - demo sessions never receive a Supabase JWT, so RLS still blocks every
 *    protected database operation;
 *  - Admin demo access requires both the build flag and an explicitly
 *    configured admin demo account (a client cannot promote itself).
 */
data class DemoLoginSettings(
    val enabled: Boolean,
    val allowRelease: Boolean,
    val isDebugBuild: Boolean,
    val otpCode: String,
    val adminEnabled: Boolean,
    val accounts: Map<String, RoleType>
) {
    /** True only when this build may actually use the demo flow. */
    val available: Boolean
        get() = enabled && (isDebugBuild || allowRelease)

    /** The phone numbers configured for local testing. */
    val configuredAccounts: List<Map.Entry<String, RoleType>>
        get() = accounts.entries.toList()

    fun configuredRoleFor(rawPhone: String?): RoleType? {
        val phone = DemoAuthConfig.normalizeDemoPhone(rawPhone) ?: return null
        return accounts[phone]
    }

    fun isConfiguredAccount(rawPhone: String?): Boolean = configuredRoleFor(rawPhone) != null

    /**
     * Admin is never self-selectable: it requires the admin build flag AND a
     * phone explicitly listed as an admin demo account.
     */
    fun canUseRole(role: RoleType, rawPhone: String?): Boolean = when (role) {
        RoleType.GOVERNMENT_ADMIN ->
            adminEnabled && configuredRoleFor(rawPhone) == RoleType.GOVERNMENT_ADMIN
        else -> true
    }

    /** Human-readable, safe description of the configured demo accounts. */
    fun describeAccounts(): String = accounts.entries.joinToString(", ") { (phone, role) ->
        val masked = if (phone.length == 10) "+91 ${phone.take(2)}••••${phone.takeLast(2)}" else phone
        "$masked=${AuthValidation.roleToSlug(role)}"
    }
}

/**
 * Build-time demo configuration. Values come from `BuildConfig` (populated by
 * `app/build.gradle.kts` from `.env` / defaults) and are parsed exactly once.
 */
object DemoAuthConfig {

    const val DEFAULT_DEMO_OTP = "123456"

    val settings: DemoLoginSettings by lazy {
        parse(
            enabled = BuildConfig.DEMO_LOGIN_ENABLED,
            allowRelease = BuildConfig.DEMO_LOGIN_ALLOW_RELEASE,
            isDebugBuild = BuildConfig.DEBUG,
            otpCodeRaw = BuildConfig.DEMO_OTP_CODE,
            adminEnabled = BuildConfig.DEMO_ENABLE_ADMIN,
            accountsRaw = BuildConfig.DEMO_ACCOUNTS
        )
    }

    /** Demo sign-in may run in this build. */
    val isAvailable: Boolean get() = settings.available

    /** The configurable demo code, default `123456`. */
    val otpCode: String get() = settings.otpCode

    val adminEnabled: Boolean get() = settings.adminEnabled

    fun configuredRoleFor(rawPhone: String?): RoleType? = settings.configuredRoleFor(rawPhone)

    fun isConfiguredAccount(rawPhone: String?): Boolean = settings.isConfiguredAccount(rawPhone)

    fun canUseRole(role: RoleType, rawPhone: String?): Boolean =
        settings.canUseRole(role, rawPhone)

    fun describeAccounts(): String = settings.describeAccounts()

    /**
     * Pure parser (unit-tested without Android). The demo code is normalised to
     * six digits; malformed configuration falls back to the safe default.
     */
    internal fun parse(
        enabled: Boolean,
        allowRelease: Boolean,
        isDebugBuild: Boolean,
        otpCodeRaw: String?,
        adminEnabled: Boolean,
        accountsRaw: String?
    ): DemoLoginSettings {
        val code = otpCodeRaw?.trim()?.takeIf { it.matches(Regex("^[0-9]{6}$")) }
            ?: DEFAULT_DEMO_OTP

        val accounts = linkedMapOf<String, RoleType>()
        accountsRaw
            .orEmpty()
            .split(',', ';', '\n')
            .map { it.trim() }
            .filter { it.contains(':') }
            .forEach { entry ->
                val phone = normalizeDemoPhone(entry.substringBefore(':'))
                val role = AuthValidation.slugToRole(entry.substringAfter(':'))
                if (phone != null && role != null) {
                    accounts[phone] = role
                }
            }

        return DemoLoginSettings(
            enabled = enabled,
            allowRelease = allowRelease,
            isDebugBuild = isDebugBuild,
            otpCode = code,
            adminEnabled = adminEnabled,
            accounts = accounts
        )
    }

    /** Accepts `9876543210`, `+91 98765 43210`, `919876543210`. */
    fun normalizeDemoPhone(rawPhone: String?): String? {
        val digits = rawPhone.orEmpty().filter { it.isDigit() }
        val national = when {
            digits.length == 12 && digits.startsWith("91") -> digits.takeLast(10)
            digits.length == 11 && digits.startsWith("0") -> digits.takeLast(10)
            digits.length == 10 -> digits
            else -> return null
        }
        return national.takeIf { it.firstOrNull() in '6'..'9' }
    }
}

/** Outcome of checking an entered demo code. */
enum class DemoOtpVerification {
    SUCCESS,
    NO_ACTIVE_CHALLENGE,
    MALFORMED,
    MISMATCH
}

/**
 * Offline demo-code checker used by [SupabaseAuthService] and unit tests.
 *
 * It compares against the configured code and, when a challenge was issued, the
 * code stored in that challenge. No network or SMS is involved.
 */
object DemoOtpVerifier {

    fun verify(
        configuredCode: String?,
        challengeCode: String?,
        enteredCode: String?
    ): DemoOtpVerification {
        val entered = enteredCode?.trim().orEmpty()
        if (challengeCode.isNullOrBlank() && configuredCode.isNullOrBlank()) {
            return DemoOtpVerification.NO_ACTIVE_CHALLENGE
        }
        if (!AuthValidation.isValidOtpFormat(entered)) {
            return DemoOtpVerification.MALFORMED
        }
        val matchesConfigured = AuthValidation.otpMatches(configuredCode, entered)
        val matchesChallenge = AuthValidation.otpMatches(challengeCode, entered)
        return if (matchesConfigured || matchesChallenge) {
            DemoOtpVerification.SUCCESS
        } else {
            DemoOtpVerification.MISMATCH
        }
    }
}
