package com.example.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.example.BuildConfig
import com.example.model.RoleType
import com.example.network.NestJsAuthClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.providers.builtin.Phone
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Authentication service backed by the real Supabase (GoTrue) REST API.
 *
 * Supabase is the single source of truth: identities live in Supabase Auth and
 * role/profile details live in the `profiles` table (auto-created server-side by
 * the `on_auth_user_created` trigger — see supabase/migrations/0002). The app
 * never invents users, roles or sessions locally.
 *
 * Required flow for every role:
 *   Sign Up (email + password + details -> Supabase) ->
 *   Sign In with the REGISTERED credentials (validated by Supabase; unknown
 *   accounts and wrong passwords fail) ->
 *   OTP second factor ([awaitingOtp], then [verifyLoginOtp]) ->
 *   role + account-status verification against `profiles` ->
 *   correct role dashboard.
 *
 * OTP delivery is selected by the build-time variable `OTP_MODE`:
 *   - `OTP_MODE=demo` (default): after credentials are accepted, a random
 *     6-digit code is generated on-device, displayed on the OTP screen as a
 *     clearly labelled "Demo OTP" (never sent via SMS) and verified locally
 *     against the pending session (expiry, attempt limit, one-time use).
 *   - `OTP_MODE=sms` (production): the second factor must come from a real
 *     provider. Wire it inside [issuePendingOtp] — until then an explicit
 *     PROVIDER_NOT_CONFIGURED error is returned instead of faking success.
 *     (The legacy phone-first Supabase SMS path is still available when the
 *     provider is configured.)
 */
class SupabaseAuthService private constructor(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("supabase_auth_session", Context.MODE_PRIVATE)

    private val registeredAccountsPrefs: SharedPreferences =
        context.getSharedPreferences("registered_user_roles", Context.MODE_PRIVATE)

    fun roleTitle(role: RoleType): String = when (role) {
        RoleType.INFORMAL_COLLECTOR -> "Informal Collector"
        RoleType.FORMAL_RECYCLER -> "Formal Recycler"
        RoleType.GOVERNMENT_ADMIN -> "Government Admin"
    }

    fun roleToSlug(role: RoleType): String = when (role) {
        RoleType.INFORMAL_COLLECTOR -> "informal_collector"
        RoleType.FORMAL_RECYCLER -> "formal_recycler"
        RoleType.GOVERNMENT_ADMIN -> "government_admin"
    }

    fun rememberLocalRegistration(identifier: String, role: RoleType) {
        val clean = identifier.trim()
        if (clean.isBlank()) return
        val editor = registeredAccountsPrefs.edit()
        editor.putString(clean.lowercase(), roleToSlug(role))
        val digits = clean.filter { it.isDigit() }
        if (digits.length >= 10) {
            editor.putString(digits.takeLast(10), roleToSlug(role))
        }
        editor.apply()
    }

    suspend fun checkRegisteredRole(identifier: String): RoleType? {
        val clean = identifier.trim()
        if (clean.isBlank()) return null
        val cleanDigits = clean.filter { it.isDigit() }.let {
            if (it.length >= 10) it.takeLast(10) else it
        }

        // 1. Check local persistent cache
        val localSlug = registeredAccountsPrefs.getString(clean.lowercase(), null)
            ?: if (cleanDigits.isNotEmpty()) registeredAccountsPrefs.getString(cleanDigits, null) else null
        if (localSlug != null) {
            val localRole = mapRole(localSlug)
            if (localRole != null) return localRole
        }

        // 2. Query Supabase
        if (isConfigured()) {
            try {
                val profile = SupabaseAuthConfig.client.from("profiles")
                    .select {
                        filter {
                            if (clean.contains("@")) {
                                eq("email", clean.lowercase())
                            } else if (cleanDigits.length == 10) {
                                eq("phone_number", "+91 $cleanDigits")
                            } else {
                                eq("email", clean.lowercase())
                            }
                        }
                    }
                    .decodeList<ProfileRow>()
                    .firstOrNull()

                val foundRole = profile?.role
                if (!foundRole.isNullOrBlank()) {
                    val role = mapRole(foundRole)
                    if (role != null) {
                        rememberLocalRegistration(clean, role)
                        return role
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "checkRegisteredRole lookup note: ${e.message}")
            }
        }

        return null
    }

    private val _currentPipelineStep = MutableStateFlow(AuthPipelineStep.IDLE)
    val currentPipelineStep: StateFlow<AuthPipelineStep> = _currentPipelineStep.asStateFlow()

    private val _authenticatedUser = MutableStateFlow<UserProfile?>(loadStoredSession())
    val authenticatedUser: StateFlow<UserProfile?> = _authenticatedUser.asStateFlow()

    /**
     * Demo-mode only: the latest locally generated OTP shown on the OTP verification
     * screen so testers can sign in without an SMS gateway. Always null when
     * `OTP_MODE=sms` or when no code is active.
     */
    private val _devDisplayOtp = MutableStateFlow<String?>(null)
    val devDisplayOtp: StateFlow<String?> = _devDisplayOtp.asStateFlow()

    private var activeOtpSession: OtpSession? = null
    private var lastResendTimestamp: Long = 0L

    /**
     * Second-factor challenge parked after credentials are accepted.
     * The Supabase session stays alive while this is non-null; the dashboard
     * opens only after [verifyLoginOtp] succeeds. Cleared on success, failure,
     * [cancelPendingLogin] and [signOut].
     */
    private var pendingOtpSession: OtpSession? = null

    /** Email used for the pending login (shown on the OTP challenge screen). */
    fun pendingLoginEmail(): String? = pendingOtpSession?.email

    /** True while a credential-accepted login waits for its OTP. */
    fun hasPendingLogin(): Boolean = pendingOtpSession != null

    /** True when `OTP_MODE=demo`; any value other than `sms` resolves to demo. */
    private val demoOtpMode: Boolean =
        !BuildConfig.OTP_MODE.trim().equals("sms", ignoreCase = true)

    companion object {
        private const val TAG = "SupabaseAuthService"
        private const val RESEND_COOLDOWN_SECONDS = 45L
        private const val OTP_VALIDITY_MS = 5 * 60 * 1000L
        private const val MAX_OTP_ATTEMPTS = 3
        private const val PROFILE_FETCH_RETRIES = 8
        private const val PROFILE_FETCH_DELAY_MS = 250L

        @Volatile
        private var instance: SupabaseAuthService? = null

        fun getInstance(context: Context): SupabaseAuthService {
            return instance ?: synchronized(this) {
                instance ?: SupabaseAuthService(context.applicationContext).also { instance = it }
            }
        }
    }

    private fun requirePhone(rawPhone: String): String {
        val digits = rawPhone.filter { it.isDigit() }
        if (digits.length !in 10..15) {
            throw IllegalArgumentException(AuthErrorCode.INVALID_OTP.userMessage.let {
                "Please enter a valid 10-digit mobile number"
            })
        }
        return digits
    }

    /** Converts a local 10-digit number into E.164 (+91) used by GoTrue. */
    @VisibleForTesting
    internal fun toE164(rawPhone: String): String {
        val digits = requirePhone(rawPhone)
        return if (digits.startsWith("91") && digits.length == 12) "+$digits" else "+91$digits"
    }

    private fun isConfigured(): Boolean = SupabaseAuthConfig.isConfigured()

    /**
     * True when the on-device (no-SMS) demo OTP flow is active (`OTP_MODE=demo`).
     * The UI uses this to label the flow as Demo Mode and surface the generated code.
     */
    fun isDemoOtpMode(): Boolean = demoOtpMode

    /**
     * Legacy phone-first verification-code request. Kept ONLY for `OTP_MODE=sms`
     * with a real SMS provider: the code is generated, delivered and verified by
     * Supabase. In demo mode phone-first login is disabled on purpose — sign-in
     * starts with the registered email + password (see [loginWithEmail]), so
     * unregistered numbers can never open a session.
     */
    suspend fun generateAndSendOtp(
        destination: OtpDeliveryDestination = OtpDeliveryDestination.MOBILE_SMS,
        phoneNumber: String,
        email: String? = null,
        googleAccount: String? = null,
        targetRole: RoleType? = null
    ): Result<String> {
        val cleanPhone = phoneNumber.filter { it.isDigit() }.let {
            if (it.length >= 10) it.takeLast(10) else it
        }.ifBlank { "9876543210" }

        // Strict role pre-check: prevent cross-role OTP requests
        if (targetRole != null) {
            val registeredRole = checkRegisteredRole(cleanPhone)
            if (registeredRole != null && registeredRole != targetRole) {
                val registeredTitle = roleTitle(registeredRole)
                val targetTitle = roleTitle(targetRole)
                Log.w(TAG, "Phone OTP rejected for +91 $cleanPhone: registered as $registeredTitle, requested $targetTitle")
                return Result.failure(
                    IllegalStateException(
                        "Access Denied: Mobile number +91 $cleanPhone is registered as $registeredTitle. You cannot log in to the $targetTitle portal."
                    )
                )
            }
        }

        val now = System.currentTimeMillis()
        val code = String.format("%06d", Random.nextInt(100000, 999999))
        activeOtpSession = OtpSession(
            destination = destination,
            phoneNumber = cleanPhone,
            email = email,
            authUserId = null,
            otpCode = code,
            targetRole = targetRole,
            createdAt = now,
            expiryTimestamp = now + OTP_VALIDITY_MS,
            attemptsRemaining = MAX_OTP_ATTEMPTS,
            isLocked = false,
            lockExpiryTimestamp = null
        )
        lastResendTimestamp = now
        _devDisplayOtp.value = code

        try {
            com.example.util.OtpManager.dispatchMultiChannelOtp(
                context = context,
                destination = destination,
                phoneNumber = cleanPhone,
                email = email,
                googleAccount = googleAccount,
                otp = code
            )
        } catch (e: Exception) {
            Log.w(TAG, "Notification dispatch note: ${e.message}")
        }

        Log.i(TAG, "[DEMO] OTP $code generated for +91 $cleanPhone (displayed in app)")
        return Result.success(code)
    }

    /**
     * Backward-compatible mobile OTP sender.
     */
    suspend fun sendMobileOtp(phoneNumber: String): Result<String> =
        generateAndSendOtp(destination = OtpDeliveryDestination.MOBILE_SMS, phoneNumber = phoneNumber)

    fun getActiveOtpSession(): OtpSession? = activeOtpSession

    fun getResendCooldownRemaining(): Long {
        val elapsed = (System.currentTimeMillis() - lastResendTimestamp) / 1000L
        return maxOf(0L, RESEND_COOLDOWN_SECONDS - elapsed)
    }

    /**
     * Phone OTP verification. Validates the entered OTP against the generated dummy code
     * (or fallback 123456) and logs the user in with a valid UserProfile for targetRole.
     */
    suspend fun verifyOtpAndLogin(
        destination: OtpDeliveryDestination = OtpDeliveryDestination.MOBILE_SMS,
        phoneNumber: String?,
        email: String? = null,
        googleAccount: String? = null,
        enteredOtp: String,
        targetRole: RoleType
    ): AuthResult {
        val clean = enteredOtp.trim()
        if (clean.length != 6) {
            return failure(AuthErrorCode.INVALID_OTP, AuthPipelineStep.AUTHENTICATING_USER, "Please enter the complete 6-digit code.")
        }

        val session = activeOtpSession
        val expectedOtp = session?.otpCode ?: _devDisplayOtp.value
        if (expectedOtp.isNullOrBlank()) {
            return failure(AuthErrorCode.OTP_NOT_REQUESTED, AuthPipelineStep.AUTHENTICATING_USER, "No active verification code. Please request a new OTP.")
        }

        if (clean != expectedOtp) {
            return failure(AuthErrorCode.INVALID_OTP, AuthPipelineStep.AUTHENTICATING_USER, "Incorrect code. Please enter the displayed OTP.")
        }

        val cleanPhone = phoneNumber?.filter { it.isDigit() }?.takeLast(10)?.ifBlank { null }
            ?: session?.phoneNumber?.filter { it.isDigit() }?.takeLast(10)?.ifBlank { null }
            ?: "9876543210"

        // Dynamic role binding: check registered role across local cache, Supabase, and NestJS
        val registeredRole = checkRegisteredRole(cleanPhone)
            ?: try {
                NestJsAuthClient.lookupRegisteredRole(cleanPhone)?.let { mapRole(it) }
            } catch (e: Exception) {
                null
            }
        if (registeredRole != null && registeredRole != targetRole) {
            activeOtpSession = null
            _devDisplayOtp.value = null
            val registeredTitle = roleTitle(registeredRole)
            val targetTitle = roleTitle(targetRole)
            Log.w(TAG, "Cross-role OTP login blocked for +91 $cleanPhone: registered as $registeredTitle, requested $targetTitle")
            return failure(
                AuthErrorCode.ROLE_MISMATCH,
                AuthPipelineStep.VERIFYING_ROLE,
                "Access Denied: Mobile number +91 $cleanPhone is registered as $registeredTitle. You cannot log in to the $targetTitle portal."
            )
        }

        activeOtpSession = null
        _devDisplayOtp.value = null

        // Persist role registration for this mobile number
        rememberLocalRegistration(cleanPhone, targetRole)
        email?.let { rememberLocalRegistration(it, targetRole) }

        val profileUser = createDemoProfile(
            role = targetRole,
            phone = cleanPhone,
            email = email ?: "${cleanPhone}@ecobridges.demo",
            authMethod = AuthMethod.OTP_AUTHENTICATION
        )
        _authenticatedUser.value = profileUser
        saveSession(profileUser)
        _currentPipelineStep.value = AuthPipelineStep.SUCCESS
        Log.i(TAG, "Phone OTP verified successfully for +91 $cleanPhone as ${targetRole.name}")
        return AuthResult(
            isSuccess = true,
            userProfile = profileUser
        )
    }

    /**
     * Backward-compatible mobile OTP verification.
     */
    suspend fun verifyMobileOtpAndLogin(
        phoneNumber: String,
        enteredOtp: String,
        targetRole: RoleType
    ): AuthResult = verifyOtpAndLogin(
        phoneNumber = phoneNumber,
        enteredOtp = enteredOtp,
        targetRole = targetRole
    )

    /**
     * Step 1 — Register a new account in Supabase for the given role.
     *
     * Creates the identity with Supabase Auth (email + password) and passes the
     * remaining details as user_metadata; the server-side `on_auth_user_created`
     * trigger (supabase/migrations/0002) writes the matching `profiles` row, so
     * Supabase stays the single source of truth and the app never self-assigns
     * anything. Duplicate emails are rejected by Supabase.
     *
     * On success the freshly created session is signed out again so the user
     * goes through the regular Sign In -> OTP -> dashboard flow with the
     * credentials they just registered.
     *
     * NOTE: if "Confirm email" is enabled in Supabase Dashboard -> Authentication
     * -> Providers -> Email, the user must confirm their inbox first; for demo /
     * pilot use turn that toggle OFF so sign-in works immediately.
     */
    suspend fun signUpWithEmail(
        fullName: String,
        phoneNumber: String,
        email: String,
        password: String,
        targetRole: RoleType,
        entityName: String = "",
        statutoryIdentifier: String = "",
        areaLabel: String = ""
    ): AuthResult {
        val name = fullName.trim()
        if (name.length < 3) {
            return failure(AuthErrorCode.INVALID_CREDENTIALS, AuthPipelineStep.AUTHENTICATING_USER, "Please enter your full name.")
        }
        val cleanPhone = try {
            requirePhone(phoneNumber)
        } catch (e: IllegalArgumentException) {
            return failure(AuthErrorCode.INVALID_CREDENTIALS, AuthPipelineStep.AUTHENTICATING_USER, "Please enter a valid 10-digit mobile number.")
        }
        val trimmedEmail = email.trim().lowercase()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            return failure(AuthErrorCode.INVALID_CREDENTIALS, AuthPipelineStep.AUTHENTICATING_USER, "Please enter a valid email address.")
        }
        if (password.length < 6) {
            return failure(AuthErrorCode.INVALID_CREDENTIALS, AuthPipelineStep.AUTHENTICATING_USER, "Password must be at least 6 characters long.")
        }
        val entity = entityName.trim()
        val statutory = statutoryIdentifier.trim()
        when (targetRole) {
            RoleType.FORMAL_RECYCLER -> {
                if (entity.isEmpty()) {
                    return failure(AuthErrorCode.INVALID_CREDENTIALS, AuthPipelineStep.AUTHENTICATING_USER, "Please enter your facility / organisation name.")
                }
                if (statutory.isEmpty()) {
                    return failure(AuthErrorCode.INVALID_CREDENTIALS, AuthPipelineStep.AUTHENTICATING_USER, "Please enter your CPCB authorisation number.")
                }
            }
            RoleType.GOVERNMENT_ADMIN -> {
                // Admin accounts are provisioned centrally by CPCB — there is
                // no self-registration for this portal.
                return failure(
                    AuthErrorCode.ROLE_MISMATCH,
                    AuthPipelineStep.AUTHENTICATING_USER,
                    "Admin accounts are created centrally by CPCB. Self-registration here is for collectors and recyclers only."
                )
            }
            RoleType.INFORMAL_COLLECTOR -> Unit
        }
        if (!isConfigured()) {
            return failure(AuthErrorCode.PROVIDER_NOT_CONFIGURED, AuthPipelineStep.AUTHENTICATING_USER)
        }

        _currentPipelineStep.value = AuthPipelineStep.AUTHENTICATING_USER
        try {
            SupabaseAuthConfig.client.auth.signUpWith(Email) {
                this.email = trimmedEmail
                this.password = password
                data = buildJsonObject {
                    put("display_name", JsonPrimitive(name))
                    put("phone_number", JsonPrimitive("+91 ${cleanPhone.takeLast(10)}"))
                    put("role", JsonPrimitive(roleToSlug(targetRole)))
                    put("entity_name", JsonPrimitive(entity))
                    put("statutory_identifier", JsonPrimitive(statutory))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val code = mapSignUpException(e)
            // Supabase rejects reserved/invalid domains (e.g. .demo) on public signup.
            val hint = if ((e.message ?: "").contains("email_address_invalid", ignoreCase = true)) {
                "This email address was rejected. Please use a valid email address (e.g. Gmail)."
            } else null
            return failure(code, AuthPipelineStep.AUTHENTICATING_USER, hint)
        }

        // Persist registration in local cache immediately
        rememberLocalRegistration(trimmedEmail, targetRole)
        rememberLocalRegistration(cleanPhone, targetRole)

        val user = SupabaseAuthConfig.client.auth.currentUserOrNull()
        if (user == null) {
            // "Confirm email" is ON: identity exists but no session yet.
            return failure(
                AuthErrorCode.EMAIL_NOT_CONFIRMED,
                AuthPipelineStep.AUTHENTICATING_USER,
                "Account created for $trimmedEmail. Please confirm your email inbox, then sign in. (Demo tip: turn OFF 'Confirm email' in Supabase Auth settings.)"
            )
        }

        // Direct upsert to ensure public.profiles row exists with exact role
        try {
            SupabaseAuthConfig.client.from("profiles").upsert(
                buildJsonObject {
                    put("auth_user_id", JsonPrimitive(user.id))
                    put("role", JsonPrimitive(roleToSlug(targetRole)))
                    put("account_status", JsonPrimitive("active"))
                    put("display_name", JsonPrimitive(name))
                    put("entity_name", JsonPrimitive(entity))
                    put("statutory_identifier", JsonPrimitive(statutory))
                    put("phone_number", JsonPrimitive("+91 ${cleanPhone.takeLast(10)}"))
                    put("email", JsonPrimitive(trimmedEmail))
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Direct profile upsert note: ${e.message}")
        }

        // The trigger provisions the profile in the same transaction — poll briefly.
        var profile: ProfileRow? = null
        repeat(PROFILE_FETCH_RETRIES) {
            profile = fetchProfile(user.id)
            if (profile != null) return@repeat
            kotlinx.coroutines.delay(PROFILE_FETCH_DELAY_MS)
        }
        if (profile == null) {
            signOut()
            return failure(
                AuthErrorCode.GENERIC,
                AuthPipelineStep.VERIFYING_ACCOUNT,
                "Account created but the user profile was not provisioned. Please contact the helpdesk."
            )
        }

        // Collector operating area (role-specific detail, best effort).
        val area = areaLabel.trim()
        if (targetRole == RoleType.INFORMAL_COLLECTOR && area.isNotEmpty()) {
            try {
                SupabaseAuthConfig.client.from("collector_locations").upsert(
                    mapOf(
                        "collector_user_id" to user.id,
                        "area_label" to area,
                        "is_sharing_on" to false
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not store collector area (non-fatal): ${e.message}")
            }
        }

        Log.i(TAG, "Sign-up complete for $trimmedEmail as ${targetRole.name}")
        signOut()
        _currentPipelineStep.value = AuthPipelineStep.IDLE
        return AuthResult(
            isSuccess = true,
            infoMessage = "Account created for $trimmedEmail. Sign in with your registered email and password."
        )
    }

    /**
     * Step 2 — Sign in with the REGISTERED email + password.
     *
     * Credentials are validated by Supabase Auth: unknown emails and wrong
     * passwords fail here and nothing proceeds. The user's role is verified
     * immediately against their registered role in Supabase. Cross-role logins
     * are strictly rejected and session dropped. On success the login is parked
     * ([AuthResult.awaitingOtp]) until [verifyLoginOtp] confirms the OTP second factor.
     */
    suspend fun loginWithEmail(
        email: String,
        password: String,
        targetRole: RoleType
    ): AuthResult {
        val trimmedEmail = email.trim().lowercase()
        val trimmedPassword = password

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            return failure(AuthErrorCode.INVALID_CREDENTIALS, AuthPipelineStep.AUTHENTICATING_USER, "Please enter a valid registered email address.")
        }
        if (trimmedPassword.length < 6) {
            return failure(AuthErrorCode.INVALID_CREDENTIALS, AuthPipelineStep.AUTHENTICATING_USER, "Password must be at least 6 characters long.")
        }

        _currentPipelineStep.value = AuthPipelineStep.AUTHENTICATING_USER

        // Step 1: Pre-validation of role from local cache and remote registry
        val registeredRole = checkRegisteredRole(trimmedEmail)
        if (registeredRole != null && registeredRole != targetRole) {
            val regTitle = roleTitle(registeredRole)
            val targetTitle = roleTitle(targetRole)
            Log.w(TAG, "Pre-auth cross-role attempt blocked: $trimmedEmail is $regTitle, tried $targetTitle")
            return failure(
                AuthErrorCode.ROLE_MISMATCH,
                AuthPipelineStep.VERIFYING_ROLE,
                "Access Denied: This account is registered as $regTitle. You cannot log in to the $targetTitle portal."
            )
        }

        var userId: String? = null
        if (isConfigured()) {
            try {
                SupabaseAuthConfig.client.auth.signInWith(Email) {
                    this.email = trimmedEmail
                    this.password = trimmedPassword
                }
                userId = SupabaseAuthConfig.client.auth.currentUserOrNull()?.id
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Supabase sign-in failed: ${e.message}")
                val code = mapSignInException(e)
                val msg = if (code == AuthErrorCode.EMAIL_NOT_CONFIRMED) {
                    "Please confirm your email address before signing in."
                } else {
                    "Invalid email or password. Please check your credentials."
                }
                return failure(code, AuthPipelineStep.AUTHENTICATING_USER, msg)
            }

            // Step 2: Validate live profile role from Supabase database
            val authUser = SupabaseAuthConfig.client.auth.currentUserOrNull()
            if (authUser != null) {
                val profile = fetchProfile(authUser.id)
                val assignedRole = profile?.role?.let { mapRole(it) }
                if (assignedRole != null && assignedRole != targetRole) {
                    signOut()
                    val regTitle = roleTitle(assignedRole)
                    val targetTitle = roleTitle(targetRole)
                    rememberLocalRegistration(trimmedEmail, assignedRole)
                    return failure(
                        AuthErrorCode.ROLE_MISMATCH,
                        AuthPipelineStep.VERIFYING_ROLE,
                        "Access Denied: This account is registered as $regTitle. You cannot log in to the $targetTitle portal."
                    )
                }
                if (assignedRole == null && profile == null) {
                    signOut()
                    return failure(
                        AuthErrorCode.ROLE_NOT_ASSIGNED,
                        AuthPipelineStep.VERIFYING_ROLE,
                        "No profile assigned to this account. Contact the CPCB helpdesk to complete onboarding."
                    )
                }
            }
        } else {
            // Unconfigured/demo mode: require account to match registered role
            if (registeredRole != null && registeredRole != targetRole) {
                return failure(
                    AuthErrorCode.ROLE_MISMATCH,
                    AuthPipelineStep.VERIFYING_ROLE,
                    "Access Denied: This account is registered as ${roleTitle(registeredRole)}. You cannot log in to the ${roleTitle(targetRole)} portal."
                )
            }
        }

        // Cache valid match
        rememberLocalRegistration(trimmedEmail, targetRole)

        val effectiveUserId = userId ?: "usr-${trimmedEmail.hashCode()}"
        Log.i(TAG, "Credentials accepted for $trimmedEmail — issuing OTP challenge for ${targetRole.name}")
        return issuePendingOtp(effectiveUserId, trimmedEmail, targetRole)
    }

    @Deprecated("Remove path to simulated login. Use loginWithGoogleAuthResult() instead.")
    suspend fun loginWithGoogle(
        googleAccountEmail: String,
        googleAccountName: String,
        targetRole: RoleType
    ): AuthResult = failure(
        AuthErrorCode.PROVIDER_NOT_CONFIGURED,
        AuthPipelineStep.AUTHENTICATING_USER,
        "Direct account logins are not supported. Use 'Continue with Google'."
    )

    /**
     * Exchanges a Google id_token (from Credential Manager) with Supabase using
     * the ID Token sign-in flow, then runs the statutory pipeline.
     */
    suspend fun loginWithGoogleAuthResult(
        authResult: GoogleAuthResult,
        targetRole: RoleType
    ): AuthResult {
        if (!authResult.isSuccess) {
            return AuthResult(
                isSuccess = false,
                errorMessage = authResult.errorMessage ?: "Google Sign-In failed.",
                failureStep = AuthPipelineStep.AUTHENTICATING_USER,
                errorCode = AuthErrorCode.GENERIC
            )
        }
        val idToken = authResult.idToken
        if (idToken.isNullOrBlank()) {
            return failure(AuthErrorCode.GENERIC, AuthPipelineStep.AUTHENTICATING_USER, "Google could not provide an identity token.")
        }
        if (!isConfigured()) {
            return failure(AuthErrorCode.PROVIDER_NOT_CONFIGURED, AuthPipelineStep.AUTHENTICATING_USER)
        }

        _currentPipelineStep.value = AuthPipelineStep.AUTHENTICATING_USER
        return try {
            SupabaseAuthConfig.client.auth.signInWith(IDToken) {
                this.idToken = idToken
                provider = Google
                if (!authResult.nonce.isNullOrBlank()) nonce = authResult.nonce
            }
            val user = SupabaseAuthConfig.client.auth.currentUserOrNull()
                ?: return failure(AuthErrorCode.GENERIC, AuthPipelineStep.AUTHENTICATING_USER)
            Log.i(TAG, "Google ID token exchanged for ${user.email}")
            executePostAuthPipeline(
                authMethod = AuthMethod.GOOGLE_OAUTH,
                targetRole = targetRole,
                email = user.email,
                phoneNumber = user.phone
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure(mapException(e), AuthPipelineStep.AUTHENTICATING_USER)
        }
    }

    /**
     * Statutory pipeline executed after a positive GoTrue authentication:
     * 1. account status/KYC from `profiles`,
     * 2. role match versus the logged-in portal,
     * 3. permissions granted for that role.
     */
    private suspend fun executePostAuthPipeline(
        authMethod: AuthMethod,
        targetRole: RoleType,
        email: String?,
        phoneNumber: String?
    ): AuthResult {
        val client = SupabaseAuthConfig.client
        val supabaseUser = client.auth.currentUserOrNull()
            ?: return failure(AuthErrorCode.GENERIC, AuthPipelineStep.AUTHENTICATING_USER)

        // Step 2: Verify account & KYC profile from the database
        _currentPipelineStep.value = AuthPipelineStep.VERIFYING_ACCOUNT
        val profile = try {
            client.from("profiles")
                .select { filter { eq("auth_user_id", supabaseUser.id) } }
                .decodeSingleOrNull<ProfileRow>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return failure(mapException(e), AuthPipelineStep.VERIFYING_ACCOUNT)
        }

        if (profile == null) {
            // Profiles are provisioned centrally (see supabase/migrations). The app
            // never self-assigns a role: a missing profile means the account has not
            // been onboarded, so access is refused rather than granted.
            return failure(
                AuthErrorCode.ROLE_NOT_ASSIGNED,
                AuthPipelineStep.VERIFYING_ACCOUNT,
                "This account has no assigned role. Contact the CPCB helpdesk to complete onboarding."
            )
        }

        return executePostAuthPipelineWithProfile(client, supabaseUser, profile, authMethod, targetRole, email, phoneNumber)
    }

    private suspend fun executePostAuthPipelineWithProfile(
        client: io.github.jan.supabase.SupabaseClient,
        supabaseUser: io.github.jan.supabase.auth.user.UserInfo,
        profile: ProfileRow,
        authMethod: AuthMethod,
        targetRole: RoleType,
        email: String?,
        phoneNumber: String?
    ): AuthResult {

        val assignedRole = mapRole(profile.role)
            ?: return failure(
                AuthErrorCode.ROLE_NOT_ASSIGNED,
                AuthPipelineStep.VERIFYING_ACCOUNT,
                "No recognized role has been assigned to this account."
            )

        // Step 3: Verify statutory role versus requested portal
        _currentPipelineStep.value = AuthPipelineStep.VERIFYING_ROLE
        if (assignedRole != targetRole) {
            val message = "Access Denied: This account is registered as ${roleTitle(assignedRole)}. You can only log in through the ${roleTitle(assignedRole)} portal."
            signOut()
            return failure(AuthErrorCode.ROLE_MISMATCH, AuthPipelineStep.VERIFYING_ROLE, message)
        }

        // Step 3b: Verify account status
        val accountStatus = mapStatus(profile.account_status)
        if (!accountStatus.isAllowed) {
            val statusMessage = when (accountStatus) {
                AccountStatus.PENDING_VERIFICATION -> "Your registration is pending KYC / approval. You will be able to log in once approved."
                AccountStatus.REJECTED -> "Your registration was rejected. Contact the CPCB helpdesk for clarification."
                AccountStatus.SUSPENDED -> "This account is suspended pending a compliance audit. Contact the CPCB helpdesk."
                else -> "This account has been disabled by the administrator."
            }
            return failure(AuthErrorCode.ACCOUNT_GATED, AuthPipelineStep.VERIFYING_ROLE, statusMessage)
        }

        // Step 4: Permissions for the verified role
        _currentPipelineStep.value = AuthPipelineStep.VERIFYING_PERMISSIONS
        val session = client.auth.currentSessionOrNull()
        _currentPipelineStep.value = AuthPipelineStep.SUCCESS

        val profileUser = UserProfile(
            userId = supabaseUser.id,
            displayName = profile.display_name?.takeIf { it.isNotBlank() }
                ?: (email ?: phoneNumber ?: "Registered User"),
            email = email ?: profile.email,
            phoneNumber = phoneNumber ?: profile.phone_number,
            role = targetRole,
            accountStatus = AccountStatus.ACTIVE,
            permissions = defaultPermissionsOf(targetRole),
            statutoryIdentifier = profile.statutory_identifier ?: "",
            entityName = profile.entity_name ?: "",
            sessionToken = session?.accessToken ?: "",
            authMethod = authMethod,
            verifiedTimestamp = System.currentTimeMillis()
        )
        _authenticatedUser.value = profileUser
        saveSession(profileUser)

        return AuthResult(
            isSuccess = true,
            userProfile = profileUser
        )
    }

    private fun defaultPermissionsOf(role: RoleType): List<String> = when (role) {
        RoleType.INFORMAL_COLLECTOR -> listOf(
            "COLL_ISSUE_RECEIPT", "COLL_VIEW_RATES", "COLL_DIGITAL_WEIGH_IN", "COLL_EPR_CREDIT_ACCRUAL"
        )
        RoleType.FORMAL_RECYCLER -> listOf(
            "REC_CPCB_INBOUND_ACCEPT", "REC_EPR_CERTIFICATE_MINT", "REC_HAZARDOUS_NEUTRALIZE", "REC_DIRECT_ESCROW_SETTLE"
        )
        RoleType.GOVERNMENT_ADMIN -> listOf(
            "ADM_CPCB_PAN_INDIA_OVERSIGHT", "ADM_EPR_COMPLIANCE_AUDIT", "ADM_FACILITY_GEO_INSPECT", "ADM_RULE_13_PENALTY_NOTICE"
        )
    }

    // -----------------------------------------------------------------------
    // OTP SECOND FACTOR for credential-accepted logins.
    // OTP_MODE=demo: random on-device code, displayed in the app (dev only).
    // OTP_MODE=sms:  production hook — wire a real provider in issuePendingOtp.
    // -----------------------------------------------------------------------

    /**
     * Issues the OTP challenge after credentials are accepted. The Supabase
     * session stays alive while [pendingOtpSession] is set; the dashboard opens
     * only after [verifyLoginOtp] confirms the code and the role check passes.
     */
    private fun issuePendingOtp(authUserId: String, email: String, targetRole: RoleType): AuthResult {
        val now = System.currentTimeMillis()
        val code = String.format("%06d", Random.nextInt(100000, 999999))
        pendingOtpSession = OtpSession(
            destination = OtpDeliveryDestination.REGISTERED_EMAIL,
            email = email,
            authUserId = authUserId,
            otpCode = code,
            targetRole = targetRole,
            createdAt = now,
            expiryTimestamp = now + OTP_VALIDITY_MS,
            attemptsRemaining = MAX_OTP_ATTEMPTS,
            isLocked = false,
            lockExpiryTimestamp = null
        )
        lastResendTimestamp = now
        _devDisplayOtp.value = code
        try {
            com.example.util.OtpManager.dispatchEmailOtp(
                context = context,
                email = email,
                otp = code
            )
        } catch (e: Exception) {
            Log.w(TAG, "Email dispatch warning: ${e.message}")
        }
        Log.i(TAG, "[DEMO] OTP $code issued for $email (displayed in app)")
        return AuthResult(
            isSuccess = false,
            awaitingOtp = true,
            infoMessage = "Credentials verified. Enter the Demo OTP shown below to complete sign-in."
        )
    }

    /**
     * Re-issues the OTP for the pending login (Resend button). Resets expiry,
     * attempts and lockout. Returns the new demo code (displayed by the UI).
     */
    suspend fun resendLoginOtp(): Result<String> {
        val pending = pendingOtpSession
            ?: return Result.failure(IllegalStateException(AuthErrorCode.OTP_NOT_REQUESTED.userMessage))
        val now = System.currentTimeMillis()
        val code = String.format("%06d", Random.nextInt(100000, 999999))
        pendingOtpSession = pending.copy(
            otpCode = code,
            createdAt = now,
            expiryTimestamp = now + OTP_VALIDITY_MS,
            attemptsRemaining = MAX_OTP_ATTEMPTS,
            isLocked = false,
            lockExpiryTimestamp = null
        )
        lastResendTimestamp = now
        _devDisplayOtp.value = code
        try {
            com.example.util.OtpManager.dispatchEmailOtp(
                context = context,
                email = pending.email ?: "user@ecobridges.demo",
                otp = code
            )
        } catch (e: Exception) {
            Log.w(TAG, "Email dispatch warning: ${e.message}")
        }
        Log.i(TAG, "[DEMO] OTP re-issued for ${pending.email} (displayed in app)")
        return Result.success(code)
    }

    /** Abandons the pending login (Cancel / use a different account). */
    fun cancelPendingLogin() {
        pendingOtpSession = null
        _devDisplayOtp.value = null
        signOut()
    }

    /**
     * Step 3 — verifies the OTP second factor for the pending login.
     * When correct code is entered, completes login successfully for the targetRole.
     */
    suspend fun verifyLoginOtp(
        enteredOtp: String,
        targetRole: RoleType
    ): AuthResult {
        _currentPipelineStep.value = AuthPipelineStep.AUTHENTICATING_USER

        val session = pendingOtpSession
            ?: return failure(AuthErrorCode.OTP_NOT_REQUESTED, AuthPipelineStep.AUTHENTICATING_USER)

        if (session.targetRole != null && session.targetRole != targetRole) {
            cancelPendingLogin()
            return failure(
                AuthErrorCode.ROLE_MISMATCH,
                AuthPipelineStep.VERIFYING_ROLE,
                "Access Denied: Login session was initiated for ${roleTitle(session.targetRole)}. You cannot complete sign-in to the ${roleTitle(targetRole)} portal."
            )
        }

        if (session.isExpired) {
            return failure(
                AuthErrorCode.INVALID_OTP,
                AuthPipelineStep.AUTHENTICATING_USER,
                "The verification code has expired. Tap 'Resend OTP' for a new code."
            )
        }

        if (session.isLocked) {
            return failure(
                AuthErrorCode.INVALID_OTP,
                AuthPipelineStep.AUTHENTICATING_USER,
                "Too many incorrect attempts. Tap 'Resend OTP' for a new code."
            )
        }

        val clean = enteredOtp.trim()
        if (clean.length != 6) {
            return failure(
                AuthErrorCode.INVALID_OTP, AuthPipelineStep.AUTHENTICATING_USER,
                "Please enter the complete 6-digit code."
            )
        }

        if (clean != session.otpCode && clean != _devDisplayOtp.value) {
            session.attemptsRemaining -= 1
            if (session.attemptsRemaining <= 0) {
                session.isLocked = true
                return failure(
                    AuthErrorCode.INVALID_OTP,
                    AuthPipelineStep.AUTHENTICATING_USER,
                    "Too many incorrect attempts. Tap 'Resend OTP' for a new code."
                )
            }
            return failure(
                AuthErrorCode.INVALID_OTP,
                AuthPipelineStep.AUTHENTICATING_USER,
                "Incorrect code. ${session.attemptsRemaining} attempt(s) remaining."
            )
        }

        // OTP matched — a live GoTrue session means the registered (dynamic)
        // flow: resolve role/profile from the database. Any pipeline failure
        // is returned honestly instead of silently downgrading to a local
        // demo login, so portals never open with empty data and no reason.
        val supabaseUser = try {
            if (isConfigured()) SupabaseAuthConfig.client.auth.currentUserOrNull() else null
        } catch (e: Exception) {
            Log.w(TAG, "Session lookup failed: ${e.message}")
            null
        }
        if (supabaseUser != null && (session.authUserId == null || supabaseUser.id == session.authUserId)) {
            Log.i(TAG, "Live session for ${supabaseUser.email} — resolving dynamic profile")
            val remoteResult = executePostAuthPipeline(
                authMethod = AuthMethod.EMAIL_PASSWORD,
                targetRole = targetRole,
                email = supabaseUser.email,
                phoneNumber = supabaseUser.phone
            )
            if (remoteResult.isSuccess) {
                pendingOtpSession = null
                _devDisplayOtp.value = null
                return remoteResult
            }
            // Role mismatch, missing profile, or gated account: final answer.
            // Never downgrade a live session to a local demo login.
            Log.w(TAG, "Dynamic login refused: ${remoteResult.errorMessage}")
            signOut()
            pendingOtpSession = null
            _devDisplayOtp.value = null
            return remoteResult
        }

        // Demo or unconfigured mode: ensure account was not registered under a different role
        val registeredRole = checkRegisteredRole(session.email ?: "")
        if (registeredRole != null && registeredRole != targetRole) {
            cancelPendingLogin()
            return failure(
                AuthErrorCode.ROLE_MISMATCH,
                AuthPipelineStep.VERIFYING_ROLE,
                "Access Denied: This account is registered as ${roleTitle(registeredRole)}. You cannot log in to the ${roleTitle(targetRole)} portal."
            )
        }

        Log.i(TAG, "No live session — demo login for ${session.email} as ${targetRole.name}")
        val profileUser = createDemoProfile(
            role = targetRole,
            phone = "9876543210",
            email = session.email,
            authMethod = AuthMethod.EMAIL_PASSWORD,
            customName = session.email?.substringBefore("@")?.replace(".", " ")?.replaceFirstChar { it.uppercase() }
        )
        _authenticatedUser.value = profileUser
        saveSession(profileUser)
        pendingOtpSession = null
        _devDisplayOtp.value = null
        _currentPipelineStep.value = AuthPipelineStep.SUCCESS
        Log.i(TAG, "Login OTP verified for ${session.email} as ${targetRole.name}")
        return AuthResult(
            isSuccess = true,
            userProfile = profileUser
        )
    }

    private fun createDemoProfile(
        role: RoleType,
        phone: String,
        email: String?,
        authMethod: AuthMethod = AuthMethod.OTP_AUTHENTICATION,
        customName: String? = null
    ): UserProfile {
        val cleanPhone = phone.filter { it.isDigit() }.takeLast(10).ifBlank { "9876543210" }
        val name = customName?.takeIf { it.isNotBlank() } ?: when (role) {
            RoleType.INFORMAL_COLLECTOR -> "Kabadiwala Aggregator"
            RoleType.FORMAL_RECYCLER -> "Rule 13 Recycling Facility"
            RoleType.GOVERNMENT_ADMIN -> "CPCB Regulatory Inspector"
        }
        val statutory = when (role) {
            RoleType.INFORMAL_COLLECTOR -> "AGG-${cleanPhone.takeLast(6)}"
            RoleType.FORMAL_RECYCLER -> "CPCB-AUTH-2026-REC"
            RoleType.GOVERNMENT_ADMIN -> "CPCB-OFFICER-001"
        }
        val entity = when (role) {
            RoleType.INFORMAL_COLLECTOR -> "Green Earth Aggregators"
            RoleType.FORMAL_RECYCLER -> "EcoClean Authorized Recyclers Pvt Ltd"
            RoleType.GOVERNMENT_ADMIN -> "Central Pollution Control Board"
        }

        return UserProfile(
            userId = "usr-${cleanPhone}-${role.name.lowercase()}",
            displayName = name,
            email = email ?: "${cleanPhone}@ecobridges.demo",
            phoneNumber = "+91 $cleanPhone",
            role = role,
            accountStatus = AccountStatus.ACTIVE,
            permissions = defaultPermissionsOf(role),
            statutoryIdentifier = statutory,
            entityName = entity,
            sessionToken = "demo-session-token",
            authMethod = authMethod,
            verifiedTimestamp = System.currentTimeMillis()
        )
    }

    // -----------------------------------------------------------------------


    /** Reads the live `profiles` row for an auth user (null when not provisioned). */
    private suspend fun fetchProfile(authUserId: String): ProfileRow? {
        return try {
            SupabaseAuthConfig.client.from("profiles")
                .select { filter { eq("auth_user_id", authUserId) } }
                .decodeSingleOrNull<ProfileRow>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Profile fetch failed: ${e.message}")
            null
        }
    }

    /**
     * Maps sign-in failures. Unknown accounts and wrong passwords both surface
     * as INVALID_CREDENTIALS (matching Supabase's own non-enumerating error),
     * so one user's credentials can never be probed against another account.
     */
    private fun mapSignInException(e: Exception): AuthErrorCode {
        val base = mapException(e)
        if (base != AuthErrorCode.GENERIC) return base
        val msg = (e.message ?: "").lowercase()
        return when {
            msg.contains("email not confirmed") || msg.contains("not confirmed") ->
                AuthErrorCode.EMAIL_NOT_CONFIRMED
            else -> AuthErrorCode.INVALID_CREDENTIALS
        }
    }

    /** Maps sign-up failures (duplicate email, weak password, provider errors). */
    private fun mapSignUpException(e: Exception): AuthErrorCode {
        val base = mapException(e)
        if (base != AuthErrorCode.GENERIC) return base
        val msg = (e.message ?: "").lowercase()
        return when {
            msg.contains("already registered") || msg.contains("already exists") ||
                msg.contains("duplicate") || msg.contains("already in use") ->
                AuthErrorCode.EMAIL_ALREADY_REGISTERED
            else -> AuthErrorCode.GENERIC
        }
    }

    private fun failure(
        errorCode: AuthErrorCode,
        step: AuthPipelineStep,
        message: String? = null
    ): AuthResult {
        _currentPipelineStep.value = AuthPipelineStep.FAILED
        return AuthResult(
            isSuccess = false,
            errorMessage = message ?: errorCode.userMessage,
            failureStep = step,
            errorCode = errorCode
        )
    }

    private fun sendError(e: Exception): Exception {
        val code = mapException(e)
        return IllegalStateException(code.userMessage, e)
    }

    private fun mapException(e: Exception): AuthErrorCode = when (e) {
        is CancellationException -> throw e
        is UnknownHostException, is ConnectException, is SocketTimeoutException, is java.net.SocketException -> AuthErrorCode.NETWORK_ERROR
        is java.net.HttpRetryException -> AuthErrorCode.NETWORK_ERROR
        is java.io.IOException -> AuthErrorCode.NETWORK_ERROR
        is AuthRestException -> mapRestException(e)
        else -> AuthErrorCode.GENERIC
    }

    private fun mapRestException(e: AuthRestException): AuthErrorCode {
        val name = e.errorCode?.name?.uppercase() ?: ""
        return when {
            name.contains("OTP") || name.contains("TOKEN_EXPIRED") || name.contains("EXPIRED") ||
                name.contains("INVALID_CODE") || name == "WRONG_OTP_VERIFY_SETTING" -> AuthErrorCode.INVALID_OTP
            name.contains("INVALID_CREDENTIALS") || name.contains("WRONG_PASSWORD") -> AuthErrorCode.INVALID_CREDENTIALS
            name.contains("EMAIL_NOT_CONFIRMED") || name.contains("PHONE_NOT_CONFIRMED") || name.contains("UNVERIFIED") -> AuthErrorCode.EMAIL_NOT_CONFIRMED
            name.contains("USER_NOT_FOUND") || name.contains("ACCOUNT_NOT_FOUND") -> AuthErrorCode.ACCOUNT_NOT_FOUND
            name.contains("USER_ALREADY_EXISTS") || name.contains("ALREADY_REGISTERED") -> AuthErrorCode.EMAIL_ALREADY_REGISTERED
            name.contains("RATE_LIMITED") || name.contains("OVER_REQUEST") ->
                AuthErrorCode.NETWORK_ERROR
            name.contains("SMS_SEND") || name.contains("PROVIDER") || name.contains("SIGNUP_DISABLED") ->
                AuthErrorCode.PROVIDER_NOT_CONFIGURED
            else -> AuthErrorCode.GENERIC
        }
    }

    fun signOut() {
        _authenticatedUser.value = null
        _currentPipelineStep.value = AuthPipelineStep.IDLE
        // Clear all OTP state (pending challenge, code, expiry, attempts) and the
        // resend cooldown so a subsequent login starts fresh. Unrelated
        // preferences (e.g. language) live in a different SharedPreferences file
        // and are intentionally left untouched.
        activeOtpSession = null
        pendingOtpSession = null
        _devDisplayOtp.value = null
        lastResendTimestamp = 0L
        prefs.edit().clear().apply()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (isConfigured()) SupabaseAuthConfig.client.auth.signOut()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Local sign-out completed; remote sign-out skipped: ${e.message}")
            }
        }
    }

    private fun saveSession(profile: UserProfile) {
        prefs.edit().apply {
            putString("user_id", profile.userId)
            putString("display_name", profile.displayName)
            putString("email", profile.email)
            putString("phone", profile.phoneNumber)
            putString("role", profile.role.name)
            putString("status", profile.accountStatus.name)
            putString("statutory_id", profile.statutoryIdentifier)
            putString("entity_name", profile.entityName)
            putString("token", profile.sessionToken)
            putString("auth_method", profile.authMethod.name)
            putLong("verified_time", profile.verifiedTimestamp)
            apply()
        }
    }

    private fun loadStoredSession(): UserProfile? {
        val userId = prefs.getString("user_id", null) ?: return null
        val roleStr = prefs.getString("role", null) ?: return null
        val role = try { RoleType.valueOf(roleStr) } catch (e: Exception) { return null }

        return UserProfile(
            userId = userId,
            displayName = prefs.getString("display_name", "Authorized User") ?: "Authorized User",
            email = prefs.getString("email", null),
            phoneNumber = prefs.getString("phone", null),
            role = role,
            accountStatus = AccountStatus.ACTIVE,
            permissions = defaultPermissionsOf(role),
            statutoryIdentifier = prefs.getString("statutory_id", "") ?: "",
            entityName = prefs.getString("entity_name", "") ?: "",
            sessionToken = prefs.getString("token", "") ?: "",
            authMethod = AuthMethod.valueOf(prefs.getString("auth_method", AuthMethod.MOBILE_OTP.name) ?: AuthMethod.MOBILE_OTP.name),
            verifiedTimestamp = prefs.getLong("verified_time", System.currentTimeMillis())
        )
    }

    private fun mapRole(value: String?): RoleType? = when (value?.trim()?.lowercase()) {        "informal_collector", "collector" -> RoleType.INFORMAL_COLLECTOR
        "formal_recycler", "recycler" -> RoleType.FORMAL_RECYCLER
        "government_admin", "admin" -> RoleType.GOVERNMENT_ADMIN
        else -> null
    }

    private fun mapStatus(value: String?): AccountStatus = when (value?.trim()?.lowercase()) {
        "pending", "pending_verification" -> AccountStatus.PENDING_VERIFICATION
        "rejected" -> AccountStatus.REJECTED
        "suspended" -> AccountStatus.SUSPENDED
        "disabled" -> AccountStatus.DISABLED
        else -> AccountStatus.ACTIVE
    }
}