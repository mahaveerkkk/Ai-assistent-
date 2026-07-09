// File: app/src/main/java/com/myai/assistant/features/device/ClipboardHelper.kt
// Clipboard Helper — Copy, paste, read clipboard

package com.myai.assistant.features.device

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClipboardHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ClipboardHelper"
    }

    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    /**
     * 📋 Text copy karo clipboard mein
     */
    fun copyText(text: String, label: String = "MyAI"): Boolean {
        return try {
            val clip = ClipData.newPlainText(label, text)
            clipboardManager.setPrimaryClip(clip)
            Log.d(TAG, "📋 Copied to clipboard: ${text.take(50)}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Copy failed: ${e.message}")
            false
        }
    }

    /**
     * 📋 Clipboard se text padhho
     */
    fun getClipboardText(): String {
        return try {
            if (clipboardManager.hasPrimaryClip()) {
                val item = clipboardManager.primaryClip?.getItemAt(0)
                item?.text?.toString() ?: ""
            } else ""
        } catch (e: Exception) {
            Log.e(TAG, "Read clipboard failed: ${e.message}")
            ""
        }
    }

    /**
     * 📋 Clipboard clear karo
     */
    fun clearClipboard(): Boolean {
        return try {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("", ""))
            true
        } catch (e: Exception) {
            Log.e(TAG, "Clear clipboard failed: ${e.message}")
            false
        }
    }
}
