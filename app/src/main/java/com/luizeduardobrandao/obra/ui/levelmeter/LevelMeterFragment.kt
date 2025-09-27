package com.luizeduardobrandao.obra.ui.levelmeter

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.databinding.FragmentLevelMeterBinding
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.graphics.createBitmap
import kotlin.math.abs
import kotlin.math.atan2

class LevelMeterFragment : Fragment(), SensorEventListener {

    private var _binding: FragmentLevelMeterBinding? = null
    private val binding get() = _binding!!

    private lateinit var sensorManager: SensorManager
    private var hasRotationVector = false
    private var hasAccel = false
    private var hasMagnet = false

    // --- GRAVITY preferencial (fallback: ACCEL + low-pass) ---
    private var hasGravity = false
    private val gravity = FloatArray(3) // [gx, gy, gz] no referencial da tela
    private val accel = FloatArray(3)
    private val alpha = 0.12f // low-pass para estabilizar quando usar ACCEL

    // ==== Runtime permissions ====
    private val requestCameraPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera()
        else Toast.makeText(
            requireContext(),
            getString(R.string.level_camera_perm_denied),
            Toast.LENGTH_SHORT
        ).show()
    }

    private val requestLegacyWritePerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) takeAndSaveScreenshot()
        else Toast.makeText(
            requireContext(),
            getString(R.string.level_screenshot_fail),
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLevelMeterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        sensorManager = requireContext().getSystemService(SensorManager::class.java)

        // Botão lock/unlock
        btnLock.setOnClickListener {
            overlay.isLocked = !overlay.isLocked
            btnLock.setImageResource(if (overlay.isLocked) R.drawable.ic_lock else R.drawable.ic_lock_open)
            val msg = if (overlay.isLocked) R.string.level_locked else R.string.level_unlocked
            Toast.makeText(requireContext(), getString(msg), Toast.LENGTH_SHORT).show()
        }

        binding.previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE

        // Botão screenshot -> salva em Pictures/LevelMeter
        btnScreenshot.setOnClickListener {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                if (ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    takeAndSaveScreenshot()
                } else {
                    requestLegacyWritePerm.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            } else {
                takeAndSaveScreenshot()
            }
        }

        // Botão voltar -> volta para o HomeFragment
        binding.btnBack.setOnClickListener {
            // Se o HomeFragment está imediatamente abaixo na back stack, navigateUp() já resolve:
            findNavController().navigateUp()
        }
    }

    // ==== Lifecycle ====
    override fun onResume() {
        super.onResume()
        ensureCameraPermissionThenStart()
        registerSensors()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    // ==== CameraX ====
    private fun ensureCameraPermissionThenStart() {
        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) startCamera() else requestCameraPerm.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(binding.previewView.surfaceProvider)

            val selector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(viewLifecycleOwner, selector, preview)
            } catch (e: Exception) {
                // silencioso
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    // ==== Sensores ====
    private fun registerSensors() {
        hasGravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY) != null
        hasRotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null
        hasAccel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
        hasMagnet = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null

        if (hasGravity) {
            sensorManager.registerListener(
                this,
                sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY),
                SensorManager.SENSOR_DELAY_UI
            )
        } else {
            // fallback: vamos filtrar o ACCEL como “gravidade”
            if (hasAccel) {
                sensorManager.registerListener(
                    this,
                    sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                    SensorManager.SENSOR_DELAY_UI
                )
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GRAVITY -> {
                gravity[0] = event.values[0]
                gravity[1] = event.values[1]
                gravity[2] = event.values[2]
            }

            Sensor.TYPE_ACCELEROMETER -> {
                // low-pass para estimar gravidade quando não há TYPE_GRAVITY
                accel[0] = alpha * event.values[0] + (1 - alpha) * accel[0]
                accel[1] = alpha * event.values[1] + (1 - alpha) * accel[1]
                accel[2] = alpha * event.values[2] + (1 - alpha) * accel[2]
                gravity[0] = accel[0]
                gravity[1] = accel[1]
                gravity[2] = accel[2]
            }

            else -> return
        }

        // Projeção da gravidade no plano da tela = (gx, gy).
        // O vetor de “nível” (linha) é perpendicular a essa gravidade projetada.
        // Ângulo da linha (em graus) = atan2(gx, gy) * (180/π).
        val gx = gravity[0]
        val gy = gravity[1]

        // evita instabilidade quando o aparelho está perfeitamente deitado (gx≈gy≈0)
        if (abs(gx) < 1e-3 && abs(gy) < 1e-3) return

        val angleRad = atan2(gx.toDouble(), gy.toDouble())
        var angleDeg = Math.toDegrees(angleRad).toFloat()

        // Normaliza para [-90, 90] para combinar com sua UI
        if (angleDeg > 90f) angleDeg -= 180f
        if (angleDeg < -90f) angleDeg += 180f

        // Se preferir inverter o sentido, troque o sinal:
        // angleDeg = -angleDeg

        binding.overlay.rollDeg = angleDeg
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit


// ======== Screenshot (salva na Galeria) ========

    // Chame este mét0do no clique do botão:
    private fun takeAndSaveScreenshot() {
        // Tenta capturar toda a janela via PixelCopy (pega a câmera + overlay)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            captureWindowWithPixelCopy { bitmap ->
                if (bitmap != null) {
                    saveBitmapWithMediaStore(bitmap)
                } else {
                    // Fallback: compõe PreviewView.bitmap + overlay (pode dar preto se camBmp==null)
                    saveBitmapWithMediaStore(composeFallbackBitmap())
                }
            }
        } else {
            // API < 26: usa fallback
            saveBitmapWithMediaStore(composeFallbackBitmap())
        }
    }

    /** Usa PixelCopy para copiar a janela inteira (inclui SurfaceView da câmera). */
    private fun captureWindowWithPixelCopy(callback: (Bitmap?) -> Unit) {
        val activity = requireActivity()
        val root = binding.root

        val w = root.width
        val h = root.height
        if (w == 0 || h == 0) {
            callback(null); return
        }

        // Bitmap destino do tamanho do fragment
        val out = createBitmap(w, h)

        // Rect do fragment dentro da janela da Activity
        val location = IntArray(2)
        root.getLocationInWindow(location)
        val rect = android.graphics.Rect(
            location[0],                 // x
            location[1],                 // y
            location[0] + w,
            location[1] + h
        )

        // Requisita PixelCopy na janela da Activity
        android.view.PixelCopy.request(
            activity.window,
            rect,
            out,
            { result ->
                if (result == android.view.PixelCopy.SUCCESS) {
                    callback(out)
                } else {
                    callback(null)
                }
            },
            android.os.Handler(activity.mainLooper)
        )
    }

    /** Fallback antigo: PreviewView.bitmap + overlay (pode ficar preto se o camBmp vier null). */
    private fun composeFallbackBitmap(): Bitmap {
        val w = binding.root.width
        val h = binding.root.height
        val final = createBitmap(w, h)

        val camBmp = binding.previewView.bitmap
        val overlayBmp = createBitmap(w, h).also {
            val c = Canvas(it)
            binding.overlay.draw(c)
        }

        Canvas(final).apply {
            if (camBmp != null) {
                drawBitmap(camBmp, null, android.graphics.Rect(0, 0, w, h), null)
            } else {
                // fundo preto se câmera não forneceu bitmap (ex.: emulador)
                drawColor(android.graphics.Color.BLACK)
            }
            drawBitmap(overlayBmp, 0f, 0f, null)
        }
        return final
    }

    private fun saveBitmapWithMediaStore(bitmap: Bitmap) {
        val resolver = requireContext().contentResolver
        val fileName = "nivel_laser_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            // Não define RELATIVE_PATH -> o SO coloca na pasta padrão (Pictures/Galeria)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: run {
            Toast.makeText(
                requireContext(),
                getString(R.string.level_screenshot_fail),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        try {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Toast.makeText(
                requireContext(),
                getString(R.string.level_screenshot_saved),
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            Toast.makeText(
                requireContext(),
                getString(R.string.level_screenshot_fail),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}