package com.example.ui.recycler

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import com.example.ai.EwasteObbDetector
import com.example.ai.ObbDetection
import com.example.model.Language
import com.example.model.MaterialCategory
import com.example.ui.theme.EcoGreenPrimary
import com.example.ui.theme.EcoGreenBright
import com.example.ui.theme.EcoOrangeAccent
import com.example.ui.theme.MintBorder
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryMuted
import com.example.ui.theme.WarningAmber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecyclerAiDetectScreen(
    language: Language,
    expectedCategory: MaterialCategory?,
    onBack: () -> Unit,
    onResult: (ObbDetection?) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val detector = remember { EwasteObbDetector(context.applicationContext) }
    val imageCapture = remember { mutableStateOf<ImageCapture?>(null) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) Toast.makeText(context, "Camera permission required for AI scan", Toast.LENGTH_SHORT).show()
    }

    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var analyzing by remember { mutableStateOf(false) }
    var detection by remember { mutableStateOf<ObbDetection?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            detector.close()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = when (language) {
                            Language.ENGLISH -> "AI Item Scan"
                            Language.HINDI -> "AI वस्तु स्कैन"
                            Language.MARATHI -> "AI वस्तू स्कॅन"
                            Language.TAMIL -> "AI பொருள் ஸ்கேன்"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = TextPrimaryDark
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("aiscan_nav_back")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            if (!hasCameraPermission) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = when (language) {
                            Language.ENGLISH -> "Camera permission is needed to scan e-waste items"
                            Language.HINDI -> "ई-कचरा स्कैन करने के लिए कैमरा अनुमति आवश्यक है"
                            Language.MARATHI -> "ई-कचरा स्कॅन करण्यासाठी कॅमेरा परवानगी आवश्यक आहे"
                            Language.TAMIL -> "மின்-கழிவு பொருட்களை ஸ்கேன் செய்ய கேமரா அனுமதி தேவை"
                        },
                        color = Color.White,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary)
                    ) {
                        Text(when (language) {
                            Language.ENGLISH -> "Grant camera access"
                            Language.HINDI -> "कैमरा अनुमति दें"
                            Language.MARATHI -> "कॅमेरा परवानगी द्या"
                            Language.TAMIL -> "கேமரா அனுமதி வழங்கவும்"
                        })
                    }
                }
            } else {
                val bitmap = capturedBitmap
                if (bitmap == null && detection == null) {
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }
                                val capture = ImageCapture.Builder()
                                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                    .build()
                                imageCapture.value = capture
                                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                                try {
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        cameraSelector,
                                        preview,
                                        capture
                                    )
                                } catch (exc: Exception) {
                                    // Camera binding failed; leave shutter disabled.
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("aiscan_preview_view")
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 40.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        IconButton(
                            onClick = {
                                val capture = imageCapture.value ?: return@IconButton
                                analyzing = true
                                capture.takePicture(
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageCapturedCallback() {
                                        override fun onCaptureSuccess(image: ImageProxy) {
                                            val bmp = imageProxyToBitmapForDetection(image)
                                            image.close()
                                            capturedBitmap = bmp
                                            scope.launch {
                                                val dets = withContext(Dispatchers.Default) {
                                                    kotlin.runCatching { detector.detect(bmp) }.getOrElse { emptyList() }
                                                }
                                                detection = dets.firstOrNull()
                                                analyzing = false
                                            }
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            analyzing = false
                                            Toast.makeText(context, "Capture failed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            },
                            enabled = !analyzing,
                            colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(containerColor = EcoGreenPrimary),
                            modifier = Modifier
                                .size(76.dp)
                                .border(4.dp, Color.White, CircleShape)
                                .testTag("aiscan_shutter")
                        ) {
                            if (analyzing) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(34.dp))
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Capture",
                                    tint = Color.White,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                    }
                } else {
                    val showImage = capturedBitmap
                    if (showImage != null) {
                        Image(
                            bitmap = showImage.asImageBitmap(),
                            contentDescription = "Captured item",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(Color.Black.copy(alpha = 0.78f))
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        val det = detection
                        when {
                            det == null && analyzing -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(color = EcoGreenBright, modifier = Modifier.size(22.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = when (language) {
                                            Language.ENGLISH -> "Analyzing item with AI model..."
                                            Language.HINDI -> "एआई मॉडल से वस्तु का विश्लेषण..."
                                            Language.MARATHI -> "AI मॉडलद्वारे वस्तूचे विश्लेषण..."
                                            Language.TAMIL -> "AI மாதிரியுடன் பொருளை பகுப்பாய்வு செய்கிறது..."
                                        },
                                        color = Color.White,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                            det == null -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = WarningAmber)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = when (language) {
                                            Language.ENGLISH -> "No e-waste item detected. Try again with better lighting."
                                            Language.HINDI -> "कोई ई-कचरा वस्तु नहीं मिली। बेहतर रोशनी में पुनः प्रयास करें।"
                                            Language.MARATHI -> "ई-कचरा वस्तू आढळली नाही. चांगल्या प्रकाशात पुन्हा प्रयत्न करा."
                                            Language.TAMIL -> "மின்-கழிவு பொருள் கண்டறியப்படவில்லை. நல்ல வெளிச்சத்தில் மீண்டும் முயற்சிக்கவும்."
                                        },
                                        color = Color.White,
                                        fontSize = 13.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = {
                                        detection = null
                                        capturedBitmap = null
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("aiscan_retake")
                                ) {
                                    Text(when (language) {
                                        Language.ENGLISH -> "Scan again"
                                        Language.HINDI -> "फिर से स्कैन करें"
                                        Language.MARATHI -> "पुन्हा स्कॅन करा"
                                        Language.TAMIL -> "மீண்டும் ஸ்கேன்"
                                    })
                                }
                            }
                            else -> {
                                val isMatch = expectedCategory == null || det.category == expectedCategory
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = when (language) {
                                            Language.ENGLISH -> "Detected:"
                                            Language.HINDI -> "पहचाना गया:"
                                            Language.MARATHI -> "ओळखले:"
                                            Language.TAMIL -> "கண்டறியப்பட்டது:"
                                        },
                                        color = TextSecondaryMuted,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(det.className, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${(det.confidence * 100).toInt()}%",
                                        color = EcoGreenBright,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "${when (language) {
                                        Language.ENGLISH -> "Category"
                                        Language.HINDI -> "श्रेणी"
                                        Language.MARATHI -> "श्रेणी"
                                        Language.TAMIL -> "வகை"
                                    }}: ${det.category.getTitle(language)}",
                                    color = Color.White,
                                    fontSize = 13.sp
                                )
                                if (expectedCategory != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Card(
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isMatch) SuccessGreen else WarningAmber
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (isMatch) Icons.Default.CheckCircle else Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (isMatch) {
                                                    when (language) {
                                                        Language.ENGLISH -> "Matches declared category"
                                                        Language.HINDI -> "घोषित श्रेणी से मेल खाता है"
                                                        Language.MARATHI -> "घोषित श्रेणीशी जुळते"
                                                        Language.TAMIL -> "அறிவிக்கப்பட்ட வகையுடன் பொருந்துகிறது"
                                                    }
                                                } else {
                                                    when (language) {
                                                        Language.ENGLISH -> "Mismatch — lot declares ${expectedCategory.getTitle(language)}"
                                                        Language.HINDI -> "बेमेल — लॉट में ${expectedCategory.getTitle(language)} घोषित है"
                                                        Language.MARATHI -> "जुळत नाही — लॉटमध्ये ${expectedCategory.getTitle(language)} घोषित आहे"
                                                        Language.TAMIL -> "பொருந்தவில்லை — லாட் ${expectedCategory.getTitle(language)} என அறிவிக்கிறது"
                                                    }
                                                },
                                                color = Color.White,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            detection = null
                                            capturedBitmap = null
                                        },
                                        modifier = Modifier.weight(1f).testTag("aiscan_retake")
                                    ) {
                                        Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(when (language) {
                                            Language.ENGLISH -> "Retake"
                                            Language.HINDI -> "फिर से लें"
                                            Language.MARATHI -> "पुन्हा घ्या"
                                            Language.TAMIL -> "மீண்டும் எடு"
                                        })
                                    }
                                    Button(
                                        onClick = { onResult(det) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isMatch) EcoGreenPrimary else EcoOrangeAccent
                                        ),
                                        modifier = Modifier.weight(1f).testTag("aiscan_use_result")
                                    ) {
                                        Text(if (isMatch) {
                                            when (language) {
                                                Language.ENGLISH -> "Use result"
                                                Language.HINDI -> "परिणाम उपयोग करें"
                                                Language.MARATHI -> "निकाल वापरा"
                                                Language.TAMIL -> "முடிவை பயன்படுத்து"
                                            }
                                        } else {
                                            when (language) {
                                                Language.ENGLISH -> "Confirm anyway"
                                                Language.HINDI -> "फिर भी पुष्टि करें"
                                                Language.MARATHI -> "तरीही पुष्टी करा"
                                                Language.TAMIL -> "இருந்தாலும் உறுதிப்படுத்து"
                                            }
                                        }, fontWeight = FontWeight.Bold)
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

private fun imageProxyToBitmapForDetection(image: ImageProxy): Bitmap {
    val buffer: ByteBuffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    val matrix = Matrix()
    matrix.postRotate(image.imageInfo.rotationDegrees.toFloat())
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}