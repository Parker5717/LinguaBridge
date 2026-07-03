package com.linguabridge.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linguabridge.app.ui.LinguaBridgeRoot
import com.linguabridge.app.ui.theme.LinguaBridgeTheme

// AppCompatActivity (not ComponentActivity) so that
// AppCompatDelegate.setApplicationLocales handles the in-app RU/EN switch.
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as LinguaBridgeApp).container
        setContent {
            val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
            val darkTheme = when (settings?.theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            LinguaBridgeTheme(darkTheme = darkTheme) {
                LinguaBridgeRoot()
            }
        }
    }
}
