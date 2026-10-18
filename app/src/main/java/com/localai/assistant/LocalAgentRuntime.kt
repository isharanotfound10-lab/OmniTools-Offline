package com.localai.assistant

import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine

/**
 * Process-wide owner for the on-device agent. The floating orb and the main
 * screen use the same conversation, so the orb can use the loaded model too.
 */
object LocalAgentRuntime {
    @Volatile private var engine: Engine? = null
    @Volatile private var conversation: Conversation? = null

    @Synchronized
    fun install(newEngine: Engine, newConversation: Conversation) {
        val oldConversation = conversation
        val oldEngine = engine
        conversation = newConversation
        engine = newEngine
        try { oldConversation?.close() } catch (_: Exception) {}
        try { oldEngine?.close() } catch (_: Exception) {}
    }

    @Synchronized
    fun isLoaded(): Boolean = conversation != null

    /** Call from a worker thread; serialized to protect the conversation runtime. */
    @Synchronized
    fun executeWithModel(command: String): String? {
        val active = conversation ?: return null
        return active.sendMessage(command).toString().trim().ifEmpty { "The agent returned no result." }
    }
}
