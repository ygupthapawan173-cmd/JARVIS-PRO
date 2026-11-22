package com.thanish.jarvispro

import android.content.Context
import java.io.File

object CodeWorkspace {
    fun save(context: Context, generatedText: String): File {
        val directory = File(context.filesDir, "workspace").apply { mkdirs() }
        val safeText = generatedText.trim()
        require(safeText.isNotBlank()) { "No generated code to save" }
        val file = File(directory, "generated_${System.currentTimeMillis()}.txt")
        file.writeText(safeText, Charsets.UTF_8)
        return file
    }
}
