package com.thanish.jarvispro.ai

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter

class FreeLLMClient(context: Context) {
    private val prefs = context.getSharedPreferences("jarvis_private", Context.MODE_PRIVATE)

    fun saveApiKey(key: String) {
        prefs.edit().putString("freellmapi_key", key.trim()).apply()
    }

    fun hasApiKey(): Boolean =
        !prefs.getString("freellmapi_key", null).isNullOrBlank()

    fun ask(message: String): String {
        val key = prefs.getString("freellmapi_key", null)
            ?: throw IllegalStateException("FreeLLMAPI key not configured")

        val connection = URL("http://127.0.0.1:3001/v1/chat/completions")
            .openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 60000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $key")
            connection.setRequestProperty("Content-Type", "application/json")

            val body = JSONObject().apply {
                put("model", "auto:default")
                put("messages", org.json.JSONArray().put(
                    JSONObject().put("role", "user").put("content", message)
                ))
            }

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use {
                it.write(body.toString())
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream.bufferedReader().use { it.readText() }

            if (code !in 200..299) {
                throw IllegalStateException("FreeLLMAPI HTTP $code: $response")
            }

            return JSONObject(response)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        } finally {
            connection.disconnect()
        }
    }
}
