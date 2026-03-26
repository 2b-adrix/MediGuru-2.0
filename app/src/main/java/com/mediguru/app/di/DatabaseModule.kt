package com.mediguru.app.di

import android.content.Context
import androidx.room.Room
import com.mediguru.app.data.local.AppDatabase
import com.mediguru.app.data.local.DiagnosisDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "mediguru_db"
        ).build()
    }

    @Provides
    fun provideDiagnosisDao(database: AppDatabase): DiagnosisDao {
        return database.diagnosisDao()
    }
}
