package com.github.tomasbjerre.wisp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.github.tomasbjerre.wisp.ui.WispApp
import com.github.tomasbjerre.wisp.ui.theme.WispTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WispTheme {
                val app = application as WispApplication
                WispApp(repository = app.repository, voiceFeedbackPreferences = app.voiceFeedbackPreferences)
            }
        }
    }
}
