package org.app.core.feature.model

import com.google.gson.annotations.SerializedName

data class OpenRouterRequest(
    @SerializedName("model") val model: String,
    @SerializedName("temperature") val temperature: Double = 0.1,
    @SerializedName("max_tokens") val maxTokens: Int = 4000,
    @SerializedName("messages") val messages: List<OpenRouterMessage>,
    @SerializedName("plugins") val plugins: List<OpenRouterPlugin>? = emptyList()
)

data class OpenRouterMessage(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: Any
)

data class OpenRouterPlugin(
    @SerializedName("id") val id: String,
    @SerializedName("pdf") val pdf: PdfPluginConfig? = null
)

data class PdfPluginConfig(@SerializedName("engine") val engine: String)
data class OpenRouterResponse(@SerializedName("choices") val choices: List<OpenRouterChoice>?)
data class OpenRouterChoice(@SerializedName("message") val message: OpenRouterAssistantMessage?)
data class OpenRouterAssistantMessage(@SerializedName("content") val content: String?)