package com.group4.liveroo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.group4.liveroo.ui.nav.LiverooNavHost
import com.group4.liveroo.ui.theme.LiverooTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as LiverooApp).container
        setContent {
            LiverooTheme {
                CompositionLocalProvider(LocalContainer provides container) {
                    LiverooNavHost()
                }
            }
        }
    }
}
