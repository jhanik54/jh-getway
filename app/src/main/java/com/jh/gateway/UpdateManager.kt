package com.jh.gateway

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class AvailableUpdate(
    val versionCode: Int,
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val releaseUrl: String
)

/** GitHub release checker plus Android Download Manager based installer flow. */
class UpdateManager(private val context: Context) {
    private val latestReleaseApi = "https://api.github.com/repos/jhanik54/jh-getway/releases/latest"

    fun check(onResult: (Result<AvailableUpdate?>) -> Unit) {
        Thread {
            val result = runCatching {
                val connection = (URL(latestReleaseApi).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 15_000
                    setRequestProperty("User-Agent", "JH-GetWay/${BuildConfig.VERSION_NAME}")
                    setRequestProperty("Accept", "application/vnd.github+json")
                }
                val status = connection.responseCode
                check(status in 200..299) { "GitHub returned HTTP $status" }
                val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                connection.disconnect()
                val asset = release.optJSONArray("assets")?.let { assets -> (0 until assets.length()).asSequence().map { assets.getJSONObject(it) }.firstOrNull { it.optString("name").endsWith(".apk") } }
                    ?: error("No APK asset found in the latest release")
                val tag = release.optString("tag_name", "v0.0.0")
                val versionName = tag.removePrefix("v")
                val versionCode = versionName.substringBefore('.').toIntOrNull() ?: 0
                if (versionCode > BuildConfig.VERSION_CODE) AvailableUpdate(
                    versionCode,
                    versionName,
                    release.optString("body").ifBlank { "Performance improvements and bug fixes." },
                    asset.getString("browser_download_url"),
                    release.optString("html_url", "https://github.com/jhanik54/jh-getway/releases")
                ) else null
            }
            onResult(result)
        }.start()
    }

    fun openReleasePage(update: AvailableUpdate) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.releaseUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun download(update: AvailableUpdate, onResult: (Result<File>) -> Unit) {
        val fileName = "jh-getway-${update.versionName}.apk"
        val target = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName).apply { parentFile?.mkdirs(); delete() }
        val downloadManager = context.getSystemService(DownloadManager::class.java)
        val request = DownloadManager.Request(Uri.parse(update.apkUrl)).apply {
            setTitle("JH GetWay ${update.versionName}")
            setDescription("Downloading update")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(false)
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
        }
        val id = downloadManager.enqueue(request)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receivedContext: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) != id) return
                runCatching { receivedContext.unregisterReceiver(this) }
                val query = downloadManager.query(DownloadManager.Query().setFilterById(id))
                query.use {
                    if (!it.moveToFirst()) return onResult(Result.failure(IllegalStateException("Download record missing")))
                    val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    if (status == DownloadManager.STATUS_SUCCESSFUL && target.exists() && target.length() > 1_000_000) onResult(Result.success(target))
                    else onResult(Result.failure(IllegalStateException("Download failed")))
                }
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun install(file: File, onError: (String) -> Unit = {}) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            onError("Allow installation permission, then tap Install update again")
            return
        }
        val uri = FileProvider.getUriForFile(context, "com.jh.gateway.fileprovider", file)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "application/vnd.android.package-archive"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION) })
    }
}
