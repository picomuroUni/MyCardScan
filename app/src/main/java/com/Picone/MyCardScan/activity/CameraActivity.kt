package com.Picone.MyCardScan.activity

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import com.Picone.MyCardScan.view.CameraOverlayView
import com.Picone.MyCardScan.R
import com.yalantis.ucrop.UCrop
import com.yalantis.ucrop.UCropActivity
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : BaseActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null

    // Richiesta permesso camera
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera()
            else Log.e("CAMERA", "Permesso camera negato")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.camera_layout)

        previewView = findViewById(R.id.previewView)
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Controlla permesso e avvia camera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        // Pulsante scatta foto
        findViewById<ImageButton>(R.id.btnCapture).setOnClickListener {
            takePhoto()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // ImageCapture
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            // Usa la camera posteriore
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Log.e("CAMERA", "Errore avvio camera: ${e.message}")
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        // File dove salvare la foto
        val photoFile = File(filesDir, "card.jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        val overlayView = findViewById<CameraOverlayView>(R.id.overlayView)
        val leftPercent   = overlayView.rectLeftPercent+0.15f
        val topPercent    = overlayView.rectTopPercent
        val widthPercent  = overlayView.rectWidthPercent-0.3f
        val heightPercent = overlayView.rectHeightPercent
        imageCapture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val rotated = fixRotation(photoFile) // giriamo la carta nella posizione corretta
                    FileOutputStream(photoFile).use { out ->
                        rotated.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    }
                    val originalFile = File(filesDir, "card_photo_original.jpg")
                    photoFile.copyTo(originalFile, overwrite = true)
                    Log.d("CAMERA", "Originale salvato in: ${originalFile.absolutePath}")
                    Log.d("CAMERA", "Foto croppata salvata: ${photoFile.absolutePath}")
                    Log.d("Crop", "1 scatto ok, chiamo cropToOverlay")
                    try {
                        cropToOverlay(photoFile, leftPercent, topPercent, widthPercent, heightPercent)
                        val src = Uri.fromFile(photoFile)
                        startCrop(src)
                    }  catch (e: Throwable) {
                        Log.e("Crop", "Eccezione prima del launch", e) }
                }
                override fun onError(exception: ImageCaptureException) {
                    Log.e("CAMERA", "Errore salvataggio foto: ${exception.message}")
                }
            }
        )
    }

    //ritagliamo la foto attorno all'overlay prima di uCrop
    private fun cropToOverlay(
        photoFile: File,
        leftPercent: Float,
        topPercent: Float,
        widthPercent: Float,
        heightPercent: Float
    ) {
        try {
            val original  = BitmapFactory.decodeFile(photoFile.absolutePath)

            val imgWidth  = original.width.toFloat()
            val imgHeight = original.height.toFloat()

            // Applica le percentuali alla dimensione reale della foto
            val left   = (imgWidth  * leftPercent).toInt()
            val top    = (imgHeight * topPercent).toInt()
            val width  = (imgWidth  * widthPercent).toInt()
            val height = (imgHeight * heightPercent).toInt()

            Log.d("CROP", "Foto: ${imgWidth}x${imgHeight} → Crop: ${left},${top} ${width}x${height}")

            val cropped = Bitmap.createBitmap(original, left, top, width, height)

            FileOutputStream(photoFile).use { out ->
                cropped.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }

            original.recycle()
            cropped.recycle()

        } catch (e: Exception) {
            Log.e("CROP", "Errore crop: ${e.message}")
        }
    }

    private fun fixRotation(photoFile: File): Bitmap {
        val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
        val exif = ExifInterface(photoFile.absolutePath)
        val rotation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        val matrix = Matrix()
        when (rotation) {
            ExifInterface.ORIENTATION_ROTATE_90  -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    //UcropActivity per far ritagliare la foto all'utente
    private val cropLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("Crop", "risultato resultCode=${result.resultCode}")
        when (result.resultCode) {
            RESULT_OK -> {
                val uri = UCrop.getOutput(result.data!!)
                if (uri != null) {
                    File(uri.path!!).copyTo(File(filesDir, "card.jpg"), overwrite = true)
                }
                setResult(RESULT_OK)
                finish()
            }
            UCrop.RESULT_ERROR -> {
                val e = UCrop.getError(result.data!!)
                Log.e("Crop", "Errore", e)
            }
        }
    }

    private fun startCrop(source: Uri) {
        Log.d("Crop", "startCrop src=$source exists=${source.path?.let { File(it).exists() }}")
        val dest = Uri.fromFile(File(cacheDir, "card.jpg"))
        val options = UCrop.Options().apply {
            setFreeStyleCropEnabled(true)   // crop libero, non solo rapporti fissi
            setCompressionQuality(95)
            setAllowedGestures(UCropActivity.ALL, UCropActivity.ALL, UCropActivity.ALL)
        }
        val intent = UCrop.of(source, dest)
            .withOptions(options)
            .getIntent(this)
        cropLauncher.launch(intent)
        Log.d("Crop", "launch chiamato")
    }
}