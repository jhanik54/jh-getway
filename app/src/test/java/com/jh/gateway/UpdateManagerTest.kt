package com.jh.gateway

import android.content.Context
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class UpdateManagerTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("pending_update", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        prefs.edit().clear().commit()
    }

    private fun storeCached(update: AvailableUpdate) {
        prefs.edit().putString("json", JSONObject().apply {
            put("versionCode", update.versionCode)
            put("versionName", update.versionName)
            put("notes", update.notes)
            put("apkUrl", update.apkUrl)
            put("releaseUrl", update.releaseUrl)
            put("sha256", update.sha256 ?: "")
        }.toString()).commit()
    }

    @Test
    fun cachedUpdateOlderThanInstalledIsDiscardedAndCleared() {
        storeCached(AvailableUpdate(1, "0.0.1", "old", "https://example.com/app.apk", "https://example.com/release"))
        assertNull(UpdateManager(context).cachedUpdate())
        assertNull(prefs.getString("json", null))
    }

    @Test
    fun cachedUpdateNewerThanInstalledIsReturned() {
        val newer = BuildConfig.VERSION_CODE + 1
        storeCached(AvailableUpdate(newer, "9.9.9", "notes", "https://example.com/app.apk", "https://example.com/release"))
        val update = UpdateManager(context).cachedUpdate()
        assertNotNull(update)
        assertEquals(newer, update!!.versionCode)
        assertEquals("9.9.9", update.versionName)
    }

    @Test
    fun blankStoredSha256SurfacesAsNull() {
        val newer = BuildConfig.VERSION_CODE + 1
        prefs.edit().putString("json", JSONObject().apply {
            put("versionCode", newer)
            put("versionName", "9.9.9")
            put("notes", "notes")
            put("apkUrl", "https://example.com/app.apk")
            put("releaseUrl", "https://example.com/release")
            put("sha256", "")
        }.toString()).commit()
        assertNull(UpdateManager(context).cachedUpdate()!!.sha256)
    }
}
