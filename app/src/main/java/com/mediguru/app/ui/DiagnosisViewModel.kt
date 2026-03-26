package com.mediguru.app.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediguru.app.data.local.DiagnosisEntity
import com.mediguru.app.data.repository.DiagnosisRepository
import com.mediguru.app.domain.usecase.GetDiagnosisUseCase
import com.mediguru.app.util.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class DiagnosisStatus {
    IDLE,
    TRANSCRIBING,
    NEURAL_ANALYSIS,
    GENOMIC_MAPPING,
    RADIOLOGIST_REVIEW,
    SPECIALIST_CONSULT,
    PHARMACIST_CHECK,
    FINALIZING,
    SUCCESS,
    ERROR
}

enum class BioSystem {
    NONE,
    NERVOUS,
    RESPIRATORY,
    DIGESTIVE,
    CARDIOVASCULAR,
    MUSCULOSKELETAL,
    DERMATOLOGICAL
}

data class Specialist(
    val name: String,
    val role: String,
    val icon: String // We'll map this to an Icon in the UI
)

data class RecoveryMilestone(
    val day: Int,
    val action: String,
    val bioTarget: String
)

data class BioVitals(
    val inflammation: Float = 0f,
    val hydration: Float = 0.8f,
    val recoveryPotential: Float = 0.5f,
    val painIntensity: Float = 0f
)

data class BoardMessage(
    val agent: String,
    val message: String
)

data class DiagnosisState(
    val transcription: String = "",
    val doctorResponse: String = "",
    val status: DiagnosisStatus = DiagnosisStatus.IDLE,
    val error: UiText? = null,
    val selectedImageUri: Uri? = null,
    val isRecording: Boolean = false,
    val audioFile: File? = null,
    val vitals: BioVitals = BioVitals(),
    val targetSystem: BioSystem = BioSystem.NONE,
    val trajectory: List<RecoveryMilestone> = emptyList(),
    val boardLog: List<BoardMessage> = emptyList(),
    val geneticRiskScore: Float = 0f,
    val neuralPulseSpeed: Int = 3000,
    val activeSpecialists: List<Specialist> = listOf(
        Specialist("Radiologist", "Scan Expert", "Visibility"),
        Specialist("Physician", "General Medicine", "Person"),
        Specialist("Pharmacist", "Clinical Chemist", "Medication")
    )
)

@HiltViewModel
class DiagnosisViewModel @Inject constructor(
    private val repository: DiagnosisRepository,
    private val getDiagnosisUseCase: GetDiagnosisUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiagnosisState())
    val uiState = _uiState.asStateFlow()

    val diagnosisHistory: StateFlow<List<DiagnosisEntity>> = repository.allDiagnoses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onImageSelected(uri: Uri?) {
        _uiState.value = _uiState.value.copy(selectedImageUri = uri)
    }

    fun onRecordingStateChanged(isRecording: Boolean, file: File?) {
        _uiState.value = _uiState.value.copy(isRecording = isRecording, audioFile = file)
        if (isRecording) {
            _uiState.value = _uiState.value.copy(status = DiagnosisStatus.NEURAL_ANALYSIS, neuralPulseSpeed = 1000)
        } else {
            _uiState.value = _uiState.value.copy(neuralPulseSpeed = 3000)
        }
    }
    
    fun onSymptomTapped(symptom: String) {
        val currentText = _uiState.value.transcription
        val newText = if (currentText.isBlank()) symptom else "$currentText, $symptom"
        
        val system = when {
            symptom.contains("Headache", true) -> BioSystem.NERVOUS
            symptom.contains("Cough", true) -> BioSystem.RESPIRATORY
            symptom.contains("Nausea", true) -> BioSystem.DIGESTIVE
            symptom.contains("Chest", true) -> BioSystem.CARDIOVASCULAR
            symptom.contains("Pain", true) -> BioSystem.MUSCULOSKELETAL
            symptom.contains("Rash", true) -> BioSystem.DERMATOLOGICAL
            else -> _uiState.value.targetSystem
        }

        val specialists = mutableListOf(
            Specialist("Radiologist", "Scan Expert", "Visibility"),
            when(system) {
                BioSystem.DERMATOLOGICAL -> Specialist("Dermatologist", "Skin Specialist", "Face")
                BioSystem.CARDIOVASCULAR -> Specialist("Cardiologist", "Heart Specialist", "Favorite")
                BioSystem.NERVOUS -> Specialist("Neurologist", "Neural Specialist", "Psychology")
                else -> Specialist("Physician", "General Medicine", "Person")
            },
            Specialist("Pharmacist", "Clinical Chemist", "Medication")
        )

        _uiState.value = _uiState.value.copy(
            transcription = newText,
            targetSystem = system,
            activeSpecialists = specialists
        )
    }

    private fun addBoardMessage(agent: String, message: String) {
        val currentLog = _uiState.value.boardLog.toMutableList()
        currentLog.add(BoardMessage(agent, message))
        _uiState.value = _uiState.value.copy(boardLog = currentLog)
    }

    fun processDiagnosis() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                status = DiagnosisStatus.TRANSCRIBING, 
                error = null,
                doctorResponse = "",
                trajectory = emptyList(),
                boardLog = emptyList(),
                vitals = BioVitals(0.2f, 0.7f, 0.4f, 0.3f),
                geneticRiskScore = 0f,
                neuralPulseSpeed = 800
            )
            
            getDiagnosisUseCase.stream(
                audioFile = _uiState.value.audioFile,
                imageUri = _uiState.value.selectedImageUri,
                onStatusChange = { status ->
                    _uiState.value = _uiState.value.copy(status = status)
                    viewModelScope.launch {
                        when(status) {
                            DiagnosisStatus.NEURAL_ANALYSIS -> {
                                addBoardMessage("Neural Engine", "Extracting biometric frequency from vocal data...")
                                _uiState.value = _uiState.value.copy(vitals = _uiState.value.vitals.copy(painIntensity = 0.6f))
                            }
                            DiagnosisStatus.GENOMIC_MAPPING -> {
                                addBoardMessage("Genome Agent", "Identifying hereditary risk factors...")
                                _uiState.value = _uiState.value.copy(vitals = _uiState.value.vitals.copy(recoveryPotential = 0.6f), geneticRiskScore = 0.45f)
                            }
                            DiagnosisStatus.RADIOLOGIST_REVIEW -> {
                                addBoardMessage("Radiologist", "Analyzing structural anomalies in scan...")
                                _uiState.value = _uiState.value.copy(vitals = _uiState.value.vitals.copy(inflammation = 0.55f))
                            }
                            DiagnosisStatus.SPECIALIST_CONSULT -> {
                                val specialist = _uiState.value.activeSpecialists[1].name
                                addBoardMessage(specialist, "Evaluating systemic impact based on $specialist criteria...")
                                _uiState.value = _uiState.value.copy(vitals = _uiState.value.vitals.copy(recoveryPotential = 0.85f))
                            }
                            DiagnosisStatus.PHARMACIST_CHECK -> {
                                addBoardMessage("Pharmacist", "Optimizing pharmacological compatibility...")
                                _uiState.value = _uiState.value.copy(vitals = _uiState.value.vitals.copy(hydration = 0.95f))
                            }
                            else -> {}
                        }
                    }
                }
            ).catch { error ->
                _uiState.value = _uiState.value.copy(
                    status = DiagnosisStatus.ERROR,
                    error = UiText.DynamicString(error.localizedMessage ?: "Consensus failure"),
                    neuralPulseSpeed = 3000
                )
            }.collect { text ->
                _uiState.value = _uiState.value.copy(
                    doctorResponse = text,
                    status = DiagnosisStatus.SUCCESS,
                    neuralPulseSpeed = 3000
                )
                if (_uiState.value.trajectory.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        trajectory = listOf(
                            RecoveryMilestone(1, "Neural Homeostasis & Recovery", "Bio-Stabilization"),
                            RecoveryMilestone(2, "Metabolic Correction phase", "Systemic Reset"),
                            RecoveryMilestone(3, "Optimal Peak performance", "Vulnerability Minimalized")
                        )
                    )
                }
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }
}
