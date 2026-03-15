package com.parentalormente.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.parentalormente.ParentalApp
import com.parentalormente.alerts.RealTimeRelay
import com.parentalormente.data.db.IncidentEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onIncidentClick: (Long) -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = ParentalApp.instance.database.incidentDao()
    val incidents by dao.getAllIncidents().collectAsState(initial = emptyList())
    val unreviewedCount by dao.getUnreviewedCount().collectAsState(initial = 0)
    val highSeverityCount by dao.getHighSeverityUnreviewedCount().collectAsState(initial = 0)
    var showPanicConfirm by remember { mutableStateOf(false) }

    // Panic button confirmation dialog
    if (showPanicConfirm) {
        AlertDialog(
            onDismissRequest = { showPanicConfirm = false },
            title = { Text("Send SOS to Parent?") },
            text = { Text("This will immediately alert your parent that you need help.") },
            confirmButton = {
                Button(
                    onClick = {
                        showPanicConfirm = false
                        scope.launch {
                            RealTimeRelay(context).sendPanicAlert()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("YES — I NEED HELP")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPanicConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ParentalORMental") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            LargeFloatingActionButton(
                onClick = { showPanicConfirm = true },
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ) {
                Text("SOS", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Status card
            StatusCard(
                unreviewedCount = unreviewedCount,
                highSeverityCount = highSeverityCount
            )

            // Incident list
            if (incidents.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "All clear",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            "No incidents detected yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(incidents) { incident ->
                        IncidentCard(
                            incident = incident,
                            onClick = { onIncidentClick(incident.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(unreviewedCount: Int, highSeverityCount: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highSeverityCount > 0)
                MaterialTheme.colorScheme.errorContainer
            else
                MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (highSeverityCount > 0) Icons.Default.Warning else Icons.Default.Shield,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (highSeverityCount > 0)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    if (highSeverityCount > 0)
                        "$highSeverityCount urgent alert${if (highSeverityCount != 1) "s" else ""}"
                    else
                        "Protection active",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (unreviewedCount > 0) {
                    Text(
                        "$unreviewedCount unreviewed incident${if (unreviewedCount != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun IncidentCard(incident: IncidentEntity, onClick: () -> Unit) {
    val severityColor = when (incident.severity) {
        4 -> Color(0xFFD32F2F) // Critical - red
        3 -> Color(0xFFE64A19) // High - deep orange
        2 -> Color(0xFFF57C00) // Medium - orange
        1 -> Color(0xFFFBC02D) // Low - yellow
        else -> Color.Gray
    }
    val severityLabel = when (incident.severity) {
        4 -> "CRITICAL"
        3 -> "HIGH"
        2 -> "MEDIUM"
        1 -> "LOW"
        else -> "UNKNOWN"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    severityLabel,
                    color = severityColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                if (!incident.reviewed) {
                    Badge { Text("NEW") }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "From: ${incident.sender}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                incident.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                formatTimestamp(incident.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}
