package com.nexar.assistant

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.navigation.compose.rememberNavController
import com.nexar.assistant.service.NexarForegroundService
import com.nexar.assistant.ui.navigation.NexarNavHost
import com.nexar.assistant.ui.theme.NexarTheme
import com.nexar.assistant.ui.viewmodel.NexarViewModel
import com.nexar.assistant.utils.NexarLogger

class MainActivity : ComponentActivity() {

    private val TAG = "MainActivity"
    private val viewModel: NexarViewModel by viewModels()

    private val screenShareLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            viewModel.onScreenShareGranted(result.resultCode, result.data!!)
        } else {
            NexarLogger.d(TAG, "Screen share permission denied by user")
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        NexarLogger.d(TAG, "Permissions result: $permissions")
        viewModel.refreshPermissionStates()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS))
        }

        // Start the foreground service
        startNexarService()

        setContent {
            NexarTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF050A14)
                ) {
                    val navController = rememberNavController()

                    // Observe screen share requests
                    LaunchedEffect(Unit) {
                        viewModel.requestScreenShare.collect { intent ->
                            screenShareLauncher.launch(intent)
                        }
                    }

                    NexarNavHost(
                        navController = navController,
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissionStates()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun startNexarService() {
        try {
            val serviceIntent = Intent(this, NexarForegroundService::class.java).apply {
                action = NexarForegroundService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            NexarLogger.e(TAG, "Failed to start foreground service", e)
        }
    }
}
