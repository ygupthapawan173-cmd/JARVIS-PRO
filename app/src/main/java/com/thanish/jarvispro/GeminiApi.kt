package com.thanish.jarvispro

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object GeminiApi {
    private const val MODEL = "gemini-3.5-flash"

    fun generate(apiKey: String, userPrompt: String, codeMode: Boolean): String {
        val system = if (codeMode) {
            """
You are JARVIS Self-Coding Assistant. Produce a safe Android project file bundle.
Return ONLY valid JSON, with this exact shape:
{"files":[{"path":"app/src/main/java/com/thanish/jarvispro/Example.kt","content":"complete source code"}],"notes":"short summary"}
Do not use Markdown or code fences. Escape JSON strings correctly.
Only create Kotlin source files under app/src/main/java/com/thanish/jarvispro/ and XML layout files under app/src/main/res/layout/.
Use package com.thanish.jarvispro for Kotlin files. Prefer Android SDK native Activity and widgets unless the user asks otherwise.
Never create Gradle scripts, manifest files, shell scripts, network downloaders, or executable commands.
Do not generate malware, credential theft, stealth, persistence, destructive actions, or code that bypasses user consent.
Never claim code was executed, tested, installed, or built. Files are staged for review only and are not executed.
Limit output to at most 8 files and keep each file under 100 KB.
            """.trimIndent()
        } else {
            "You are JARVIS, a helpful bilingual Telugu and English assistant. Understand Telugu, English, and mixed Telugu-English. Reply naturally in the language the user uses. Be concise and accurate."
        }
        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", userPrompt)))))
            .put("generationConfig", JSONObject().put("temperature", if (codeMode) 0.15 else 0.55))
        val connection = (URL("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20000
            readTimeout = 60000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val response = stream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            if (responseCode !in 200..299) {
                val message = json.optJSONObject("error")?.optString("message") ?: "HTTP $responseCode"
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
