package com.nexar.assistant.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nexar.assistant.utils.NexarLogger

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            NexarLogger.d("BootReceiver", "Boot completed - NEXAR ready")
            // Service starts on user demand, not automatically on boot
            // This receiver exists for future auto-start capability
        }
    }
}
