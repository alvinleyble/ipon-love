package com.iponlove.app.core.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * A generic, feature-agnostic coach-mark (guided-tooltip) engine — the reusable primitive behind
 * the first-run tutorial (ADR-0034), living in `core/ui` so any feature can drive a walkthrough,
 * not just the tutorial. It knows nothing about navigation, modules, or Ipon domain concepts: a
 * caller tags targets with [coachMarkTarget], feeds the current [CoachMarkStep], and handles the
 * skip / primary callbacks.
 *
 * Deliberately a *simple anchored tooltip*, not a dimmed-cutout spotlight (ADR-0034 decision 2):
 * the overlay draws only a highlight ring + a tooltip card, and never intercepts touches outside
 * the card — so the real UI underneath stays fully tappable. That is what lets the tutorial
 * advance by *observing a real tap* (e.g. the user actually opening the More sheet) rather than
 * driving navigation itself (ADR-0034 decision 3).
 */
@Stable
class CoachMarkState {
    /** Latest root-space bounds reported by each tagged target, keyed by the caller's step key. */
    private val bounds = mutableStateMapOf<String, Rect>()

    internal fun register(key: String, rect: Rect) {
        bounds[key] = rect
    }

    internal fun boundsOf(key: String): Rect? = bounds[key]
}

/**
 * Tags this node as a coach-mark target under [key], reporting its root-space bounds to [state]
 * so the overlay can anchor a tooltip to it. Reporting only — never intercepts input.
 */
fun Modifier.coachMarkTarget(key: String, state: CoachMarkState): Modifier =
    this.onGloballyPositioned { coords ->
        if (coords.isAttached) state.register(key, coords.boundsInRoot())
    }

/**
 * A drives-the-whole-walkthrough handle the app shell shares down to every feature screen through
 * [LocalTutorialController], so a screen can arm its first-visit tour and report a real target tap
 * without knowing anything about the tutorial ViewModel or Hilt scoping (ADR-0038 dec. 3).
 */
interface TutorialController {
    /** Start [tourId]'s tour if it's unseen, no other tour is active, and its paired-gate passes. */
    fun maybeStartTour(tourId: String)

    /** Tell the active tour that [targetKey] was really operated (advances wait-for-tap steps). */
    fun onTargetActivated(targetKey: String)
}

/** The shell's single [CoachMarkState], shared down so any screen can tag its own targets. */
val LocalCoachMarkState = staticCompositionLocalOf<CoachMarkState?> { null }

/** The shell's [TutorialController], shared down so any screen can arm its first-visit tour. */
val LocalTutorialController = staticCompositionLocalOf<TutorialController?> { null }

/**
 * Composable overload that reads the shared [LocalCoachMarkState] — the form feature screens use so
 * they don't have to thread the state through every call site. A no-op when no state is provided
 * (e.g. previews), so tagging is always safe to leave in.
 */
@Composable
fun Modifier.coachMarkTarget(key: String): Modifier {
    val state = LocalCoachMarkState.current ?: return this
    return this.coachMarkTarget(key, state)
}

/**
 * Arms the given tours the first time a screen composes (ADR-0038 dec. 3): fires each `maybeStart`
 * through the shared [LocalTutorialController], which handles seen-set gating, the paired gate, and
 * the single-active-tour guard. Safe to place unconditionally at the top of any screen.
 */
@Composable
fun StartTourOnFirstVisit(vararg tourIds: String) {
    val controller = LocalTutorialController.current ?: return
    LaunchedEffect(Unit) { tourIds.forEach { controller.maybeStartTour(it) } }
}

/** One step of a walkthrough: which target to highlight and what the tooltip says. */
data class CoachMarkStep(
    val targetKey: String,
    val text: String,
    val title: String? = null,
    /** Small progress hint, e.g. "1 of 3". */
    val stepLabel: String? = null,
    /**
     * Label for the primary advance button. `null` hides it — for steps that advance only once
     * the caller observes the real action happen (ADR-0034 decision 3).
     */
    val primaryLabel: String? = null,
)

/**
 * Renders the highlight ring + anchored tooltip for [step] over whatever is beneath it. Draws
 * nothing (and consumes no touches) when [step] is null or its target hasn't been laid out yet.
 * Place this as the last child of a full-size [androidx.compose.foundation.layout.Box] wrapping
 * the screen content so it shares the same coordinate root as the tagged targets.
 */
@Composable
fun CoachMarkOverlay(
    state: CoachMarkState,
    step: CoachMarkStep?,
    onPrimary: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (step == null) return
    // Anchor to the target when it's laid out; otherwise degrade to a centered tooltip (no ring) so
    // a step whose target isn't on-screen yet — or at all, given a screen's conditional chrome —
    // never leaves the user trapped with no card to advance or skip.
    val target = state.boundsOf(step.targetKey)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val rootW = constraints.maxWidth.toFloat()
        val rootH = constraints.maxHeight.toFloat()
        val density = androidx.compose.ui.platform.LocalDensity.current
        val gapPx = with(density) { 12.dp.toPx() }
        val padPx = with(density) { 16.dp.toPx() }
        val maxCardWidthPx = with(density) { 300.dp.toPx() }

        // Highlight ring around the target — no pointer input, so the real target beneath stays
        // tappable (the overlay is a later Box sibling but hit-testing falls through it).
        if (target != null) {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .offset { IntOffset(target.left.roundToInt(), target.top.roundToInt()) }
                    .size(
                        width = with(density) { target.width.toDp() },
                        height = with(density) { target.height.toDp() },
                    )
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)),
            )
        }

        // Tooltip card, anchored above the target when it sits in the lower half of the screen
        // (the common case for a bottom-bar target), otherwise below it. With no target, centered.
        // Positioned via a layout modifier so the measured card size is known before placement.
        val placeAbove = target != null && target.center.y > rootH / 2f
        androidx.compose.foundation.layout.Box(
            Modifier.layout { measurable, c ->
                val cardConstraints = Constraints(maxWidth = maxCardWidthPx.roundToInt())
                val placeable = measurable.measure(cardConstraints)
                val y = when {
                    target == null -> (rootH - placeable.height) / 2f
                    placeAbove -> target.top - gapPx - placeable.height
                    else -> target.bottom + gapPx
                }
                val x = if (target == null) {
                    (rootW - placeable.width) / 2f
                } else {
                    target.center.x - placeable.width / 2f
                }
                val yClamped = y.coerceIn(padPx, (rootH - placeable.height - padPx).coerceAtLeast(padPx))
                val xClamped = x.coerceIn(padPx, (rootW - placeable.width - padPx).coerceAtLeast(padPx))
                layout(c.maxWidth, c.maxHeight) {
                    placeable.place(xClamped.roundToInt(), yClamped.roundToInt())
                }
            },
        ) {
            CoachMarkTooltip(step = step, onPrimary = onPrimary, onSkip = onSkip)
        }
    }
}

@Composable
private fun CoachMarkTooltip(
    step: CoachMarkStep,
    onPrimary: () -> Unit,
    onSkip: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.widthIn(max = 300.dp).padding(16.dp)) {
            step.stepLabel?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
            }
            step.title?.let {
                Text(it, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
            }
            Text(
                step.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onSkip) { Text("Skip") }
                step.primaryLabel?.let {
                    Button(onClick = onPrimary) { Text(it) }
                }
            }
        }
    }
}
