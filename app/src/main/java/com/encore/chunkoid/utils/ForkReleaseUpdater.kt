package com.encore.chunkoid.utils

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.encore.chunkoid.R
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Reads published stable APK releases only from the public Chunkoid fork. */
object ForkReleaseUpdater {
    private const val API_URL = "https://api.github.com/repos/PhateValleyman/Chunkoid/releases?per_page=20"
    private const val CONNECT_TIMEOUT_MS = 10000
    private const val READ_TIMEOUT_MS = 15000

    data class ReleaseAsset(
        val name: String,
        val downloadUrl: String
    )

    data class ReleaseInfo(
        val tagName: String,
        val title: String,
        val notes: String,
        val asset: ReleaseAsset
    )

    @Throws(IOException::class)
    fun findLatestStableApk(): ReleaseInfo? {
        val connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "Chunkoid-Android-Updater")
        }
        try {
            val statusCode = connection.responseCode
            if (statusCode != HttpURLConnection.HTTP_OK) {
                throw IOException("GitHub vrátil stav HTTP $statusCode")
            }
            val releases = JSONArray(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            for (index in 0 until releases.length()) {
                val release = releases.getJSONObject(index)
                if (release.optBoolean("draft", true) || release.optBoolean("prerelease", true)) continue
                val assets = release.optJSONArray("assets") ?: continue
                for (assetIndex in 0 until assets.length()) {
                    val assetJson = assets.getJSONObject(assetIndex)
                    val fileName = assetJson.optString("name")
                    if (!fileName.endsWith(".apk", ignoreCase = true)) continue
                    val downloadUrl = assetJson.optString("browser_download_url")
                    if (!isForkReleaseUrl(downloadUrl)) continue
                    return ReleaseInfo(
                        tagName = release.optString("tag_name"),
                        title = release.optString("name").ifBlank { release.optString("tag_name") },
                        notes = release.optString("body"),
                        asset = ReleaseAsset(fileName, downloadUrl)
                    )
                }
            }
            return null
        } finally {
            connection.disconnect()
        }
    }

    fun enqueueApkDownload(context: Context, asset: ReleaseAsset): Long {
        require(isForkReleaseUrl(asset.downloadUrl)) { "Adresa APK nevede na vydání forku Chunkoid" }
        val safeFileName = asset.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val request = DownloadManager.Request(Uri.parse(asset.downloadUrl)).apply {
            setTitle(safeFileName)
            setDescription(context.getString(R.string.update_download_description))
            setMimeType("application/vnd.android.package-archive")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, safeFileName)
        }
        val manager = context.getSystemService(DownloadManager::class.java)
            ?: throw IllegalStateException("Služba stahování není dostupná")
        return manager.enqueue(request)
    }

    private fun isForkReleaseUrl(value: String): Boolean {
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return false
        if (uri.scheme != "https" || uri.encodedAuthority != "github.com" || uri.query != null || uri.fragment != null) return false
        val path = uri.path ?: return false
        val prefix = "/PhateValleyman/Chunkoid/releases/download/"
        if (!path.startsWith(prefix)) return false
        val pathSegments = path.removePrefix(prefix).split('/')
        if (pathSegments.size != 2) return false
        if (pathSegments.any { segment ->
                segment.isBlank() || segment == "." || segment == ".." || segment.contains('\\') ||
                    segment.contains("%2e", ignoreCase = true) || segment.contains("%2f", ignoreCase = true) ||
                    segment.contains("%5c", ignoreCase = true)
            }) return false
        return pathSegments[1].endsWith(".apk", ignoreCase = true)
    }
}
