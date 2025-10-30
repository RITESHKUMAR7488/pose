package com.example.yourapp.pushupcounter

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CameraFront
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yourapp.ui.theme.AccentCyan
import com.example.yourapp.ui.theme.PrimaryBlueGradientEnd
import com.example.yourapp.ui.theme.PrimaryBlueGradientStart
import com.example.yourapp.ui.theme.TextGray
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Main Composable screen that holds the camera, overlay, and UI
 */
@Composable
fun PushupCounterScreen(
    viewModel: PushupCounterViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Remember our analyzer
    val poseAnalyzer = remember { PoseCameraAnalyzer(viewModel) }

    // Remember the camera executor
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
            pose = uiState.pose,
            imageSize = uiState.imageSize,
            modifier = Modifier.fillMaxSize()
        )

        // Layer 3: UI Elements
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
 */
@Composable
fun PushupCounterUI(
    repCount: Int,
    instruction: String,
    onTryLaterClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Refresh, // Placeholder for swap icon
                    contentDescription = "Swap Camera",
                    tint = Color.White
                )
                Icon(
                    imageVector = Icons.Default.CameraFront, // Placeholder for camera icon
                    contentDescription = "Camera",
                    tint = Color.White,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Text(
                text = "Pushscroll",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { /* TODO: Help click */ }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = "Help",
                    tint = Color.White
                )
            }
        }

        // Instruction Chip
        Surface(
            shape = RoundedCornerShape(50),
            color = Color.Black.copy(alpha = 0.5f),
            modifier = Modifier.padding(top = 24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CameraFront, // Placeholder for body icon
                    contentDescription = null,
                    tint = AccentCyan
                )
                Text(
                    text = instruction,
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Rep Counter Circle
        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(PrimaryBlueGradientStart, PrimaryBlueGradientEnd)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$repCount",
                color = Color.White,
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // "Try later" Button
        TextButton(onClick = onTryLaterClicked, modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                text = "Try later",
                color = TextGray,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * The Composable that draws the skeleton
 */
@Composable
fun PoseOverlay(
    pose: Pose?,
    imageSize: Size,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        pose ?: return@Canvas // Don't draw if pose is null

        // --- Scaling Logic ---
        // This logic scales the pose landmarks (which are based on the image size)
        // to fit the Composable's canvas size, while maintaining aspect ratio.
        val imageAspectRatio = imageSize.width / imageSize.height
        val canvasAspectRatio = size.width / size.height
        val scale: Float
        val offsetX: Float
        val offsetY: Float

        if (imageAspectRatio > canvasAspectRatio) {
            // Image is wider than canvas (letterboxed)
            scale = size.width / imageSize.width
            offsetX = 0f
            offsetY = (size.height - imageSize.height * scale) / 2f
        } else {
            // Image is taller than canvas (pillarboxed)
            scale = size.height / imageSize.height
            offsetX = (size.width - imageSize.width * scale) / 2f
            offsetY = 0f
        }

        // Helper to scale and translate a landmark
        fun PoseLandmark.toOffset() = Offset(
            x = this.position.x * scale + offsetX,
            y = this.position.y * scale + offsetY
        )

        // --- Draw Lines (Connections) ---
        val connections = listOf(
            PoseLandmark.LEFT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_ELBOW,
            PoseLandmark.LEFT_ELBOW to PoseLandmark.LEFT_WRIST,
            PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_ELBOW,
            PoseLandmark.RIGHT_ELBOW to PoseLandmark.RIGHT_WRIST,
            PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_HIP,
            PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_HIP to PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_HIP to PoseLandmark.LEFT_KNEE,
            PoseLandmark.RIGHT_HIP to PoseLandmark.RIGHT_KNEE
            // Add more connections as needed
        )

        val landmarks = pose.allPoseLandmarks.associateBy { it.landmarkType }
        val linePoints = mutableListOf<Offset>()

        connections.forEach { (startType, endType) ->
            val start = landmarks[startType]
            val end = landmarks[endType]
            if (start != null && end != null) {
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

        // --- Draw Points (Landmarks) ---
        pose.allPoseLandmarks.forEach { landmark ->
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

/**
 * The Composable that displays the camera feed
 */
@Composable
fun CameraPreview(
    context: Context,
    analyzer: ImageAnalysis.Analyzer,
    cameraExecutor: ExecutorService,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture: ListenableFuture<ProcessCameraProvider> =
        remember { ProcessCameraProvider.getInstance(context) }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                // 1. Preview Use Case
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                // 2. ImageAnalysis Use Case
                val imageAnalysis = ImageAnalysis.Builder()

                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, analyzer)
                    }

                // 3. Camera Selector
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                // 4. Bind all to lifecycle
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (exc: Exception) {
                    Log.e("CameraPreview", "Use case binding failed", exc)
                }

            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = modifier
    )
}