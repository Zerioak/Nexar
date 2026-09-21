package com.nexar.assistant.di

import android.content.Context
import com.nexar.assistant.ai.live.NexarLiveSessionManager
import com.nexar.assistant.ai.tools.NexarToolExecutor
import com.nexar.assistant.assistant.agent.NexarAgentController
import com.nexar.assistant.audio.NexarAudioManager
import com.nexar.assistant.memory.NexarMemoryRepository
import com.nexar.assistant.notifications.NexarNotificationRepository
import com.nexar.assistant.settings.NexarSettingsRepository

class NexarContainer(val context: Context) {

    val settingsRepository: NexarSettingsRepository by lazy {
        NexarSettingsRepository(context)
    }

    val memoryRepository: NexarMemoryRepository by lazy {
        NexarMemoryRepository(context)
    }

    val notificationRepository: NexarNotificationRepository by lazy {
        NexarNotificationRepository()
    }

    val audioManager: NexarAudioManager by lazy {
        NexarAudioManager(context)
    }

    val toolExecutor: NexarToolExecutor by lazy {
        NexarToolExecutor(context)
    }

    val liveSessionManager: NexarLiveSessionManager by lazy {
        NexarLiveSessionManager(context, settingsRepository, audioManager)
    }

    val agentController: NexarAgentController by lazy {
        NexarAgentController(liveSessionManager, toolExecutor)
    }
}
