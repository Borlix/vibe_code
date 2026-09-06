package com.example.translatekeyboard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Calls Groq's free, OpenAI-compatible chat completions endpoint to translate
 * text. Using a chat model for translation (rather than a dedicated translate
 * API) means no separate translation API key is needed — the same free Groq
 * key covers it, and the model tends to handle context/idiom reasonably well.
 */
object TranslationClient {

    private const val GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"
    private const val MODEL = "llama-3.3-70b-versatile"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /**
     * Translates [text] from [sourceLang] to [targetLang] using the given [apiKey].
     * Runs on Dispatchers.IO and is safe to call from a coroutine on the main thread.
     * Throws an exception with a readable message on any failure — caller should
     * catch and show it in the UI rather than crash the keyboard.
     */
    suspend fun translate(apiKey: String, sourceLang: String, targetLang: String, text: String): String {
        if (text.isBlank()) return ""
        if (apiKey.isBlank()) throw IllegalStateException("No Groq API key set. Open keyboard settings.")

        return withContext(Dispatchers.IO) {
            val systemPrompt =
                "You are a translation engine. Translate the user's text from $sourceLang to $targetLang. " +
                "Respond with ONLY the translated text, no quotes, no explanation, no extra commentary."

            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", text)
                })
            }

            val payload = JSONObject().apply {
                put("model", MODEL)
                put("temperature", 0.3)
                put("max_tokens", 500)
                put("messages", messages)
            }

            val request = Request.Builder()
                .url(GROQ_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("Groq API error (${response.code}): ${body.take(200)}")
                }
                val json = JSONObject(body)
                json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
            }
        }
    }
}
