package com.thanish.jarvispro

import android.content.Context
import java.io.BufferedInputStream
import java.io.File
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Downloads the small English Vosk model on first use.
 * The model is stored privately inside the app's files directory.
 */
object VoskModelManager {
    private const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
    private const val MODEL_DIR = "vosk-model-small-en-us-0.15"

    fun ensureModel(context: Context, onProgress: (String) -> Unit = {}): File {
        val root = File(context.filesDir, "vosk")
        val model = File(root, MODEL_DIR)
        if (File(model, "am").exists() && File(model, "conf").exists()) return model
        if (!root.exists() && !root.mkdirs()) error("Cannot create model folder")
        val archive = File(root, "model.zip")
        onProgress("Downloading offline English wake-word model…")
        URL(MODEL_URL).openConnection().apply {
            connectTimeout = 20000
            readTimeout = 60000
            getInputStream().use { input -> archive.outputStream().use { output -> input.copyTo(output) } }
        }
        onProgress("Installing wake-word model…")
        ZipInputStream(BufferedInputStream(archive.inputStream())).use { zip ->
            var entry = zip.nextEntry
            val canonicalRoot = root.canonicalFile
            while (entry != null) {
                val target = File(root, entry.name).canonicalFile
                require(target.path.startsWith(canonicalRoot.path + File.separator)) { "Unsafe model archive entry" }
                if (entry.isDirectory) target.mkdirs() else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { out -> zip.copyTo(out) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        archive.delete()
        require(File(model, "am").exists() && File(model, "conf").exists()) { "Model archive is incomplete" }
        return model
    }
}
