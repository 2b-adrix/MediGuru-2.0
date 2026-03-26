package com.mediguru.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.mediguru.app.data.api.GroqApi
import com.mediguru.app.data.local.DiagnosisDao
import com.mediguru.app.data.local.DiagnosisEntity
import com.mediguru.app.data.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiagnosisRepository @Inject constructor(
    private val api: GroqApi,
    private val dao: DiagnosisDao,
    @ApplicationContext private val context: Context
) {

    val allDiagnoses: Flow<List<DiagnosisEntity>> = dao.getAllDiagnoses()

    suspend fun processDiagnosis(
        audioFile: File?,
        imageUri: Uri?
    ): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            // 1. Transcribe Audio if exists
            val transcription = audioFile?.let {
                val requestFile = it.asRequestBody("audio/m4a".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("file", it.name, requestFile)
                val model = "whisper-large-v3".toRequestBody("text/plain".toMediaTypeOrNull())
                api.transcribeAudio(body, model).text
            } ?: ""

            // 2. Analyze with Vision Model
            val encodedImage = imageUri?.let { encodeImage(it) }
            
            // Using llama-3.2-90b-vision-preview as the 11b version has been decommissioned on Groq.
            val modelId = if (encodedImage != null) "llama-3.2-90b-vision-preview" else "llama-3.3-70b-versatile"

            val systemPrompt = "You are MediGuru, a professional medical AI assistant. You specialize in reading medical prescriptions, analyzing X-rays, and interpreting patient symptoms. " +
                               "If an image is provided, perform OCR to read prescriptions or carefully analyze X-rays/scans for abnormalities. " +
                               "Provide clear, medical-grade advice (max 5 sentences). Suggest seeing a real doctor for serious issues. No markdown."

            val userContent: Any = if (encodedImage != null) {
                val promptText = when {
                    transcription.isNotEmpty() -> "Patient symptoms: $transcription. Also analyze the attached medical image (X-ray or prescription) in detail."
                    else -> "Analyze this medical image (X-ray, prescription, or scan) and provide a medical interpretation."
                }
                listOf(
                    ChatContent(type = "text", text = promptText),
                    ChatContent(type = "image_url", image_url = ImageUrl("data:image/jpeg;base64,$encodedImage"))
                )
            } else {
                if (transcription.isNotEmpty()) {
                    "Patient symptoms: $transcription"
                } else {
                    "Provide a general health check-up advice for a healthy individual."
                }
            }

            val chatRequest = ChatRequest(
                model = modelId,
                messages = listOf(
                    ChatMessage(role = "system", content = systemPrompt),
                    ChatMessage(role = "user", content = userContent)
                )
            )

            val chatResponse = api.chatCompletion(chatRequest)
            val doctorResponse = chatResponse.choices.firstOrNull()?.message?.content 
                ?: "I couldn't generate a response. Please try again."

            // 3. Save to local DB for industrial persistence
            val savedImagePath = imageUri?.let { saveImageToInternalStorage(it) }
            dao.insertDiagnosis(
                DiagnosisEntity(
                    transcription = transcription,
                    doctorResponse = doctorResponse,
                    imagePath = savedImagePath
                )
            )

            Result.success(transcription to doctorResponse)
        } catch (e: Exception) {
            Timber.e(e, "Diagnosis processing failed")
            Result.failure(e)
        }
    }

    private fun encodeImage(uri: Uri): String {
        val inputStream = context.contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        val outputStream = ByteArrayOutputStream()
        
        // High resolution for OCR accuracy
        val maxDimension = 1536
        val scaledBitmap = if (bitmap != null && (bitmap.width > maxDimension || bitmap.height > maxDimension)) {
            val ratio = Math.min(maxDimension.toFloat() / bitmap.width, maxDimension.toFloat() / bitmap.height)
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
        } else {
            bitmap
        }
        
        scaledBitmap?.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    private fun saveImageToInternalStorage(uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val file = File(context.filesDir, "med_${System.currentTimeMillis()}.jpg")
            val outputStream = FileOutputStream(file)
            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            Timber.e(e, "Failed to save image")
            null
        }
    }

    suspend fun clearHistory() {
        dao.deleteAll()
    }
}
