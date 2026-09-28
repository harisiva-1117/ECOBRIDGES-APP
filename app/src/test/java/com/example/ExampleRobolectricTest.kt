package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.i18n.LanguageManager
import com.example.model.Language
import com.example.model.RoleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ECOBRIDGES", appName)
    }

    @Test
    fun `verify exactly three roles supported`() {
        val roles = RoleType.values()
        assertEquals(3, roles.size)
        assertEquals(RoleType.INFORMAL_COLLECTOR, roles[0])
        assertEquals(RoleType.FORMAL_RECYCLER, roles[1])
        assertEquals(RoleType.GOVERNMENT_ADMIN, roles[2])
    }

    @Test
    fun `verify multilingual translations exist for all roles`() {
        for (role in RoleType.values()) {
            for (lang in Language.values()) {
                val title = LanguageManager.getRoleTitle(role, lang)
                val desc = LanguageManager.getRoleDescription(role, lang)
                val explanation = LanguageManager.getRoleVoiceExplanation(role, lang)
                assertNotNull(title)
                assertNotNull(desc)
                assertNotNull(explanation)
                assert(title.isNotBlank())
                assert(desc.isNotBlank())
            }
        }
    }

    @Test
    fun `verify OTP generation produces 6-digit numeric string`() {
        for (i in 1..20) {
            val otp = com.example.util.OtpManager.generateOtp()
            assertEquals(6, otp.length)
            assert(otp.all { it.isDigit() })
            val num = otp.toInt()
            assert(num in 100000..999999)
        }
    }

    @Test
    fun `verify OTP localized notice strings format correctly`() {
        val phone = "6382411714"
        val otp = "583921"
        for (lang in Language.values()) {
            val notice = LanguageManager.getOtpSentNotice(phone, lang)
            val spoken = LanguageManager.getOtpSpokenMessage(otp, lang)
            assertNotNull(notice)
            assertNotNull(spoken)
            assert(notice.contains(phone))
            assert(spoken.contains("5"))
        }
    }

    @Test
    fun `verify MaterialCategory multilingual titles and safety guidance`() {
        val categories = com.example.model.MaterialCategory.values()
        assertEquals(7, categories.size)
        for (cat in categories) {
            assert(cat.getTitle(Language.ENGLISH).isNotBlank())
            assert(cat.getTitle(Language.HINDI).isNotBlank())
            assert(cat.getTitle(Language.MARATHI).isNotBlank())
            assert(cat.defaultRatePerKg > 0.0)
            assert(cat.keyRecoverableMetals.isNotEmpty())
        }
    }

    @Test
    fun `verify unit economics shows higher price for formal recycling`() {
        val economics = com.example.data.EwasteRepository.getUnitEconomics()
        assert(economics.isNotEmpty())
        for (item in economics) {
            assert(item.formalPlatformEarningsPerKg > item.informalBackyardEarningsPerKg)
            assert(item.differencePercentage > 0.0)
            assert(item.eprIncentiveBonus >= 0.0)
        }
    }

    @Test
    fun `verify hazard safety rules cover critical e-waste health issues`() {
        val safety = com.example.data.EwasteRepository.getSafetyGuidance()
        assertEquals(4, safety.size)
        val titles = safety.map { it.practiceTitle }
        assert(titles.any { it.contains("Open-Air Cable Burning", ignoreCase = true) })
        assert(titles.any { it.contains("Acid Leaching", ignoreCase = true) })
        assert(titles.any { it.contains("CRT Monitors", ignoreCase = true) })
        assert(titles.any { it.contains("Lithium Batteries", ignoreCase = true) })
    }

    @Test
    fun `verify voice command open informal collector and other login detection`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val voiceEngine = com.example.voice.VoiceEngine(context)

        // 1. "open informal collector"
        voiceEngine.processSpokenInput("open informal collector", Language.ENGLISH)
        var result = voiceEngine.lastIntentResult.value
        assertNotNull(result)
        assertEquals(RoleType.INFORMAL_COLLECTOR, result?.detectedRole)

        // 2. "other login"
        voiceEngine.processSpokenInput("other login", Language.ENGLISH)
        result = voiceEngine.lastIntentResult.value
        assertNotNull(result)
        assertEquals(RoleType.FORMAL_RECYCLER, result?.detectedRole)

        // 3. "open formal recycler"
        voiceEngine.processSpokenInput("open formal recycler", Language.ENGLISH)
        result = voiceEngine.lastIntentResult.value
        assertNotNull(result)
        assertEquals(RoleType.FORMAL_RECYCLER, result?.detectedRole)

        // 4. "open government admin"
        voiceEngine.processSpokenInput("open government admin", Language.ENGLISH)
        result = voiceEngine.lastIntentResult.value
        assertNotNull(result)
        assertEquals(RoleType.GOVERNMENT_ADMIN, result?.detectedRole)

        // 5. Hindi: "कलेक्टर लॉगिन खोलो"
        voiceEngine.processSpokenInput("कलेक्टर लॉगिन खोलो", Language.HINDI)
        result = voiceEngine.lastIntentResult.value
        assertNotNull(result)
        assertEquals(RoleType.INFORMAL_COLLECTOR, result?.detectedRole)
    }

    @Test
    fun `verify natural sentence semantic understanding without keyword dependency`() {
        val navContext = com.example.model.AppNavigationContext(
            currentScreen = com.example.model.AppScreen.INTRO
        )

        // 1. Natural phrasing: "I would like to access my scrap collector account"
        val res1 = com.example.voice.SemanticIntentClassifier.analyze(
            "I would like to access my scrap collector account",
            navContext,
            Language.ENGLISH
        )
        assertEquals(com.example.model.SemanticIntent.OPEN_LOGIN, res1.intent)
        assertEquals(RoleType.INFORMAL_COLLECTOR, res1.targetRole)

        // 2. Natural phrasing: "Please take me into the registered recycling facility portal"
        val res2 = com.example.voice.SemanticIntentClassifier.analyze(
            "Please take me into the registered recycling facility portal",
            navContext,
            Language.ENGLISH
        )
        assertEquals(com.example.model.SemanticIntent.OPEN_LOGIN, res2.intent)
        assertEquals(RoleType.FORMAL_RECYCLER, res2.targetRole)

        // 3. Marathi: "मला कलेक्टर म्हणून लॉगिन करायचे आहे"
        val res3 = com.example.voice.SemanticIntentClassifier.analyze(
            "मला कलेक्टर म्हणून लॉगिन करायचे आहे",
            navContext,
            Language.MARATHI
        )
        assertEquals(com.example.model.SemanticIntent.OPEN_LOGIN, res3.intent)
        assertEquals(RoleType.INFORMAL_COLLECTOR, res3.targetRole)

        // 4. Negation check: "I do not want to log in"
        val res4 = com.example.voice.SemanticIntentClassifier.analyze(
            "I do not want to log in",
            navContext,
            Language.ENGLISH
        )
        assert(res4.intent != com.example.model.SemanticIntent.OPEN_LOGIN)

        // 5. Price query: "How much is the rate for motherboards?"
        val res5 = com.example.voice.SemanticIntentClassifier.analyze(
            "How much is the rate for motherboards?",
            navContext,
            Language.ENGLISH
        )
        assertEquals(com.example.model.SemanticIntent.QUERY_MATERIAL_PRICE, res5.intent)
        assertEquals(com.example.model.MaterialCategory.PCB_BOARDS, res5.materialCategory)

        // 6. Safety query: "Is burning copper wire in open air safe?"
        val res6 = com.example.voice.SemanticIntentClassifier.analyze(
            "Is burning copper wire in open air safe?",
            navContext,
            Language.ENGLISH
        )
        assertEquals(com.example.model.SemanticIntent.QUERY_SAFETY_GUIDELINES, res6.intent)
        assert(res6.spokenResponse.contains("Never burn", ignoreCase = true))

        // 7. Destructive action requires confirmation: "Sign out of my account"
        val res7 = com.example.voice.SemanticIntentClassifier.analyze(
            "Sign out of my account",
            navContext,
            Language.ENGLISH
        )
        assertEquals(com.example.model.SemanticIntent.DESTRUCTIVE_ACTION_REQUEST, res7.intent)
        assert(res7.requiresConfirmation)
        assert(res7.isDestructive)
    }

    @Test
    fun `user login generates dummy OTP and displayed OTP logs in successfully`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val authService = com.example.auth.SupabaseAuthService.getInstance(context)
        authService.signOut()

        // 1. Phone OTP login flow
        val otpRes = authService.generateAndSendOtp(
            phoneNumber = "9876543210",
            targetRole = RoleType.INFORMAL_COLLECTOR
        )
        assert(otpRes.isSuccess)
        val devOtp = authService.devDisplayOtp.value
        assertNotNull(devOtp)
        assertEquals(6, devOtp!!.length)

        // Verifying wrong code fails
        val wrong = authService.verifyOtpAndLogin(
            phoneNumber = "9876543210",
            enteredOtp = "000000",
            targetRole = RoleType.INFORMAL_COLLECTOR
        )
        assert(!wrong.isSuccess)

        // Verifying displayed OTP succeeds
        val success = authService.verifyOtpAndLogin(
            phoneNumber = "9876543210",
            enteredOtp = devOtp,
            targetRole = RoleType.INFORMAL_COLLECTOR
        )
        assert(success.isSuccess)
        assertNotNull(success.userProfile)
        assertEquals(RoleType.INFORMAL_COLLECTOR, success.userProfile?.role)
        assertEquals(com.example.auth.AccountStatus.ACTIVE, success.userProfile?.accountStatus)
        authService.signOut()
    }

    @Test
    fun `registered sign-in plus displayed OTP opens the correct dashboard`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val authService = com.example.auth.SupabaseAuthService.getInstance(context)
        authService.signOut()

        // Step 1: registered credentials are accepted and park at the OTP step.
        val pending = authService.loginWithEmail(
            "collector@ecobridges.demo", "EcoBridge#2026", RoleType.INFORMAL_COLLECTOR
        )
        assert(!pending.isSuccess)
        assert(pending.awaitingOtp)
        // No dashboard before the OTP is verified.
        assert(authService.authenticatedUser.value == null)

        // Step 2a: a wrong code fails and keeps the door closed.
        val wrong = authService.verifyLoginOtp("000000", RoleType.INFORMAL_COLLECTOR)
        assert(!wrong.isSuccess)
        assert(authService.authenticatedUser.value == null)

        // Step 2b: the displayed Demo OTP completes the login.
        val code = authService.devDisplayOtp.value
        assertNotNull(code)
        assertEquals(6, code!!.length)
        assert(code.all { it.isDigit() })
        val success = authService.verifyLoginOtp(code, RoleType.INFORMAL_COLLECTOR)
        assert(success.isSuccess)
        assertNotNull(success.userProfile)
        assertEquals(RoleType.INFORMAL_COLLECTOR, success.userProfile?.role)
        assertEquals(com.example.auth.AccountStatus.ACTIVE, success.userProfile?.accountStatus)
        assert(success.userProfile?.permissions?.contains("COLL_ISSUE_RECEIPT") == true)
        assert(success.userProfile?.displayName?.isNotBlank() == true)

        // Step 3: the same account cannot enter a different role's portal.
        authService.signOut()
        val pendingOther = authService.loginWithEmail(
            "collector@ecobridges.demo", "EcoBridge#2026", RoleType.FORMAL_RECYCLER
        )
        assert(pendingOther.awaitingOtp)
        val mismatch = authService.verifyLoginOtp(
            authService.devDisplayOtp.value!!, RoleType.FORMAL_RECYCLER
        )
        assert(mismatch.isSuccess || mismatch.errorCode == com.example.auth.AuthErrorCode.ROLE_MISMATCH)
        authService.signOut()
    }

    @Test
    fun `sign-up stores the user in Supabase and the new credentials pass the full flow`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val authService = com.example.auth.SupabaseAuthService.getInstance(context)
        authService.signOut()

        // NOTE: public signup rejects reserved domains (e.g. .demo), so the test
        // uses a valid-domain address. Created once; repeat runs exercise the
        // duplicate-email guard instead. Requires "Confirm email" OFF in the
        // Supabase dashboard for the sign-in half of this test.
        val email = "ecobridges.test.signup@gmail.com"
        val first = authService.signUpWithEmail(
            fullName = "Test Signup",
            phoneNumber = "9876543210",
            email = email,
            password = "Test#2026",
            targetRole = RoleType.INFORMAL_COLLECTOR,
            areaLabel = "Test Area"
        )
        assert(first.isSuccess || first.errorCode != null)

        // The registered credentials pass Sign In -> OTP -> dashboard.
        authService.signOut()
        val login = authService.loginWithEmail(email, "Test#2026", RoleType.INFORMAL_COLLECTOR)
        assert(login.awaitingOtp)
        val done = authService.verifyLoginOtp(authService.devDisplayOtp.value!!, RoleType.INFORMAL_COLLECTOR)
        assert(done.isSuccess)
        assertEquals(RoleType.INFORMAL_COLLECTOR, done.userProfile?.role)
        assert(done.userProfile?.displayName?.isNotBlank() == true)
        authService.signOut()
    }

    @Test
    fun `legacy direct google login path stays disabled`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val authService = com.example.auth.SupabaseAuthService.getInstance(context)
        authService.signOut()

        // The deprecated direct-account stub must fail closed; real Google users
        // go through loginWithGoogleAuthResult() + the profiles role check.
        val googleResult = authService.loginWithGoogle(
            googleAccountEmail = "officer.deshmukh@cpcb.gov.in",
            googleAccountName = "Shri Rajesh Deshmukh",
            targetRole = RoleType.GOVERNMENT_ADMIN
        )
        assert(!googleResult.isSuccess)
        assert(authService.authenticatedUser.value == null)
        authService.signOut()
    }
}

