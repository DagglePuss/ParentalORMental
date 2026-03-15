package com.parentalormente.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.parentalormente.ParentalApp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentDetailScreen(incidentId: Long, onBack: () -> Unit) {
    val dao = ParentalApp.instance.database.incidentDao()
    val scope = rememberCoroutineScope()
    var incident by remember { mutableStateOf<IncidentEntity?>(null) }
    var notes by remember { mutableStateOf("") }

    LaunchedEffect(incidentId) {
        incident = dao.getById(incidentId)
        incident?.let { notes = it.parentNotes }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Incident Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        incident?.let { inc ->
            val severityColor = when (inc.severity) {
                4 -> Color(0xFFD32F2F)
                3 -> Color(0xFFE64A19)
                2 -> Color(0xFFF57C00)
                1 -> Color(0xFFFBC02D)
                else -> Color.Gray
            }
            val severityLabel = when (inc.severity) {
                4 -> "CRITICAL"
                3 -> "HIGH"
                2 -> "MEDIUM"
                1 -> "LOW"
                else -> "UNKNOWN"
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Severity badge
                Card(
                    colors = CardDefaults.cardColors(containerColor = severityColor.copy(alpha = 0.15f))
                ) {
                    Text(
                        severityLabel,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = severityColor,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Sender
                Text("From", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(inc.sender, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)

                // Timestamp
                Text("When", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatTimestamp(inc.timestamp), style = MaterialTheme.typography.bodyMedium)

                // Detection summary
                Text("Detection", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(inc.summary, style = MaterialTheme.typography.bodyMedium)

                // Matched patterns
                Text("Matched Patterns", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    inc.matchedPatterns.replace("|", "\n"),
                    style = MaterialTheme.typography.bodySmall
                )

                // Message content
                Text("Message Content", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        inc.content,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Parent notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Parent Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                dao.markFalsePositive(incidentId)
                                onBack()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("False Positive")
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                dao.markReviewed(incidentId, notes)
                                onBack()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Mark Reviewed")
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("EEEE, MMM d yyyy 'at' h:mm a", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}
