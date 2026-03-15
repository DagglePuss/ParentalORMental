package com.parentalormente.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Each row = one flagged message or event.
 * Stored locally on-device only. No cloud. No harvesting. Period.
 */
@Entity(tableName = "incidents")
data class IncidentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "source_type")
    val sourceType: String, // "SMS", "CALL", "APP_NOTIFICATION"

    @ColumnInfo(name = "sender")
    val sender: String, // phone number or contact name

    @ColumnInfo(name = "content")
    val content: String, // the message text

    @ColumnInfo(name = "severity")
    val severity: Int, // maps to BullyingDetector.Severity.level

    @ColumnInfo(name = "matched_patterns")
    val matchedPatterns: String, // comma-separated pattern matches

    @ColumnInfo(name = "summary")
    val summary: String, // human-readable detection summary

    @ColumnInfo(name = "parent_alerted")
    val parentAlerted: Boolean = false,

    @ColumnInfo(name = "reviewed")
    val reviewed: Boolean = false,

    @ColumnInfo(name = "parent_notes")
    val parentNotes: String = "",

    @ColumnInfo(name = "false_positive")
    val falsePositive: Boolean = false
)
