package com.parentalormente.evidence

import android.content.Context
import android.os.Environment
import android.util.Log
import com.parentalormente.data.db.IncidentDao
import com.parentalormente.data.db.IncidentEntity
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileWriter
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Collects, preserves, and exports evidence for prosecutorial or protective action.
 *
 * All evidence is:
 * - Timestamped with device clock
 * - SHA-256 hashed for tamper detection
 * - Stored in a protected app directory
 * - Exportable as structured reports (CSV + human-readable text)
 *
 * This is designed to be admissible — unmodified original data with
 * cryptographic integrity verification.
 */
class EvidenceCollector(private val context: Context) {

    companion object {
        private const val TAG = "EvidenceCollector"
        private const val EVIDENCE_DIR = "evidence"
    }

    private val evidenceDir: File by lazy {
        File(context.filesDir, EVIDENCE_DIR).also { it.mkdirs() }
    }

    /**
     * Archive a flagged incident with full metadata for evidence preservation.
     * Called immediately when an incident is logged.
     */
    fun archiveIncident(incident: IncidentEntity): EvidenceRecord {
        val timestamp = System.currentTimeMillis()
        val record = EvidenceRecord(
            incidentId = incident.id,
            capturedAt = timestamp,
            sourceType = incident.sourceType,
            sender = incident.sender,
            rawContent = incident.content,
            severity = incident.severity,
            matchedPatterns = incident.matchedPatterns,
            detectionSummary = incident.summary,
            deviceTimestamp = incident.timestamp,
            contentHash = sha256(incident.content),
            recordHash = "" // computed after all fields set
        )

        // Compute hash of the entire record for tamper detection
        val fullRecord = record.copy(recordHash = sha256(record.toEvidenceString()))

        // Write to individual evidence file
        val file = File(evidenceDir, "incident_${incident.id}_$timestamp.txt")
        FileWriter(file).use { writer ->
            writer.write(fullRecord.toEvidenceString())
            writer.write("\n\n--- SHA-256 INTEGRITY HASH ---\n")
            writer.write(fullRecord.recordHash)
            writer.write("\n")
        }

        Log.i(TAG, "Evidence archived: incident ${incident.id} -> ${file.name}")
        return fullRecord
    }

    /**
     * Export all incidents as a structured evidence report.
     * Suitable for law enforcement, school administration, or legal counsel.
     */
    suspend fun exportFullReport(dao: IncidentDao): File {
        val incidents = dao.getAllIncidents().first()
        val reportFile = File(evidenceDir, "evidence_report_${System.currentTimeMillis()}.txt")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault())

        FileWriter(reportFile).use { writer ->
            writer.write("=" .repeat(70) + "\n")
            writer.write("PARENTALORMENTE — INCIDENT EVIDENCE REPORT\n")
            writer.write("=" .repeat(70) + "\n\n")
            writer.write("Generated: ${dateFormat.format(Date())}\n")
            writer.write("Total Incidents: ${incidents.size}\n")
            writer.write("Report Integrity Hash: [computed below]\n\n")

            val reportContent = StringBuilder()

            // Summary statistics
            val critical = incidents.count { it.severity == 4 }
            val high = incidents.count { it.severity == 3 }
            val medium = incidents.count { it.severity == 2 }
            val low = incidents.count { it.severity == 1 }

            writer.write("SEVERITY BREAKDOWN:\n")
            writer.write("  CRITICAL (threats/violence): $critical\n")
            writer.write("  HIGH (harassment): $high\n")
            writer.write("  MEDIUM (insults/exclusion): $medium\n")
            writer.write("  LOW (teasing): $low\n\n")

            // Unique senders (potential perpetrators)
            val senders = incidents.map { it.sender }.distinct()
            writer.write("UNIQUE SENDERS (${senders.size}):\n")
            senders.forEach { sender ->
                val count = incidents.count { it.sender == sender }
                val maxSev = incidents.filter { it.sender == sender }.maxOf { it.severity }
                val sevLabel = when (maxSev) {
                    4 -> "CRITICAL"; 3 -> "HIGH"; 2 -> "MEDIUM"; else -> "LOW"
                }
                writer.write("  $sender — $count incident(s), max severity: $sevLabel\n")
            }

            writer.write("\n" + "-".repeat(70) + "\n")
            writer.write("DETAILED INCIDENT LOG\n")
            writer.write("-".repeat(70) + "\n\n")

            // Each incident in full detail
            for ((index, incident) in incidents.withIndex()) {
                val sevLabel = when (incident.severity) {
                    4 -> "CRITICAL"; 3 -> "HIGH"; 2 -> "MEDIUM"; 1 -> "LOW"; else -> "UNKNOWN"
                }
                val block = buildString {
                    append("INCIDENT #${index + 1} (ID: ${incident.id})\n")
                    append("  Date/Time:    ${dateFormat.format(Date(incident.timestamp))}\n")
                    append("  Severity:     $sevLabel\n")
                    append("  Source:       ${incident.sourceType}\n")
                    append("  Sender:       ${incident.sender}\n")
                    append("  Detection:    ${incident.summary}\n")
                    append("  Patterns:     ${incident.matchedPatterns.replace("|", ", ")}\n")
                    append("  Reviewed:     ${if (incident.reviewed) "Yes" else "No"}\n")
                    append("  False Pos:    ${if (incident.falsePositive) "Yes" else "No"}\n")
                    if (incident.parentNotes.isNotBlank()) {
                        append("  Parent Notes: ${incident.parentNotes}\n")
                    }
                    append("  Content Hash: ${sha256(incident.content)}\n")
                    append("\n  --- FULL MESSAGE CONTENT ---\n")
                    append("  ${incident.content}\n")
                    append("  --- END CONTENT ---\n\n")
                }
                writer.write(block)
                reportContent.append(block)
            }

            // Compute and write report integrity hash
            val reportHash = sha256(reportContent.toString())
            writer.write("=" .repeat(70) + "\n")
            writer.write("REPORT INTEGRITY HASH (SHA-256):\n")
            writer.write(reportHash + "\n")
            writer.write("=" .repeat(70) + "\n")
            writer.write("\nThis hash can be used to verify this report has not been tampered with.\n")
            writer.write("Any modification to the incident data above will produce a different hash.\n")
        }

        Log.i(TAG, "Full evidence report exported: ${reportFile.name}")
        return reportFile
    }

    /**
     * Export incidents as CSV for spreadsheet analysis or database import.
     */
    suspend fun exportCsv(dao: IncidentDao): File {
        val incidents = dao.getAllIncidents().first()
        val csvFile = File(evidenceDir, "incidents_${System.currentTimeMillis()}.csv")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        FileWriter(csvFile).use { writer ->
            // Header
            writer.write("id,timestamp,date_time,source,sender,severity,severity_label,summary,matched_patterns,content,reviewed,false_positive,parent_notes,content_hash\n")

            for (incident in incidents) {
                val sevLabel = when (incident.severity) {
                    4 -> "CRITICAL"; 3 -> "HIGH"; 2 -> "MEDIUM"; 1 -> "LOW"; else -> "UNKNOWN"
                }
                writer.write(buildString {
                    append("${incident.id},")
                    append("${incident.timestamp},")
                    append("${dateFormat.format(Date(incident.timestamp))},")
                    append("${incident.sourceType},")
                    append("${csvEscape(incident.sender)},")
                    append("${incident.severity},")
                    append("$sevLabel,")
                    append("${csvEscape(incident.summary)},")
                    append("${csvEscape(incident.matchedPatterns)},")
                    append("${csvEscape(incident.content)},")
                    append("${incident.reviewed},")
                    append("${incident.falsePositive},")
                    append("${csvEscape(incident.parentNotes)},")
                    append(sha256(incident.content))
                    append("\n")
                })
            }
        }

        Log.i(TAG, "CSV export: ${csvFile.name}")
        return csvFile
    }

    /**
     * Get the evidence directory for sharing/transfer.
     */
    fun getEvidenceDirectory(): File = evidenceDir

    /**
     * List all archived evidence files.
     */
    fun listEvidenceFiles(): List<File> {
        return evidenceDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun csvEscape(value: String): String {
        return "\"${value.replace("\"", "\"\"").replace("\n", " ")}\""
    }
}

/**
 * Immutable evidence record — represents a single archived incident.
 */
data class EvidenceRecord(
    val incidentId: Long,
    val capturedAt: Long,
    val sourceType: String,
    val sender: String,
    val rawContent: String,
    val severity: Int,
    val matchedPatterns: String,
    val detectionSummary: String,
    val deviceTimestamp: Long,
    val contentHash: String,
    val recordHash: String
) {
    fun toEvidenceString(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault())
        return buildString {
            append("PARENTALORMENTE EVIDENCE RECORD\n")
            append("================================\n")
            append("Incident ID:     $incidentId\n")
            append("Captured At:     ${dateFormat.format(Date(capturedAt))}\n")
            append("Device Time:     ${dateFormat.format(Date(deviceTimestamp))}\n")
            append("Source Type:     $sourceType\n")
            append("Sender:          $sender\n")
            append("Severity:        $severity\n")
            append("Detection:       $detectionSummary\n")
            append("Matched:         $matchedPatterns\n")
            append("Content SHA-256: $contentHash\n")
            append("\n--- RAW CONTENT ---\n")
            append(rawContent)
            append("\n--- END RAW CONTENT ---\n")
        }
    }
}
