package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import com.example.model.Language
import com.example.model.MaterialCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Which engine produced an AI component result for one captured photo. */
enum class CaptureAiSource { ONNX, GEMINI, MANUAL }

/**
 * Result of identifying the component(s) in a single e-waste photo.
 * [source] ONNX = offline model confident; GEMINI = fallback cloud naming (covers
 * laptops and items outside the ONNX classes); MANUAL = nothing AI could name, so
 * [isNewOrUnknown] is true and the user picks a category themselves.
 */
data class AiComponentResult(
    val componentName: String,
    val category: MaterialCategory?,
    val confidence: Float,
    val source: CaptureAiSource,
    val isNewOrUnknown: Boolean,
    val onnxDetection: ObbDetection?
)

/**
 * Two-stage photo AI for the camera-first capture flow (used by both the informal
 * collector and the formal recycler):
 *
 *  1. ONNX-LiteRT YOLOv8n-OBB on-device model (always available, offline). A
 *     confident detection (conf >= [ONNX_CONFIDENCE_THRESHOLD]) wins immediately.
 *  2. If ONNX is uncertain or finds nothing, Gemini 2.5 Flash names the item
 *     (this is what recognises "Laptop", "Router", monitors, etc.).
 *  3. If both fail or the item is genuinely unknown, return an "Other / new item"
 *     result so the UI can ask the user to pick a category.
 */
class AiCaptureAnalyzer(context: Context) : AutoCloseable {

    private val detector = EwasteObbDetector(context.applicationContext)

    suspend fun analyze(bitmap: Bitmap, language: Language): AiComponentResult {
        // Pass 1 — offline ONNX.
        val onnx = withContext(Dispatchers.Default) {
            kotlin.runCatching { detector.detect(bitmap) }.getOrElse { emptyList() }
        }
        val best = onnx.firstOrNull { it.className != "cd" && it.className != "w" }
        if (best != null && best.confidence >= ONNX_CONFIDENCE_THRESHOLD) {
            return AiComponentResult(
                componentName = best.className.replaceFirstChar { it.uppercase() },
                category = best.category,
                confidence = best.confidence,
                source = CaptureAiSource.ONNX,
                isNewOrUnknown = false,
                onnxDetection = best
            )
        }

        // Pass 2 — Gemini cloud naming (also identifies laptops / unseen devices).
        val gemini = GeminiScannerClient.analyzeEwasteImage(bitmap, language).getOrNull()
        if (gemini != null) {
            val itemName = gemini.itemName.trim()
            val lowered = itemName.lowercase()

            // Laptops / notebooks do not sit in the ONNX classes; map explicitly to
            // the circuit-board category so pricing and disposal flags are sensible.
            if (lowered.contains("laptop") || lowered.contains("notebook") || lowered.contains("macbook")) {
                return AiComponentResult(
                    componentName = if (itemName.isBlank()) "Laptop" else itemName,
                    category = gemini.identifiedCategory
                        .takeIf { it == MaterialCategory.PCB_BOARDS || it == MaterialCategory.LCD_PANELS }
                        ?: MaterialCategory.PCB_BOARDS,
                    confidence = GEMINI_CONFIDENCE,
                    source = CaptureAiSource.GEMINI,
                    isNewOrUnknown = false,
                    onnxDetection = best?.takeIf { it.confidence >= 0.2f }
                )
            }

            // Vague AI names ("metallic component assembly", "scrap material", …)
            // would only produce a wrong category and rate — ask the user instead.
            val isGeneric = GENERIC_GEMINI_NAMES.any { lowered.startsWith(it) || lowered.contains(" $it ") || lowered.contains(it) && itemName.length <= 42 }
            if (!isGeneric) {
                return AiComponentResult(
                    componentName = itemName,
                    category = gemini.identifiedCategory,
                    confidence = GEMINI_CONFIDENCE,
                    source = CaptureAiSource.GEMINI,
                    isNewOrUnknown = false,
                    onnxDetection = best?.takeIf { it.confidence >= 0.2f }
                )
            }
            // fall through → user picks the right category for this unclear item
        }

        // Pass 3 — unknown / new item; ask the user for the category.
        return AiComponentResult(
            componentName = "",
            category = null,
            confidence = 0f,
            source = CaptureAiSource.MANUAL,
            isNewOrUnknown = true,
            onnxDetection = best
        )
    }

    override fun close() {
        detector.close()
    }

    companion object {
        /** ONNX detections at/above this confidence are trusted without Gemini. */
        const val ONNX_CONFIDENCE_THRESHOLD = 0.35f
        /** Displayed confidence when Gemini named the item. */
        const val GEMINI_CONFIDENCE = 0.85f

        /** Gemini outputs that describe the photo rather than a specific e-waste item. */
        private val GENERIC_GEMINI_NAMES = listOf(
            "metallic component", "metallic", "component assembly", "module assembly",
            "electronic waste", "e-waste", "scrap material", "metal scrap", "mixed metal",
            "electronic device", "electrical device", "component", "components",
            "device", "machine", "machinery", "parts", "piece", "pieces", "items",
            "material", "materials", "stuff", "object", "objects", "appliance",
            "electrical item", "used item", "gadget", "hardware", "circuit", "board piece"
        )
    }
}