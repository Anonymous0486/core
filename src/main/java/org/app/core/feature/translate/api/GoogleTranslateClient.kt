package org.app.core.feature.translate.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import org.app.core.BuildConfig
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleTranslateClient @Inject constructor(
    private val gson: Gson,
    private val okHttpClient: OkHttpClient,
) {
    private val baseUrl: String = BuildConfig.BASE_GOOGLE_TRANSLATE_URL
    private val callTimeoutSeconds = 12L

    fun translate(text: String, fromLang: String, toLang: String): String {
        if (text.isBlank()) return text
        val resolvedFromLang = fromLang
        val resolvedToLang = toLang

        val encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8.name())
        val url = buildString {
            append(baseUrl)
            append("?client=gtx&dt=t&dj=1")
            append("&sl=$resolvedFromLang")
            append("&tl=$resolvedToLang")
            append("&q=$encodedText")
        }

        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/42.0.2311.135 Safari/537.36"
            )
            .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .get()
            .build()

        okHttpClient.newBuilder()
            .callTimeout(callTimeoutSeconds, TimeUnit.SECONDS)
            .build()
            .newCall(request)
            .execute()
            .use { response ->
            val rawBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Google Translate ${response.code}: $rawBody")
            if (rawBody.isBlank()) throw IOException("Empty response from Google Translate")
            val parsed = try {
                gson.fromJson(rawBody, JsonObject::class.java)
            } catch (e: Exception) {
                throw IOException("Failed to parse Google Translate response: ${e.message}", e)
            }

            val sentences = parsed.getAsJsonArray("sentences") ?: throw IOException("Google Translate response missing 'sentences'")
            val translated = sentences.joinToString(separator = "") { sentence ->
                sentence.asJsonObject.get("trans")?.asString.orEmpty()
            }
            if (translated.isBlank()) throw IOException("Google Translate returned empty translation")
            return translated
        }
    }

//    private fun normalizeLanguageCode(languageCode: String): String {
//        val raw = languageCode.trim()
//        if (raw.equals("auto", ignoreCase = true)) return "auto"
//        return TranslateLanguage.from(raw).code
//    }
}
