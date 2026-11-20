package com.prudent66.vetguide

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.prudent66.vetguide.ui.components.BrandSplash
import com.prudent66.vetguide.ui.theme.VETGUIDETheme
import dagger.hilt.android.AndroidEntryPoint

/** Single-activity host: all screens are Compose destinations. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 12+ system splash (brand-green field + medical cross) hands
        // over to the Compose overlay that fades the VETGUIDE wordmark in.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VETGUIDETheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    RootContent()
                }
            }
        }
    }
}

/** App content with the brand overlay on top until the wordmark hold elapses. */
@Composable
private fun RootContent() {
    var showSplash by remember { mutableStateOf(true) }
    Box(modifier = Modifier.fillMaxSize()) {
        VetguideApp()
        AnimatedVisibility(
            visible = showSplash,
            exit = fadeOut(animationSpec = tween(durationMillis = 350)),
        ) {
            BrandSplash(onFinished = { showSplash = false })
        }
    }
}
