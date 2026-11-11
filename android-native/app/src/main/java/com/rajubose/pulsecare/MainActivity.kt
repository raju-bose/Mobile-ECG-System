package com.rajubose.pulsecare

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {
    private lateinit var db: HistoryDb
    private lateinit var previewView: PreviewView
    private lateinit var graph: PpgGraphView
    private lateinit var pulseText: TextView
    private lateinit var statusText: TextView
    private lateinit var historyText: TextView
    private lateinit var systolicInput: EditText
    private lateinit var diastolicInput: EditText
    private lateinit var cameraButton: Button
    private var camera: Camera? = null
    private var currentPulse: Int? = null
    private var cameraRunning = false
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val samples = ArrayDeque<Pair<Long, Float>>()
    private var lastPeakAt = 0L
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else statusText.text = "Camera permission is required for fingertip pulse scanning."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = HistoryDb(this)
        buildUi()
        refreshHistory()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(243, 247, 249))
            setPadding(dp(16), dp(14), dp(16), dp(18))
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        content.addView(label("PulseCare", 26, Color.rgb(8, 100, 103), true))
        content.addView(label("Camera pulse monitor • Vivo Y100", 13, Color.DKGRAY))
        content.addView(label("Pulse measurement is an estimate, not an ECG or medical diagnosis.", 12, Color.rgb(150, 75, 20)))

        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            setBackgroundColor(Color.BLACK)
        }
        content.addView(previewView, LinearLayout.LayoutParams(-1, dp(190)).apply { topMargin = dp(12) })
        pulseText = label("-- bpm", 34, Color.rgb(8, 100, 103), true).apply { gravity = Gravity.CENTER }
        content.addView(pulseText)
        statusText = label("Place your fingertip gently over the rear camera and flash.", 13, Color.DKGRAY)
        content.addView(statusText)
        graph = PpgGraphView(this).apply { setBackgroundColor(Color.rgb(13, 35, 46)) }
        content.addView(graph, LinearLayout.LayoutParams(-1, dp(130)).apply { topMargin = dp(8) })

        cameraButton = Button(this).apply {
            text = "Start camera pulse scan"
            setOnClickListener { if (cameraRunning) stopCamera() else requestCamera() }
        }
        content.addView(cameraButton)
        val savePulse = Button(this).apply {
            text = "Save pulse reading"
            setOnClickListener {
                val pulse = currentPulse
                if (pulse == null) statusText.text = "No stable pulse detected yet. Scan first."
                else { db.add(pulse, null, null, "Camera PPG estimate"); statusText.text = "Pulse reading saved locally."; refreshHistory() }
            }
        }
        content.addView(savePulse)

        content.addView(label("Blood pressure (manual cuff)", 18, Color.BLACK, true).apply { setPadding(0, dp(18), 0, dp(4)) })
        content.addView(label("Enter the values shown on your upper-arm BP monitor. PulseCare does not measure BP with the camera.", 12, Color.DKGRAY))
        val bpRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        systolicInput = EditText(this).apply { hint = "Systolic"; inputType = 2 }
        diastolicInput = EditText(this).apply { hint = "Diastolic"; inputType = 2 }
        bpRow.addView(systolicInput, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(6) })
        bpRow.addView(diastolicInput, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginStart = dp(6) })
        content.addView(bpRow)
        content.addView(Button(this).apply {
            text = "Save cuff BP reading"
            setOnClickListener { saveBp() }
        })

        content.addView(label("SpO₂", 18, Color.BLACK, true).apply { setPadding(0, dp(18), 0, dp(4)) })
        content.addView(label("Unavailable on this phone-camera setup. A validated pulse oximeter is required for reliable oxygen saturation.", 12, Color.DKGRAY))

        content.addView(label("Camera BP research (experimental)", 18, Color.rgb(150, 75, 20), true).apply { setPadding(0, dp(18), 0, dp(4)) })
        content.addView(label("The app records PPG signal samples for research only. It does not calculate or display a camera-derived BP value. Use a validated cuff for all BP measurements.", 12, Color.DKGRAY))
        content.addView(Button(this).apply {
            text = "Save research session note"
            setOnClickListener {
                db.add(currentPulse, null, null, "Experimental camera PPG session; not a BP measurement")
                statusText.text = "Research session note saved. No BP was inferred."
                refreshHistory()
            }
        })

        content.addView(label("Saved measurement history", 18, Color.BLACK, true).apply { setPadding(0, dp(18), 0, dp(4)) })
        historyText = label("No readings saved yet.", 12, Color.DKGRAY)
        content.addView(historyText)
        content.addView(Button(this).apply { text = "Refresh history"; setOnClickListener { refreshHistory() } })
        setContentView(root)
    }

    private fun requestCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else permissionRequest.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(cameraExecutor) { image ->
                    try {
                        val w = image.width
                        val h = image.height
                        val plane = image.planes[0]
                        val buffer = plane.buffer
                        val rowStride = plane.rowStride
                        val pixelStride = plane.pixelStride
                        var sum = 0.0
                        var count = 0
                        val left = w / 3
                        val right = (w * 2) / 3
                        val top = h / 3
                        val bottom = (h * 2) / 3
                        var y = top
                        while (y < bottom) {
                            var x = left
                            while (x < right) {
                                val index = y * rowStride + x * pixelStride
                                if (index < buffer.limit()) { sum += (buffer.get(index).toInt() and 0xFF); count++ }
                                x += 5
                            }
                            y += 5
                        }
                        if (count > 0) processSample(System.currentTimeMillis(), (sum / count).toFloat())
                    } catch (_: Exception) {
                    } finally { image.close() }
                }
                provider.unbindAll()
                camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                if (camera?.cameraInfo?.hasFlashUnit() == true) camera?.cameraControl?.enableTorch(true)
                cameraRunning = true
                cameraButton.text = "Stop camera pulse scan"
                statusText.text = "Scanning. Keep your fingertip still and avoid pressing hard."
            } catch (e: Exception) {
                statusText.text = "Camera could not start: ${e.localizedMessage ?: "device error"}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @Synchronized private fun processSample(time: Long, raw: Float) {
        samples.addLast(time to raw)
        while (samples.isNotEmpty() && time - samples.first().first > 12000) samples.removeFirst()
        graph.push(raw)
        if (samples.size < 40) return
        val values = samples.map { it.second }
        val mean = values.average().toFloat()
        val variance = values.map { (it - mean) * (it - mean) }.average().toFloat()
        val threshold = sqrt(variance) * 0.28f
        val smooth = values.mapIndexed { i, v ->
            val from = (i - 2).coerceAtLeast(0)
            val to = (i + 2).coerceAtMost(values.lastIndex)
            values.subList(from, to + 1).average().toFloat()
        }
        val now = time
        var peaks = 0
        var previous = 0L
        for (i in 2 until smooth.lastIndex) {
            if (smooth[i] > smooth[i - 1] && smooth[i] >= smooth[i + 1] && abs(smooth[i] - mean) > threshold) {
                val peakTime = samples.elementAt(i).first
                if (peakTime - previous >= 350 && peakTime - previous <= 1800) {
                    peaks++
                    previous = peakTime
                } else if (previous == 0L) previous = peakTime
            }
        }
        val duration = (samples.last().first - samples.first().first).coerceAtLeast(1L)
        val bpm = if (peaks >= 2) ((peaks - 1) * 60000f / duration).toInt() else 0
        if (bpm in 40..180) {
            currentPulse = bpm
            runOnUiThread {
                pulseText.text = "$bpm bpm"
                statusText.text = "Pulse estimate from camera signal. Stay still for a steadier result."
            }
        }
    }

    private fun stopCamera() {
        camera?.cameraControl?.enableTorch(false)
        camera = null
        ProcessCameraProvider.getInstance(this).get().unbindAll()
        cameraRunning = false
        cameraButton.text = "Start camera pulse scan"
        statusText.text = "Camera scan stopped."
    }

    private fun saveBp() {
        val sys = systolicInput.text.toString().toIntOrNull()
        val dia = diastolicInput.text.toString().toIntOrNull()
        if (sys == null || dia == null || sys !in 60..260 || dia !in 30..160 || sys <= dia) {
            statusText.text = "Enter valid cuff values (systolic higher than diastolic)."
            return
        }
        db.add(null, sys, dia, "Manually entered from BP cuff")
        systolicInput.text.clear()
        diastolicInput.text.clear()
        statusText.text = "BP cuff reading saved locally."
        refreshHistory()
    }

    private fun refreshHistory() {
        val rows = db.latest()
        historyText.text = if (rows.isEmpty()) "No readings saved yet." else rows.joinToString("\n\n") { r ->
            val parts = mutableListOf<String>()
            if (r.pulse != null) parts += "Pulse ${r.pulse} bpm (camera estimate)"
            if (r.systolic != null) parts += "BP ${r.systolic}/${r.diastolic} mmHg (manual cuff)"
            "${r.time}\n${parts.joinToString(" • ")}\n${r.note}"
        }
    }

    private fun label(text: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = text
        textSize = size.toFloat()
        setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(5), 0, dp(5))
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        try { camera?.cameraControl?.enableTorch(false) } catch (_: Exception) {}
        cameraExecutor.shutdown()
        db.close()
        super.onDestroy()
    }
}
