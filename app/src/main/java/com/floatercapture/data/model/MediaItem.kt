package com.floatercapture.data.model

data class MediaItem(
    val id: String = "",
    val type: MediaType,
    val url: String = "",
    val sourcePackage: String = "",
    val sourceAppName: String = "",
    val description: String = "",
    val fileSize: Long = 0,
    val mimeType: String = "",
    val thumbnailUrl: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isDownloaded: Boolean = false,
    val localFilePath: String = "",
    // 文本内容（用于 TEXT 类型）
    val textContent: String = ""
)

enum class MediaType(val displayName: String, val mimeType: String) {
    IMAGE("图片", "image/*"),
    VIDEO("视频", "video/*"),
    AUDIO("音频", "audio/*"),
    DOCUMENT("文档", "application/*"),
    TEXT("文本", "text/*"),
    OTHER("其他", "*/*")
}
