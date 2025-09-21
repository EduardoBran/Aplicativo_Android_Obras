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
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
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

class LevelMeterFragment : Fragment(), SensorEventListener {

    private var _binding: FragmentLevelMeterBinding? = null
    private val binding get() = _binding!!

    private lateinit var sensorManager: SensorManager
    private var accelValues = FloatArray(3)
    private var magnetValues = FloatArray(3)
    private var hasAccel = false
    private var hasMagnet = false

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
        hasAccel = sensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER).isNotEmpty()
        hasMagnet = sensorManager.getSensorList(Sensor.TYPE_MAGNETIC_FIELD).isNotEmpty()

        if (hasAccel) {
            sensorManager.registerListener(
                this,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_UI
            )
        }
        if (hasMagnet) {
            sensorManager.registerListener(
                this,
                sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                SensorManager.SENSOR_DELAY_UI
            )
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accelValues = event.values.clone()
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                magnetValues = event.values.clone()
            }
        }
        if (!hasAccel || !hasMagnet) return

        val rm = FloatArray(9)
        val im = FloatArray(9)
        val success = SensorManager.getRotationMatrix(rm, im, accelValues, magnetValues)
        if (!success) return

        // leva em conta rotação da tela para remapear e obter roll corretamente
        val rotation = binding.root.display?.rotation ?: Surface.ROTATION_0
        val outR = FloatArray(9)
        when (rotation) {
            Surface.ROTATION_0 -> SensorManager.remapCoordinateSystem(
                rm,
                SensorManager.AXIS_X,
                SensorManager.AXIS_Y,
                outR
            )

            Surface.ROTATION_90 -> SensorManager.remapCoordinateSystem(
                rm,
                SensorManager.AXIS_Y,
                SensorManager.AXIS_MINUS_X,
                outR
            )

            Surface.ROTATION_180 -> SensorManager.remapCoordinateSystem(
                rm,
                SensorManager.AXIS_MINUS_X,
                SensorManager.AXIS_MINUS_Y,
                outR
            )

            Surface.ROTATION_270 -> SensorManager.remapCoordinateSystem(
                rm,
                SensorManager.AXIS_MINUS_Y,
                SensorManager.AXIS_X,
                outR
            )

            else -> System.arraycopy(rm, 0, outR, 0, 9)
        }

        val orient = FloatArray(3)
        SensorManager.getOrientation(outR, orient)
        // orient[2] é o roll em radianos → converte pra graus e limita a [-90, 90]
        val rollDeg = Math.toDegrees(orient[2].toDouble()).toFloat().coerceIn(-90f, 90f)

        binding.overlay.rollDeg = rollDeg
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    // ==== Screenshot (salva na Galeria) ====
    private fun takeAndSaveScreenshot() {
        val root = binding.root
        if (root.width == 0 || root.height == 0) return

        val bitmap = createBitmap(root.width, root.height)
        val canvas = Canvas(bitmap)
        root.draw(canvas)

        val resolver = requireContext().contentResolver
        val fileName = "nivel_laser_${System.currentTimeMillis()}.png"

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            // Sistema do usuário decide onde vai salvar
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
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