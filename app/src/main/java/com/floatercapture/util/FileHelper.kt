package com.floatercapture.util

import android.os.Environment
import java.io.File
import java.net.URLDecoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileHelper {

    enum class MediaType(val dirName: String) {
        IMAGE("Images"),
        VIDEO("Videos"),
        AUDIO("Audio"),
        OTHER("Others")
    }

    private val MEDIA_EXTENSIONS = mapOf(
        "jpg" to MediaType.IMAGE,
        "jpeg" to MediaType.IMAGE,
        "png" to MediaType.IMAGE,
        "gif" to MediaType.IMAGE,
        "webp" to MediaType.IMAGE,
        "bmp" to MediaType.IMAGE,
        "svg" to MediaType.IMAGE,
        "heic" to MediaType.IMAGE,
        "heif" to MediaType.IMAGE,
        "mp4" to MediaType.VIDEO,
        "mkv" to MediaType.VIDEO,
        "avi" to MediaType.VIDEO,
        "mov" to MediaType.VIDEO,
        "wmv" to MediaType.VIDEO,
        "flv" to MediaType.VIDEO,
        "webm" to MediaType.VIDEO,
        "3gp" to MediaType.VIDEO,
        "mp3" to MediaType.AUDIO,
        "wav" to MediaType.AUDIO,
        "aac" to MediaType.AUDIO,
        "ogg" to MediaType.AUDIO,
        "flac" to MediaType.AUDIO,
        "m4a" to MediaType.AUDIO
    )

    private val MIME_TYPES = mapOf(
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "png" to "image/png",
        "gif" to "image/gif",
        "webp" to "image/webp",
        "bmp" to "image/bmp",
        "svg" to "image/svg+xml",
        "heic" to "image/heic",
        "heif" to "image/heif",
        "mp4" to "video/mp4",
        "mkv" to "video/x-matroska",
        "avi" to "video/x-msvideo",
        "mov" to "video/quicktime",
        "wmv" to "video/x-ms-wmv",
        "flv" to "video/x-flv",
        "webm" to "video/webm",
        "3gp" to "video/3gpp",
        "mp3" to "audio/mpeg",
        "wav" to "audio/wav",
        "aac" to "audio/aac",
        "ogg" to "audio/ogg",
        "flac" to "audio/flac",
        "m4a" to "audio/mp4"
    )

    fun getSaveDirectory(context: android.content.Context, mediaType: MediaType): File {
        val baseDir = if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            context.getExternalFilesDir(null)
                ?: context.filesDir
        } else {
            context.filesDir
        }

        val dir = File(baseDir, "FloaterCapture/${mediaType.dirName}")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun generateFileName(url: String, mediaType: MediaType): String {
        val extension = getFileExtension(url)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.getDefault()).format(Date())
        val urlHash = md5(url).take(8)
        return "FC_${mediaType.dirName}_${timestamp}_$urlHash.$extension"
    }

    fun getMimeType(url: String): String {
        val extension = getFileExtension(url).lowercase(Locale.ROOT)
        return MIME_TYPES[extension] ?: "application/octet-stream"
    }

    fun isValidMediaUrl(url: String): Boolean {
        if (url.isBlank()) return false

        return try {
            val decodedUrl = URLDecoder.decode(url, "UTF-8")
            val path = decodedUrl.substringBefore('?').substringBefore('#')
            val extension = path.substringAfterLast('.', "").lowercase(Locale.ROOT)
            extension.isNotEmpty() && extension.length <= 5 && extension.all { it.isLetterOrDigit() }
        } catch (e: Exception) {
            false
        }
    }

    fun getFileExtension(url: String): String {
        return try {
            val decodedUrl = URLDecoder.decode(url, "UTF-8")
            val path = decodedUrl.substringBefore('?').substringBefore('#')
            val extension = path.substringAfterLast('.', "").lowercase(Locale.ROOT)
            if (extension.isNotEmpty() && extension.length <= 5 && extension.all { it.isLetterOrDigit() }) {
                extension
            } else {
                "dat"
            }
        } catch (e: Exception) {
            "dat"
        }
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
