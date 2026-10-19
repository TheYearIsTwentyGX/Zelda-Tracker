package gx.zelda.android

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import gx.zelda.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val dataStore = (application as ZeldaApp).dataStore
        setContent { App(dataStore, onDarkTheme = ::matchSystemBars) }
    }

    /**
     * Light or dark system-bar icons to suit the app, which Settings can set apart from the system.
     * The scrims are enableEdgeToEdge's defaults.
     */
    private fun matchSystemBars(dark: Boolean) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(Color.argb(0xe6, 0xFF, 0xFF, 0xFF), Color.argb(0x80, 0x1b, 0x1b, 0x1b)) { dark },
        )
    }
}
