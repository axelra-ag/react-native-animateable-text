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

class JBAnimatedTextView(context: Context) : ReactTextView(context) {
  var transitionMode = TextTransitionMode.NONE
  var transitionDurationSeconds = 0.0
  var transitionDirection = "auto"
  var transitionUnit = TextTransitionUnit.AUTO
  var isFontSizeAdjustedToFit = false
  var maxLineCount = 0

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
  )

  private var pendingChange: OldText? = null
  private var transition: Transition? = null

  /** Called before RN applies new text, while the old Layout is still current. */
  fun prepareForTextChange() {
    pendingChange = null
    val oldLayout = layout
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        transitionMode == TextTransitionMode.NONE ||
        oldLayout == null ||
        !isAttachedToWindow ||
        width == 0) {
      cancelTransition()
      return
    }
    pendingChange =
        if (transition?.hasSegments == true) {
          // Retargeting: the segments on screen are the old text; skip re-recording it.
          OldText(oldLayout.text.toString(), oldLayout.lineCount, null, emptyMap(), left, top)
        } else {
          captureOldText(oldLayout)
        }
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  private fun captureOldText(oldLayout: Layout): OldText {
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
    val ranges = TextTransitionPlan.segmentRanges(text, transitionUnit) + listOfNotNull(text.indices.takeIf { text.isNotEmpty() })
    val rects = ranges.associateWith { range -> textRect(oldLayout, originX, originY, range) }
    return OldText(text, oldLayout.lineCount, node, rects, left, top)
  }

  override fun onDraw(canvas: Canvas) {
    val pending = pendingChange
    val newLayout = layout
    if (pending != null && newLayout != null) {
      pendingChange = null
      if (pending.text != newLayout.text.toString()) {
        startOrRetarget(pending, newLayout, canvas)
      }
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
    val newText = newLayout.text.toString()
    var mode = transitionMode
    val needsWholeText =
        isFontSizeAdjustedToFit ||
            maxLineCount > 0 ||
            (mode == TextTransitionMode.NUMERIC &&
                transitionUnit != TextTransitionUnit.WHOLE &&
                (old.lineCount > 1 || newLayout.lineCount > 1))
    val plan =
        if (needsWholeText) {
          mode = TextTransitionMode.FADE
          TextTransitionPlan.wholeText(old.text, newText)
        } else {
          TextTransitionPlan.plan(old.text, newText, mode, transitionDirection, transitionUnit)
        }
    val current = transition ?: Transition().also { transition = it }
    // A change mid-transition retargets from what is on screen.
    current.retarget(plan, mode, old, newLayout)
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

    val endMs: Long
      get() = startMs + (TextTransitionTiming.totalTime(duration) * 1000).toLong()

    fun lookAt(nowMs: Long): SegmentLook {
      val elapsed = (nowMs - startMs) / 1000.0
      val p = if (nowMs >= endMs) 1f else TextTransitionTiming.progress(elapsed, duration)
      return if (role == Role.LEAVE) TextTransitionTiming.leave(from, p, drift, minScale, hasBlur)
      else TextTransitionTiming.settle(from, p)
    }

    fun start(role: Role, from: SegmentLook, nowMs: Long, duration: Double) {
      this.role = role
      this.from = from
      this.startMs = nowMs
      this.duration = duration
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
    /** Segments of earlier text, on their way out. */
    private val leaving = mutableListOf<Segment>()
    private val density = resources.displayMetrics.density

    fun retarget(plan: TextTransitionPlan, mode: TextTransitionMode, old: OldText, newLayout: Layout) {
      val now = AnimationUtils.currentAnimationTimeMillis()
      val motion = SegmentMotion.forMode(mode)
      val direction = plan.direction
      val duration = transitionDurationSeconds
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
        if (!segment.isHurrying) {
          segment.isHurrying = true
          segment.drift *= TextTransitionTiming.HURRY_DRIFT_FACTOR
          segment.start(Role.LEAVE, look, now, TextTransitionTiming.HURRY_DURATION)
        }
      }

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

      fun leave(segment: Segment) {
        val look = segment.lookAt(now)
        segment.drift = -direction * segment.restRect.height() * motion.offsetFactor
        segment.minScale = motion.minScale
        segment.hasBlur = segment.maxBlur > 0 && motion.hasBlur
        segment.isHurrying = false
        segment.start(Role.LEAVE, look, now, duration)
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
              if (abs(deltaX) > 0.001f || abs(deltaY) > 0.001f) {
                existing.shift(-deltaX, -deltaY)
                existing.start(
                    Role.SETTLE,
                    look.copy(translateX = look.translateX + deltaX, translateY = look.translateY + deltaY),
                    now,
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
            segment.start(
                Role.SETTLE,
                look.copy(
                    translateX = look.translateX + oldRect.left - segment.restRect.left,
                    translateY = look.translateY + oldRect.top - segment.restRect.top),
                now,
                duration)
            settling.add(segment)
          }
          SegmentChange.INSERTED -> {
            val segment = newSegment(op.newRange!!)
            val start =
                SegmentLook(
                    translateY = direction * segment.restRect.height() * motion.offsetFactor,
                    scale = motion.minScale,
                    opacity = 0f,
                    blur = if (motion.hasBlur) 1f else 0f,
                )
            segment.start(Role.SETTLE, start, now, duration)
            settling.add(segment)
          }
          SegmentChange.REMOVED -> {
            val existing = byRange.remove(op.oldRange!!)
            // Not a segment of the text on screen (e.g. a whole-text fallback range): the
            // current segments it covers leave below.
            if (existing == null && current.isNotEmpty()) continue
            leave(existing ?: oldSegment(op.oldRange))
          }
        }
      }
      // Text on screen that the plan no longer refers to leaves too.
      byRange.values.forEach { leave(it) }
      current = settling
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
      val maxBlur = if (motion.hasBlur) min(10f * density, max(2f * density, rect.height() * 0.15f)) else 0f
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
