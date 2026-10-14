/*
 * Custom
 *
 * Mirrors the timing and looks in ios/JBTextTransitionAnimator.mm; keep both in sync.
 */

package com.reactnativereanimatedtext

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

internal data class SegmentLook(
    val translateX: Float = 0f,
    val translateY: Float = 0f,
    val scale: Float = 1f,
    val opacity: Float = 1f,
    /** 0…1 of the maximum blur. */
    val blur: Float = 0f,
)

internal data class SegmentMotion(val offsetFactor: Float, val minScale: Float, val hasBlur: Boolean) {
  companion object {
    fun forMode(mode: TextTransitionMode): SegmentMotion =
        when (mode) {
          TextTransitionMode.NUMERIC -> SegmentMotion(0.5f, 0.7f, true)
          TextTransitionMode.BLUR -> SegmentMotion(0f, 1f, true)
          else -> SegmentMotion(0f, 1f, false)
        }
  }
}

internal object TextTransitionTiming {
  private const val DEFAULT_DURATION = 0.5
  private const val SETTLE_OMEGA_TIME = 9.23 // (1 + x) e^-x < 0.001

  private fun omega(duration: Double) = 2 * PI / (if (duration > 0) duration else DEFAULT_DURATION)

  /** Segments already leaving when a new change arrives hurry out over this duration. */
  const val HURRY_DURATION = 0.15
  const val HURRY_DRIFT_FACTOR = 0.25f

  /** Progress (0…1) of a critically damped spring that matches SwiftUI's `.smooth(duration:)`. */
  fun progress(time: Double, duration: Double): Float {
    if (time <= 0) return 0f
    val omegaTime = omega(duration) * time
    return (1 - (1 + omegaTime) * exp(-omegaTime)).toFloat()
  }

  fun totalTime(duration: Double): Double = SETTLE_OMEGA_TIME / omega(duration)

  private fun clamp01(value: Float) = min(1f, max(0f, value))

  /** From wherever it is: drift by [drift], shrink to [minScale], fade out, blur in. */
  fun leave(from: SegmentLook, p: Float, drift: Float, minScale: Float, hasBlur: Boolean) =
      SegmentLook(
          translateX = from.translateX,
          translateY = from.translateY + drift * p,
          scale = from.scale + (minScale - from.scale) * p,
          opacity = from.opacity * (1 - clamp01(p / 0.7f)),
          blur = if (hasBlur) from.blur + (1 - from.blur) * clamp01(p / 0.7f) else 0f,
      )

  /** From wherever it is to rest (no offset, full size, opaque, sharp). */
  fun settle(from: SegmentLook, p: Float) =
      SegmentLook(
          translateX = from.translateX * (1 - p),
          translateY = from.translateY * (1 - p),
          scale = from.scale + (1 - from.scale) * p,
          opacity = from.opacity + (1 - from.opacity) * clamp01(p / 0.6f),
          blur = from.blur * (1 - clamp01(p / 0.8f)),
      )
}
