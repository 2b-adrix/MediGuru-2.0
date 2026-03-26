package com.mediguru.app.data.api

import com.mediguru.app.data.model.ChatRequest
import com.mediguru.app.data.model.ChatResponse
import com.mediguru.app.data.model.TranscriptionResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.*

interface GroqApi {
    @POST("v1/audio/transcriptions")
    @Multipart
    suspend fun transcribeAudio(
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody
    ): TranscriptionResponse

    @POST("v1/chat/completions")
    @Streaming
    suspend fun chatCompletionStream(
        @Body request: ChatRequest
    ): ResponseBody

    @POST("v1/chat/completions")
    suspend fun chatCompletion(
        @Body request: ChatRequest
    ): ChatResponse
}
