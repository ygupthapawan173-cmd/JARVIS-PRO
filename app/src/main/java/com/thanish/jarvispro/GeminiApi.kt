package com.thanish.jarvispro

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object GeminiApi {
    private const val MODEL = "gemini-3.5-flash"

    fun generate(apiKey: String, userPrompt: String, codeMode: Boolean): String {
        val system = if (codeMode) {
            "You are JARVIS Self-Coding Assistant. Generate safe, maintainable code as requested. Return code and a short explanation. Never claim code was executed or tested. Do not generate malware, credential theft, stealth, persistence, or destructive actions. Generated code is only saved as text for user review; it is not executed."
        } else {
            "You are JARVIS, a helpful bilingual Telugu and English assistant. Understand Telugu, English, and mixed Telugu-English. Reply naturally in the language the user uses. Be concise and accurate."
        }
        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", userPrompt)))))
            .put("generationConfig", JSONObject().put("temperature", if (codeMode) 0.2 else 0.55))
        val connection = (URL("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20000
            readTimeout = 60000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            if (code !in 200..299) {
                val message = json.optJSONObject("error")?.optString("message") ?: "HTTP $code"
                throw IllegalStateException(message.take(500))
            }
            val parts = json.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                ?: throw IllegalStateException("Empty AI response")
            val result = buildString {
                for (i in 0 until parts.length()) append(parts.optJSONObject(i)?.optString("text").orEmpty())
            }.trim()
            if (result.isBlank()) throw IllegalStateException("AI returned empty text")
            return result
        } finally {
            connection.disconnect()
        }
    }
}
