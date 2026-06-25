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
    val localFilePath: String = ""
)

enum class MediaType(val displayName: String, val mimeType: String) {
    IMAGE("图片", "image/*"),
    VIDEO("视频", "video/*"),
    DOCUMENT("文档", "application/*"),
    OTHER("其他", "*/*")
}
