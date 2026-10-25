// File: app/src/main/java/com/myai/assistant/features/camera/CameraManager.kt
// Camera Manager — Photo lena + ML Kit analysis
// Optimized: Memory-safe downscaling to prevent OOM, clean error handling, async coroutine execution

package com.myai.assistant.features.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import android.util.Size
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.max

@Singleton
class CameraManager @Inject constructor() {

    companion object {
        private const val TAG = "CameraManager"
        // Target max dimension for ML Kit processing (1080p-1920p optimal for OCR without OOM)
        private const val MAX_IMAGE_DIMENSION = 1920
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
            try {
                cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetResolution(Size(1920, 1080))
                    .build()

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)
                onReady()
                Log.d(TAG, "✅ Camera started successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Camera start failed: ${e.message}", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Photo lo aur file mein save karo
     */
    suspend fun takePhoto(context: Context): File? = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            val capture = imageCapture
            if (capture == null) {
                Log.w(TAG, "Cannot take photo: ImageCapture is not initialized")
                if (cont.isActive) cont.resume(null)
                return@suspendCancellableCoroutine
            }

            val photoFile = try {
                File(
                    context.cacheDir,
                    "AI_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create target photo file: ${e.message}")
                if (cont.isActive) cont.resume(null)
                return@suspendCancellableCoroutine
            }

            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

            capture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        Log.d(TAG, "📸 Photo saved: ${photoFile.absolutePath}")
                        if (cont.isActive) cont.resume(photoFile)
                    }

                    override fun onError(e: ImageCaptureException) {
                        Log.e(TAG, "Photo capture error: ${e.message}")
                        if (cont.isActive) cont.resume(null)
                    }
                }
            )

            cont.invokeOnCancellation {
                Log.d(TAG, "takePhoto coroutine was cancelled")
            }
        }
    }

    /**
     * Image mein se text padhho (OCR)
     * Memory-safe downscaling prevents OutOfMemoryError on high-resolution camera photos.
     */
    suspend fun recognizeText(context: Context, imageUri: Uri): String = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            var decodedBitmap: Bitmap? = null
            try {
                val sampled = decodeSampledBitmap(context, imageUri)
                val inputImage = if (sampled != null) {
                    decodedBitmap = sampled.first
                    InputImage.fromBitmap(sampled.first, sampled.second)
                } else {
                    InputImage.fromFilePath(context, imageUri)
                }

                textRecognizer.process(inputImage)
                    .addOnSuccessListener { result ->
                        Log.d(TAG, "📝 Text found: ${result.text.take(100)}")
                        if (cont.isActive) cont.resume(result.text)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "OCR recognition error: ${e.message}")
                        if (cont.isActive) cont.resume("")
                    }
                    .addOnCompleteListener {
                        recycleBitmapSafely(decodedBitmap)
                    }

            } catch (t: Throwable) {
                Log.e(TAG, "recognizeText error (OOM or IO): ${t.message}", t)
                recycleBitmapSafely(decodedBitmap)
                if (cont.isActive) cont.resume("")
            }
        }
    }

    /**
     * Image mein kya hai pehchaano (labels)
     * Memory-safe downscaling prevents OutOfMemoryError on high-resolution camera photos.
     */
    suspend fun labelImage(context: Context, imageUri: Uri): List<String> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            var decodedBitmap: Bitmap? = null
            try {
                val sampled = decodeSampledBitmap(context, imageUri)
                val inputImage = if (sampled != null) {
                    decodedBitmap = sampled.first
                    InputImage.fromBitmap(sampled.first, sampled.second)
                } else {
                    InputImage.fromFilePath(context, imageUri)
                }

                imageLabeler.process(inputImage)
                    .addOnSuccessListener { labels ->
                        val results = labels.map { "${it.text} (${(it.confidence * 100).toInt()}%)" }
                        Log.d(TAG, "🏷️ Labels: $results")
                        if (cont.isActive) cont.resume(results)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Image labeling error: ${e.message}")
                        if (cont.isActive) cont.resume(emptyList())
                    }
                    .addOnCompleteListener {
                        recycleBitmapSafely(decodedBitmap)
                    }

            } catch (t: Throwable) {
                Log.e(TAG, "labelImage error (OOM or IO): ${t.message}", t)
                recycleBitmapSafely(decodedBitmap)
                if (cont.isActive) cont.resume(emptyList())
            }
        }
    }

    /**
     * Memory-safe bitmap decoding:
     * 1. Reads dimensions without allocating pixel memory.
     * 2. Calculates power-of-two inSampleSize to keep max dimension <= MAX_IMAGE_DIMENSION.
     * 3. Extracts EXIF rotation degrees.
     * 4. Returns downsampled bitmap and rotation degrees.
     */
    private fun decodeSampledBitmap(
        context: Context,
        imageUri: Uri,
        maxDimension: Int = MAX_IMAGE_DIMENSION
    ): Pair<Bitmap, Int>? {
        return try {
            // Step 1: Decode bounds only
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            context.contentResolver.openInputStream(imageUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            } ?: return null

            val originalWidth = boundsOptions.outWidth
            val originalHeight = boundsOptions.outHeight
            if (originalWidth <= 0 || originalHeight <= 0) return null

            // Step 2: Compute power-of-two inSampleSize
            var inSampleSize = 1
            val maxOriginalDim = max(originalWidth, originalHeight)
            while ((maxOriginalDim / (inSampleSize * 2)) >= maxDimension) {
                inSampleSize *= 2
            }

            // Step 3: Decode with optimal sample size
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val bitmap = context.contentResolver.openInputStream(imageUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            // Step 4: Extract EXIF rotation
            val rotationDegrees = getExifOrientation(context, imageUri)

            Pair(bitmap, rotationDegrees)
        } catch (t: Throwable) {
            Log.e(TAG, "Error decoding sampled bitmap: ${t.message}", t)
            null
        }
    }

    /**
     * Extract EXIF rotation degrees from image URI
     */
    private fun getExifOrientation(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exifInterface = ExifInterface(stream)
                when (exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (_: Throwable) {
            0
        }
    }

    /**
     * Safely recycle bitmap to avoid holding onto native memory
     */
    private fun recycleBitmapSafely(bitmap: Bitmap?) {
        try {
            if (bitmap != null && !bitmap.isRecycled) {
                bitmap.recycle()
            }
        } catch (_: Throwable) {}
    }

    /**
     * Camera band karo
     */
    fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping camera: ${e.message}")
        } finally {
            cameraProvider = null
            imageCapture = null
        }
    }
}
