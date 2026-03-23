package com.mediguru.app.data.model

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
