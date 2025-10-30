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
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
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

    val poseLandmarkerHelper = viewModel.poseLandmarkerHelper
    val poseAnalyzer = remember { PoseCameraAnalyzer(poseLandmarkerHelper) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreview(
            context = context,
            analyzer = poseAnalyzer,
            cameraExecutor = cameraExecutor,
            modifier = Modifier.fillMaxSize()
        )
        PoseOverlay(
            poseResult = uiState.poseResult,
            imageSize = uiState.imageSize,
            modifier = Modifier.fillMaxSize()
        )
        PushupCounterUI(
            repCount = uiState.repCount,
            instruction = uiState.instruction,
            onTryLaterClicked = { /* TODO: Handle click */ },
            modifier = Modifier.fillMaxSize()
        )
    }

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
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Swap Camera",
                    tint = Color.White
                )
                Icon(
                    imageVector = Icons.Default.CameraFront,
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
                    imageVector = Icons.Default.CameraFront,
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
    poseResult: PoseLandmarkerResult?,
    imageSize: Size,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        poseResult ?: return@Canvas
        if (poseResult.landmarks().isEmpty()) return@Canvas

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

        fun NormalizedLandmark.toOffset(): Offset {
            val imageX = this.x() * imageSize.width
            val imageY = this.y() * imageSize.height
            return Offset(
                x = imageX * scale + offsetX,
                y = imageY * scale + offsetY
            )
        }

        val landmarks = poseResult.landmarks()[0]

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

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(android.util.Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, analyzer)
                    }

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

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