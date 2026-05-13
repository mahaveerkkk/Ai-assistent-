// File: app/src/main/java/com/myai/assistant/features/camera/CameraManager.kt
// Camera Manager — Photo lena + ML Kit analysis

package com.myai.assistant.features.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class CameraManager @Inject constructor() {

    companion object {
        private const val TAG = "CameraManager"
    }

    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null

    // ML Kit instances
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val imageLabeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder().setConfidenceThreshold(0.7f).build()
    )

    /**
     * Camera start karo with preview
     */
    fun startCamera(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onReady: () -> Unit = {}
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)
                onReady()
                Log.d(TAG, "✅ Camera started")
            } catch (e: Exception) {
                Log.e(TAG, "Camera start failed: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Photo lo aur file mein save karo
     */
    suspend fun takePhoto(context: Context): File? = suspendCancellableCoroutine { cont ->
        val capture = imageCapture ?: run { cont.resume(null); return@suspendCancellableCoroutine }

        val photoFile = File(
            context.cacheDir,
            "AI_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(outputOptions, ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Log.d(TAG, "📸 Photo saved: ${photoFile.absolutePath}")
                    cont.resume(photoFile)
                }
                override fun onError(e: ImageCaptureException) {
                    Log.e(TAG, "Photo error: ${e.message}")
                    cont.resume(null)
                }
            })
    }

    /**
     * Image mein se text padhho (OCR)
     */
    suspend fun recognizeText(context: Context, imageUri: Uri): String = suspendCancellableCoroutine { cont ->
        try {
            val image = InputImage.fromFilePath(context, imageUri)
            textRecognizer.process(image)
                .addOnSuccessListener { result ->
                    Log.d(TAG, "📝 Text found: ${result.text.take(100)}")
                    cont.resume(result.text)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "OCR error: ${e.message}")
                    cont.resume("")
                }
        } catch (e: Exception) {
            cont.resume("")
        }
    }

    /**
     * Image mein kya hai pehchaano (labels)
     */
    suspend fun labelImage(context: Context, imageUri: Uri): List<String> = suspendCancellableCoroutine { cont ->
        try {
            val image = InputImage.fromFilePath(context, imageUri)
            imageLabeler.process(image)
                .addOnSuccessListener { labels ->
                    val results = labels.map { "${it.text} (${(it.confidence * 100).toInt()}%)" }
                    Log.d(TAG, "🏷️ Labels: $results")
                    cont.resume(results)
                }
                .addOnFailureListener { cont.resume(emptyList()) }
        } catch (e: Exception) {
            cont.resume(emptyList())
        }
    }

    fun stopCamera() {
        cameraProvider?.unbindAll()
    }
}
