package com.example.data

import com.example.model.MaterialCategory
import com.example.model.PriceChangeKind
import com.example.model.RecyclerOffer
import com.example.model.RoleType
import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * Pure, dependency-free price-management rules.
 *
 * These functions are the single place where price edits are validated and
 * authorized on-device. The database enforces the same rules independently
 * (constraints + RLS in `0008_price_management.sql`), so a compromised client
 * cannot bypass them.
 */
object PriceManagementPolicy {

    /** Percentage move that requires an explicit confirmation dialog. */
    const val SIGNIFICANT_CHANGE_PERCENT = 20.0

    private val allowedCurrencies = setOf("INR")
    private val allowedOfferStatuses = setOf("DRAFT", "ACTIVE", "PAUSED", "EXPIRED")

    /** Only an upload/admin officer may edit government reference prices. */
    fun canManageReferencePrices(role: RoleType?): Boolean =
        role == RoleType.GOVERNMENT_ADMIN

    /** Only a formal recycler may author purchase offers. */
    fun canManageOffers(role: RoleType?): Boolean =
        role == RoleType.FORMAL_RECYCLER

    /**
     * An offer is owned by exactly one recycler. Comparison is
     * case-insensitive because the server resolves the id from the CPCB
     * registry while the client caches a display key.
     */
    fun ownsOffer(offerRecyclerId: String, actorRecyclerId: String): Boolean {
        if (offerRecyclerId.isBlank() || actorRecyclerId.isBlank()) return false
        return offerRecyclerId.trim().equals(actorRecyclerId.trim(), ignoreCase = true)
    }

    fun validateReferencePrice(input: ReferencePriceInput): PriceValidation {
        val errors = mutableListOf<String>()
        if (input.materialName.isBlank()) errors += "Material name is required."
        if (!input.pricePerKg.isFinite() || input.pricePerKg < 0.0) {
            errors += "Price per kg must be a non-negative number."
        }
        if (!input.marketMin.isFinite() || input.marketMin < 0.0) {
            errors += "Minimum price must be a non-negative number."
        }
        if (!input.marketMax.isFinite() || input.marketMax < 0.0) {
            errors += "Maximum price must be a non-negative number."
        }
        if (input.marketMin.isFinite() && input.marketMax.isFinite() &&
            input.marketMin > input.marketMax
        ) {
            errors += "Minimum price cannot be greater than the maximum price."
        }
        if (input.currency.uppercase() !in allowedCurrencies) {
            errors += "Only INR is supported."
        }
        if (input.materialName.length > 120) errors += "Material name is too long."
        if (input.grade.length > 60) errors += "Grade is too long."
        return PriceValidation(errors)
    }

    fun validateOffer(input: OfferInput): PriceValidation {
        val errors = mutableListOf<String>()
        if (input.materialName.isBlank()) errors += "Material name is required."
        if (!input.pricePerKg.isFinite() || input.pricePerKg <= 0.0) {
            errors += "Offered price per kg must be greater than zero."
        }
        val min = input.minQuantityKg
        val max = input.maxQuantityKg
        if (min != null && (!min.isFinite() || min < 0.0)) {
            errors += "Minimum quantity must be a non-negative number."
        }
        if (max != null && (!max.isFinite() || max < 0.0)) {
            errors += "Maximum quantity must be a non-negative number."
        }
        if (min != null && max != null && min > max) {
            errors += "Minimum quantity cannot be greater than the maximum quantity."
        }
        if (input.status.uppercase() !in allowedOfferStatuses) {
            errors += "Offer status must be DRAFT, ACTIVE, PAUSED or EXPIRED."
        }
        if (input.currency.uppercase() !in allowedCurrencies) {
            errors += "Only INR is supported."
        }
        return PriceValidation(errors)
    }

    /**
     * True when the change is large enough that the operator should confirm it.
     * A change from zero (new price) is never "significant".
     */
    fun isSignificantChange(
        oldPricePerKg: Double?,
        newPricePerKg: Double,
        thresholdPercent: Double = SIGNIFICANT_CHANGE_PERCENT
    ): Boolean {
        val old = oldPricePerKg ?: return false
        if (!old.isFinite() || !newPricePerKg.isFinite() || old <= 0.0) return false
        val percent = abs(newPricePerKg - old) / old * 100.0
        return percent >= thresholdPercent
    }

    /**
     * Optimistic concurrency check. Returns true when it is safe to send the
     * edit; false when the server row has moved on and the user must reload.
     */
    fun canApplyChange(baseVersion: Long, serverVersion: Long?): Boolean {
        if (serverVersion == null) {
            // The row no longer exists: only a create is safe.
            return baseVersion <= 0L
        }
        return baseVersion == serverVersion
    }

    /**
     * An already-agreed handover price is immutable. A proposed different value
     * must never silently replace it.
     */
    fun canChangeAgreedPrice(existingAgreedRate: Double?, proposedRate: Double): Boolean {
        val existing = existingAgreedRate ?: return true
        return existing == proposedRate
    }

    fun changeKindFor(role: RoleType): PriceChangeKind? = when (role) {
        RoleType.GOVERNMENT_ADMIN -> PriceChangeKind.REFERENCE_PRICE
        RoleType.FORMAL_RECYCLER -> PriceChangeKind.RECYCLER_OFFER
        RoleType.INFORMAL_COLLECTOR -> null
    }

    /** Human-readable owner label for an offer row. */
    fun offerOwnerLabel(offer: RecyclerOffer): String =
        offer.recyclerName.ifBlank { offer.recyclerId.ifBlank { "Unknown recycler" } }
}

/** Validation outcome; [isValid] is the only thing callers need to branch on. */
data class PriceValidation(val errors: List<String>) {
    val isValid: Boolean get() = errors.isEmpty()
    fun message(): String = errors.joinToString(" ")
}

@Serializable
data class ReferencePriceInput(
    val priceId: String,
    val category: MaterialCategory,
    val materialName: String,
    val grade: String = "",
    val location: String = "",
    val pricePerKg: Double,
    val marketMin: Double,
    val marketMax: Double,
    val currency: String = "INR",
    val effectiveDate: String? = null,
    val isActive: Boolean = true,
    val baseVersion: Long = 0L,
    val trend: String = "STABLE",
    val trendPercentage: Double = 0.0,
    val keyMetals: List<String> = emptyList(),
    val updatedByLabel: String = ""
)

@Serializable
data class OfferInput(
    val offerId: String,
    val recyclerId: String,
    val recyclerName: String,
    val category: MaterialCategory,
    val materialName: String,
    val grade: String = "",
    val pricePerKg: Double,
    val currency: String = "INR",
    val minQuantityKg: Double? = null,
    val maxQuantityKg: Double? = null,
    val condition: String = "",
    val status: String = "ACTIVE",
    val notes: String = "",
    val baseVersion: Long = 0L,
    val updatedByLabel: String = ""
)
