package com.example.data

import com.example.model.AuthorizedRecycler
import com.example.model.HazardSafetyInfo
import com.example.model.LotStatus
import com.example.model.MaterialCategory
import com.example.model.MaterialLot
import com.example.model.PaymentMode
import com.example.model.PendingPriceChange
import com.example.model.PriceChangeKind
import com.example.model.PriceChangeSyncStatus
import com.example.model.PriceHistoryEntry
import com.example.model.PriceRecord
import com.example.model.RecyclerOffer
import com.example.model.RoleType
import com.example.model.UnitEconomicsData
import com.example.network.BackendApiClient
import com.example.network.BackendOfferResult
import com.example.network.BackendPriceResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class EwasteRepository(
    private val lotDao: MaterialLotDao,
    private val priceDao: PriceDao,
    private val recyclerDao: RecyclerDao,
    private val ledgerDao: TransactionLedgerDao,
    private val safetyDao: SafetyGuidelineDao,
    private val photoDao: LotPhotoDao,
    private val locationDao: CollectorLocationDao,
    private val connectionDao: ConnectionRequestDao,
    private val quotationDao: QuotationDao,
    private val auditDao: AuditLogDao,
    private val offerDao: RecyclerOfferDao,
    private val priceHistoryDao: PriceHistoryDao,
    private val priceQueueDao: PriceChangeQueueDao
) {
    val allLots: Flow<List<MaterialLot>> = lotDao.getAllLots().map { entities ->
        entities.map { it.toDomainModel() }
    }

    val allPrices: Flow<List<PriceRecord>> = priceDao.getAllPrices().map { entities ->
        entities.map { it.toDomainModel() }
    }

    /** Applicable (non-deactivated) reference prices for the collector board. */
    val activeReferencePrices: Flow<List<PriceRecord>> = priceDao.getActivePrices().map { entities ->
        entities.map { it.toDomainModel() }
    }

    /** Every reference price, including deactivated rows (admin view). */
    val allReferencePrices: Flow<List<PriceRecord>> = priceDao.getAllPrices().map { entities ->
        entities.map { it.toDomainModel() }
    }

    /** Recycler purchase offers â€” a different concept from reference prices. */
    val allRecyclerOffers: Flow<List<RecyclerOffer>> = offerDao.getAll().map { entities ->
        entities.map { it.toDomainModel() }
    }

    val activeRecyclerOffers: Flow<List<RecyclerOffer>> = offerDao.getActive().map { entities ->
        entities.map { it.toDomainModel() }
    }

    val priceHistory: Flow<List<PriceHistoryEntry>> = priceHistoryDao.getAll().map { entities ->
        entities.map { it.toDomainModel() }
    }

    val pendingPriceChanges: Flow<List<PendingPriceChange>> = priceQueueDao.getAll().map { entities ->
        entities.map { it.toDomainModel() }
    }

    val pendingPriceChangeCount: Flow<Int> = priceQueueDao.getPendingCount()

    val allRecyclers: Flow<List<AuthorizedRecycler>> = recyclerDao.getAllRecyclers().map { entities ->
        // Only authorized + active recyclers with a valid registration are
        // ever shown as verified options. Suspended/expired/pending rows and
        // rows without a CPCB number are hidden, never fabricated.
        entities.map { it.toDomainModel() }
            .filter { it.authorizationStatus == "active" && it.cpcbRegNo.isNotBlank() }
    }

    val allSafetyGuidelines: Flow<List<HazardSafetyInfo>> = safetyDao.getAllGuidelines().map { entities ->
        if (entities.isNotEmpty()) {
            entities.map { it.toDomainModel() }
        } else {
            Companion.getSafetyGuidance()
        }
    }

    val totalSettledEarnings: Flow<Double> = ledgerDao.getTotalSettledEarnings().map { it ?: 0.0 }
    val pendingDues: Flow<Double> = ledgerDao.getPendingDues().map { it ?: 0.0 }
    val allTransactions: Flow<List<TransactionLedgerEntity>> = ledgerDao.getAllTransactions()
    val unsyncedLotsCount: Flow<Int> = lotDao.getUnsyncedLots().map { it.size }

    val completedTxnCount: Flow<Int> = ledgerDao.getCompletedTxnCount()
    val pendingTxnCount: Flow<Int> = ledgerDao.getPendingTxnCount()

    // ---- Photos -----------------------------------------------------------
    val pendingPhotos: Flow<List<LotPhotoEntity>> = photoDao.getPendingPhotos()

    fun photosForLot(lotId: String): Flow<List<LotPhotoEntity>> =
        photoDao.getPhotosForLot(lotId)

    suspend fun insertDraftPhoto(photo: LotPhotoEntity) = photoDao.insertPhoto(photo)

    suspend fun attachDraftPhotosToLot(draftLotId: String, lotId: String) =
        photoDao.attachPhotosToLot(draftLotId, lotId)

    suspend fun removePhoto(photoId: String) = photoDao.deletePhoto(photoId)

    suspend fun getPendingPhotosNow(): List<LotPhotoEntity> = photoDao.getPendingPhotosNow()

    suspend fun markPhotoStatus(photoId: String, status: String, remotePath: String?) =
        photoDao.updateStatus(photoId, status, remotePath)

    // ---- Collector location (privacy-preserving) ----------------------------
    fun collectorLocation(userId: String): Flow<CollectorLocationEntity?> =
        locationDao.getLocation(userId)

    suspend fun collectorLocationNow(userId: String): CollectorLocationEntity? =
        locationDao.getLocationNow(userId)

    suspend fun saveCollectorLocation(location: CollectorLocationEntity) =
        locationDao.upsert(location)

    // ---- Connections & quotations -------------------------------------------
    val allConnections: Flow<List<ConnectionRequestEntity>> = connectionDao.getAll()
    val activeConnections: Flow<List<ConnectionRequestEntity>> = connectionDao.getActive()
    val allQuotations: Flow<List<QuotationEntity>> = quotationDao.getAll()
    val pendingQuotations: Flow<List<QuotationEntity>> = quotationDao.getPending()

    suspend fun sendConnectionRequest(
        collectorUserId: String,
        recyclerId: String,
        recyclerName: String
    ): ConnectionRequestEntity {
        val existing = connectionDao.getExistingPending(collectorUserId, recyclerId)
        return existing ?: run {
            val now = System.currentTimeMillis()
            val request = ConnectionRequestEntity(
                requestId = "CONN-${now}-${(1000..9999).random()}",
                collectorUserId = collectorUserId,
                recyclerId = recyclerId,
                recyclerName = recyclerName,
                status = "PENDING",
                createdAt = now,
                updatedAt = now
            )
            connectionDao.insert(request)
            request
        }
    }

    suspend fun updateConnectionStatus(requestId: String, status: String) {
        connectionDao.updateStatus(requestId, status, System.currentTimeMillis())
    }

    suspend fun addQuotation(
        requestId: String,
        recyclerId: String,
        recyclerName: String,
        lotId: String?,
        quotedRatePerKg: Double,
        quotedTotalInr: Double?,
        note: String
    ): QuotationEntity {
        val now = System.currentTimeMillis()
        val quotation = QuotationEntity(
            quotationId = "QUO-${now}-${(1000..9999).random()}",
            requestId = requestId,
            recyclerId = recyclerId,
            recyclerName = recyclerName,
            lotId = lotId,
            quotedRatePerKg = quotedRatePerKg,
            quotedTotalInr = quotedTotalInr ?: 0.0,
            note = note,
            status = "PENDING",
            createdAt = now,
            respondedAt = null
        )
        quotationDao.insert(quotation)
        return quotation
    }

    suspend fun respondToQuotation(quotationId: String, accept: Boolean) {
        quotationDao.updateStatus(
            quotationId,
            if (accept) "ACCEPTED" else "REJECTED",
            System.currentTimeMillis()
        )
    }

    // ---- Price management (admin reference prices + recycler offers) ---------
    /**
     * Creates or updates a government reference price.
     *
     * Authorization is checked here AND by the database (RLS + constraints).
     * A demo session never holds a Supabase JWT, so its edits are explicitly
     * local-only and are not queued for the cloud.
     */
    suspend fun saveReferencePrice(
        input: ReferencePriceInput,
        actorRole: RoleType?,
        actorUserId: String,
        actorLabel: String,
        isDemoAccount: Boolean
    ): PriceWriteResult {
        val role = actorRole
        if (role == null || !PriceManagementPolicy.canManageReferencePrices(role)) {
            return PriceWriteResult.Rejected(
                listOf("Only a government administrator can change reference prices.")
            )
        }
        val validation = PriceManagementPolicy.validateReferencePrice(input)
        if (!validation.isValid) return PriceWriteResult.Rejected(validation.errors)

        val existing = input.priceId.takeIf { it.isNotBlank() }?.let { priceDao.getPriceById(it) }
        if (existing != null && existing.version != input.baseVersion) {
            return PriceWriteResult.Conflict(
                "This price was updated on the server. Reload before saving.",
                existing.version
            )
        }
        if (existing == null && input.baseVersion > 0L) {
            return PriceWriteResult.Conflict(
                "This price no longer exists on the server. Reload the price list.",
                null
            )
        }

        val now = System.currentTimeMillis()
        val priceId = existing?.id ?: input.priceId.ifBlank {
            "PRICE-${input.category.id}-${now}"
        }
        val version = (existing?.version ?: 0L) + 1L
        val changeType = when {
            existing == null -> "CREATE"
            existing.isActive && !input.isActive -> "DEACTIVATE"
            else -> "UPDATE"
        }
        val entity = existing?.copy(
            categoryName = input.category.name,
            subCategory = input.materialName.trim(),
            location = input.location.trim(),
            prevailingBuyRate = input.pricePerKg,
            marketMin = input.marketMin,
            marketMax = input.marketMax,
            trend = input.trend,
            trendPercentage = input.trendPercentage,
            dateUpdated = formatPriceDate(now),
            keyMetalsJoined = input.keyMetals.joinToString(","),
            grade = input.grade.trim(),
            currency = input.currency.uppercase(),
            isActive = input.isActive,
            effectiveDate = input.effectiveDate,
            updatedAt = now,
            updatedBy = actorLabel,
            version = version
        ) ?: PriceRecordEntity(
            id = priceId,
            categoryName = input.category.name,
            subCategory = input.materialName.trim(),
            location = input.location.trim(),
            prevailingBuyRate = input.pricePerKg,
            marketMin = input.marketMin,
            marketMax = input.marketMax,
            trend = input.trend,
            trendPercentage = input.trendPercentage,
            unit = "â‚¹/kg",
            dateUpdated = formatPriceDate(now),
            keyMetalsJoined = input.keyMetals.joinToString(","),
            grade = input.grade.trim(),
            currency = input.currency.uppercase(),
            isActive = input.isActive,
            effectiveDate = input.effectiveDate,
            updatedAt = now,
            updatedBy = actorLabel,
            version = version
        )

        val payload = input.copy(priceId = priceId, updatedByLabel = actorLabel)
        val cloudResult = runCatching {
            BackendApiClient.saveReferencePrice(payload)
        }.getOrNull()

        when (cloudResult) {
            is BackendPriceResult.Success -> {
                priceDao.insertPrice(cloudResult.price.toEntity())
                priceHistoryDao.insert(
                    PriceHistoryEntity(
                        priceId = priceId,
                        materialName = cloudResult.price.subCategory,
                        categoryName = cloudResult.price.category.name,
                        grade = cloudResult.price.grade,
                        pricePerKg = cloudResult.price.prevailingBuyRate,
                        marketMin = cloudResult.price.marketMin,
                        marketMax = cloudResult.price.marketMax,
                        currency = cloudResult.price.currency,
                        changeType = changeType,
                        changedBy = actorLabel,
                        changedAt = now,
                        version = cloudResult.price.version
                    )
                )
                return PriceWriteResult.Saved(localOnly = false)
            }
            is BackendPriceResult.Conflict -> {
                return PriceWriteResult.Conflict(cloudResult.message, cloudResult.serverVersion)
            }
            is BackendPriceResult.Rejected -> {
                return PriceWriteResult.Rejected(listOf(cloudResult.message))
            }
            else -> {
                priceDao.insertPrice(entity)
                priceHistoryDao.insert(
                    PriceHistoryEntity(
                        priceId = priceId,
                        materialName = entity.subCategory,
                        categoryName = entity.categoryName,
                        grade = entity.grade,
                        pricePerKg = entity.prevailingBuyRate,
                        marketMin = entity.marketMin,
                        marketMax = entity.marketMax,
                        currency = entity.currency,
                        changeType = changeType,
                        changedBy = actorLabel,
                        changedAt = now,
                        version = version
                    )
                )
                if (!isDemoAccount) {
                    enqueuePriceChange(
                        kind = PriceChangeKind.REFERENCE_PRICE,
                        targetId = priceId,
                        actorUserId = actorUserId,
                        actorRole = role,
                        payloadJson = pricePayloadJson.encodeToString(payload),
                        baseVersion = input.baseVersion
                    )
                }
                return PriceWriteResult.Saved(localOnly = isDemoAccount)
            }
        }
    }

    /** Deactivates (soft-deletes) a reference price. History is preserved. */
    suspend fun deactivateReferencePrice(
        priceId: String,
        baseVersion: Long,
        actorRole: RoleType?,
        actorUserId: String,
        actorLabel: String,
        isDemoAccount: Boolean
    ): PriceWriteResult {
        val role = actorRole
        if (role == null || !PriceManagementPolicy.canManageReferencePrices(role)) {
            return PriceWriteResult.Rejected(
                listOf("Only a government administrator can deactivate reference prices.")
            )
        }
        val existing = priceDao.getPriceById(priceId)
            ?: return PriceWriteResult.Rejected(listOf("Price not found in the local cache."))
        if (!existing.isActive) {
            return PriceWriteResult.Rejected(listOf("This price is already inactive."))
        }
        if (existing.version != baseVersion) {
            return PriceWriteResult.Conflict(
                "This price was updated on the server. Reload before deactivating.",
                existing.version
            )
        }
        val now = System.currentTimeMillis()
        val version = existing.version + 1L

        val payload = existing.toPriceInput().copy(
            isActive = false,
            baseVersion = baseVersion,
            updatedByLabel = actorLabel
        )
        val cloudResult = runCatching {
            BackendApiClient.saveReferencePrice(payload)
        }.getOrNull()

        when (cloudResult) {
            is BackendPriceResult.Success -> {
                priceDao.setActive(priceId, false, cloudResult.price.version, now, actorLabel)
                priceHistoryDao.insert(
                    PriceHistoryEntity(
                        priceId = existing.id,
                        materialName = existing.subCategory,
                        categoryName = existing.categoryName,
                        grade = existing.grade,
                        pricePerKg = existing.prevailingBuyRate,
                        marketMin = existing.marketMin,
                        marketMax = existing.marketMax,
                        currency = existing.currency,
                        changeType = "DEACTIVATE",
                        changedBy = actorLabel,
                        changedAt = now,
                        version = cloudResult.price.version
                    )
                )
                return PriceWriteResult.Saved(localOnly = false)
            }
            is BackendPriceResult.Conflict -> {
                return PriceWriteResult.Conflict(cloudResult.message, cloudResult.serverVersion)
            }
            is BackendPriceResult.Rejected -> {
                return PriceWriteResult.Rejected(listOf(cloudResult.message))
            }
            else -> {
                priceDao.setActive(priceId, false, version, now, actorLabel)
                priceHistoryDao.insert(
                    PriceHistoryEntity(
                        priceId = existing.id,
                        materialName = existing.subCategory,
                        categoryName = existing.categoryName,
                        grade = existing.grade,
                        pricePerKg = existing.prevailingBuyRate,
                        marketMin = existing.marketMin,
                        marketMax = existing.marketMax,
                        currency = existing.currency,
                        changeType = "DEACTIVATE",
                        changedBy = actorLabel,
                        changedAt = now,
                        version = version
                    )
                )
                if (!isDemoAccount) {
                    enqueuePriceChange(
                        kind = PriceChangeKind.REFERENCE_PRICE,
                        targetId = priceId,
                        actorUserId = actorUserId,
                        actorRole = role,
                        payloadJson = pricePayloadJson.encodeToString(payload),
                        baseVersion = baseVersion
                    )
                }
                return PriceWriteResult.Saved(localOnly = isDemoAccount)
            }
        }
    }

    /**
     * Creates or updates one of the caller's own recycler offers. An offer
     * belonging to another recycler is never accepted, regardless of role.
     */
    suspend fun saveRecyclerOffer(
        input: OfferInput,
        actorRole: RoleType?,
        actorUserId: String,
        actorLabel: String,
        actorRecyclerId: String,
        isDemoAccount: Boolean
    ): PriceWriteResult {
        val role = actorRole
        if (role == null || !PriceManagementPolicy.canManageOffers(role)) {
            return PriceWriteResult.Rejected(
                listOf("Only a formal recycler can publish purchase offers.")
            )
        }
        if (actorRecyclerId.isBlank()) {
            return PriceWriteResult.Rejected(
                listOf("Your recycler identity could not be resolved. Sign in again.")
            )
        }
        if (!PriceManagementPolicy.ownsOffer(input.recyclerId, actorRecyclerId)) {
            return PriceWriteResult.Rejected(
                listOf("You can only edit offers that belong to your own facility.")
            )
        }
        val validation = PriceManagementPolicy.validateOffer(input)
        if (!validation.isValid) return PriceWriteResult.Rejected(validation.errors)

        val existing = input.offerId.takeIf { it.isNotBlank() }?.let { offerDao.getById(it) }
        if (existing != null && !PriceManagementPolicy.ownsOffer(existing.recyclerId, actorRecyclerId)) {
            return PriceWriteResult.Rejected(
                listOf("You can only edit offers that belong to your own facility.")
            )
        }
        if (existing == null) {
            val duplicate = offerDao.getForRecycler(actorRecyclerId)
                .firstOrNull { it.categoryName == input.category.name }
            if (duplicate != null) {
                return PriceWriteResult.Rejected(
                    listOf("An offer for this material already exists. Edit the existing offer instead.")
                )
            }
        }
        if (existing != null && existing.version != input.baseVersion) {
            return PriceWriteResult.Conflict(
                "This offer was updated on the server. Reload before saving.",
                existing.version
            )
        }
        if (existing == null && input.baseVersion > 0L) {
            return PriceWriteResult.Conflict(
                "This offer no longer exists on the server. Reload the offers list.",
                null
            )
        }

        val now = System.currentTimeMillis()
        val offerId = existing?.offerId ?: input.offerId.ifBlank { "OFFER-${now}" }
        val version = (existing?.version ?: 0L) + 1L
        val entity = RecyclerOfferEntity(
            offerId = offerId,
            recyclerId = actorRecyclerId,
            recyclerName = input.recyclerName.trim(),
            categoryName = input.category.name,
            materialName = input.materialName.trim(),
            grade = input.grade.trim(),
            pricePerKg = input.pricePerKg,
            currency = input.currency.uppercase(),
            minQuantityKg = input.minQuantityKg,
            maxQuantityKg = input.maxQuantityKg,
            condition = input.condition.trim(),
            status = input.status.uppercase(),
            notes = input.notes.trim(),
            effectiveFrom = existing?.effectiveFrom ?: now,
            updatedAt = now,
            updatedBy = actorLabel,
            version = version
        )
        val payload = input.copy(offerId = offerId, recyclerId = actorRecyclerId, updatedByLabel = actorLabel)
        val cloudResult = runCatching {
            BackendApiClient.saveRecyclerOffer(payload)
        }.getOrNull()

        when (cloudResult) {
            is BackendOfferResult.Success -> {
                offerDao.insert(cloudResult.offer.toEntity())
                return PriceWriteResult.Saved(localOnly = false)
            }
            is BackendOfferResult.Conflict -> {
                return PriceWriteResult.Conflict(cloudResult.message, cloudResult.serverVersion)
            }
            is BackendOfferResult.Rejected -> {
                return PriceWriteResult.Rejected(listOf(cloudResult.message))
            }
            else -> {
                offerDao.insert(entity)
                if (!isDemoAccount) {
                    enqueuePriceChange(
                        kind = PriceChangeKind.RECYCLER_OFFER,
                        targetId = offerId,
                        actorUserId = actorUserId,
                        actorRole = role,
                        payloadJson = pricePayloadJson.encodeToString(payload),
                        baseVersion = input.baseVersion
                    )
                }
                return PriceWriteResult.Saved(localOnly = isDemoAccount)
            }
        }
    }

    /**
     * Records the price agreed at handover. Once set it can never be changed,
     * so a later reference-price update cannot rewrite a completed payout.
     */
    suspend fun recordAgreedHandoverPrice(
        lotId: String,
        ratePerKg: Double,
        actorRole: RoleType?,
        actorUserId: String
    ): PriceWriteResult {
        if (actorRole != RoleType.FORMAL_RECYCLER && actorRole != RoleType.GOVERNMENT_ADMIN) {
            return PriceWriteResult.Rejected(listOf("Only the receiving recycler can record the agreed price."))
        }
        if (!ratePerKg.isFinite() || ratePerKg < 0.0) {
            return PriceWriteResult.Rejected(listOf("Agreed price must be a non-negative number."))
        }
        val existing = lotDao.getLotById(lotId)
            ?: return PriceWriteResult.Rejected(listOf("Lot not found in the local cache."))
        if (!PriceManagementPolicy.canChangeAgreedPrice(existing.agreedRatePerKg, ratePerKg)) {
            return PriceWriteResult.Rejected(
                listOf("The agreed handover price is already locked and cannot be overwritten.")
            )
        }
        if (existing.agreedRatePerKg != null) {
            return PriceWriteResult.Saved(localOnly = false)
        }
        lotDao.updateLot(
            existing.copy(
                agreedRatePerKg = ratePerKg,
                agreedAt = System.currentTimeMillis(),
                isSynced = false
            )
        )
        return PriceWriteResult.Saved(localOnly = false)
    }

    /** Pulls the authoritative price/offer tables into the local cache. */
    suspend fun refreshPriceCachesFromCloud(): Boolean {
        var ok = false
        runCatching {
            val backendPrices = BackendApiClient.fetchReferencePrices()
            if (backendPrices.isNotEmpty()) {
                priceDao.insertPrices(backendPrices.map { it.toEntity() })
                ok = true
            } else {
                val prices = CloudSyncManager.fetchMaterialPrices()
                if (prices.isNotEmpty()) {
                    priceDao.insertPrices(prices.map { it.toEntity() })
                    ok = true
                }
            }
        }.onFailure {
            runCatching {
                val prices = CloudSyncManager.fetchMaterialPrices()
                if (prices.isNotEmpty()) {
                    priceDao.insertPrices(prices.map { it.toEntity() })
                    ok = true
                }
            }
        }
        runCatching {
            val backendOffers = BackendApiClient.fetchRecyclerOffers()
            if (backendOffers.isNotEmpty()) {
                offerDao.insertAll(backendOffers.map { it.toEntity() })
                ok = true
            } else {
                val offers = CloudSyncManager.fetchRecyclerOffers()
                if (offers.isNotEmpty()) {
                    offerDao.insertAll(offers.map { it.toEntity() })
                    ok = true
                }
            }
        }.onFailure {
            runCatching {
                val offers = CloudSyncManager.fetchRecyclerOffers()
                if (offers.isNotEmpty()) {
                    offerDao.insertAll(offers.map { it.toEntity() })
                    ok = true
                }
            }
        }
        return ok
    }

    /**
     * Pushes queued price edits. The cloud call is version-checked: if the
     * server row moved on, the queued change is marked CONFLICT and the newer
     * server value is never overwritten.
     */
    private fun BackendPriceResult.toPricePushResult(): CloudSyncManager.PricePushResult = when (this) {
        is BackendPriceResult.Success -> CloudSyncManager.PricePushResult.Success(price.version)
        is BackendPriceResult.Conflict -> CloudSyncManager.PricePushResult.Conflict(message, serverVersion)
        is BackendPriceResult.Rejected -> CloudSyncManager.PricePushResult.Failed(message)
        is BackendPriceResult.Failed -> CloudSyncManager.PricePushResult.Failed(message)
    }

    private fun BackendOfferResult.toPricePushResult(): CloudSyncManager.PricePushResult = when (this) {
        is BackendOfferResult.Success -> CloudSyncManager.PricePushResult.Success(offer.version)
        is BackendOfferResult.Conflict -> CloudSyncManager.PricePushResult.Conflict(message, serverVersion)
        is BackendOfferResult.Rejected -> CloudSyncManager.PricePushResult.Failed(message)
        is BackendOfferResult.Failed -> CloudSyncManager.PricePushResult.Failed(message)
    }

    suspend fun syncPendingPriceChanges(): PriceSyncReport {
        val pending = priceQueueDao.getPendingNow()
        if (pending.isEmpty()) return PriceSyncReport()
        var pushed = 0
        var conflicts = 0
        var failed = 0
        for (change in pending) {
            val kind = runCatching { PriceChangeKind.valueOf(change.kind) }.getOrNull()
            // Demo identities are not Supabase sessions: they must be replayed
            // through the backend, which re-checks role/facility/version.
            val isDemoActor = change.actorUserId.startsWith("demo-")
            val result = when (kind) {
                PriceChangeKind.REFERENCE_PRICE -> {
                    val input = runCatching {
                        pricePayloadJson.decodeFromString<ReferencePriceInput>(change.payloadJson)
                    }.getOrNull()
                    when {
                        input == null -> CloudSyncManager.PricePushResult.Failed("Queued price payload is corrupt.")
                        isDemoActor -> BackendApiClient.saveReferencePrice(input).toPricePushResult()
                        else -> CloudSyncManager.pushReferencePrice(input)
                    }
                }
                PriceChangeKind.RECYCLER_OFFER -> {
                    val input = runCatching {
                        pricePayloadJson.decodeFromString<OfferInput>(change.payloadJson)
                    }.getOrNull()
                    when {
                        input == null -> CloudSyncManager.PricePushResult.Failed("Queued offer payload is corrupt.")
                        isDemoActor -> BackendApiClient.saveRecyclerOffer(input).toPricePushResult()
                        else -> CloudSyncManager.pushRecyclerOffer(input)
                    }
                }
                null -> CloudSyncManager.PricePushResult.Failed("Unknown queued change type.")
            }
            when (result) {
                is CloudSyncManager.PricePushResult.Success -> {
                    priceQueueDao.delete(change.queueId)
                    pushed++
                }
                is CloudSyncManager.PricePushResult.Conflict -> {
                    priceQueueDao.updateStatus(
                        change.queueId,
                        PriceChangeSyncStatus.CONFLICT.name,
                        result.message,
                        result.serverVersion,
                        System.currentTimeMillis()
                    )
                    conflicts++
                }
                is CloudSyncManager.PricePushResult.Failed -> {
                    priceQueueDao.updateStatus(
                        change.queueId,
                        PriceChangeSyncStatus.FAILED.name,
                        result.message,
                        null,
                        System.currentTimeMillis()
                    )
                    failed++
                }
            }
        }
        if (conflicts > 0) {
            // Show the server's newer values; the queued intent stays visible as
            // CONFLICT so nothing is silently lost.
            refreshPriceCachesFromCloud()
        }
        return PriceSyncReport(pushed = pushed, conflicts = conflicts, failed = failed)
    }

    suspend fun discardQueuedPriceChange(queueId: String) {
        priceQueueDao.delete(queueId)
    }

    private suspend fun enqueuePriceChange(
        kind: PriceChangeKind,
        targetId: String,
        actorUserId: String,
        actorRole: RoleType,
        payloadJson: String,
        baseVersion: Long
    ) {
        val now = System.currentTimeMillis()
        priceQueueDao.insert(
            PriceChangeQueueEntity(
                queueId = "PCQ-$now-${(1000..9999).random()}",
                kind = kind.name,
                targetId = targetId,
                actorUserId = actorUserId,
                actorRole = actorRole.name,
                payloadJson = payloadJson,
                baseVersion = baseVersion,
                status = PriceChangeSyncStatus.PENDING.name,
                errorMessage = null,
                serverVersion = null,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    // ---- Audit -----------------------------------------------------------------
    suspend fun addAuditLog(userId: String, action: String, detailJson: String) {
        auditDao.insert(
            AuditLogEntity(
                id = "AUD-${System.currentTimeMillis()}-${(1000..9999).random()}",
                userId = userId,
                action = action,
                detailJson = detailJson,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    // ---- Revenue stats (from the real ledger) ------------------------------------
    fun settledEarningsBetween(startMillis: Long, endMillis: Long): Flow<Double> =
        ledgerDao.getSettledEarningsBetween(startMillis, endMillis)

    // ---- Duplicate-lot prevention ---------------------------------------------------
    suspend fun findDuplicateCandidate(category: MaterialCategory, weightKg: Double): MaterialLot? {
        val since = System.currentTimeMillis() - 60_000L
        return lotDao.findRecentDuplicate(category.name, weightKg, since)?.toDomainModel()
    }

    // ---- Live "nearby" helpers -------------------------------------------------------
    fun nearestRecyclers(recyclers: List<AuthorizedRecycler>, lat: Double, lon: Double, limit: Int = 8): List<AuthorizedRecycler> {
        return recyclers
            .map { it to CloudSyncManager.distanceKm(lat, lon, it.latitude, it.longitude) }
            .sortedBy { it.second }
            .take(limit)
            .map { it.first }
    }

    suspend fun syncOfflineData(): Int {
        val count = lotDao.getUnsyncedLotsCount()
        if (count > 0) {
            lotDao.markAllLotsSynced()
        }
        return count
    }

    // ---- Point reads (shared by recycler/admin portals) -----------------------
    suspend fun lotById(lotId: String): MaterialLot? = lotDao.getLotById(lotId)?.toDomainModel()

    suspend fun transactionByLot(lotId: String): TransactionLedgerEntity? =
        ledgerDao.getTransactionByLot(lotId)

    suspend fun unsyncedLotsNow(): List<MaterialLotEntity> = lotDao.getUnsyncedLotsNow()

    suspend fun allTransactionsNow(): List<TransactionLedgerEntity> = ledgerDao.getAllTransactionsNow()

    suspend fun createLot(
        category: MaterialCategory,
        subCategory: String,
        weightKg: Double,
        condition: String,
        location: String,
        gpsCoordinates: String,
        matchedRecycler: AuthorizedRecycler?,
        paymentMode: PaymentMode,
        isOffline: Boolean = false
    ): MaterialLot {
        val lotId = "LOT-MH-" + SimpleDateFormat("yyyy", Locale.US).format(Date()) + "-" + (1000..9999).random()
        val rate = matchedRecycler?.buyingRates?.get(category) ?: category.defaultRatePerKg
        val estimatedValue = weightKg * rate
        val receiptNumber = "EPR-RC-" + (10000..99999).random()

        val entity = MaterialLotEntity(
            lotId = lotId,
            categoryName = category.name,
            subCategory = subCategory,
            weightKg = weightKg,
            condition = condition,
            imageUri = null,
            estimatedValueInr = estimatedValue,
            quotedRatePerKg = rate,
            collectionTimestamp = System.currentTimeMillis(),
            collectionLocation = location,
            gpsCoordinates = gpsCoordinates,
            matchedRecyclerId = matchedRecycler?.recyclerId,
            matchedRecyclerName = matchedRecycler?.name,
            statusName = LotStatus.HANDOVER_PENDING.name,
            paymentModeName = paymentMode.name,
            handoverReceiptNumber = receiptNumber,
            recyclerConfirmed = false,
            eprCertificateNo = null,
            manifestDetails = null,
            isSynced = !isOffline
        )
        lotDao.insertLot(entity)

        // Also create a ledger entry marked as pending
        val txn = TransactionLedgerEntity(
            transactionId = "TXN-" + System.currentTimeMillis() % 100000,
            lotId = lotId,
            categoryName = category.titleEn,
            weightKg = weightKg,
            ratePerKg = rate,
            totalAmountInr = estimatedValue,
            paymentMode = paymentMode.label,
            recyclerName = matchedRecycler?.name ?: "Authorized Recycler Hub",
            timestamp = System.currentTimeMillis(),
            receiptNumber = receiptNumber,
            isSettled = false
        )
        ledgerDao.insertTransaction(txn)

        return entity.toDomainModel()
    }

    suspend fun confirmRecyclerHandover(
        lotId: String,
        verifiedWeight: Double? = null,
        markPaid: Boolean = true,
        agreedRatePerKg: Double? = null
    ) {
        val existing = lotDao.getLotById(lotId) ?: return
        val finalWeight = verifiedWeight ?: existing.weightKg
        // An already-agreed rate can never be overwritten; otherwise the newly
        // agreed rate (if supplied) wins, then the original quoted rate.
        val agreedRate = existing.agreedRatePerKg
            ?: agreedRatePerKg?.takeIf { it.isFinite() && it >= 0.0 }
            ?: existing.quotedRatePerKg
        val finalVal = finalWeight * agreedRate
        val certNo = "EPR-CERT-MoEFCC-" + (10000..99999).random()

        val updated = existing.copy(
            weightKg = finalWeight,
            estimatedValueInr = finalVal,
            statusName = if (markPaid) LotStatus.PAYMENT_COMPLETED.name else LotStatus.RECYCLER_VERIFIED.name,
            recyclerConfirmed = true,
            eprCertificateNo = certNo,
            // Snapshot the price actually agreed at handover. Never overwritten
            // by later reference-price changes.
            agreedRatePerKg = agreedRate,
            agreedAt = existing.agreedAt ?: System.currentTimeMillis()
        )
        lotDao.updateLot(updated)

        // Update or upsert the single ledger row for this lot (dedup: REPLACE by primary
        // key keeps one row per lot instead of creating a second settled duplicate).
        val existingTxn = ledgerDao.getTransactionByLot(lotId)
        val txn = (existingTxn?.copy(
            categoryName = existing.categoryName,
            weightKg = finalWeight,
            ratePerKg = agreedRate,
            totalAmountInr = finalVal,
            paymentMode = existing.paymentModeName,
            recyclerName = existing.matchedRecyclerName ?: "EcoReclaim Green Refineries",
            timestamp = System.currentTimeMillis(),
            receiptNumber = existing.handoverReceiptNumber ?: ("RC-" + lotId),
            isSettled = markPaid
        )) ?: TransactionLedgerEntity(
            transactionId = "TXN-SETTLED-" + lotId,
            lotId = lotId,
            categoryName = existing.categoryName,
            weightKg = finalWeight,
            ratePerKg = agreedRate,
            totalAmountInr = finalVal,
            paymentMode = existing.paymentModeName,
            recyclerName = existing.matchedRecyclerName ?: "EcoReclaim Green Refineries",
            timestamp = System.currentTimeMillis(),
            receiptNumber = existing.handoverReceiptNumber ?: ("RC-" + lotId),
            isSettled = markPaid
        )
        ledgerDao.insertTransaction(txn)
    }

    /** Saves the formal recycler's Form-6 manifest against the shared lot. */
    suspend fun saveManifest(lotId: String, details: String) {
        val existing = lotDao.getLotById(lotId) ?: return
        lotDao.updateLot(existing.copy(manifestDetails = details, isSynced = false))
    }

    fun getSafetyGuidance(): List<HazardSafetyInfo> = Companion.getSafetyGuidance()
    fun getUnitEconomics(): List<UnitEconomicsData> = Companion.getUnitEconomics()

    companion object {
        // Safety and Hazard Guidance Dataset
        fun getSafetyGuidance(): List<HazardSafetyInfo> {
            return listOf(
                HazardSafetyInfo(
                    id = "HAZ-01",
                    practiceTitle = "Open-Air Cable Burning (à¤–à¥à¤²à¥€ à¤†à¤— à¤®à¥‡à¤‚ à¤¤à¤¾à¤° à¤œà¤²à¤¾à¤¨à¤¾)",
                    whyUnsafe = "Burning plastic & PVC insulation releases deadly Dioxins, Furans, and Lead fumes into your lungs and neighborhood.",
                    whatIsLost = "Burning oxidizes and degrades copper quality, lowering scrap value by 20â€“30% and causing chronic respiratory illness.",
                    safeFormalAlternative = "Use low-cost mechanical wire stripper or handover intact wires to authorized recyclers for full pure electrolytic copper rates (â‚¹460/kg).",
                    iconEmoji = "ðŸ”¥",
                    alertLevel = "CRITICAL"
                ),
                HazardSafetyInfo(
                    id = "HAZ-02",
                    practiceTitle = "Acid Leaching of Circuit Boards (à¤à¤¸à¤¿à¤¡ à¤®à¥‡à¤‚ à¤®à¤¦à¤°à¤¬à¥‹à¤°à¥à¤¡ à¤—à¤²à¤¾à¤¨à¤¾)",
                    whyUnsafe = "Using Aqua Regia and Nitric Acid creates toxic nitrogen dioxide clouds, water table poisoning, and high chemical burn risk.",
                    whatIsLost = "Backyard acid only recovers partial gold (loss of 85% palladium, neodymium, gallium, and tantalum worth thousands of rupees).",
                    safeFormalAlternative = "Sell whole PCBs to formal recyclers who operate closed-loop hydrometallurgical recovery, paying for gold, silver, and rare earth contents.",
                    iconEmoji = "ðŸ§ª",
                    alertLevel = "CRITICAL"
                ),
                HazardSafetyInfo(
                    id = "HAZ-03",
                    practiceTitle = "Shattering CRT Monitors (à¤¸à¥€à¤†à¤°à¤Ÿà¥€ à¤¸à¥à¤•à¥à¤°à¥€à¤¨ à¤¤à¥‹à¤¡à¤¼à¤¨à¤¾)",
                    whyUnsafe = "CRT tubes contain high vacuum (implosion hazard) and 1.5 to 3 kg of lead and toxic barium phosphor powder that causes neurological damage.",
                    whatIsLost = "Broken glass cannot be safely processed and is rejected by formal recyclers, forfeiting your payment.",
                    safeFormalAlternative = "Keep CRT monitors completely intact. Handover in one piece to authorized aggregators for safe glass lead-separation.",
                    iconEmoji = "ðŸ“º",
                    alertLevel = "HIGH"
                ),
                HazardSafetyInfo(
                    id = "HAZ-04",
                    practiceTitle = "Crushing or Puncturing Lithium Batteries (à¤¬à¥ˆà¤Ÿà¤°à¥€ à¤«à¥‹à¤¡à¤¼à¤¨à¤¾)",
                    whyUnsafe = "Lithium-ion cells catch fire instantaneously upon puncture (thermal runaway up to 600Â°C) and release toxic hydrofluoric acid gas.",
                    whatIsLost = "Destroys valuable high-grade cobalt and nickel cathodes and creates severe personal burn risks.",
                    safeFormalAlternative = "Store batteries in a dry, cool wooden/plastic crate without metal contact. Formal refiners recover 95% of lithium and cobalt safely.",
                    iconEmoji = "âš¡",
                    alertLevel = "CRITICAL"
                )
            )
        }

        // Unit Economics Comparison Dataset
        fun getUnitEconomics(): List<UnitEconomicsData> {
            return listOf(
                UnitEconomicsData(
                    materialType = "PCBs & Motherboards",
                    informalBackyardEarningsPerKg = 240.0,
                    formalPlatformEarningsPerKg = 380.0,
                    differencePercentage = 58.3,
                    lostPreciousMetalsValue = 110.0,
                    eprIncentiveBonus = 30.0,
                    healthAndLegalSecurity = "Full CPCB legal immunity; Zero acid lung damage"
                ),
                UnitEconomicsData(
                    materialType = "Copper Cables & Wiring",
                    informalBackyardEarningsPerKg = 310.0,
                    formalPlatformEarningsPerKg = 460.0,
                    differencePercentage = 48.4,
                    lostPreciousMetalsValue = 90.0,
                    eprIncentiveBonus = 60.0,
                    healthAndLegalSecurity = "No police penalties for open burning; pure copper grade pricing"
                ),
                UnitEconomicsData(
                    materialType = "Lithium Batteries",
                    informalBackyardEarningsPerKg = 90.0,
                    formalPlatformEarningsPerKg = 160.0,
                    differencePercentage = 77.7,
                    lostPreciousMetalsValue = 50.0,
                    eprIncentiveBonus = 20.0,
                    healthAndLegalSecurity = "Zero explosion risk; direct payment on delivery scale"
                ),
                UnitEconomicsData(
                    materialType = "Rare Earth Motor Magnets",
                    informalBackyardEarningsPerKg = 70.0,
                    formalPlatformEarningsPerKg = 210.0,
                    differencePercentage = 200.0,
                    lostPreciousMetalsValue = 120.0,
                    eprIncentiveBonus = 20.0,
                    healthAndLegalSecurity = "Valued as strategic rare-earths instead of ordinary scrap iron"
                )
            )
        }

        // Standard authorized recyclers (fallback seed for offline / cold-start map rendering)
        fun getDefaultRecyclers(): List<RecyclerEntity> {
            return listOf(
                RecyclerEntity(
                    recyclerId = "REC-CPCB-MH-001",
                    name = "EcoReclaim Green Refineries Pvt Ltd",
                    facilityLocation = "Plot C-14, MIDC Turbhe, Navi Mumbai",
                    city = "Mumbai",
                    distanceKm = 4.2,
                    cpcbRegNo = "CPCB/EPR-REC/2023/MH-0042",
                    authorizationValidity = "Valid until Dec 2028",
                    phone = "+91 98201 44521",
                    acceptedCategoriesJoined = "PCB_BOARDS,CABLES_WIRES,BATTERIES,MOTORS_MAGNETS,LCD_PANELS",
                    ratesJson = "",
                    doorstepPickup = true,
                    minWeightForPickupKg = 25.0,
                    rating = 4.9f,
                    latitude = 19.0688,
                    longitude = 73.0189
                ),
                RecyclerEntity(
                    recyclerId = "REC-CPCB-MH-002",
                    name = "MahaClean Tech Circular Resources",
                    facilityLocation = "Bhiwandi Logistics Park, Thane District",
                    city = "Mumbai",
                    distanceKm = 11.5,
                    cpcbRegNo = "CPCB/EPR-REC/2022/MH-0118",
                    authorizationValidity = "Valid until Aug 2027",
                    phone = "+91 91370 88234",
                    acceptedCategoriesJoined = "PCB_BOARDS,CABLES_WIRES,CRTS_MONITORS,MIXED_PLASTICS",
                    ratesJson = "",
                    doorstepPickup = true,
                    minWeightForPickupKg = 50.0,
                    rating = 4.7f,
                    latitude = 19.2967,
                    longitude = 73.0631
                ),
                RecyclerEntity(
                    recyclerId = "REC-CPCB-MH-003",
                    name = "SwachhBharat E-Waste Recyclers",
                    facilityLocation = "Pimpri-Chinchwad MIDC Phase 2, Pune",
                    city = "Pune",
                    distanceKm = 120.0,
                    cpcbRegNo = "CPCB/EPR-REC/2024/MH-0205",
                    authorizationValidity = "Valid until Jan 2029",
                    phone = "+91 98902 55192",
                    acceptedCategoriesJoined = "PCB_BOARDS,BATTERIES,LCD_PANELS,MOTORS_MAGNETS",
                    ratesJson = "",
                    doorstepPickup = true,
                    minWeightForPickupKg = 40.0,
                    rating = 4.8f,
                    latitude = 18.6298,
                    longitude = 73.7997
                ),
                RecyclerEntity(
                    recyclerId = "REC-CPCB-MH-004",
                    name = "Kurla Aggregator & Dismantling Center",
                    facilityLocation = "LBS Marg, Kurla West, Mumbai",
                    city = "Mumbai",
                    distanceKm = 2.8,
                    cpcbRegNo = "CPCB/EPR-REC/2023/MH-0091",
                    authorizationValidity = "Valid until Nov 2027",
                    phone = "+91 98205 11299",
                    acceptedCategoriesJoined = "PCB_BOARDS,CABLES_WIRES,BATTERIES,CRTS_MONITORS,LCD_PANELS,MOTORS_MAGNETS,MIXED_PLASTICS",
                    ratesJson = "",
                    doorstepPickup = true,
                    minWeightForPickupKg = 15.0,
                    rating = 4.6f,
                    latitude = 19.0728,
                    longitude = 72.8795
                )
            )
        }

        fun getDefaultAuthorizedRecyclers(): List<AuthorizedRecycler> {
            return getDefaultRecyclers().map { it.toDomainModel() }
        }
    }
}

/** Outcome of an on-device price write. */
sealed interface PriceWriteResult {
    /** Saved locally. [localOnly] is true for a demo session (no server write). */
    data class Saved(val localOnly: Boolean) : PriceWriteResult
    data class Rejected(val errors: List<String>) : PriceWriteResult
    data class Conflict(val message: String, val serverVersion: Long?) : PriceWriteResult
}

/** Outcome of pushing the offline price queue. */
data class PriceSyncReport(
    val pushed: Int = 0,
    val conflicts: Int = 0,
    val failed: Int = 0
) {
    val hasIssues: Boolean get() = conflicts > 0 || failed > 0
}

private val pricePayloadJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private fun formatPriceDate(epochMillis: Long): String =
    SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(epochMillis))

/** Default effective date (ISO) used when the operator does not pick one. */
private fun defaultEffectiveDate(now: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))

// Extension mappers
private fun MaterialLotEntity.toDomainModel(): MaterialLot {
    val category = runCatching { MaterialCategory.valueOf(categoryName) }.getOrDefault(MaterialCategory.PCB_BOARDS)
    val status = runCatching { LotStatus.valueOf(statusName) }.getOrDefault(LotStatus.VALUATED)
    val paymentMode = runCatching { PaymentMode.valueOf(paymentModeName) }.getOrDefault(PaymentMode.CASH)

    return MaterialLot(
        lotId = lotId,
        category = category,
        subCategory = subCategory,
        weightKg = weightKg,
        condition = condition,
        imageUri = imageUri,
        estimatedValueInr = estimatedValueInr,
        quotedRatePerKg = quotedRatePerKg,
        collectionTimestamp = collectionTimestamp,
        collectionLocation = collectionLocation,
        gpsCoordinates = gpsCoordinates,
        matchedRecyclerId = matchedRecyclerId,
        matchedRecyclerName = matchedRecyclerName,
        status = status,
        paymentMode = paymentMode,
        handoverReceiptNumber = handoverReceiptNumber,
        recyclerConfirmed = recyclerConfirmed,
        eprCertificateNo = eprCertificateNo,
        manifestDetails = manifestDetails,
        agreedRatePerKg = agreedRatePerKg,
        agreedAt = agreedAt,
        isSynced = isSynced
    )
}

private fun PriceRecordEntity.toDomainModel(): PriceRecord {
    val category = runCatching { MaterialCategory.valueOf(categoryName) }.getOrDefault(MaterialCategory.PCB_BOARDS)
    val metals = keyMetalsJoined.split(",").map { it.trim() }.filter { it.isNotBlank() }

    return PriceRecord(
        category = category,
        subCategory = subCategory,
        location = location,
        prevailingBuyRate = prevailingBuyRate,
        marketMin = marketMin,
        marketMax = marketMax,
        trend = trend,
        trendPercentage = trendPercentage,
        unit = unit,
        dateUpdated = dateUpdated,
        keyMetals = metals,
        priceId = id,
        grade = grade,
        currency = currency,
        isActive = isActive,
        effectiveDate = effectiveDate,
        updatedAt = updatedAt,
        updatedBy = updatedBy,
        version = version
    )
}

internal fun PriceRecordEntity.toPriceInput(): ReferencePriceInput = ReferencePriceInput(
    priceId = id,
    category = runCatching { MaterialCategory.valueOf(categoryName) }.getOrDefault(MaterialCategory.PCB_BOARDS),
    materialName = subCategory,
    grade = grade,
    location = location,
    pricePerKg = prevailingBuyRate,
    marketMin = marketMin,
    marketMax = marketMax,
    currency = currency,
    effectiveDate = effectiveDate,
    isActive = isActive,
    baseVersion = version,
    trend = trend,
    trendPercentage = trendPercentage,
    keyMetals = keyMetalsJoined.split(",").map { it.trim() }.filter { it.isNotBlank() }
)

internal fun RecyclerOfferEntity.toDomainModel(): RecyclerOffer = RecyclerOffer(
    offerId = offerId,
    recyclerId = recyclerId,
    recyclerName = recyclerName,
    category = runCatching { MaterialCategory.valueOf(categoryName) }.getOrDefault(MaterialCategory.PCB_BOARDS),
    materialName = materialName,
    grade = grade,
    pricePerKg = pricePerKg,
    currency = currency,
    minQuantityKg = minQuantityKg,
    maxQuantityKg = maxQuantityKg,
    condition = condition,
    status = status,
    notes = notes,
    effectiveFrom = effectiveFrom,
    updatedAt = updatedAt,
    updatedBy = updatedBy,
    version = version
)

internal fun PriceHistoryEntity.toDomainModel(): PriceHistoryEntry = PriceHistoryEntry(
    historyId = historyId,
    priceId = priceId,
    materialName = materialName,
    category = runCatching { MaterialCategory.valueOf(categoryName) }.getOrDefault(MaterialCategory.PCB_BOARDS),
    grade = grade,
    pricePerKg = pricePerKg,
    marketMin = marketMin,
    marketMax = marketMax,
    currency = currency,
    changeType = changeType,
    changedBy = changedBy,
    changedAt = changedAt,
    version = version
)

internal fun PriceChangeQueueEntity.toDomainModel(): PendingPriceChange = PendingPriceChange(
    queueId = queueId,
    kind = runCatching { PriceChangeKind.valueOf(kind) }.getOrDefault(PriceChangeKind.REFERENCE_PRICE),
    targetId = targetId,
    actorUserId = actorUserId,
    actorRole = runCatching { RoleType.valueOf(actorRole) }.getOrDefault(RoleType.INFORMAL_COLLECTOR),
    payloadJson = payloadJson,
    baseVersion = baseVersion,
    status = runCatching { PriceChangeSyncStatus.valueOf(status) }
        .getOrDefault(PriceChangeSyncStatus.PENDING),
    errorMessage = errorMessage,
    serverVersion = serverVersion,
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun RecyclerEntity.toDomainModel(): AuthorizedRecycler {
    val accepted = acceptedCategoriesJoined.split(",")
        .mapNotNull { catStr -> runCatching { MaterialCategory.valueOf(catStr.trim()) }.getOrNull() }

    // Offered rates come from Supabase (`recycler_offered_rates`, cached in
    // ratesJson); any category without a cloud rate falls back to baseline.
    val cloudRates = decodeRecyclerRates(ratesJson)
    val rates = MaterialCategory.values().associateWith { cat ->
        cloudRates[cat.name] ?: cat.defaultRatePerKg
    }

    return AuthorizedRecycler(
        recyclerId = recyclerId,
        name = name,
        facilityLocation = facilityLocation,
        city = city,
        distanceKm = distanceKm,
        cpcbRegNo = cpcbRegNo,
        authorizationValidity = authorizationValidity,
        authorizationStatus = authorizationStatus,
        serviceArea = serviceArea,
        phone = phone,
        acceptedCategories = accepted,
        buyingRates = rates,
        doorstepPickup = doorstepPickup,
        minWeightForPickupKg = minWeightForPickupKg,
        paymentModesOffered = listOf(PaymentMode.CASH, PaymentMode.UPI),
        rating = rating,
        latitude = latitude,
        longitude = longitude
    )
}

private fun SafetyGuidelineEntity.toDomainModel(): HazardSafetyInfo {
    return HazardSafetyInfo(
        id = id,
        practiceTitle = practiceTitle,
        whyUnsafe = whyUnsafe,
        whatIsLost = whatIsLost,
        safeFormalAlternative = safeFormalAlternative,
        iconEmoji = iconEmoji,
        alertLevel = alertLevel
    )
}

fun RecyclerOffer.toEntity(): RecyclerOfferEntity = RecyclerOfferEntity(
    offerId = offerId,
    recyclerId = recyclerId,
    recyclerName = recyclerName,
    categoryName = category.name,
    materialName = materialName,
    grade = grade,
    pricePerKg = pricePerKg,
    currency = currency,
    minQuantityKg = minQuantityKg,
    maxQuantityKg = maxQuantityKg,
    condition = condition,
    status = status,
    notes = notes,
    effectiveFrom = effectiveFrom,
    updatedAt = updatedAt,
    updatedBy = updatedBy,
    version = version
)

fun PriceRecord.toEntity(): PriceRecordEntity = PriceRecordEntity(
    id = priceId,
    categoryName = category.name,
    subCategory = subCategory,
    location = location,
    prevailingBuyRate = prevailingBuyRate,
    marketMin = marketMin,
    marketMax = marketMax,
    trend = trend,
    trendPercentage = trendPercentage,
    unit = unit,
    dateUpdated = dateUpdated,
    keyMetalsJoined = keyMetals.joinToString(","),
    grade = grade,
    currency = currency,
    isActive = isActive,
    effectiveDate = effectiveDate,
    updatedAt = updatedAt,
    updatedBy = updatedBy,
    version = version
)

