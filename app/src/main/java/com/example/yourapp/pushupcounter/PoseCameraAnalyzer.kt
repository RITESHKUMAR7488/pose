package com.example.yourapp.pushupcounter

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

// This class acts as the "glue" between CameraX and our ViewModel
// It implements the ImageAnalysis.Analyzer interface
class PoseCameraAnalyzer(
    private val viewModel: PushupCounterViewModel
) : ImageAnalysis.Analyzer {

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        // This is the only thing it does:
        // Pass the frame to the ViewModel for processing.
        viewModel.onFrameAnalyzed(imageProxy)
    }
}