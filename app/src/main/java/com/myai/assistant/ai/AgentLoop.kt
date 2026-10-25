// File: app/src/main/java/com/myai/assistant/ai/AgentLoop.kt
// Agent Loop — Multi-step autonomous agent for complex tasks
// Read Screen → Decide → Act → Read Again → Decide → Act... until goal is done
// Optimized: Loop detection, intelligent screen settlement & verification, max step summaries, immediate cancellation

package com.myai.assistant.ai

import android.util.Log
import com.myai.assistant.accessibility.MyAccessibilityService
import com.myai.assistant.ai.models.AiParsedResponse
import com.myai.assistant.data.repository.ChatRepository
import kotlinx.coroutines.delay

/**
 * AgentLoop — Multi-step autonomous control loop
 *
 * 1. Understand user goal
 * 2. Read screen layout & interactive elements with coordinates
 * 3. AI decides next step
 * 4. Execute action with cancellation safety
 * 5. Verify screen change and wait for UI settlement
 * 6. Loop detection guards against repetitive actions/stagnant states
 * 7. Cleanly halt on max steps or user stop with detailed execution summary
 */
class AgentLoop(
    private val aiClient: AIClient,
    private val chatRepository: ChatRepository
) {
    companion object {
        private const val TAG = "AgentLoop"
        private const val MAX_STEPS = 10
        private const val SCREEN_SETTLE_DELAY_MS = 1500L
        private const val MAX_IDENTICAL_ACTIONS = 3
        private const val MAX_IDENTICAL_SCREEN_STATES = 3
    }

    // Track if loop is running
    @Volatile
    var isRunning = false
        private set

    /**
     * Stop the agent loop immediately
     */
    fun stop() {
        isRunning = false
        Log.d(TAG, "🛑 Agent loop stop requested by user")
    }

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
        val actionSignatures = mutableListOf<String>()
        val screenSignatures = mutableListOf<String>()
        var currentStep = 0
        var loopWarningMessage: String? = null

        Log.d(TAG, "🔁 Starting Agent Loop: $userGoal (max $maxSteps steps)")
        chatRepository.saveSystemMessage("🤖 Agent Mode: Working on \"$userGoal\"...")

        try {
            while (currentStep < maxSteps && isRunning) {
                // Immediate cancellation check
                if (!isRunning) break

                currentStep++
                Log.d(TAG, "📍 Step $currentStep/$maxSteps")

                // 1. Read current screen & compute signature
                val screenContext = getScreenContext()
                val currentScreenSig = getScreenSignature()

                // Check for infinite screen state loop (same screen repeated)
                screenSignatures.add(currentScreenSig)
                val sameScreenOccurrences = screenSignatures.count { it == currentScreenSig }
                if (sameScreenOccurrences >= MAX_IDENTICAL_SCREEN_STATES && currentStep > 2) {
                    val lastAction = actionHistory.lastOrNull()
                    if (lastAction != null && !lastAction.screenChanged) {
                        Log.w(TAG, "⚠️ Loop detected: Screen unchanged for $sameScreenOccurrences steps")
                        loopWarningMessage = "WARNING: Screen has not changed after multiple actions. Try an alternative action (scroll, coordinate click, or BACK)."
                    }
                }

                // 2. Build prompt with history and loop guards
                val agentPrompt = buildAgentPrompt(
                    goal = userGoal,
                    screenContext = screenContext,
                    history = actionHistory,
                    currentStep = currentStep,
                    maxSteps = maxSteps,
                    loopWarning = loopWarningMessage
                )

                // Immediate cancellation check before calling AI
                if (!isRunning) break

                // 3. Ask AI for next action
                val response = aiClient.sendAgentMessage(agentPrompt)

                // Immediate cancellation check after AI response
                if (!isRunning) break

                // 4. Check if AI says DONE or finished
                if (response.action.equals("DONE", ignoreCase = true) ||
                    response.action.equals("GENERAL", ignoreCase = true)
                ) {
                    Log.d(TAG, "✅ Agent completed goal at step $currentStep")
                    isRunning = false
                    val summary = generateSummary(actionHistory, userGoal, true, "Goal completed successfully.")
                    chatRepository.saveSystemMessage("✅ Task complete! (${currentStep} steps)\n$summary")
                    return AgentResult(
                        success = true,
                        stepsUsed = currentStep,
                        message = response.message,
                        history = actionHistory,
                        summary = summary
                    )
                }

                // 5. Loop Detection: Check for repeated identical actions
                val actionKey = "${response.action.uppercase()}:${response.target?.trim()?.lowercase() ?: ""}"
                actionSignatures.add(actionKey)
                val identicalRecentActions = actionSignatures.takeLast(MAX_IDENTICAL_ACTIONS).count { it == actionKey }

                if (identicalRecentActions >= MAX_IDENTICAL_ACTIONS) {
                    Log.e(TAG, "🛑 Halting agent: Infinite action loop detected on '$actionKey'")
                    isRunning = false
                    val abortMsg = "Loop detected: Repeated action '${response.action}' with target '${response.target ?: ""}' 3 times without progress."
                    val summary = generateSummary(actionHistory, userGoal, false, abortMsg)
                    chatRepository.saveSystemMessage("🛑 Agent aborted: $abortMsg")
                    return AgentResult(
                        success = false,
                        stepsUsed = currentStep,
                        message = abortMsg,
                        history = actionHistory,
                        summary = summary
                    )
                }

                // Immediate cancellation check before action execution
                if (!isRunning) break

                // 6. Execute the action
                chatRepository.saveSystemMessage("⚙️ Step $currentStep: ${response.action} → ${response.target ?: ""}")

                val preActionSig = currentScreenSig
                val actionSuccess = try {
                    executeAction(response)
                } catch (e: Exception) {
                    Log.e(TAG, "Action failed at step $currentStep: ${e.message}")
                    false
                }

                // Immediate cancellation check after action execution
                if (!isRunning) break

                // 7. Intelligent screen settlement & change verification
                val settleTime = getSettleTime(response.action)
                val screenChanged = verifyScreenChange(preActionSig, settleTime)

                // 8. Record step
                val stepRecord = ActionStep(
                    stepNumber = currentStep,
                    action = response.action,
                    target = response.target,
                    success = actionSuccess,
                    message = response.message,
                    screenChanged = screenChanged
                )
                actionHistory.add(stepRecord)

                // Set warning for next step if no screen change occurred on interactive action
                loopWarningMessage = if (!screenChanged && response.action.uppercase() in listOf("CLICK", "TYPE_TEXT", "OPEN_APP", "SEARCH")) {
                    "Note: Last action '${response.action}(${response.target ?: ""})' did not produce a visible screen change. If button was not tapped, verify target name or try scrolling/coordinates."
                } else {
                    null
                }
            }

            // Loop ended: Check if stopped by user or max steps
            if (!isRunning) {
                Log.d(TAG, "🛑 Agent loop terminated due to user stop")
                val summary = generateSummary(actionHistory, userGoal, false, "Cancelled by user.")
                chatRepository.saveSystemMessage("🛑 Agent stopped: Task was cancelled by user.")
                return AgentResult(
                    success = false,
                    stepsUsed = currentStep,
                    message = "Cancelled by user",
                    history = actionHistory,
                    summary = summary
                )
            }

            // Exceeded max steps limit
            isRunning = false
            val maxStepsMsg = "Max step limit reached ($maxSteps steps)."
            val summary = generateSummary(actionHistory, userGoal, false, maxStepsMsg)
            Log.w(TAG, "⏱️ Agent halted: $maxStepsMsg")
            chatRepository.saveSystemMessage(summary)

            return AgentResult(
                success = false,
                stepsUsed = currentStep,
                message = maxStepsMsg,
                history = actionHistory,
                summary = summary
            )

        } catch (e: Exception) {
            isRunning = false
            Log.e(TAG, "Agent loop error: ${e.message}", e)
            val errorSummary = generateSummary(actionHistory, userGoal, false, "Error: ${e.message}")
            return AgentResult(
                success = false,
                stepsUsed = currentStep,
                message = "Error: ${e.message}",
                history = actionHistory,
                summary = errorSummary
            )
        }
    }

    /**
     * Get current screen context formatted with interactive elements and coordinates
     */
    private fun getScreenContext(): String {
        val service = MyAccessibilityService.instance ?: return "Screen not available"

        return try {
            val elements = service.getScreenSnapshot()
            val activeApp = MyAccessibilityService.currentApp.value
            val elementsList = elements
                .filter { it.isClickable || it.isEditable || it.text.isNotBlank() || it.contentDescription.isNotBlank() }
                .take(30)
                .joinToString("\n") { element ->
                    val type = element.className.substringAfterLast(".")
                    val clickable = if (element.isClickable) "[Click]" else ""
                    val editable = if (element.isEditable) "[Edit]" else ""
                    val text = if (element.text.isNotBlank()) "'${element.text.take(40)}'" else ""
                    val desc = if (element.contentDescription.isNotBlank()) "desc='${element.contentDescription.take(30)}'" else ""
                    val coords = if (!element.bounds.isEmpty) "coords=(${element.centerX},${element.centerY})" else ""
                    "  - $type: $text $desc $clickable $editable $coords".trim()
                }
            "App: $activeApp\nUI Elements:\n$elementsList"
        } catch (e: Exception) {
            "Screen read error: ${e.message}"
        }
    }

    /**
     * Create a lightweight signature of current screen to detect state transitions
     */
    private fun getScreenSignature(): String {
        val service = MyAccessibilityService.instance ?: return ""
        val app = MyAccessibilityService.currentApp.value
        val screenText = MyAccessibilityService.screenText.value
        val elements = try {
            service.getScreenSnapshot()
                .take(8)
                .joinToString(";") { "${it.displayText}:${it.centerX},${it.centerY}" }
        } catch (_: Exception) { "" }
        return "$app|$elements|${screenText.hashCode()}"
    }

    /**
     * Build the agent prompt with full context and loop detection advisories
     */
    private fun buildAgentPrompt(
        goal: String,
        screenContext: String,
        history: List<ActionStep>,
        currentStep: Int,
        maxSteps: Int,
        loopWarning: String?
    ): String {
        return buildString {
            appendLine("=== AGENT MODE ===")
            appendLine("GOAL: $goal")
            appendLine("Step: $currentStep / $maxSteps")
            appendLine()

            if (!loopWarning.isNullOrBlank()) {
                appendLine("⚠️ $loopWarning")
                appendLine()
            }

            if (history.isNotEmpty()) {
                appendLine("PREVIOUS ACTIONS:")
                history.takeLast(5).forEach { step ->
                    val status = if (step.success) "✅" else "❌"
                    val change = if (step.screenChanged) "" else " [NO_SCREEN_CHANGE]"
                    appendLine("  $status Step ${step.stepNumber}: ${step.action}(${step.target ?: ""})$change → ${step.message}")
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
            appendLine("- If an action failed or caused no screen change, try an alternative approach or scroll")
            appendLine("- Be precise with button text for CLICK actions")
        }
    }

    /**
     * Smart settlement delay based on action type
     */
    private fun getSettleTime(action: String): Long {
        return when (action.uppercase()) {
            "OPEN_APP" -> 2200L
            "CLICK" -> 900L
            "TYPE_TEXT" -> 500L
            "BACK", "HOME" -> 800L
            "SCROLL_DOWN", "SCROLL_UP" -> 700L
            "SEARCH" -> 1800L
            else -> SCREEN_SETTLE_DELAY_MS
        }
    }

    /**
     * ⏳ Verify if screen actually changed after an action
     * Polls efficiently instead of sleeping blindly
     */
    private suspend fun verifyScreenChange(
        initialSignature: String,
        maxWaitMs: Long
    ): Boolean {
        val startTime = System.currentTimeMillis()
        val timeout = maxWaitMs.coerceIn(600L, 3000L)

        while (System.currentTimeMillis() - startTime < timeout) {
            if (!isRunning) return false

            delay(150)

            val currentSig = getScreenSignature()
            if (currentSig.isNotBlank() && currentSig != initialSignature) {
                Log.d(TAG, "Screen change verified in ${System.currentTimeMillis() - startTime}ms")
                // Short settling buffer for animations
                delay(200)
                return true
            }
        }

        return false
    }

    /**
     * Build clean markdown summary of completed steps
     */
    private fun generateSummary(
        history: List<ActionStep>,
        goal: String,
        isSuccess: Boolean,
        statusNote: String
    ): String {
        return buildString {
            appendLine(if (isSuccess) "✅ Task Completed" else "⏱️ Task Stopped")
            appendLine("Goal: \"$goal\"")
            appendLine("Status: $statusNote")
            appendLine("Total steps executed: ${history.size}")
            if (history.isNotEmpty()) {
                appendLine("\nStep History:")
                history.forEach { step ->
                    val statusIcon = if (step.success) "✓" else "✗"
                    val changeIcon = if (step.screenChanged) "📱" else "⚠️ (no change)"
                    appendLine(" ${step.stepNumber}. [$statusIcon] ${step.action}(${step.target ?: ""}) $changeIcon")
                }
            }
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
    val message: String,
    val screenChanged: Boolean = true
)

/**
 * Agent loop final result
 */
data class AgentResult(
    val success: Boolean,
    val stepsUsed: Int,
    val message: String,
    val history: List<ActionStep>,
    val summary: String = ""
)
