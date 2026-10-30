package com.example.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.i18n.PortalLabels
import com.example.model.Language
import com.example.model.MaterialLot
import com.example.ui.theme.BackgroundCream
import com.example.ui.theme.EcoMint
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.ForestGreenDark
import com.example.ui.theme.ForestGreenPrimary
import com.example.ui.theme.MintBorder
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryMuted
import com.example.ui.theme.WarningAmber
import java.util.Locale
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GovernmentAdminPortalScreen(
    viewModel: AdminViewModel,
    adminId: String,
    language: Language,
    onBack: () -> Unit
) {
    val metrics by viewModel.metrics.collectAsState()
    val lots by viewModel.lots.collectAsState()
    val collectors by viewModel.collectors.collectAsState()
    val recyclers by viewModel.recyclers.collectAsState()
    val unlinkedLots by viewModel.unlinkedLots.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val lastError by viewModel.lastError.collectAsState()

    var showPriceManagement by remember { mutableStateOf(false) }
    if (showPriceManagement) {
        AdminPriceManagementScreen(
            viewModel = viewModel,
            language = language,
            onBack = { showPriceManagement = false }
        )
        return
    }

    // Keep the national registry current while an officer is viewing it. The
    // refresh icon remains available for an immediate on-demand update.
    LaunchedEffect(viewModel) {
        while (true) {
            delay(15_000)
            viewModel.refresh()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = PortalLabels.getCpcbEprOversight(language),
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = ForestGreenPrimary
                        )
                        Text(
                            text = PortalLabels.getMoefccSubtitle(language),
                            fontSize = 11.sp,
                            color = ForestGreenDark
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("admin_nav_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = ForestGreenPrimary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refresh() },
                        modifier = Modifier.testTag("admin_refresh")
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = ForestGreenPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = ForestGreenPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundCream)
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (lastError != null) {
                item {
                    Surface(
                        color = WarningAmber.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = lastError!!,
                            color = TextPrimaryDark,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = ForestGreenDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldAccent.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(imageVector = Icons.Default.AccountBalance, contentDescription = null, tint = EmeraldAccent)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = PortalLabels.getNationalRegistry(language),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = PortalLabels.getAdminOfficerId(language, adminId),
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MetricTile(PortalLabels.getInformalCollectors(language), "${metrics.collectors}", PortalLabels.getRegisteredAccounts(language))
                            MetricTile(PortalLabels.getFormalRecyclers(language), "${metrics.recyclers}", PortalLabels.getAuthorizedFacilities(language))
                            MetricTile(PortalLabels.getRegisteredLots(language), "${metrics.lots}", PortalLabels.getSharedCloudRecords(language))
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MintBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_metrics_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = PortalLabels.getLiveEprMetrics(language),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimaryDark
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MetricTileLight(PortalLabels.getAwaitingWeighIn(language), "${metrics.pendingLots}", WarningAmber)
                            MetricTileLight(PortalLabels.getVerifiedPaid(language), "${metrics.confirmedLots}", SuccessGreen)
                            MetricTileLight(
                                PortalLabels.getFormalization(language),
                                String.format(Locale.US, "%.1f%%", metrics.formalizationPercent),
                                ForestGreenPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MetricTileLight(
                                PortalLabels.getTraceableWeight(language),
                                String.format(Locale.US, "%.1f kg", metrics.totalWeightKg),
                                ForestGreenPrimary
                            )
                            MetricTileLight(
                                PortalLabels.getSettledPayout(language),
                                String.format(Locale.US, "₹%.0f", metrics.settledValueInr),
                                ForestGreenPrimary
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MintBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPriceManagement = true }
                        .testTag("admin_price_management_entry")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(EcoMint),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sell,
                                contentDescription = null,
                                tint = ForestGreenPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Price management",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = TextPrimaryDark
                            )
                            Text(
                                text = "Publish indicative reference prices, review history and sync",
                                fontSize = 11.sp,
                                color = TextSecondaryMuted
                            )
                        }
                        Text(
                            text = "Open",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ForestGreenPrimary
                        )
                    }
                }
            }

            item {
                DirectorySectionHeader(
                    title = PortalLabels.getInformalCollectors(language),
                    count = collectors.size,
                    language = language,
                    testTag = "admin_collectors_header"
                )
            }

            if (collectors.isEmpty()) {
                item {
                    DirectoryEmptyHint(
                        text = PortalLabels.getCollectorsEmptyHint(language),
                        testTag = "admin_collectors_empty"
                    )
                }
            }

            items(collectors) { entry ->
                UserDirectoryCard(
                    entry = entry,
                    language = language,
                    icon = Icons.Default.Person,
                    idLabel = entry.profile.statutory_identifier ?: "—",
                    testTag = "admin_collector_card"
                )
            }

            item {
                DirectorySectionHeader(
                    title = PortalLabels.getFormalRecyclers(language),
                    count = recyclers.size,
                    language = language,
                    testTag = "admin_recyclers_header"
                )
            }

            if (recyclers.isEmpty()) {
                item {
                    DirectoryEmptyHint(
                        text = PortalLabels.getRecyclersEmptyHint(language),
                        testTag = "admin_recyclers_empty"
                    )
                }
            }

            items(recyclers) { entry ->
                UserDirectoryCard(
                    entry = entry,
                    language = language,
                    icon = Icons.Default.Factory,
                    idLabel = entry.profile.statutory_identifier
                        ?: entry.profile.entity_name ?: "—",
                    testTag = "admin_recycler_card"
                )
            }

            if (unlinkedLots.isNotEmpty()) {
                item {
                    DirectorySectionHeader(
                        title = PortalLabels.getDeviceLotsAwaitingLink(language),
                        count = unlinkedLots.size,
                        language = language,
                        testTag = "admin_unlinked_header"
                    )
                }
            }

            items(unlinkedLots) { lot ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MintBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        LotMiniRow(lot = lot, language = language)
                    }
                }
            }

            item {
                Text(
                    text = PortalLabels.getSharedTraceabilityRecords(language),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextPrimaryDark
                )
            }

            if (lots.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MintBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_empty_state")
                    ) {
                        Text(
                            text = PortalLabels.getNoTraceabilityRecords(language),
                            fontSize = 12.sp,
                            color = TextSecondaryMuted,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            items(lots) { lot ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MintBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = lot.lotId, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ForestGreenPrimary)
                                Text(
                                    text = "${lot.category.getTitle(language)} • ${lot.weightKg} kg",
                                    fontSize = 13.sp,
                                    color = TextPrimaryDark
                                )
                                Text(
                                    text = PortalLabels.getCollectorLine(language, lot.matchedRecyclerName ?: "—"),
                                    fontSize = 10.sp,
                                    color = TextSecondaryMuted
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (lot.recyclerConfirmed) SuccessGreen.copy(alpha = 0.15f) else WarningAmber.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = PortalLabels.getAdminStatusBadge(language, lot.recyclerConfirmed),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (lot.recyclerConfirmed) SuccessGreen else WarningAmber,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = PortalLabels.getLocationLine(language, lot.collectionLocation), fontSize = 11.sp, color = TextSecondaryMuted)
                            Text(text = PortalLabels.getValueLine(language, lot.estimatedValueInr.toInt()), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ForestGreenPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.MetricTile(label: String, value: String, caption: String) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = EmeraldAccent.copy(alpha = 0.15f)),
        modifier = Modifier.weight(1f)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(text = label, color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
            Text(text = value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(text = caption, color = EmeraldAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}
@Composable
private fun RowScope.MetricTileLight(label: String, value: String, accent: Color) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = 0.10f),
        modifier = Modifier.weight(1f)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(text = label, color = TextSecondaryMuted, fontSize = 10.sp)
            Text(text = value, color = accent, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

@Composable
private fun DirectorySectionHeader(title: String, count: Int, language: Language, testTag: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = TextPrimaryDark
        )
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = ForestGreenPrimary.copy(alpha = 0.10f)
        ) {
            Text(
                text = PortalLabels.getLiveCount(language, count),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ForestGreenPrimary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun DirectoryEmptyHint(text: String, testTag: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, MintBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = TextSecondaryMuted,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun UserDirectoryCard(
    entry: UserDirectoryEntry,
    language: Language,
    icon: ImageVector,
    idLabel: String,
    testTag: String
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, MintBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(ForestGreenPrimary.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = ForestGreenPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.displayName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = ForestGreenPrimary
                    )
                    Text(
                        text = "${entry.maskedPhone} • ${entry.profile.account_status ?: PortalLabels.getActiveStatus(language)}",
                        fontSize = 11.sp,
                        color = TextSecondaryMuted
                    )
                    Text(
                        text = idLabel,
                        fontSize = 10.sp,
                        color = TextSecondaryMuted
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ForestGreenPrimary.copy(alpha = 0.10f)
                ) {
                    Text(
                        text = PortalLabels.getLotsCount(language, entry.lotCount),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ForestGreenPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = PortalLabels.getLotsSummary(language, entry.lotCount, "%.1f".format(entry.totalKg), entry.totalValueInr.toInt()),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimaryDark
            )

            if (entry.lots.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                entry.lots.forEach { lot ->
                    LotMiniRow(lot = lot, language = language)
                }
            }
        }
    }
}

@Composable
private fun LotMiniRow(lot: MaterialLot, language: Language) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = lot.lotId,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = ForestGreenPrimary
            )
            Text(
                text = "${lot.category.getTitle(language)} • ${lot.weightKg} kg",
                fontSize = 11.sp,
                color = TextPrimaryDark
            )
        }
        Text(
            text = "₹${lot.estimatedValueInr.toInt()}",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = ForestGreenPrimary
        )
    }
}
