// File: app/src/main/java/com/myai/assistant/features/files/FileManager.kt
// File Manager — Files read/write/list

package com.myai.assistant.features.files

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class FileInfo(
    val name: String,
    val path: String,
    val size: Long,
    val isDirectory: Boolean,
    val lastModified: Long
)

@Singleton
class FileManager @Inject constructor() {

    companion object {
        private const val TAG = "FileManager"
    }

    /**
     * Directory ki files list karo
     */
    fun listFiles(path: String = Environment.getExternalStorageDirectory().absolutePath): List<FileInfo> {
        return try {
            val dir = File(path)
            if (!dir.exists() || !dir.isDirectory) return emptyList()

            dir.listFiles()?.map {
                FileInfo(it.name, it.absolutePath, it.length(), it.isDirectory, it.lastModified())
            }?.sortedByDescending { it.lastModified } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "List files error: ${e.message}")
            emptyList()
        }
    }

    /**
     * File read karo (text)
     */
    fun readFile(path: String): String? {
        return try {
            File(path).readText()
        } catch (e: Exception) {
            Log.e(TAG, "Read error: ${e.message}")
            null
        }
    }

    /**
     * File write karo
     */
    fun writeFile(path: String, content: String): Boolean {
        return try {
            File(path).writeText(content)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Write error: ${e.message}")
            false
        }
    }

    /**
     * Downloads folder ki files
     */
    fun getDownloads(): List<FileInfo> {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return listFiles(downloadsDir.absolutePath)
    }

    /**
     * Recent images (MediaStore se)
     */
    fun getRecentImages(context: Context, limit: Int = 20): List<String> {
        val images = mutableListOf<String>()
        val cursor = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media.DATA),
            null, null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC LIMIT $limit"
        )
        cursor?.use {
            while (it.moveToNext()) {
                it.getString(0)?.let { path -> images.add(path) }
            }
        }
        return images
    }

    /**
     * File search karo by name
     */
    fun searchFiles(rootPath: String, query: String, maxResults: Int = 20): List<FileInfo> {
        val results = mutableListOf<FileInfo>()
        searchRecursive(File(rootPath), query.lowercase(), results, maxResults)
        return results
    }

    private fun searchRecursive(dir: File, query: String, results: MutableList<FileInfo>, max: Int) {
        if (results.size >= max || !dir.exists()) return
        dir.listFiles()?.forEach { file ->
            if (results.size >= max) return
            if (file.name.lowercase().contains(query)) {
                results.add(FileInfo(file.name, file.absolutePath, file.length(), file.isDirectory, file.lastModified()))
            }
            if (file.isDirectory && !file.name.startsWith(".")) {
                searchRecursive(file, query, results, max)
            }
        }
    }
}
