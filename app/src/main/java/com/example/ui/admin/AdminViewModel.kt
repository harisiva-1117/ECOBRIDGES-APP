package com.example.ui.admin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.auth.ProfileRow
import com.example.data.CloudSyncManager
import com.example.data.EwasteDatabase
import com.example.data.EwasteRepository
import com.example.data.toEntity
import com.example.data.toMaterialLot
import com.example.model.MaterialLot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Database-driven snapshot for the CPCB / MoEFCC oversight portal. */
data class AdminMetrics(
    val collectors: Long = 0,
    val recyclers: Long = 0,
    val admins: Long = 0,
    val lots: Long = 0,
    val pendingLots: Long = 0,
    val confirmedLots: Long = 0,
    val totalWeightKg: Double = 0.0,
    val settledValueInr: Double = 0.0,
    val formalizationPercent: Double = 0.0
)

/**
 * Government-admin portal data source.
 *
 * Offline-first, mirroring the collector and recycler dashboards:
 *  - live Supabase admin role views (`v_admin_metrics`, `v_admin_lots`) when an
 *    authenticated admin session is present;
 *  - local Room records (lots created on this device) so the registry stays
 *    populated even during a demo / offline run;
 *  - the shared authorized-recycler registry (cloud) with the built-in default
 *    recyclers as a seed, so the national directory is never blank.
 *
 * No number is fabricated: metrics not covered by the live admin view are
 * derived from the merged lot records above.
 */
class AdminViewModel(application: Application) : AndroidViewModel(application) {

    private val database = EwasteDatabase.getDatabase(application, viewModelScope)
    private val repository = EwasteRepository(
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

    private val _metrics = MutableStateFlow(AdminMetrics())
    val metrics: StateFlow<AdminMetrics> = _metrics.asStateFlow()

    private val _lots = MutableStateFlow<List<MaterialLot>>(emptyList())
    val lots: StateFlow<List<MaterialLot>> = _lots.asStateFlow()

    /** Dynamic directory: every registered informal collector + their lots. */
    private val _collectors = MutableStateFlow<List<UserDirectoryEntry>>(emptyList())
    val collectors: StateFlow<List<UserDirectoryEntry>> = _collectors.asStateFlow()

    /** Dynamic directory: registered formal recyclers + the authorized registry. */
    private val _recyclers = MutableStateFlow<List<UserDirectoryEntry>>(emptyList())
    val recyclers: StateFlow<List<UserDirectoryEntry>> = _recyclers.asStateFlow()

    /** Cloud lots whose owner matches no registered profile (honest overflow). */
    private val _unlinkedLots = MutableStateFlow<List<MaterialLot>>(emptyList())
    val unlinkedLots: StateFlow<List<MaterialLot>> = _unlinkedLots.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                // 1. Live admin views (only populated for a real admin session).
                val dto = CloudSyncManager.fetchAdminMetrics()
                val remoteLots = CloudSyncManager.fetchAdminLots()

                // 2. Offline-first: merge with local device records.
                ensureRecyclerSeed()
                val localLots = repository.allLots.first()
                val mergedLots = (remoteLots.map { it.toMaterialLot() } + localLots)
                    .distinctBy { it.lotId }
                _lots.value = mergedLots

                // 3. Directories from the registry + profiles.
                val collectorProfiles = CloudSyncManager.fetchProfilesByRole("informal_collector")
                val recyclerProfiles = CloudSyncManager.fetchProfilesByRole("formal_recycler")

                val collectorEntries = buildCollectorEntries(collectorProfiles, remoteLots)
                val recyclerEntries = buildRecyclerEntries(recyclerProfiles, mergedLots)
                _collectors.value = collectorEntries
                _recyclers.value = recyclerEntries

                // 4. Honest overflow: cloud lots that match no registered profile.
                _unlinkedLots.value = if (collectorProfiles.isEmpty()) {
                    emptyList()
                } else {
                    val knownOwnerIds = collectorProfiles.mapNotNull { it.auth_user_id }.toSet()
                    remoteLots
                        .filter { it.collector_user_id !in knownOwnerIds }
                        .map { it.toMaterialLot() }
                }

                // 5. Metrics: authoritative cloud view first, local derivation as fallback.
                if (dto != null) {
                    val formalization = if (dto.lot_count > 0L) {
                        (dto.confirmed_lot_count.toDouble() / dto.lot_count.toDouble()) * 100.0
                    } else {
                        0.0
                    }
                    _metrics.value = AdminMetrics(
                        collectors = dto.collector_count,
                        recyclers = dto.recycler_count,
                        admins = dto.admin_count,
                        lots = dto.lot_count,
                        pendingLots = dto.pending_lot_count,
                        confirmedLots = dto.confirmed_lot_count,
                        totalWeightKg = dto.total_weight_kg,
                        settledValueInr = dto.settled_value_inr,
                        formalizationPercent = formalization
                    )
                } else if (mergedLots.isNotEmpty() || collectorEntries.isNotEmpty() || recyclerEntries.isNotEmpty()) {
                    val pending = mergedLots.count { !it.recyclerConfirmed }
                    val confirmed = mergedLots.count { it.recyclerConfirmed }
                    val formalization = if (mergedLots.isNotEmpty()) {
                        (confirmed.toDouble() / mergedLots.size.toDouble()) * 100.0
                    } else {
                        0.0
                    }
                    _metrics.value = AdminMetrics(
                        collectors = collectorEntries.size.toLong(),
                        recyclers = recyclerEntries.size.toLong(),
                        admins = 0,
                        lots = mergedLots.size.toLong(),
                        pendingLots = pending.toLong(),
                        confirmedLots = confirmed.toLong(),
                        totalWeightKg = mergedLots.sumOf { it.weightKg },
                        settledValueInr = mergedLots.sumOf { it.estimatedValueInr },
                        formalizationPercent = formalization
                    )
                } else {
                    _metrics.value = AdminMetrics()
                }

                val hasContent = dto != null || mergedLots.isNotEmpty() ||
                    collectorEntries.isNotEmpty() || recyclerEntries.isNotEmpty()
                _lastError.value = if (hasContent) {
                    null
                } else {
                    "The national registry has no records yet. Register collectors and recyclers, or create the first lot from the collector portal — it appears here automatically."
                }
            } catch (e: Exception) {
                _lastError.value = e.message
            } finally {
                _isSyncing.value = false
            }
        }
    }

    /**
     * Ensures the authorized-recycler registry has at least the built-in seed,
     * so the national directory is never blank even on a fresh offline device.
     */
    private suspend fun ensureRecyclerSeed() {
        try {
            if (database.recyclerDao().getRecyclerCount() == 0) {
                val remote = CloudSyncManager.fetchAuthorizedRecyclers()
                if (remote.isNotEmpty()) {
                    database.recyclerDao().insertRecyclers(remote.map { it.toEntity() })
                } else {
                    database.recyclerDao().insertRecyclers(EwasteRepository.getDefaultRecyclers())
                }
            }
        } catch (e: Exception) {
            try {
                if (database.recyclerDao().getRecyclerCount() == 0) {
                    database.recyclerDao().insertRecyclers(EwasteRepository.getDefaultRecyclers())
                }
            } catch (e2: Exception) {
                // Registry seeding is best effort.
            }
        }
    }

    private fun buildCollectorEntries(
        collectorProfiles: List<ProfileRow>,
        remoteLots: List<CloudSyncManager.CollectorLotDto>
    ): List<UserDirectoryEntry> {
        val lotsByCollector = remoteLots.groupBy { it.collector_user_id }
        return collectorProfiles.map { profile ->
            val lots = lotsByCollector[profile.auth_user_id].orEmpty()
                .map { it.toMaterialLot() }
            UserDirectoryEntry(profile = profile, lots = lots)
        }
    }

    private suspend fun buildRecyclerEntries(
        recyclerProfiles: List<ProfileRow>,
        mergedLots: List<MaterialLot>
    ): List<UserDirectoryEntry> {
        val profileEntries = recyclerProfiles.map { profile ->
            val routed = mergedLots.filter { lot ->
                (lot.matchedRecyclerId != null && lot.matchedRecyclerId == profile.statutory_identifier) ||
                    (!lot.matchedRecyclerName.isNullOrBlank() &&
                        (lot.matchedRecyclerName == profile.display_name || lot.matchedRecyclerName == profile.entity_name))
            }
            UserDirectoryEntry(profile = profile, lots = routed)
        }

        val remoteRegistry = try {
            CloudSyncManager.fetchAuthorizedRecyclers()
        } catch (e: Exception) {
            emptyList()
        }
        val registryEntries = remoteRegistry
            .map { remote ->
                val profile = ProfileRow(
                    role = "formal_recycler",
                    account_status = remote.authorization_status?.ifBlank { null } ?: "active",
                    display_name = remote.name,
                    entity_name = remote.name,
                    statutory_identifier = remote.cpcb_reg_no?.ifBlank { null } ?: remote.recycler_id,
                    phone_number = remote.phone?.ifBlank { null },
                    email = remote.contact_email?.ifBlank { null }
                )
                val routed = mergedLots.filter { lot ->
                    lot.matchedRecyclerId == remote.recycler_id || lot.matchedRecyclerName == remote.name
                }
                UserDirectoryEntry(profile = profile, lots = routed)
            }

        val localRecyclers = try {
            repository.allRecyclers.first()
        } catch (e: Exception) {
            emptyList()
        }
        val localEntries = localRecyclers
            .map { recycler ->
                val profile = ProfileRow(
                    role = "formal_recycler",
                    account_status = recycler.authorizationStatus,
                    display_name = recycler.name,
                    entity_name = recycler.name,
                    statutory_identifier = recycler.cpcbRegNo.ifBlank { recycler.recyclerId },
                    phone_number = recycler.phone.ifBlank { null }
                )
                val routed = mergedLots.filter { lot ->
                    lot.matchedRecyclerId == recycler.recyclerId || lot.matchedRecyclerName == recycler.name
                }
                UserDirectoryEntry(profile = profile, lots = routed)
            }

        return (profileEntries + registryEntries + localEntries).distinctBy {
            it.profile.statutory_identifier?.trim()?.lowercase() ?: it.profile.entity_name?.trim()?.lowercase() ?: it.displayName
        }
    }
}

/** One registered user plus the live lots attached to them. */
data class UserDirectoryEntry(
    val profile: ProfileRow,
    val lots: List<MaterialLot> = emptyList()
) {
    val lotCount: Int get() = lots.size
    val totalKg: Double get() = lots.sumOf { it.weightKg }
    val totalValueInr: Double get() = lots.sumOf { it.estimatedValueInr }
    val displayName: String get() =
        profile.display_name?.ifBlank { null }
            ?: profile.entity_name?.ifBlank { null }
            ?: profile.email?.substringBefore("@")
            ?: "Unnamed account"
    /** Privacy-preserving: middle digits of the phone are masked. */
    val maskedPhone: String get() {
        val digits = profile.phone_number?.filter { it.isDigit() }?.takeLast(10) ?: return "—"
        return if (digits.length == 10) "+91 ${digits.take(2)}•••••${digits.takeLast(3)}" else "+91 $digits"
    }
}