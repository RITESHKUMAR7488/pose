package com.example.yourapp

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.yourapp.pushupcounter.PushupCounterScreen
import com.example.yourapp.ui.theme.YourAppTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YourAppTheme {
                val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (cameraPermissionState.status) {
                        PermissionStatus.Granted -> {
                            PushupCounterScreen()
                        }
                        is PermissionStatus.Denied -> {
                            PermissionDeniedContent(
                                (cameraPermissionState.status as PermissionStatus.Denied).shouldShowRationale,
                                cameraPermissionState::launchPermissionRequest
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionDeniedContent(
    shouldShowRationale: Boolean,
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val text = if (shouldShowRationale) {
            "Camera permission is needed to analyze your pushups. Please grant the permission."
        } else {
            "Camera permission is required for this feature to work. Please grant permission in settings."
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRequestPermission) {
            Text("Grant Permission")
        }
    }
}