package com.chinoisfacile.mandarin

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chinoisfacile.mandarin.data.model.ThemeMode
import com.chinoisfacile.mandarin.ui.navigation.AppNavHost
import com.chinoisfacile.mandarin.ui.theme.ChinoisFacileTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as MandarinApplication).container
        setContent {
            val themeMode by container.repository.observeThemeMode()
                .collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)

            val isDarkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            DisposableEffect(isDarkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDarkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (isDarkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                )
                onDispose {}
            }

            ChinoisFacileTheme(darkTheme = isDarkTheme) {
                AppNavHost(container = container)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        (application as MandarinApplication).container.stopAudio()
    }
}
