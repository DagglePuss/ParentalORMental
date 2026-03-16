package com.parentalormente.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.parentalormente.ParentalApp
import com.parentalormente.evidence.EvidenceCollector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─── Data model ──────────────────────────────────────────────────────────────

private enum class EvidenceFileType {
    INCIDENT_TEXT,  // incident_<id>_<ts>.txt
    REPORT_TEXT,    // evidence_report_<ts>.txt
    CSV,            // incidents_<ts>.csv
    SCREENSHOT,     // screenshots/*.png
    OTHER
}

private data class EvidenceFile(
    val file: File,
    val type: EvidenceFileType,
    val sizeBytes: Long,
    val lastModified: Long
)

private fun classifyFile(file: File): EvidenceFileType = when {
    file.name.endsWith(".png") -> EvidenceFileType.SCREENSHOT
    file.name.endsWith(".csv") -> EvidenceFileType.CSV
    file.name.startsWith("evidence_report") -> EvidenceFileType.REPORT_TEXT
    file.name.startsWith("incident_") -> EvidenceFileType.INCIDENT_TEXT
    else -> EvidenceFileType.OTHER
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024    -> "%.1f KB".format(bytes / 1_024.0)
    else              -> "$bytes B"
}

private val dateFormat = SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault())

// ─── Main screen ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvidenceBrowserScreen(
    onBack: () -> Unit,
    onViewTextFile: (String) -> Unit   // passes absolute file path
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var files by remember { mutableStateOf<List<EvidenceFile>>(emptyList()) }
    var isExporting by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<EvidenceFile?>(null) }

    // Load file list
    LaunchedEffect(Unit) {
        files = withContext(Dispatchers.IO) {
            val collector = EvidenceCollector(context)
            collector.listEvidenceFiles()
                .flatMap { top ->
                    if (top.isDirectory) top.walkTopDown().filter { it.isFile }.toList()
                    else listOf(top)
                }
                .map { EvidenceFile(it, classifyFile(it), it.length(), it.lastModified()) }
                .sortedByDescending { it.lastModified }
        }
    }

    // Delete confirmation dialog
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete file?") },
            text = { Text(target.file.name) },
            confirmButton = {
                Button(
                    onClick = {
                        target.file.delete()
                        files = files.filter { it.file.absolutePath != target.file.absolutePath }
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Evidence Files") },
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
        ) {
            // ── Export buttons ─────────────────────────────────────────────
            ExportBar(
                isExporting = isExporting,
                onExportReport = {
                    scope.launch {
                        isExporting = true
                        val dao = ParentalApp.instance.database.incidentDao()
                        val report = withContext(Dispatchers.IO) {
                            EvidenceCollector(context).exportFullReport(dao)
                        }
                        // Refresh list and share
                        files = withContext(Dispatchers.IO) { reloadFiles(context) }
                        shareFile(context, report)
                        isExporting = false
                    }
                },
                onExportCsv = {
                    scope.launch {
                        isExporting = true
                        val dao = ParentalApp.instance.database.incidentDao()
                        val csv = withContext(Dispatchers.IO) {
                            EvidenceCollector(context).exportCsv(dao)
                        }
                        files = withContext(Dispatchers.IO) { reloadFiles(context) }
                        shareFile(context, csv)
                        isExporting = false
                    }
                }
            )

            HorizontalDivider()

            if (files.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No evidence files yet", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Files appear here when incidents are logged\nor when you export a report.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // ── File count header ──────────────────────────────────────
                Text(
                    "${files.size} file${if (files.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(files, key = { it.file.absolutePath }) { ef ->
                        EvidenceFileCard(
                            ef = ef,
                            onView = {
                                if (ef.type == EvidenceFileType.SCREENSHOT) {
                                    shareFile(context, ef.file)   // open image in system viewer
                                } else {
                                    onViewTextFile(ef.file.absolutePath)
                                }
                            },
                            onShare = { shareFile(context, ef.file) },
                            onDelete = { deleteTarget = ef }
                        )
                    }
                }
            }
        }
    }
}

// ─── Export bar ───────────────────────────────────────────────────────────────

@Composable
private fun ExportBar(
    isExporting: Boolean,
    onExportReport: () -> Unit,
    onExportCsv: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = onExportReport,
            enabled = !isExporting,
            modifier = Modifier.weight(1f)
        ) {
            if (isExporting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Export Report", style = MaterialTheme.typography.labelMedium)
            }
        }
        OutlinedButton(
            onClick = onExportCsv,
            enabled = !isExporting,
            modifier = Modifier.weight(1f)
        ) {
            Text("Export CSV", style = MaterialTheme.typography.labelMedium)
        }
    }
}

// ─── File card ────────────────────────────────────────────────────────────────

@Composable
private fun EvidenceFileCard(
    ef: EvidenceFile,
    onView: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val typeLabel = when (ef.type) {
        EvidenceFileType.SCREENSHOT    -> "Screenshot"
        EvidenceFileType.CSV           -> "CSV Export"
        EvidenceFileType.REPORT_TEXT   -> "Full Report"
        EvidenceFileType.INCIDENT_TEXT -> "Incident Record"
        EvidenceFileType.OTHER         -> "File"
    }
    val typeEmoji = when (ef.type) {
        EvidenceFileType.SCREENSHOT    -> "\uD83D\uDDBC\uFE0F"  // 🖼️
        EvidenceFileType.CSV           -> "\uD83D\uDCCA"        // 📊
        EvidenceFileType.REPORT_TEXT   -> "\uD83D\uDCC4"        // 📄
        EvidenceFileType.INCIDENT_TEXT -> "\uD83D\uDCDD"        // 📝
        EvidenceFileType.OTHER         -> "\uD83D\uDCC1"        // 📁
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onView)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Text(typeEmoji, style = MaterialTheme.typography.headlineSmall)

            Spacer(Modifier.width(12.dp))

            // File info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    typeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    ef.file.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${formatSize(ef.sizeBytes)}  •  ${dateFormat.format(Date(ef.lastModified))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Actions
            IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = "Share",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private fun reloadFiles(context: android.content.Context): List<EvidenceFile> {
    return EvidenceCollector(context).listEvidenceFiles()
        .flatMap { top ->
            if (top.isDirectory) top.walkTopDown().filter { it.isFile }.toList()
            else listOf(top)
        }
        .map { EvidenceFile(it, classifyFile(it), it.length(), it.lastModified()) }
        .sortedByDescending { it.lastModified }
}

private fun shareFile(context: android.content.Context, file: File) {
    val mimeType = when {
        file.name.endsWith(".png") -> "image/png"
        file.name.endsWith(".csv") -> "text/csv"
        else -> "text/plain"
    }
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "ParentalORMental — Evidence: ${file.name}")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(intent, "Share evidence").also {
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}
