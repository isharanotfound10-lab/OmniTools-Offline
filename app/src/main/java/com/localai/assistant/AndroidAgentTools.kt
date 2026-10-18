package com.localai.assistant

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import java.util.Locale

/** Allow-listed Android tools; model output is never executed as arbitrary code. */
class AndroidAgentTools(private val context: Context) : ToolSet {
    @Tool(description = "Open an installed Android app by its common name, such as YouTube, WhatsApp, Chrome, Gmail, Maps, Camera, Clock, or Calculator.")
    fun openApp(@ToolParam(description = "Common app name, for example YouTube or WhatsApp") appName: String): String =
        AgentActions.execute(context, "open $appName") ?: "That app is not in the currently supported app allow-list."

    @Tool(description = "Open a supported Android settings screen such as Wi-Fi, Bluetooth, display, sound, battery, app settings, or general settings.")
    fun openAndroidSettings(@ToolParam(description = "Settings area: Wi-Fi, Bluetooth, display, sound, battery, app settings, or general settings") setting: String): String =
        AgentActions.execute(context, "open $setting") ?: "That settings screen is not supported on this device."

    @Tool(description = "Search the phone's indexed photos, videos, and audio files by part of their filename. Reads metadata only; it does not change or delete files.")
    fun searchMediaFiles(
        @ToolParam(description = "Words or part of a filename to search for") query: String,
        @ToolParam(description = "Media type: all, photos, videos, or audio") mediaType: String
    ): String = queryMedia(query, mediaType, 20, false)

    @Tool(description = "Find the largest indexed photos, videos, or audio files on the phone. Reads metadata only; it does not change or delete files.")
    fun findLargestMediaFiles(
        @ToolParam(description = "Media type: all, photos, videos, or audio") mediaType: String,
        @ToolParam(description = "How many results to return, from 1 to 20") limit: Int
    ): String = queryMedia("", mediaType, limit.coerceIn(1, 20), true)

    private fun queryMedia(query: String, mediaType: String, limit: Int, largestFirst: Boolean): String {
        val type = mediaType.trim().lowercase(Locale.ROOT)
        val wantsImages = type.isBlank() || type in listOf("all", "photo", "photos", "image", "images")
        val wantsVideos = type.isBlank() || type in listOf("all", "video", "videos")
        val wantsAudio = type.isBlank() || type in listOf("all", "audio", "music", "songs")
        if (!wantsImages && !wantsVideos && !wantsAudio) return "Unknown media type. Choose all, photos, videos, or audio."

        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            if (wantsImages && context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) needed.add("Photos")
            if (wantsVideos && context.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) needed.add("Videos")
            if (wantsAudio && context.checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) needed.add("Music and audio")
        } else if (context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            needed.add("Files and media")
        }
        if (needed.isNotEmpty()) return "I need ${needed.distinct().joinToString(" and ")} permission to search your media. Open Android Settings > Apps > Local AI > Permissions and allow the relevant media access, then try again."

        val results = mutableListOf<MediaItem>()
        try {
            if (wantsImages) results.addAll(readCollection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, query, limit, largestFirst, "Photo"))
            if (wantsVideos) results.addAll(readCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, query, limit, largestFirst, "Video"))
            if (wantsAudio) results.addAll(readCollection(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, query, limit, largestFirst, "Audio"))
        } catch (_: SecurityException) {
            return "Android denied media access. Open Android Settings > Apps > Local AI > Permissions and allow media access."
        } catch (e: Exception) {
            return "I couldn't search the media library: ${e.message ?: "unknown error"}."
        }
        val filtered = if (largestFirst) results.sortedByDescending { it.size }.take(limit) else results.sortedBy { it.name.lowercase(Locale.ROOT) }.take(limit)
        if (filtered.isEmpty()) return if (query.isBlank()) "I couldn't find matching indexed media files. The file may not be indexed, or its permission may be unavailable." else "No indexed media files matched '${query.trim()}'."
        return buildString {
            append("Found ${filtered.size} media file(s):\n")
            filtered.forEachIndexed { index, item ->
                append("${index + 1}. ${item.name} — ${formatBytes(item.size)} (${item.kind})\n")
                append("   URI: ${item.uri}\n")
            }
            append("These are indexed media results only; no files were modified.")
        }.trim()
    }

    private fun readCollection(
        collection: android.net.Uri,
        query: String,
        limit: Int,
        largestFirst: Boolean,
        kind: String
    ): List<MediaItem> {
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE)
        val selection = if (query.isBlank()) null else "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
        val selectionArgs = if (query.isBlank()) null else arrayOf("%${query.trim().replace("%", "\\%").replace("_", "\\_")}%")
        val sortOrder = if (largestFirst) "${MediaStore.MediaColumns.SIZE} DESC" else "${MediaStore.MediaColumns.DISPLAY_NAME} COLLATE NOCASE ASC"
        val items = mutableListOf<MediaItem>()
        context.contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            while (cursor.moveToNext() && items.size < limit) {
                val id = cursor.getLong(idIndex)
                val name = cursor.getString(nameIndex) ?: "(unnamed)"
                val size = if (cursor.isNull(sizeIndex)) 0L else cursor.getLong(sizeIndex)
                items.add(MediaItem(name, size, kind, ContentUris.withAppendedId(collection, id).toString()))
            }
        }
        return items
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1_000_000L -> "${bytes / 1_000} KB"
        bytes < 1_000_000_000L -> String.format(Locale.ROOT, "%.1f MB", bytes / 1_000_000.0)
        else -> String.format(Locale.ROOT, "%.2f GB", bytes / 1_000_000_000.0)
    }

    private data class MediaItem(val name: String, val size: Long, val kind: String, val uri: String)
}
