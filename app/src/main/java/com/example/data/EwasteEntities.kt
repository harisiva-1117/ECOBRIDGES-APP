package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "material_lots")
data class MaterialLotEntity(
    @PrimaryKey val lotId: String,
    val categoryName: String,
    val subCategory: String,
    val weightKg: Double,
    val condition: String,
    val imageUri: String?,
    val estimatedValueInr: Double,
    val quotedRatePerKg: Double,
    val collectionTimestamp: Long,
    val collectionLocation: String,
    val gpsCoordinates: String,
    val matchedRecyclerId: String?,
    val matchedRecyclerName: String?,
    val statusName: String,
    val paymentModeName: String,
    val handoverReceiptNumber: String?,
    val recyclerConfirmed: Boolean,
    val eprCertificateNo: String?,
    val manifestDetails: String?,
    val isSynced: Boolean,
    /** Snapshot of the price actually agreed at handover. Never overwritten. */
    val agreedRatePerKg: Double? = null,
    val agreedAt: Long? = null
)

/**
 * Cached government/CPCB **indicative reference price**. Editable only by an
 * authenticated ACTIVE government admin (enforced by RLS server-side and by
 * [com.example.data.PriceManagementPolicy] on-device).
 */
@Entity(tableName = "price_records")
data class PriceRecordEntity(
    @PrimaryKey val id: String,
    val categoryName: String,
    val subCategory: String,
    val location: String,
    val prevailingBuyRate: Double,
    val marketMin: Double,
    val marketMax: Double,
    val trend: String,
    val trendPercentage: Double,
    val unit: String,
    val dateUpdated: String,
    val keyMetalsJoined: String,
    val grade: String = "",
    val currency: String = "INR",
    val isActive: Boolean = true,
    val effectiveDate: String? = null,
    val updatedAt: Long = 0L,
    val updatedBy: String = "",
    val version: Long = 1L
)

/**
 * A recycler's purchase offer for one material. Deliberately separate from
 * [PriceRecordEntity] (the government reference price). Only the owning
 * recycler may write an offer; the server resolves that owner from the CPCB
 * registry via `current_recycler_id()`.
 */
@Entity(tableName = "recycler_offers")
data class RecyclerOfferEntity(
    @PrimaryKey val offerId: String,
    val recyclerId: String,
    val recyclerName: String,
    val categoryName: String,
    val materialName: String,
    val grade: String,
    val pricePerKg: Double,
    val currency: String,
    val minQuantityKg: Double?,
    val maxQuantityKg: Double?,
    val condition: String,
    val status: String,
    val notes: String,
    val effectiveFrom: Long,
    val updatedAt: Long,
    val updatedBy: String,
    val version: Long
)

/** Immutable local copy of a reference-price change (server has its own trigger). */
@Entity(tableName = "price_history")
data class PriceHistoryEntity(
    @PrimaryKey(autoGenerate = true) val historyId: Long = 0L,
    val priceId: String,
    val materialName: String,
    val categoryName: String,
    val grade: String,
    val pricePerKg: Double,
    val marketMin: Double,
    val marketMax: Double,
    val currency: String,
    val changeType: String,
    val changedBy: String,
    val changedAt: Long,
    val version: Long
)

/**
 * Offline queue for price edits authored by an authorized user. [baseVersion]
 * is the server version visible when the edit was made, so synchronization can
 * refuse to overwrite a newer server row.
 */
@Entity(tableName = "price_change_queue")
data class PriceChangeQueueEntity(
    @PrimaryKey val queueId: String,
    val kind: String,
    val targetId: String,
    val actorUserId: String,
    val actorRole: String,
    val payloadJson: String,
    val baseVersion: Long,
    val status: String,
    val errorMessage: String?,
    val serverVersion: Long?,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "authorized_recyclers")
data class RecyclerEntity(
    @PrimaryKey val recyclerId: String,
    val name: String,
    val facilityLocation: String,
    val city: String,
    val distanceKm: Double,
    val cpcbRegNo: String,
    val authorizationValidity: String,
    val authorizationStatus: String = "active",
    val serviceArea: String = "",
    val phone: String,
    val contactEmail: String = "",
    val acceptedCategoriesJoined: String,
    val ratesJson: String,
    val doorstepPickup: Boolean,
    val minWeightForPickupKg: Double,
    val rating: Float,
    val latitude: Double = 19.0760,
    val longitude: Double = 72.8777
)

@Entity(tableName = "transaction_ledger")
data class TransactionLedgerEntity(
    @PrimaryKey val transactionId: String,
    val lotId: String,
    val categoryName: String,
    val weightKg: Double,
    val ratePerKg: Double,
    val totalAmountInr: Double,
    val paymentMode: String,
    val recyclerName: String,
    val timestamp: Long,
    val receiptNumber: String,
    val isSettled: Boolean
)

@Entity(tableName = "safety_guidelines")
data class SafetyGuidelineEntity(
    @PrimaryKey val id: String,
    val practiceTitle: String,
    val whyUnsafe: String,
    val whatIsLost: String,
    val safeFormalAlternative: String,
    val iconEmoji: String,
    val alertLevel: String
)

/** Photos attached to a lot. lotId == "DRAFT-<id>" while the lot is being
 *  authored, and is rewritten to the final lotId once the lot is created.
 *  uploadStatus is PENDING / UPLOADED / FAILED (offline-first queue). */
@Entity(tableName = "lot_photos")
data class LotPhotoEntity(
    @PrimaryKey val photoId: String,
    val lotId: String,
    val collectorUserId: String,
    val localUri: String,
    val remotePath: String?,
    val mimeType: String,
    val uploadStatus: String,
    val isPrimary: Boolean,
    val createdAt: Long
)

/** Collector's shared operating location (privacy-preserving: area + coarse
 *  coordinates only — never an exact house address). */
@Entity(tableName = "collector_locations")
data class CollectorLocationEntity(
    @PrimaryKey val collectorUserId: String,
    val latitude: Double,
    val longitude: Double,
    val areaLabel: String?,
    val isSharingOn: Boolean,
    val updatedAt: Long
)

/** Collector -> recycler connection request. Status: PENDING / ACCEPTED /
 *  REJECTED / BLOCKED / REPORTED. */
@Entity(tableName = "connection_requests")
data class ConnectionRequestEntity(
    @PrimaryKey val requestId: String,
    val collectorUserId: String,
    val recyclerId: String,
    val recyclerName: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
)

/** Quotation issued against a connection request / lot by a recycler.
 *  Status: PENDING / ACCEPTED / REJECTED. */
@Entity(tableName = "quotations")
data class QuotationEntity(
    @PrimaryKey val quotationId: String,
    val requestId: String,
    val recyclerId: String,
    val recyclerName: String,
    val lotId: String?,
    val quotedRatePerKg: Double,
    val quotedTotalInr: Double,
    val note: String,
    val status: String,
    val createdAt: Long,
    val respondedAt: Long?
)

/** Immutable audit trail for security-critical actions (block, report,
 *  handover confirmation). */
@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val action: String,
    val detailJson: String,
    val createdAt: Long
)
