package com.thanish.jarvispro

import android.content.Context
import java.io.BufferedInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Downloads and safely extracts the small English Vosk model on first use.
 */
object VoskModelManager {
    private const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
    private const val MODEL_DIR = "vosk-model-small-en-us-0.15"
    private const val MAX_ARCHIVE_BYTES = 150L * 1024L * 1024L

    fun ensureModel(context: Context, onProgress: (String) -> Unit = {}): File {
        val root = File(context.filesDir, "vosk")
        val model = File(root, MODEL_DIR)
        if (File(model, "am").isDirectory && File(model, "conf").isDirectory) return model

        if (root.exists() && !root.isDirectory) {
            error("Vosk model path exists but is not a folder: ${root.absolutePath}")
        }
        if (!root.exists() && !root.mkdirs() && !root.isDirectory) {
            error("Cannot create Vosk model folder: ${root.absolutePath}")
        }

        // Keep the downloaded ZIP at its final working path. Renaming a file in
        // app-private storage can fail on some Android filesystems; extraction
        // can safely read the .part file directly, so no rename is necessary.
        val partial = File(root, "model.zip.part")
        if (partial.exists() && !partial.delete()) {
            error("Cannot remove incomplete model download: ${partial.absolutePath}")
        }
        onProgress("Downloading offline English wake-word model…")

        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30000
            readTimeout = 90000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("Model download failed: HTTP $code")
            val length = connection.contentLengthLong
            if (length > MAX_ARCHIVE_BYTES) error("Model archive is unexpectedly large: $length bytes")
            connection.inputStream.use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_ARCHIVE_BYTES) error("Model archive exceeds size limit")
                        output.write(buffer, 0, count)
                    }
                    output.flush()
                }
            }
        } catch (e: Exception) {
            partial.delete()
            throw IllegalStateException("Vosk download/save failed (${e.javaClass.simpleName}): ${e.message ?: "no details"}", e)
        } finally {
            connection.disconnect()
        }

        if (!partial.isFile || partial.length() < 4L) {
            partial.delete()
            error("Downloaded Vosk model archive is empty or incomplete")
        }

        onProgress("Installing wake-word model…")
        try {
            ZipInputStream(BufferedInputStream(partial.inputStream())).use { zip ->
                var entry = zip.nextEntry
                val canonicalRoot = root.canonicalFile
                while (entry != null) {
                    val target = File(root, entry.name).canonicalFile
                    require(target.path.startsWith(canonicalRoot.path + File.separator)) {
                        "Unsafe model archive entry"
                    }
                    if (entry.isDirectory) {
                        if (!target.exists() && !target.mkdirs()) error("Cannot create model folder: ${target.path}")
                    } else {
                        target.parentFile?.let { parent ->
                            if (!parent.exists() && !parent.mkdirs()) error("Cannot create model folder: ${parent.path}")
                        }
                        target.outputStream().buffered().use { out -> zip.copyTo(out) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) {
            throw IllegalStateException("Vosk model extraction failed: ${e.message ?: e.javaClass.simpleName}", e)
        } finally {
            partial.delete()
        }

        require(File(model, "am").isDirectory && File(model, "conf").isDirectory) {
            "Model archive is incomplete; delete the Vosk model folder and retry"
        }
        onProgress("Offline English wake-word model ready")
        return model
    }
}
