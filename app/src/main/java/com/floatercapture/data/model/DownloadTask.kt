package com.floatercapture.data.model

data class DownloadTask(
    val id: String = "",
    val url: String = "",
    val fileName: String = "",
    val mediaType: MediaType = MediaType.OTHER,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val state: DownloadState = DownloadState.Pending,
    val progress: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val errorMessage: String = ""
) {
    init {
        require(progress in 0..100) { "Progress must be between 0 and 100" }
    }
}

enum class DownloadState {
    Pending,
    Downloading,
    Completed,
    Failed,
    Paused
}
