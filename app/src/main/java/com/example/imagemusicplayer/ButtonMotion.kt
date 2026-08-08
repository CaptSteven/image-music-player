package com.example.imagemusicplayer

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import java.util.WeakHashMap

object ButtonMotion {
    private const val PRESSED_SCALE = 0.96f
    private const val PRESS_DURATION_MS = 90L
    private const val RELEASE_DURATION_MS = 160L
    private val runningAnimators = WeakHashMap<View, AnimatorSet>()

    @JvmStatic
    fun attach(view: View) {
        view.isClickable = true
        view.setOnTouchListener { touchedView, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> animateTo(touchedView, PRESSED_SCALE, dp(touchedView, 1f), PRESS_DURATION_MS)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> animateTo(touchedView, 1f, 0f, RELEASE_DURATION_MS)
            }
            false
        }
    }

    private fun animateTo(view: View, scale: Float, translationZ: Float, durationMs: Long) {
        runningAnimators.remove(view)?.cancel()

        val scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, view.scaleX, scale)
        val scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, view.scaleY, scale)
        val lift = ObjectAnimator.ofFloat(view, View.TRANSLATION_Z, view.translationZ, translationZ)
        val progress = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator()
        }

        AnimatorSet().apply {
            playTogether(scaleX, scaleY, lift, progress)
            duration = durationMs
            interpolator = DecelerateInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (runningAnimators[view] == animation) {
                        runningAnimators.remove(view)
                    }
                }
            })
            runningAnimators[view] = this
            start()
        }
    }

    private fun dp(view: View, value: Float): Float {
        return value * view.resources.displayMetrics.density
    }
}
