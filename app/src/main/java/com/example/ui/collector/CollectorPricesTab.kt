package com.example.ui.collector

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Language
import com.example.model.MaterialCategory
import com.example.model.MaterialLot
import com.example.model.PriceRecord
import com.example.model.RecyclerOffer
import com.example.ui.theme.EcoBorder
import com.example.ui.theme.EcoGreenPrimary
import com.example.ui.theme.EcoMint
import com.example.ui.theme.EcoOrangeBg
import com.example.ui.theme.EcoTextPrimary
import com.example.ui.theme.EcoTextSecondary
import com.example.ui.theme.EcoWhite
import com.example.ui.theme.ForestGreenPrimary
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Collector-facing price board.
 *
 * Clearly separates the government's **indicative reference price** from the
 * **actual purchase offers** published by authorised recyclers, and keeps the
 * price agreed at handover visible as historical fact.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CollectorPricesTab(
    referencePrices: List<PriceRecord>,
    offers: List<RecyclerOffer>,
    lots: List<MaterialLot>,
    isSyncing: Boolean,
    language: Language,
    onRefresh: () -> Unit
) {
    var categoryFilter by remember { mutableStateOf<MaterialCategory?>(null) }

    val visiblePrices = remember(referencePrices, categoryFilter) {
        referencePrices
            .filter { it.isActive }
            .filter { categoryFilter == null || it.category == categoryFilter }
            .sortedBy { it.subCategory.lowercase(Locale.US) }
    }
    val visibleOffers = remember(offers, categoryFilter) {
        offers
            .filter { it.isActive }
            .filter { categoryFilter == null || it.category == categoryFilter }
            .sortedByDescending { it.pricePerKg }
    }
    val agreedLots = remember(lots) {
        lots.filter { it.agreedRatePerKg != null }
            .sortedByDescending { it.agreedAt ?: 0L }
            .take(10)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = ForestGreenPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Sell,
                            contentDescription = null,
                            tint = EcoWhite
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Price board",
                                color = EcoWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Reference price vs. actual recycler offers",
                                color = EcoWhite.copy(alpha = 0.85f),
                                fontSize = 11.sp
                            )
                        }
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = EcoWhite
                            )
                        } else {
                            IconButton(
                                onClick = onRefresh,
                                modifier = Modifier.testTag("collector_prices_refresh")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh prices",
                                    tint = EcoWhite
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "An indicative reference price is guidance published by the " +
                            "government. A recycler offer is a real buying price from a named " +
                            "facility. Only the offer applies to a new handover.",
                        color = EcoWhite.copy(alpha = 0.9f),
                        fontSize = 11.sp
                    )
                }
            }
        }

        item {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterPill(
                    label = "All",
                    selected = categoryFilter == null,
                    onClick = { categoryFilter = null }
                )
                MaterialCategory.entries.forEach { cat ->
                    FilterPill(
                        label = cat.getTitle(language),
                        selected = categoryFilter == cat,
                        onClick = {
                            categoryFilter = if (categoryFilter == cat) null else cat
                        }
                    )
                }
            }
        }

        if (visiblePrices.isEmpty() && visibleOffers.isEmpty()) {
            item {
                InfoCard(
                    title = "No published prices yet",
                    body = "Reference prices and recycler offers will appear here after the " +
                        "next sync. You can still create a lot offline; its price is preserved.",
                    container = EcoOrangeBg,
                    testTag = "collector_prices_empty"
                )
            }
        }

        items(visiblePrices, key = { it.priceId.ifBlank { it.category.name + it.subCategory } }) { price ->
            val offersForPrice = visibleOffers.filter {
                it.category == price.category &&
                    (it.materialName.isBlank() ||
                        it.materialName.equals(price.subCategory, ignoreCase = true))
            }
            PriceComparisonCard(price = price, offers = offersForPrice)
        }

        val categoriesWithOffersOnly = visibleOffers
            .filter { offer -> visiblePrices.none { it.category == offer.category } }
            .groupBy { it.category }
        items(categoriesWithOffersOnly.entries.toList()) { (category, categoryOffers) ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = EcoWhite),
                border = BorderStroke(1.dp, EcoBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("collector_offers_only_${category.name.lowercase(Locale.US)}")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = category.getTitle(language),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = EcoTextPrimary
                    )
                    Text(
                        text = "No government reference price is published for this material. " +
                            "The offers below are actual recycler buying prices.",
                        fontSize = 11.sp,
                        color = EcoTextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    categoryOffers.sortedByDescending { it.pricePerKg }.forEach { offer ->
                        OfferRow(offer = offer)
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }

        if (agreedLots.isNotEmpty()) {
            item {
                Text(
                    text = "Agreed handover prices (locked)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = EcoTextPrimary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            item {
                InfoCard(
                    title = "Historic, not live",
                    body = "The price agreed at each handover is preserved even when a " +
                        "reference price or offer later changes.",
                    container = EcoMint,
                    testTag = "collector_agreed_notice"
                )
            }
            items(agreedLots, key = { it.lotId }) { lot ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = EcoWhite),
                    border = BorderStroke(1.dp, EcoBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("collector_agreed_${lot.lotId}")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = lot.subCategory.ifBlank { lot.category.titleEn },
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = EcoTextPrimary
                            )
                            Text(
                                text = "${formatWeight(lot.weightKg)} kg · " +
                                    "agreed ${formatTimestamp(lot.agreedAt)}",
                                fontSize = 10.sp,
                                color = EcoTextSecondary
                            )
                        }
                        Text(
                            text = "₹${formatRate(lot.agreedRatePerKg ?: 0.0)}/kg",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = SuccessGreen
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(140.dp)) }
    }
}

@Composable
private fun PriceComparisonCard(
    price: PriceRecord,
    offers: List<RecyclerOffer>
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = EcoWhite),
        border = BorderStroke(1.dp, EcoBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("collector_price_${price.category.name.lowercase(Locale.US)}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = EcoMint,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = ForestGreenPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Indicative reference price",
                            fontSize = 10.sp,
                            color = EcoTextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "₹${formatRate(price.prevailingBuyRate)}/kg " +
                                "(₹${formatRate(price.marketMin)}–₹${formatRate(price.marketMax)})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForestGreenPrimary
                        )
                    }
                    Text(
                        text = "Updated\n${formatTimestamp(price.updatedAt)}",
                        fontSize = 9.sp,
                        color = EcoTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (offers.isEmpty()) {
                Text(
                    text = "No authorised recycler has published an offer for this material.",
                    fontSize = 11.sp,
                    color = EcoTextSecondary
                )
            } else {
                Text(
                    text = "Actual recycler offers (${offers.size})",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = EcoTextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                offers.forEach { offer ->
                    OfferRow(offer = offer)
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun OfferRow(offer: RecyclerOffer) {
    Surface(
        color = EcoWhite,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, EcoBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("collector_offer_${offer.offerId}")
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(SuccessGreen.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Sell,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = offer.recyclerName.ifBlank { "Authorised recycler" },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = EcoTextPrimary
                )
                Text(
                    text = buildString {
                        append("Offer · ").append(offer.quantityLabel)
                        if (offer.condition.isNotBlank()) append(" · ").append(offer.condition)
                    },
                    fontSize = 10.sp,
                    color = EcoTextSecondary
                )
                Text(
                    text = "Updated ${formatTimestamp(offer.updatedAt)}" +
                        if (offer.updatedBy.isBlank()) "" else " by ${offer.updatedBy}",
                    fontSize = 9.sp,
                    color = EcoTextSecondary.copy(alpha = 0.85f)
                )
            }
            Text(
                text = "₹${formatRate(offer.pricePerKg)}/kg",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = SuccessGreen
            )
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) ForestGreenPrimary else EcoMint,
        border = BorderStroke(1.dp, if (selected) ForestGreenPrimary else EcoBorder),
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("collector_price_filter_${label.lowercase(Locale.US)}")
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) EcoWhite else EcoTextPrimary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun InfoCard(
    title: String,
    body: String,
    container: androidx.compose.ui.graphics.Color,
    testTag: String
) {
    Surface(
        color = container,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = EcoTextPrimary
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(text = body, fontSize = 11.sp, color = EcoTextSecondary)
        }
    }
}

private fun formatRate(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.2f", value)

private fun formatWeight(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.2f", value)

private fun formatTimestamp(millis: Long?): String {
    if (millis == null || millis <= 0L) return "never"
    return SimpleDateFormat("dd MMM, HH:mm", Locale.US).format(Date(millis))
}
