package com.example.ui.admin

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PriceManagementPolicy
import com.example.data.ReferencePriceInput
import com.example.model.Language
import com.example.model.MaterialCategory
import com.example.model.PriceHistoryEntry
import com.example.model.PriceRecord
import com.example.ui.theme.BackgroundCream
import com.example.ui.theme.EcoBorder
import com.example.ui.theme.EcoGreenPrimary
import com.example.ui.theme.EcoMint
import com.example.ui.theme.EcoTextPrimary
import com.example.ui.theme.EcoTextSecondary
import com.example.ui.theme.EcoWhite
import com.example.ui.theme.ForestGreenDark
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class PriceSort { NEWEST, PRICE_HIGH, PRICE_LOW, MATERIAL }

/**
 * Government-admin price management.
 *
 * Edits create an immutable history entry and are queued for the cloud when
 * offline. Reference prices here are **indicative** — never a recycler's
 * actual purchase offer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPriceManagementScreen(
    viewModel: AdminViewModel,
    language: Language,
    onBack: () -> Unit
) {
    val prices by viewModel.referencePrices.collectAsState()
    val history by viewModel.priceHistory.collectAsState()
    val pendingChanges by viewModel.pendingPriceChanges.collectAsState()
    val isSaving by viewModel.isSavingPrice.collectAsState()
    val isSyncing by viewModel.isSyncingPrices.collectAsState()
    val message by viewModel.priceMessage.collectAsState()
    val conflict by viewModel.priceConflict.collectAsState()

    var search by remember { mutableStateOf("") }
    var categoryFilter by remember { mutableStateOf<MaterialCategory?>(null) }
    var sort by remember { mutableStateOf(PriceSort.NEWEST) }
    var sortMenu by remember { mutableStateOf(false) }
    var editorTarget by remember { mutableStateOf<PriceRecord?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var pendingConfirm by remember { mutableStateOf<ReferencePriceInput?>(null) }
    var deactivateTarget by remember { mutableStateOf<PriceRecord?>(null) }
    var historyTarget by remember { mutableStateOf<PriceRecord?>(null) }

    val visible = remember(prices, search, categoryFilter, sort) {
        prices
            .filter { categoryFilter == null || it.category == categoryFilter }
            .filter { price ->
                val needle = search.trim()
                needle.isEmpty() ||
                    price.subCategory.contains(needle, ignoreCase = true) ||
                    price.category.titleEn.contains(needle, ignoreCase = true) ||
                    price.grade.contains(needle, ignoreCase = true)
            }
            .sortedWith(
                when (sort) {
                    PriceSort.NEWEST -> compareByDescending<PriceRecord> { it.updatedAt }
                    PriceSort.PRICE_HIGH -> compareByDescending { it.prevailingBuyRate }
                    PriceSort.PRICE_LOW -> compareBy { it.prevailingBuyRate }
                    PriceSort.MATERIAL -> compareBy { it.subCategory.lowercase(Locale.US) }
                }
            )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Reference prices",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = ForestGreenDark
                        )
                        Text(
                            text = "Indicative government rates · not recycler offers",
                            fontSize = 10.sp,
                            color = EcoTextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("price_mgmt_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = ForestGreenDark
                        )
                    }
                },
                actions = {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = EcoGreenPrimary
                        )
                    } else {
                        IconButton(
                            onClick = { viewModel.refreshPriceManagement() },
                            modifier = Modifier.testTag("price_mgmt_refresh")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh prices",
                                tint = EcoGreenPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = EcoWhite)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundCream)
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(
                    color = EcoMint,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "These are reference prices published for guidance. " +
                                "They do not represent any recycler's actual purchase offer.",
                            fontSize = 12.sp,
                            color = EcoTextPrimary
                        )
                        if (viewModel.demoSession) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Demo session: edits are saved on this device only and are " +
                                    "never sent to the backend.",
                                fontSize = 11.sp,
                                color = WarningAmber,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            if (conflict != null) {
                item {
                    PriceBanner(
                        text = conflict!!,
                        container = WarningAmber.copy(alpha = 0.18f),
                        actionLabel = "Dismiss",
                        onAction = { viewModel.clearPriceFeedback() }
                    )
                }
            }
            if (message != null) {
                item {
                    PriceBanner(
                        text = message!!,
                        container = EcoMint,
                        actionLabel = "Dismiss",
                        onAction = { viewModel.clearPriceFeedback() }
                    )
                }
            }

            if (pendingChanges.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = EcoWhite),
                        border = androidx.compose.foundation.BorderStroke(1.dp, EcoBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("price_pending_queue")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Queued price changes (${pendingChanges.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = EcoTextPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            pendingChanges.take(5).forEach { change ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${change.kind.name.replace('_', ' ')} · ${change.targetId}",
                                            fontSize = 11.sp,
                                            color = EcoTextPrimary
                                        )
                                        Text(
                                            text = change.errorMessage
                                                ?: "Waiting to sync (${change.status.name.lowercase()})",
                                            fontSize = 10.sp,
                                            color = when (change.status.name) {
                                                "CONFLICT" -> WarningAmber
                                                "FAILED" -> Color(0xFFB3261E)
                                                else -> EcoTextSecondary
                                            }
                                        )
                                    }
                                    TextButton(
                                        onClick = { viewModel.discardPriceChange(change.queueId) }
                                    ) {
                                        Text("Discard", fontSize = 10.sp)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = { viewModel.refreshPriceManagement() },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Retry sync", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Search material or grade") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("price_mgmt_search")
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { sortMenu = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("price_mgmt_sort")
                        ) {
                            Text(
                                text = when (sort) {
                                    PriceSort.NEWEST -> "Newest first"
                                    PriceSort.PRICE_HIGH -> "Price: high → low"
                                    PriceSort.PRICE_LOW -> "Price: low → high"
                                    PriceSort.MATERIAL -> "Material A–Z"
                                },
                                fontSize = 11.sp
                            )
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            PriceSort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            when (option) {
                                                PriceSort.NEWEST -> "Newest first"
                                                PriceSort.PRICE_HIGH -> "Price: high → low"
                                                PriceSort.PRICE_LOW -> "Price: low → high"
                                                PriceSort.MATERIAL -> "Material A–Z"
                                            }
                                        )
                                    },
                                    onClick = {
                                        sort = option
                                        sortMenu = false
                                    }
                                )
                            }
                        }
                    }
                    Button(
                        onClick = {
                            editorTarget = null
                            showEditor = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("price_mgmt_add")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New", fontSize = 12.sp)
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = categoryFilter == null,
                        onClick = { categoryFilter = null },
                        label = { Text("All", fontSize = 10.sp) }
                    )
                    MaterialCategory.entries.take(4).forEach { cat ->
                        FilterChip(
                            selected = categoryFilter == cat,
                            onClick = {
                                categoryFilter = if (categoryFilter == cat) null else cat
                            },
                            label = { Text(cat.titleEn.substringBefore(" &").substringBefore(" ("), fontSize = 10.sp) }
                        )
                    }
                }
            }

            if (visible.isEmpty()) {
                item {
                    Surface(
                        color = EcoWhite,
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, EcoBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("price_mgmt_empty")
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "No reference prices match this view.",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = EcoTextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Create a price, clear the search, or refresh from the server.",
                                fontSize = 11.sp,
                                color = EcoTextSecondary
                            )
                        }
                    }
                }
            }

            items(visible, key = { it.priceId.ifBlank { it.subCategory + it.category.name } }) { price ->
                AdminPriceCard(
                    price = price,
                    onEdit = {
                        editorTarget = price
                        showEditor = true
                    },
                    onDeactivate = { deactivateTarget = price },
                    onHistory = { historyTarget = price }
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (showEditor) {
        AdminPriceEditorDialog(
            existing = editorTarget,
            prices = prices,
            isSaving = isSaving,
            onDismiss = { showEditor = false },
            onSave = { input ->
                val old = editorTarget?.prevailingBuyRate
                if (PriceManagementPolicy.isSignificantChange(old, input.pricePerKg)) {
                    pendingConfirm = input
                    showEditor = false
                } else {
                    viewModel.saveReferencePrice(input)
                    showEditor = false
                }
            }
        )
    }

    pendingConfirm?.let { input ->
        val old = editorTarget?.prevailingBuyRate
        AlertDialog(
            onDismissRequest = { pendingConfirm = null },
            title = { Text("Confirm a large price change") },
            text = {
                Text(
                    "This changes the reference price from " +
                        (old?.let { "₹${formatRate(it)}" } ?: "no value") +
                        " to ₹${formatRate(input.pricePerKg)} per kg " +
                        "(≥ ${PriceManagementPolicy.SIGNIFICANT_CHANGE_PERCENT.toInt()}%). " +
                        "Existing handovers keep their agreed price."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveReferencePrice(input)
                        pendingConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary)
                ) { Text("Confirm change") }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirm = null }) { Text("Cancel") }
            }
        )
    }

    deactivateTarget?.let { price ->
        AlertDialog(
            onDismissRequest = { deactivateTarget = null },
            title = { Text("Deactivate reference price?") },
            text = {
                Text(
                    "${price.subCategory} will stop being shown as an applicable price. " +
                        "Its history is preserved."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deactivateReferencePrice(price.priceId, price.version)
                        deactivateTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WarningAmber)
                ) { Text("Deactivate") }
            },
            dismissButton = {
                TextButton(onClick = { deactivateTarget = null }) { Text("Cancel") }
            }
        )
    }

    historyTarget?.let { price ->
        val entries = history.filter { it.priceId == price.priceId }
        AlertDialog(
            onDismissRequest = { historyTarget = null },
            title = { Text("Price history") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${price.subCategory} · ${price.category.titleEn}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                    if (entries.isEmpty()) {
                        Text(
                            text = "No history recorded on this device yet.",
                            fontSize = 11.sp,
                            color = EcoTextSecondary
                        )
                    }
                    entries.forEach { entry: PriceHistoryEntry ->
                        Surface(
                            color = EcoMint,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "${entry.changeType} · ₹${formatRate(entry.pricePerKg)}/kg · v${entry.version}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = EcoTextPrimary
                                )
                                Text(
                                    text = "${formatTimestamp(entry.changedAt)}" +
                                        if (entry.changedBy.isBlank()) "" else " · ${entry.changedBy}",
                                    fontSize = 10.sp,
                                    color = EcoTextSecondary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { historyTarget = null }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun AdminPriceCard(
    price: PriceRecord,
    onEdit: () -> Unit,
    onDeactivate: () -> Unit,
    onHistory: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = EcoWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, EcoBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_price_card_${price.category.name.lowercase(Locale.US)}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = price.subCategory.ifBlank { price.category.titleEn },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = EcoTextPrimary
                    )
                    Text(
                        text = price.category.titleEn +
                            if (price.grade.isBlank()) "" else " • ${price.grade}",
                        fontSize = 11.sp,
                        color = EcoTextSecondary
                    )
                }
                Surface(
                    color = if (price.isActive) SuccessGreen.copy(alpha = 0.15f) else EcoBorder.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = if (price.isActive) "Active" else "Inactive",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (price.isActive) SuccessGreen else EcoTextSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "₹${formatRate(price.prevailingBuyRate)}/kg",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = EcoGreenPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "range ₹${formatRate(price.marketMin)}–₹${formatRate(price.marketMax)}",
                    fontSize = 11.sp,
                    color = EcoTextSecondary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "v${price.version} · updated ${formatTimestamp(price.updatedAt)}" +
                    if (price.updatedBy.isBlank()) "" else " by ${price.updatedBy}",
                fontSize = 10.sp,
                color = EcoTextSecondary
            )
            if (!price.effectiveDate.isNullOrBlank()) {
                Text(
                    text = "Effective ${price.effectiveDate} · ${price.currency}",
                    fontSize = 10.sp,
                    color = EcoTextSecondary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onEdit,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("admin_price_edit")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit", fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = onHistory,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("admin_price_history")
                ) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("History", fontSize = 11.sp)
                }
                if (price.isActive) {
                    OutlinedButton(
                        onClick = onDeactivate,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("admin_price_deactivate")
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = WarningAmber
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Deactivate", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminPriceEditorDialog(
    existing: PriceRecord?,
    prices: List<PriceRecord>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (ReferencePriceInput) -> Unit
) {
    var category by remember { mutableStateOf(existing?.category ?: MaterialCategory.PCB_BOARDS) }
    var materialName by remember { mutableStateOf(existing?.subCategory ?: "") }
    var grade by remember { mutableStateOf(existing?.grade ?: "") }
    var priceText by remember { mutableStateOf(existing?.prevailingBuyRate?.let { formatRate(it) } ?: "") }
    var minText by remember { mutableStateOf(existing?.marketMin?.let { formatRate(it) } ?: "") }
    var maxText by remember { mutableStateOf(existing?.marketMax?.let { formatRate(it) } ?: "") }
    var effectiveDate by remember { mutableStateOf(existing?.effectiveDate ?: "") }
    var isActive by remember { mutableStateOf(existing?.isActive ?: true) }
    var categoryMenu by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    val duplicate = prices.firstOrNull {
        it.category == category &&
            it.priceId != existing?.priceId &&
            it.subCategory.equals(materialName.trim(), ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = {
            Text(
                text = if (existing == null) "New reference price" else "Edit reference price",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box {
                    OutlinedButton(
                        onClick = { categoryMenu = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("price_editor_category")
                    ) {
                        Text(category.titleEn, fontSize = 12.sp)
                    }
                    DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                        MaterialCategory.entries.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.titleEn) },
                                onClick = {
                                    category = cat
                                    categoryMenu = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = materialName,
                    onValueChange = { materialName = it },
                    label = { Text("Material name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("price_editor_material")
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Reference price (₹/kg)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("price_editor_price")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minText,
                        onValueChange = { minText = it },
                        label = { Text("Market min") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("price_editor_min")
                    )
                    OutlinedTextField(
                        value = maxText,
                        onValueChange = { maxText = it },
                        label = { Text("Market max") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("price_editor_max")
                    )
                }
                OutlinedTextField(
                    value = grade,
                    onValueChange = { grade = it },
                    label = { Text("Grade (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = effectiveDate,
                    onValueChange = { effectiveDate = it },
                    label = { Text("Effective date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isActive) "Applicable (active)" else "Inactive (hidden)",
                        fontSize = 12.sp,
                        color = EcoTextSecondary
                    )
                }

                if (duplicate != null) {
                    Text(
                        text = "A price for this material already exists in this category.",
                        fontSize = 11.sp,
                        color = WarningAmber
                    )
                }
                if (localError != null) {
                    Text(text = localError!!, fontSize = 11.sp, color = WarningAmber)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSaving && duplicate == null,
                colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary),
                modifier = Modifier.testTag("price_editor_save"),
                onClick = {
                    val input = ReferencePriceInput(
                        priceId = existing?.priceId ?: "",
                        category = category,
                        materialName = materialName.trim(),
                        grade = grade.trim(),
                        location = existing?.location ?: "",
                        pricePerKg = priceText.trim().toDoubleOrNull() ?: -1.0,
                        marketMin = minText.trim().toDoubleOrNull() ?: -1.0,
                        marketMax = maxText.trim().toDoubleOrNull() ?: -1.0,
                        currency = existing?.currency ?: "INR",
                        effectiveDate = effectiveDate.trim().ifBlank { null },
                        isActive = isActive,
                        baseVersion = existing?.version ?: 0L,
                        trend = existing?.trend ?: "STABLE",
                        trendPercentage = existing?.trendPercentage ?: 0.0,
                        keyMetals = existing?.keyMetals ?: emptyList()
                    )
                    val validation = PriceManagementPolicy.validateReferencePrice(input)
                    if (!validation.isValid) {
                        localError = validation.message()
                    } else {
                        localError = null
                        onSave(input)
                    }
                }
            ) {
                Text(if (isSaving) "Saving…" else "Save price")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") }
        }
    )
}

@Composable
private fun PriceBanner(
    text: String,
    container: Color,
    actionLabel: String,
    onAction: () -> Unit
) {
    Surface(color = container, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                fontSize = 12.sp,
                color = EcoTextPrimary,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onAction) { Text(actionLabel, fontSize = 11.sp) }
        }
    }
}

private fun formatRate(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.2f", value)

private fun formatTimestamp(millis: Long): String {
    if (millis <= 0L) return "never"
    return SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(Date(millis))
}
