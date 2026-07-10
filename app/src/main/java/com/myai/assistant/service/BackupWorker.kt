// File: app/src/main/java/com/myai/assistant/service/BackupWorker.kt
package com.myai.assistant.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.myai.assistant.data.db.ChatDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.lang.Exception

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val chatDao: ChatDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            // Calculate the timestamp for 7 days ago
            val sevenDaysAgo = System.currentTimeMillis() - (7L * 24L * 60L * 60L * 1000L)
            
            // Delete old messages
            chatDao.deleteOldMessages(sevenDaysAgo)
            
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}
