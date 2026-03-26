package com.mediguru.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [DiagnosisEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun diagnosisDao(): DiagnosisDao
}
