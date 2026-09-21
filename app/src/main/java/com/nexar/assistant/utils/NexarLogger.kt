package com.nexar.assistant.utils

import android.util.Log
import com.nexar.assistant.BuildConfig

object NexarLogger {
    private const val APP_TAG = "NEXAR"
    private val debugEnabled = BuildConfig.DEBUG

    fun d(tag: String, message: String) {
        if (debugEnabled) Log.d("$APP_TAG/$tag", message)
    }

    fun i(tag: String, message: String) {
        Log.i("$APP_TAG/$tag", message)
    }

    fun w(tag: String, message: String) {
        Log.w("$APP_TAG/$tag", message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e("$APP_TAG/$tag", message, throwable)
        } else {
            Log.e("$APP_TAG/$tag", message)
        }
    }
}
