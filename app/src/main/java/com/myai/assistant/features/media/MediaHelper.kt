// File: app/src/main/java/com/myai/assistant/features/media/MediaHelper.kt
// Media Helper — Play music, control media playback

package com.myai.assistant.features.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "MediaHelper"
        private const val SearchManager_QUERY = "query"
    }

    /**
     * 🎵 Music search and play — works with Spotify, YT Music, default player
     * @param query Song name, artist, or genre
     */
    fun playMusic(query: String): Boolean {
        return try {
            // First try: MEDIA_PLAY_FROM_SEARCH (supported by Spotify, YT Music, etc.)
            val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                putExtra(SearchManager_QUERY, query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "🎵 Playing music: $query")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Media search intent failed, trying YouTube Music: ${e.message}")
            // Fallback: YouTube Music/YouTube search
            try {
                val ytIntent = Intent(Intent.ACTION_VIEW, 
                    Uri.parse("https://music.youtube.com/search?q=${Uri.encode(query)}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(ytIntent)
                true
            } catch (ex: Exception) {
                Log.e(TAG, "Music play failed: ${ex.message}")
                false
            }
        }
    }

    /**
     * 📧 Email compose karo
     * @param to Recipient email address
     * @param subject Email subject
     * @param body Email body
     */
    fun composeEmail(to: String = "", subject: String = "", body: String = ""): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                if (to.isNotBlank()) putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
                if (subject.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, subject)
                if (body.isNotBlank()) putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "📧 Email compose: to=$to, subject=$subject")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Email compose failed: ${e.message}")
            false
        }
    }


}
