package com.example.ui.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ai.AiCaptureAnalyzer
import com.example.ai.CaptureAiSource
import com.example.ai.ObbDetection
import com.example.model.IndiaMartRates
import com.example.model.Language
import com.example.model.MaterialCategory
import com.example.ui.theme.EcoGreenBright
import com.example.ui.theme.EcoGreenDeep
import com.example.ui.theme.EcoGreenPrimary
import com.example.ui.theme.EcoOrangeAccent
import com.example.ui.theme.MintBorder
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryMuted
import com.example.ui.theme.WarningAmber
import com.example.ui.voice.BottomsFloatingDockInset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

enum class AiCaptureMode { COLLECTOR, RECYCLER }

/** One accepted photo in the guided capture flow. */
data class CapturePhotoItem(
    val index: Int,
    val imageUri: String,
    val componentName: String,
    val category: MaterialCategory?,
    val confidence: Float,
    val source: CaptureAiSource,
    val isNewOrUnknown: Boolean,
    val weightKg: Double?,
    val iotReadingKg: Double?,
    val pricePerKg: Double,
    val estimatedValueInr: Double,
    val detection: ObbDetection?
)

/** Shared helper: persists a JPEG to cache and returns a FileProvider URI string. */
fun saveCaptureBitmap(context: Context, bitmap: Bitmap): String {
    val dir = File(context.cacheDir, "photo_cache").apply { mkdirs() }
    val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
    FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
    return androidx.core.content.FileProvider
        .getUriForFile(context, "${context.packageName}.fileprovider", file)
        .toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiCaptureScreen(
    language: Language,
    mode: AiCaptureMode,
    expectedCategory: MaterialCategory?,
    onBack: () -> Unit,
    onFinish: (List<CapturePhotoItem>) -> Unit,
    onSpeakText: ((String, Language) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val analyzer = remember { AiCaptureAnalyzer(context.applicationContext) }
    val imageCapture = remember { mutableStateOf<ImageCapture?>(null) }

    val items = remember { mutableStateListOf<CapturePhotoItem>() }
    val queue = remember { mutableStateListOf<Bitmap>() }
    var capturing by remember { mutableStateOf(true) }
    var analyzing by remember { mutableStateOf(false) }
    var lastBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var lastAnalysis by remember { mutableStateOf<com.example.ai.AiComponentResult?>(null) }
    var pendingWeight by remember { mutableStateOf("") }
    var selectedManualCategory by remember { mutableStateOf<MaterialCategory?>(null) }
    var iotWeightKg by remember { mutableStateOf<Double?>(null) }
    var lastPickedCategory by remember { mutableStateOf<MaterialCategory?>(null) }
    var camError by remember { mutableStateOf(false) }

    fun resetReview() {
        lastBitmap = null
        lastAnalysis = null
        pendingWeight = ""
        selectedManualCategory = null
        analyzing = false
    }

    fun processNext() {
        val next = queue.firstOrNull() ?: run {
            resetReview()
            capturing = true
            return
        }
        queue.removeAt(0)
        analyzing = true
        capturing = false
        val bmp = next
        scope.launch {
            val analysis = analyzer.analyze(bmp, language)
            if (analysis.category != null) {
                // Confident detection sets both the batch memory and the selection.
                lastPickedCategory = analysis.category
                selectedManualCategory = analysis.category
            } else if (selectedManualCategory == null) {
                // Generic/new item: carry the previously picked category forward so a
                // same-material batch does not need the picker again (chip is highlighted).
                selectedManualCategory = lastPickedCategory
            }
            pendingWeight = ""
            lastBitmap = bmp
            lastAnalysis = analysis
            analyzing = false
        }
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            camError = true
            Toast.makeText(context, "Camera permission required for AI scan", Toast.LENGTH_SHORT).show()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(5)
    ) { uris ->
        scope.launch {
            val decoded = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    kotlin.runCatching { decodePickedImage(context, uri) }.getOrNull()
                }
            }
            queue.addAll(decoded)
            processNext()
        }
    }

    fun confirmCurrent() {
        val bmp = lastBitmap ?: return
        val analysis = lastAnalysis ?: return
        if (analysis.category == null && selectedManualCategory == null) {
            Toast.makeText(context, "Please pick a category for this item", Toast.LENGTH_SHORT).show()
            return
        }
        val category = analysis.category ?: selectedManualCategory!!
        lastPickedCategory = category
        val weight = pendingWeight.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }
        val price = IndiaMartRates.ratePerKg(category)
        items.add(
            CapturePhotoItem(
                index = items.size + 1,
                imageUri = saveCaptureBitmap(context, bmp),
                componentName = analysis.componentName.ifBlank { category.getTitle(language) },
                category = category,
                confidence = analysis.confidence,
                source = analysis.source,
                isNewOrUnknown = analysis.isNewOrUnknown,
                weightKg = weight,
                iotReadingKg = weight?.let { iotWeightKg },
                pricePerKg = price,
                estimatedValueInr = weight?.let { it * price } ?: 0.0,
                detection = analysis.onnxDetection
            )
        )
        bmp.recycle()
        resetReview()
        processNext()
    }

    DisposableEffect(Unit) {
        BottomsFloatingDockInset.hidden.value = true
        onDispose {
            BottomsFloatingDockInset.hidden.value = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            analyzer.close()
            queue.forEach { it.recycle() }
            lastBitmap?.recycle()
        }
    }

    val title = when (mode) {
        AiCaptureMode.COLLECTOR -> when (language) {
            Language.ENGLISH -> "AI Scrap Scanner"
            Language.HINDI -> "AI स्क्रैप स्कैनर"
            Language.MARATHI -> "AI स्क्रॅप स्कॅनर"
            Language.TAMIL -> "AI ஸ்கிராப் ஸ்கேனர்"
        }
        AiCaptureMode.RECYCLER -> when (language) {
            Language.ENGLISH -> "Weigh-in AI Scan"
            Language.HINDI -> "वज़न AI स्कैन"
            Language.MARATHI -> "वजन AI स्कॅन"
            Language.TAMIL -> "எடை AI ஸ்கேன்"
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextPrimaryDark)
                        if (items.isNotEmpty()) {
                            Text(
                                text = when (language) {
                                    Language.ENGLISH -> "${items.size} photo(s) added — add more or finish"
                                    Language.HINDI -> "${items.size} फोटो जोड़े गए — और जोड़ें या पूरा करें"
                                    Language.MARATHI -> "${items.size} फोटो जोडले — अधिक जोडा किंवा पूर्ण करा"
                                    Language.TAMIL -> "${items.size} புகைப்படங்கள் சேர்க்கப்பட்டன — மேலும் சேர்க்கவும் அல்லது முடிக்கவும்"
                                },
                                fontSize = 10.sp,
                                color = TextSecondaryMuted
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (queue.isNotEmpty() || lastBitmap != null) {
                                queue.clear()
                                resetReview()
                                capturing = true
                            } else {
                                onBack()
                            }
                        },
                        modifier = Modifier.testTag("aicapture_nav_back")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (items.isNotEmpty() && !analyzing && lastAnalysis == null) {
                        Button(
                            onClick = { onFinish(items.toList()) },
                            colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary),
                            modifier = Modifier.padding(end = 8.dp).testTag("aicapture_done")
                        ) {
                            Text(
                                when (language) {
                                    Language.ENGLISH -> "Finish"
                                    Language.HINDI -> "समाप्त"
                                    Language.MARATHI -> "पूर्ण"
                                    Language.TAMIL -> "முடி"
                                }, fontWeight = FontWeight.Bold, fontSize = 12.sp
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White)
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black)
        ) {
            val currentReview = lastBitmap
            when {
                camError || !hasCameraPermission -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = when (language) {
                                Language.ENGLISH -> "Camera access is needed to capture e-waste photos"
                                Language.HINDI -> "ई-कचरा फोटो के लिए कैमरा अनुमति आवश्यक है"
                                Language.MARATHI -> "ई-कचरा फोटोसाठी कॅमेरा परवानगी आवश्यक आहे"
                                Language.TAMIL -> "மின்-கழிவு புகைப்படங்களுக்கு கேமரா அனுமதி தேவை"
                            },
                            color = Color.White,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Button(
                            onClick = {
                                camError = false
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary)
                        ) {
                            Text(if (camError) {
                                when (language) {
                                    Language.ENGLISH -> "Open app settings"
                                    Language.HINDI -> "सेटिंग्स खोलें"
                                    Language.MARATHI -> "सेटिंग्ज उघडा"
                                    Language.TAMIL -> "அமைப்புகளை திற"
                                }
                            } else {
                                when (language) {
                                    Language.ENGLISH -> "Grant camera access"
                                    Language.HINDI -> "कैमरा अनुमति दें"
                                    Language.MARATHI -> "कॅमेरा परवानगी द्या"
                                    Language.TAMIL -> "கேமரா அனுமதி வழங்கவும்"
                                }
                            })
                        }
                    }
                }
                currentReview != null && !analyzing -> {
                    Image(
                        bitmap = currentReview.asImageBitmap(),
                        contentDescription = "Captured item",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(Color.Black.copy(alpha = 0.80f))
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        ReviewCard(
                            language = language,
                            analysis = lastAnalysis,
                            selectedCategory = selectedManualCategory,
                            onPickCategory = { selectedManualCategory = it },
                            expectedCategory = expectedCategory,
                            pendingWeight = pendingWeight,
                            onWeightChange = { pendingWeight = it },
                            iotWeightKg = iotWeightKg,
                            onIotFakeReading = { w ->
                                iotWeightKg = w
                                pendingWeight = "%s".format(w)
                            },
                            onConfirm = { confirmCurrent() },
                            onDiscard = {
                                currentReview.recycle()
                                resetReview()
                                processNext()
                            }
                        )
                    }
                }
                analyzing -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = EcoGreenBright, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = when (language) {
                                Language.ENGLISH -> "Detecting components with AI..."
                                Language.HINDI -> "AI से घटक पहचाना जा रहा है..."
                                Language.MARATHI -> "AI द्वारे घटक ओळखले जात आहेत..."
                                Language.TAMIL -> "AI மூலம் கூறுகள் கண்டறியப்படுகின்றன..."
                            },
                            color = Color.White,
                            fontSize = 13.sp
                        )
                    }
                }
                else -> {
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                            val providerFuture = ProcessCameraProvider.getInstance(ctx)
                            providerFuture.addListener({
                                try {
                                    val provider = providerFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }
                                    val capture = ImageCapture.Builder()
                                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                        .build()
                                    imageCapture.value = capture
                                    provider.unbindAll()
                                    provider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        capture
                                    )
                                } catch (e: Exception) {
                                    // leave shutter disabled
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        },
                        modifier = Modifier.fillMaxSize().testTag("aicapture_preview")
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 40.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(containerColor = EcoOrangeAccent),
                            modifier = Modifier.size(56.dp).testTag("aicapture_gallery")
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Gallery", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(20.dp))
                        IconButton(
                            onClick = {
                                val capture = imageCapture.value ?: return@IconButton
                                capture.takePicture(
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageCapturedCallback() {
                                        override fun onCaptureSuccess(image: ImageProxy) {
                                            val bmp = imageProxyToBitmap(image)
                                            image.close()
                                            queue.add(bmp)
                                            processNext()
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            Toast.makeText(context, "Capture failed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            },
                            colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(containerColor = EcoGreenPrimary),
                            modifier = Modifier
                                .size(76.dp)
                                .border(4.dp, Color.White, CircleShape)
                                .testTag("aicapture_shutter")
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Capture", tint = Color.White, modifier = Modifier.size(34.dp))
                        }
                        Spacer(modifier = Modifier.width(20.dp))
                        IconButton(
                            enabled = false,
                            onClick = {},
                            colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(containerColor = Color.Transparent),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = "", tint = Color.Transparent)
                        }
                    }
                }
            }

            if (items.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
                ) {
                    items(items) { item ->
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MintBorder),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${item.index}", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = EcoGreenPrimary)
                                Text(
                                    text = item.category?.iconEmoji ?: "❓",
                                    fontSize = 18.sp
                                )
                                Text(item.componentName, fontSize = 9.sp, color = TextPrimaryDark, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(
    language: Language,
    analysis: com.example.ai.AiComponentResult?,
    selectedCategory: MaterialCategory?,
    onPickCategory: (MaterialCategory) -> Unit,
    expectedCategory: MaterialCategory?,
    pendingWeight: String,
    onWeightChange: (String) -> Unit,
    iotWeightKg: Double?,
    onIotFakeReading: (Double) -> Unit,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit
) {
    val result = analysis
    if (result == null) return
    val category = result.category ?: selectedCategory

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = when (language) {
                Language.ENGLISH -> "Detected component:"
                Language.HINDI -> "पहचाना गया घटक:"
                Language.MARATHI -> "ओळखलेला घटक:"
                Language.TAMIL -> "கண்டறியப்பட்ட கூறு:"
            },
            color = TextSecondaryMuted,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = result.componentName.ifBlank {
                when (language) {
                    Language.ENGLISH -> "Unidentified item"
                    Language.HINDI -> "अनजान वस्तु"
                    Language.MARATHI -> "ओळख नसलेली वस्तू"
                    Language.TAMIL -> "அடையாளம் காணப்படாத பொருள்"
                }
            },
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
        Spacer(modifier = Modifier.width(8.dp))
        val sourceBadge = when (result.source) {
            CaptureAiSource.ONNX -> when (language) {
                Language.ENGLISH -> "On-device"
                Language.HINDI -> "ऑन-डिवाइस"
                Language.MARATHI -> "ऑन-डिव्हाइस"
                Language.TAMIL -> "சாதனத்தில்"
            }
            CaptureAiSource.GEMINI -> "AI"
            CaptureAiSource.MANUAL -> when (language) {
                Language.ENGLISH -> "New item"
                Language.HINDI -> "नई वस्तु"
                Language.MARATHI -> "नवीन वस्तू"
                Language.TAMIL -> "புதிய பொருள்"
            }
        }
        Card(
            shape = RoundedCornerShape(6.dp),
            colors = CardDefaults.cardColors(
                containerColor = when (result.source) {
                    CaptureAiSource.ONNX -> EcoGreenPrimary
                    CaptureAiSource.GEMINI -> EcoOrangeAccent
                    CaptureAiSource.MANUAL -> WarningAmber
                }
            )
        ) {
            Text(sourceBadge, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
        }
    }
    if (result.source == CaptureAiSource.ONNX && result.confidence > 0f) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = when (language) {
                Language.ENGLISH -> "On-device confidence ${(result.confidence * 100).toInt()}%"
                Language.HINDI -> "ऑन-डिवाइस विश्वास ${(result.confidence * 100).toInt()}%"
                Language.MARATHI -> "ऑन-डिव्हाइस आत्मविश्वास ${(result.confidence * 100).toInt()}%"
                Language.TAMIL -> "சாதன நம்பிக்கை ${(result.confidence * 100).toInt()}%"
            },
            color = EcoGreenBright,
            fontSize = 11.sp
        )
    }

    if (category != null) {
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${category.iconEmoji}  ${category.getTitle(language)}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            if (expectedCategory != null) {
                Spacer(modifier = Modifier.width(8.dp))
                val matches = category == expectedCategory
                Icon(
                    imageVector = if (matches) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (matches) SuccessGreen else WarningAmber,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (matches) {
                        when (language) {
                            Language.ENGLISH -> "Matches lot category"
                            Language.HINDI -> "लॉट श्रेणी से मेल खाता है"
                            Language.MARATHI -> "लॉट श्रेणीशी जुळते"
                            Language.TAMIL -> "லாட் வகையுடன் பொருந்துகிறது"
                        }
                    } else {
                        when (language) {
                            Language.ENGLISH -> "Differs from lot (${expectedCategory.getTitle(language)})"
                            Language.HINDI -> "लॉट से भिन्न (${expectedCategory.getTitle(language)})"
                            Language.MARATHI -> "लॉटपेक्षा वेगळे (${expectedCategory.getTitle(language)})"
                            Language.TAMIL -> "லாட் வேறுபடுகிறது (${expectedCategory.getTitle(language)})"
                        }
                    },
                    color = if (matches) SuccessGreen else WarningAmber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    if (result.isNewOrUnknown) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = when (language) {
                Language.ENGLISH -> "New/unknown item — pick the closest category:"
                Language.HINDI -> "नई/अनजान वस्तु — निकटतम श्रेणी चुनें:"
                Language.MARATHI -> "नवीन/अनोळखी वस्तू — जवळची श्रेणी निवडा:"
                Language.TAMIL -> "புதிய/தெரியாத பொருள் — நெருங்கிய வகையைத் தேர்ந்தெடுக்கவும்:"
            },
            color = WarningAmber,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(MaterialCategory.entries) { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { onPickCategory(cat) },
                    label = { Text("${cat.iconEmoji} ${cat.getTitle(language)}", fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EcoGreenPrimary,
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.testTag("aicapture_cat_${cat.name.lowercase()}")
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))
    if (category == null) return

    // Price (IndiaMART-sourced) shown for the selected category.
    val imEntry = IndiaMartRates.get(category)
    val weight = pendingWeight.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }
    val value = weight?.let { it * imEntry.ratePerKg } ?: 0.0
    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = EcoGreenDeep)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = when (language) {
                    Language.ENGLISH -> "Market rate (IndiaMART): ₹${imEntry.ratePerKg.toInt()}/kg"
                    Language.HINDI -> "बाज़ार भाव (IndiaMART): ₹${imEntry.ratePerKg.toInt()}/kg"
                    Language.MARATHI -> "बाजारभाव (IndiaMART): ₹${imEntry.ratePerKg.toInt()}/kg"
                    Language.TAMIL -> "சந்தை விலை (IndiaMART): ₹${imEntry.ratePerKg.toInt()}/kg"
                },
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color.White
            )
            Text(imEntry.sourceLabel, color = Color.White.copy(alpha = 0.8f), fontSize = 9.sp)
            Text(IndiaMartRates.DISCLAIMER, color = Color.White.copy(alpha = 0.6f), fontSize = 8.sp)
        }
    }

    Spacer(modifier = Modifier.height(10.dp))
    // IoT weight section — reads from an attached scale when available, manual entry otherwise.
    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.12f))) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = if (iotWeightKg != null) SuccessGreen else TextSecondaryMuted, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (iotWeightKg != null) {
                        when (language) {
                            Language.ENGLISH -> "IoT scale: ${iotWeightKg} kg (auto-filled)"
                            Language.HINDI -> "IoT तराजू: ${iotWeightKg} kg (स्वतः भरा)"
                            Language.MARATHI -> "IoT तराजू: ${iotWeightKg} kg (स्वयं भरले)"
                            Language.TAMIL -> "IoT எடை: ${iotWeightKg} kg (தானாக)"
                        }
                    } else {
                        when (language) {
                            Language.ENGLISH -> "IoT scale: not connected — enter weight manually"
                            Language.HINDI -> "IoT तराजू: कनेक्ट नहीं — वज़न हाथ से डालें"
                            Language.MARATHI -> "IoT तराजू: कनेक्ट नाही — वजन स्वतः टाका"
                            Language.TAMIL -> "IoT எடை: இணைக்கப்படவில்லை — எடையை கைமுறையாக உள்ளிடவும்"
                        }
                    },
                    color = Color.White,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                OutlinedButton(
                    onClick = { onIotFakeReading((1..30).random() / 1.0) },
                    modifier = Modifier.height(28.dp).testTag("aicapture_iot_sample")
                ) {
                    Text(
                        when (language) {
                            Language.ENGLISH -> "Simulate IoT"
                            Language.HINDI -> "IoT अनुकरण"
                            Language.MARATHI -> "IoT सिम्युलेट"
                            Language.TAMIL -> "IoT உருவகப்படுத்து"
                        },
                        fontSize = 9.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = pendingWeight,
                onValueChange = onWeightChange,
                label = { Text(when (language) {
                    Language.ENGLISH -> "Weight (kg)"
                    Language.HINDI -> "वज़न (किग्रा)"
                    Language.MARATHI -> "वजन (किलो)"
                    Language.TAMIL -> "எடை (கிலோ)"
                }, fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("aicapture_weight_field")
            )
            if (value > 0.0) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = when (language) {
                        Language.ENGLISH -> "Est. value: ₹${value.toInt()}  (${weight} kg × ₹${imEntry.ratePerKg.toInt()})"
                        Language.HINDI -> "अनुमानित मूल्य: ₹${value.toInt()}  (${weight} किग्रा × ₹${imEntry.ratePerKg.toInt()})"
                        Language.MARATHI -> "अंदाजित किंमत: ₹${value.toInt()}  (${weight} किलो × ₹${imEntry.ratePerKg.toInt()})"
                        Language.TAMIL -> "மதிப்பு: ₹${value.toInt()}  (${weight} கிலோ × ₹${imEntry.ratePerKg.toInt()})"
                    },
                    color = EcoGreenBright,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = onDiscard,
            modifier = Modifier.weight(1f).testTag("aicapture_retake")
        ) {
            Text(
                when (language) {
                    Language.ENGLISH -> "Retake"
                    Language.HINDI -> "फिर से लें"
                    Language.MARATHI -> "पुन्हा घ्या"
                    Language.TAMIL -> "மீண்டும் எடு"
                }, fontWeight = FontWeight.Bold, fontSize = 12.sp
            )
        }
        Button(
            onClick = onConfirm,
            colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary),
            modifier = Modifier.weight(1f).testTag("aicapture_confirm")
        ) {
            Text(
                when (language) {
                    Language.ENGLISH -> "Keep & add next"
                    Language.HINDI -> "रखें और अगला जोड़ें"
                    Language.MARATHI -> "ठेवा आणि पुढील जोडा"
                    Language.TAMIL -> "வைத்து அடுத்து சேர்"
                }, fontWeight = FontWeight.Bold, fontSize = 12.sp
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

private fun decodePickedImage(context: Context, uri: Uri): Bitmap {
    val bitmap = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it)
    } ?: throw IllegalStateException("empty uri")
    val maxDim = 1600
    val w = bitmap.width
    val h = bitmap.height
    if (w > maxDim || h > maxDim) {
        val ratio = Math.min(maxDim.toFloat() / w, maxDim.toFloat() / h)
        return Bitmap.createScaledBitmap(bitmap, (w * ratio).toInt(), (h * ratio).toInt(), true)
    }
    return bitmap
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
    val buffer: ByteBuffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    val matrix = Matrix()
    matrix.postRotate(image.imageInfo.rotationDegrees.toFloat())
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}