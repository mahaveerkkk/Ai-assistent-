package com.myai.assistant.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myai.assistant.data.repository.ChatRepository
import com.myai.assistant.features.camera.CameraManager
import com.myai.assistant.features.voice.GeminiTTSManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    val cameraManager: CameraManager,
    private val chatRepository: ChatRepository,
    private val geminiTts: GeminiTTSManager
) : ViewModel() {

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    init {
        geminiTts.initialize(appContext)
    }

    fun captureAndReadText(context: Context, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                Log.d("CameraVM", "Taking photo for OCR...")
                val photoFile = cameraManager.takePhoto(context)
                if (photoFile != null) {
                    val photoUri = Uri.fromFile(photoFile)
                    Log.d("CameraVM", "Photo taken: ${photoFile.absolutePath}, starting text recognition...")
                    val recognizedText = cameraManager.recognizeText(context, photoUri)
                    Log.d("CameraVM", "Text recognized: $recognizedText")
                    if (recognizedText.isNotBlank()) {
                        chatRepository.saveSystemMessage("🔍 OCR Result: $recognizedText")
                        geminiTts.speak(recognizedText)
                    } else {
                        chatRepository.saveSystemMessage("🔍 OCR Result: No text found")
                        geminiTts.speak("Koi text nahi mila.")
                    }
                } else {
                    chatRepository.saveSystemMessage("❌ OCR failed: Photo capture failed")
                    geminiTts.speak("Photo capture nahi ho paya.")
                }
            } catch (e: Exception) {
                Log.e("CameraVM", "OCR error", e)
                chatRepository.saveSystemMessage("❌ OCR failed: ${e.message}")
                geminiTts.speak("OCR process mein error aayi.")
            } finally {
                _isProcessing.value = false
                onComplete()
            }
        }
    }

    fun captureAndLabelImage(context: Context, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                Log.d("CameraVM", "Taking photo for labeling...")
                val photoFile = cameraManager.takePhoto(context)
                if (photoFile != null) {
                    val photoUri = Uri.fromFile(photoFile)
                    Log.d("CameraVM", "Photo taken: ${photoFile.absolutePath}, starting labeling...")
                    val labels = cameraManager.labelImage(context, photoUri)
                    Log.d("CameraVM", "Labels recognized: $labels")
                    if (labels.isNotEmpty()) {
                        val resultText = labels.joinToString(", ")
                        chatRepository.saveSystemMessage("🏷️ Labels: $resultText")
                        geminiTts.speak("Image mein ye cheezein hain: $resultText")
                    } else {
                        chatRepository.saveSystemMessage("🏷️ Labels: No objects recognized")
                        geminiTts.speak("Kuch pehchan nahi paya.")
                    }
                } else {
                    chatRepository.saveSystemMessage("❌ Labeling failed: Photo capture failed")
                    geminiTts.speak("Photo capture nahi ho paya.")
                }
            } catch (e: Exception) {
                Log.e("CameraVM", "Labeling error", e)
                chatRepository.saveSystemMessage("❌ Labeling failed: ${e.message}")
                geminiTts.speak("Labeling process mein error aayi.")
            } finally {
                _isProcessing.value = false
                onComplete()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        cameraManager.stopCamera()
    }
}
