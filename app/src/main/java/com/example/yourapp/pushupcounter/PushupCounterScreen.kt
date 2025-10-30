package com.example.yourapp.pushupcounter

// ... other imports
import androidx.camera.core.* // Keep CameraX imports
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
// ...
// [MODIFIED] Import the new result object
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.util.concurrent.Executors
// ... other imports
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import java.nio.file.Files.size

// ...

/**
 * Main Composable screen that holds the camera, overlay, and UI
 */
@Composable
fun PushupCounterScreen(
    viewModel: PushupCounterViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // [MODIFIED] Get the helper from the ViewModel
    val poseLandmarkerHelper = viewModel.poseLandmarkerHelper

    // [MODIFIED] Pass the helper to the analyzer
    val poseAnalyzer = remember { PoseCameraAnalyzer(poseLandmarkerHelper) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    Box(modifier = Modifier.fillMaxSize()) {
        // Layer 1: Camera Preview
        CameraPreview(
            context = context,
            analyzer = poseAnalyzer,
            cameraExecutor = cameraExecutor,
            modifier = Modifier.fillMaxSize()
        )

        // Layer 2: Pose Overlay (The Skeleton)
        PoseOverlay(
            // [MODIFIED] Pass the new result object
            poseResult = uiState.poseResult,
            imageSize = uiState.imageSize,
            modifier = Modifier.fillMaxSize()
        )

        // Layer 3: UI Elements (No change)
        PushupCounterUI(
            repCount = uiState.repCount,
            instruction = uiState.instruction,
            onTryLaterClicked = { /* TODO: Handle click */ },
            modifier = Modifier.fillMaxSize()
        )
    }

    // Clean up the executor when the composable is disposed
    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }
}

/**
 * The stateless UI elements overlaid on the camera
 * (This composable does not need any changes)
 */
@Composable
fun PushupCounterUI(
    // ...
) {
    // ...
}

/**
 * The Composable that draws the skeleton
 * [MODIFIED] This is heavily updated to use PoseLandmarkerResult
 */
/**
 * The Composable that draws the skeleton
 * [CORRECTED] This is the fixed version that uses integer indexes
 */
@Composable
fun PoseOverlay(
    poseResult: PoseLandmarkerResult?,
    imageSize: androidx.compose.ui.geometry.Size,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        poseResult ?: return@Canvas // Don't draw if result is null
        if (poseResult.landmarks().isEmpty()) return@Canvas

        // --- Scaling Logic (This was correct) ---
        val imageAspectRatio = imageSize.width / imageSize.height
        val canvasAspectRatio = size.width / size.height
        val scale: Float
        val offsetX: Float
        val offsetY: Float

        if (imageAspectRatio > canvasAspectRatio) {
            scale = size.width / imageSize.width
            offsetX = 0f
            offsetY = (size.height - imageSize.height * scale) / 2f
        } else {
            scale = size.height / imageSize.height
            offsetX = (size.width - imageSize.width * scale) / 2f
            offsetY = 0f
        }

        // [FIXED] Helper to scale and translate a normalized landmark
        // We use the correct import: com.google.mediapipe.tasks.components.containers.NormalizedLandmark
        fun NormalizedLandmark.toOffset(): Offset {
            val imageX = this.x() * imageSize.width
            val imageY = this.y() * imageSize.height
            return Offset(
                x = imageX * scale + offsetX,
                y = imageY * scale + offsetY
            )
        }

        // --- Draw Lines (Connections) ---
        val landmarks = poseResult.landmarks()[0]

        // [FIXED] Connections list now uses the correct integer indexes
        val connections = listOf(
            11 to 12, // LEFT_SHOULDER to RIGHT_SHOULDER
            11 to 13, // LEFT_SHOULDER to LEFT_ELBOW
            13 to 15, // LEFT_ELBOW to LEFT_WRIST
            12 to 14, // RIGHT_SHOULDER to RIGHT_ELBOW
            14 to 16, // RIGHT_ELBOW to RIGHT_WRIST
            11 to 23, // LEFT_SHOULDER to LEFT_HIP
            12 to 24, // RIGHT_SHOULDER to RIGHT_HIP
            23 to 24, // LEFT_HIP to RIGHT_HIP
            23 to 25, // LEFT_HIP to LEFT_KNEE
            24 to 26  // RIGHT_HIP to RIGHT_KNEE
        )

        val linePoints = mutableListOf<Offset>()

        connections.forEach { (startType, endType) ->
            val start = landmarks[startType]
            val end = landmarks[endType]
            // Check visibility before drawing
            if (start.visibility() > 0.5f && end.visibility() > 0.5f) {
                linePoints.add(start.toOffset())
                linePoints.add(end.toOffset())
            }
        }

        drawPoints(
            points = linePoints,
            pointMode = PointMode.Lines,
            color = AccentCyan,
            strokeWidth = 6f,
            cap = StrokeCap.Round
        )

        // --- Draw Points (Landmarks) (This was correct) ---
        landmarks.forEach { landmark ->
            if (landmark.visibility() > 0.5f) {
                drawCircle(
                    color = AccentCyan,
                    radius = 8f,
                    center = landmark.toOffset()
                )
                drawCircle(
                    color = Color.White,
                    radius = 8f,
                    center = landmark.toOffset(),
                    style = Stroke(width = 2f)
                )
            }
        }
    }
}

/**
 * The Composable that displays the camera feed
 * (This composable does not need any changes)
 */
@Composable
fun CameraPreview(
    // ...
) {
    // ...
}