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

/** Keep in sync with motionForMode in ios/JBTextTransitionAnimator.mm. */
internal data class SegmentMotion(
    /** Of the line height. */
    val offsetFactor: Float,
    val minScale: Float,
    /** Of the maximum blur; 0: no blur. */
    val blurScale: Float,
    /**
     * An arriving segment that is replaced while still fainter than this hands its timeline to
     * its replacement instead of leaving, so values that change every frame still get to show.
     */
    val takeoverOpacity: Float,
    /**
     * How long a segment needs to (nearly) settle, of the duration; 0: no minimum interval.
     * With one, a change that arrives before the previous transition has landed (stagger
     * spread + this) is held back and the latest one is shown when it has.
     */
    val minIntervalFactor: Float,
) {
  val hasBlur: Boolean
    get() = blurScale > 0f

  companion object {
    fun forMode(mode: TextTransitionMode): SegmentMotion =
        when (mode) {
          TextTransitionMode.NUMERIC -> SegmentMotion(0.5f, 0.7f, 1f, 0f, 0f)
          // 0.25 × line height ≈ 0.3 em. 0.65 × duration: a segment is ~90% settled.
          TextTransitionMode.ROLL -> SegmentMotion(0.25f, 1f, 0.2f, 0.9f, 0.65f)
          TextTransitionMode.BLUR -> SegmentMotion(0f, 1f, 1f, 0f, 0f)
          else -> SegmentMotion(0f, 1f, 0f, 0f, 0f)
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

  /** The delays of a staggered change never add up to more than this, however long the text. */
  private const val MAX_STAGGER_SPREAD = 0.3

  /** How long a segment of [mode] needs to (nearly) settle; 0: the mode has no minimum interval. */
  fun settleTime(mode: TextTransitionMode, duration: Double): Double =
      SegmentMotion.forMode(mode).minIntervalFactor * (if (duration > 0) duration else DEFAULT_DURATION)

  fun hurryDuration(duration: Double): Double = min(HURRY_DURATION, if (duration > 0) duration else DEFAULT_DURATION)

  /** Delay between neighbouring positions, shortened so the whole change fits the spread cap. */
  fun staggerStep(stagger: Double, maxIndex: Int): Double =
      if (stagger <= 0 || maxIndex <= 0) 0.0 else min(stagger, MAX_STAGGER_SPREAD / maxIndex)

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
