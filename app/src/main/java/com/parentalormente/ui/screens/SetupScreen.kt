package com.parentalormente.ui.screens

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.parentalormente.data.prefs.AppPreferences
import com.parentalormente.monitor.MonitorService
import com.parentalormente.remote.PomDeviceAdmin
import kotlinx.coroutines.launch

@Composable
fun SetupScreen(onSetupComplete: () -> Unit) {
    val context = LocalContext.current
    val prefs = AppPreferences(context)
    val scope = rememberCoroutineScope()

    var parentPhone by remember { mutableStateOf("") }
    var step by remember { mutableIntStateOf(0) }

    // Device Admin state — re-checked every time step 2 is composed
    val dpm = remember { context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager }
    val adminComponent = remember { ComponentName(context, PomDeviceAdmin::class.java) }
    var adminActive by remember { mutableStateOf(dpm.isAdminActive(adminComponent)) }

    val adminActivationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Re-check after the system dialog returns
        adminActive = dpm.isAdminActive(adminComponent)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (step) {
            0 -> {
                // Step 0: Welcome
                Icon(
                    Icons.Default.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "ParentalORMental",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Free cyberbullying protection for your child",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "No subscriptions. No cloud. No data harvesting.\nEverything stays on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = { step = 1 }) {
                    Text("Get Started")
                }
            }

            1 -> {
                // Step 1: Parent phone number
                Text(
                    "Parent Alert Setup",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Enter the parent's phone number to receive SMS alerts when bullying is detected.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedTextField(
                    value = parentPhone,
                    onValueChange = { parentPhone = it },
                    label = { Text("Parent Phone Number") },
                    placeholder = { Text("+1234567890") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        scope.launch {
                            prefs.setParentPhone(parentPhone)
                        }
                        step = 2
                    },
                    enabled = parentPhone.isNotBlank()
                ) {
                    Text("Next")
                }
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = { step = 2 }) {
                    Text("Skip — I'll set this up later")
                }
            }

            2 -> {
                // Step 2: Device Admin
                Icon(
                    Icons.Default.AdminPanelSettings,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = if (adminActive) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Remote Lock & Lockdown",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Granting Device Admin lets you remotely lock this phone or " +
                    "activate lockdown mode by texting POM:LOCK or POM:LOCKDOWN " +
                    "from your number. No data leaves the device.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "You can revoke this at any time in Android Settings > Security > Device Admin.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))

                if (adminActive) {
                    FilledTonalButton(onClick = {}) {
                        Text("Device Admin Active")
                    }
                } else {
                    Button(
                        onClick = {
                            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                                putExtra(
                                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                    "Allows the parent to remotely lock this device via SMS commands."
                                )
                            }
                            adminActivationLauncher.launch(intent)
                        }
                    ) {
                        Text("Activate Device Admin")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        scope.launch {
                            prefs.setSetupComplete(true)
                            MonitorService.start(context)
                            onSetupComplete()
                        }
                    }
                ) {
                    Text("Finish Setup")
                }
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    onClick = {
                        scope.launch {
                            prefs.setSetupComplete(true)
                            MonitorService.start(context)
                            onSetupComplete()
                        }
                    }
                ) {
                    Text("Skip")
                }
            }
        }
    }
}
