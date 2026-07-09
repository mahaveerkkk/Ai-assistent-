// File: app/src/main/java/com/myai/assistant/ai/AgentLoop.kt
// Agent Loop — Multi-step autonomous agent for complex tasks
// Read Screen → Decide → Act → Read Again → Decide → Act... until goal is done

package com.myai.assistant.ai

import android.util.Log
import com.myai.assistant.accessibility.MyAccessibilityService
import com.myai.assistant.ai.models.AiParsedResponse
import com.myai.assistant.data.repository.ChatRepository
import kotlinx.coroutines.delay

/**
 * AgentLoop — Multi-step autonomous control loop
 * 
 * Ye sabse powerful feature hai:
 * 1. User ka goal samjho
 * 2. Screen padho
 * 3. AI se next action lo
 * 4. Action execute karo
 * 5. Result dekho
 * 6. Agar goal complete nahi hua → step 2 pe wapas jao
 * 7. Max steps ke baad ruk jao
 */
class AgentLoop(
    private val aiClient: AIClient,
    private val chatRepository: ChatRepository
) {
    companion object {
        private const val TAG = "AgentLoop"
        private const val MAX_STEPS = 10
        private const val SCREEN_SETTLE_DELAY_MS = 1500L
    }

    // Track if loop is running
    @Volatile
    var isRunning = false
        private set

    /**
     * 🔁 Execute a multi-step goal
     * @param userGoal The user's original request
     * @param executeAction Lambda to execute an action (from ViewModel)
     * @param maxSteps Maximum steps before stopping
     * @return Summary of what happened
     */
    suspend fun executeGoal(
        userGoal: String,
        executeAction: suspend (AiParsedResponse) -> Boolean,
        maxSteps: Int = MAX_STEPS
    ): AgentResult {
        isRunning = true
        val actionHistory = mutableListOf<ActionStep>()
        var currentStep = 0

        Log.d(TAG, "🔁 Starting Agent Loop: $userGoal (max $maxSteps steps)")
        chatRepository.saveSystemMessage("🤖 Agent Mode: Working on \"$userGoal\"...")

        try {
            while (currentStep < maxSteps && isRunning) {
                currentStep++
                Log.d(TAG, "📍 Step $currentStep/$maxSteps")

                // 1. Read current screen
                val screenContext = getScreenContext()

                // 2. Build prompt with history
                val agentPrompt = buildAgentPrompt(userGoal, screenContext, actionHistory, currentStep, maxSteps)

                // 3. Ask AI for next action
                val response = aiClient.sendAgentMessage(agentPrompt)

                // 4. Check if AI says DONE
                if (response.action.equals("DONE", ignoreCase = true) ||
                    response.action.equals("GENERAL", ignoreCase = true)) {
                    Log.d(TAG, "✅ Agent completed goal at step $currentStep")
                    chatRepository.saveSystemMessage("✅ Task complete! (${currentStep} steps)")
                    isRunning = false
                    return AgentResult(
                        success = true,
                        stepsUsed = currentStep,
                        message = response.message,
                        history = actionHistory
                    )
                }

                // 5. Execute the action
                chatRepository.saveSystemMessage("⚙️ Step $currentStep: ${response.action} → ${response.target ?: ""}")

                val actionSuccess = try {
                    executeAction(response)
                } catch (e: Exception) {
                    Log.e(TAG, "Action failed at step $currentStep: ${e.message}")
                    false
                }

                // 6. Record step
                actionHistory.add(ActionStep(
                    stepNumber = currentStep,
                    action = response.action,
                    target = response.target,
                    success = actionSuccess,
                    message = response.message
                ))

                // 7. Wait for screen to settle after action
                val settleTime = getSettleTime(response.action)
                delay(settleTime)

                // 8. Wait for screen to actually change
                waitForScreenChange(settleTime)
            }

            // Exceeded max steps
            isRunning = false
            chatRepository.saveSystemMessage("⏱️ Agent stopped after $maxSteps steps")
            return AgentResult(
                success = false,
                stepsUsed = currentStep,
                message = "Max steps reached",
                history = actionHistory
            )

        } catch (e: Exception) {
            isRunning = false
            Log.e(TAG, "Agent loop error: ${e.message}", e)
            return AgentResult(
                success = false,
                stepsUsed = currentStep,
                message = "Error: ${e.message}",
                history = actionHistory
            )
        }
    }

    /**
     * Stop the agent loop
     */
    fun stop() {
        isRunning = false
        Log.d(TAG, "🛑 Agent loop stopped by user")
    }

    /**
     * Get current screen context as formatted string
     */
    private fun getScreenContext(): String {
        val service = MyAccessibilityService.instance ?: return "Screen not available"

        return try {
            val elements = service.getScreenSnapshot()
            val activeApp = MyAccessibilityService.currentApp.value
            val elementsList = elements
                .filter { it.isClickable || it.isEditable || it.text.isNotBlank() || it.contentDescription.isNotBlank() }
                .take(25)
                .joinToString("\n") { element ->
                    val type = element.className.substringAfterLast(".")
                    val clickable = if (element.isClickable) "[Click]" else ""
                    val editable = if (element.isEditable) "[Edit]" else ""
                    val text = if (element.text.isNotBlank()) "'${element.text.take(40)}'" else ""
                    val desc = if (element.contentDescription.isNotBlank()) "desc='${element.contentDescription.take(30)}'" else ""
                    "  - $type: $text $desc $clickable $editable".trim()
                }
            "App: $activeApp\nUI:\n$elementsList"
        } catch (e: Exception) {
            "Screen read error: ${e.message}"
        }
    }

    /**
     * Build the agent prompt with full context
     */
    private fun buildAgentPrompt(
        goal: String,
        screenContext: String,
        history: List<ActionStep>,
        currentStep: Int,
        maxSteps: Int
    ): String {
        return buildString {
            appendLine("=== AGENT MODE ===")
            appendLine("GOAL: $goal")
            appendLine("Step: $currentStep / $maxSteps")
            appendLine()

            if (history.isNotEmpty()) {
                appendLine("PREVIOUS ACTIONS:")
                history.takeLast(5).forEach { step ->
                    val status = if (step.success) "✅" else "❌"
                    appendLine("  $status Step ${step.stepNumber}: ${step.action}(${step.target ?: ""}) → ${step.message}")
                }
                appendLine()
            }

            appendLine("CURRENT SCREEN:")
            appendLine(screenContext)
            appendLine()
            appendLine("INSTRUCTIONS:")
            appendLine("- Return the NEXT action to take towards the goal")
            appendLine("- Use action='DONE' when the goal is COMPLETE")
            appendLine("- Look at the screen elements and click/type what's needed")
            appendLine("- If an action failed before, try an alternative approach")
            appendLine("- Be precise with button text for CLICK actions")
        }
    }

    /**
     * Smart delay based on action type
     */
    private fun getSettleTime(action: String): Long {
        return when (action.uppercase()) {
            "OPEN_APP" -> 2500L
            "CLICK" -> 1000L
            "TYPE_TEXT" -> 500L
            "BACK", "HOME" -> 800L
            "SCROLL_DOWN", "SCROLL_UP" -> 600L
            "SEARCH" -> 2000L
            else -> SCREEN_SETTLE_DELAY_MS
        }
    }

    /**
     * ⏳ Wait for screen content to change (smart wait)
     * Instead of fixed delay, poll until screen tree changes or timeout
     */
    private suspend fun waitForScreenChange(maxWaitMs: Long) {
        val service = MyAccessibilityService.instance ?: return

        try {
            val initialText = MyAccessibilityService.screenText.value
            val startTime = System.currentTimeMillis()
            val timeout = maxWaitMs.coerceAtMost(3000L)

            while (System.currentTimeMillis() - startTime < timeout) {
                delay(200)
                val currentText = MyAccessibilityService.screenText.value
                if (currentText != initialText && currentText.isNotBlank()) {
                    Log.d(TAG, "Screen changed after ${System.currentTimeMillis() - startTime}ms")
                    return
                }
            }
        } catch (e: Exception) {
            // Ignore — just proceed
        }
    }
}

/**
 * Single step result
 */
data class ActionStep(
    val stepNumber: Int,
    val action: String,
    val target: String?,
    val success: Boolean,
    val message: String
)

/**
 * Agent loop final result
 */
data class AgentResult(
    val success: Boolean,
    val stepsUsed: Int,
    val message: String,
    val history: List<ActionStep>
)
