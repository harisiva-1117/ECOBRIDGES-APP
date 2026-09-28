package com.example.ui.map

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.core.content.ContextCompat
import com.example.model.AuthorizedRecycler
import com.example.model.Language
import com.example.model.MaterialCategory
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.ForestGreenDark
import com.example.ui.theme.ForestGreenPrimary
import com.example.ui.theme.MintBorder
import com.example.ui.theme.MintLight
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryMuted
import com.example.ui.theme.WarningAmber
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapCollectionPointsScreen(
    recyclers: List<AuthorizedRecycler>,
    language: Language,
    onBack: () -> Unit,
    initialRecyclerId: String? = null,
    onSelectRecyclerForLot: ((AuthorizedRecycler) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val fusedLocationClient = remember(context) { LocationServices.getFusedLocationProviderClient(context) }

    // Default center on Mumbai / Maharashtra cluster
    val defaultLocation = LatLng(19.0760, 72.8777)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLocation, 11.5f)
    }

    var isListView by remember { mutableStateOf(false) }
    var selectedRecycler by remember(recyclers, initialRecyclerId) {
        mutableStateOf(recyclers.firstOrNull { it.recyclerId == initialRecyclerId })
    }
    var selectedCategoryFilter by remember { mutableStateOf<MaterialCategory?>(null) }
    var filterDoorstepPickup by remember { mutableStateOf(false) }
    var collectorLocation by remember { mutableStateOf<LatLng?>(null) }

    fun loadCollectorLocation(animateCamera: Boolean = true) {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasFineLocation || hasCoarseLocation) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val latLng = LatLng(it.latitude, it.longitude)
                    collectorLocation = latLng
                    if (animateCamera && selectedRecycler == null) {
                        coroutineScope.launch {
                            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(latLng, 12.5f))
                        }
                    }
                }
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            loadCollectorLocation(animateCamera = selectedRecycler == null)
        }
    }

    LaunchedEffect(Unit) {
        loadCollectorLocation(animateCamera = initialRecyclerId == null)
    }

    // When an initial recycler is specified or selected, focus camera on that recycler
    LaunchedEffect(selectedRecycler) {
        selectedRecycler?.let { rec ->
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(LatLng(rec.latitude, rec.longitude), 14f)
            )
        }
    }

    val filteredRecyclers = remember(recyclers, selectedCategoryFilter, filterDoorstepPickup) {
        recyclers.filter { recycler ->
            val matchesCategory = selectedCategoryFilter == null || recycler.acceptedCategories.contains(selectedCategoryFilter)
            val matchesPickup = !filterDoorstepPickup || recycler.doorstepPickup
            matchesCategory && matchesPickup
        }
    }

    val mapUiSettings by remember {
        mutableStateOf(
            MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                compassEnabled = true,
                mapToolbarEnabled = false
            )
        )
    }

    val mapProperties = MapProperties(isMyLocationEnabled = collectorLocation != null)

    fun launchDirections(lat: Double, lng: Double) {
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

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = when (language) {
                                Language.ENGLISH -> "Authorized Collection Points"
                                Language.HINDI -> "अधिकृत ई-कचरा संग्रहण केंद्र"
                                Language.MARATHI -> "अधिकृत ई-कचरा संकलन केंद्र"
                                Language.TAMIL -> "அங்கீகரிக்கப்பட்ட சேகரிப்பு மையங்கள்"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = ForestGreenPrimary
                        )
                        Text(
                            text = "${filteredRecyclers.size} ${if (language == Language.HINDI) "केंद्र उपलब्ध" else "CPCB Authorized Points"}",
                            fontSize = 11.sp,
                            color = ForestGreenDark
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("map_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = ForestGreenPrimary
                        )
                    }
                },
                actions = {
                    // View mode toggle (Map vs List)
                    IconButton(
                        onClick = { isListView = !isListView },
                        modifier = Modifier.testTag("toggle_map_list_view")
                    ) {
                        Icon(
                            imageVector = if (isListView) Icons.Default.Map else Icons.AutoMirrored.Filled.List,
                            contentDescription = if (isListView) "Show Map" else "Show List",
                            tint = ForestGreenPrimary
                        )
                    }

                    // Recenter map
                    if (!isListView) {
                        IconButton(
                            onClick = {
                                val target = collectorLocation ?: defaultLocation
                                coroutineScope.launch {
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newLatLngZoom(target, 12f)
                                    )
                                }
                            },
                            modifier = Modifier.testTag("map_recenter_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Recenter Map",
                                tint = ForestGreenPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isListView) {
                // List View of Authorized Recyclers with Proximity and Directions
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MintLight,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when (language) {
                                        Language.ENGLISH -> "Showing recyclers sorted by distance. Tap 'View on Map' or 'Navigate' for live route."
                                        Language.HINDI -> "दूरी के अनुसार रीसायकलर। लाइव रूट के लिए 'मैप पर देखें' या 'दिशा-निर्देश' टैप करें।"
                                        Language.MARATHI -> "अंतराप्रमाणे क्रमवारी. थेट मार्गासाठी 'नकाशावर पहा' किंवा 'मार्ग' टॅप करा."
                                        Language.TAMIL -> "மறுசுழற்சியாளர்கள் தூரப்படி வரிசைப்படுத்திக் காட்டப்படுகின்றன. நேரடி வழிக்கு 'வரைபடத்தில் காண்' அல்லது 'செல்லவும்' என்பதைத் தட்டவும்."
                                    },
                                    fontSize = 12.sp,
                                    color = ForestGreenDark,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    items(filteredRecyclers) { rec ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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
                                        text = rec.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = TextPrimaryDark,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Star, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(text = "${rec.rating}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "${rec.cpcbRegNo} • ${rec.authorizationValidity}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SuccessGreen
                                )

                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${rec.facilityLocation} (${rec.distanceKm} km away)",
                                        fontSize = 11.sp,
                                        color = TextSecondaryMuted
                                    )
                                }

                                if (rec.doorstepPickup) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Surface(shape = RoundedCornerShape(6.dp), color = MintLight) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.LocalShipping, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Doorstep Pickup (Min ${rec.minWeightForPickupKg.toInt()} kg)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ForestGreenPrimary)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Action Buttons Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // View on Map button
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MintLight,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                selectedRecycler = rec
                                                isListView = false
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 7.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Map, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("View on Map", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ForestGreenPrimary)
                                        }
                                    }

                                    // Navigate button
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = ForestGreenPrimary,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { launchDirections(rec.latitude, rec.longitude) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 7.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Directions, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Directions", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }

                                    // Call button
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MintLight,
                                        modifier = Modifier
                                            .weight(0.7f)
                                            .clickable {
                                                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${rec.phone}"))
                                                context.startActivity(dialIntent)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 7.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Call, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Call", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ForestGreenPrimary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Interactive Google Map Composable
                GoogleMap(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("google_map_view"),
                    cameraPositionState = cameraPositionState,
                    uiSettings = mapUiSettings,
                    properties = mapProperties,
                    onMapClick = {
                        selectedRecycler = null
                    }
                ) {
                    // Render markers for authorized recyclers
                    filteredRecyclers.forEach { recycler ->
                        val position = LatLng(recycler.latitude, recycler.longitude)
                        val isSelected = selectedRecycler?.recyclerId == recycler.recyclerId

                        Marker(
                            state = MarkerState(position = position),
                            title = recycler.name,
                            snippet = "${recycler.facilityLocation} • ${recycler.distanceKm} km away",
                            icon = if (isSelected) {
                                BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                            } else {
                                BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                            },
                            onClick = {
                                selectedRecycler = recycler
                                coroutineScope.launch {
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newLatLngZoom(position, 14f)
                                    )
                                }
                                true
                            }
                        )
                    }

                    // Render marker for collector's current GPS location
                    collectorLocation?.let { location ->
                        Marker(
                            state = MarkerState(position = location),
                            title = when (language) {
                                Language.ENGLISH -> "Your Location (Collector)"
                                Language.HINDI -> "आपका स्थान (संग्राहक)"
                                Language.MARATHI -> "तुमचे ठिकाण (संकलक)"
                                Language.TAMIL -> "உங்கள் இருப்பிடம் (சேகரிப்பாளர்)"
                            },
                            snippet = "Current operating location",
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                        )
                    }
                }

                // Top Filter Chips Overlay
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .align(Alignment.TopCenter)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.95f),
                        shadowElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = selectedCategoryFilter == null && !filterDoorstepPickup,
                                onClick = {
                                    selectedCategoryFilter = null
                                    filterDoorstepPickup = false
                                },
                                label = { Text("All (${recyclers.size})", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ForestGreenPrimary,
                                    selectedLabelColor = Color.White
                                ),
                                modifier = Modifier.testTag("filter_all_points")
                            )

                            FilterChip(
                                selected = filterDoorstepPickup,
                                onClick = { filterDoorstepPickup = !filterDoorstepPickup },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Doorstep Pickup", fontSize = 11.sp)
                                    }
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ForestGreenPrimary,
                                    selectedLabelColor = Color.White
                                ),
                                modifier = Modifier.testTag("filter_doorstep")
                            )

                            MaterialCategory.values().take(4).forEach { cat ->
                                val isSelected = selectedCategoryFilter == cat
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedCategoryFilter = if (isSelected) null else cat
                                    },
                                    label = {
                                        Text("${cat.iconEmoji} ${cat.getTitle(language)}", fontSize = 11.sp)
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ForestGreenPrimary,
                                        selectedLabelColor = Color.White
                                    ),
                                    modifier = Modifier.testTag("filter_cat_${cat.id}")
                                )
                            }
                        }
                    }

                    // Helper banner if no recycler selected
                    if (selectedRecycler == null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White.copy(alpha = 0.9f),
                            shadowElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📍 " + when (language) {
                                        Language.ENGLISH -> "Tap any pin on map or switch to List 📋 to navigate"
                                        Language.HINDI -> "मैप पर किसी भी पिन को टैप करें या लिस्ट 📋 पर स्विच करें"
                                        Language.MARATHI -> "नकाशावरील पिन टॅप करा किंवा लिस्ट 📋 पहा"
                                        Language.TAMIL -> "செல்ல வரைபடத்தில் எந்த பின்னையும் தட்டவும் அல்லது பட்டியல் முறைக்கு மாறவும்"
                                    },
                                    fontSize = 11.sp,
                                    color = ForestGreenPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Recenter Floating Button
                FloatingActionButton(
                    onClick = {
                        val hasLocationPermission = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasLocationPermission) {
                            loadCollectorLocation(animateCamera = true)
                            collectorLocation?.let {
                                coroutineScope.launch {
                                    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(it, 13f))
                                }
                            }
                        } else {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                    containerColor = Color.White,
                    contentColor = ForestGreenPrimary,
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = if (selectedRecycler != null) 250.dp else 24.dp, end = 16.dp)
                        .testTag("fab_my_location")
                ) {
                    Icon(imageVector = Icons.Default.MyLocation, contentDescription = "Current Location")
                }

                // Bottom Selected Recycler Details Sheet Card
                AnimatedVisibility(
                    visible = selectedRecycler != null,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                ) {
                    selectedRecycler?.let { recycler ->
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MintBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("selected_recycler_card")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                // Header Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(MintLight),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = "🏭", fontSize = 20.sp)
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = recycler.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = TextPrimaryDark
                                            )
                                            Text(
                                                text = "${recycler.cpcbRegNo} • ${recycler.authorizationValidity}",
                                                fontSize = 10.sp,
                                                color = SuccessGreen,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { selectedRecycler = null },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondaryMuted)
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Location & Distance
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = ForestGreenPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${recycler.facilityLocation} • ${recycler.distanceKm} km away",
                                        fontSize = 11.sp,
                                        color = TextSecondaryMuted
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Star, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(text = "${recycler.rating}", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Accepted Categories Badges
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    recycler.acceptedCategories.forEach { cat ->
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MintLight
                                        ) {
                                            Text(
                                                text = "${cat.iconEmoji} ${cat.getTitle(language)}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = ForestGreenPrimary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Action Buttons: Directions, Call, Match
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Directions
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MintLight,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { launchDirections(recycler.latitude, recycler.longitude) }
                                            .testTag("btn_get_directions")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 9.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Directions, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Directions", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ForestGreenPrimary)
                                        }
                                    }

                                    // Call Button
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MintLight,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${recycler.phone}"))
                                                context.startActivity(dialIntent)
                                            }
                                            .testTag("btn_call_from_map")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 9.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Call, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Call", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ForestGreenPrimary)
                                        }
                                    }

                                    // Match / Select Button
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = ForestGreenPrimary,
                                        modifier = Modifier
                                            .weight(1.3f)
                                            .clickable {
                                                onSelectRecyclerForLot?.invoke(recycler)
                                            }
                                            .testTag("btn_select_collection_point")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 9.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Select Hub", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
