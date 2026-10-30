package com.example.model

/**
 * A recycler's purchase offer for one material. This is deliberately separate
 * from [PriceRecord], which is the government/CPCB **indicative reference
 * price**. Only the owning recycler (resolved server-side) may edit an offer.
 */
data class RecyclerOffer(
    val offerId: String,
    val recyclerId: String,
    val recyclerName: String,
    val category: MaterialCategory,
    val materialName: String,
    val grade: String,
    val pricePerKg: Double,
    val currency: String = "INR",
    val minQuantityKg: Double? = null,
    val maxQuantityKg: Double? = null,
    val condition: String = "",
    val status: String = "ACTIVE",
    val notes: String = "",
    val effectiveFrom: Long = 0L,
    val updatedAt: Long = 0L,
    val updatedBy: String = "",
    val version: Long = 1L
) {
    val isActive: Boolean get() = status.equals("ACTIVE", ignoreCase = true)

    val quantityLabel: String
        get() = when {
            minQuantityKg != null && maxQuantityKg != null -> "${minQuantityKg.toInt()}–${maxQuantityKg.toInt()} kg"
            maxQuantityKg != null -> "up to ${maxQuantityKg.toInt()} kg"
            minQuantityKg != null -> "from ${minQuantityKg.toInt()} kg"
            else -> "no quantity limit"
        }
}

/** One immutable reference-price change recorded by the database trigger. */
data class PriceHistoryEntry(
    val historyId: Long,
    val priceId: String,
    val materialName: String,
    val category: MaterialCategory,
    val grade: String,
    val pricePerKg: Double,
    val marketMin: Double,
    val marketMax: Double,
    val currency: String,
    val changeType: String,
    val changedBy: String,
    val changedAt: Long,
    val version: Long
) {
    val isDeactivation: Boolean get() = changeType.equals("DEACTIVATE", ignoreCase = true)
}

/** Kinds of offline price changes that may be queued. */
enum class PriceChangeKind { REFERENCE_PRICE, RECYCLER_OFFER }

/** Queue state for an offline-authored change. */
enum class PriceChangeSyncStatus { PENDING, CONFLICT, FAILED }

/**
 * An offline change authored by an authorized user. [baseVersion] is the
 * server version that was visible when the change was made; synchronization
 * must refuse to overwrite a newer server row.
 */
data class PendingPriceChange(
    val queueId: String,
    val kind: PriceChangeKind,
    val targetId: String,
    val actorUserId: String,
    val actorRole: RoleType,
    val payloadJson: String,
    val baseVersion: Long,
    val status: PriceChangeSyncStatus = PriceChangeSyncStatus.PENDING,
    val errorMessage: String? = null,
    val serverVersion: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
