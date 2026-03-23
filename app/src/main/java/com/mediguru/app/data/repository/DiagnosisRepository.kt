package com.mediguru.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.mediguru.app.data.api.GroqApi
import com.mediguru.app.data.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiagnosisRepository @Inject constructor(
    private val api: GroqApi,
    @ApplicationContext private val context: Context
) {

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
            
            // Using the Llama 3.2 11B Vision model which is currently stable on Groq.
            // Note: Groq occasionally updates model availability.
            val modelId = if (encodedImage != null) "llama-3.2-11b-vision-preview" else "llama-3.3-70b-versatile"

            val chatRequest = ChatRequest(
                model = modelId,
                messages = listOf(
                    ChatMessage(
                        role = "system",
                        content = listOf(
                            ChatContent(
                                type = "text",
                                text = "You are a professional doctor. Analyze symptoms and images. Provide concise advice (max 2-3 sentences). No markdown."
                            )
                        )
                    ),
                    ChatMessage(
                        role = "user",
                        content = buildList {
                            val promptText = if (transcription.isEmpty()) "Analyze this medical image." else "Patient symptoms: $transcription"
                            add(ChatContent(type = "text", text = promptText))
                            if (encodedImage != null) {
                                add(ChatContent(type = "image_url", image_url = ImageUrl("data:image/jpeg;base64,$encodedImage")))
                            }
                        }
                    )
                )
            )

            val chatResponse = api.chatCompletion(chatRequest)
            val doctorResponse = chatResponse.choices.firstOrNull()?.message?.content 
                ?: "I couldn't generate a response. Please try again."

            Result.success(transcription to doctorResponse)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun encodeImage(uri: Uri): String {
        val inputStream = context.contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        val outputStream = ByteArrayOutputStream()
        
        // Resize image to ensure it's well within Groq's payload limits.
        // Groq prefers images around 512-1024px.
        val maxDimension = 768
        val scaledBitmap = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
            val ratio = Math.min(maxDimension.toFloat() / bitmap.width, maxDimension.toFloat() / bitmap.height)
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
        } else {
            bitmap
        }
        
        // 50% quality is usually enough for AI analysis and drastically reduces size
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }
}
