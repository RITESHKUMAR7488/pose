package com.example.yourapp.pushupcounter

import android.app.Application
import androidx.compose.ui.geometry.Size
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.yourapp.pushupcounter.logic.PoseLandmarkerHelper
import com.example.yourapp.pushupcounter.logic.PoseLandmarkerResultBundle
import com.example.yourapp.pushupcounter.logic.PushupRepCounter
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// UI State data class
data class PushupUiState(
    val repCount: Int = 0,
    // [MODIFIED] We now store the MediaPipe result
    val poseResult: PoseLandmarkerResult? = null,
    val instruction: String = "Keep your whole body in frame.",
    val imageSize: Size = Size(480f, 640f) // Default, will be updated
)

class PushupCounterViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PushupUiState())
    val uiState: StateFlow<PushupUiState> = _uiState.asStateFlow()

    // 1. Setup the Rep Counter Logic
    private val repCounter = PushupRepCounter()

    // 2. Setup the MediaPipe Helper
    // [COROUTINE USAGE]
    // The PoseLandmarkerHelper listener runs on a background thread from MediaPipe.
    // We use viewModelScope.launch to safely switch back to the main thread
    // to perform our logic (repCounter) and update the StateFlow.
    val poseLandmarkerHelper = PoseLandmarkerHelper(
        context = application.applicationContext,
        runningMode = RunningMode.LIVE_STREAM,
        listener = { resultBundle ->
            // This is the listener lambda that PoseLandmarkerHelper calls
            viewModelScope.launch {
                onPoseResult(resultBundle)
            }
        }
    )

    /**
     * This function is called by the PoseLandmarkerHelper's listener
     * every time a new pose is detected.
     */
    private fun onPoseResult(resultBundle: PoseLandmarkerResultBundle) {
        // Perform counting logic (CPU-intensive) in this coroutine
        val (newCount, instruction) = repCounter.analyzePose(resultBundle.result)

        // Update the StateFlow to notify the UI
        _uiState.update {
            it.copy(
                poseResult = resultBundle.result,
                repCount = newCount,
                instruction = instruction,
                imageSize = Size(
                    resultBundle.imageWidth.toFloat(),
                    resultBundle.imageHeight.toFloat()
                )
            )
        }
    }

    // [REMOVED] onFrameAnalyzed is gone. The helper handles it.

    override fun onCleared() {
        super.onCleared()
        poseLandmarkerHelper.clear() // Clean up the helper
    }
}