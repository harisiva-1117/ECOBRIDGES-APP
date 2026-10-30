package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.auth.OtpDeliveryDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom

/**
 * OTP helpers for **DEMO MODE only**.
 *
 * The app does not have an SMS gateway. Nothing here sends an SMS, an email or a
 * Google message. In `OTP_MODE=demo` the code is generated on-device and shown
 * in the app (and optionally in a local notification) so a reviewer can complete
 * the flow. Every label says "Demo OTP" so it can never be mistaken for real
 * out-of-band verification.
 *
 * For production, `OTP_MODE=sms` must be backed by a real server-side provider
 * (Supabase phone auth with Twilio/MessageBird, or an equivalent service). See
 * docs/SECURITY_REVIEW.md.
 */
data class OtpDeliveryResult(
    val otp: String,
    /** Always false: this build never transmits SMS. Kept for API compatibility. */
    val isSmsDispatched: Boolean = false,
    val isNotificationPosted: Boolean = false,
    val destinationType: OtpDeliveryDestination = OtpDeliveryDestination.ALL_CHANNELS,
    val formattedDestination: String = "",
    val detailMessage: String = ""
)

data class ReceivedOtpMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val destinationType: OtpDeliveryDestination,
    val destinationAddress: String,
    val otpCode: String,
    val title: String,
    val senderBadge: String,
    val previewSnippet: String,
    val timestamp: Long = System.currentTimeMillis()
)

object OtpManager {

    private const val TAG = "OtpManager"
    const val CHANNEL_ID_SMS = "ewaste_otp_notification_channel"
    const val CHANNEL_ID_EMAIL = "ewaste_email_notification_channel"
    const val CHANNEL_ID_GOOGLE = "ewaste_google_notification_channel"

    private const val NOTIFICATION_ID_SMS = 2026
    private const val NOTIFICATION_ID_EMAIL = 2027
    private const val NOTIFICATION_ID_GOOGLE = 2028

    private val secureRandom = SecureRandom()

    private val _activeDeliveries = MutableStateFlow<List<ReceivedOtpMessage>>(emptyList())
    val activeDeliveries: StateFlow<List<ReceivedOtpMessage>> = _activeDeliveries.asStateFlow()

    /** Cryptographically secure 6-digit numeric code. */
    fun generateOtp(): String = (secureRandom.nextInt(900_000) + 100_000).toString()

    fun clearDeliveries() {
        _activeDeliveries.value = emptyList()
    }

    /**
     * Records a Demo OTP for the given channel and (optionally) posts a local
     * notification. No message leaves the device.
     */
    fun dispatchMultiChannelOtp(
        context: Context,
        destination: OtpDeliveryDestination,
        phoneNumber: String?,
        email: String?,
        googleAccount: String?,
        otp: String
    ): List<OtpDeliveryResult> {
        val results = mutableListOf<OtpDeliveryResult>()
        val incomingMessages = mutableListOf<ReceivedOtpMessage>()

        val cleanPhone = phoneNumber?.filter { it.isDigit() }?.takeLast(10).orEmpty()
        val targetEmail = email?.trim()?.ifBlank { null }
        val targetGoogle = googleAccount?.trim()?.ifBlank { null }

        when (destination) {
            OtpDeliveryDestination.MOBILE_SMS -> {
                if (cleanPhone.length == 10) {
                    results.add(dispatchMobileOtp(context, cleanPhone, otp))
                    incomingMessages.add(
                        ReceivedOtpMessage(
                            destinationType = OtpDeliveryDestination.MOBILE_SMS,
                            destinationAddress = "+91 $cleanPhone",
                            otpCode = otp,
                            title = "Demo OTP (not sent by SMS)",
                            senderBadge = "Demo Mode",
                            previewSnippet = "Demo OTP for +91 $cleanPhone is $otp. This code was generated on-device and was NOT sent by SMS."
                        )
                    )
                }
            }
            OtpDeliveryDestination.REGISTERED_EMAIL -> {
                if (targetEmail != null) {
                    results.add(dispatchEmailOtp(context, targetEmail, otp))
                    incomingMessages.add(
                        ReceivedOtpMessage(
                            destinationType = OtpDeliveryDestination.REGISTERED_EMAIL,
                            destinationAddress = targetEmail,
                            otpCode = otp,
                            title = "Demo OTP (not sent by email)",
                            senderBadge = "Demo Mode",
                            previewSnippet = "Demo OTP for $targetEmail is $otp. No email was sent."
                        )
                    )
                }
            }
            OtpDeliveryDestination.GOOGLE_ACCOUNT -> {
                if (targetGoogle != null) {
                    results.add(dispatchGoogleOtp(context, targetGoogle, otp))
                    incomingMessages.add(
                        ReceivedOtpMessage(
                            destinationType = OtpDeliveryDestination.GOOGLE_ACCOUNT,
                            destinationAddress = targetGoogle,
                            otpCode = otp,
                            title = "Demo OTP (not sent to Google)",
                            senderBadge = "Demo Mode",
                            previewSnippet = "Demo OTP for $targetGoogle is $otp. No Google message was sent."
                        )
                    )
                }
            }
            OtpDeliveryDestination.ALL_CHANNELS -> {
                if (cleanPhone.length == 10) {
                    results.add(dispatchMobileOtp(context, cleanPhone, otp))
                }
                targetEmail?.let { results.add(dispatchEmailOtp(context, it, otp)) }
                targetGoogle?.let { results.add(dispatchGoogleOtp(context, it, otp)) }
                incomingMessages.add(
                    ReceivedOtpMessage(
                        destinationType = OtpDeliveryDestination.ALL_CHANNELS,
                        destinationAddress = listOfNotNull(
                            cleanPhone.takeIf { it.length == 10 }?.let { "+91 $it" },
                            targetEmail,
                            targetGoogle
                        ).joinToString(", "),
                        otpCode = otp,
                        title = "Demo OTP (local only)",
                        senderBadge = "Demo Mode",
                        previewSnippet = "Demo OTP: $otp. Generated on-device; no SMS/email was sent."
                    )
                )
            }
        }

        if (incomingMessages.isNotEmpty()) {
            _activeDeliveries.value = incomingMessages
        }
        return results
    }

    /** Backward-compatible helper for mobile-only demo dispatch. */
    fun dispatchOtp(
        context: Context,
        rawPhoneNumber: String,
        otp: String
    ): OtpDeliveryResult = dispatchMobileOtp(context, rawPhoneNumber, otp)

    fun dispatchMobileOtp(
        context: Context,
        rawPhoneNumber: String,
        otp: String
    ): OtpDeliveryResult {
        val cleanPhone = rawPhoneNumber.filter { it.isDigit() }
        val nationalPhone = if (cleanPhone.length >= 10) cleanPhone.takeLast(10) else cleanPhone
        val notificationPosted = postDemoNotification(
            context = context,
            channelId = CHANNEL_ID_SMS,
            notificationId = NOTIFICATION_ID_SMS,
            title = "Demo OTP for +91 $nationalPhone",
            body = "Code: $otp\nThis is a DEMO code generated on this device. No SMS was sent.",
            icon = android.R.drawable.ic_dialog_info
        )
        return OtpDeliveryResult(
            otp = otp,
            isSmsDispatched = false,
            isNotificationPosted = notificationPosted,
            destinationType = OtpDeliveryDestination.MOBILE_SMS,
            formattedDestination = "+91 $nationalPhone",
            detailMessage = "Demo mode: code displayed locally, no SMS transmitted."
        )
    }

    fun dispatchEmailOtp(
        context: Context,
        email: String,
        otp: String
    ): OtpDeliveryResult {
        val notificationPosted = postDemoNotification(
            context = context,
            channelId = CHANNEL_ID_EMAIL,
            notificationId = NOTIFICATION_ID_EMAIL,
            title = "Demo OTP for $email",
            body = "Code: $otp\nThis is a DEMO code generated on this device. No email was sent.",
            icon = android.R.drawable.ic_dialog_email
        )
        return OtpDeliveryResult(
            otp = otp,
            isSmsDispatched = false,
            isNotificationPosted = notificationPosted,
            destinationType = OtpDeliveryDestination.REGISTERED_EMAIL,
            formattedDestination = email,
            detailMessage = "Demo mode: code displayed locally, no email transmitted."
        )
    }

    fun dispatchGoogleOtp(
        context: Context,
        googleEmail: String,
        otp: String
    ): OtpDeliveryResult {
        val notificationPosted = postDemoNotification(
            context = context,
            channelId = CHANNEL_ID_GOOGLE,
            notificationId = NOTIFICATION_ID_GOOGLE,
            title = "Demo OTP for $googleEmail",
            body = "Code: $otp\nThis is a DEMO code generated on this device. No Google message was sent.",
            icon = android.R.drawable.ic_dialog_info
        )
        return OtpDeliveryResult(
            otp = otp,
            isSmsDispatched = false,
            isNotificationPosted = notificationPosted,
            destinationType = OtpDeliveryDestination.GOOGLE_ACCOUNT,
            formattedDestination = googleEmail,
            detailMessage = "Demo mode: code displayed locally, no Google message transmitted."
        )
    }

    private fun postDemoNotification(
        context: Context,
        channelId: String,
        notificationId: Int,
        title: String,
        body: String,
        icon: Int
    ): Boolean {
        return try {
            createNotificationChannels(context)
            if (!canPostNotification(context)) {
                return false
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                Intent(Intent.ACTION_VIEW).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(body.lineSequence().first())
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not post demo OTP notification: ${e.message}")
            false
        }
    }

    private fun canPostNotification(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    private fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return
            listOf(
                Triple(CHANNEL_ID_SMS, "Demo OTP (mobile)", "Local demo codes, not SMS"),
                Triple(CHANNEL_ID_EMAIL, "Demo OTP (email)", "Local demo codes, not email"),
                Triple(CHANNEL_ID_GOOGLE, "Demo OTP (Google)", "Local demo codes, not Google")
            ).forEach { (id, name, description) ->
                notificationManager.createNotificationChannel(
                    NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
                        this.description = description
                        enableVibration(true)
                    }
                )
            }
        }
    }

    /** Copies the Demo OTP to the clipboard. */
    fun copyOtpToClipboard(context: Context, otp: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("Demo OTP", otp)
        clipboard?.setPrimaryClip(clip)
    }
}
