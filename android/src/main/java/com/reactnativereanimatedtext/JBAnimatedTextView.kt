/*
 * Custom
 *
 * ReactTextView that animates text changes (contentTransition). At rest it draws exactly
 * like ReactTextView. During a transition every segment of the text is a RenderNode with its
 * own timeline, so a change that arrives mid-transition retargets from what is on screen.
 * Mirrors ios/JBTextTransitionAnimator.mm.
 */

package com.reactnativereanimatedtext

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import android.provider.Settings
import android.text.Layout
import android.view.animation.AnimationUtils
import androidx.annotation.RequiresApi
import com.facebook.react.views.text.ReactTextView
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Leaving segments a text keeps at most; the oldest go first. Bounds work and memory when
// changes arrive every frame (scrubbing). Mirrors ios/JBTextTransitionAnimator.mm.
private const val MAX_LEAVING_PER_SEGMENT = 2
private const val MIN_MAX_LEAVING = 8
private const val INVISIBLE_OPACITY = 0.01f
// Blur is quantised to a few levels (like iOS's pre-rendered levels). Segments are grouped by
// level and each group is blurred once per frame: every RenderEffect is an offscreen pass, so
// per-segment blur meant ~100 passes per frame while scrubbing several texts.
private const val BLUR_LEVELS = 4

/** Whether two glyph rects sit on top of each other on screen (same line, clearly overlapping). */
private fun overlapsOnScreen(a: RectF, b: RectF): Boolean {
  val overlapX = min(a.right, b.right) - max(a.left, b.left)
  val overlapY = min(a.bottom, b.bottom) - max(a.top, b.top)
  return overlapX > 0.3f * min(a.width(), b.width()) && overlapY > 0.5f * min(a.height(), b.height())
}

private fun blurLevel(amount: Float): Int = (amount * (BLUR_LEVELS - 1)).roundToInt().coerceIn(0, BLUR_LEVELS - 1)

private val blurEffects = HashMap<Int, RenderEffect>()

@RequiresApi(Build.VERSION_CODES.S)
private fun blurEffect(level: Int, maxRadius: Float): RenderEffect? {
  if (level <= 0 || maxRadius <= 0f) return null
  val radiusPx = (maxRadius * level / (BLUR_LEVELS - 1)).roundToInt().coerceAtLeast(1)
  return blurEffects.getOrPut(radiusPx) {
    RenderEffect.createBlurEffect(radiusPx.toFloat(), radiusPx.toFloat(), Shader.TileMode.DECAL)
  }
}

/** One set of transition settings: contentTransition's or reveal's. */
internal data class TransitionOptions(
    val mode: TextTransitionMode = TextTransitionMode.NONE,
    val durationSeconds: Double = 0.0,
    val direction: String = "auto",
    val unit: TextTransitionUnit = TextTransitionUnit.AUTO,
    val staggerSeconds: Double = 0.0,
    val scope: TextTransitionScope = TextTransitionScope.ALL,
    /** < 0: automatic (the transition has landed), 0: off, > 0: that long. */
    val minIntervalSeconds: Double = -1.0,
    val delaySeconds: Double = 0.0,
)

class JBAnimatedTextView(context: Context) : ReactTextView(context) {
  var transitionMode = TextTransitionMode.NONE
  var transitionDurationSeconds = 0.0
  var transitionDirection = "auto"
  var transitionUnit = TextTransitionUnit.AUTO
  var transitionStaggerSeconds = 0.0
  var transitionScope = TextTransitionScope.ALL
  /** < 0: automatic (the transition has landed), 0: off, > 0: that long. */
  var transitionMinIntervalSeconds = -1.0
  var transitionDelaySeconds = 0.0
  var isFontSizeAdjustedToFit = false
  var maxLineCount = 0

  var revealMode = TextTransitionMode.NONE
  var revealDurationSeconds = 0.0
  var revealDirection = "up"
  var revealUnit = TextTransitionUnit.AUTO
  var revealStaggerSeconds = 0.0
  var revealDelaySeconds = 0.0

  /** Canonical transitionKey (TransitionKey.kt); null: no key, every change animates. */
  var transitionKey: String? = null
  /** The key when the text last changed. A text change animates only if the key differs. */
  private var keyAtLastTextChange: String? = null
  /** The reveal runs once per mounted view, the first time it shows non-empty text. */
  private var hasRevealed = false
  /** Started on the next draw (only drawn views reveal). */
  private var isRevealPending = false
  /** The text before RN applies an update (see [onTextApplied]). */
  private var textBeforeUpdate: String? = null
  /** Settings of the transition on screen. */
  private var runningOptions: TransitionOptions? = null

  private fun contentOptions() =
      TransitionOptions(
          transitionMode,
          transitionDurationSeconds,
          transitionDirection,
          transitionUnit,
          transitionStaggerSeconds,
          transitionScope,
          transitionMinIntervalSeconds,
          transitionDelaySeconds)

  private fun revealOptions() =
      TransitionOptions(
          revealMode,
          revealDurationSeconds,
          revealDirection,
          revealUnit,
          revealStaggerSeconds,
          TextTransitionScope.ALL,
          0.0,
          revealDelaySeconds)

  /**
   * The text on screen right before RN applies a change. TextView reuses its Layout object
   * for single-line text (BoringLayout.replaceOrMake), so the old Layout can't be kept; its
   * pixels and segment rects are captured here instead.
   */
  private class OldText(
      val text: String,
      val lineCount: Int,
      /**
       * The whole old text, drawn in view coordinates at the old position. Null while a
       * transition runs: its segments already are the old text.
       */
      val node: RenderNode?,
      /** Rect of every segment, in view coordinates at the old position. */
      val segmentRects: Map<IntRange, RectF>,
      val left: Int,
      val top: Int,
      /** Settings the change animates with. */
      val options: TransitionOptions,
  )

  private var pendingChange: OldText? = null
  private var transition: Transition? = null
  /**
   * Modes with a minimum interval (roll) don't start a transition for every change: while the
   * last one is younger than the interval the change is held back, and one transition to the
   * latest text runs when the interval is up. This is the text the segments still show.
   */
  private var heldChange: OldText? = null

  private fun isTransitionRunning(): Boolean =
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
          transition?.isActive(AnimationUtils.currentAnimationTimeMillis()) == true

  /** Called before RN applies new text, while the old Layout is still current. */
  fun prepareForTextChange() {
    pendingChange = null
    textBeforeUpdate = text?.toString() ?: ""
    // A pending reveal shows whatever the text is when it starts.
    if (isRevealPending) return
    // transitionKey: animate only if the key changed since the last text change (no key:
    // always). A change that doesn't animate swaps instantly from rest, but retargets a
    // running transition: snapping what is on screen would jump.
    val isRunning = isTransitionRunning()
    val keyAllows = transitionKey == null || transitionKey != keyAtLastTextChange
    val options =
        when {
          // A change that doesn't animate never waits: it retargets at once, or joins the
          // running transition's start if that is still in its delay.
          transitionMode != TextTransitionMode.NONE && (keyAllows || isRunning) ->
              contentOptions().let { if (keyAllows) it else it.copy(delaySeconds = 0.0) }
          // e.g. a reveal with contentTransition 'none': carry on with the reveal's look.
          isRunning -> runningOptions?.copy(delaySeconds = 0.0)
          else -> null
        } ?: return
    val oldLayout = layout
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || oldLayout == null || !isAttachedToWindow || width == 0) {
      cancelTransition()
      return
    }
    pendingChange =
        if (transition?.hasSegments == true) {
          // Retargeting: the segments on screen are the old text; skip re-recording it.
          OldText(oldLayout.text.toString(), oldLayout.lineCount, null, emptyMap(), left, top, options)
        } else {
          captureOldText(oldLayout, options)
        }
  }

  /** Called after RN applied an update (the new text is set; its Layout may come later). */
  fun onTextApplied() {
    val before = textBeforeUpdate ?: return
    textBeforeUpdate = null
    val newText = text?.toString() ?: ""
    if (newText == before) return
    keyAtLastTextChange = transitionKey
    // The first non-empty text is the reveal's, whatever the key.
    if (!hasRevealed && newText.isNotEmpty()) {
      hasRevealed = true
      if (revealMode != TextTransitionMode.NONE) {
        pendingChange = null
        cancelTransition()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !isAnimationDisabled()) {
          isRevealPending = true
          invalidate()
        }
      }
    }
  }

  /** View recycling: the next mount reveals again. */
  fun resetForRecycle() {
    cancelTransition()
    pendingChange = null
    isRevealPending = false
    hasRevealed = false
    keyAtLastTextChange = null
    textBeforeUpdate = null
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  private fun captureOldText(oldLayout: Layout, options: TransitionOptions): OldText {
    val text = oldLayout.text.toString()
    val originX = totalPaddingLeft.toFloat()
    val originY = totalPaddingTop.toFloat()
    val node = RenderNode("JBOldText")
    node.setPosition(0, 0, width, height)
    node.clipToBounds = false
    val recording = node.beginRecording()
    applyTextPaintState()
    recording.translate(originX, originY)
    oldLayout.draw(recording)
    node.endRecording()
    // Rects of the segments a plan can refer to: this unit's, plus the whole text (fallbacks).
    val ranges = TextTransitionPlan.segmentRanges(text, options.unit) + listOfNotNull(text.indices.takeIf { text.isNotEmpty() })
    val rects = ranges.associateWith { range -> textRect(oldLayout, originX, originY, range) }
    return OldText(text, oldLayout.lineCount, node, rects, left, top, options)
  }

  override fun onDraw(canvas: Canvas) {
    val pending = pendingChange
    val newLayout = layout
    if (isRevealPending && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && newLayout != null) {
      isRevealPending = false
      pendingChange = null
      startReveal(newLayout, canvas)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && newLayout != null) {
      val now = AnimationUtils.currentAnimationTimeMillis()
      val running = transition?.takeIf { it.hasSegments }
      val nextAllowedMs = running?.nextRetargetMs ?: 0L
      if (running == null) heldChange = null
      if (pending != null) {
        pendingChange = null
        // A transition still in its delay hasn't moved anything yet: a change re-aims it
        // (keeping its start) instead of being held.
        val isHeld =
            running != null &&
                (heldChange != null ||
                    (pending.node == null && now < nextAllowedMs && !running.isWaitingToStart(now)))
        if (running != null && isHeld) {
          // Too soon after the last transition: keep showing the segments (moved with the view).
          running.shiftAll((pending.left - left).toFloat(), (pending.top - top).toFloat())
          if (heldChange == null) heldChange = pending
        } else if (pending.text != newLayout.text.toString()) {
          startOrRetarget(pending, newLayout, canvas)
        }
      }
      val held = heldChange
      if (held != null && now >= nextAllowedMs) {
        heldChange = null
        if (held.text != newLayout.text.toString()) {
          startOrRetarget(OldText(held.text, held.lineCount, null, emptyMap(), left, top, held.options), newLayout, canvas)
        }
      }
    } else {
      pendingChange = null
    }
    val current = transition
    if (current != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      if (current.draw(canvas)) {
        postInvalidateOnAnimation()
        return
      }
      transition = null
    }
    super.onDraw(canvas)
  }

  override fun onDetachedFromWindow() {
    cancelTransition()
    pendingChange = null
    super.onDetachedFromWindow()
  }

  private fun cancelTransition() {
    transition = null
    heldChange = null
    runningOptions = null
  }

  private fun applyTextPaintState() {
    paint.color = currentTextColor
    paint.drawableState = drawableState
  }

  private fun isAnimationDisabled(): Boolean =
      Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

  private fun startOrRetarget(old: OldText, newLayout: Layout, canvas: Canvas) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !canvas.isHardwareAccelerated || isAnimationDisabled()) {
      cancelTransition()
      return
    }
    val options = old.options
    val newText = newLayout.text.toString()
    var mode = options.mode
    val needsWholeText =
        isFontSizeAdjustedToFit ||
            maxLineCount > 0 ||
            (mode.isNumeric &&
                options.unit != TextTransitionUnit.WHOLE &&
                (old.lineCount > 1 || newLayout.lineCount > 1))
    val plan =
        if (needsWholeText) {
          mode = TextTransitionMode.FADE
          TextTransitionPlan.wholeText(old.text, newText)
        } else {
          TextTransitionPlan.plan(old.text, newText, mode, options.direction, options.unit, options.scope)
        }
    val current = transition ?: Transition().also { transition = it }
    // A change mid-transition retargets from what is on screen.
    current.retarget(plan, mode, old, newLayout, options)
    runningOptions = options
  }

  private fun startReveal(newLayout: Layout, canvas: Canvas) {
    val text = newLayout.text.toString()
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        text.isEmpty() ||
        !canvas.isHardwareAccelerated ||
        isAnimationDisabled()) {
      return
    }
    val options = revealOptions()
    var mode = options.mode
    val plan =
        if (isFontSizeAdjustedToFit || maxLineCount > 0) {
          // Same fallback as a change: truncation and font scaling are whole-text.
          mode = TextTransitionMode.FADE
          TextTransitionPlan.wholeText("", text)
        } else {
          TextTransitionPlan.reveal(text, mode, options.direction, options.unit)
        }
    val reveal = Transition().also { transition = it }
    reveal.retarget(plan, mode, OldText("", 0, null, emptyMap(), left, top, options), newLayout, options)
    runningOptions = options
  }

  /** Glyph bounds of [range] (end exclusive), in view coordinates. */
  private fun textRect(layout: Layout, originX: Float, originY: Float, range: IntRange): RectF {
    val path = Path()
    layout.getSelectionPath(range.first, range.last + 1, path)
    val rect = RectF()
    path.computeBounds(rect, true)
    rect.offset(originX, originY)
    return rect
  }

  private enum class Role {
    SETTLE,
    LEAVE,
  }

  /** One piece of text on screen, with its own timeline. */
  @RequiresApi(Build.VERSION_CODES.Q)
  private class Segment(
      val node: RenderNode,
      /** Glyph bounds at rest, in view coordinates. */
      var restRect: RectF,
      /** Range in the text this segment belongs to. */
      var range: IntRange,
      val maxBlur: Float,
      /** The glyphs and colour drawn; equal ones can reuse the node. */
      val text: String,
      val color: Int,
  ) {
    var isHurrying = false
    /** Moves the recorded node to its current rest position. */
    var shiftX = 0f
    var shiftY = 0f
    var role = Role.SETTLE
    var from = SegmentLook()
    var drift = 0f
    var minScale = 1f
    var hasBlur = false
    var startMs = 0L
    var duration = 0.0
    /** A staggered leave: the segment carries on as it is until [leaveStartMs], then leaves. */
    var hasPendingLeave = false
    var leaveStartMs = 0L
    var leaveDuration = 0.0
    /** For an arriving segment: the leaving segment it replaces in its slot. */
    var partner: Segment? = null
    /**
     * Leaving segments never move sideways: they rise/fade from where they were when they were
     * told to leave, even while still waiting for their turn. NaN: not fixed.
     */
    var fixedTranslateX = Float.NaN

    private fun totalMs(duration: Double) = (TextTransitionTiming.totalTime(duration) * 1000).toLong()

    val endMs: Long
      get() = if (hasPendingLeave) leaveStartMs + totalMs(leaveDuration) else startMs + totalMs(duration)

    private fun baseLookAt(nowMs: Long): SegmentLook {
      val elapsed = (nowMs - startMs) / 1000.0
      val p = if (nowMs >= startMs + totalMs(duration)) 1f else TextTransitionTiming.progress(elapsed, duration)
      return if (role == Role.LEAVE) TextTransitionTiming.leave(from, p, drift, minScale, hasBlur)
      else TextTransitionTiming.settle(from, p)
    }

    fun lookAt(nowMs: Long): SegmentLook {
      val look =
          if (hasPendingLeave && nowMs >= leaveStartMs) {
            val elapsed = (nowMs - leaveStartMs) / 1000.0
            val p = if (nowMs >= endMs) 1f else TextTransitionTiming.progress(elapsed, leaveDuration)
            TextTransitionTiming.leave(baseLookAt(leaveStartMs), p, drift, minScale, hasBlur)
          } else {
            baseLookAt(nowMs)
          }
      return if (fixedTranslateX.isNaN()) look else look.copy(translateX = fixedTranslateX)
    }

    fun isLeavingAt(nowMs: Long) = role == Role.LEAVE || (hasPendingLeave && nowMs >= leaveStartMs)

    /**
     * Arriving and not started yet (staggered or delayed): invisible, waiting for its turn. A
     * kept segment waiting for a delayed start is visible (the old text), so it isn't waiting.
     */
    fun isWaitingAt(nowMs: Long) =
        role == Role.SETTLE && !hasPendingLeave && startMs > nowMs && from.opacity <= INVISIBLE_OPACITY

    fun isAtRest(look: SegmentLook) =
        abs(look.translateX) < 0.01f &&
            abs(look.translateY) < 0.01f &&
            abs(look.scale - 1) < 0.001f &&
            abs(look.opacity - 1) < 0.001f &&
            look.blur < 0.001f

    fun start(role: Role, from: SegmentLook, nowMs: Long, duration: Double) {
      this.role = role
      this.from = from
      this.startMs = nowMs
      this.duration = duration
      this.hasPendingLeave = false
    }

    /** Leaves at [timeMs] (in the future) from whatever it looks like then. */
    fun leaveAt(timeMs: Long, duration: Double) {
      hasPendingLeave = true
      leaveStartMs = timeMs
      leaveDuration = duration
    }

    fun shift(dx: Float, dy: Float) {
      shiftX += dx
      shiftY += dy
      restRect.offset(dx, dy)
    }
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  private inner class Transition {
    /** Segments of the latest text, settling into place. */
    private var current = mutableListOf<Segment>()

    val hasSegments: Boolean
      get() = current.isNotEmpty()

    /**
     * When the latest transition starts (its change + its delay). Changes that arrive before
     * then join it: they keep this start instead of delaying it again.
     */
    private var transitionStartMs = 0L

    /** The latest transition is still in its delay (nothing of it has moved yet). */
    fun isWaitingToStart(now: Long) = now < transitionStartMs && isActive(now)

    /** Changes that arrive before this are held back (see heldChange). */
    var nextRetargetMs = 0L
      private set

    /** Moves everything on screen (the view moved under it) without retargeting. */
    fun shiftAll(dx: Float, dy: Float) {
      if (abs(dx) < 0.001f && abs(dy) < 0.001f) return
      for (segment in current) segment.shift(dx, dy)
      for (segment in leaving) segment.shift(dx, dy)
    }
    /** Segments of earlier text, on their way out. */
    private val leaving = mutableListOf<Segment>()
    private val density = resources.displayMetrics.density

    /** Whether anything is still moving (finished segments are pruned on the next draw). */
    fun isActive(now: Long): Boolean = leaving.any { now < it.endMs } || current.any { now < it.endMs }

    fun retarget(
        plan: TextTransitionPlan,
        mode: TextTransitionMode,
        old: OldText,
        newLayout: Layout,
        options: TransitionOptions,
    ) {
      val now = AnimationUtils.currentAnimationTimeMillis()
      val motion = SegmentMotion.forMode(mode)
      val direction = plan.direction
      val duration = options.durationSeconds
      val staggerStepMs = TextTransitionTiming.staggerStep(options.staggerSeconds, plan.maxStaggerIndex) * 1000
      // Every position's schedule (leaving, kept, arriving) counts from baseMs: the delay after
      // the change, or the start of the running transition if it is still waiting for it.
      val baseMs =
          if (isWaitingToStart(now)) transitionStartMs
          else now + (max(0.0, options.delaySeconds) * 1000).toLong()
      transitionStartMs = baseMs
      val baseDelayMs = baseMs - now
      val hurryDuration = TextTransitionTiming.hurryDuration(duration)
      // Minimum interval: automatic is "this transition has landed" (its stagger spread plus
      // a segment's settle time).
      val settleTime = TextTransitionTiming.settleTime(options.mode, duration)
      val minInterval =
          when {
            settleTime <= 0 || options.minIntervalSeconds == 0.0 -> 0.0
            options.minIntervalSeconds > 0 -> options.minIntervalSeconds
            else -> staggerStepMs * plan.maxStaggerIndex / 1000 + settleTime
          }
      // Counted from the transition's start (after its delay).
      nextRetargetMs = baseMs + (minInterval * 1000).toLong()
      val newOriginX = totalPaddingLeft.toFloat()
      val newOriginY = totalPaddingTop.toFloat()
      // Keep what is on screen in place while the view moves under it.
      val viewOffsetX = (old.left - left).toFloat()
      val viewOffsetY = (old.top - top).toFloat()
      for (segment in current + leaving) segment.shift(viewOffsetX, viewOffsetY)

      // Earlier text that is still leaving hurries out, once: restarting the hurry on every
      // change would keep it on screen forever while changes arrive every frame.
      val iterator = leaving.iterator()
      while (iterator.hasNext()) {
        val segment = iterator.next()
        val look = segment.lookAt(now)
        if (now >= segment.endMs || look.opacity <= INVISIBLE_OPACITY) {
          iterator.remove()
          continue
        }
        // A staggered segment whose turn hasn't come is still what its slot shows: it keeps
        // its schedule, so it leaves when its replacement arrives.
        if (!segment.isHurrying && segment.isLeavingAt(now)) {
          segment.isHurrying = true
          segment.drift *= TextTransitionTiming.HURRY_DRIFT_FACTOR
          segment.start(Role.LEAVE, look, now, hurryDuration)
        }
      }

      // Positions that get a new segment, and what they replace (see REMOVED/INSERTED below).
      var insertedIndices = 0L
      for (op in plan.ops) {
        if (op.change == SegmentChange.INSERTED && op.staggerIndex < 64) insertedIndices = insertedIndices or (1L shl op.staggerIndex)
      }
      val takenOver = HashMap<Int, Segment>()
      val leftByIndex = HashMap<Int, Segment>()

      val byRange = current.associateBy { it.range }.toMutableMap()

      fun oldSegment(range: IntRange): Segment {
        val rect =
            RectF(
                old.segmentRects[range]
                    ?: old.segmentRects.values.reduceOrNull { a, b -> RectF(a).apply { union(b) } }
                    ?: RectF(0f, 0f, width.toFloat(), height.toFloat()))
        rect.offset(viewOffsetX, viewOffsetY)
        val wholeText = range.first == 0 && range.last + 1 == old.text.length
        val text = if (range.last < old.text.length) old.text.substring(range) else ""
        return record(rect, range, motion, wholeText, text) { recording ->
          val oldNode = old.node ?: return@record
          recording.translate(viewOffsetX, viewOffsetY)
          recording.drawRenderNode(oldNode)
        }
      }

      fun newSegment(range: IntRange): Segment {
        val rect = textRect(newLayout, newOriginX, newOriginY, range)
        val wholeText = range.first == 0 && range.last + 1 == newLayout.text.length
        val text = newLayout.text.substring(range.first, range.last + 1)
        return record(rect, range, motion, wholeText, text) { recording ->
          applyTextPaintState()
          recording.translate(newOriginX, newOriginY)
          newLayout.draw(recording)
        }
      }

      fun leave(segment: Segment, delayMs: Long) {
        val look = segment.lookAt(now)
        // From here on it only rises/fades where it is (a waiting leave doesn't follow its slot).
        segment.fixedTranslateX = look.translateX
        segment.drift = -direction * segment.restRect.height() * motion.offsetFactor
        segment.minScale = motion.minScale
        segment.hasBlur = segment.maxBlur > 0 && motion.hasBlur
        segment.isHurrying = false
        if (delayMs > 0) {
          // Staggered: it keeps doing what it does until its turn.
          segment.leaveAt(now + delayMs, duration)
        } else {
          segment.start(Role.LEAVE, look, now, duration)
        }
        leaving.add(segment)
      }

      val settling = mutableListOf<Segment>()
      for (op in plan.ops) {
        when (op.change) {
          SegmentChange.KEPT -> {
            val existing = byRange.remove(op.oldRange!!)
            val newRect = textRect(newLayout, newOriginX, newOriginY, op.newRange!!)
            val newText = newLayout.text.substring(op.newRange.first, op.newRange.last + 1)
            if (existing != null && existing.text == newText && existing.color == currentTextColor) {
              // Same glyphs and colour: keep the node, move it to the new rest position.
              val look = existing.lookAt(now)
              val deltaX = existing.restRect.left - newRect.left
              val deltaY = existing.restRect.top - newRect.top
              existing.range = op.newRange
              // Kept segments that are moving re-aim at once; one at rest moves when the
              // transition starts; one still waiting for its start keeps it.
              val keptStartMs =
                  if (existing.startMs > now) existing.startMs else if (existing.isAtRest(look)) baseMs else now
              if (abs(deltaX) > 0.001f || abs(deltaY) > 0.001f) {
                existing.shift(-deltaX, -deltaY)
                existing.start(
                    Role.SETTLE,
                    look.copy(translateX = look.translateX + deltaX, translateY = look.translateY + deltaY),
                    keptStartMs,
                    duration)
              } else if (existing.role != Role.SETTLE) {
                existing.start(Role.SETTLE, look, now, duration)
              }
              settling.add(existing)
              continue
            }
            // Different glyphs or colour: redraw from the new text, starting from wherever the
            // old piece is now.
            val look = existing?.lookAt(now) ?: SegmentLook()
            val oldRect =
                existing?.restRect
                    ?: RectF(old.segmentRects[op.oldRange] ?: RectF()).apply { offset(viewOffsetX, viewOffsetY) }
            val segment = newSegment(op.newRange)
            segment.partner = existing?.partner
            segment.start(
                Role.SETTLE,
                look.copy(
                    translateX = look.translateX + oldRect.left - segment.restRect.left,
                    translateY = look.translateY + oldRect.top - segment.restRect.top),
                when {
                  existing == null -> baseMs
                  existing.startMs > now -> existing.startMs
                  existing.isAtRest(look) -> baseMs
                  else -> now
                },
                duration)
            settling.add(segment)
          }
          SegmentChange.INSERTED -> {
            val segment = newSegment(op.newRange!!)
            var start =
                SegmentLook(
                    translateY = direction * segment.restRect.height() * motion.offsetFactor,
                    scale = motion.minScale,
                    opacity = 0f,
                    blur = if (motion.hasBlur) 1f else 0f,
                )
            var startMs = baseMs + (op.staggerIndex * staggerStepMs).toLong()
            var segmentDuration = duration
            val young = takenOver[op.staggerIndex]
            if (young != null) {
              // The slot's previous arrival never got to show. Don't push the slot back: carry
              // on with its rise if it has started, otherwise keep its (earlier) start.
              if (young.startMs <= now) {
                start = young.from
                startMs = young.startMs
                segmentDuration = young.duration
              } else {
                startMs = min(startMs, young.startMs)
              }
              val partner = young.partner
              segment.partner = partner
              if (partner != null && partner.hasPendingLeave && partner.leaveStartMs > max(startMs, now)) {
                partner.leaveAt(max(startMs, now), partner.leaveDuration)
              }
            } else {
              segment.partner = leftByIndex[op.staggerIndex]
            }
            // The segment it replaces stays where it is (even if the slot moved); this one
            // arrives straight at its final place.
            segment.start(Role.SETTLE, start, startMs, segmentDuration)
            settling.add(segment)
          }
          SegmentChange.REMOVED -> {
            val existing = byRange.remove(op.oldRange!!)
            // Not a segment of the text on screen (e.g. a whole-text fallback range): the
            // current segments it covers leave below.
            if (existing == null && current.isNotEmpty()) continue
            if (existing != null) {
              val isWaiting = existing.isWaitingAt(now)
              val isYoung =
                  isWaiting ||
                      (existing.role == Role.SETTLE &&
                          existing.from.opacity < 0.001f &&
                          existing.lookAt(now).opacity < motion.takeoverOpacity)
              val isReplaced = op.staggerIndex < 64 && (insertedIndices and (1L shl op.staggerIndex)) != 0L
              if (isYoung && isReplaced) {
                // Replaced before it showed: its replacement takes over its timeline.
                takenOver[op.staggerIndex] = existing
                continue
              }
              if (isWaiting) continue
            }
            val segment = existing ?: oldSegment(op.oldRange)
            leave(segment, baseDelayMs + (op.staggerIndex * staggerStepMs).toLong())
            leftByIndex[op.staggerIndex] = segment
          }
        }
      }
      // Text on screen that the plan no longer refers to leaves too.
      byRange.values.forEach { if (!it.isWaitingAt(now)) leave(it, baseDelayMs) }
      current = settling

      // Leaving segments stay at their own x, so when the width changes an arrival can land on
      // an old glyph that is still waiting for its turn. That glyph leaves no later than the
      // arrival.
      for (arriving in current) {
        if (arriving.role != Role.SETTLE || arriving.from.opacity > INVISIBLE_OPACITY) continue
        val arrivalMs = max(now, arriving.startMs)
        for (old in leaving) {
          if (!old.hasPendingLeave || old.leaveStartMs <= arrivalMs) continue
          val onScreen = RectF(old.restRect).apply { if (!old.fixedTranslateX.isNaN()) offset(old.fixedTranslateX, 0f) }
          if (overlapsOnScreen(onScreen, arriving.restRect)) old.leaveAt(arrivalMs, old.leaveDuration)
        }
      }
      leaving.removeAll { now >= it.endMs }
      // Bound the leaving pile: the oldest are the most faded.
      val maxLeaving = maxOf(MIN_MAX_LEAVING, MAX_LEAVING_PER_SEGMENT * current.size)
      if (leaving.size > maxLeaving) {
        // Drop the most faded first, so what disappears is already (nearly) invisible.
        leaving.sortByDescending { it.lookAt(now).opacity }
        while (leaving.size > maxLeaving) leaving.removeAt(leaving.size - 1)
      }
      invalidate()
    }

    /** Draws the current frame; returns false once everything has settled or left. */
    /** One node per blur level (index 1…), re-recorded every frame with that level's segments. */
    private val levelNodes = arrayOfNulls<RenderNode>(BLUR_LEVELS)
    private val levelSegments = Array(BLUR_LEVELS) { mutableListOf<Segment>() }

    fun draw(canvas: Canvas): Boolean {
      val now = AnimationUtils.currentAnimationTimeMillis()
      leaving.removeAll { now >= it.endMs }
      val isActive = leaving.isNotEmpty() || current.any { now < it.endMs }
      // Once settled, RN's own text drawing takes over (identical to <Text>).
      if (!isActive) return false

      for (list in levelSegments) list.clear()
      var maxBlur = 0f
      for (segment in leaving) place(segment, now)
      for (segment in current) place(segment, now)
      for (list in levelSegments) for (segment in list) maxBlur = max(maxBlur, segment.maxBlur)

      for (level in BLUR_LEVELS - 1 downTo 1) {
        val segments = levelSegments[level]
        if (segments.isEmpty()) continue
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) blurEffect(level, maxBlur) else null
        if (effect == null) {
          for (segment in segments) canvas.drawRenderNode(segment.node)
          continue
        }
        val group = levelNodes[level] ?: RenderNode("JBTextBlurLevel").also { levelNodes[level] = it }
        val padding = (3 * maxBlur).roundToInt() + height
        group.setPosition(-padding, -padding, width + padding, height + padding)
        group.clipToBounds = false
        group.setRenderEffect(effect)
        val recording = group.beginRecording()
        recording.translate(padding.toFloat(), padding.toFloat())
        for (segment in segments) recording.drawRenderNode(segment.node)
        group.endRecording()
        canvas.drawRenderNode(group)
      }
      for (segment in levelSegments[0]) canvas.drawRenderNode(segment.node)
      return true
    }

    /** Applies the segment's look to its node and files it under its blur level. */
    private fun place(segment: Segment, now: Long) {
      val look = segment.lookAt(now)
      val node = segment.node
      node.translationX = segment.shiftX + look.translateX
      node.translationY = segment.shiftY + look.translateY
      node.scaleX = look.scale
      node.scaleY = look.scale
      node.alpha = look.opacity
      if (look.opacity <= INVISIBLE_OPACITY) return
      levelSegments[if (segment.maxBlur > 0f) blurLevel(look.blur) else 0].add(segment)
    }

    /**
     * A RenderNode around [rect] (view coordinates) with room for ink and blur. Shaping comes
     * from the whole line, so joined scripts stay joined; the clip is widened vertically for
     * diacritics.
     */
    private fun record(
        rect: RectF,
        range: IntRange,
        motion: SegmentMotion,
        wholeText: Boolean,
        text: String,
        draw: (Canvas) -> Unit,
    ): Segment {
      val maxBlur = motion.blurScale * min(10f * density, max(2f * density, rect.height() * 0.15f))
      val padding = max(3 * maxBlur, rect.height() * 0.35f)
      val bounds =
          RectF(floor(rect.left - padding), floor(rect.top - padding), ceil(rect.right + padding), ceil(rect.bottom + padding))
      if (wholeText) bounds.union(-padding, -padding, width + padding, height + padding)
      val node = RenderNode("JBTextSegment")
      node.setPosition(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt())
      node.pivotX = rect.centerX() - bounds.left
      node.pivotY = rect.centerY() - bounds.top
      val recording = node.beginRecording()
      recording.translate(-bounds.left, -bounds.top)
      if (!wholeText) {
        val inkPadding = rect.height() * 0.35f
        recording.clipRect(rect.left, rect.top - inkPadding, rect.right, rect.bottom + inkPadding)
      }
      draw(recording)
      node.endRecording()
      return Segment(node, RectF(rect), range, maxBlur, text, currentTextColor).also { it.hasBlur = maxBlur > 0 }
    }
  }
}
