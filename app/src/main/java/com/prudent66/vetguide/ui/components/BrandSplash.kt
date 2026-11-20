package com.prudent66.vetguide.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prudent66.vetguide.R
import com.prudent66.vetguide.ui.theme.ForestGreen
import kotlinx.coroutines.delay

private const val WORDMARK_FADE_MS = 700
private const val SPLASH_HOLD_MS = 1_500L

/**
 * In-app brand splash drawn over the first Compose frame (the Android 12+
 * system splash cannot render text): forest-green field, the Phase-0 medical
 * cross, and the VETGUIDE wordmark fading in. [onFinished] fires once the hold
 * elapses; the caller fades the whole overlay out.
 */
@Composable
fun BrandSplash(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    val wordmarkAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = WORDMARK_FADE_MS),
        label = "splash-wordmark-alpha",
    )

    LaunchedEffect(Unit) {
        visible = true
        delay(SPLASH_HOLD_MS)
        onFinished()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ForestGreen),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_splash_logo),
            contentDescription = null,
            modifier = Modifier.size(112.dp),
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.app_name),
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 6.sp,
            modifier = Modifier.alpha(wordmarkAlpha),
        )
    }
}
