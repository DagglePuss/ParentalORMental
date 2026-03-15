package com.parentalormente.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.parentalormente.data.prefs.AppPreferences
import com.parentalormente.monitor.MonitorService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = AppPreferences(context)
    val scope = rememberCoroutineScope()

    val monitoringEnabled by prefs.monitoringEnabled.collectAsState(initial = true)
    val stealthMode by prefs.stealthMode.collectAsState(initial = false)
    val smsAlerts by prefs.smsAlerts.collectAsState(initial = true)
    val parentPhone by prefs.parentPhone.collectAsState(initial = "")
    val alertMinSeverity by prefs.alertMinSeverity.collectAsState(initial = "MEDIUM")

    var phoneInput by remember(parentPhone) { mutableStateOf(parentPhone) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Monitoring toggle
            Text("Monitoring", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Active Monitoring")
                    Text(
                        "Monitor incoming SMS for bullying",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = monitoringEnabled,
                    onCheckedChange = {
                        scope.launch {
                            prefs.setMonitoringEnabled(it)
                            if (it) MonitorService.start(context)
                            else MonitorService.stop(context)
                        }
                    }
                )
            }

            HorizontalDivider()

            // Stealth mode
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Stealth Mode")
                    Text(
                        "Hide app from launcher (access via dialer code)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = stealthMode,
                    onCheckedChange = {
                        scope.launch { prefs.setStealthMode(it) }
                    }
                )
            }

            HorizontalDivider()

            // Alerts section
            Text("Alerts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("SMS Alerts to Parent")
                    Text(
                        "Send text message when bullying detected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = smsAlerts,
                    onCheckedChange = {
                        scope.launch { prefs.setSmsAlerts(it) }
                    }
                )
            }

            // Parent phone
            OutlinedTextField(
                value = phoneInput,
                onValueChange = { phoneInput = it },
                label = { Text("Parent Phone Number") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (phoneInput != parentPhone) {
                Button(
                    onClick = {
                        scope.launch { prefs.setParentPhone(phoneInput) }
                    }
                ) {
                    Text("Save Phone Number")
                }
            }

            HorizontalDivider()

            // Severity threshold
            Text("Alert Threshold", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Minimum severity level to trigger parent alerts",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val severityOptions = listOf("LOW", "MEDIUM", "HIGH", "CRITICAL")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                severityOptions.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = alertMinSeverity == label,
                        onClick = {
                            scope.launch { prefs.setAlertMinSeverity(label) }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, severityOptions.size)
                    ) {
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
