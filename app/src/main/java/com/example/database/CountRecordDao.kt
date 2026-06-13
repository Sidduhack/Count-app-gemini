package com.example.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CountRecordDao {
    @Query("SELECT * FROM count_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<CountRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: CountRecord): Long

    @Query("DELETE FROM count_records WHERE id = :id")
    suspend fun deleteRecordById(id: Int)

    @Query("DELETE FROM count_records")
    suspend fun deleteAllRecords()
}
