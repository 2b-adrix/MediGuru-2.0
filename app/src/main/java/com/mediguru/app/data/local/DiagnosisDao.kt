package com.mediguru.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DiagnosisDao {
    @Query("SELECT * FROM diagnoses ORDER BY timestamp DESC")
    fun getAllDiagnoses(): Flow<List<DiagnosisEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiagnosis(diagnosis: DiagnosisEntity)

    @Delete
    suspend fun deleteDiagnosis(diagnosis: DiagnosisEntity)

    @Query("DELETE FROM diagnoses")
    suspend fun deleteAll()
}
