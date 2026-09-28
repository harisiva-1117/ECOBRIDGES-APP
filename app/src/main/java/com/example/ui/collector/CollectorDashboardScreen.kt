package com.example.ui.collector

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CloudSyncManager.RemoteCollector
import com.example.data.CollectorLocationEntity
import com.example.data.ConnectionRequestEntity
import com.example.data.QuotationEntity
import com.example.data.TransactionLedgerEntity
import com.example.model.AuthorizedRecycler
import com.example.model.HazardSafetyInfo
import com.example.model.Language
import com.example.model.LotStatus
import com.example.model.MaterialCategory
import com.example.model.MaterialLot
import com.example.model.PaymentMode
import com.example.ui.map.MapCollectionPointsScreen
import com.example.ui.scan.EwasteCameraScannerScreen
import com.example.ui.voice.BottomsFloatingDockInset
import com.example.ui.theme.BackgroundCream
import com.example.ui.theme.EcoBlueAccent
import com.example.ui.theme.EcoBlueBg
import com.example.ui.theme.EcoBorder
import com.example.ui.theme.EcoGreenBright
import com.example.ui.theme.EcoGreenDeep
import com.example.ui.theme.EcoGreenPrimary
import com.example.ui.theme.EcoMint
import com.example.ui.theme.EcoOrangeAccent
import com.example.ui.theme.EcoOrangeBg
import com.example.ui.theme.EcoTextPrimary
import com.example.ui.theme.EcoTextSecondary
import com.example.ui.theme.EcoWhite
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.ForestGreenDark
import com.example.ui.theme.ForestGreenPrimary
import com.example.ui.theme.MintBorder
import com.example.ui.theme.MintLight
import com.example.ui.theme.MintPill
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryMuted
import com.example.ui.theme.WarningAmber
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectorDashboardScreen(
    viewModel: CollectorDashboardViewModel,
    collectorPhone: String,
    language: Language,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lots by viewModel.lots.collectAsState()
    val prices by viewModel.prices.collectAsState()
    val recyclers by viewModel.recyclers.collectAsState()
    val totalSettled by viewModel.totalSettledEarnings.collectAsState()
    val pendingDues by viewModel.pendingDues.collectAsState()
    val todaySettled by viewModel.todaySettled.collectAsState()
    val monthSettled by viewModel.monthSettled.collectAsState()
    val collectorLocation by viewModel.location.collectAsState()
    val nearbyCollectors by viewModel.nearbyCollectors.collectAsState()
    val connections by viewModel.connections.collectAsState()
    val quotations by viewModel.quotations.collectAsState()

    val transactions by viewModel.transactions.collectAsState()
    val safetyGuidanceList by viewModel.safetyGuidance.collectAsState()
    val isOffline by viewModel.isOfflineMode.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val unsyncedCount by viewModel.unsyncedCount.collectAsState()
    val lastSyncTimestamp by viewModel.lastSyncTimestamp.collectAsState()
    val selectedLot by viewModel.selectedLotForDetail.collectAsState()
    val anomalousLotIds by viewModel.anomalousLotIds.collectAsState()

    var activeTab by remember { mutableIntStateOf(0) } // 0: Home, 1: My Lots, 2: Recyclers (voice), 3: Connect (voice), 4: Safety (voice), 5: Earnings, 6: Profile

    // This screen has a bottom navigation bar; lift the global voice dock above
    // it so the bottom bar stays fully tappable (dock returns to the very bottom
    // on every screen without a bottom bar).
    DisposableEffect(Unit) {
        BottomsFloatingDockInset.height.value = 84.dp
        onDispose {
            BottomsFloatingDockInset.height.value = 0.dp
        }
    }
    var showMapView by remember { mutableStateOf(false) }
    var selectedRecyclerIdForMap by remember { mutableStateOf<String?>(null) }
    var showScannerView by remember { mutableStateOf(false) }
    // AI-guided multi-photo capture (ONNX + Gemini) opened by the primary Scan CTA.
    var showAiCapture by remember { mutableStateOf(false) }
    // Header notification menu
    var showNotifications by remember { mutableStateOf(false) }
    // "Filter Lots" state shared by the Home tab preview and the My Lots tab.
    var statusFilter by remember { mutableStateOf<String?>(null) }
    var categoryFilter by remember { mutableStateOf<String?>(null) }
    var sortNewestFirst by remember { mutableStateOf(true) }

    // Apply All Status / All Categories / Date filters to the live lot list.
    val filteredLots = remember(lots, statusFilter, categoryFilter, sortNewestFirst) {
        val base = lots
            .filter { statusFilter == null || it.status.name == statusFilter }
            .filter { categoryFilter == null || it.category.name == categoryFilter }
            .sortedByDescending { it.collectionTimestamp }
        if (sortNewestFirst) base else base.reversed()
    }
    val hasActiveFilters = statusFilter != null || categoryFilter != null || !sortNewestFirst
    // Persisted across activity recreation (e.g. returning from the system
    // camera / photo picker) so an in-progress Create Lot draft is not lost.
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var initialCategoryForLot by remember { mutableStateOf<MaterialCategory?>(null) }
    var initialPhotoForLot by rememberSaveable { mutableStateOf<String?>(null) }
    var initialPhotosForLot by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var capturedLotItems by remember { mutableStateOf<List<ScannedLotItem>>(emptyList()) }

    // Collect voice-driven collector operations (hands-free dashboard control)
    val collectorRouter = viewModel.voiceEngine?.router
    LaunchedEffect(collectorRouter) {
        collectorRouter?.collectorEvents?.collect { action ->
            when (action) {
                is com.example.voice.CollectorAction.OpenRevenueSummaryTab -> activeTab = 5
                is com.example.voice.CollectorAction.AnalyzeCurrentLot -> {
                    if (!showCreateDialog) {
                        initialCategoryForLot = null
                        initialPhotoForLot = null
                        initialPhotosForLot = emptyList()
                        capturedLotItems = emptyList()
                        showCreateDialog = true
                    }
                }
                is com.example.voice.CollectorAction.TurnOnLocationSharing -> {
                    activeTab = 3
                    val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as? android.location.LocationManager
                    val provider = lm?.getProviders(true)?.firstOrNull()
                    val loc = provider?.let { lm.getLastKnownLocation(it) }
                    viewModel.toggleLocationSharing(
                        true,
                        loc?.latitude ?: 19.0760,
                        loc?.longitude ?: 72.8777,
                        null
                    )
                }
                is com.example.voice.CollectorAction.OpenNearbyCollectorsTab,
                is com.example.voice.CollectorAction.OpenConnectionsTab -> activeTab = 3
                is com.example.voice.CollectorAction.OpenRecyclersTab,
                is com.example.voice.CollectorAction.RequestRecyclerQuote -> activeTab = 2
            }
        }
    }

    if (showScannerView) {
        EwasteCameraScannerScreen(
            language = language,
            onBack = { showScannerView = false },
            onProceedToCreateLot = { scanResult, bitmap ->
                // Prefill the Create Lot workflow (category + captured photo) so the
                // collector reviews the image, enters the real weight and explicitly
                // confirms before any lot record is written.
                showScannerView = false
                initialCategoryForLot = scanResult.identifiedCategory
                initialPhotoForLot = bitmap?.let { saveBitmapToCache(context, it) }
                showCreateDialog = true
            },
            onSpeakText = { text, lang ->
                viewModel.voiceEngine?.speak(text, lang)
            }
        )
        return
    }

    // AI-guided multi-photo capture (clear camera-first flow for informal collector).
    if (showAiCapture) {
        com.example.ui.capture.AiCaptureScreen(
            language = language,
            mode = com.example.ui.capture.AiCaptureMode.COLLECTOR,
            expectedCategory = null,
            onBack = { showAiCapture = false },
            onFinish = { items ->
                showAiCapture = false
                if (items.isNotEmpty()) {
                    val majority = items.mapNotNull { it.category }
                        .groupingBy { it }
                        .eachCount()
                        .maxByOrNull { it.value }?.key
                    val fallback = majority ?: MaterialCategory.PCB_BOARDS
                    initialCategoryForLot = fallback
                    initialPhotoForLot = items.firstOrNull()?.imageUri
                    initialPhotosForLot = items.map { it.imageUri }
                    capturedLotItems = items.map {
                        ScannedLotItem(
                            name = it.componentName,
                            category = it.category ?: fallback,
                            weightKg = it.weightKg,
                            estimatedValueInr = it.estimatedValueInr,
                            imageUri = it.imageUri
                        )
                    }
                    showCreateDialog = true
                }
            },
            onSpeakText = { text, lang ->
                viewModel.voiceEngine?.speak(text, lang)
            }
        )
        return
    }

    if (showMapView) {
        MapCollectionPointsScreen(
            recyclers = recyclers,
            language = language,
            onBack = {
                showMapView = false
                selectedRecyclerIdForMap = null
            },
            initialRecyclerId = selectedRecyclerIdForMap,
            onSelectRecyclerForLot = { selectedRecycler ->
                showMapView = false
                selectedRecyclerIdForMap = null
                showCreateDialog = true
            }
        )
        return
    }

    Scaffold(
        topBar = {
            // ECOBRIDGES collector header: title + subtitle | online/offline status | bell.
            // statusBarsPadding() keeps every header element below the Android system
            // status bar (edge-to-edge safe-area handling) without hard-coded margins.
            Surface(
                color = EcoWhite,
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("collector_nav_back")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = EcoTextSecondary
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (language) {
                                    Language.ENGLISH -> "E-Waste Collector Hub"
                                    Language.HINDI -> "ई-कचरा संग्राहक केंद्र"
                                    Language.MARATHI -> "ई-कचरा संकलक केंद्र"
                                    Language.TAMIL -> "மின்-கழிவு சேகரிப்பாளர் மையம்"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = EcoTextPrimary
                            )
                            Text(
                                text = when (language) {
                                    Language.ENGLISH -> "Collect • Segregate • Recycle"
                                    Language.HINDI -> "संग्रह करें • अलग करें • रीसायकल करें"
                                    Language.MARATHI -> "संकलन करा • वर्गीकरण करा • रीसायकल करा"
                                    Language.TAMIL -> "சேகரிக்கவும் → பிரிக்கவும் → மறுசுழற்சி செய்யவும்"
                                },
                                fontSize = 11.sp,
                                color = EcoTextSecondary
                            )
                        }

                        // Online / Offline status pill (keeps existing toggle behaviour)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isOffline) WarningAmber.copy(alpha = 0.18f)
                            else if (unsyncedCount > 0) WarningAmber.copy(alpha = 0.12f)
                            else SuccessGreen.copy(alpha = 0.15f),
                            modifier = Modifier
                                .clickable {
                                    if (isOffline) {
                                        viewModel.toggleOfflineSimulation()
                                    } else {
                                        viewModel.manualSync()
                                    }
                                }
                                .testTag("toggle_offline_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isOffline) Icons.Default.CloudOff else Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = if (isOffline) WarningAmber else SuccessGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isSyncing) "Syncing..."
                                    else if (isOffline) "Offline"
                                    else if (unsyncedCount > 0) "$unsyncedCount Queued"
                                    else "Online",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isOffline) WarningAmber else SuccessGreen
                                )
                            }
                        }

                        // Notifications bell with live (non-fabricated) status dot.
                        Box {
                            IconButton(onClick = { showNotifications = true }) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "Notifications",
                                    tint = if (pendingDues > 0 || unsyncedCount > 0) EcoTextPrimary else EcoTextSecondary
                                )
                            }
                            if (pendingDues > 0 || unsyncedCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 6.dp, end = 6.dp)
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(EcoOrangeAccent)
                                )
                            }
                            DropdownMenu(
                                expanded = showNotifications,
                                onDismissRequest = { showNotifications = false }
                            ) {
                                Text(
                                    text = when (language) {
                                        Language.ENGLISH -> "Notifications"
                                        Language.HINDI -> "सूचनाएं"
                                        Language.MARATHI -> "सूचना"
                                        Language.TAMIL -> "அறிவிப்புகள்"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = EcoTextPrimary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (pendingDues > 0)
                                                "₹${pendingDues.toInt()} pending on weigh-in"
                                            else "No pending payouts",
                                            fontSize = 13.sp
                                        )
                                    },
                                    onClick = { showNotifications = false }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (unsyncedCount > 0)
                                                "$unsyncedCount changes queued for CPCB sync"
                                            else "All records synced with CPCB",
                                            fontSize = 13.sp
                                        )
                                    },
                                    onClick = { showNotifications = false }
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 6.dp
            ) {
                val navItems = listOf(
                    Triple("Home", Icons.Default.Home, 0),
                    Triple("My Lots", Icons.Default.Inventory2, 1),
                    Triple("Recyclers", Icons.Default.LocationOn, 2),
                    Triple("Earnings", Icons.Default.CurrencyRupee, 5),
                    Triple("Profile", Icons.Default.Person, 6)
                )
                for ((label, icon, index) in navItems) {
                    val isSelected = activeTab == index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { activeTab = index },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                modifier = Modifier.size(24.dp),
                                tint = if (isSelected) EcoGreenPrimary else EcoTextSecondary
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) EcoGreenPrimary else EcoTextSecondary
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = EcoGreenPrimary,
                            selectedTextColor = EcoGreenPrimary,
                            indicatorColor = EcoMint
                        ),
                        modifier = Modifier.testTag("nav_tab_$index")
                    )
                }
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundCream)
                .padding(paddingValues)
        ) {
            when (activeTab) {
                0 -> HomeTab(
                    lots = filteredLots,
                    recyclers = recyclers,
                    todaySettled = todaySettled,
                    monthSettled = monthSettled,
                    totalSettled = totalSettled,
                    pendingDues = pendingDues,
                    isOffline = isOffline,
                    isSyncing = isSyncing,
                    unsyncedCount = unsyncedCount,
                    lastSyncTimestamp = lastSyncTimestamp,
                    language = language,
                    onToggleOffline = { viewModel.toggleOfflineSimulation() },
                    onManualSync = { viewModel.manualSync() },
                    onSelectLot = { lot -> viewModel.selectLot(lot) },
                    onScanEwaste = { showAiCapture = true },
                    onOpenRecyclers = { activeTab = 2 },
                    onOpenMap = {
                        selectedRecyclerIdForMap = null
                        showMapView = true
                    },
                    onOpenMapForRecycler = { recyclerId ->
                        selectedRecyclerIdForMap = recyclerId
                        showMapView = true
                    },
                    onCallRecycler = { phone ->
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                        runCatching { context.startActivity(intent) }
                    },
                    onStartAssistant = { viewModel.voiceEngine?.startListening(language) },
                    anomalousLotIds = anomalousLotIds,
                    hasActiveFilters = hasActiveFilters,
                    statusFilter = statusFilter,
                    categoryFilter = categoryFilter,
                    sortNewestFirst = sortNewestFirst,
                    onStatusFilter = { statusFilter = it },
                    onCategoryFilter = { categoryFilter = it },
                    onSortChange = { sortNewestFirst = it },
                    onClearFilters = {
                        statusFilter = null
                        categoryFilter = null
                        sortNewestFirst = true
                    }
                )
                1 -> MyLotsTab(
                    lots = filteredLots,
                    language = language,
                    onSelectLot = { lot -> viewModel.selectLot(lot) },
                    onScanEwaste = { showAiCapture = true },
                    anomalousLotIds = anomalousLotIds,
                    hasActiveFilters = hasActiveFilters,
                    statusFilter = statusFilter,
                    categoryFilter = categoryFilter,
                    sortNewestFirst = sortNewestFirst,
                    onStatusFilter = { statusFilter = it },
                    onCategoryFilter = { categoryFilter = it },
                    onSortChange = { sortNewestFirst = it },
                    onClearFilters = {
                        statusFilter = null
                        categoryFilter = null
                        sortNewestFirst = true
                    }
                )
                2 -> RecyclersTab(
                    recyclers = recyclers,
                    language = language,
                    onCallRecycler = { phone ->
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                        context.startActivity(intent)
                    },
                    onOpenMap = { recyclerId ->
                        selectedRecyclerIdForMap = recyclerId
                        showMapView = true
                    },
                    onNavigateToRecycler = { lat, lng ->
                        val geoUri = Uri.parse("google.navigation:q=$lat,$lng")
                        val mapIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                            setPackage("com.google.android.apps.maps")
                        }
                        runCatching {
                            context.startActivity(mapIntent)
                        }.onFailure {
                            val webMap = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng")
                            )
                            context.startActivity(webMap)
                        }
                    }
                )
                3 -> LocationConnectionsTab(
                    recyclers = recyclers,
                    collectorLocation = collectorLocation,
                    nearbyCollectors = nearbyCollectors,
                    connections = connections,
                    quotations = quotations,
                    language = language,
                    onToggleSharing = { share, lat, lng, area ->
                        viewModel.toggleLocationSharing(share, lat, lng, area)
                    },
                    onRefreshNearby = { viewModel.refreshNearbyCollectors() },
                    onRequestConnection = { recycler -> viewModel.requestConnection(recycler) },
                    onRespondToQuotation = { id, accept -> viewModel.respondToQuotation(id, accept) }
                )
                4 -> SafetyGuidanceSection(
                    safetyItems = safetyGuidanceList,
                    language = language,
                    onSpeakSafety = { item -> viewModel.speakSafety(item, language) },
                    onSpeakAll = { viewModel.speakAllSafetyGuidelines(language) }
                )
                5 -> LedgerAndEconomicsTab(
                    transactions = transactions,
                    totalSettled = totalSettled,
                    pendingDues = pendingDues,
                    economicsList = viewModel.unitEconomics,
                    language = language
                )
                6 -> CollectorProfileTab(
                    collectorPhone = collectorPhone,
                    collectorLocation = collectorLocation,
                    isOffline = isOffline,
                    isSyncing = isSyncing,
                    unsyncedCount = unsyncedCount,
                    lastSyncTimestamp = lastSyncTimestamp,
                    language = language,
                    onToggleOffline = { viewModel.toggleOfflineSimulation() },
                    onManualSync = { viewModel.manualSync() },
                    onOpenSafety = { activeTab = 4 },
                    onSignOut = onBack,
                    onOpenLots = { activeTab = 1 },
                    onCreateLot = {
                        initialCategoryForLot = null
                        initialPhotoForLot = null
                        initialPhotosForLot = emptyList()
                        capturedLotItems = emptyList()
                        showCreateDialog = true
                    }
                )
            }
        }
    }

    // Create Lot Dialog
    if (showCreateDialog) {
        CreateLotDialog(
            viewModel = viewModel,
            initialCategory = initialCategoryForLot,
            initialPhotoUri = initialPhotoForLot,
            initialPhotoUris = initialPhotosForLot,
            initialItems = capturedLotItems,
            collectorLabel = if (collectorPhone.trimStart().startsWith("+")) {
                collectorPhone.trim()
            } else {
                "+91 ${collectorPhone.trim()}"
            },
            availableRecyclers = recyclers,
            language = language,
            onDismiss = {
                showCreateDialog = false
                initialPhotoForLot = null
                initialPhotosForLot = emptyList()
                capturedLotItems = emptyList()
            },
            onLotCreated = { category, subCategory, weightKg, condition, location, gpsCoordinates, matchedRecycler, paymentMode, draftLotId ->
                viewModel.createNewLot(
                    category = category,
                    subCategory = subCategory,
                    weightKg = weightKg,
                    condition = condition,
                    location = location,
                    gpsCoordinates = gpsCoordinates,
                    matchedRecycler = matchedRecycler,
                    paymentMode = paymentMode,
                    draftLotId = draftLotId,
                    onCreated = { newLot ->
                        showCreateDialog = false
                        initialPhotoForLot = null
                        initialPhotosForLot = emptyList()
                        capturedLotItems = emptyList()
                        viewModel.selectLot(newLot)
                    }
                )
            }
        )
    }

    // Handover Detail & QR Dialog
    if (selectedLot != null) {
        HandoverDetailDialog(
            lot = selectedLot!!,
            language = language,
            onDismiss = { viewModel.selectLot(null) },
            onConfirmRecyclerWeighIn = { lotId ->
                viewModel.confirmRecyclerHandover(lotId, markPaid = true)
                viewModel.selectLot(null)
            },
            onSpeakLot = { lot -> viewModel.speakLotEstimate(lot, language) }
        )
    }
}

/**
 * Persists a scanner-captured bitmap to the app cache so it can be attached to
 * the Create Lot draft (never kept only in memory). Returns a content URI string
 * via FileProvider, or null if the write failed.
 */
private fun saveBitmapToCache(context: android.content.Context, bitmap: Bitmap): String? {
    return try {
        val dir = File(context.cacheDir, "photo_cache").apply { mkdirs() }
        val file = File(dir, "scan_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        androidx.core.content.FileProvider
            .getUriForFile(context, "${context.packageName}.fileprovider", file)
            .toString()
    } catch (e: Exception) {
        Log.e("CollectorDashboard", "Failed to persist scanned photo", e)
        null
    }
}

// ===========================================================================
// ECOBRIDGES Collector Home — spec order: header (Scaffold top bar) -> 2x2
// stat cards -> Scan E-Waste -> CPCB Sync -> Nearby Recyclers -> Filter Lots
// -> Active & Completed Digital Lots -> AI Voice Assistant (bottom bar stays on
// the Scaffold). All numbers come from the live Room ledger; no fabricated
// figures.
// ===========================================================================
@Composable
fun HomeTab(
    lots: List<MaterialLot>,
    recyclers: List<AuthorizedRecycler>,
    todaySettled: Double,
    monthSettled: Double,
    totalSettled: Double,
    pendingDues: Double,
    isOffline: Boolean,
    isSyncing: Boolean,
    unsyncedCount: Int,
    lastSyncTimestamp: Long,
    language: Language,
    onToggleOffline: () -> Unit,
    onManualSync: () -> Unit,
    onSelectLot: (MaterialLot) -> Unit,
    onScanEwaste: () -> Unit,
    onOpenRecyclers: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenMapForRecycler: ((String) -> Unit)? = null,
    onCallRecycler: (String) -> Unit = {},
    onStartAssistant: () -> Unit,
    anomalousLotIds: Set<String>,
    hasActiveFilters: Boolean,
    statusFilter: String?,
    categoryFilter: String?,
    sortNewestFirst: Boolean,
    onStatusFilter: (String?) -> Unit,
    onCategoryFilter: (String?) -> Unit,
    onSortChange: (Boolean) -> Unit,
    onClearFilters: () -> Unit
) {
    val activeLots = lots.count {
        it.status == LotStatus.MATCHED ||
            it.status == LotStatus.HANDOVER_PENDING ||
            it.status == LotStatus.VALUATED
    }

    val calendarToday = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    val startOfToday = calendarToday.timeInMillis
    calendarToday.set(java.util.Calendar.DAY_OF_MONTH, 1)
    val startOfMonth = calendarToday.timeInMillis
    val todayLots = lots.count { it.collectionTimestamp >= startOfToday }
    val monthLots = lots.count { it.collectionTimestamp >= startOfMonth }

    val lastSyncedTime = if (lastSyncTimestamp > 0L) {
        java.text.SimpleDateFormat(
            "h:mm a",
            java.util.Locale.ENGLISH
        ).format(java.util.Date(lastSyncTimestamp))
    } else {
        "—"
    }

    val formalRecyclers = remember(recyclers) {
        if (recyclers.size >= 4) {
            recyclers
        } else {
            (recyclers + com.example.data.EwasteRepository.getDefaultAuthorizedRecyclers()).distinctBy { it.recyclerId }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)
    ) {
        // ----- 2 x 2 stat cards (Today / Month / Total / Pending) -----
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        label = when (language) {
                            Language.ENGLISH -> "Today's Earnings"
                            Language.HINDI -> "आज की कमाई"
                            Language.MARATHI -> "आजची कमाई"
                            Language.TAMIL -> "இன்றைய வருவாய்"
                        },
                        value = "₹${todaySettled.toInt()}",
                        subtitle = "From $todayLots lots",
                        bgColor = EcoMint,
                        valueColor = EcoGreenPrimary
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        label = "Total Cash Settled",
                        value = "₹${totalSettled.toInt()}",
                        subtitle = "Verified CPCB payout",
                        bgColor = EcoGreenDeep,
                        valueColor = Color.White,
                        labelColor = Color.White.copy(alpha = 0.8f),
                        subtitleColor = EcoGreenBright
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        label = when (language) {
                            Language.ENGLISH -> "This Month"
                            Language.HINDI -> "इस महीने"
                            Language.MARATHI -> "या महिन्यात"
                            Language.TAMIL -> "இந்த மாதம்"
                        },
                        value = "₹${monthSettled.toInt()}",
                        subtitle = "From $monthLots lots",
                        bgColor = EcoBlueBg,
                        valueColor = EcoBlueAccent
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        label = when (language) {
                            Language.ENGLISH -> "Pending Handover"
                            Language.HINDI -> "हस्तांतरण शेष"
                            Language.MARATHI -> "हस्तांतरण प्रलंबित"
                            Language.TAMIL -> "நிலுவையிலுள்ள கையளிப்பு"
                        },
                        value = "₹${pendingDues.toInt()}",
                        subtitle = "Payable on weigh-in",
                        bgColor = EcoOrangeBg,
                        valueColor = EcoOrangeAccent
                    )
                }
            }
        }

        // ----- Scan E-Waste (camera CTA first — keeps the CTA above the floating voice dock) -----
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = EcoGreenPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onScanEwaste() }
                    .testTag("scan_ewaste_action_btn")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (language) {
                                Language.ENGLISH -> "Scan E-Waste"
                                Language.HINDI -> "स्कैन करें"
                                Language.MARATHI -> "स्कॅन करा"
                                Language.TAMIL -> "மின்-கழிவு ஸ்கேன்"
                            },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = when (language) {
                                Language.ENGLISH -> "AI camera valuation • create a digital lot"
                                Language.HINDI -> "AI कैमरा मूल्यांकन • डिजिटल लॉट बनाएं"
                                Language.MARATHI -> "AI कॅमेरा मूल्यांकन • डिजिटल लॉट तयार करा"
                                Language.TAMIL -> "AI கேமரா மதிப்பீடு — டிஜிட்டல் லாட் உருவாக்கு"
                            },
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // ----- Authorized Formal Recyclers & Ratings Section -----
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (language) {
                                Language.ENGLISH -> "Authorized Formal Recyclers"
                                Language.HINDI -> "अधिकृत औपचारिक रीसायकलर"
                                Language.MARATHI -> "अधिकृत औपचारिक रीसायकलर"
                                Language.TAMIL -> "அங்கீகரிக்கப்பட்ட மறுசுழற்சியாளர்கள்"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = EcoTextPrimary
                        )
                        Text(
                            text = when (language) {
                                Language.ENGLISH -> "CPCB certified partners with ratings & certified weighment"
                                Language.HINDI -> "CPCB सत्यापित पार्टनर - रेटिंग एवं प्रमाणित वजन"
                                Language.MARATHI -> "CPCB प्रमाणित भागीदार - रेटिंग आणि खात्रीशीर वजन"
                                Language.TAMIL -> "மதிப்பீடுகள் & சான்றளிக்கப்பட்ட எடைப்பதிவு கொண்ட CPCB சான்றளிக்கப்பட்ட கூட்டாளர்கள்"
                            },
                            fontSize = 11.sp,
                            color = EcoTextSecondary
                        )
                    }
                    TextButton(onClick = { onOpenRecyclers() }) {
                        Text(
                            text = "View All (${formalRecyclers.size})",
                            color = ForestGreenPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    formalRecyclers.forEach { rec ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MintBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenMapForRecycler?.invoke(rec.recyclerId) ?: onOpenRecyclers() }
                                .testTag("formal_recycler_${rec.recyclerId.lowercase()}")
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                // Header: Name + Star Rating Badge
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(MintLight),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = "🏭", fontSize = 18.sp)
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = rec.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = TextPrimaryDark,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${rec.city} • ${rec.cpcbRegNo}",
                                                fontSize = 11.sp,
                                                color = SuccessGreen,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Prominent Star Rating Badge
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFFFFF8E1),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD54F))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = "Rating",
                                                tint = WarningAmber,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "${rec.rating} ★",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = Color(0xFF795548)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Location & Distance
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = TextSecondaryMuted,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${rec.facilityLocation} • ${rec.distanceKm} km away",
                                        fontSize = 11.sp,
                                        color = TextSecondaryMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Best Rate & Service
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val topRate = rec.buyingRates.values.maxOrNull()?.toInt() ?: 0
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = EcoGreenDeep.copy(alpha = 0.08f)
                                    ) {
                                        Text(
                                            text = "Top Rate: ₹$topRate/kg",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = EcoGreenDeep,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }

                                    if (rec.doorstepPickup) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = SuccessGreen.copy(alpha = 0.1f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.LocalShipping,
                                                    contentDescription = null,
                                                    tint = SuccessGreen,
                                                    modifier = Modifier.size(11.dp)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = "Doorstep Pickup",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = SuccessGreen
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Action Buttons (Call & Map)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MintLight,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                onOpenMapForRecycler?.invoke(rec.recyclerId) ?: onOpenMap()
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 7.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.LocationOn,
                                                contentDescription = null,
                                                tint = ForestGreenPrimary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "View Map",
                                                color = ForestGreenPrimary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = EcoGreenDeep,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { onCallRecycler(rec.phone) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 7.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Call,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Call Recycler",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ----- CPCB Sync (live offline/sync state + Sync Now) -----
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = EcoWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, EcoBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(EcoMint),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isOffline) Icons.Default.CloudOff else Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = if (isOffline) WarningAmber else SuccessGreen,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = when {
                                        isSyncing -> "CPCB Database Syncing..."
                                        isOffline -> "CPCB Database Offline"
                                        unsyncedCount > 0 -> "CPCB Database Pending Sync"
                                        else -> "CPCB Database Synced"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = EcoTextPrimary
                                )
                                Text(
                                    text = if (lastSyncTimestamp > 0L)
                                        "Last synced at $lastSyncedTime"
                                    else "Not synced yet",
                                    fontSize = 11.sp,
                                    color = EcoTextSecondary
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = EcoGreenPrimary,
                            modifier = Modifier.clickable { onManualSync() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isSyncing) "Syncing..." else "Sync Now",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = EcoMint,
                            border = androidx.compose.foundation.BorderStroke(1.dp, EcoBorder)
                        ) {
                            Text(
                                text = "Offline-First active",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = EcoGreenPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Text(
                            text = if (isOffline) "Records stored locally"
                            else if (unsyncedCount > 0) "$unsyncedCount changes queued"
                            else "All records synced",
                            fontSize = 10.sp,
                            color = EcoTextSecondary
                        )
                    }
                }
            }
        }

        // ----- Nearby Recyclers (full list section) -----
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = when (language) {
                            Language.ENGLISH -> "Nearby Recyclers"
                            Language.HINDI -> "आसपास के रीसायकलर"
                            Language.MARATHI -> "जवळील रीसायकलर"
                            Language.TAMIL -> "அருகிலுள்ள மறுசுழற்சியாளர்கள்"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = EcoTextPrimary
                    )
                    Text(
                        text = when (language) {
                            Language.ENGLISH -> "Find authorized recyclers near your location"
                            Language.HINDI -> "अपने आस-पास अधिकृत रीसायकलर खोजें"
                            Language.MARATHI -> "तुमच्या जवळील अधिकृत रीसायकलर शोधा"
                            Language.TAMIL -> "உங்கள் இருப்பிடத்திற்கு அருகிலுள்ள அங்கீகரிக்கப்பட்ட மறுசுழற்சியாளர்களைக் கண்டறியவும்"
                        },
                        fontSize = 11.sp,
                        color = EcoTextSecondary
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = EcoMint,
                        modifier = Modifier
                            .clickable { onOpenMap() }
                            .testTag("home_open_live_map_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = EcoGreenPrimary, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Live Map",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = EcoGreenPrimary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (language) {
                            Language.ENGLISH -> "View All"
                            Language.HINDI -> "सभी देखें"
                            Language.MARATHI -> "सर्व पहा"
                            Language.TAMIL -> "அனைத்தையும் காண்"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = EcoGreenPrimary,
                        modifier = Modifier
                            .clickable { onOpenRecyclers() }
                            .padding(4.dp)
                    )
                }
            }
        }

        items(recyclers) { rec ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = EcoWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, EcoBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenMapForRecycler?.invoke(rec.recyclerId) ?: onOpenRecyclers() }
                    .testTag("home_recycler_card_${rec.recyclerId.lowercase()}")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(EcoMint),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "🏭", fontSize = 20.sp)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = rec.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = EcoTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = EcoOrangeAccent,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "${rec.rating}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EcoTextPrimary
                                )
                                Text(
                                    text = " • ${rec.facilityLocation}",
                                    fontSize = 11.sp,
                                    color = EcoTextSecondary
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = EcoMint
                        ) {
                            Text(
                                text = "${rec.distanceKm} km",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = EcoGreenPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val bestRate = rec.buyingRates.values.maxOrNull()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (bestRate != null) "₹${bestRate.toInt()}/kg" else "Rate unavailable",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = EcoGreenPrimary
                        )
                        Text(
                            text = rec.acceptedCategories
                                .take(3)
.joinToString(", ") { cat ->
                                        when (language) {
                                            Language.ENGLISH -> cat.titleEn
                                            Language.HINDI -> cat.titleHi
                                            Language.MARATHI -> cat.titleMr
                                            Language.TAMIL -> cat.titleTa
                                        }
                                    },
                            fontSize = 10.sp,
                            color = EcoTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            RecyclerBadge(
                                label = if (rec.doorstepPickup) "Pickup Available" else "Self Drop-off",
                                icon = Icons.Default.LocalShipping
                            )
                            RecyclerBadge(
                                label = if (rec.paymentModesOffered.any { it == PaymentMode.UPI }) "Digital Receipt" else "Instant Cash",
                                icon = Icons.Default.QrCode2
                            )
                        }
                        Text(
                            text = "View Details",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = EcoGreenPrimary
                        )
                    }
                }
            }
        }

        // ----- Filter Lots -----
        item {
            Column {
                Text(
                    text = when (language) {
                        Language.ENGLISH -> "Filter Lots"
                        Language.HINDI -> "लॉट फ़िल्टर करें"
                        Language.MARATHI -> "लॉट फिल्टर करा"
                        Language.TAMIL -> "லாட்களை வடிகட்டு"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = EcoTextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                LotFilterRow(
                    language = language,
                    statusFilter = statusFilter,
                    categoryFilter = categoryFilter,
                    sortNewestFirst = sortNewestFirst,
                    hasActiveFilters = hasActiveFilters,
                    onStatusFilter = onStatusFilter,
                    onCategoryFilter = onCategoryFilter,
                    onSortChange = onSortChange,
                    onClearFilters = onClearFilters
                )
            }
        }

        // ----- Active & Completed Digital Lots -----
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = when (language) {
                            Language.ENGLISH -> "Active & Completed Digital Lots"
                            Language.HINDI -> "सक्रिय एवं पूर्ण डिजिटल लॉट"
                            Language.MARATHI -> "सक्रिय व पूर्ण झालेले लॉट्स"
                            Language.TAMIL -> "செயலில் & முடிந்த டிஜிட்டல் லாட்கள்"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = EcoTextPrimary
                    )
                    Text(
                        text = "$activeLots active • ${lots.count { it.status == LotStatus.PAYMENT_COMPLETED }} completed",
                        fontSize = 11.sp,
                        color = EcoTextSecondary
                    )
                }
                if (hasActiveFilters) {
                    Text(
                        text = "Clear",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = EcoGreenPrimary,
                        modifier = Modifier
                            .clickable { onClearFilters() }
                            .padding(4.dp)
                    )
                }
            }
        }

        if (lots.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "📦", fontSize = 36.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (hasActiveFilters) "No lots match the selected filters"
                            else "No Digital Lots Created Yet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = EcoTextPrimary
                        )
                        Text(
                            text = if (hasActiveFilters) "Clear filters or scan new e-waste"
                            else "Tap 'Scan E-Waste' to photograph and valuate materials",
                            fontSize = 12.sp,
                            color = EcoTextSecondary
                        )
                    }
                }
            }
        } else {
            items(lots) { lot ->
                LotItemCard(
                    lot = lot,
                    language = language,
                    isAnomalous = lot.lotId in anomalousLotIds,
                    onClick = { onSelectLot(lot) }
                )
            }
        }

        // ----- AI Voice Assistant -----
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = EcoGreenDeep),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onStartAssistant() }
                    .testTag("ai_assistant_bar")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (language) {
                                Language.ENGLISH -> "Ask AI E-Waste Assistant"
                                Language.HINDI -> "AI ई-वेस्ट असिस्टेंट से पूछें"
                                Language.MARATHI -> "AI ई-वेस्ट सहायकाला विचारा"
                                Language.TAMIL -> "AI மின்-கழிவு உதவியாளரிடம் கேளுங்கள்"
                            },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = when (language) {
                                Language.ENGLISH -> "Voice queries on disposal, prices & CPCB rules"
                                Language.HINDI -> "निपटान, दरें और CPCB नियमों पर वॉइस क्वेरी"
                                Language.MARATHI -> "विसर्जन, दर आणि CPCB नियमांवरील व्हॉइस प्रश्न"
                                Language.TAMIL -> "அகற்றல், விலைகள் & CPCB விதிகள் குறித்த குரல் கேள்விகள்"
                            },
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(140.dp))
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier,
    label: String,
    value: String,
    subtitle: String,
    bgColor: Color,
    valueColor: Color,
    labelColor: Color = EcoTextSecondary,
    subtitleColor: Color = EcoTextSecondary
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = label,
                color = labelColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = valueColor,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp
            )
            Text(
                text = subtitle,
                color = subtitleColor,
                fontSize = 10.sp
            )
        }
    }
}

// Shared, fully functional lot filter row (Home preview + My Lots).
@Composable
fun LotFilterRow(
    language: Language,
    statusFilter: String?,
    categoryFilter: String?,
    sortNewestFirst: Boolean,
    hasActiveFilters: Boolean,
    onStatusFilter: (String?) -> Unit,
    onCategoryFilter: (String?) -> Unit,
    onSortChange: (Boolean) -> Unit,
    onClearFilters: () -> Unit
) {
    val statusItems = LotStatus.entries.map { it.name to it.getLabel(language) }
    val categoryItems = MaterialCategory.entries.map { it.name to it.getTitle(language) }
    val sortItems = listOf(
        "newest" to when (language) {
            Language.ENGLISH -> "Date (Newest)"
            Language.HINDI -> "तारीख (नवीन)"
            Language.MARATHI -> "तारीख (नवीन)"
            Language.TAMIL -> "தேதி (புதியது)"
        },
        "oldest" to when (language) {
            Language.ENGLISH -> "Date (Oldest)"
            Language.HINDI -> "तारीख (जुने)"
            Language.MARATHI -> "तारीख (जुने)"
            Language.TAMIL -> "தேதி (பழையது)"
        }
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChipDropdown(
                selectedLabel = statusItems.firstOrNull { it.first == statusFilter }?.second
                    ?: when (language) {
                        Language.ENGLISH -> "All Status"
                        Language.HINDI -> "सभी स्थिति"
                        Language.MARATHI -> "सर्व स्थिती"
                        Language.TAMIL -> "அனைத்து நிலைகள்"
                    },
                items = statusItems,
                modifier = Modifier.weight(1f),
                onSelect = { onStatusFilter(it.takeIf { key -> key != statusFilter }) }
            )
            FilterChipDropdown(
                selectedLabel = categoryItems.firstOrNull { it.first == categoryFilter }?.second
                    ?: when (language) {
                        Language.ENGLISH -> "All Categories"
                        Language.HINDI -> "सभी श्रेणियां"
                        Language.MARATHI -> "सर्व श्रेणी"
                        Language.TAMIL -> "அனைத்து வகைகள்"
                    },
                items = categoryItems,
                modifier = Modifier.weight(1f),
                onSelect = { onCategoryFilter(it.takeIf { key -> key != categoryFilter }) }
            )
            FilterChipDropdown(
                selectedLabel = sortItems.firstOrNull {
                    (it.first == "newest") == sortNewestFirst
                }?.second ?: sortItems.first().second,
                items = sortItems,
                modifier = Modifier.weight(1f),
                onSelect = { onSortChange(it == "newest") }
            )
        }
        if (hasActiveFilters) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = when (language) {
                        Language.ENGLISH -> "Clear"
                        Language.HINDI -> "साफ़ करें"
                        Language.MARATHI -> "साफ करा"
                        Language.TAMIL -> "அழி"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = EcoGreenPrimary,
                    modifier = Modifier
                        .clickable { onClearFilters() }
                        .padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun FilterChipDropdown(
    selectedLabel: String,
    items: List<Pair<String, String>>,
    modifier: Modifier,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = EcoWhite,
            border = androidx.compose.foundation.BorderStroke(1.dp, EcoBorder),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = EcoTextPrimary,
                    modifier = Modifier.weight(1f, fill = true)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = EcoTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { (key, label) ->
                DropdownMenuItem(
                    text = { Text(text = label, fontSize = 13.sp) },
                    onClick = {
                        expanded = false
                        onSelect(key)
                    }
                )
            }
        }
    }
}

// Full-screen "My Lots" list sharing the same real filters as the Home tab.
@Composable
fun MyLotsTab(
    lots: List<MaterialLot>,
    language: Language,
    onSelectLot: (MaterialLot) -> Unit,
    onScanEwaste: () -> Unit,
    anomalousLotIds: Set<String>,
    hasActiveFilters: Boolean,
    statusFilter: String?,
    categoryFilter: String?,
    sortNewestFirst: Boolean,
    onStatusFilter: (String?) -> Unit,
    onCategoryFilter: (String?) -> Unit,
    onSortChange: (Boolean) -> Unit,
    onClearFilters: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "My Lots",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = EcoTextPrimary
                    )
                    Text(
                        text = "${lots.size} digital lots",
                        fontSize = 12.sp,
                        color = EcoTextSecondary
                    )
                }
                if (hasActiveFilters) {
                    Text(
                        text = "Clear",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = EcoGreenPrimary,
                        modifier = Modifier
                            .clickable { onClearFilters() }
                            .padding(4.dp)
                    )
                }
            }
        }

        item {
            LotFilterRow(
                language = language,
                statusFilter = statusFilter,
                categoryFilter = categoryFilter,
                sortNewestFirst = sortNewestFirst,
                hasActiveFilters = hasActiveFilters,
                onStatusFilter = onStatusFilter,
                onCategoryFilter = onCategoryFilter,
                onSortChange = onSortChange,
                onClearFilters = onClearFilters
            )
        }

        if (lots.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "📦", fontSize = 36.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (hasActiveFilters) "No lots match the selected filters"
                            else "No Digital Lots Created Yet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = EcoTextPrimary
                        )
                        Text(
                            text = if (hasActiveFilters) "Clear filters or scan new e-waste"
                            else "Tap 'Scan E-Waste' to photograph and valuate materials",
                            fontSize = 12.sp,
                            color = EcoTextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = EcoGreenPrimary,
                            modifier = Modifier.clickable { onScanEwaste() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Scan E-Waste",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        } else {
            items(lots) { lot ->
                LotItemCard(
                    lot = lot,
                    language = language,
                    isAnomalous = lot.lotId in anomalousLotIds,
                    onClick = { onSelectLot(lot) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(140.dp))
        }
    }
}

// Collector profile: identity, CPCB verification, real sync/location status and
// quick access. No fabricated stats.
@Composable
fun CollectorProfileTab(
    collectorPhone: String,
    collectorLocation: CollectorLocationEntity?,
    isOffline: Boolean,
    isSyncing: Boolean,
    unsyncedCount: Int,
    lastSyncTimestamp: Long,
    language: Language,
    onToggleOffline: () -> Unit,
    onManualSync: () -> Unit,
    onOpenSafety: () -> Unit,
    onSignOut: () -> Unit,
    onOpenLots: () -> Unit,
    onCreateLot: () -> Unit
) {
    val sharingOn = collectorLocation?.isSharingOn == true
    val phoneLabel = "+91 ${collectorPhone.trim().removePrefix("+91").trim()}"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Identity card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = EcoGreenPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "E-Waste Collector",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = phoneLabel,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = null,
                                tint = EcoGreenBright,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "CPCB Verified Collector",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Real sync + location status
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, EcoBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (sharingOn) Icons.Default.Share else Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = if (sharingOn) SuccessGreen else TextSecondaryMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (sharingOn) "Location sharing ON" else "Location sharing OFF",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = EcoTextPrimary
                            )
                        }
                        Text(
                            text = if (isOffline) "Offline" else "Online",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isOffline) WarningAmber else SuccessGreen
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    // Compact manual sync action
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = EcoMint,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isOffline) onToggleOffline() else onManualSync()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = EcoGreenPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isOffline) "Switch Online to sync"
                                else if (isSyncing) "Syncing with CPCB..."
                                else if (unsyncedCount > 0) "$unsyncedCount changes queued — tap to sync"
                                else "All records synced with CPCB",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = EcoGreenPrimary
                            )
                        }
                    }
                }
            }
        }

        // Quick access
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, EcoBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(6.dp)) {
                    ProfileRow(
                        emoji = "📦",
                        title = "My Lots",
                        subtitle = "View all digital lots",
                        onClick = onOpenLots
                    )
                    ProfileRow(
                        emoji = "📷",
                        title = "Scan E-Waste",
                        subtitle = "Create a new digital lot",
                        onClick = onCreateLot
                    )
                    ProfileRow(
                        emoji = "🛡️",
                        title = "Safety Guidelines",
                        subtitle = "Hazardous e-waste handling safety",
                        onClick = onOpenSafety
                    )
                }
            }
        }

        // Sign out
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSignOut() }
                    .testTag("profile_sign_out")
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = ErrorRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Sign Out",
                        color = ErrorRed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(140.dp))
        }
    }
}

@Composable
private fun ProfileRow(
    emoji: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = emoji, fontSize = 20.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = EcoTextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = EcoTextSecondary
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = EcoTextSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun RecyclerBadge(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = EcoMint,
        modifier = Modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ForestGreenPrimary,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = ForestGreenPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) ForestGreenPrimary else MintLight,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else ForestGreenPrimary,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

@Composable
fun LotsOverviewTab(
    lots: List<MaterialLot>,
    totalSettled: Double,
    pendingDues: Double,
    todaySettled: Double,
    monthSettled: Double,
    isOffline: Boolean,
    isSyncing: Boolean,
    unsyncedCount: Int,
    lastSyncTimestamp: Long,
    safetyItems: List<HazardSafetyInfo>,
    language: Language,
    onToggleOffline: () -> Unit,
    onManualSync: () -> Unit,
    onSpeakAllSafety: () -> Unit,
    onSpeakSafetyItem: (HazardSafetyInfo) -> Unit,
    onNavigateToSafetyTab: () -> Unit,
    onSelectLot: (MaterialLot) -> Unit,
    onScanEwaste: () -> Unit,
    onCreateLot: () -> Unit,
    anomalousLotIds: Set<String> = emptySet()
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Primary Quick Actions: Scan E-Waste + Create Lot (kept clearly visible above the
        // pinned global Voice Assistant bar so they are never overlapped at any screen size)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MintLight),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MintBorder),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onScanEwaste() }
                        .testTag("scan_ewaste_action_btn")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp, horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Scan E-Waste",
                            tint = ForestGreenPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = when (language) {
                                Language.ENGLISH -> "Scan E-Waste"
                                Language.HINDI -> "स्कैन करें"
                                Language.MARATHI -> "स्कॅन करा"
                                Language.TAMIL -> "மின்-கழிவு ஸ்கேன்"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = ForestGreenPrimary
                        )
                        Text(
                            text = when (language) {
                                Language.ENGLISH -> "AI camera valuation"
                                Language.HINDI -> "AI कैमरा मूल्यांकन"
                                Language.MARATHI -> "AI कॅमेरा मूल्यांकन"
                                Language.TAMIL -> "AI கேமரா மதிப்பீடு"
                            },
                            fontSize = 10.sp,
                            color = TextSecondaryMuted
                        )
                    }
                }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ForestGreenPrimary),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onCreateLot() }
                        .testTag("create_lot_action_btn")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp, horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create Lot",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = when (language) {
                                Language.ENGLISH -> "Create Lot"
                                Language.HINDI -> "लॉट बनाएं"
                                Language.MARATHI -> "लॉट तयार करा"
                                Language.TAMIL -> "லாட் உருவாக்கு"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                        Text(
                            text = when (language) {
                                Language.ENGLISH -> "Log a new e-waste lot"
                                Language.HINDI -> "नया लॉट दर्ज करें"
                                Language.MARATHI -> "नवीन लॉट नोंदवा"
                                Language.TAMIL -> "புதிய மின்-கழிவு லாட் பதிவு செய்"
                            },
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        // Today / This-Month Earnings Strip
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MintLight),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = when (language) {
                                Language.ENGLISH -> "Today's Earnings"
                                Language.HINDI -> "आज की कमाई"
                                Language.MARATHI -> "आजची कमाई"
                                Language.TAMIL -> "இன்றைய வருவாய்"
                            },
                            color = ForestGreenPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "₹${todaySettled.toInt()}",
                            color = ForestGreenPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                    }
                }
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MintLight),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = when (language) {
                                Language.ENGLISH -> "This Month"
                                Language.HINDI -> "इस महीने"
                                Language.MARATHI -> "या महिन्यात"
                                Language.TAMIL -> "இந்த மாதம்"
                            },
                            color = ForestGreenPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "₹${monthSettled.toInt()}",
                            color = ForestGreenPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                    }
                }
            }
        }

        // Financial Summary Top Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ForestGreenPrimary),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = when (language) {
                                Language.ENGLISH -> "Total Cash Settled"
                                Language.HINDI -> "कुल नकद प्राप्त"
                                Language.MARATHI -> "एकूण जमा रक्कम"
                                Language.TAMIL -> "மொத்த பணத் தீர்வு"
                            },
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "₹${totalSettled.toInt()}",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp
                        )
                        Text(
                            text = "Verified CPCB Payout",
                            color = EmeraldAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MintBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = when (language) {
                                Language.ENGLISH -> "Pending Handover"
                                Language.HINDI -> "हस्तांतरण शेष"
                                Language.MARATHI -> "हस्तांतरण प्रलंबित"
                                Language.TAMIL -> "நிலுவையிலுள்ள கையளிப்பு"
                            },
                            color = TextSecondaryMuted,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "₹${pendingDues.toInt()}",
                            color = WarningAmber,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp
                        )
                        Text(
                            text = "Payable on weigh-in",
                            color = TextSecondaryMuted,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        // Offline-First Sync Indicator and Manual Synchronization Toggle
        item {
            OfflineSyncIndicatorBar(
                isOffline = isOffline,
                isSyncing = isSyncing,
                unsyncedCount = unsyncedCount,
                lastSyncTimestamp = lastSyncTimestamp,
                language = language,
                onToggleOffline = onToggleOffline,
                onManualSync = onManualSync
            )
        }

        // Dedicated Section for Pictorial and Audio-Based Safety Guidance on Hazardous Waste
        item {
            PictorialSafetyGuidanceCard(
                safetyItems = safetyItems,
                language = language,
                onSpeakAllSafety = onSpeakAllSafety,
                onSpeakItem = onSpeakSafetyItem,
                onNavigateToSafetyTab = onNavigateToSafetyTab
            )
        }

        // Section Title
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (language) {
                        Language.ENGLISH -> "Active & Completed Digital Lots"
                        Language.HINDI -> "सक्रिय एवं पूर्ण डिजिटल लॉट"
                        Language.MARATHI -> "सक्रिय व पूर्ण झालेले लॉट्स"
                        Language.TAMIL -> "செயலில் & முடிந்த டிஜிட்டல் லாட்கள்"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextPrimaryDark
                )
                Text(
                    text = "${lots.size} Total",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ForestGreenPrimary
                )
            }
        }

        // List of Digital Lots
        items(lots) { lot ->
            LotItemCard(
                lot = lot,
                language = language,
                isAnomalous = lot.lotId in anomalousLotIds,
                onClick = { onSelectLot(lot) }
            )
        }

        // Empty state
        if (lots.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "📦", fontSize = 40.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No Digital Lots Created Yet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = TextPrimaryDark
                        )
                        Text(
                            text = "Tap 'Create Lot' to photograph and valuate materials",
                            fontSize = 12.sp,
                            color = TextSecondaryMuted
                        )
                    }
                }
            }
        }

item {
            Spacer(modifier = Modifier.height(140.dp))
        }
    }
}

@Composable
fun LotItemCard(
    lot: MaterialLot,
    language: Language,
    isAnomalous: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isAnomalous) WarningAmber.copy(alpha = 0.06f) else Color.White
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isAnomalous) WarningAmber.copy(alpha = 0.5f) else MintBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("lot_item_${lot.lotId.lowercase()}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MintLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = lot.category.iconEmoji, fontSize = 22.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = lot.lotId,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = ForestGreenPrimary
                        )
                        Text(
                            text = lot.category.getTitle(language),
                            fontSize = 12.sp,
                            color = TextPrimaryDark
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (lot.recyclerConfirmed) SuccessGreen.copy(alpha = 0.15f) else MintPill
                ) {
                    Text(
                        text = lot.status.getLabel(language),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (lot.recyclerConfirmed) SuccessGreen else ForestGreenDark,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (isAnomalous) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = WarningAmber.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, WarningAmber.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = WarningAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when (language) {
                                Language.ENGLISH -> "Rate outside market range — verify before handover"
                                Language.HINDI -> "दर बाजार सीमा से बाहर — हस्तांतरण से पहले जांचें"
                                Language.MARATHI -> "दर बाजार श्रेणीबाहेर — हस्तांतरणापूर्वी तपासा"
                                Language.TAMIL -> "விகிதம் சந்தை வரம்பிற்கு வெளியே — கையளிப்பதற்கு முன் சரிபார்க்கவும்"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WarningAmber
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MintLight.copy(alpha = 0.5f))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Weight", fontSize = 10.sp, color = TextSecondaryMuted)
                    Text(
                        text = "${lot.weightKg} kg",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextPrimaryDark
                    )
                }
                Column {
                    Text(text = "Offered Rate", fontSize = 10.sp, color = TextSecondaryMuted)
                    Text(
                        text = "₹${lot.quotedRatePerKg.toInt()}/kg",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = TextPrimaryDark
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "Value", fontSize = 10.sp, color = TextSecondaryMuted)
                    Text(
                        text = "₹${lot.estimatedValueInr.toInt()}",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = ForestGreenPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = lot.matchedRecyclerName ?: "EcoReclaim Green Refineries",
                        fontSize = 11.sp,
                        color = TextSecondaryMuted
                    )
                }

                // Sync status indicator badge for the individual lot
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (lot.isSynced) Icons.Default.CloudDone else Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = if (lot.isSynced) SuccessGreen else WarningAmber,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (lot.isSynced) "Synced" else "Queued",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (lot.isSynced) SuccessGreen else WarningAmber
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.QrCode2,
                        contentDescription = null,
                        tint = ForestGreenPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "View Handover",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ForestGreenPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun RecyclersTab(
    recyclers: List<AuthorizedRecycler>,
    language: Language,
    onCallRecycler: (String) -> Unit,
    onOpenMap: (String?) -> Unit,
    onNavigateToRecycler: (Double, Double) -> Unit
) {
    // Dynamic discovery filters. The incoming list already contains only
    // authorized + active recyclers (repository gate); these filters narrow
    // by accepted material, doorstep pickup, and sort order.
    var materialFilter by remember { mutableStateOf<MaterialCategory?>(null) }
    var pickupOnly by remember { mutableStateOf(false) }
    var sortByTopRate by remember { mutableStateOf(false) }
    val allRecyclers = remember(recyclers) {
        if (recyclers.size >= 4) {
            recyclers
        } else {
            (recyclers + com.example.data.EwasteRepository.getDefaultAuthorizedRecyclers()).distinctBy { it.recyclerId }
        }
    }
    val availableMaterials = remember(allRecyclers) {
        allRecyclers.flatMap { it.acceptedCategories }.distinct()
    }
    val visibleRecyclers = remember(allRecyclers, materialFilter, pickupOnly, sortByTopRate) {
        allRecyclers
            .filter { rec ->
                (materialFilter == null || rec.acceptedCategories.contains(materialFilter)) &&
                    (!pickupOnly || rec.doorstepPickup)
            }
            .let { list ->
                if (sortByTopRate) list.sortedByDescending { it.buyingRates.values.maxOrNull() ?: 0.0 }
                else list.sortedBy { it.distanceKm }
            }
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = ForestGreenPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "🏭", fontSize = 28.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (language) {
                                    Language.ENGLISH -> "Authorized Recyclers & Aggregators"
                                    Language.HINDI -> "अधिकृत रीसायकलर एवं एग्रीगेटर"
                                    Language.MARATHI -> "अधिकृत रीसायकलर आणि संकलन केंद्रे"
                                    Language.TAMIL -> "அங்கீகரிக்கப்பட்ட மறுசுழற்சியாளர்கள் & ஒருங்கிணைப்பாளர்கள்"
                                },
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "CPCB / SPCB registered with guaranteed electronic weighing scales",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.18f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenMap(null) }
                            .testTag("open_map_from_tab_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, tint = EmeraldAccent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (language) {
                                    Language.ENGLISH -> "View Collection Points on Live Map"
                                    Language.HINDI -> "लाइव मैप पर संग्रहण केंद्र देखें"
                                    Language.MARATHI -> "थेट नकाशावर संकलन केंद्र पहा"
                                    Language.TAMIL -> "நேரடி வரைபடத்தில் சேகரிப்பு மையங்களைக் காண்"
                                },
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        item {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Verified options (${visibleRecyclers.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextPrimaryDark,
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        label = "Pickup",
                        selected = pickupOnly,
                        onClick = { pickupOnly = !pickupOnly }
                    )
                    FilterChip(
                        label = if (sortByTopRate) "Top rate" else "Nearest",
                        selected = sortByTopRate,
                        onClick = { sortByTopRate = !sortByTopRate }
                    )
                }

                if (availableMaterials.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(
                                label = "All materials",
                                selected = materialFilter == null,
                                onClick = { materialFilter = null }
                            )
                        }
                        items(availableMaterials) { cat ->
                            FilterChip(
                                label = when (language) {
                                    Language.ENGLISH -> cat.titleEn
                                    Language.HINDI -> cat.titleHi
                                    Language.MARATHI -> cat.titleMr
                                    Language.TAMIL -> cat.titleTa
                                },
                                selected = materialFilter == cat,
                                onClick = {
                                    materialFilter = if (materialFilter == cat) null else cat
                                }
                            )
                        }
                    }
                }
            }
        }

        if (visibleRecyclers.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MintBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No verified recycler matches these filters. Clear a filter to see every authorized option.",
                        fontSize = 12.sp,
                        color = TextSecondaryMuted,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        items(visibleRecyclers) { rec ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, MintBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("recycler_card_${rec.recyclerId.lowercase()}")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = rec.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimaryDark,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFFF8E1),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD54F))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Rating",
                                    tint = WarningAmber,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "${rec.rating} ★",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFF795548)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = rec.cpcbRegNo + " • " + rec.authorizationValidity,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SuccessGreen
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Verified-only badge + service area (dataset-driven).
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SuccessGreen.copy(alpha = 0.12f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Authorized • Active",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SuccessGreen
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Serves: ${rec.serviceArea.ifBlank { rec.city }}",
                            fontSize = 10.sp,
                            color = TextSecondaryMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, tint = TextSecondaryMuted, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${rec.facilityLocation} (${rec.distanceKm} km away)",
                            fontSize = 11.sp,
                            color = TextSecondaryMuted
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (rec.doorstepPickup) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MintLight
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.LocalShipping, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Doorstep Pickup Available (Min ${rec.minWeightForPickupKg.toInt()} kg)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ForestGreenPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Live offered rates per accepted material (Supabase dataset).
                    Text(
                        text = "Offered rates (live)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(rec.acceptedCategories) { cat ->
                            val rate = rec.buyingRates[cat]
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MintLight
                            ) {
                                Text(
                                    text = "${cat.titleEn}: ${if (rate != null) "₹${rate.toInt()}/kg" else "—"}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ForestGreenPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Live buying rate + supported categories (from real recycler data)
                    val bestRate = rec.buyingRates.values.maxOrNull()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (bestRate != null) "₹${bestRate.toInt()}/kg" else "Rate unavailable",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                color = EcoGreenPrimary
                            )
                            Text(
                                text = "Top rate offered • " + rec.acceptedCategories
                                    .take(3)
                                    .joinToString(", ") { cat ->
when (language) {
                                            Language.ENGLISH -> cat.titleEn
                                            Language.HINDI -> cat.titleHi
                                            Language.MARATHI -> cat.titleMr
                                            Language.TAMIL -> cat.titleTa
                                         }
                                    },
                                fontSize = 10.sp,
                                color = TextSecondaryMuted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MintLight
                        ) {
                            Text(
                                text = "${rec.acceptedCategories.size} categories",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = ForestGreenPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Feature badges = Pickup + payment modes actually offered
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (rec.doorstepPickup) {
                            RecyclerBadge(label = "Pickup Available", icon = Icons.Default.LocalShipping)
                        }
                        RecyclerBadge(
                            label = if (rec.paymentModesOffered.any { it == PaymentMode.UPI }) "UPI" else "Instant Cash",
                            icon = Icons.Default.CurrencyRupee
                        )
                        if (rec.paymentModesOffered.any { it == PaymentMode.BANK_TRANSFER }) {
                            RecyclerBadge(label = "Bank", icon = Icons.Default.CurrencyRupee)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Action buttons: View on Map, Directions, Call
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MintLight,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onOpenMap(rec.recyclerId) }
                                .testTag("recycler_view_map_${rec.recyclerId.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Map", color = ForestGreenPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MintLight,
                            modifier = Modifier
                                .weight(1.2f)
                                .clickable { onNavigateToRecycler(rec.latitude, rec.longitude) }
                                .testTag("recycler_directions_${rec.recyclerId.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.Directions, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Directions", color = ForestGreenPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ForestGreenPrimary,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onCallRecycler(rec.phone) }
                                .testTag("recycler_call_${rec.recyclerId.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.Call, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Call", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(140.dp))
        }
    }
}

@Composable
fun LedgerAndEconomicsTab(
    transactions: List<TransactionLedgerEntity>,
    totalSettled: Double,
    pendingDues: Double,
    economicsList: List<com.example.model.UnitEconomicsData>,
    language: Language
) {
    val dateFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())

    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Unit Economics assessment section
        item {
            UnitEconomicsCard(
                economicsList = economicsList,
                language = language
            )
        }

        // Ledger Header
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = when (language) {
                        Language.ENGLISH -> "Usable Financial Ledger & Receipts"
                        Language.HINDI -> "वित्तीय खाता एवं रसीदें"
                        Language.MARATHI -> "आर्थिक नोंदवही व पावत्या"
                        Language.TAMIL -> "பயன்படத்தக்க நிதி பேரேடு & ரசீதுகள்"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = TextPrimaryDark
                )
                Text(
                    text = "Documented proof of transaction for bank credit and formal identity",
                    fontSize = 11.sp,
                    color = TextSecondaryMuted
                )
            }
        }

        items(transactions) { txn ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, MintBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("txn_item_${txn.transactionId.lowercase()}")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = txn.categoryName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = TextPrimaryDark
                        )
                        Text(
                            text = "₹${txn.totalAmountInr.toInt()}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = if (txn.isSettled) SuccessGreen else WarningAmber
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${txn.weightKg} kg @ ₹${txn.ratePerKg.toInt()}/kg",
                            fontSize = 11.sp,
                            color = TextSecondaryMuted
                        )
                        Text(
                            text = if (txn.isSettled) "PAID IN CASH" else "HANDOVER PENDING",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (txn.isSettled) SuccessGreen else WarningAmber
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Recycler: ${txn.recyclerName} • ${dateFormat.format(Date(txn.timestamp))}",
                        fontSize = 10.sp,
                        color = TextSecondaryMuted
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(140.dp))
        }
    }
}

@Composable
fun LocationConnectionsTab(
    recyclers: List<AuthorizedRecycler>,
    collectorLocation: CollectorLocationEntity?,
    nearbyCollectors: List<RemoteCollector>,
    connections: List<ConnectionRequestEntity>,
    quotations: List<QuotationEntity>,
    language: Language,
    onToggleSharing: (Boolean, Double, Double, String?) -> Unit,
    onRefreshNearby: () -> Unit,
    onRequestConnection: (AuthorizedRecycler) -> Unit,
    onRespondToQuotation: (String, Boolean) -> Unit
) {
    val context = LocalContext.current
    val dateTimeFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())

    // Resolve a coarse last-known location from the platform (fallback: live map defaults)
    val coarseLocation = remember {
        val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as? android.location.LocationManager
        val provider = lm?.getProviders(true)?.firstOrNull()
        val loc = provider?.let { lm.getLastKnownLocation(it) }
        Pair(if (loc == null) 19.0760 else loc.latitude, if (loc == null) 72.8777 else loc.longitude)
    }
    val sharingOn = collectorLocation?.isSharingOn == true
    val lastAreaLabel = collectorLocation?.areaLabel

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = ForestGreenPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (language) {
                                    Language.ENGLISH -> "Share My Location"
                                    Language.HINDI -> "मेरा स्थान साझा करें"
                                    Language.MARATHI -> "माझे ठिकाण शेअर करा"
                                    Language.TAMIL -> "என் இருப்பிடத்தைப் பகிர்"
                                },
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Visible to nearby e-waste collectors only (no address / phone)",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = sharingOn,
                            onCheckedChange = { share ->
                                onToggleSharing(share, coarseLocation.first, coarseLocation.second, lastAreaLabel)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = EmeraldAccent,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color.White.copy(alpha = 0.5f)
                            )
                        )
                    }
                    if (sharingOn) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "📍 ${"%.4f".format(coarseLocation.first)}, ${"%.4f".format(coarseLocation.second)}",
                            color = EmeraldAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (collectorLocation != null)
                                "Updated ${dateTimeFormat.format(Date(collectorLocation.updatedAt))}"
                            else when (language) {
                                Language.ENGLISH -> "Never shared yet"
                                Language.HINDI -> "अभी तक साझा नहीं किया"
                                Language.MARATHI -> "अजून शेअर केले नाही"
                                Language.TAMIL -> "இன்னும் பகிரப்படவில்லை"
                            },
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        // Nearby collectors discovered from the sharing registry
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = when (language) {
                            Language.ENGLISH -> "Nearby Collectors (50 km)"
                            Language.HINDI -> "आसपास के संग्राहक (50 किमी)"
                            Language.MARATHI -> "जवळचे संकलक (50 किमी)"
                            Language.TAMIL -> "அருகிலுள்ள சேகரிப்பாளர்கள் (50 கி.மீ)"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextPrimaryDark
                    )
                    Text(
                        text = "Aggregators optimise pickup routes automatically",
                        fontSize = 10.sp,
                        color = TextSecondaryMuted
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MintLight,
                    modifier = Modifier
                        .clickable { onRefreshNearby() }
                        .testTag("refresh_nearby_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            tint = ForestGreenPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Refresh",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForestGreenPrimary
                        )
                    }
                }
            }
        }

        if (nearbyCollectors.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MintLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Filled.Group, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (sharingOn)
                                    when (language) {
                                        Language.ENGLISH -> "No other collectors sharing right now"
                                        Language.HINDI -> "अभी कोई अन्य संग्राहक शेयर नहीं कर रहा है"
                                        Language.MARATHI -> "आत्ता इतर संकलक शेअर करत नाहीत"
                                        Language.TAMIL -> "இப்போது மற்ற சேகரிப்பாளர்கள் எதுவும் பகிரவில்லை"
                                    }
                                else when (language) {
                                    Language.ENGLISH -> "Enable location sharing to see the network"
                                    Language.HINDI -> "नेटवर्क देखने के लिए स्थान साझा करना चालू करें"
                                    Language.MARATHI -> "नेटवर्क पाहण्यासाठी स्थान शेअरिंग सुरू करा"
                                    Language.TAMIL -> "நெட்வொர்க்கைப் பார்க்க இருப்பிட பகிர்வை இயக்கவும்"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ForestGreenPrimary
                            )
                        }
                    }
                }
            }
        } else {
            items(nearbyCollectors) { collector ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MintBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MintLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "♻️", fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = collector.area_label
                                    ?: when (language) {
                                        Language.ENGLISH -> "Nearby collector"
                                        Language.HINDI -> "आस-पास संग्राहक"
                                        Language.MARATHI -> "जवळचा संकलक"
                                        Language.TAMIL -> "அருகிலுள்ள சேகரிப்பாளர்"
                                    },
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = TextPrimaryDark
                            )
                            Text(
                                text = buildString {
                                    val distance = com.example.data.CloudSyncManager.distanceKm(
                                        coarseLocation.first, coarseLocation.second,
                                        collector.latitude, collector.longitude
                                    )
                                    append(String.format(Locale.US, "%.1f km away", distance))
                                },
                                fontSize = 11.sp,
                                color = TextSecondaryMuted
                            )
                        }
                        Icon(imageVector = Icons.Filled.Verified, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Connection requests and quotations header
        item {
            Text(
                text = when (language) {
                    Language.ENGLISH -> "Connections & Quotations"
                    Language.HINDI -> "संपर्क और कोटेशन"
                    Language.MARATHI -> "संपर्क आणि भाव पत्र"
                    Language.TAMIL -> "இணைப்புகள் & விலை மேற்கோள்கள்"
                },
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = TextPrimaryDark,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        if (connections.isEmpty() && quotations.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MintBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Filled.Handshake, contentDescription = null, tint = TextSecondaryMuted, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (language) {
                                Language.ENGLISH -> "No live requests. Reach out to a recycler below."
                                Language.HINDI -> "कोई सक्रिय अनुरोध नहीं। नीचे किसी रीसायकलर से संपर्क करें।"
                                Language.MARATHI -> "सक्रिय विनंती नाही. खालील रीसायकलरशी संपर्क साधा."
                                Language.TAMIL -> "நேரடி கோரிக்கைகள் இல்லை. கீழே உள்ள மறுசுழற்சியாளரை அணுகவும்."
                            },
                            fontSize = 12.sp,
                            color = TextSecondaryMuted
                        )
                    }
                }
            }
        }

        items(connections) { conn ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, MintBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = conn.recyclerName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = TextPrimaryDark
                        )
                        val statusColor = when (conn.status) {
                            "ACCEPTED" -> SuccessGreen
                            "REJECTED", "BLOCKED" -> ErrorRed
                            else -> WarningAmber
                        }
                        Text(
                            text = conn.status,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                    Text(
                        text = dateTimeFormat.format(Date(conn.createdAt)),
                        fontSize = 10.sp,
                        color = TextSecondaryMuted
                    )
                }
            }
        }

        items(quotations) { quote ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (quote.status == "PENDING") WarningAmber.copy(alpha = 0.6f) else MintBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = quote.recyclerName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = TextPrimaryDark
                            )
                            Text(
                                text = "₹${quote.quotedRatePerKg.toInt()}/kg → ₹${quote.quotedTotalInr.toInt()}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = ForestGreenPrimary
                            )
                        }
                        if (quote.status == "PENDING") {
                            Row {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = SuccessGreen,
                                    modifier = Modifier
                                        .clickable { onRespondToQuotation(quote.quotationId, true) }
                                        .testTag("accept_quote_${quote.quotationId}")
                                ) {
                                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(text = "Accept", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed),
                                    modifier = Modifier
                                        .clickable { onRespondToQuotation(quote.quotationId, false) }
                                        .testTag("reject_quote_${quote.quotationId}")
                                ) {
                                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Filled.Close, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(text = "Reject", color = ErrorRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = quote.status,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (quote.status == "ACCEPTED") SuccessGreen else TextSecondaryMuted
                            )
                        }
                    }
                }
            }
        }

        // Reach a recycler directly
        item {
            Text(
                text = when (language) {
                    Language.ENGLISH -> "Reach a Recycler"
                    Language.HINDI -> "रीसायकलर से संपर्क करें"
                    Language.MARATHI -> "रीसायकलरशी संपर्क साधा"
                    Language.TAMIL -> "மறுசுழற்சியாளரை அணுகவும்"
                },
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = TextPrimaryDark,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        items(recyclers) { rec ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, MintBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = rec.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = TextPrimaryDark
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(imageVector = Icons.Filled.Verified, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
                        }
                        Text(
                            text = rec.facilityLocation,
                            fontSize = 11.sp,
                            color = TextSecondaryMuted
                        )
                    }
                    val alreadyRequested = connections.any { it.recyclerId == rec.recyclerId && it.status != "REJECTED" }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (alreadyRequested) MintLight else ForestGreenPrimary,
                        modifier = Modifier
                            .clickable(enabled = !alreadyRequested) { onRequestConnection(rec) }
                            .testTag("connect_to_recycler_${rec.recyclerId.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (alreadyRequested) Icons.Filled.Check else Icons.Filled.Handshake,
                                contentDescription = null,
                                tint = if (alreadyRequested) ForestGreenPrimary else Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (alreadyRequested) "Requested" else "Connect",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (alreadyRequested) ForestGreenPrimary else Color.White
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(140.dp))
        }
    }
}
