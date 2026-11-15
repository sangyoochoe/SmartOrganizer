package com.example.smartorganizer

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import androidx.window.java.layout.WindowInfoTrackerCallbackAdapter
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.function.Consumer

/**
 * Reports the current app-window posture using AndroidX WindowManager.
 * Width is used only as a fallback when the OS does not expose a folding feature
 * (for example, on a non-foldable device or a cover-display window).
 */
class FoldStateMonitor(
    private val activity: Activity,
    private val onChanged: (State) -> Unit
) {
    enum class Posture { COVER_OR_COMPACT, BOOK, TABLETOP, OPEN_OR_FLAT }

    data class State(
        val posture: Posture,
        val isSeparating: Boolean,
        val hingeBounds: android.graphics.Rect?,
        val widthDp: Int,
        val heightDp: Int
    )

    private val tracker = WindowInfoTrackerCallbackAdapter(WindowInfoTracker.getOrCreate(activity))
    private val executor: Executor = Executors.newSingleThreadExecutor()
    private val listener = Consumer<WindowLayoutInfo> { info ->
        val feature = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
        val metrics = activity.resources.displayMetrics
        val widthDp = (metrics.widthPixels / metrics.density).toInt()
        val heightDp = (metrics.heightPixels / metrics.density).toInt()
        val posture = when {
            feature?.state == FoldingFeature.State.HALF_OPENED &&
                feature.orientation == FoldingFeature.Orientation.HORIZONTAL -> Posture.TABLETOP
            feature?.state == FoldingFeature.State.HALF_OPENED -> Posture.BOOK
            feature?.state == FoldingFeature.State.FLAT -> Posture.OPEN_OR_FLAT
            widthDp >= 600 -> Posture.OPEN_OR_FLAT
            else -> Posture.COVER_OR_COMPACT
        }
        onChanged(State(posture, feature?.isSeparating == true, feature?.bounds, widthDp, heightDp))
    }

    fun start() {
        tracker.addWindowLayoutInfoListener(activity, executor, listener)
    }

    fun stop() {
        tracker.removeWindowLayoutInfoListener(listener)
    }
}
