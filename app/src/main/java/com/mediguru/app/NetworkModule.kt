package com.mediguru.app

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.io.File
import java.util.concurrent.TimeUnit

interface GroqService {
    @POST("v1/audio/transcriptions")
    @Multipart
    suspend fun transcribeAudio(
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody,
        @Part("language") language: RequestBody? = null,
        @Header("Authorization") authHeader: String
    ): TranscriptionResponse

    @POST("v1/chat/completions")
    suspend fun chatCompletion(
        @Body request: ChatRequest,
        @Header("Authorization") authHeader: String
    ): ChatResponse
}

data class TranscriptionResponse(val text: String)

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>
)

data class ChatMessage(
    val role: String,
    val content: List<ChatContent>
)

data class ChatContent(
    val type: String,
    val text: String? = null,
    val image_url: ImageUrl? = null
)

data class ImageUrl(val url: String)

data class ChatResponse(val choices: List<Choice>)
data class Choice(val message: ChatMessageOutput)
data class ChatMessageOutput(val role: String, val content: String)

object NetworkClient {
    private const val BASE_URL = "https://api.groq.com/openai/"

    private val client = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val groqService: GroqService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GroqService::class.java)
}
