package com.example.yourapp.pushupcounter

import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import androidx.compose.ui.geometry.Size
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yourapp.pushupcounter.logic.PushupRepCounter
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
// [FIXED] Changed this import
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// UI State data class
data class PushupUiState(
    val repCount: Int = 0,
    val pose: Pose? = null,
    val instruction: String = "Keep your whole body in frame.",
    val imageSize: Size = Size(480f, 640f) // Default, will be updated
)

class PushupCounterViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PushupUiState())
    val uiState: StateFlow<PushupUiState> = _uiState.asStateFlow()

    // 1. Setup the ML Kit Pose Detector
    private val poseDetectorOptions =
        // [FIXED] Changed to AccuratePoseDetectorOptions
        PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()

    private val poseDetector = PoseDetection.getClient(poseDetectorOptions)

    // 2. Setup the Rep Counter Logic
    private val repCounter = PushupRepCounter()

    // 3. The Analyzer Function (called from CameraX)
    @OptIn(ExperimentalGetImage::class)
    fun onFrameAnalyzed(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        val inputImage =
            InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        // [COROUTINE USAGE]
        // Launch a coroutine to process the image off the main thread
        viewModelScope.launch {
            try {
                // Use .await() to suspend until the ML Kit task is complete
                val pose = poseDetector.process(inputImage).await()

                // Perform counting logic (CPU-intensive) in this coroutine
                val (newCount, instruction) = repCounter.analyzePose(pose)

                // Update the StateFlow to notify the UI
                _uiState.update {
                    it.copy(
                        pose = pose,
                        repCount = newCount,
                        instruction = instruction,
                        imageSize = Size(inputImage.width.toFloat(), inputImage.height.toFloat())                    )
                }
            } catch (e: Exception) {
                Log.e("PushupVM", "Pose detection failed", e)
            } finally {
                imageProxy.close()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        poseDetector.close() // Clean up the detector when ViewModel is destroyed
    }
}