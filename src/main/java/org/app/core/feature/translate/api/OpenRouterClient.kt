package org.app.core.feature.translate.api

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.app.core.BuildConfig
import org.app.core.feature.model.OpenRouterMessage
import org.app.core.feature.model.OpenRouterRequest
import org.app.core.feature.model.OpenRouterResponse
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenRouterClient @Inject constructor(
    private val gson: Gson,
    private val okHttpClient: OkHttpClient,
) {
    private val apiKey: String = BuildConfig.OPEN_ROUTER_API_KEY
    private val baseUrl: String = BuildConfig.BASE_OPEN_ROUTER_URL
    private val appReferer: String = BuildConfig.OPEN_ROUTER_APP_REFERER
    private val appTitle: String = BuildConfig.OPEN_ROUTER_APP_TITLE

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val callTimeoutSeconds = 20L

    fun translateText(model: String, prompt: String, temperature: Double = 0.1, maxTokens: Int = 4000): String {
        val requestPayload = OpenRouterRequest(
            model = model,
            temperature = temperature,
            maxTokens = maxTokens,
            messages = listOf(OpenRouterMessage(role = "user", content = prompt))
        )
        return call(requestPayload)
    }

    private fun call(payload: OpenRouterRequest): String {
        val requestJson = gson.toJson(payload)
        Timber.tag("Log_Translate").d("OpenRouter request json=%s", requestJson)
        val requestBody = requestJson.toRequestBody(jsonMediaType)
        val requestBuilder = Request.Builder()
            .url(baseUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)

        if (appReferer.isNotBlank()) requestBuilder.addHeader("HTTP-Referer", appReferer)
        if (appTitle.isNotBlank()) requestBuilder.addHeader("X-OpenRouter-Title", appTitle)
        val request = requestBuilder.build()
        okHttpClient.newBuilder()
            .callTimeout(callTimeoutSeconds, TimeUnit.SECONDS)
            .build()
            .newCall(request)
            .execute()
            .use { response ->
            val rawBody = response.body?.string().orEmpty()
            Timber.tag("Log_Translate").d("OpenRouter response code=%s body=%s", response.code, rawBody)
            if (!response.isSuccessful) throw IOException("OpenRouter ${response.code}: $rawBody")
            val parsedResponse = try {
                gson.fromJson(rawBody, OpenRouterResponse::class.java)
            } catch (e: Exception) {
                throw IOException("Failed to parse OpenRouter response: ${e.message}", e)
            }
            val content = parsedResponse.choices?.firstOrNull()?.message?.content?.trim().orEmpty()
            if (content.isBlank()) throw IOException("OpenRouter returned empty content")
            return content
        }
    }
}
