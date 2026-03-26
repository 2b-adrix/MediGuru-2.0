package com.mediguru.app.data.model

import com.google.gson.annotations.SerializedName

data class TranscriptionResponse(
    @SerializedName("text") val text: String
)

data class ChatRequest(
    @SerializedName("model") val model: String,
    @SerializedName("messages") val messages: List<ChatMessage>,
    @SerializedName("max_tokens") val maxTokens: Int? = null,
    @SerializedName("temperature") val temperature: Double? = null,
    @SerializedName("stream") val stream: Boolean? = null
)

data class ChatMessage(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: Any // Can be String for text or List<ChatContent> for multi-modal
)

data class ChatContent(
    @SerializedName("type") val type: String,
    @SerializedName("text") val text: String? = null,
    @SerializedName("image_url") val image_url: ImageUrl? = null
)

data class ImageUrl(
    @SerializedName("url") val url: String
)

data class ChatResponse(
    @SerializedName("choices") val choices: List<Choice>
)

data class Choice(
    @SerializedName("message") val message: ChatMessageOutput
)

data class ChatMessageOutput(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: String
)
