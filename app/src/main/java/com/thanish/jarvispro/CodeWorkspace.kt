package com.thanish.jarvispro

import android.content.Context
import org.json.JSONObject
import java.io.File

object CodeWorkspace {
    private const val MAX_FILES = 8
    private const val MAX_FILE_CHARS = 100_000

    fun save(context: Context, generatedText: String): File {
        val directory = File(context.filesDir, "workspace").apply { mkdirs() }
        val safeText = generatedText.trim()
        require(safeText.isNotBlank()) { "No generated code to save" }
        val file = File(directory, "generated_${System.currentTimeMillis()}.txt")
        file.writeText(safeText, Charsets.UTF_8)
        return file
    }

    fun parseFiles(response: String): Map<String, String> {
        val start = response.indexOf('{')
        val end = response.lastIndexOf('}')
        require(start >= 0 && end > start) { "AI did not return a JSON file bundle" }
        val root = JSONObject(response.substring(start, end + 1))
        val array = root.optJSONArray("files") ?: throw IllegalArgumentException("JSON is missing files[]")
        require(array.length() in 1..MAX_FILES) { "Bundle must contain 1 to $MAX_FILES files" }

        val result = linkedMapOf<String, String>()
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val path = item.getString("path").trim()
            val content = item.getString("content")
            validatePath(path)
            require(content.isNotBlank()) { "Empty file: $path" }
            require(content.length <= MAX_FILE_CHARS) { "File too large: $path" }
            require(!result.containsKey(path)) { "Duplicate path: $path" }
            result[path] = content
        }
        return result
    }

    private fun validatePath(path: String) {
        require(path.isNotBlank() && !path.startsWith("/") && !path.contains("..") && !path.contains('\\')) {
            "Unsafe file path"
        }
        val kotlinAllowed = path.startsWith("app/src/main/java/com/thanish/jarvispro/") && path.endsWith(".kt")
        val xmlAllowed = path.startsWith("app/src/main/res/layout/") && path.endsWith(".xml")
        require(kotlinAllowed || xmlAllowed) { "File path is outside the allowed Kotlin/XML folders: $path" }
    }

    fun saveApprovedFiles(context: Context, files: Map<String, String>): List<String> {
        require(files.isNotEmpty() && files.size <= MAX_FILES) { "No valid files to save" }
        val root = File(context.filesDir, "workspace/staged_project").canonicalFile
        if (!root.exists()) require(root.mkdirs()) { "Could not create staging folder" }
        val saved = mutableListOf<String>()
        for ((relativePath, content) in files) {
            validatePath(relativePath)
            require(content.isNotBlank() && content.length <= MAX_FILE_CHARS) { "Invalid file content: $relativePath" }
            val target = File(root, relativePath).canonicalFile
            require(target.path.startsWith(root.path + File.separator)) { "Path escaped staging folder" }
            target.parentFile?.let { if (!it.exists()) require(it.mkdirs()) { "Could not create folder" } }
            target.writeText(content, Charsets.UTF_8)
            saved.add(relativePath)
        }
        return saved
    }
}
