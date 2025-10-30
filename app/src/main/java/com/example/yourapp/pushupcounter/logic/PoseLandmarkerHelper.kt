package com.example.yourapp.pushupcounter.logic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Data class to hold the analysis results.
 *
 * @param result The list of detected poses.
 * @param imageWidth The width of the image that was processed.
 * @param imageHeight The height of the image that was processed.
 */
data class PoseLandmarkerResultBundle(
    val result: PoseLandmarkerResult,
    val imageWidth: Int,
    val imageHeight: Int
)

/**
 * This class handles all the MediaPipe Pose Landmarker setup and inference logic.
 */
class PoseLandmarkerHelper(
    val context: Context,
    val runningMode: RunningMode = RunningMode.LIVE_STREAM,
    // This listener sends results back to the ViewModel
    val listener: (PoseLandmarkerResultBundle) -> Unit
) {
    private var poseLandmarker: PoseLandmarker? = null
    private var backgroundExecutor: ExecutorService? = null

    // Initialize the helper
    init {
        setupPoseLandmarker()
    }

    /**
     * Sets up the PoseLandmarker from the .task model file in assets.
     */
    private fun setupPoseLandmarker() {
        // Create a background thread executor for MediaPipe
        backgroundExecutor = Executors.newSingleThreadExecutor()

        // Set up the model options
        val modelName = "pose_landmarker_full.task"
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(modelName)
            .build()

        val optionsBuilder = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(runningMode)
            .setNumPoses(1) // We only care about one person for a pushup counter

        // Set up the result listener for LIVE_STREAM mode
        if (runningMode == RunningMode.LIVE_STREAM) {
            optionsBuilder.setResultListener { result, image ->
                val resultBundle = PoseLandmarkerResultBundle(
                    result = result,
                    imageWidth = image.width,
                    imageHeight = image.height
                )
                // Pass the result back to the listener (which is in the ViewModel)
                listener.invoke(resultBundle)
            }.setErrorListener { error ->
                Log.e("PoseLandmarkerHelper", "MediaPipe error: ${error.message}")
            }
        }

        val options = optionsBuilder.build()
        try {
            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            Log.e("PoseLandmarkerHelper", "Failed to create PoseLandmarker: ${e.message}")
        }
    }

    /**
     * Main detection function for live camera streams.
     * Converts the CameraX ImageProxy to a MediaPipe MPImage and runs detection.
     */
    @OptIn(ExperimentalGetImage::class)
    fun detectLiveStream(imageProxy: ImageProxy) {
        // Don't run if the landmarker isn't ready
        if (poseLandmarker == null) {
            imageProxy.close()
            return
        }

        // Get the timestamp for the frame
        val frameTime = SystemClock.uptimeMillis()

        // Convert the ImageProxy to a Bitmap, then to an MPImage
        val bitmap = imageProxy.toBitmap()
        if (bitmap == null) {
            imageProxy.close()
            return
        }

        // Apply the same rotation as the ImageProxy
        val matrix = Matrix().apply {
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
        }
        val rotatedBitmap = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false
        )

        val mpImage = BitmapImageBuilder(rotatedBitmap).build()

        // Run asynchronous detection
        poseLandmarker?.detectAsync(mpImage, frameTime)

        // Close the ImageProxy *after* we're done with its bitmap
        // Note: .toBitmap() creates a copy, so we can close this immediately
        // if we weren't using the bitmap variable later.
        // But since we use rotatedBitmap, we are safe to close.
        imageProxy.close()
    }

    /**
     * Cleans up the background executor and closes the landmarker.
     */
    fun clear() {
        poseLandmarker?.close()
        poseLandmarker = null
        backgroundExecutor?.shutdown()
        backgroundExecutor = null
    }
}