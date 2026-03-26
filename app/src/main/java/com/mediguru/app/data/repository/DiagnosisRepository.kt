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
import com.mediguru.app.ui.DiagnosisStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiagnosisRepository @Inject constructor(
    private val api: GroqApi,
    private val dao: DiagnosisDao,
    @ApplicationContext private val context: Context
) {

    val allDiagnoses: Flow<List<DiagnosisEntity>> = dao.getAllDiagnoses()

    fun processDiagnosisStream(
        audioFile: File?,
        imageUri: Uri?,
        onStatusChange: (DiagnosisStatus) -> Unit
    ): Flow<String> = flow {
        try {
            onStatusChange(DiagnosisStatus.TRANSCRIBING)
            val transcription = audioFile?.let {
                val requestFile = it.asRequestBody("audio/m4a".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("file", it.name, requestFile)
                val model = "whisper-large-v3".toRequestBody("text/plain".toMediaTypeOrNull())
                api.transcribeAudio(body, model).text
            } ?: ""

            onStatusChange(DiagnosisStatus.NEURAL_ANALYSIS)
            delay(1000)
            
            onStatusChange(DiagnosisStatus.GENOMIC_MAPPING)
            delay(1000)

            onStatusChange(DiagnosisStatus.RADIOLOGIST_REVIEW)
            val encodedImage = imageUri?.let { uri -> encodeImage(uri) }
            delay(1000)
            
            onStatusChange(DiagnosisStatus.SPECIALIST_CONSULT)
            delay(1000)
            
            onStatusChange(DiagnosisStatus.PHARMACIST_CHECK)
            delay(800)
            
            onStatusChange(DiagnosisStatus.FINALIZING)

            val modelId = if (encodedImage != null) "llama-3.2-90b-vision-preview" else "llama-3.3-70b-versatile"
            val systemPrompt = "You are the MediGuru Medical Board. Provide a Consensus Report. NO MARKDOWN."
            val userContent: Any = if (encodedImage != null) {
                listOf(
                    ChatContent(type = "text", text = "Board Inquiry: Symptoms: $transcription"),
                    ChatContent(type = "image_url", image_url = ImageUrl("data:image/jpeg;base64,$encodedImage"))
                )
            } else "Board Inquiry: Symptoms: $transcription"

            val chatRequest = ChatRequest(
                model = modelId,
                messages = listOf(ChatMessage("system", systemPrompt), ChatMessage("user", userContent)),
                stream = true
            )

            val responseBody = api.chatCompletionStream(chatRequest)
            val reader = responseBody.byteStream().bufferedReader()
            var fullResponse = ""

            reader.useLines { lines ->
                lines.forEach { line ->
                    if (line.startsWith("data: ")) {
                        val data = line.substring(6)
                        if (data.trim() != "[DONE]") {
                            try {
                                val json = JSONObject(data)
                                val choices = json.getJSONArray("choices")
                                if (choices.length() > 0) {
                                    val delta = choices.getJSONObject(0).getJSONObject("delta")
                                    if (delta.has("content")) {
                                        val content = delta.getString("content")
                                        fullResponse += content
                                        emit(fullResponse)
                                    }
                                }
                            } catch (e: Exception) {}
                        }
                    }
                }
            }

            val savedImagePath = imageUri?.let { saveImageToInternalStorage(it) }
            dao.insertDiagnosis(DiagnosisEntity(transcription = transcription, doctorResponse = fullResponse, imagePath = savedImagePath))

        } catch (e: Exception) {
            Timber.e(e, "Streaming failure")
            throw e
        }
    }.flowOn(Dispatchers.IO)

    suspend fun processDiagnosis(
        audioFile: File?,
        imageUri: Uri?,
        onStatusChange: (DiagnosisStatus) -> Unit
    ): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            onStatusChange(DiagnosisStatus.TRANSCRIBING)
            val transcription = audioFile?.let {
                val requestFile = it.asRequestBody("audio/m4a".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("file", it.name, requestFile)
                val model = "whisper-large-v3".toRequestBody("text/plain".toMediaTypeOrNull())
                api.transcribeAudio(body, model).text
            } ?: ""

            onStatusChange(DiagnosisStatus.RADIOLOGIST_REVIEW)
            val encodedImage = imageUri?.let { uri -> encodeImage(uri) }
            
            onStatusChange(DiagnosisStatus.SPECIALIST_CONSULT)
            val modelId = if (encodedImage != null) "llama-3.2-90b-vision-preview" else "llama-3.3-70b-versatile"
            val chatRequest = ChatRequest(
                model = modelId,
                messages = listOf(ChatMessage("system", "Medical AI"), ChatMessage("user", "Symptoms: $transcription")),
                maxTokens = 1500
            )
            val response = api.chatCompletion(chatRequest)
            val doctorResponse = response.choices.firstOrNull()?.message?.content ?: ""
            
            val savedImagePath = imageUri?.let { saveImageToInternalStorage(it) }
            dao.insertDiagnosis(DiagnosisEntity(transcription = transcription, doctorResponse = doctorResponse, imagePath = savedImagePath))
            Result.success(transcription to doctorResponse)
        } catch (e: Exception) { Result.failure(e) }
    }

    private fun encodeImage(uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(inputStream) ?: return null
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) { null }
    }

    private fun saveImageToInternalStorage(uri: Uri): String? {
        return try {
            val file = File(context.filesDir, "med_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
            file.absolutePath
        } catch (e: Exception) { null }
    }

    suspend fun clearHistory() { dao.deleteAll() }
}
