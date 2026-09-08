package com.example.treadmillcontroller.trail

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class TrailRepository(private val context: Context) {

    private val hikesDir = File(context.filesDir, "hikes").apply { mkdirs() }
    private val _trails = MutableStateFlow<List<Trail>>(emptyList())
    val trails: StateFlow<List<Trail>> = _trails.asStateFlow()

    suspend fun initialize() = withContext(Dispatchers.IO) {
        val loadedList = mutableListOf<Trail>()

        // 1. Ensure built-in Well Gulch is copied to filesDir/hikes/wells_gulch.gpx if not present
        val builtInFile = File(hikesDir, "wells_gulch.gpx")
        if (!builtInFile.exists()) {
            try {
                context.assets.open("hikes/wells_gulch.gpx").use { input ->
                    FileOutputStream(builtInFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed copying built-in trail: ${e.message}")
            }
        }

        // 2. Scan and parse all GPX files in hikesDir
        val gpxFiles = hikesDir.listFiles { file -> file.extension.equals("gpx", ignoreCase = true) } ?: emptyArray()
        for (file in gpxFiles) {
            try {
                file.inputStream().use { stream ->
                    val isBuiltIn = file.name == "wells_gulch.gpx"
                    val trail = GpxParser.parse(stream, fileName = file.name, isBuiltIn = isBuiltIn)
                    if (trail.points.isNotEmpty()) {
                        loadedList.add(trail)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading trail file ${file.name}: ${e.message}")
            }
        }

        // Fallback: if no trails loaded, load directly from assets
        if (loadedList.isEmpty()) {
            GpxParser.loadFromAssets(context, "hikes/wells_gulch.gpx")?.let { loadedList.add(it) }
        }

        _trails.value = loadedList
    }

    suspend fun addTrailFromUri(uri: Uri): Result<Trail> = withContext(Dispatchers.IO) {
        try {
            var fileName = getFileNameFromUri(uri) ?: "trail_${System.currentTimeMillis()}.gpx"
            if (!fileName.endsWith(".gpx", ignoreCase = true)) {
                fileName += ".gpx"
            }

            val destFile = File(hikesDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(Exception("Could not open file input stream"))

            val parsedTrail = destFile.inputStream().use { stream ->
                GpxParser.parse(stream, fileName = destFile.name, isBuiltIn = false)
            }

            if (parsedTrail.points.isEmpty()) {
                destFile.delete()
                return@withContext Result.failure(Exception("No track points found in GPX file"))
            }

            val updated = _trails.value.toMutableList()
            updated.removeAll { it.id == parsedTrail.id || it.fileName == parsedTrail.fileName }
            updated.add(parsedTrail)
            _trails.value = updated

            Result.success(parsedTrail)
        } catch (e: Exception) {
            Log.e(TAG, "Error importing GPX: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun deleteTrail(trail: Trail): Boolean = withContext(Dispatchers.IO) {
        if (trail.isBuiltIn) return@withContext false
        val file = File(hikesDir, trail.fileName)
        val deleted = if (file.exists()) file.delete() else true
        if (deleted) {
            _trails.value = _trails.value.filter { it.id != trail.id }
        }
        deleted
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var name: String? = null
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                }
            }
        } catch (_: Exception) {}
        return name ?: uri.lastPathSegment
    }

    companion object {
        private const val TAG = "TrailRepository"
    }
}
