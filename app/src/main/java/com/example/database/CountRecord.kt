package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "count_records")
data class CountRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val itemName: String,
    val count: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val description: String,
    val sampleImagePath: String?,
    val sceneImagePath: String?,
    val detectionsJson: String? // JSON serialized list of detections
)
