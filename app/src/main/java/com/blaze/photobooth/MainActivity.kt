package com.blaze.photobooth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blaze.photobooth.core.LocalBoothSettings
import com.blaze.photobooth.data.prefs.BoothSettings
import com.blaze.photobooth.ui.navigation.BoothNavHost
import com.blaze.photobooth.ui.theme.BoothTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val preferences = (application as BoothApp).container.boothPreferences
        setContent {
            // Settings' switches (§4.8) reach every screen, e.g. Haptics.
            val settings by preferences.settings.collectAsStateWithLifecycle(BoothSettings())
            BoothTheme {
                CompositionLocalProvider(LocalBoothSettings provides settings) {
                    BoothNavHost()
                }
            }
        }
    }
}
