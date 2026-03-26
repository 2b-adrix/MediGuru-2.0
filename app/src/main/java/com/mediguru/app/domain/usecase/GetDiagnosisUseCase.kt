package com.mediguru.app.domain.usecase

import android.net.Uri
import com.mediguru.app.data.repository.DiagnosisRepository
import com.mediguru.app.ui.DiagnosisStatus
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject

class GetDiagnosisUseCase @Inject constructor(
    private val repository: DiagnosisRepository
) {
    fun stream(
        audioFile: File?,
        imageUri: Uri?,
        onStatusChange: (DiagnosisStatus) -> Unit
    ): Flow<String> {
        return repository.processDiagnosisStream(audioFile, imageUri, onStatusChange)
    }

    suspend operator fun invoke(
        audioFile: File?,
        imageUri: Uri?,
        onStatusChange: (DiagnosisStatus) -> Unit
    ): Result<Pair<String, String>> {
        return repository.processDiagnosis(audioFile, imageUri, onStatusChange)
    }
}
