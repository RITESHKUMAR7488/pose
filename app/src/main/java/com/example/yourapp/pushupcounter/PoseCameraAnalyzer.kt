package com.example.yourapp.pushupcounter

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.yourapp.pushupcounter.logic.PoseLandmarkerHelper

/**
 * This class acts as the "glue" between CameraX and our PoseLandmarkerHelper.
 * It's only job is to pass the ImageProxy to the helper for detection.
 */
class PoseCameraAnalyzer(
    private val poseLandmarkerHelper: PoseLandmarkerHelper
) : ImageAnalysis.Analyzer {

    override fun analyze(imageProxy: ImageProxy) {
        // This is the only thing it does:
        // Pass the frame to the helper for processing.
        poseLandmarkerHelper.detectLiveStream(imageProxy)
    }
}