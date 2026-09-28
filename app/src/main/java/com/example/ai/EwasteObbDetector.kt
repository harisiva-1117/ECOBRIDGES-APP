package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import com.example.model.MaterialCategory
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtLoggingLevel
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * On-device YOLOv8n-OBB e-waste detector running through ONNX Runtime.
 *
 * The exported ONNX graph already bakes in DFL + anchor decoding, so each of the
 * 8400 output columns is [cx, cy, w, h, cls_0..cls_11, angle] in 640-space pixels
 * where angle is the rotated-box angle in radians. Only NMS and letterbox mapping
 * are done here in Kotlin.
 */
data class ObbDetection(
    val classIndex: Int,
    val className: String,
    val confidence: Float,
    val category: MaterialCategory,
    val angle: Float,
    val cx: Float,
    val cy: Float,
    val w: Float,
    val h: Float
) {
    val corners: List<FloatArray>
        get() {
            val c = kotlin.math.cos(angle.toDouble())
            val s = kotlin.math.sin(angle.toDouble())
            val hw = w / 2f
            val hh = h / 2f
            return listOf(
                floatArrayOf((cx + hw * c.toFloat() - hh * s.toFloat()), (cy + hw * s.toFloat() + hh * c.toFloat())),
                floatArrayOf((cx - hw * c.toFloat() - hh * s.toFloat()), (cy - hw * s.toFloat() + hh * c.toFloat())),
                floatArrayOf((cx - hw * c.toFloat() + hh * s.toFloat()), (cy - hw * s.toFloat() - hh * c.toFloat())),
                floatArrayOf((cx + hw * c.toFloat() + hh * s.toFloat()), (cy + hw * s.toFloat() - hh * c.toFloat()))
            )
        }

    fun cornerX(i: Int): Float = corners[i][0]
    fun cornerY(i: Int): Float = corners[i][1]
}

class EwasteObbDetector(context: Context) : AutoCloseable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    /** data.yaml class order. 'cd' and 'w' have almost no training samples. */
    private val labels = listOf(
        "battery", "cd", "keyboard", "microwave", "mobile", "mouse",
        "pcb", "printer", "speaker", "television", "w", "washing machine"
    )

    private val modelFile: File = File(
        context.cacheDir, "e_waste_obb.onnx"
    )

    init {
        if (!modelFile.exists()) {
            context.assets.open("ml/e_waste_obb.onnx").use { input ->
                modelFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        val options = OrtSession.SessionOptions()
        options.setIntraOpNumThreads(2)
        options.setSessionLogLevel(OrtLoggingLevel.ORT_LOGGING_LEVEL_ERROR)
        session = env.createSession(modelFile.absolutePath, options)
    }

    fun detect(mutableBitmap: Bitmap, confThreshold: Float = 0.25f, nmsThreshold: Float = 0.5f): List<ObbDetection> {
        val (letterboxed, scale, dw, dh) = letterbox(mutableBitmap)
        val input = toModelInput(letterboxed, scale, dw, dh)
        val floatBuffer = input.asFloatBuffer()
        OnnxTensor.createTensor(env, floatBuffer, longArrayOf(1L, 3L, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())).use { tensor ->
            session.run(mapOf(inputName to tensor)).use { result ->
                val out = (result.get(outputName).get() as OnnxTensor).getFloatBuffer()
                return decode(out, scale, dw, dh, confThreshold, nmsThreshold)
            }
        }
    }

    private fun decode(out: FloatBuffer, scale: Float, dw: Float, dh: Float, confThres: Float, nmsThres: Float): List<ObbDetection> {
        val n = 8400
        val rows = 17
        val nc = labels.size
        val stride = n
        val candidates = ArrayList<ObbDetection>(64)
        for (c in 0 until n) {
            val cx = out[0 * stride + c]
            val cy = out[1 * stride + c]
            val w = out[2 * stride + c]
            val h = out[3 * stride + c]
            if (w <= 0f || h <= 0f) continue
            var best = 0f
            var bestIdx = 0
            for (r in 0 until nc) {
                val s = out[(4 + r) * stride + c]
                if (s > best) {
                    best = s
                    bestIdx = r
                }
            }
            if (best < confThres) continue
            val angle = out[16 * stride + c]
            candidates.add(
                ObbDetection(
                    classIndex = bestIdx,
                    className = labels[bestIdx],
                    confidence = best,
                    category = categoryOf(bestIdx),
                    angle = angle,
                    cx = (cx - dw) / scale,
                    cy = (cy - dh) / scale,
                    w = w / scale,
                    h = h / scale
                )
            )
        }
        candidates.sortByDescending { it.confidence }
        val keep = ArrayList<ObbDetection>(16)
        val used = BooleanArray(candidates.size)
        for (i in candidates.indices) {
            if (used[i]) continue
            val a = candidates[i]
            keep.add(a)
            for (j in i + 1 until candidates.size) {
                if (used[j]) continue
                if (polygonIou(a.corners, candidates[j].corners) > nmsThres) used[j] = true
            }
        }
        return keep
    }

    private fun categoryOf(classIndex: Int): MaterialCategory = when (labels.getOrNull(classIndex)) {
        "battery" -> MaterialCategory.BATTERIES
        "cd" -> MaterialCategory.MIXED_PLASTICS
        "keyboard" -> MaterialCategory.PCB_BOARDS
        "microwave" -> MaterialCategory.PCB_BOARDS
        "mobile" -> MaterialCategory.PCB_BOARDS
        "mouse" -> MaterialCategory.PCB_BOARDS
        "pcb" -> MaterialCategory.PCB_BOARDS
        "printer" -> MaterialCategory.PCB_BOARDS
        "speaker" -> MaterialCategory.PCB_BOARDS
        "television" -> MaterialCategory.LCD_PANELS
        "w" -> MaterialCategory.MIXED_PLASTICS
        "washing machine" -> MaterialCategory.MOTORS_MAGNETS
        else -> MaterialCategory.MIXED_PLASTICS
    }

    private data class LetterboxResult(val bitmap: Bitmap, val scale: Float, val dw: Float, val dh: Float)

    private fun letterbox(src: Bitmap): LetterboxResult {
        val hw = src.width
        val hh = src.height
        if (hw == INPUT_SIZE && hh == INPUT_SIZE && src.config == Bitmap.Config.ARGB_8888) {
            return LetterboxResult(src.copy(Bitmap.Config.ARGB_8888, false), 1f, 0f, 0f)
        }
        val r = min(INPUT_SIZE.toFloat() / hw, INPUT_SIZE.toFloat() / hh)
        val nw = (hw * r).roundToInt()
        val nh = (hh * r).roundToInt()
        val dw = (INPUT_SIZE - nw) / 2f
        val dh = (INPUT_SIZE - nh) / 2f
        val resized = Bitmap.createScaledBitmap(src, nw, nh, true)
        val canvas = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
        canvas.eraseColor(0xFF808080.toInt())
        android.graphics.Canvas(canvas).drawBitmap(resized, dw, dh, null)
        resized.recycle()
        return LetterboxResult(canvas, r, dw, dh)
    }

    private fun toModelInput(bitmap: Bitmap, scale: Float, dw: Float, dh: Float): ByteBuffer {
        val size = INPUT_SIZE * INPUT_SIZE
        val pixels = IntArray(size)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        val buf = ByteBuffer.allocateDirect(3 * size * 4)
        buf.order(ByteOrder.nativeOrder())
        val rgb = FloatBuffer.allocate(3 * size)
        for (i in 0 until size) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            rgb.put(r)
            rgb.put(g)
            rgb.put(b)
        }
        rgb.flip()
        // interleave to CHW
        val rArr = FloatArray(size)
        val gArr = FloatArray(size)
        val bArr = FloatArray(size)
        for (i in 0 until size) {
            rArr[i] = rgb.get()
            gArr[i] = rgb.get()
            bArr[i] = rgb.get()
        }
        for (i in 0 until size) buf.putFloat(rArr[i])
        for (i in 0 until size) buf.putFloat(gArr[i])
        for (i in 0 until size) buf.putFloat(bArr[i])
        return buf
    }

    private fun polygonIou(a: List<FloatArray>, b: List<FloatArray>): Float {
        var clip = a
        for (i in b.indices) {
            if (clip.size < 3) return 0f
            val j = (i + 1) % b.size
            clip = clipByEdge(clip, b[i], b[j])
        }
        val inter = polygonArea(clip)
        val union = polygonArea(a) + polygonArea(b) - inter
        return if (union <= 1e-6f) 0f else inter / union
    }

    private fun clipByEdge(poly: List<FloatArray>, e1: FloatArray, e2: FloatArray): List<FloatArray> {
        val out = ArrayList<FloatArray>(poly.size + 1)
        for (k in poly.indices) {
            val cur = poly[k]
            val prev = poly[(k + poly.size - 1) % poly.size]
            val curIn = inside(cur, e1, e2)
            val prevIn = inside(prev, e1, e2)
            if (curIn) {
                if (!prevIn) out.add(intersection(prev, cur, e1, e2))
                out.add(cur)
            } else if (prevIn) {
                out.add(intersection(prev, cur, e1, e2))
            }
        }
        return out
    }

    private fun inside(p: FloatArray, e1: FloatArray, e2: FloatArray): Boolean {
        return (e2[0] - e1[0]) * (p[1] - e1[1]) - (e2[1] - e1[1]) * (p[0] - e1[0]) >= 0f
    }

    private fun intersection(p1: FloatArray, p2: FloatArray, e1: FloatArray, e2: FloatArray): FloatArray {
        val d = (p1[0] - p2[0]) * (e2[1] - e1[1]) - (p1[1] - p2[1]) * (e2[0] - e1[0])
        if (kotlin.math.abs(d) < 1e-9f) return p2
        val t = ((e1[0] - p2[0]) * (e2[1] - e1[1]) - (e1[1] - p2[1]) * (e2[0] - e1[0])) / d
        return floatArrayOf(p2[0] + t * (p1[0] - p2[0]), p2[1] + t * (p1[1] - p2[1]))
    }

    private fun polygonArea(poly: List<FloatArray>): Float {
        if (poly.size < 3) return 0f
        var area = 0f
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[(i + 1) % poly.size]
            area += a[0] * b[1] - b[0] * a[1]
        }
        return kotlin.math.abs(area) / 2f
    }

    override fun close() {
        session.close()
    }

    companion object {
        const val INPUT_SIZE = 640
        private const val inputName = "images"
        private const val outputName = "output0"
    }
}