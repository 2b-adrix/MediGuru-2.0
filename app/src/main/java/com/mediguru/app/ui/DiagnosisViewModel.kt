package com.mediguru.app.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediguru.app.data.repository.DiagnosisRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class DiagnosisState(
    val transcription: String = "",
    val doctorResponse: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedImageUri: Uri? = null,
    val isRecording: Boolean = false,
    val audioFile: File? = null
)

@HiltViewModel
class DiagnosisViewModel @Inject constructor(
    private val repository: DiagnosisRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiagnosisState())
    val uiState = _uiState.asStateFlow()

    fun onImageSelected(uri: Uri?) {
        _uiState.value = _uiState.value.copy(selectedImageUri = uri)
    }

    fun onRecordingStateChanged(isRecording: Boolean, file: File?) {
        _uiState.value = _uiState.value.copy(isRecording = isRecording, audioFile = file)
    }

    fun processDiagnosis() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            repository.processDiagnosis(
                audioFile = _uiState.value.audioFile,
                imageUri = _uiState.value.selectedImageUri
            ).onSuccess { (trans, resp) ->
                _uiState.value = _uiState.value.copy(
                    transcription = trans,
                    doctorResponse = resp,
                    isLoading = false
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = error.localizedMessage ?: "Unknown error occurred"
                )
            }
        }
    }
}
