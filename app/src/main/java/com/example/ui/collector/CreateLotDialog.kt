package com.example.ui.collector

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.model.AuthorizedRecycler
import com.example.model.Language
import com.example.model.MaterialCategory
import com.example.model.PaymentMode
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.ForestGreenDark
import com.example.ui.theme.ForestGreenPrimary
import com.example.ui.theme.MintBorder
import com.example.ui.theme.MintLight
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryMuted
import com.example.ui.theme.WarningAmber
import java.io.File

/** Editable component line produced by AI or added manually by the collector. */
private data class ComponentDraft(
    val name: String,
    val isUncertain: Boolean
)

/** Explicit, predictable stages of the lot-creation workflow. */
private enum class CreateLotStep { PHOTO, CAPTURED, DETAILS, REVIEW }

/** One component already weighed/captured by the AI Scrap Scanner flow. */
data class ScannedLotItem(
    val name: String,
    val category: MaterialCategory,
    val weightKg: Double?,
    val estimatedValueInr: Double,
    val imageUri: String
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateLotDialog(
    viewModel: CollectorDashboardViewModel,
    initialCategory: MaterialCategory? = null,
    initialPhotoUri: String? = null,
    initialPhotoUris: List<String> = emptyList(),
    initialItems: List<ScannedLotItem> = emptyList(),
    collectorLabel: String = "",
    availableRecyclers: List<AuthorizedRecycler>,
    language: Language,
    onDismiss: () -> Unit,
    onLotCreated: (
        category: MaterialCategory,
        subCategory: String,
        weightKg: Double,
        condition: String,
        location: String,
        gpsCoordinates: String,
        matchedRecycler: AuthorizedRecycler?,
        paymentMode: PaymentMode,
        draftLotId: String?
    ) -> Unit
) {
    var step by remember { mutableStateOf(CreateLotStep.PHOTO) }
    var selectedCategory by remember { mutableStateOf(initialCategory ?: MaterialCategory.PCB_BOARDS) }
    var weightInput by remember { mutableStateOf("") }
    var weightTouched by remember { mutableStateOf(false) }
    var conditionText by remember { mutableStateOf("Sorted & Clean Scrap") }
    var locationInput by remember { mutableStateOf("") }
    var selectedPaymentMode by remember { mutableStateOf(PaymentMode.CASH) }
    var components by remember { mutableStateOf<List<ComponentDraft>>(emptyList()) }

    // Captured-image review state (kept with the draft until confirmed or deleted).
    var capturedUri by remember { mutableStateOf<String?>(null) }
    var capturedPhotoId by remember { mutableStateOf<String?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var pendingOpenCamera by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val parsedWeight = weightInput.trim().replace(',', '.').toDoubleOrNull()
    val weightValid = parsedWeight != null && parsedWeight > 0.0 && !parsedWeight.isNaN() && !parsedWeight.isInfinite()
    val weightError = when {
        !weightTouched -> null
        weightInput.isBlank() -> "Weight is required."
        parsedWeight == null -> "Enter a valid number (kg)."
        parsedWeight <= 0.0 -> "Weight must be greater than 0 kg."
        else -> null
    }
    val displayWeight = parsedWeight?.takeIf { it > 0.0 } ?: 0.0

    val rankedMatch = com.example.ai.RecyclerMatcher.topMatch(selectedCategory, displayWeight.coerceAtLeast(0.01), availableRecyclers)
    val matchedRecycler = rankedMatch?.recycler

    val duplicateHint by viewModel.duplicateHint.collectAsState()
    val aiAnalysis by viewModel.aiAnalysis.collectAsState()
    val aiUnavailable by viewModel.aiUnavailableReason.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val isCreatingLot by viewModel.isCreatingLot.collectAsState()
    val createError by viewModel.createError.collectAsState()

    val aiSnapshot = aiAnalysis
    val unavailableSnapshot = aiUnavailable

    val prevailingRate = matchedRecycler?.buyingRates?.get(selectedCategory) ?: selectedCategory.defaultRatePerKg
    val estimatedTotalInr = displayWeight * prevailingRate
    val rateLabel = aiSnapshot?.let { a ->
        val min = a.priceMinPerKg
        val max = a.priceMaxPerKg
        if (min != null && max != null) "AI range ₹${min.toInt()}–₹${max.toInt()}/kg" else "Rate ₹${prevailingRate.toInt()}/kg"
    } ?: "Rate ₹${prevailingRate.toInt()}/kg"

    // Start a fresh creation session when this dialog opens.
    LaunchedEffect(Unit) {
        viewModel.beginCreationSession()
        viewModel.clearAiAnalysis()
        viewModel.clearCreateError()
        viewModel.refreshWorkbench()
        locationInput = viewModel.location.value?.areaLabel?.let { "$it, approx. area" } ?: ""
        val seedPhotos = buildList {
            if (!initialPhotoUri.isNullOrBlank()) add(initialPhotoUri)
            addAll(initialPhotoUris)
        }.distinct()
        seedPhotos.forEachIndexed { idx, uri ->
            if (idx == 0) {
                capturedUri = uri
                viewModel.addDraftPhoto(uri) { id -> capturedPhotoId = id }
            } else {
                viewModel.addDraftPhoto(uri)
            }
        }
        if (seedPhotos.isNotEmpty()) {
            step = CreateLotStep.CAPTURED
        }
        // Carry over what the AI Scrap Scanner already captured: each scanned
        // component becomes its own line item and the lot weight is pre-filled
        // with the sum of the per-item weights (no second weight entry).
        if (initialItems.isNotEmpty()) {
            components = initialItems.map {
                ComponentDraft(it.name.ifBlank { it.category.getTitle(language) }, false)
            }
            val carriedWeight = initialItems.mapNotNull { it.weightKg }.sum()
            if (carriedWeight > 0.0) {
                weightInput = String.format(java.util.Locale.US, "%.1f", carriedWeight)
                weightTouched = true
            }
        }
    }

    val currentSessionId = viewModel.draftLotId.value
    val sessionPhotos = viewModel.pendingPhotos.collectAsState().value
        .filter { currentSessionId == null || it.lotId == currentSessionId }

    // Keep the duplicate warning live whenever category/weight change.
    LaunchedEffect(selectedCategory, parsedWeight) {
        viewModel.checkDuplicateCandidate(selectedCategory, parsedWeight)
    }

    // When AI analysis arrives, prefill the editable component summary and category/weight.
    LaunchedEffect(aiAnalysis) {
        val analysis = aiAnalysis ?: return@LaunchedEffect
        components = analysis.components.map { ComponentDraft(it.name, it.isUncertain) }
        analysis.detectedCategory?.let { detected ->
            selectedCategory = detected
        }
        if (weightInput.isBlank()) {
            analysis.estimatedWeightKg?.let { w ->
                weightInput = String.format(java.util.Locale.US, "%.1f", w)
                weightTouched = true
            }
        }
    }

    fun cleanupAndDismiss() {
        sessionPhotos.forEach { viewModel.removeDraftPhoto(it.photoId) }
        viewModel.endCreationSession()
        onDismiss()
    }

    // Real device capture: gallery picker (max 10) and system camera via FileProvider.
    var lastCameraUri by remember { mutableStateOf<Uri?>(null) }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris ->
        if (uris.isNotEmpty()) {
            // Add each selected image exactly once; the first becomes the primary
            // photo under review.
            uris.forEachIndexed { index, uri ->
                if (index == 0) {
                    viewModel.addDraftPhoto(uri.toString()) { id -> capturedPhotoId = id }
                } else {
                    viewModel.addDraftPhoto(uri.toString())
                }
            }
            capturedUri = uris.first().toString()
            step = CreateLotStep.CAPTURED
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            lastCameraUri?.let { uri ->
                capturedUri = uri.toString()
                viewModel.addDraftPhoto(uri.toString()) { id -> capturedPhotoId = id }
                cameraError = null
                step = CreateLotStep.CAPTURED
            }
        } else {
            cameraError = "Photo was not captured. Try again or upload from gallery."
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingOpenCamera = true
        } else {
            cameraError = "Camera permission is required to capture e-waste images."
        }
    }
    fun launchCamera() {
        try {
            val dir = File(context.cacheDir, "photo_cache").apply { mkdirs() }
            val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            lastCameraUri = uri
            cameraError = null
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            Log.e("CreateLotDialog", "Camera launch failed", e)
            cameraError = "Camera is unavailable on this device. Please upload from gallery."
        }
    }
    fun requestCamera() {
        val granted = context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) launchCamera() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }
    fun deleteCapturedPhoto() {
        capturedPhotoId?.let { viewModel.removeDraftPhoto(it) }
        capturedUri = null
        capturedPhotoId = null
        cameraError = null
        step = CreateLotStep.PHOTO
    }
    fun retakePhoto() {
        capturedPhotoId?.let { viewModel.removeDraftPhoto(it) }
        capturedUri = null
        capturedPhotoId = null
        cameraError = null
        step = CreateLotStep.PHOTO
        requestCamera()
    }

    // Reopen the camera after a granted permission request.
    LaunchedEffect(pendingOpenCamera) {
        if (pendingOpenCamera) {
            pendingOpenCamera = false
            launchCamera()
        }
    }

    val gpsCoordinates = viewModel.location.value?.let { "${it.latitude}, ${it.longitude}" } ?: ""

    Dialog(
        onDismissRequest = { cleanupAndDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 24.dp)
                .testTag("create_lot_dialog")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Dialog Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = when (language) {
                                Language.ENGLISH -> "Create Digital E-Waste Lot"
                                Language.HINDI -> "डिजिटल ई-कचरा लॉट बनाएं"
                                Language.MARATHI -> "नवीन ई-कचरा लॉट तयार करा"
                                Language.TAMIL -> "டிஜிட்டல் மின்-கழிவு லாட் உருவாக்கு"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = ForestGreenPrimary
                        )
                        Text(
                            text = "Photo → AI → Weight → Review → Create",
                            fontSize = 11.sp,
                            color = TextSecondaryMuted
                        )
                    }
                    IconButton(
                        onClick = { cleanupAndDismiss() },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MintLight)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = ForestGreenPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Step indicator
                CreateLotStepIndicator(step = step)

                when (step) {
                    CreateLotStep.PHOTO, CreateLotStep.CAPTURED -> {
                        // ---- STEP 1: Real photo capture ----
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MintLight.copy(alpha = 0.5f)),
                            border = BorderStroke(1.dp, MintBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Photograph the Material",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = ForestGreenPrimary
                                    )
                                    Text(
                                        text = "${sessionPhotos.size}/10",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (sessionPhotos.size >= 10) WarningAmber else TextSecondaryMuted
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                if (sessionPhotos.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        sessionPhotos.forEach { photo ->
                                            Box {
                                                AsyncImage(
                                                    model = photo.localUri,
                                                    contentDescription = "Attached photo",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(64.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                )
                                                IconButton(
                                                    onClick = {
                                                        viewModel.removeDraftPhoto(photo.photoId)
                                                        if (photo.photoId == capturedPhotoId) {
                                                            capturedUri = null
                                                            capturedPhotoId = null
                                                            if (step == CreateLotStep.CAPTURED) step = CreateLotStep.PHOTO
                                                        }
                                                    },
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .size(22.dp)
                                                        .clip(CircleShape)
                                                        .background(Color.Black.copy(alpha = 0.55f))
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Remove photo",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                                if (sessionPhotos.size < 10) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = { requestCamera() },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(44.dp)
                                                .testTag("capture_photo_btn")
                                        ) {
                                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Camera", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                        }
                                        Button(
                                            onClick = {
                                                try {
                                                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                                } catch (e: Exception) {
                                                    Log.e("CreateLotDialog", "Gallery launch failed", e)
                                                    cameraError = "Could not open gallery. Please try again."
                                                }
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(44.dp)
                                                .testTag("gallery_btn")
                                        ) {
                                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Gallery", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Photos are stored securely and uploaded to your private lot folder.",
                                    fontSize = 10.sp,
                                    color = TextSecondaryMuted
                                )
                            }
                        }

                        // Camera error + gallery fallback
                        cameraError?.let { message ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                                border = BorderStroke(1.dp, WarningAmber),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("camera_error_card")
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(message, fontSize = 12.sp, color = TextPrimaryDark, fontWeight = FontWeight.SemiBold)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { requestCamera() },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("camera_try_again_btn")
                                        ) { Text("Try Again", fontSize = 12.sp, color = ForestGreenPrimary) }
                                        OutlinedButton(
                                            onClick = {
                                                try {
                                                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                                } catch (e: Exception) {
                                                    Log.e("CreateLotDialog", "Gallery launch failed", e)
                                                }
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("camera_gallery_fallback_btn")
                                        ) { Text("Upload From Gallery", fontSize = 12.sp, color = ForestGreenPrimary) }
                                    }
                                }
                            }
                        }
                    }

                    else -> Unit
                }

                // ---- STEP 2: Captured image review ----
                if (step == CreateLotStep.CAPTURED) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.5.dp, ForestGreenPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("captured_review_card")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Captured Image", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ForestGreenPrimary)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            val uri = capturedUri
                            if (uri != null) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "Captured e-waste photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(260.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(1.dp, MintBorder, RoundedCornerShape(14.dp))
                                        .testTag("captured_image")
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { deleteCapturedPhoto() },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .testTag("delete_photo_btn")
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Delete Photo", fontSize = 12.sp, color = ErrorRed)
                                }
                                OutlinedButton(
                                    onClick = { retakePhoto() },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .testTag("retake_photo_btn")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Retake Photo", fontSize = 12.sp, color = ForestGreenPrimary)
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    step = CreateLotStep.DETAILS
                                    // Scanner-provided items are already analyzed; only re-analyze
                                    // when the photos were captured inside the lot dialog itself.
                                    if (initialItems.isEmpty()) {
                                        capturedPhotoId?.let { id ->
                                            viewModel.analyzeDraftPhoto(id, selectedCategory, language)
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("use_photo_btn")
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Use This Photo", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "The photo stays attached to this lot draft until you confirm or delete it.",
                                fontSize = 10.sp,
                                color = TextSecondaryMuted
                            )
                        }
                    }
                }

                // ---- STEP 3: AI identification + weight + details ----
                if (step == CreateLotStep.DETAILS) {
                    // AI Analysis — real call, honest unavailable state
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.5.dp, if (aiAnalysis != null || isAnalyzing) ForestGreenPrimary else MintBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("AI Material Analysis", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ForestGreenPrimary)
                                }
                                if (isAnalyzing) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = ForestGreenPrimary)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            if (isAnalyzing) {
                                Text("Inspecting the photo… this may take a few seconds.", fontSize = 11.sp, color = TextSecondaryMuted)
                            } else if (aiSnapshot != null) {
                                Text(aiSnapshot.summary, fontSize = 12.sp, color = TextPrimaryDark, fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.height(6.dp))
                                aiSnapshot.priceMinPerKg?.let { min ->
                                    aiSnapshot.priceMaxPerKg?.let { max ->
                                        Text("Estimate range ₹${min.toInt()}–₹${max.toInt()}/kg", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ForestGreenPrimary)
                                    }
                                }
                                Text(aiSnapshot.disclaimer, fontSize = 10.sp, color = TextSecondaryMuted)
                            } else if (unavailableSnapshot != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        unavailableSnapshot ?: "AI identification unavailable. Please select the e-waste category manually.",
                                        fontSize = 11.sp,
                                        color = WarningAmber,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            } else if (initialItems.isNotEmpty()) {
                                Text(
                                    "Carried from your scan: ${initialItems.size} item(s), ${selectedCategory.getTitle(language)}. Edit below or tap Analysis to re-run.",
                                    fontSize = 11.sp,
                                    color = TextSecondaryMuted
                                )
                            } else {
                                Text(
                                    "Run AI on the captured photo, or select the category and enter the weight manually.",
                                    fontSize = 11.sp,
                                    color = TextSecondaryMuted
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    capturedPhotoId?.let { id ->
                                        viewModel.analyzeDraftPhoto(id, selectedCategory, language)
                                    }
                                },
                                enabled = capturedPhotoId != null && !isAnalyzing,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MintLight, disabledContainerColor = Color(0xFFEEEEEE)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .testTag("analyze_photos_btn")
                            ) {
                                Text(
                                    text = if (aiAnalysis != null) "Re-analyze Photo" else "Analyze Photo with AI",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (capturedPhotoId != null) ForestGreenPrimary else TextSecondaryMuted
                                )
                            }
                        }
                    }

                    // Duplicate prevention warning
                    duplicateHint?.let { dup ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.12f)),
                            border = BorderStroke(1.dp, WarningAmber),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "${dup.weightKg} kg ${dup.category.getTitle(language)} lot already created recently. Please verify you are not entering a duplicate.",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF8A5A00)
                                    )
                                    Text(text = "Lot ${dup.lotId} • ${dup.collectionTimestamp}", fontSize = 10.sp, color = TextSecondaryMuted)
                                }
                            }
                        }
                    }

                    // Material Category Selector
                    Column {
                        Text(
                            text = if (unavailableSnapshot != null) "Select Material Category (manual)" else "Select Material Category",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = TextPrimaryDark
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (cat in MaterialCategory.values()) {
                                val isSelected = selectedCategory == cat
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) ForestGreenPrimary else MintLight,
                                    border = BorderStroke(1.dp, if (isSelected) ForestGreenPrimary else MintBorder),
                                    modifier = Modifier
                                        .clickable { selectedCategory = cat }
                                        .testTag("lot_cat_${cat.name.lowercase()}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = cat.iconEmoji, fontSize = 16.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = cat.getTitle(language),
                                            color = if (isSelected) Color.White else TextPrimaryDark,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Weight (manual entry, validated)
                    Column {
                        Text("Enter Weight (Kilograms)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimaryDark)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    weightTouched = true
                                    val w = ((parsedWeight ?: 0.0) - 1.0).coerceAtLeast(0.0)
                                    weightInput = String.format("%.1f", w)
                                },
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MintLight)
                                    .testTag("weight_minus")
                            ) {
                                Icon(imageVector = Icons.Default.Remove, contentDescription = "Decrease", tint = ForestGreenPrimary)
                            }
                            OutlinedTextField(
                                value = weightInput,
                                onValueChange = {
                                    weightInput = it
                                    weightTouched = true
                                },
                                isError = weightError != null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("weight_input_field"),
                                shape = RoundedCornerShape(12.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    color = ForestGreenPrimary
                                ),
                                suffix = { Text("kg", fontWeight = FontWeight.Bold) }
                            )
                            IconButton(
                                onClick = {
                                    weightTouched = true
                                    val w = (parsedWeight ?: 0.0) + 1.0
                                    weightInput = String.format("%.1f", w)
                                },
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MintLight)
                                    .testTag("weight_plus")
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = "Increase", tint = ForestGreenPrimary)
                            }
                        }
                        weightError?.let { err ->
                            Text(
                                text = err,
                                color = ErrorRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .testTag("weight_error")
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(0.5, 2.0, 5.0, 10.0, 25.0).forEach { presetKg ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MintLight,
                                    border = BorderStroke(1.dp, MintBorder),
                                    modifier = Modifier.clickable {
                                        weightTouched = true
                                        weightInput = presetKg.toString()
                                    }
                                ) {
                                    Text(
                                        text = "${presetKg} kg",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ForestGreenPrimary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Editable components (from AI or manual)
                    Column {
                        Text("Components & Items in Lot", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimaryDark)
                        Spacer(modifier = Modifier.height(8.dp))
                        components.forEachIndexed { index, component ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = component.name,
                                    onValueChange = { newName ->
                                        components = components.toMutableList().also { it[index] = it[index].copy(name = newName) }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                                    leadingIcon = if (component.isUncertain) {
                                        {
                                            Text(
                                                text = "?",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = WarningAmber,
                                                modifier = Modifier.padding(start = 6.dp)
                                            )
                                        }
                                    } else null
                                )
                                IconButton(
                                    onClick = { components = components.toMutableList().also { it.removeAt(index) } },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color(0xFFB00020), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        Button(
                            onClick = { components = components + ComponentDraft("", false) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MintLight),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add another component", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ForestGreenPrimary)
                            }
                        }
                    }

                    // Instant estimate
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MintLight),
                        border = BorderStroke(1.5.dp, ForestGreenPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Estimated Payout", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ForestGreenPrimary)
                                Text(rateLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryMuted)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("₹${estimatedTotalInr.toInt()}", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = ForestGreenPrimary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Estimate — verify at scale", fontSize = 12.sp, color = TextSecondaryMuted, modifier = Modifier.padding(bottom = 4.dp))
                            }
                            Text(
                                text = "Recovered: " + selectedCategory.keyRecoverableMetals.take(3).joinToString(", "),
                                fontSize = 11.sp,
                                color = ForestGreenDark,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Matched Authorized Recycler
                    if (matchedRecycler != null) {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, MintBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Default.Verified, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Top Matched Recycler", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                                    }
                                    Text("${matchedRecycler.distanceKm} km away", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = ForestGreenPrimary)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(matchedRecycler.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimaryDark)
                                Text(matchedRecycler.facilityLocation + " • CPCB Valid", fontSize = 11.sp, color = TextSecondaryMuted)
                                rankedMatch?.reasons?.take(2)?.let { reasons ->
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(reasons.joinToString(" • "), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = ForestGreenDark)
                                }
                            }
                        }
                    }

                    // Payment preference
                    Column {
                        Text("Preferred Payment Mode", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimaryDark)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selectedPaymentMode == PaymentMode.CASH) ForestGreenPrimary else MintLight,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedPaymentMode = PaymentMode.CASH }
                            ) {
                                Text(
                                    text = "💵 Cash on Scale",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedPaymentMode == PaymentMode.CASH) Color.White else TextPrimaryDark,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 10.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selectedPaymentMode == PaymentMode.UPI) ForestGreenPrimary else MintLight,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedPaymentMode = PaymentMode.UPI }
                            ) {
                                Text(
                                    text = "📱 Direct UPI",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedPaymentMode == PaymentMode.UPI) Color.White else TextPrimaryDark,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 10.dp)
                                )
                            }
                        }
                    }

                    // Continue to review (weight must be valid)
                    Button(
                        onClick = {
                            weightTouched = true
                            if (weightValid) step = CreateLotStep.REVIEW
                        },
                        enabled = weightValid,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ForestGreenPrimary,
                            disabledContainerColor = Color(0xFFBDBDBD)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("continue_to_review_btn")
                    ) {
                        Text("Continue", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                    }
                }

                // ---- STEP 4: Lot review + explicit create ----
                if (step == CreateLotStep.REVIEW) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.5.dp, ForestGreenPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("lot_review_card")
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Review E-Waste Lot", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = ForestGreenPrimary)
                            Spacer(modifier = Modifier.height(10.dp))
                            capturedUri?.let { uri ->
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "Lot photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, MintBorder, RoundedCornerShape(12.dp))
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                            ReviewRow("Category", selectedCategory.getTitle(language))
                            ReviewRow("AI Confidence", if (aiSnapshot != null) "Identified by AI model (confidence not reported)" else "Not available")
                            ReviewRow("Weight", "${displayWeight} kg")
                            ReviewRow("Collector", collectorLabel.ifBlank { "Authenticated collector" })
                            ReviewRow("Location", locationInput.ifBlank { "Local Collection Point" })
                            ReviewRow("Payment", if (selectedPaymentMode == PaymentMode.CASH) "Cash on Scale" else "Direct UPI")
                            ReviewRow("Recycler", matchedRecycler?.name ?: "Authorized Recycler Hub")
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Estimated Payout", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                                Text("₹${estimatedTotalInr.toInt()}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = ForestGreenPrimary)
                            }
                        }
                    }

                    createError?.let { err ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.1f)),
                            border = BorderStroke(1.dp, ErrorRed),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("create_error_card")
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(err, fontSize = 11.sp, color = TextPrimaryDark, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { step = CreateLotStep.DETAILS },
                            enabled = !isCreatingLot,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("edit_lot_btn")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit", fontSize = 12.sp, color = ForestGreenPrimary)
                        }
                        OutlinedButton(
                            onClick = { deleteCapturedPhoto() },
                            enabled = !isCreatingLot,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("review_delete_photo_btn")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete Photo", fontSize = 12.sp, color = ErrorRed)
                        }
                    }

                    Button(
                        onClick = {
                            val subCategory = if (aiSnapshot != null) {
                                (aiSnapshot.summary.take(200) + " ${selectedCategory.getTitle(language)}").trim()
                            } else {
                                components.joinToString(", ") { it.name.replace("\n", " ") }.take(200)
                                    .ifBlank { selectedCategory.getTitle(language) }
                            }
                            onLotCreated(
                                selectedCategory,
                                subCategory.ifBlank { selectedCategory.getTitle(language) },
                                displayWeight,
                                conditionText,
                                locationInput,
                                gpsCoordinates,
                                matchedRecycler,
                                selectedPaymentMode,
                                viewModel.draftLotId.value
                            )
                        },
                        enabled = !isCreatingLot && weightValid,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ForestGreenPrimary,
                            disabledContainerColor = Color(0xFFBDBDBD)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("confirm_create_lot_btn")
                    ) {
                        if (isCreatingLot) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Creating Lot…", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                        } else {
                            Text("Create Lot", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = TextSecondaryMuted)
        Text(
            value,
            fontSize = 12.sp,
            color = TextPrimaryDark,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun CreateLotStepIndicator(step: CreateLotStep) {
    val labels = listOf("1 Photo", "2 Captured", "3 Weight", "4 Review")
    val activeIndex = when (step) {
        CreateLotStep.PHOTO -> 0
        CreateLotStep.CAPTURED -> 1
        CreateLotStep.DETAILS -> 2
        CreateLotStep.REVIEW -> 3
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val active = index <= activeIndex
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (active) ForestGreenPrimary else MintLight,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (active) Color.White else TextSecondaryMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
        }
    }
}
