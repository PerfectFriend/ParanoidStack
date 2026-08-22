package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private sealed class Step {
    data object Permissions : Step()
    data object Vpn : Step()
    data object Done : Step()
}

@Composable
fun PermissionsScreen(onAllGranted: () -> Unit) {
    val context = LocalContext.current

    val standardPermissions = remember {
        buildList {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    var currentStep by remember { mutableStateOf<Step>(Step.Permissions) }
    var stdGranted by remember { mutableStateOf(false) }
    var vpnApproved by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val anyDenied = results.any { (_, granted) -> !granted }
        if (anyDenied) {
            denied = true
        } else {
            stdGranted = true
            currentStep = Step.Vpn
        }
    }

    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        vpnApproved = result.resultCode == android.app.Activity.RESULT_OK
        currentStep = Step.Done
    }

    LaunchedEffect(currentStep) {
        when (currentStep) {
            Step.Permissions -> {
                val missing = standardPermissions.filter {
                    ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                }
                if (missing.isEmpty()) {
                    stdGranted = true
                    currentStep = Step.Vpn
                } else {
                    permLauncher.launch(missing.toTypedArray())
                }
            }
            Step.Vpn -> {
                if (!stdGranted) {
                    currentStep = Step.Permissions
                    return@LaunchedEffect
                }
                val vpnIntent = VpnService.prepare(context)
                if (vpnIntent == null) {
                    vpnApproved = true
                    currentStep = Step.Done
                } else {
                    vpnLauncher.launch(vpnIntent)
                }
            }
            Step.Done -> {
                onAllGranted()
            }
        }
    }

    val labels = mapOf(
        Manifest.permission.CAMERA to "Camera — scan QR codes & invitations",
        Manifest.permission.RECORD_AUDIO to "Microphone — voice messages & walkie-talkie",
        Manifest.permission.POST_NOTIFICATIONS to "Notifications — service background alerts",
        Manifest.permission.ACCESS_FINE_LOCATION to "Location — WiFi mesh & peer discovery",
        Manifest.permission.ACCESS_COARSE_LOCATION to "Location (coarse) — network scanning"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0D0B1A),
                        Color(0xFF07050F),
                        Color(0xFF020108)
                    ),
                    radius = 1.5f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = if (denied) "Permissions required" else "Grant permissions",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00FFCC),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (denied) "All permissions must be granted for the app to function.\nPlease allow them in system settings."
                       else "N2 needs the following permissions to work:",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (perm in standardPermissions) {
                    val granted = ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    if (granted) Color(0xFF00FFCC) else Color(0xFFFF007F),
                                    RoundedCornerShape(5.dp)
                                )
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = labels[perm] ?: perm,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = if (granted) 0.5f else 0.9f),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                if (vpnApproved) Color(0xFF00FFCC) else Color(0xFFFF007F),
                                RoundedCornerShape(5.dp)
                            )
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "VPN — secure tunnel & censorship bypass",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = if (vpnApproved) 0.5f else 0.9f),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
            if (denied) {
                Button(
                    onClick = {
                        denied = false
                        currentStep = Step.Permissions
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF007F))
                ) {
                    Text("Retry", fontWeight = FontWeight.Bold)
                }
            } else if (currentStep !is Step.Done) {
                CircularProgressIndicator(
                    color = Color(0xFF00FFCC),
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    }
}
