package com.jh.gateway

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class AvailableUpdate(val versionCode: Int, val versionName: String, val notes: String, val apkUrl: String, val releaseUrl: String)

/** App-owned updater: APK is stored under files/updates and remains available for later install. */
class UpdateManager(private val context: Context) {
    private val latestReleaseApi = "https://api.github.com/repos/jhanik54/jh-getway/releases/latest"
    private val updatePrefs = context.getSharedPreferences("pending_update", Context.MODE_PRIVATE)

    fun check(onResult: (Result<AvailableUpdate?>) -> Unit) {
        Thread {
            val result = runCatching {
                val connection = (URL(latestReleaseApi).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000; readTimeout = 15_000
                    setRequestProperty("User-Agent", "JH-GetWay/${BuildConfig.VERSION_NAME}")
                    setRequestProperty("Accept", "application/vnd.github+json")
                }
                check(connection.responseCode in 200..299) { "GitHub returned HTTP ${connection.responseCode}" }
                val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }); connection.disconnect()
                val asset = release.optJSONArray("assets")?.let { assets -> (0 until assets.length()).asSequence().map { assets.getJSONObject(it) }.firstOrNull { it.optString("name").endsWith(".apk") } } ?: error("No APK asset found")
                val versionName = release.optString("tag_name", "v0.0.0").removePrefix("v")
                val versionCode = versionName.substringBefore('.').toIntOrNull() ?: 0
                val found = if (versionCode > BuildConfig.VERSION_CODE) AvailableUpdate(versionCode, versionName, release.optString("body").ifBlank { "Performance improvements and bug fixes." }, asset.getString("browser_download_url"), release.optString("html_url")) else null
                if (found == null) clearCachedUpdate() else cacheUpdate(found)
                found
            }
            onResult(result)
        }.start()
    }

    fun cachedUpdate(): AvailableUpdate? {
        val cached = updatePrefs.getString("json", null) ?: return null
        return runCatching {
            val value = JSONObject(cached)
            val versionCode = value.getInt("versionCode")
            if (versionCode <= BuildConfig.VERSION_CODE) { clearCachedUpdate(); null }
            else AvailableUpdate(versionCode, value.getString("versionName"), value.getString("notes"), value.getString("apkUrl"), value.getString("releaseUrl"))
        }.getOrNull()
    }

    private fun cacheUpdate(update: AvailableUpdate) {
        updatePrefs.edit().putString("json", JSONObject().apply {
            put("versionCode", update.versionCode); put("versionName", update.versionName); put("notes", update.notes)
            put("apkUrl", update.apkUrl); put("releaseUrl", update.releaseUrl)
        }.toString()).apply()
    }

    private fun clearCachedUpdate() { updatePrefs.edit().remove("json").apply() }

    fun download(update: AvailableUpdate, onProgress: (Int) -> Unit, onResult: (Result<File>) -> Unit) {
        Thread {
            val result = runCatching {
                val folder = File(context.filesDir, "updates").apply { mkdirs() }
                val apk = File(folder, "jh-getway-${update.versionName}.apk")
                val partial = File(folder, "${apk.name}.part")
                apk.delete(); partial.delete()
                val connection = (URL(update.apkUrl).openConnection() as HttpURLConnection).apply { connectTimeout = 15_000; readTimeout = 120_000; instanceFollowRedirects = true; setRequestProperty("User-Agent", "JH-GetWay/${BuildConfig.VERSION_NAME}"); setRequestProperty("Accept", "application/octet-stream") }
                check(connection.responseCode in 200..299) { "APK download failed (HTTP ${connection.responseCode})" }
                val total = connection.contentLengthLong
                var copied = 0L
                connection.inputStream.use { input -> partial.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) { val count = input.read(buffer); if (count < 0) break; output.write(buffer, 0, count); copied += count; if (total > 0) onProgress(((copied * 100) / total).toInt().coerceIn(0, 100)) }
                } }
                connection.disconnect()
                check(partial.length() > 1_000_000) { "Downloaded APK is incomplete" }
                check(partial.inputStream().use { input -> input.read() == 'P'.code && input.read() == 'K'.code }) { "Downloaded file is not a valid APK" }
                check(partial.renameTo(apk)) { "Could not save APK inside app storage" }
                apk
            }
            onResult(result)
        }.start()
    }

    /** Returns a completed APK kept in app-private storage after the update dialog was closed. */
    fun existingApk(): File? {
        val folder = File(context.filesDir, "updates")
        return folder.listFiles()
            ?.filter { it.isFile && it.extension.equals("apk", ignoreCase = true) && it.length() > 1_000_000 }
            ?.filter { file ->
                val version = Regex("jh-getway-(\\d+)(?:\\.\\d+){0,2}\\.apk").find(file.name)?.groupValues?.getOrNull(1)?.toIntOrNull()
                if (version == null || version <= BuildConfig.VERSION_CODE) { file.delete(); false } else true
            }
            ?.maxByOrNull { it.lastModified() }
    }

    fun openReleasePage(update: AvailableUpdate) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.releaseUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    fun install(file: File, onError: (String) -> Unit = {}) {
        if (!file.exists() || file.length() < 1_000_000) { onError("Update APK is missing or incomplete. Download it again."); return }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); onError("Allow installation permission, then tap Install update again"); return
        }
        val uri = FileProvider.getUriForFile(context, "com.jh.gateway.fileprovider", file)
        context.startActivity(Intent(Intent.ACTION_INSTALL_PACKAGE).apply { setDataAndType(uri, "application/vnd.android.package-archive"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_CLEAR_TOP) })
    }
}
