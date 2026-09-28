package com.example.ui.collector

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiLotAnalysis
import com.example.ai.AnalysisUnavailableException
import com.example.ai.GeminiScannerClient
import com.example.data.CloudSyncManager
import com.example.data.CloudSyncManager.RemoteCollector
import com.example.data.CollectorLocationEntity
import com.example.data.ConnectionRequestEntity
import com.example.data.EwasteDatabase
import com.example.data.EwasteRepository
import com.example.data.LotPhotoEntity
import com.example.data.encodeRecyclerRates
import com.example.data.toCollectorLotDto
import com.example.data.toEntity
import com.example.data.QuotationEntity
import com.example.data.TransactionLedgerEntity
import com.example.model.AuthorizedRecycler
import com.example.model.HazardSafetyInfo
import com.example.model.Language
import com.example.model.MaterialCategory
import com.example.model.MaterialLot
import com.example.model.PaymentMode
import com.example.model.PriceRecord
import com.example.model.UnitEconomicsData
import com.example.voice.VoiceEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class CollectorDashboardViewModel(
    application: Application,
    val voiceEngine: VoiceEngine? = null
) : AndroidViewModel(application) {

    private companion object {
        const val TAG = "CollectorDashboardVM"
    }

    private val repository: EwasteRepository

    val lots: StateFlow<List<MaterialLot>>
    val prices: StateFlow<List<PriceRecord>>
    val recyclers: StateFlow<List<AuthorizedRecycler>>
    val totalSettledEarnings: StateFlow<Double>
    val pendingDues: StateFlow<Double>
    val transactions: StateFlow<List<TransactionLedgerEntity>>

    val safetyGuidance: StateFlow<List<HazardSafetyInfo>>
    val unitEconomics: List<UnitEconomicsData>

    private val _isOfflineMode = MutableStateFlow(false)
    val isOfflineMode: StateFlow<Boolean> = _isOfflineMode.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow(System.currentTimeMillis())
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    val unsyncedCount: StateFlow<Int>

    // ---- Revenue dashboard state ---------------------------------------------
    val todaySettled: StateFlow<Double>
    val monthSettled: StateFlow<Double>
    val completedTxnCount: StateFlow<Int>
    val pendingTxnCount: StateFlow<Int>

    // ---- Photo capture / AI analysis state -------------------------------------
    val pendingPhotos: StateFlow<List<LotPhotoEntity>>

    private val _selectedLotForDetail = MutableStateFlow<MaterialLot?>(null)
    val selectedLotForDetail: StateFlow<MaterialLot?> = _selectedLotForDetail.asStateFlow()

    init {
        val database = EwasteDatabase.getDatabase(application, viewModelScope)
        repository = EwasteRepository(
            database.materialLotDao(),
            database.priceDao(),
            database.recyclerDao(),
            database.transactionLedgerDao(),
            database.safetyGuidelineDao(),
            database.lotPhotoDao(),
            database.collectorLocationDao(),
            database.connectionRequestDao(),
            database.quotationDao(),
            database.auditLogDao()
        )

        val nowMillis = System.currentTimeMillis()
        todaySettled = repository.settledEarningsBetween(startOfDayMillis(nowMillis), nowMillis).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0.0
        )
        monthSettled = repository.settledEarningsBetween(startOfMonthMillis(nowMillis), nowMillis).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0.0
        )

        unsyncedCount = repository.unsyncedLotsCount.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0
        )

        lots = repository.allLots.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        prices = repository.allPrices.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        recyclers = repository.allRecyclers.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        totalSettledEarnings = repository.totalSettledEarnings.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0.0
        )

        pendingDues = repository.pendingDues.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0.0
        )

        transactions = repository.allTransactions.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        completedTxnCount = repository.completedTxnCount.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0
        )

        pendingTxnCount = repository.pendingTxnCount.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0
        )

        pendingPhotos = repository.pendingPhotos.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        safetyGuidance = repository.allSafetyGuidelines.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            EwasteRepository.getSafetyGuidance()
        )
        unitEconomics = repository.getUnitEconomics()

        // Pull the shared Supabase source of truth into the local cache.
        refreshFromCloud()
    }

    /**
     * Single synchronisation point for the collector role. Pulls the
     * authoritative records from Supabase (role view `v_collector_my_lots` /
     * `v_collector_my_transactions`) plus the shared reference tables, and
     * mirrors them into the local Room cache. Every dashboard flow reads Room,
     * so a change made by the recycler surfaces here after a pull.
     */
    fun refreshFromCloud() {
        viewModelScope.launch {
            val database = EwasteDatabase.getDatabase(getApplication(), viewModelScope)

            // 1. Fetch public registries (authorized recyclers + rates, prices, safety)
            runCatching {
                val recyclers = CloudSyncManager.fetchAuthorizedRecyclers()
                if (recyclers.isNotEmpty()) {
                    val ratesByRecycler = CloudSyncManager.fetchRecyclerOfferedRates()
                        .groupBy({ it.recycler_id }, { it.category_name to it.rate_per_kg })
                        .mapValues { (_, pairs) -> pairs.toMap() }
                    database.recyclerDao().insertRecyclers(
                        recyclers.map { remote ->
                            remote.toEntity().copy(
                                ratesJson = encodeRecyclerRates(
                                    ratesByRecycler[remote.recycler_id].orEmpty()
                                )
                            )
                        }
                    )
                } else if (database.recyclerDao().getRecyclerCount() == 0) {
                    database.recyclerDao().insertRecyclers(EwasteRepository.getDefaultRecyclers())
                }
            }.onFailure {
                if (database.recyclerDao().getRecyclerCount() == 0) {
                    database.recyclerDao().insertRecyclers(EwasteRepository.getDefaultRecyclers())
                }
            }

            runCatching {
                val prices = CloudSyncManager.fetchMaterialPrices()
                if (prices.isNotEmpty()) database.priceDao().insertPrices(prices.map { it.toEntity() })
            }
            runCatching {
                val guides = CloudSyncManager.fetchSafetyGuidelines()
                if (guides.isNotEmpty()) database.safetyGuidelineDao().insertGuidelines(guides.map { it.toEntity() })
            }

            // 2. Fetch user-specific records (lots, transactions) if logged in
            val userId = currentUserId()
            if (userId.isNotBlank()) {
                runCatching {
                    CloudSyncManager.fetchCollectorLots(userId).forEach {
                        database.materialLotDao().insertLot(it.toEntity())
                    }
                }
                runCatching {
                    CloudSyncManager.fetchCollectorTransactions(userId).forEach {
                        database.transactionLedgerDao().insertTransaction(it.toEntity())
                    }
                }
            }
            _lastSyncTimestamp.value = System.currentTimeMillis()
        }
    }

    private val _isCreatingLot = MutableStateFlow(false)
    val isCreatingLot: StateFlow<Boolean> = _isCreatingLot.asStateFlow()

    private val _createError = MutableStateFlow<String?>(null)
    val createError: StateFlow<String?> = _createError.asStateFlow()

    fun clearCreateError() {
        _createError.value = null
    }

    fun createNewLot(
        category: MaterialCategory,
        subCategory: String,
        weightKg: Double,
        condition: String,
        location: String,
        gpsCoordinates: String,
        matchedRecycler: AuthorizedRecycler?,
        paymentMode: PaymentMode,
        draftLotId: String? = null,
        onCreated: (MaterialLot) -> Unit = {}
    ) {
        // Idempotent guard: repeated taps must never create duplicate lot records.
        if (_isCreatingLot.value) return
        if (weightKg <= 0.0 || weightKg.isNaN() || weightKg.isInfinite()) {
            _createError.value = "Enter a valid weight greater than 0 kg before creating the lot."
            return
        }
        _isCreatingLot.value = true
        _createError.value = null
        viewModelScope.launch {
            try {
                val newLot = repository.createLot(
                    category = category,
                    subCategory = subCategory,
                    weightKg = weightKg,
                    condition = condition,
                    location = location,
                    gpsCoordinates = gpsCoordinates,
                    matchedRecycler = matchedRecycler,
                    paymentMode = paymentMode,
                    isOffline = _isOfflineMode.value
                )
                if (draftLotId != null) {
                    repository.attachDraftPhotosToLot(draftLotId, newLot.lotId)
                }
                // Push the new record to the shared Supabase source of truth so the
                // formal recycler and government admin observe the same row. Offline
                // creates stay local with isSynced=false until connectivity returns.
                if (!_isOfflineMode.value) {
                    val uid = currentUserId()
                    if (uid.isNotBlank()) {
                        runCatching { CloudSyncManager.upsertLot(newLot.toCollectorLotDto(uid)) }
                            .onFailure { Log.w(TAG, "Cloud push deferred (offline/error): ${it.message}") }
                    }
                }
                endCreationSession()
                addAuditLog(
                    action = "LOT_CREATED",
                    detailJson = "{\"lotId\":\"${newLot.lotId}\",\"category\":\"${category.name}\",\"weightKg\":$weightKg}"
                )
                onCreated(newLot)
            } catch (e: Exception) {
                Log.e(TAG, "Lot creation failed", e)
                _createError.value = "Could not create lot: ${e.message ?: "unexpected error"}. Your draft is kept."
            } finally {
                _isCreatingLot.value = false
            }
        }
    }

    fun selectLot(lot: MaterialLot?) {
        _selectedLotForDetail.value = lot
    }

    fun confirmRecyclerHandover(lotId: String, verifiedWeight: Double? = null, markPaid: Boolean = true) {
        viewModelScope.launch {
            repository.confirmRecyclerHandover(lotId, verifiedWeight, markPaid)
        }
    }

    fun toggleOfflineSimulation() {
        val wasOffline = _isOfflineMode.value
        _isOfflineMode.value = !wasOffline
        // When connectivity is restored, trigger synchronization automatically
        if (wasOffline) {
            manualSync()
        }
    }

    fun manualSync(onComplete: (Int) -> Unit = {}) {
        if (_isOfflineMode.value) return // cannot sync if in offline mode
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val database = EwasteDatabase.getDatabase(getApplication(), viewModelScope)
                val authService = com.example.auth.SupabaseAuthService.getInstance(getApplication())
                val userId = authService.authenticatedUser.value?.userId ?: ""
                val unsyncedLots = database.materialLotDao().getUnsyncedLotsNow()
                val transactions = database.transactionLedgerDao().getAllTransactionsNow()
                val outcome = com.example.data.CloudSyncManager.syncLotsAndTransactions(
                    lots = unsyncedLots,
                    transactions = transactions,
                    userId = userId
                )
                if (outcome.serverReachable) {
                    repository.syncOfflineData()
                    // Push work-in-progress artifacts (photos, location, requests, quotations, audit).
                    val photoContext = getApplication<Application>()
                    database.lotPhotoDao().getPendingPhotosNow().forEach { photo ->
                        CloudSyncManager.uploadLotPhoto(photoContext, photo, userId) { status, remotePath ->
                            viewModelScope.launch { repository.markPhotoStatus(photo.photoId, status, remotePath) }
                        }
                    }
                    database.collectorLocationDao().getLocationNow(userId)?.let {
                        CloudSyncManager.pushCollectorLocation(it)
                    }
                    CloudSyncManager.pushConnectionRequests(userId, database.connectionRequestDao().getAllNow())
                    CloudSyncManager.pushQuotations(userId, database.quotationDao().getAllNow())
                    CloudSyncManager.pushAuditLogs(userId, database.auditLogDao().getAllNow())
                    refreshNearbyCollectors()
                }
                if (outcome.lotsPushed > 0 || outcome.transactionsPushed > 0) {
                    Log.i(TAG, "Cloud sync pushed ${outcome.lotsPushed} lots, ${outcome.transactionsPushed} transactions")
                }
                _lastSyncTimestamp.value = System.currentTimeMillis()
                // Re-read the shared source of truth so recycler-side changes to
                // this collector's records surface (real-time refetch sync).
                refreshFromCloud()
            } finally {
                _isSyncing.value = false
            }
            onComplete(0)
        }
    }

    // ---- Revenue dashboard state ---------------------------------------------
    // (todaySettled/monthSettled/completedTxnCount/pendingTxnCount declared above)

    // ---- Photo capture / AI analysis state -------------------------------------
    private val _aiAnalysis = MutableStateFlow<AiLotAnalysis?>(null)
    val aiAnalysis: StateFlow<AiLotAnalysis?> = _aiAnalysis.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _aiUnavailableReason = MutableStateFlow<String?>(null)
    val aiUnavailableReason: StateFlow<String?> = _aiUnavailableReason.asStateFlow()

    private val _duplicateHint = MutableStateFlow<MaterialLot?>(null)
    val duplicateHint: StateFlow<MaterialLot?> = _duplicateHint.asStateFlow()

    // ---- Location sharing state -------------------------------------------------
    private val _location = MutableStateFlow<CollectorLocationEntity?>(null)
    val location: StateFlow<CollectorLocationEntity?> = _location.asStateFlow()

    private val _nearbyCollectors = MutableStateFlow<List<RemoteCollector>>(emptyList())
    val nearbyCollectors: StateFlow<List<RemoteCollector>> = _nearbyCollectors.asStateFlow()

    // ---- Connections & quotations --------------------------------------------------
    private val _connections = MutableStateFlow<List<ConnectionRequestEntity>>(emptyList())
    val connections: StateFlow<List<ConnectionRequestEntity>> = _connections.asStateFlow()

    private val _quotations = MutableStateFlow<List<QuotationEntity>>(emptyList())
    val quotations: StateFlow<List<QuotationEntity>> = _quotations.asStateFlow()

    fun beginCreationSession() {
        _draftLotId.value = "DRAFT-${System.currentTimeMillis()}"
    }

    fun endCreationSession() {
        _draftLotId.value = null
        _duplicateHint.value = null
        _aiAnalysis.value = null
        _aiUnavailableReason.value = null
    }

    fun addDraftPhoto(localUri: String, onAdded: (String) -> Unit = {}) {
        if (localUri.isBlank()) return
        viewModelScope.launch {
            val userId = currentUserId()
            val sessionDraftId = _draftLotId.value ?: run {
                beginCreationSession()
                _draftLotId.value ?: "DRAFT-${System.currentTimeMillis()}"
            }
            val photo = LotPhotoEntity(
                photoId = "PHOTO-${System.currentTimeMillis()}-${(1000..9999).random()}",
                lotId = sessionDraftId,
                collectorUserId = userId,
                localUri = localUri,
                remotePath = null,
                mimeType = "image/jpeg",
                uploadStatus = "PENDING",
                isPrimary = false,
                createdAt = System.currentTimeMillis()
            )
            repository.insertDraftPhoto(photo)
            onAdded(photo.photoId)
        }
    }

    fun removeDraftPhoto(photoId: String) {
        if (photoId.isBlank()) return
        viewModelScope.launch { repository.removePhoto(photoId) }
    }

    /** Analyzes exactly one captured draft photo (never fabricates a result). */
    fun analyzeDraftPhoto(photoId: String, categoryHint: MaterialCategory, language: Language) {
        if (photoId.isBlank()) return
        viewModelScope.launch {
            val photo = EwasteDatabase.getDatabase(getApplication(), viewModelScope)
                .lotPhotoDao().getPhotoById(photoId)
            val bitmap = photo?.let { loadBitmap(getApplication(), it.localUri) }
            if (bitmap == null) {
                _aiAnalysis.value = null
                _aiUnavailableReason.value =
                    "AI identification unavailable. Please select the e-waste category manually."
                return@launch
            }
            analyzePhotos(listOf(bitmap), language, categoryHint)
        }
    }

    fun analyzePhotos(bitmaps: List<Bitmap>, language: Language, categoryHint: MaterialCategory?) {
        if (bitmaps.isEmpty()) return
        viewModelScope.launch {
            _isAnalyzing.value = true
            _aiUnavailableReason.value = null
            val result = GeminiScannerClient.analyzeLotPhotos(bitmaps, language, categoryHint)
            if (result.isSuccess) {
                _aiAnalysis.value = result.getOrNull()
            } else {
                _aiAnalysis.value = null
                _aiUnavailableReason.value = result.exceptionOrNull()?.message
                    ?: "AI analysis failed. Please enter the details manually."
            }
            _isAnalyzing.value = false
        }
    }

    /** Analyzes the photos already captured for the current draft session. Loads
     *  the real image bytes from the persisted draft photos (never fabricated). */
    fun analyzeDraftPhotos(categoryHint: MaterialCategory, language: Language) {
        viewModelScope.launch {
            val sessionDraftId = _draftLotId.value
            val photos = if (sessionDraftId != null) {
                EwasteDatabase.getDatabase(getApplication(), viewModelScope).lotPhotoDao()
                    .getPendingPhotosNow().filter { it.lotId == sessionDraftId }
            } else {
                repository.getPendingPhotosNow()
            }
            val bitmaps = photos.mapNotNull { loadBitmap(getApplication(), it.localUri) }
            analyzePhotos(bitmaps, language, categoryHint)
        }
    }

    private fun loadBitmap(context: android.content.Context, uri: String): Bitmap? {
        return try {
            val resolver = context.contentResolver
            val parsedUri = Uri.parse(uri)
            val openStream = {
                if (parsedUri.scheme == "content" || parsedUri.scheme == "file") {
                    resolver.openInputStream(parsedUri)
                } else {
                    val file = java.io.File(uri)
                    if (file.exists()) java.io.FileInputStream(file) else resolver.openInputStream(parsedUri)
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            runCatching { openStream()?.use { BitmapFactory.decodeStream(it, null, bounds) } }
            val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
            var inSampleSize = 1
            while (maxDim / (inSampleSize * 2) >= 1280) inSampleSize *= 2
            val opts = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
            openStream()?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (e: Exception) {
            Log.w(TAG, "Bitmap load failed for $uri: ${e.message}")
            null
        }
    }

    fun clearAiAnalysis() {
        _aiAnalysis.value = null
        _aiUnavailableReason.value = null
    }

    private val _draftLotId = MutableStateFlow<String?>(null)
    val draftLotId: StateFlow<String?> = _draftLotId.asStateFlow()

    fun checkDuplicateCandidate(category: MaterialCategory?, weightKg: Double?) {
        if (category == null || weightKg == null || weightKg <= 0) {
            _duplicateHint.value = null
            return
        }
        viewModelScope.launch {
            _duplicateHint.value = repository.findDuplicateCandidate(category, weightKg)
        }
    }

    // ---- Location + nearby collectors --------------------------------------------
    fun toggleLocationSharing(share: Boolean, latitude: Double, longitude: Double, areaLabel: String?) {
        val userId = currentUserId()
        viewModelScope.launch {
            val entry = CollectorLocationEntity(
                collectorUserId = userId,
                latitude = latitude,
                longitude = longitude,
                areaLabel = areaLabel,
                isSharingOn = share,
                updatedAt = System.currentTimeMillis()
            )
            repository.saveCollectorLocation(entry)
            _location.value = entry
            if (share) {
                CloudSyncManager.pushCollectorLocation(entry)
                refreshNearbyCollectors(entry)
            }
        }
    }

    fun refreshNearbyCollectors(self: CollectorLocationEntity? = null) {
        viewModelScope.launch {
            val entry = self ?: currentLocationEntity()
            val remote = CloudSyncManager.fetchNearbyCollectors()
            val filtered = if (entry != null) {
                remote
                    .filter { it.collector_user_id != entry.collectorUserId }
                    .map { it to CloudSyncManager.distanceKm(entry.latitude, entry.longitude, it.latitude, it.longitude) }
                    .filter { it.second <= 50.0 }
                    .sortedBy { it.second }
                    .map { it.first }
            } else {
                remote
            }
            _nearbyCollectors.value = filtered
        }
    }

    private suspend fun currentLocationEntity(): CollectorLocationEntity? {
        val userId = currentUserId()
        return repository.collectorLocationNow(userId)
    }

    // ---- Connections & quotes -----------------------------------------------------
    fun requestConnection(recycler: AuthorizedRecycler) {
        viewModelScope.launch {
            val request = repository.sendConnectionRequest(
                collectorUserId = currentUserId(),
                recyclerId = recycler.recyclerId,
                recyclerName = recycler.name
            )
            CloudSyncManager.pushConnectionRequests(currentUserId(), listOf(request))
            refreshConnections()
            addAuditLog("CONNECTION_REQUESTED", "{\"recyclerId\":\"${recycler.recyclerId}\"}")
        }
    }

    fun respondToQuotation(quotationId: String, accept: Boolean) {
        viewModelScope.launch {
            repository.respondToQuotation(quotationId, accept)
            refreshQuotations()
            val quotation = _quotations.value.find { it.quotationId == quotationId }
            if (quotation != null) {
                CloudSyncManager.pushQuotations(currentUserId(), databaseQuotationsNow())
            }
        }
    }

    private suspend fun databaseQuotationsNow(): List<QuotationEntity> =
        EwasteDatabase.getDatabase(getApplication(), viewModelScope).quotationDao().getAllNow()

    private fun refreshConnections() {
        viewModelScope.launch {
            _connections.value = EwasteDatabase.getDatabase(getApplication(), viewModelScope)
                .connectionRequestDao().getAllNow()
        }
    }

    private fun refreshQuotations() {
        viewModelScope.launch {
            _quotations.value = EwasteDatabase.getDatabase(getApplication(), viewModelScope)
                .quotationDao().getAllNow()
        }
    }

    fun refreshWorkbench() {
        refreshConnections()
        refreshQuotations()
        refreshFromCloud()
        viewModelScope.launch {
            _location.value = repository.collectorLocationNow(currentUserId())
        }
    }

    fun addAuditLog(action: String, detailJson: String) {
        viewModelScope.launch {
            repository.addAuditLog(currentUserId(), action, detailJson)
        }
    }

    private fun currentUserId(): String =
        com.example.auth.SupabaseAuthService.getInstance(getApplication())
            .authenticatedUser.value?.userId ?: ""

    private fun startOfDayMillis(millis: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun startOfMonthMillis(millis: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * Flag lots whose quoted rate is implausible relative to the seeded market
     * range (basic anomaly detection). Exposed to the UI to warn the collector
     * before a handover.
     */
    val anomalousLotIds: StateFlow<Set<String>> = combine(lots, prices) { lotList, priceList ->
        val rangeByCategory = priceList.associate { price ->
            price.category to (price.marketMin to price.marketMax)
        }
        lotList.filter { lot ->
            val range = rangeByCategory[lot.category]
            range != null && (lot.quotedRatePerKg < range.first * 0.9 || lot.quotedRatePerKg > range.second * 1.1)
        }.mapTo(mutableSetOf()) { it.lotId }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptySet()
    )

    fun speakAllSafetyGuidelines(language: Language) {
        val overview = when (language) {
            Language.ENGLISH -> "Critical E-Waste Safety Advisory. Never burn cables open-air because it creates dioxins and ruins copper scrap value. Never leach circuit boards in backyard acid baths as toxic nitrogen dioxide and heavy metals escape. Never shatter CRT monitors or crush lithium-ion batteries. Handover all materials intact to authorized recyclers for certified payments."
            Language.HINDI -> "महत्वपूर्ण ई-कचरा सुरक्षा सलाह। तारों को खुली आग में कभी न जलाएं। एसिड में मदरबोर्ड कभी न गलाएं। सीआरटी मॉनिटर या लिथियम बैटरी को न तोड़ें। पूर्ण सरकारी भुगतान के लिए सभी सामग्री अधिकृत रिसाइकलर को ही सौंपें।"
            Language.MARATHI -> "महत्त्वाची ई-कचरा सुरक्षा सूचना. उघड्यावर वायर जाळू नका. ऍसिडमध्ये मदरबोर्ड विरघळवू नका. सीआरटी स्क्रीन किंवा लिथियम बॅटरी फोडू नका. संपूर्ण शासकीय मोबदल्यासाठी अधिकृत रिसायकलर्सकडेच साहित्य द्या."
            Language.TAMIL -> "முக்கியமான மின்-கழிவு பாதுகாப்பு அறிவுரை. கேபிள்களை வெட்டவெளியில் எரிக்கவே கூடாது — இது டையாக்ஸின் உருவாக்கி செப்பு கழிவு மதிப்பைக் கெடுக்கும். சர்க்யூட் போர்டுகளை வீட்டில் அமிலக் குளியலில் போடவே கூடாது — நச்சு நைட்ரஜன் டை ஆக்சைடும் கன உலோகங்களும் வெளியேறும். CRT மானிட்டர்களை உடைக்கவோ லித்தியம் பேட்டரிகளை நசுக்கவோ கூடாது. சான்றளிக்கப்பட்ட கட்டணத்திற்காக அனைத்து பொருட்களையும் அப்படியே அங்கீகரிக்கப்பட்ட மறுசுழற்சியாளர்களிடம் ஒப்படைக்கவும்."
        }
        voiceEngine?.speak(overview, language)
    }

    fun speakPrice(price: PriceRecord, language: Language) {
        val message = when (language) {
            Language.ENGLISH -> "${price.category.titleEn}. Current government registered buying rate is ${price.prevailingBuyRate.toInt()} rupees per kilogram in ${price.location}. Trend is ${price.trend} by ${price.trendPercentage} percent."
            Language.HINDI -> "${price.category.titleHi}. वर्तमान सरकारी अधिकृत खरीद दर ${price.prevailingBuyRate.toInt()} रुपये प्रति किलोग्राम है। रुझान ${price.trendPercentage} प्रतिशत बढ़ा है।"
            Language.MARATHI -> "${price.category.titleMr}. चालू शासकीय अधिकृत खरेदी दर ${price.prevailingBuyRate.toInt()} रुपये प्रति किलो आहे. कल ${price.trendPercentage} टक्के वाढला आहे."
            Language.TAMIL -> "${price.category.titleEn}. தற்போதைய அரசு பதிவு செய்யப்பட்ட கொள்முதல் விகிதம் ${price.prevailingBuyRate.toInt()} ரூபாய் ஒரு கிலோ, ${price.location} இல். போக்கு ${price.trend} ${price.trendPercentage} சதவீதம்."
        }
        voiceEngine?.speak(message, language)
    }

    fun speakSafety(safety: HazardSafetyInfo, language: Language) {
        val message = when (language) {
            Language.ENGLISH -> "Warning: ${safety.practiceTitle}. ${safety.whyUnsafe}. Safe practice: ${safety.safeFormalAlternative}"
            Language.HINDI -> "चेतावनी: ${safety.practiceTitle}. ${safety.whyUnsafe}. सुरक्षित उपाय: ${safety.safeFormalAlternative}"
            Language.MARATHI -> "धोका सूचना: ${safety.practiceTitle}. ${safety.whyUnsafe}. सुरक्षित पर्याय: ${safety.safeFormalAlternative}"
            Language.TAMIL -> "எச்சரிக்கை: ${safety.practiceTitle}. ${safety.whyUnsafe}. பாதுகாப்பான நடைமுறை: ${safety.safeFormalAlternative}"
        }
        voiceEngine?.speak(message, language)
    }

    fun speakLotEstimate(lot: MaterialLot, language: Language) {
        val message = when (language) {
            Language.ENGLISH -> "Lot number ${lot.lotId}. Total weight ${lot.weightKg} kilograms of ${lot.category.titleEn}. Total estimated value is ${lot.estimatedValueInr.toInt()} rupees. Recycler: ${lot.matchedRecyclerName ?: "Authorized Aggregator"}."
            Language.HINDI -> "लॉट नंबर ${lot.lotId}. कुल वजन ${lot.weightKg} किलोग्राम। कुल अनुमानित मूल्य ${lot.estimatedValueInr.toInt()} रुपये है।"
            Language.MARATHI -> "लॉट क्रमांक ${lot.lotId}. एकूण वजन ${lot.weightKg} किलो. एकूण अंदाजे रक्कम ${lot.estimatedValueInr.toInt()} रुपये आहे."
            Language.TAMIL -> "லாட் எண் ${lot.lotId}. மொத்த எடை ${lot.weightKg} கிலோ ${lot.category.titleEn}. மொத்த மதிப்பீடு ${lot.estimatedValueInr.toInt()} ரூபாய். மறுசுழற்சியாளர்: ${lot.matchedRecyclerName ?: "Authorized Aggregator"}."
        }
        voiceEngine?.speak(message, language)
    }
}
