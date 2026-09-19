package com.example.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

class AppUpdateManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("ota_update_prefs", Context.MODE_PRIVATE)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    companion object {
        const val PREF_UPDATE_URL = "ota_manifest_url"
        // Default update URL pointing to your repository's latest release
        const val DEFAULT_UPDATE_URL = "https://api.github.com/repos/99PercentJesus/footy-countdown-app/releases/latest"
    }

    fun getUpdateUrl(): String {
        return prefs.getString(PREF_UPDATE_URL, DEFAULT_UPDATE_URL) ?: DEFAULT_UPDATE_URL
    }

    fun setUpdateUrl(url: String) {
        prefs.edit().putString(PREF_UPDATE_URL, url.trim()).apply()
    }

    fun resetState() {
        _updateState.value = UpdateUiState.Idle
    }

    suspend fun checkForUpdates(customUrl: String? = null) {
        _updateState.value = UpdateUiState.Checking

        val urlToUse = (customUrl ?: getUpdateUrl()).trim().ifEmpty {
            _updateState.value = UpdateUiState.Error("No update URL configured. Please enter a valid JSON or GitHub Releases URL in Settings.")
            return
        }

        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(urlToUse)
                    .header("Accept", "application/json")
                    .header("User-Agent", "MatchCountdown-Android/${BuildConfig.VERSION_NAME}")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        _updateState.value = UpdateUiState.Error("Server returned code ${response.code}: ${response.message}")
                        return@withContext
                    }

                    val responseBody = response.body?.string() ?: ""
                    if (responseBody.isBlank()) {
                        _updateState.value = UpdateUiState.Error("Empty response received from update server.")
                        return@withContext
                    }

                    val updateInfo = parseUpdateResponse(responseBody, urlToUse)
                    if (updateInfo == null) {
                        _updateState.value = UpdateUiState.Error("Could not find a valid APK release in the response.")
                        return@withContext
                    }

                    val currentVersionCode = BuildConfig.VERSION_CODE
                    if (updateInfo.versionCode > currentVersionCode) {
                        _updateState.value = UpdateUiState.UpdateAvailable(updateInfo)
                    } else {
                        _updateState.value = UpdateUiState.UpToDate
                    }
                }
            } catch (e: Exception) {
                _updateState.value = UpdateUiState.Error("Update check failed: ${e.localizedMessage ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun parseUpdateResponse(jsonString: String, url: String): UpdateInfo? {
        val json = JSONObject(jsonString)

        // Case 1: GitHub Releases API response
        // Format has "tag_name", "body", and "assets" array containing .apk
        if (json.has("tag_name") && json.has("assets")) {
            val tagName = json.optString("tag_name", "")
            val body = json.optString("body", "New release available on GitHub.")
            val publishedAt = json.optString("published_at", "")
            val assets = json.optJSONArray("assets")

            var apkDownloadUrl = ""
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkDownloadUrl = asset.optString("browser_download_url", "")
                        break
                    }
                }
            }

            if (apkDownloadUrl.isEmpty()) {
                return null
            }

            // Extract numeric version code from tag name (e.g. "v1.2" -> 12, or tag like "v2" -> 2)
            val digitsOnly = tagName.filter { it.isDigit() }
            val parsedVersionCode = digitsOnly.toIntOrNull() ?: (BuildConfig.VERSION_CODE + 1)

            return UpdateInfo(
                versionCode = parsedVersionCode,
                versionName = tagName.removePrefix("v"),
                apkUrl = apkDownloadUrl,
                changelog = body,
                releaseDate = publishedAt.take(10)
            )
        }

        // Case 2: Custom direct JSON manifest
        if (json.has("versionCode") && json.has("apkUrl")) {
            return UpdateInfo(
                versionCode = json.getInt("versionCode"),
                versionName = json.optString("versionName", "v${json.getInt("versionCode")}"),
                apkUrl = json.getString("apkUrl"),
                changelog = json.optString("changelog", "Bug fixes and performance improvements."),
                releaseDate = json.optString("releaseDate", ""),
                forceUpdate = json.optBoolean("forceUpdate", false),
                sha256 = json.optString("sha256", "").ifBlank { null }
            )
        }

        return null
    }

    suspend fun downloadUpdate(info: UpdateInfo) {
        _updateState.value = UpdateUiState.Downloading(0f, 0L, 0L)

        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(info.apkUrl)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        _updateState.value = UpdateUiState.Error("Download failed with HTTP ${response.code}")
                        return@withContext
                    }

                    val body = response.body ?: run {
                        _updateState.value = UpdateUiState.Error("Download body was empty.")
                        return@withContext
                    }

                    val totalBytes = body.contentLength()
                    val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
                    val targetApk = File(updatesDir, "update_v${info.versionCode}.apk")

                    if (targetApk.exists()) {
                        targetApk.delete()
                    }

                    var bytesCopied: Long = 0
                    val buffer = ByteArray(8 * 1024)
                    val inputStream: InputStream = body.byteStream()
                    val outputStream = FileOutputStream(targetApk)

                    outputStream.use { out ->
                        var bytes = inputStream.read(buffer)
                        var lastProgressUpdate = System.currentTimeMillis()

                        while (bytes >= 0) {
                            out.write(buffer, 0, bytes)
                            bytesCopied += bytes

                            val now = System.currentTimeMillis()
                            if (now - lastProgressUpdate > 100 || bytesCopied == totalBytes) {
                                lastProgressUpdate = now
                                val progress = if (totalBytes > 0) bytesCopied.toFloat() / totalBytes else 0.5f
                                _updateState.value = UpdateUiState.Downloading(progress, bytesCopied, totalBytes)
                            }

                            bytes = inputStream.read(buffer)
                        }
                        out.flush()
                    }

                    _updateState.value = UpdateUiState.ReadyToInstall(targetApk, info)
                }
            } catch (e: Exception) {
                _updateState.value = UpdateUiState.Error("Download error: ${e.localizedMessage ?: e.javaClass.simpleName}")
            }
        }
    }

    fun canInstallApks(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun installApk(apkFile: File) {
        try {
            if (!canInstallApks()) {
                openInstallPermissionSettings()
                return
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
        } catch (e: Exception) {
            _updateState.value = UpdateUiState.Error("Failed to launch installer: ${e.localizedMessage}")
        }
    }
}
