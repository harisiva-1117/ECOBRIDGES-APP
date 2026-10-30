package com.example.ui.recycler

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.OfferInput
import com.example.data.PriceManagementPolicy
import com.example.model.Language
import com.example.model.MaterialCategory
import com.example.model.RecyclerOffer
import com.example.ui.theme.EcoBorder
import com.example.ui.theme.EcoGreenPrimary
import com.example.ui.theme.EcoMint
import com.example.ui.theme.EcoOrangeAccent
import com.example.ui.theme.EcoOrangeBg
import com.example.ui.theme.EcoTextPrimary
import com.example.ui.theme.EcoTextSecondary
import com.example.ui.theme.EcoWhite
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Recycler-owned purchase offers.
 *
 * These are **actual offers** to buy material. They are deliberately kept
 * separate from the government's **indicative reference prices** shown for
 * comparison on each card.
 */
@Composable
fun RecyclerOffersTab(
    viewModel: RecyclerViewModel,
    facilityName: String,
    cpcbNumber: String,
    language: Language
) {
    val offers by viewModel.offers.collectAsState()
    val referencePrices by viewModel.referencePrices.collectAsState()
    val actingRecyclerId by viewModel.actingRecyclerId.collectAsState()
    val isSaving by viewModel.isSavingPrice.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val message by viewModel.priceMessage.collectAsState()
    val conflict by viewModel.priceConflict.collectAsState()

    var editorTarget by remember { mutableStateOf<RecyclerOffer?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    val myOffers = remember(offers, actingRecyclerId, facilityName) {
        offers.filter { offer ->
            PriceManagementPolicy.ownsOffer(offer.recyclerId, actingRecyclerId) ||
                (actingRecyclerId.isBlank() &&
                    PriceManagementPolicy.ownsOffer(offer.recyclerName, facilityName))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = EcoGreenPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Sell,
                                contentDescription = null,
                                tint = EcoWhite
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "My purchase offers",
                                    color = EcoWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = facilityName.ifBlank { "Your facility" },
                                    color = EcoWhite.copy(alpha = 0.85f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Offers are your own buying rates. Only this facility " +
                                "(${cpcbNumber.ifBlank { "unverified CPCB id" }}) can create or edit them. " +
                                "They are not government reference prices.",
                            color = EcoWhite.copy(alpha = 0.9f),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            if (actingRecyclerId.isBlank()) {
                item {
                    Surface(
                        color = EcoOrangeBg,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Your recycler identity could not be resolved yet. " +
                                "Refresh after signing in with a registered CPCB facility.",
                            color = EcoTextPrimary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            if (conflict != null) {
                item {
                    FeedbackBanner(
                        text = conflict!!,
                        container = WarningAmber.copy(alpha = 0.18f),
                        actionLabel = "Dismiss",
                        onAction = { viewModel.clearPriceFeedback() }
                    )
                }
            }
            if (message != null) {
                item {
                    FeedbackBanner(
                        text = message!!,
                        container = EcoMint,
                        actionLabel = "Dismiss",
                        onAction = { viewModel.clearPriceFeedback() }
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Your offers (${myOffers.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = EcoTextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { viewModel.refreshOffers() },
                        modifier = Modifier.testTag("recycler_offers_refresh")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh offers",
                            tint = EcoGreenPrimary
                        )
                    }
                    Button(
                        onClick = {
                            editorTarget = null
                            showEditor = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary),
                        shape = RoundedCornerShape(12.dp),
                        enabled = actingRecyclerId.isNotBlank() && !isSyncing,
                        modifier = Modifier.testTag("recycler_offer_add")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add", fontSize = 12.sp)
                    }
                }
            }

            if (myOffers.isEmpty()) {
                item {
                    Surface(
                        color = EcoWhite,
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, EcoBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("recycler_offers_empty")
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "No offers yet.",
                                fontWeight = FontWeight.SemiBold,
                                color = EcoTextPrimary,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Publish a rate so collectors can compare actual offers " +
                                    "with the indicative reference prices.",
                                color = EcoTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            items(myOffers) { offer ->
                OfferCard(
                    offer = offer,
                    reference = referencePrices.firstOrNull { it.category == offer.category },
                    onEdit = {
                        editorTarget = offer
                        showEditor = true
                    }
                )
            }

            item { Spacer(modifier = Modifier.height(140.dp)) }
        }
    }

    if (showEditor) {
        OfferEditorDialog(
            existing = editorTarget,
            recyclerId = actingRecyclerId,
            recyclerName = facilityName,
            referencePrices = referencePrices,
            existingOffers = myOffers,
            isSaving = isSaving,
            onDismiss = { showEditor = false },
            onSave = { input ->
                viewModel.saveOffer(input)
                showEditor = false
            }
        )
    }
}

@Composable
private fun OfferCard(
    offer: RecyclerOffer,
    reference: com.example.model.PriceRecord?,
    onEdit: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = EcoWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, EcoBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recycler_offer_card_${offer.offerId}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = offer.materialName.ifBlank { offer.category.titleEn },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = EcoTextPrimary
                    )
                    Text(
                        text = offer.category.titleEn + if (offer.grade.isBlank()) "" else " • ${offer.grade}",
                        fontSize = 11.sp,
                        color = EcoTextSecondary
                    )
                }
                StatusPill(status = offer.status)
                Spacer(modifier = Modifier.width(6.dp))
                TextButton(onClick = onEdit, modifier = Modifier.testTag("recycler_offer_edit")) {
                    Text("Edit", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "₹${formatRate(offer.pricePerKg)}/kg",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = EcoGreenPrimary
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = offer.quantityLabel,
                    fontSize = 11.sp,
                    color = EcoTextSecondary
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Indicative govt reference: " +
                    (reference?.let { "₹${formatRate(it.prevailingBuyRate)}/kg" } ?: "not published"),
                fontSize = 11.sp,
                color = EcoTextSecondary
            )

            if (offer.condition.isNotBlank() || offer.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = listOf(offer.condition, offer.notes)
                        .filter { it.isNotBlank() }
                        .joinToString(" • "),
                    fontSize = 11.sp,
                    color = EcoTextSecondary
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Updated ${formatTimestamp(offer.updatedAt)}" +
                    if (offer.updatedBy.isBlank()) "" else " by ${offer.updatedBy}",
                fontSize = 10.sp,
                color = EcoTextSecondary.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun OfferEditorDialog(
    existing: RecyclerOffer?,
    recyclerId: String,
    recyclerName: String,
    referencePrices: List<com.example.model.PriceRecord>,
    existingOffers: List<RecyclerOffer>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (OfferInput) -> Unit
) {
    var category by remember { mutableStateOf(existing?.category ?: MaterialCategory.PCB_BOARDS) }
    var materialName by remember { mutableStateOf(existing?.materialName ?: "") }
    var grade by remember { mutableStateOf(existing?.grade ?: "") }
    var priceText by remember { mutableStateOf(existing?.pricePerKg?.let { formatRate(it) } ?: "") }
    var minText by remember { mutableStateOf(existing?.minQuantityKg?.let { formatRate(it) } ?: "") }
    var maxText by remember { mutableStateOf(existing?.maxQuantityKg?.let { formatRate(it) } ?: "") }
    var condition by remember { mutableStateOf(existing?.condition ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var status by remember { mutableStateOf(existing?.status ?: "ACTIVE") }
    var categoryMenu by remember { mutableStateOf(false) }
    var statusMenu by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    val reference = referencePrices.firstOrNull { it.category == category }
    val duplicate = existingOffers.firstOrNull {
        it.category == category && it.offerId != existing?.offerId
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = {
            Text(
                text = if (existing == null) "New purchase offer" else "Edit purchase offer",
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
                Text(
                    text = "This is a real buying offer from your facility, not a government reference price.",
                    fontSize = 11.sp,
                    color = EcoTextSecondary
                )

                Box {
                    OutlinedButton(
                        onClick = { categoryMenu = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("offer_category")
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

                Text(
                    text = reference?.let {
                        "Govt indicative reference: ₹${formatRate(it.prevailingBuyRate)}/kg " +
                            "(range ₹${formatRate(it.marketMin)}–₹${formatRate(it.marketMax)})"
                    } ?: "No government reference price is published for this material.",
                    fontSize = 11.sp,
                    color = EcoTextSecondary
                )

                OutlinedTextField(
                    value = materialName,
                    onValueChange = { materialName = it },
                    label = { Text("Material name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("offer_material")
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Offered price (₹/kg)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("offer_price")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minText,
                        onValueChange = { minText = it },
                        label = { Text("Min kg") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = maxText,
                        onValueChange = { maxText = it },
                        label = { Text("Max kg") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
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
                    value = condition,
                    onValueChange = { condition = it },
                    label = { Text("Condition / requirements") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Box {
                    OutlinedButton(
                        onClick = { statusMenu = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Status: $status", fontSize = 12.sp)
                    }
                    DropdownMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }) {
                        listOf("DRAFT", "ACTIVE", "PAUSED", "EXPIRED").forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    status = option
                                    statusMenu = false
                                }
                            )
                        }
                    }
                }

                if (duplicate != null) {
                    Text(
                        text = "You already have an offer for this material. Edit that offer instead.",
                        fontSize = 11.sp,
                        color = WarningAmber
                    )
                }
                if (localError != null) {
                    Text(text = localError!!, fontSize = 11.sp, color = EcoOrangeAccent)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSaving && duplicate == null,
                colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary),
                onClick = {
                    val minQty = minText.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
                    val maxQty = maxText.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
                    val input = OfferInput(
                        offerId = existing?.offerId ?: "",
                        recyclerId = recyclerId,
                        recyclerName = recyclerName,
                        category = category,
                        materialName = materialName.trim(),
                        grade = grade.trim(),
                        pricePerKg = priceText.trim().toDoubleOrNull() ?: -1.0,
                        minQuantityKg = minQty,
                        maxQuantityKg = maxQty,
                        condition = condition.trim(),
                        status = status,
                        notes = notes.trim(),
                        baseVersion = existing?.version ?: 0L
                    )
                    val validation = PriceManagementPolicy.validateOffer(input)
                    if (!validation.isValid) {
                        localError = validation.message()
                    } else {
                        localError = null
                        onSave(input)
                    }
                },
                modifier = Modifier.testTag("offer_save")
            ) {
                Text(if (isSaving) "Saving…" else "Save offer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") }
        }
    )
}

@Composable
private fun StatusPill(status: String) {
    val normalized = status.uppercase(Locale.US)
    val container = when (normalized) {
        "ACTIVE" -> SuccessGreen.copy(alpha = 0.15f)
        "DRAFT" -> EcoMint
        "PAUSED" -> EcoOrangeBg
        else -> EcoBorder.copy(alpha = 0.5f)
    }
    val content = when (normalized) {
        "ACTIVE" -> SuccessGreen
        "PAUSED" -> EcoOrangeAccent
        else -> EcoTextSecondary
    }
    Surface(color = container, shape = RoundedCornerShape(20.dp)) {
        Text(
            text = normalized.lowercase(Locale.US).replaceFirstChar { it.uppercase() },
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun FeedbackBanner(
    text: String,
    container: androidx.compose.ui.graphics.Color,
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
            TextButton(onClick = onAction) {
                Text(actionLabel, fontSize = 11.sp)
            }
        }
    }
}

private fun formatRate(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.2f", value)

private fun formatTimestamp(millis: Long): String {
    if (millis <= 0L) return "never"
    return SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(Date(millis))
}
