package com.parentalormente.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface IncidentDao {

    @Insert
    suspend fun insert(incident: IncidentEntity): Long

    @Update
    suspend fun update(incident: IncidentEntity)

    @Query("SELECT * FROM incidents ORDER BY timestamp DESC")
    fun getAllIncidents(): Flow<List<IncidentEntity>>

    @Query("SELECT * FROM incidents WHERE id = :id")
    suspend fun getById(id: Long): IncidentEntity?

    @Query("SELECT * FROM incidents WHERE severity >= :minSeverity ORDER BY timestamp DESC")
    fun getBySeverity(minSeverity: Int): Flow<List<IncidentEntity>>

    @Query("SELECT * FROM incidents WHERE reviewed = 0 ORDER BY severity DESC, timestamp DESC")
    fun getUnreviewed(): Flow<List<IncidentEntity>>

    @Query("SELECT COUNT(*) FROM incidents WHERE reviewed = 0")
    fun getUnreviewedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM incidents WHERE severity >= 3 AND reviewed = 0")
    fun getHighSeverityUnreviewedCount(): Flow<Int>

    @Query("SELECT * FROM incidents WHERE sender = :sender ORDER BY timestamp DESC")
    fun getBySender(sender: String): Flow<List<IncidentEntity>>

    @Query("SELECT COUNT(*) FROM incidents WHERE timestamp > :since")
    fun getCountSince(since: Long): Flow<Int>

    @Query("DELETE FROM incidents WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE incidents SET reviewed = 1, parent_notes = :notes WHERE id = :id")
    suspend fun markReviewed(id: Long, notes: String)

    @Query("UPDATE incidents SET false_positive = 1, reviewed = 1 WHERE id = :id")
    suspend fun markFalsePositive(id: Long)
}
