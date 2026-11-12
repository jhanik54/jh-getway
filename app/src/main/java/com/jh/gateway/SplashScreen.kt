package com.jh.gateway

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Branded cold-launch splash, per docs/UI_SPLASH_ANIMATION_SPEC.md.
 *
 * One deliberate motion (~2.3s): JH GetWay fades in below the center, rises to a
 * controlled upper position, settles back to center, then JH Soft Corporation fades in.
 * Runs once per cold launch (the caller keeps the "done" flag), never on ordinary resume,
 * honors the reduced-motion preference, and blocks nothing — the gateway and UI
 * compose behind it. No fake progress is shown.
 */
@Composable
fun SplashOverlay(darkTheme: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    val reducedMotion = remember { animationsDisabled(context) }

    // 0.dp = content vertically centered; positive = below center; negative = upper position.
    val offsetDp = remember { Animatable(140f) }
    val contentAlpha = remember { Animatable(0f) }
    val brandAlpha = remember { Animatable(0f) }
    val overlayAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        if (reducedMotion) {
            offsetDp.snapTo(0f)
            contentAlpha.snapTo(1f)
            brandAlpha.snapTo(1f)
            delay(600)
            overlayAlpha.animateTo(0f, tween(150))
            onDone()
            return@LaunchedEffect
        }
        // Fade in below the center.
        contentAlpha.animateTo(1f, tween(250, easing = FastOutSlowInEasing))
        // Rise to the controlled upper position.
        offsetDp.animateTo(-96f, tween(750, easing = FastOutSlowInEasing))
        // Return to final center position with a restrained settle.
        offsetDp.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
        // Reveal the publisher line.
        brandAlpha.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
        delay(220)
        overlayAlpha.animateTo(0f, tween(320, easing = FastOutSlowInEasing))
        onDone()
    }

    val background = if (darkTheme) Color(0xFF111827) else Color(0xFFF9FAFB)
    val foreground = if (darkTheme) Color.White else Color(0xFF111827)
    val muted = if (darkTheme) Color(0xFF9CA3AF) else Color(0xFF667085)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(overlayAlpha.value)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .offset(y = offsetDp.value.dp)
                .alpha(contentAlpha.value)
        ) {
            Image(
                painter = painterResource(R.drawable.jh_getway_logo),
                contentDescription = stringResource(R.string.str_splash_logo_description_7a3e90),
                modifier = Modifier.size(96.dp)
            )
            Text(
                text = stringResource(R.string.str_jh_getway_d50ce4),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = foreground
            )
        }
        Text(
            text = stringResource(R.string.str_jh_soft_corporation_4f2b1c),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = muted,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
                .alpha(brandAlpha.value)
        )
    }
}

/** True when the user disabled system animator durations (reduced motion). */
private fun animationsDisabled(context: Context): Boolean = runCatching {
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}.getOrDefault(false)
