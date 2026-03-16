package com.parentalormente.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Scrollable in-app viewer for plain-text evidence files (.txt, .csv).
 *
 * Shows raw file content in a monospace font so the tamper-evident
 * hash lines and structured formatting are readable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvidenceFileViewerScreen(
    filePath: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val file = remember { File(filePath) }

    var content by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(filePath) {
        content = withContext(Dispatchers.IO) {
            runCatching { file.readText() }.getOrNull()
        }
        if (content == null) error = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(file.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        if (content != null) {
                            Text(
                                "${content!!.lines().size} lines  •  ${(file.length() / 1024).coerceAtLeast(1)} KB",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = if (file.name.endsWith(".csv")) "text/csv" else "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, "Evidence: ${file.name}")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(
                            Intent.createChooser(shareIntent, "Share evidence")
                                .also { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        )
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share file")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Could not read file", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(filePath, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                content == null -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        Text(
                            text = content!!,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
