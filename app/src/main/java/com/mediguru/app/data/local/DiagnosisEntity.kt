package com.mediguru.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "diagnoses")
data class DiagnosisEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val transcription: String,
    val doctorResponse: String,
    val timestamp: Long = System.currentTimeMillis(),
    val imagePath: String? = null
)
