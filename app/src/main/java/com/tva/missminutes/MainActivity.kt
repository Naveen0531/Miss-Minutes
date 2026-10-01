package com.tva.missminutes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.tva.missminutes.ui.screen.AssistantScreen
import com.tva.missminutes.ui.screen.SplashScreen
import com.tva.missminutes.ui.theme.MissMinutesTheme
import com.tva.missminutes.ui.theme.TVABackground

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MissMinutesTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = TVABackground
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

/**
 * Root navigation: Splash screen → Main assistant screen.
 * Uses AnimatedContent for a smooth crossfade transition.
 */
@Composable
private fun AppNavigation() {
    var showSplash by remember { mutableStateOf(true) }

    AnimatedContent(
        targetState  = showSplash,
        transitionSpec = {
            if (targetState) {
                // Entering splash (app just opened)
                fadeIn(tween(300)).togetherWith(fadeOut(tween(300)))
            } else {
                // Transitioning from splash → main
                (fadeIn(tween(600)) + slideInVertically(tween(600)) { it / 8 })
                    .togetherWith(fadeOut(tween(400)))
            }
        },
        label = "appNav"
    ) { isSplash ->
        if (isSplash) {
            SplashScreen(onComplete = { showSplash = false })
        } else {
            AssistantScreen()
        }
    }
}
