package com.jh.gateway

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class AvailableUpdate(val versionCode: Int, val versionName: String, val notes: String, val apkUrl: String, val releaseUrl: String, val sha256: String? = null)

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
                val assets = release.optJSONArray("assets") ?: error("Release has no assets")
                val asset = (0 until assets.length()).asSequence().map { assets.getJSONObject(it) }.firstOrNull { it.optString("name").equals("app-debug.apk", true) || it.optString("name").endsWith(".apk") } ?: error("No APK asset found")
                val metadataAsset = (0 until assets.length()).asSequence().map { assets.getJSONObject(it) }.firstOrNull { it.optString("name") == "latest.json" }
                val metadata = metadataAsset?.let { readJson(it.getString("browser_download_url")) }
                val versionName = metadata?.optString("versionName").orEmpty().ifBlank { release.optString("tag_name", "v0.0.0").removePrefix("v") }
                val versionCode = metadata?.optInt("versionCode", 0)?.takeIf { it > 0 } ?: versionName.substringBefore('.').toIntOrNull() ?: 0
                val apkUrl = metadata?.optString("apkUrl").orEmpty().ifBlank { asset.getString("browser_download_url") }
                val found = if (versionCode > BuildConfig.VERSION_CODE) AvailableUpdate(versionCode, versionName, release.optString("body").ifBlank { "Performance improvements and bug fixes." }, apkUrl, release.optString("html_url"), metadata?.optString("sha256")?.ifBlank { null }) else null
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
            else AvailableUpdate(versionCode, value.getString("versionName"), value.getString("notes"), value.getString("apkUrl"), value.getString("releaseUrl"), value.optString("sha256").ifBlank { null })
        }.getOrNull()
    }

    private fun cacheUpdate(update: AvailableUpdate) {
        updatePrefs.edit().putString("json", JSONObject().apply {
            put("versionCode", update.versionCode); put("versionName", update.versionName); put("notes", update.notes)
            put("apkUrl", update.apkUrl); put("releaseUrl", update.releaseUrl); put("sha256", update.sha256 ?: "")
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
                update.sha256?.let { expected -> check(sha256(partial).equals(expected, true)) { "APK checksum verification failed" } }
                verifyApk(partial)?.let { problem -> partial.delete(); throw IllegalStateException(problem) }
                check(partial.renameTo(apk)) { "Could not save APK inside app storage" }
                apk
            }
            onResult(result)
        }.start()
    }

    private fun readJson(url: String): JSONObject? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 15_000; readTimeout = 15_000; setRequestProperty("User-Agent", "JH-GetWay/${BuildConfig.VERSION_NAME}") }
        connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }.also { connection.disconnect() }
    }.getOrNull()

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(32 * 1024); while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Verifies that [apk] is an installable, newer APK of this same app, signed by the
     * same certificate this installation uses. Returns a user-presentable failure
     * message, or null when the APK may be installed as an update.
     */
    private fun verifyApk(apk: File): String? {
        val archiveFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageArchiveInfo(apk.absolutePath, archiveFlags)
            ?: return "Downloaded file is not an installable APK"
        if (info.packageName != context.packageName) return "APK package mismatch (expected ${context.packageName}, got ${info.packageName})"
        val candidateVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        if (candidateVersion <= BuildConfig.VERSION_CODE) return "Downloaded APK version ($candidateVersion) is not newer than the installed version (${BuildConfig.VERSION_CODE})"
        val expected = currentSigningDigests()
        val actual = archiveSigningDigests(info)
        if (expected.isEmpty() || actual.isEmpty()) return "Could not read the APK signing certificate"
        if (actual.none { it in expected }) return "APK signing certificate does not match this installation. Install it manually only if you trust its source."
        return null
    }

    private fun currentSigningDigests(): Set<String> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            return info.signingInfo?.apkContentsSigners?.map { sha256(it.toByteArray()) }?.toSet().orEmpty()
        }
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        @Suppress("DEPRECATION")
        return info.signatures?.map { sha256(it.toByteArray()) }?.toSet().orEmpty()
    }

    private fun archiveSigningDigests(info: PackageInfo): Set<String> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return info.signingInfo?.apkContentsSigners?.map { sha256(it.toByteArray()) }?.toSet().orEmpty()
        }
        @Suppress("DEPRECATION")
        return info.signatures?.map { sha256(it.toByteArray()) }?.toSet().orEmpty()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

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
        // Re-verify before handing the file to the package installer: the file may
        // have been replaced or tampered with since the download completed.
        verifyApk(file)?.let { onError(it); return }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); onError("Allow installation permission, then tap Install update again"); return
        }
        val uri = FileProvider.getUriForFile(context, "com.jh.gateway.fileprovider", file)
        context.startActivity(Intent(Intent.ACTION_INSTALL_PACKAGE).apply { setDataAndType(uri, "application/vnd.android.package-archive"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_CLEAR_TOP) })
    }
}
