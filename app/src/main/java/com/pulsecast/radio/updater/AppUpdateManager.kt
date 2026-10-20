package com.pulsecast.radio.updater

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class VersionInfo(
    val version_code: Int? = null,
    val version_name: String? = null,
    val changelog: String? = null,
    val apk_url: String? = null,
    val generated_at: String? = null
)

data class GitHubReleaseAsset(
    val name: String? = null,
    val browser_download_url: String? = null
)

data class GitHubRelease(
    val tag_name: String? = null,
    val name: String? = null,
    val body: String? = null,
    val assets: List<GitHubReleaseAsset>? = null
)

sealed class AppUpdateState {
    object Idle : AppUpdateState()
    object Checking : AppUpdateState()
    data class Available(
        val versionName: String,
        val versionCode: Int,
        val changelog: String,
        val downloadUrl: String
    ) : AppUpdateState()
    data class Downloading(val progressPercent: Int) : AppUpdateState()
    data class ReadyToInstall(val apkFile: File) : AppUpdateState()
    object UpToDate : AppUpdateState()
    data class Error(val error: String) : AppUpdateState()
}

class AppUpdateManager(private val context: Context) {

    private val gson = Gson()
    private val _updateState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val updateState: StateFlow<AppUpdateState> = _updateState.asStateFlow()

    fun getCurrentVersionName(): String {
        return try {
            val pInfo: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
    }

    fun getCurrentVersionCode(): Int {
        return try {
            val pInfo: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
        } catch (_: Exception) {
            1
        }
    }

    fun checkForUpdate(scope: CoroutineScope = CoroutineScope(Dispatchers.IO)) {
        scope.launch {
            _updateState.value = AppUpdateState.Checking
            try {
                // 1. Check version.json on raw repository
                val rawVersionUrl = "https://raw.githubusercontent.com/MartinRieser/radio/android-native/version.json"
                val versionInfo = fetchVersionJson(rawVersionUrl)

                val curCode = getCurrentVersionCode()
                val curName = getCurrentVersionName()

                if (versionInfo != null && versionInfo.version_code != null) {
                    val remoteCode = versionInfo.version_code
                    val remoteName = versionInfo.version_name ?: "v$remoteCode"
                    val apkUrl = versionInfo.apk_url

                    if (remoteCode > curCode && !apkUrl.isNullOrBlank()) {
                        _updateState.value = AppUpdateState.Available(
                            versionName = remoteName,
                            versionCode = remoteCode,
                            changelog = versionInfo.changelog ?: "Neue Version verfügbar",
                            downloadUrl = apkUrl
                        )
                        return@launch
                    }
                }

                // 2. Fallback to GitHub Releases API
                val gitHubRelease = fetchGitHubLatestRelease()
                if (gitHubRelease != null) {
                    val tag = gitHubRelease.tag_name?.trim()?.removePrefix("v") ?: ""
                    val apkAsset = gitHubRelease.assets?.firstOrNull { it.name?.endsWith(".apk", ignoreCase = true) == true }

                    if (isVersionGreater(tag, curName) && apkAsset?.browser_download_url != null) {
                        _updateState.value = AppUpdateState.Available(
                            versionName = gitHubRelease.tag_name ?: "Neu",
                            versionCode = curCode + 1,
                            changelog = gitHubRelease.body ?: gitHubRelease.name ?: "Neues Update verfügbar",
                            downloadUrl = apkAsset.browser_download_url
                        )
                        return@launch
                    }
                }

                _updateState.value = AppUpdateState.UpToDate
            } catch (e: Exception) {
                e.printStackTrace()
                _updateState.value = AppUpdateState.Error(e.message ?: "Prüfung fehlgeschlagen")
            }
        }
    }

    private fun fetchVersionJson(urlStr: String): VersionInfo? {
        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "PulseCast-Android")
            if (conn.responseCode == 200) {
                val json = conn.inputStream.bufferedReader().use { it.readText() }
                gson.fromJson(json, VersionInfo::class.java)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchGitHubLatestRelease(): GitHubRelease? {
        return try {
            val url = URL("https://api.github.com/repos/MartinRieser/radio/releases/latest")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "PulseCast-Android")
            if (conn.responseCode == 200) {
                val json = conn.inputStream.bufferedReader().use { it.readText() }
                gson.fromJson(json, GitHubRelease::class.java)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun startDownloadAndInstall(activity: Activity, downloadUrl: String, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            _updateState.value = AppUpdateState.Downloading(0)
            try {
                val targetFile = File(activity.cacheDir, "PulseCast-update.apk")
                if (targetFile.exists()) {
                    targetFile.delete()
                }

                val url = URL(downloadUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 30000
                conn.setRequestProperty("User-Agent", "PulseCast-Android")
                conn.connect()

                if (conn.responseCode != 200) {
                    _updateState.value = AppUpdateState.Error("Download-Fehler (HTTP ${conn.responseCode})")
                    return@launch
                }

                val totalLength = conn.contentLength
                var downloaded = 0L

                conn.inputStream.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var read: Int
                        var lastPercent = 0
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (totalLength > 0) {
                                val percent = ((downloaded * 100) / totalLength).toInt()
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    _updateState.value = AppUpdateState.Downloading(percent)
                                }
                            }
                        }
                    }
                }

                _updateState.value = AppUpdateState.ReadyToInstall(targetFile)
                withContext(Dispatchers.Main) {
                    triggerInstall(activity, targetFile)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _updateState.value = AppUpdateState.Error("Download fehlgeschlagen: ${e.message}")
            }
        }
    }

    fun triggerInstall(activity: Activity, apkFile: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!activity.packageManager.canRequestPackageInstalls()) {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${activity.packageName}")
                    }
                    activity.startActivity(intent)
                    return
                }
            }

            val apkUri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(installIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            _updateState.value = AppUpdateState.Error("Installationsaufruf fehlgeschlagen: ${e.message}")
        }
    }

    fun dismissUpdate() {
        _updateState.value = AppUpdateState.Idle
    }

    private fun isVersionGreater(remote: String, current: String): Boolean {
        try {
            val rParts = remote.split(".").mapNotNull { it.trim().toIntOrNull() }
            val cParts = current.split(".").mapNotNull { it.trim().toIntOrNull() }
            val maxLen = maxOf(rParts.size, cParts.size)
            for (i in 0 until maxLen) {
                val r = rParts.getOrElse(i) { 0 }
                val c = cParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
        } catch (_: Exception) {}
        return false
    }
}
