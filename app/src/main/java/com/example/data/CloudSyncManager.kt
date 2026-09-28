package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.auth.ProfileRow
import com.example.auth.SupabaseAuthConfig
import com.example.model.LotStatus
import com.example.model.MaterialCategory
import com.example.model.MaterialLot
import com.example.model.PaymentMode
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import kotlin.math.min
import kotlin.math.pow

/**
 * Pushes offline-created lots and their ledger transactions to the Supabase
 * `collector_lots` and `collector_transactions` tables (created by
 * supabase/schema.sql). The push is idempotent (PostgREST upsert keyed on
 * primary key). If the tables have not been provisioned yet, the sync fails
 * softly so the app keeps working fully offline-first.
 */
object CloudSyncManager {

    private const val TAG = "CloudSyncManager"

    @Serializable
    data class CollectorLotDto(
        val lot_id: String,
        val collector_user_id: String,
        val category_name: String,
        val sub_category: String,
        val weight_kg: Double,
        val condition: String,
        val estimated_value_inr: Double,
        val quoted_rate_per_kg: Double,
        val collection_timestamp: Long,
        val collection_location: String,
        val gps_coordinates: String,
        val matched_recycler_id: String? = null,
        val matched_recycler_name: String? = null,
        val status_name: String,
        val payment_mode: String,
        val handover_receipt_number: String? = null,
        val recycler_confirmed: Boolean,
        val epr_certificate_no: String? = null,
        val manifest_details: String? = null
    )

    @Serializable
    data class CollectorTxnDto(
        val transaction_id: String,
        val lot_id: String,
        val collector_user_id: String,
        val category_name: String,
        val weight_kg: Double,
        val rate_per_kg: Double,
        val total_amount_inr: Double,
        val payment_mode: String,
        val recycler_name: String,
        val timestamp: Long,
        val receipt_number: String,
        val is_settled: Boolean
    )

    @Serializable
    data class LotPhotoDto(
        val photo_id: String,
        val lot_id: String,
        val collector_user_id: String,
        val remote_path: String?,
        val mime_type: String,
        val upload_status: String,
        val is_primary: Boolean,
        val created_at: Long
    )

    @Serializable
    data class CollectorLocationDto(
        val collector_user_id: String,
        val latitude: Double,
        val longitude: Double,
        val area_label: String? = null,
        val is_sharing_on: Boolean,
        val updated_at: Long
    )

    @Serializable
    data class ConnectionRequestDto(
        val request_id: String,
        val collector_user_id: String,
        val recycler_id: String,
        val recycler_name: String? = null,
        val status: String,
        val created_at: Long,
        val updated_at: Long
    )

    @Serializable
    data class QuotationDto(
        val quotation_id: String,
        val request_id: String,
        val recycler_id: String,
        val recycler_name: String? = null,
        val lot_id: String? = null,
        val collector_user_id: String,
        val quoted_rate_per_kg: Double,
        val quoted_total_inr: Double,
        val note: String? = null,
        val status: String,
        val created_at: Long,
        val responded_at: Long? = null
    )

    @Serializable
    data class AuditLogDto(
        val id: String,
        val user_id: String,
        val action: String,
        val detail_json: String? = null,
        val created_at: Long
    )

    /** Remote view of a shared collector's operating area. Privacy-preserving:
     *  only area label + coarse coordinates — never address or phone. */
    @Serializable
    data class RemoteCollector(
        val collector_user_id: String,
        val latitude: Double,
        val longitude: Double,
        val area_label: String? = null,
        val is_sharing_on: Boolean = true
    )

    /** Public registry row for authorized recyclers (government dataset). */
    @Serializable
    data class RemoteRecycler(
        val recycler_id: String,
        val name: String,
        val facility_location: String? = null,
        val city: String? = null,
        val distance_km: Double = 0.0,
        val cpcb_reg_no: String? = null,
        val authorization_validity: String? = null,
        val authorization_status: String? = null,
        val service_area: String? = null,
        val phone: String? = null,
        val contact_email: String? = null,
        val accepted_categories: String? = null,
        val doorstep_pickup: Boolean = false,
        val min_weight_for_pickup_kg: Double = 0.0,
        val rating: Double = 0.0,
        val latitude: Double = 0.0,
        val longitude: Double = 0.0
    )

    /** Offered rate per accepted material from `recycler_offered_rates`. */
    @Serializable
    data class RecyclerRateDto(
        val rate_id: String,
        val recycler_id: String,
        val category_name: String,
        val rate_per_kg: Double = 0.0,
        val unit: String = "₹/kg"
    )

    /** Aggregated national metrics from the `v_admin_metrics` role view. */
    @Serializable
    data class AdminMetricsDto(
        val collector_count: Long = 0,
        val recycler_count: Long = 0,
        val admin_count: Long = 0,
        val lot_count: Long = 0,
        val pending_lot_count: Long = 0,
        val confirmed_lot_count: Long = 0,
        val total_weight_kg: Double = 0.0,
        val settled_value_inr: Double = 0.0
    )

    /** Read-only market reference row (single source for all roles). */
    @Serializable
    data class PriceDto(
        val price_id: String,
        val category_name: String,
        val sub_category: String? = null,
        val location: String? = null,
        val prevailing_buy_rate: Double = 0.0,
        val market_min: Double = 0.0,
        val market_max: Double = 0.0,
        val trend: String? = null,
        val trend_percentage: Double = 0.0,
        val unit: String = "₹/kg",
        val date_updated: String? = null,
        val key_metals_joined: String? = null
    )

    /** Read-only hazard-safety reference row (single source for all roles). */
    @Serializable
    data class SafetyGuidelineDto(
        val guideline_id: String,
        val practice_title: String,
        val why_unsafe: String? = null,
        val what_is_lost: String? = null,
        val safe_formal_alternative: String? = null,
        val icon_emoji: String? = null,
        val alert_level: String? = null
    )

    data class SyncOutcome(val lotsPushed: Int, val transactionsPushed: Int, val serverReachable: Boolean)

    suspend fun syncLotsAndTransactions(
        lots: List<MaterialLotEntity>,
        transactions: List<TransactionLedgerEntity>,
        userId: String
    ): SyncOutcome {
        if (!SupabaseAuthConfig.isConfigured()) {
            return SyncOutcome(0, 0, serverReachable = false)
        }
        // RLS ties every lot/transaction row to the signed-in Supabase user
        // (auth.uid() = collector_user_id). When the device holds a real GoTrue
        // session (email/Google login), stamp its uid so rows land under the
        // registered profile and surface in the admin directory. Otherwise keep
        // the local id (offline/demo) — the push then fails softly by design.
        val sessionUid = runCatching {
            SupabaseAuthConfig.client.auth.currentUserOrNull()?.id
        }.getOrNull()?.ifBlank { null }
        val effectiveUserId = sessionUid ?: userId
        try {
            if (lots.isNotEmpty()) {
                val lotDtos = lots.map { it.toSyncDto(effectiveUserId) }
                SupabaseAuthConfig.client.from("collector_lots")
                    .upsert(lotDtos) { onConflict = "lot_id" }
            }
            if (transactions.isNotEmpty()) {
                val txnDtos = transactions.map { it.toSyncDto(effectiveUserId) }
                SupabaseAuthConfig.client.from("collector_transactions")
                    .upsert(txnDtos) { onConflict = "transaction_id" }
            }
            Log.i(TAG, "Synced ${lots.size} lots and ${transactions.size} transactions for $userId")
            return SyncOutcome(lots.size, transactions.size, serverReachable = true)
        } catch (e: Exception) {
            Log.w(TAG, "Cloud sync skipped (offline or table not provisioned): ${e.message}")
            return SyncOutcome(0, 0, serverReachable = false)
        }
    }

    /** Offline-first photo upload queue. Reads + compresses the captured photo,
     *  uploads it to the private `lot-photos` bucket under {userId}/{photoId}.jpg
     *  and reports per-photo status. Never fabricates a success. */
    suspend fun uploadLotPhoto(
        context: Context,
        photo: LotPhotoEntity,
        userId: String,
        onStatus: (String, String?) -> Unit = { _, _ -> }
    ): Boolean {
        if (!SupabaseAuthConfig.isConfigured()) {
            onStatus("FAILED", null)
            return false
        }
        return try {
            val bytes = readCompressedBytes(context, photo.localUri)
                ?: run {
                    Log.w(TAG, "Photo bytes unavailable for ${photo.photoId}; skipping upload")
                    onStatus("FAILED", null)
                    return false
                }
            val remotePath = "$userId/${photo.photoId}.jpg"
            SupabaseAuthConfig.client.storage.from("lot-photos")
                .upload(remotePath, bytes)
            onStatus("UPLOADED", remotePath)
            Log.i(TAG, "Uploaded photo ${photo.photoId} -> $remotePath")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Photo upload failed for ${photo.photoId}: ${e.message}")
            onStatus("FAILED", null)
            false
        }
    }

    suspend fun pushCollectorLocation(location: CollectorLocationEntity) {
        if (!SupabaseAuthConfig.isConfigured()) return
        try {
            SupabaseAuthConfig.client.from("collector_locations")
                .upsert(location.toDto()) { onConflict = "collector_user_id" }
        } catch (e: Exception) {
            Log.w(TAG, "Location push skipped: ${e.message}")
        }
    }

    suspend fun pushConnectionRequests(userId: String, requests: List<ConnectionRequestEntity>) {
        if (!SupabaseAuthConfig.isConfigured() || requests.isEmpty()) return
        try {
            val dtos = requests.map { it.toDto(userId) }
            SupabaseAuthConfig.client.from("connection_requests")
                .upsert(dtos) { onConflict = "request_id" }
        } catch (e: Exception) {
            Log.w(TAG, "Connection push skipped: ${e.message}")
        }
    }

    suspend fun pushQuotations(userId: String, quotations: List<QuotationEntity>) {
        if (!SupabaseAuthConfig.isConfigured() || quotations.isEmpty()) return
        try {
            val dtos = quotations.map { it.toDto(userId) }
            SupabaseAuthConfig.client.from("quotations")
                .upsert(dtos) { onConflict = "quotation_id" }
        } catch (e: Exception) {
            Log.w(TAG, "Quotation push skipped: ${e.message}")
        }
    }

    suspend fun pushAuditLogs(userId: String, logs: List<AuditLogEntity>) {
        if (!SupabaseAuthConfig.isConfigured() || logs.isEmpty()) return
        try {
            val dtos = logs.map { it.toDto(userId) }
            SupabaseAuthConfig.client.from("audit_logs")
                .upsert(dtos) { onConflict = "id" }
        } catch (e: Exception) {
            Log.w(TAG, "Audit push skipped: ${e.message}")
        }
    }

    /** Fetch other collectors currently sharing their (privacy-preserving)
     *  operating area. Returns rows already filtered to `is_sharing_on = true`;
     *  distance targeting is computed by the caller. */
    suspend fun fetchNearbyCollectors(): List<RemoteCollector> {
        if (!SupabaseAuthConfig.isConfigured()) return emptyList()
        return try {
            SupabaseAuthConfig.client.from("collector_locations")
                .select { filter { eq("is_sharing_on", true) } }
                .decodeList<RemoteCollector>()
        } catch (e: Exception) {
            Log.w(TAG, "Nearby collectors fetch failed: ${e.message}")
            emptyList()
        }
    }

    /** Fetch the offered-rates table. Public read; keyed by recycler. */
    suspend fun fetchRecyclerOfferedRates(): List<RecyclerRateDto> {
        if (!SupabaseAuthConfig.isConfigured()) return emptyList()
        return try {
            SupabaseAuthConfig.client.from("recycler_offered_rates")
                .select()
                .decodeList<RecyclerRateDto>()
        } catch (e: Exception) {
            Log.w(TAG, "Offered rates fetch failed: ${e.message}")
            emptyList()
        }
    }

    /** Fetch the public CPCB/SPCB authorized-recycler registry. The Supabase
     *  table is the single source of truth; the UI falls back to a static row
     *  only when the device is offline. */
    suspend fun fetchAuthorizedRecyclers(): List<RemoteRecycler> {
        if (!SupabaseAuthConfig.isConfigured()) return emptyList()
        return try {
            SupabaseAuthConfig.client.from("authorized_recyclers")
                .select()
                .decodeList<RemoteRecycler>()
        } catch (e: Exception) {
            Log.w(TAG, "Registry fetch failed: ${e.message}")
            emptyList()
        }
    }

    // ---- Shared source-of-truth reads (role views + RLS) -------------------

    /** Collector's own lots via `v_collector_my_lots`. */
    suspend fun fetchCollectorLots(userId: String): List<CollectorLotDto> {
        if (!SupabaseAuthConfig.isConfigured()) return emptyList()
        return try {
            SupabaseAuthConfig.client.from("v_collector_my_lots")
                .select { filter { eq("collector_user_id", userId) } }
                .decodeList<CollectorLotDto>()
        } catch (e: Exception) {
            Log.w(TAG, "Collector lots fetch failed: ${e.message}")
            emptyList()
        }
    }

    /** Collector's own ledger rows via `v_collector_my_transactions`. */
    suspend fun fetchCollectorTransactions(userId: String): List<CollectorTxnDto> {
        if (!SupabaseAuthConfig.isConfigured()) return emptyList()
        return try {
            SupabaseAuthConfig.client.from("v_collector_my_transactions")
                .select { filter { eq("collector_user_id", userId) } }
                .decodeList<CollectorTxnDto>()
        } catch (e: Exception) {
            Log.w(TAG, "Collector transactions fetch failed: ${e.message}")
            emptyList()
        }
    }

    /** Formal recycler inbound queue via `v_recycler_inbound_lots` (RLS grants
     *  the formal-recycler role read access to every collector's lots). */
    suspend fun fetchRecyclerInboundLots(): List<CollectorLotDto> {
        if (!SupabaseAuthConfig.isConfigured()) return emptyList()
        return try {
            SupabaseAuthConfig.client.from("v_recycler_inbound_lots")
                .select()
                .decodeList<CollectorLotDto>()
        } catch (e: Exception) {
            Log.w(TAG, "Recycler inbound fetch failed: ${e.message}")
            emptyList()
        }
    }

    /** Government admin read of the national lot registry. */
    suspend fun fetchAdminLots(): List<CollectorLotDto> {
        if (!SupabaseAuthConfig.isConfigured()) return emptyList()
        return try {
            SupabaseAuthConfig.client.from("v_admin_lots")
                .select()
                .decodeList<CollectorLotDto>()
        } catch (e: Exception) {
            Log.w(TAG, "Admin lots fetch failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun fetchAdminMetrics(): AdminMetricsDto? {
        if (!SupabaseAuthConfig.isConfigured()) return null
        return try {
            SupabaseAuthConfig.client.from("v_admin_metrics")
                .select()
                .decodeSingleOrNull<AdminMetricsDto>()
        } catch (e: Exception) {
            Log.w(TAG, "Admin metrics fetch failed: ${e.message}")
            null
        }
    }

    /** Government directory: every `profiles` row for one role
     *  (`informal_collector` / `formal_recycler`), newest last. Requires an
     *  authenticated admin session (RLS `government_admin` check); returns an
     *  empty list otherwise. Never fabricated. */
    suspend fun fetchProfilesByRole(role: String): List<ProfileRow> {
        if (!SupabaseAuthConfig.isConfigured()) return emptyList()
        return try {
            SupabaseAuthConfig.client.from("profiles")
                .select { filter { eq("role", role) } }
                .decodeList<ProfileRow>()
        } catch (e: Exception) {
            Log.w(TAG, "Directory fetch failed for $role: ${e.message}")
            emptyList()
        }
    }

    suspend fun fetchMaterialPrices(): List<PriceDto> {
        if (!SupabaseAuthConfig.isConfigured()) return emptyList()
        return try {
            SupabaseAuthConfig.client.from("material_prices").select().decodeList<PriceDto>()
        } catch (e: Exception) {
            Log.w(TAG, "Material prices fetch failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun fetchSafetyGuidelines(): List<SafetyGuidelineDto> {
        if (!SupabaseAuthConfig.isConfigured()) return emptyList()
        return try {
            SupabaseAuthConfig.client.from("safety_guidelines").select().decodeList<SafetyGuidelineDto>()
        } catch (e: Exception) {
            Log.w(TAG, "Safety guidelines fetch failed: ${e.message}")
            emptyList()
        }
    }

    /** Upsert a single lot (collector creation or recycler lifecycle update). */
    suspend fun upsertLot(lot: CollectorLotDto): Boolean {
        if (!SupabaseAuthConfig.isConfigured()) return false
        return try {
            SupabaseAuthConfig.client.from("collector_lots")
                .upsert(lot) { onConflict = "lot_id" }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Lot upsert failed for ${lot.lot_id}: ${e.message}")
            false
        }
    }

    suspend fun upsertTransaction(txn: CollectorTxnDto): Boolean {
        if (!SupabaseAuthConfig.isConfigured()) return false
        return try {
            SupabaseAuthConfig.client.from("collector_transactions")
                .upsert(txn) { onConflict = "transaction_id" }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Transaction upsert failed for ${txn.transaction_id}: ${e.message}")
            false
        }
    }

    /** Haversine distance in kilometres between two coordinates. */
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = (Math.sin(dLat / 2)).pow(2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * (Math.sin(dLon / 2)).pow(2)
        return 2 * r * Math.asin(Math.sqrt(a))
    }

    private fun readCompressedBytes(context: Context, uri: String): ByteArray? {
        return try {
            val input = context.contentResolver.openInputStream(Uri.parse(uri)) ?: return null
            val bitmap = BitmapFactory.decodeStream(input)
            input.close()
            if (bitmap == null) return null
            val scaled = if (bitmap.width > 1600 || bitmap.height > 1600) {
                val ratio = min(1600f / bitmap.width, 1600f / bitmap.height)
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
            } else {
                bitmap
            }
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
            if (scaled != bitmap) scaled.recycle()
            bitmap.recycle()
            out.toByteArray()
        } catch (e: Exception) {
            Log.w(TAG, "Photo decode failed for $uri: ${e.message}")
            null
        }
    }
}

private fun MaterialLotEntity.toSyncDto(userId: String): CloudSyncManager.CollectorLotDto =
    CloudSyncManager.CollectorLotDto(
        lot_id = lotId,
        collector_user_id = userId,
        category_name = categoryName,
        sub_category = subCategory,
        weight_kg = weightKg,
        condition = condition,
        estimated_value_inr = estimatedValueInr,
        quoted_rate_per_kg = quotedRatePerKg,
        collection_timestamp = collectionTimestamp,
        collection_location = collectionLocation,
        gps_coordinates = gpsCoordinates,
        matched_recycler_id = matchedRecyclerId,
        matched_recycler_name = matchedRecyclerName,
        status_name = statusName,
        payment_mode = paymentModeName,
        handover_receipt_number = handoverReceiptNumber,
        recycler_confirmed = recyclerConfirmed,
        epr_certificate_no = eprCertificateNo,
        manifest_details = manifestDetails
    )

private fun TransactionLedgerEntity.toSyncDto(userId: String): CloudSyncManager.CollectorTxnDto =
    CloudSyncManager.CollectorTxnDto(
        transaction_id = transactionId,
        lot_id = lotId,
        collector_user_id = userId,
        category_name = categoryName,
        weight_kg = weightKg,
        rate_per_kg = ratePerKg,
        total_amount_inr = totalAmountInr,
        payment_mode = paymentMode,
        recycler_name = recyclerName,
        timestamp = timestamp,
        receipt_number = receiptNumber,
        is_settled = isSettled
    )

private fun LotPhotoEntity.toDto(userId: String): CloudSyncManager.LotPhotoDto =
    CloudSyncManager.LotPhotoDto(
        photo_id = photoId,
        lot_id = lotId,
        collector_user_id = userId,
        remote_path = remotePath,
        mime_type = mimeType,
        upload_status = uploadStatus,
        is_primary = isPrimary,
        created_at = createdAt
    )

private fun CollectorLocationEntity.toDto(): CloudSyncManager.CollectorLocationDto =
    CloudSyncManager.CollectorLocationDto(
        collector_user_id = collectorUserId,
        latitude = latitude,
        longitude = longitude,
        area_label = areaLabel,
        is_sharing_on = isSharingOn,
        updated_at = updatedAt
    )

private fun ConnectionRequestEntity.toDto(userId: String): CloudSyncManager.ConnectionRequestDto =
    CloudSyncManager.ConnectionRequestDto(
        request_id = requestId,
        collector_user_id = userId,
        recycler_id = recyclerId,
        recycler_name = recyclerName,
        status = status,
        created_at = createdAt,
        updated_at = updatedAt
    )

private fun QuotationEntity.toDto(userId: String): CloudSyncManager.QuotationDto =
    CloudSyncManager.QuotationDto(
        quotation_id = quotationId,
        request_id = requestId,
        recycler_id = recyclerId,
        recycler_name = recyclerName,
        lot_id = lotId,
        collector_user_id = userId,
        quoted_rate_per_kg = quotedRatePerKg,
        quoted_total_inr = quotedTotalInr,
        note = note,
        status = status,
        created_at = createdAt,
        responded_at = respondedAt
    )

private fun AuditLogEntity.toDto(userId: String): CloudSyncManager.AuditLogDto =
    CloudSyncManager.AuditLogDto(
        id = id,
        user_id = userId,
        action = action,
        detail_json = detailJson,
        created_at = createdAt
    )

// ---- Remote -> local cache / domain mappers ---------------------------------

fun MaterialLot.toCollectorLotDto(collectorUserId: String): CloudSyncManager.CollectorLotDto =
    CloudSyncManager.CollectorLotDto(
        lot_id = lotId,
        collector_user_id = collectorUserId,
        category_name = category.name,
        sub_category = subCategory,
        weight_kg = weightKg,
        condition = condition,
        estimated_value_inr = estimatedValueInr,
        quoted_rate_per_kg = quotedRatePerKg,
        collection_timestamp = collectionTimestamp,
        collection_location = collectionLocation,
        gps_coordinates = gpsCoordinates,
        matched_recycler_id = matchedRecyclerId,
        matched_recycler_name = matchedRecyclerName,
        status_name = status.name,
        payment_mode = paymentMode.name,
        handover_receipt_number = handoverReceiptNumber,
        recycler_confirmed = recyclerConfirmed,
        epr_certificate_no = eprCertificateNo,
        manifest_details = manifestDetails
    )

fun CloudSyncManager.CollectorLotDto.toEntity(): MaterialLotEntity = MaterialLotEntity(
    lotId = lot_id,
    categoryName = category_name,
    subCategory = sub_category,
    weightKg = weight_kg,
    condition = condition,
    imageUri = null,
    estimatedValueInr = estimated_value_inr,
    quotedRatePerKg = quoted_rate_per_kg,
    collectionTimestamp = collection_timestamp,
    collectionLocation = collection_location,
    gpsCoordinates = gps_coordinates,
    matchedRecyclerId = matched_recycler_id,
    matchedRecyclerName = matched_recycler_name,
    statusName = status_name,
    paymentModeName = payment_mode,
    handoverReceiptNumber = handover_receipt_number,
    recyclerConfirmed = recycler_confirmed,
    eprCertificateNo = epr_certificate_no,
    manifestDetails = manifest_details,
    isSynced = true
)

fun CloudSyncManager.CollectorLotDto.toMaterialLot(): MaterialLot {
    val category = runCatching { MaterialCategory.valueOf(category_name) }
        .getOrDefault(MaterialCategory.PCB_BOARDS)
    val status = runCatching { LotStatus.valueOf(status_name) }
        .getOrDefault(LotStatus.VALUATED)
    val payment = runCatching { PaymentMode.valueOf(payment_mode) }
        .getOrDefault(PaymentMode.CASH)
    return MaterialLot(
        lotId = lot_id,
        category = category,
        subCategory = sub_category,
        weightKg = weight_kg,
        condition = condition,
        estimatedValueInr = estimated_value_inr,
        quotedRatePerKg = quoted_rate_per_kg,
        collectionTimestamp = collection_timestamp,
        collectionLocation = collection_location,
        gpsCoordinates = gps_coordinates,
        matchedRecyclerId = matched_recycler_id,
        matchedRecyclerName = matched_recycler_name,
        status = status,
        paymentMode = payment,
        handoverReceiptNumber = handover_receipt_number,
        recyclerConfirmed = recycler_confirmed,
        eprCertificateNo = epr_certificate_no,
        manifestDetails = manifest_details,
        isSynced = true
    )
}

fun CloudSyncManager.CollectorTxnDto.toEntity(): TransactionLedgerEntity = TransactionLedgerEntity(
    transactionId = transaction_id,
    lotId = lot_id,
    categoryName = category_name,
    weightKg = weight_kg,
    ratePerKg = rate_per_kg,
    totalAmountInr = total_amount_inr,
    paymentMode = payment_mode,
    recyclerName = recycler_name,
    timestamp = timestamp,
    receiptNumber = receipt_number,
    isSettled = is_settled
)

fun CloudSyncManager.PriceDto.toEntity(): PriceRecordEntity = PriceRecordEntity(
    id = price_id,
    categoryName = category_name,
    subCategory = sub_category ?: "",
    location = location ?: "",
    prevailingBuyRate = prevailing_buy_rate,
    marketMin = market_min,
    marketMax = market_max,
    trend = trend ?: "STABLE",
    trendPercentage = trend_percentage,
    unit = unit,
    dateUpdated = date_updated ?: "",
    keyMetalsJoined = key_metals_joined ?: ""
)

fun CloudSyncManager.SafetyGuidelineDto.toEntity(): SafetyGuidelineEntity = SafetyGuidelineEntity(
    id = guideline_id,
    practiceTitle = practice_title,
    whyUnsafe = why_unsafe ?: "",
    whatIsLost = what_is_lost ?: "",
    safeFormalAlternative = safe_formal_alternative ?: "",
    iconEmoji = icon_emoji ?: "",
    alertLevel = alert_level ?: "HIGH"
)

fun CloudSyncManager.RemoteRecycler.toEntity(): RecyclerEntity = RecyclerEntity(
    recyclerId = recycler_id,
    name = name,
    facilityLocation = facility_location ?: "",
    city = city ?: "",
    distanceKm = distance_km,
    cpcbRegNo = cpcb_reg_no ?: "",
    authorizationValidity = authorization_validity ?: "",
    authorizationStatus = authorization_status?.lowercase() ?: "active",
    serviceArea = service_area ?: "",
    phone = phone ?: "",
    contactEmail = contact_email ?: "",
    acceptedCategoriesJoined = accepted_categories ?: "",
    ratesJson = "",
    doorstepPickup = doorstep_pickup,
    minWeightForPickupKg = min_weight_for_pickup_kg,
    rating = rating.toFloat(),
    latitude = latitude,
    longitude = longitude
)

/** Encodes cloud offered rates for Room caching ("CATEGORY=rate;..."). */
fun encodeRecyclerRates(rates: Map<String, Double>): String =
    rates.entries.joinToString(";") { "${it.key}=${it.value}" }

/** Decodes cached offered rates; unknown/malformed parts are ignored. */
fun decodeRecyclerRates(ratesJson: String): Map<String, Double> {
    if (ratesJson.isBlank()) return emptyMap()
    return ratesJson.split(";").mapNotNull { part ->
        val kv = part.split("=", limit = 2)
        if (kv.size != 2) return@mapNotNull null
        val rate = kv[1].toDoubleOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
        kv[0].trim().ifBlank { null }?.let { it to rate }
    }.toMap()
}
