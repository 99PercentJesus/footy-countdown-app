package com.example.update

import java.io.File

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val changelog: String = "",
    val releaseDate: String = "",
    val forceUpdate: Boolean = false,
    val sha256: String? = null
)

sealed interface UpdateUiState {
    object Idle : UpdateUiState
    object Checking : UpdateUiState
    data class UpdateAvailable(val info: UpdateInfo) : UpdateUiState
    data class Downloading(val progress: Float, val bytesDownloaded: Long, val totalBytes: Long) : UpdateUiState
    data class ReadyToInstall(val apkFile: File, val info: UpdateInfo) : UpdateUiState
    object UpToDate : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}
