package com.capture.engine.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.capture.engine.model.CaptureEntity
import com.capture.engine.model.DeliveryStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptureDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(capture: CaptureEntity): Long

    @Update
    suspend fun update(capture: CaptureEntity)

    @Query("SELECT * FROM captures ORDER BY timestamp DESC")
    fun getAllCapturesFlow(): Flow<List<CaptureEntity>>

    @Query("SELECT * FROM captures WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CaptureEntity?

    @Query("SELECT * FROM captures WHERE status = :status ORDER BY timestamp ASC")
    suspend fun getByStatus(status: DeliveryStatus): List<CaptureEntity>

    @Query("DELETE FROM captures WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM captures")
    suspend fun clearAll()
}
