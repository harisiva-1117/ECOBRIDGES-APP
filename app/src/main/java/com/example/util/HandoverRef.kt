package com.example.util

/**
 * Formats the locally generated handover reference for display.
 *
 * Legacy rows created before the compliance fix may still contain a
 * government-sounding prefix ("EPR-CERT-MoEFCC-"). Strip it at render time so
 * the UI never implies that this app produces a government-issued certificate.
 */
object HandoverRef {
    private const val LEGACY_PREFIX = "EPR-CERT-MoEFCC-"

    fun display(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        return if (value.startsWith(LEGACY_PREFIX)) {
            "LOCAL-HANDOVER-" + value.removePrefix(LEGACY_PREFIX)
        } else {
            value
        }
    }
}
