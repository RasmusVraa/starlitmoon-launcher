package ru.starlitmoon.launcher.ui.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/** Вкл/выкл анимации UI (из настроек лаунчера). */
val LocalStarlitMotion = compositionLocalOf { true }

object StarlitMotion {
    const val FastMs = 140
    const val NormalMs = 220
    const val SlowMs = 320
}

@Composable
fun starlitMotionEnabled(): Boolean = LocalStarlitMotion.current

@Composable
fun <T> starlitTween(durationMs: Int = StarlitMotion.NormalMs): FiniteAnimationSpec<T> =
    if (LocalStarlitMotion.current) {
        tween(durationMs, easing = FastOutSlowInEasing)
    } else {
        snap()
    }

@Composable
fun starlitSpring(): AnimationSpec<Float> =
    if (LocalStarlitMotion.current) {
        spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)
    } else {
        snap()
    }

@Composable
fun starlitAnimateColor(target: Color, durationMs: Int = StarlitMotion.FastMs, label: String = "color"): Color {
    val enabled = LocalStarlitMotion.current
    val animated by animateColorAsState(
        targetValue = target,
        animationSpec = if (enabled) tween(durationMs, easing = FastOutSlowInEasing) else snap(),
        label = label,
    )
    return if (enabled) animated else target
}

@Composable
fun starlitAnimateDp(target: Dp, durationMs: Int = StarlitMotion.NormalMs, label: String = "dp"): Dp {
    val enabled = LocalStarlitMotion.current
    val animated by animateDpAsState(
        targetValue = target,
        animationSpec = if (enabled) tween(durationMs, easing = FastOutSlowInEasing) else snap(),
        label = label,
    )
    return if (enabled) animated else target
}

@Composable
fun starlitAnimateFloat(target: Float, durationMs: Int = StarlitMotion.NormalMs, label: String = "float"): Float {
    val enabled = LocalStarlitMotion.current
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = if (enabled) tween(durationMs, easing = FastOutSlowInEasing) else snap(),
        label = label,
    )
    return if (enabled) animated else target
}

@Composable
fun starlitExpandVertically(): EnterTransition =
    if (LocalStarlitMotion.current) {
        expandVertically(animationSpec = tween(StarlitMotion.NormalMs)) + fadeIn(tween(StarlitMotion.FastMs))
    } else {
        EnterTransition.None
    }

@Composable
fun starlitShrinkVertically(): ExitTransition =
    if (LocalStarlitMotion.current) {
        shrinkVertically(animationSpec = tween(StarlitMotion.FastMs)) + fadeOut(tween(StarlitMotion.FastMs))
    } else {
        ExitTransition.None
    }

@Suppress("UNUSED_PARAMETER")
fun <S> starlitTabTransition(
    enabled: Boolean,
    scope: AnimatedContentTransitionScope<S>,
    isForward: Boolean,
): ContentTransform {
    if (!enabled) {
        return EnterTransition.None togetherWith ExitTransition.None
    }
    val enter = fadeIn(tween(StarlitMotion.NormalMs)) + slideInHorizontally(
        animationSpec = tween(StarlitMotion.NormalMs),
        initialOffsetX = { full -> if (isForward) full / 28 else -full / 28 },
    )
    val exit = fadeOut(tween(StarlitMotion.FastMs)) + slideOutHorizontally(
        animationSpec = tween(StarlitMotion.FastMs),
        targetOffsetX = { full -> if (isForward) -full / 28 else full / 28 },
    )
    return enter togetherWith exit
}
