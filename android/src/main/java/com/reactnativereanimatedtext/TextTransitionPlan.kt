/*
 * Custom
 *
 * Pure Kotlin (no Android APIs) so it runs in JVM unit tests. Mirrors
 * ios/JBTextTransitionPlan.mm; both must pass fixtures/text-transitions.tsv.
 */

package com.reactnativereanimatedtext

import java.text.BreakIterator

enum class TextTransitionMode {
  NONE,
  NUMERIC,
  BLUR,
  FADE;

  companion object {
    fun fromString(value: String?): TextTransitionMode =
        when (value) {
          "numeric" -> NUMERIC
          "blur" -> BLUR
          "fade" -> FADE
          else -> NONE
        }
  }
}

/** What animates as one piece. */
enum class TextTransitionUnit {
  /** Graphemes; joining scripts (Arabic) grouped into words. */
  AUTO,
  /** Whitespace-separated words, every script. */
  WORD,
  /** The entire text. */
  WHOLE;

  companion object {
    fun fromString(value: String?): TextTransitionUnit =
        when (value) {
          "word" -> WORD
          "whole" -> WHOLE
          else -> AUTO
        }
  }
}

enum class SegmentChange {
  KEPT,
  REMOVED,
  INSERTED,
}

/** Ranges are [start, end) UTF-16 indices; null when the op has no side in that text. */
data class TextTransitionOp(val change: SegmentChange, val oldRange: IntRange?, val newRange: IntRange?)

class TextTransitionPlan(
    val ops: List<TextTransitionOp>,
    /** 1: content moves up (counting up), -1: moves down. */
    val direction: Int,
) {
  fun fixtureDescription(oldText: String, newText: String): String =
      ops.joinToString(" ") { op ->
        val segment =
            when (op.change) {
              SegmentChange.INSERTED -> newText.substring(op.newRange!!)
              else -> oldText.substring(op.oldRange!!)
            }
        val prefix =
            when (op.change) {
              SegmentChange.KEPT -> "="
              SegmentChange.REMOVED -> "-"
              SegmentChange.INSERTED -> "+"
            }
        prefix + segment.replace(" ", "␠")
      }

  companion object {
    private const val MAX_LCS_SEGMENTS = 400

    fun plan(
        oldText: String,
        newText: String,
        mode: TextTransitionMode,
        direction: String?,
        unit: TextTransitionUnit = TextTransitionUnit.AUTO,
    ): TextTransitionPlan {
      val oldSegments = segmentText(oldText, unit)
      val newSegments = segmentText(newText, unit)
      val resolvedDirection = resolveDirection(oldText, newText, direction)
      if (unit == TextTransitionUnit.WHOLE) {
        return TextTransitionPlan(wholeText(oldText, newText).ops, resolvedDirection)
      }
      if (mode == TextTransitionMode.NUMERIC) {
        return TextTransitionPlan(numericOps(oldSegments, newSegments), resolvedDirection)
      }
      if (oldSegments.size > MAX_LCS_SEGMENTS || newSegments.size > MAX_LCS_SEGMENTS) {
        return wholeText(oldText, newText)
      }
      return TextTransitionPlan(lcsOps(oldSegments, newSegments), resolvedDirection)
    }

    /** The segments [plan] works with, as [start, end) ranges. */
    fun segmentRanges(text: String, unit: TextTransitionUnit = TextTransitionUnit.AUTO): List<IntRange> =
        segmentText(text, unit).map { it.range }

    /** Whole-text cross-fade: one REMOVED op for the old text, one INSERTED for the new. */
    fun wholeText(oldText: String, newText: String): TextTransitionPlan {
      val ops = mutableListOf<TextTransitionOp>()
      if (oldText.isNotEmpty()) ops.add(TextTransitionOp(SegmentChange.REMOVED, 0 until oldText.length, null))
      if (newText.isNotEmpty()) ops.add(TextTransitionOp(SegmentChange.INSERTED, null, 0 until newText.length))
      return TextTransitionPlan(ops, 1)
    }
  }
}

private data class Segment(val range: IntRange, val text: String)

// Letters of scripts whose glyphs join their neighbours. Splitting these per
// character would break the shaping, so they are grouped into words.
private fun isJoiningLetter(c: Char): Boolean {
  val code = c.code
  return code in 0x0620..0x064A ||
      code in 0x066E..0x06D3 ||
      code == 0x06D5 ||
      code in 0x06EE..0x06EF ||
      code in 0x06FA..0x06FF ||
      code in 0x0750..0x077F ||
      code in 0x08A0..0x08FF ||
      code in 0xFB50..0xFDFF ||
      code in 0xFE70..0xFEFF
}

private fun isRegionalIndicator(codePoint: Int) = codePoint in 0x1F1E6..0x1F1FF

// java.text.BreakIterator on older JVMs (unit tests) splits emoji sequences that ICU keeps
// together; join them explicitly so every runtime segments the same way.
private fun continuesGrapheme(text: String, start: Int, end: Int, previous: String?): Boolean {
  if (previous == null) return false
  val codePoint = text.codePointAt(start)
  if (codePoint in 0x1F3FB..0x1F3FF || codePoint == 0x200D || codePoint in 0xFE00..0xFE0F) return true
  if (codePoint in 0xE0020..0xE007F) return true
  if (previous.endsWith("\u200D")) return true
  val previousCodePoints = previous.codePoints().toArray()
  return isRegionalIndicator(codePoint) && previousCodePoints.size == 1 && isRegionalIndicator(previousCodePoints[0])
}

private fun graphemeRanges(text: String): List<IntRange> {
  val ranges = mutableListOf<IntRange>()
  val iterator = BreakIterator.getCharacterInstance()
  iterator.setText(text)
  var start = iterator.first()
  var end = iterator.next()
  while (end != BreakIterator.DONE) {
    val previous = ranges.lastOrNull()?.let { text.substring(it) }
    if (continuesGrapheme(text, start, end, previous)) {
      ranges[ranges.size - 1] = ranges.last().first until end
    } else {
      ranges.add(start until end)
    }
    start = end
    end = iterator.next()
  }
  return ranges
}

private fun segmentText(text: String, unit: TextTransitionUnit): List<Segment> {
  if (unit == TextTransitionUnit.WHOLE) {
    return if (text.isEmpty()) emptyList() else listOf(Segment(text.indices, text))
  }
  val segments = mutableListOf<Segment>()
  var previousJoins = false
  for (grapheme in graphemeRanges(text)) {
    val start = grapheme.first
    val end = grapheme.last + 1
    // Auto: joining letters group. Word: everything but whitespace groups.
    val joins = if (unit == TextTransitionUnit.WORD) !text[start].isWhitespace() else isJoiningLetter(text[start])
    if (joins && previousJoins && segments.isNotEmpty()) {
      val last = segments.removeAt(segments.size - 1)
      val range = last.range.first until end
      segments.add(Segment(range, text.substring(range)))
    } else {
      val range = start until end
      segments.add(Segment(range, text.substring(range)))
    }
    previousJoins = joins
  }
  return segments
}

private fun digitValue(c: Char): Int {
  val code = c.code
  return when (code) {
    in '0'.code..'9'.code -> code - '0'.code
    in 0x0660..0x0669 -> code - 0x0660
    in 0x06F0..0x06F9 -> code - 0x06F0
    else -> -1
  }
}

private fun containsDigit(text: String): Boolean = text.any { digitValue(it) >= 0 }

// Reads the number in a display string: digits in any of the supported sets,
// "." or "٫" as decimal point, "-" or "−" before the first digit as sign.
// Separators and labels ("SAR", "ر.س", ",") are ignored.
private fun parseNumber(text: String): Double? {
  var hasDigits = false
  var isNegative = false
  var inFraction = false
  var value = 0.0
  var scale = 0.1
  for (c in text) {
    val digit = digitValue(c)
    if (digit >= 0) {
      hasDigits = true
      if (inFraction) {
        value += digit * scale
        scale /= 10
      } else {
        value = value * 10 + digit
      }
    } else if (!hasDigits && (c == '-' || c == '−')) {
      isNegative = true
    } else if (hasDigits && !inFraction && (c == '.' || c == '٫')) {
      inFraction = true
    }
  }
  if (!hasDigits) return null
  return if (isNegative) -value else value
}

private fun resolveDirection(oldText: String, newText: String, direction: String?): Int {
  if (direction == "up") return 1
  if (direction == "down") return -1
  val oldValue = parseNumber(oldText) ?: return 1
  val newValue = parseNumber(newText) ?: return 1
  return if (newValue >= oldValue) 1 else -1
}

// Shared non-digit prefix and suffix (labels like "SAR ", "%", " ر.س") stay put;
// the rest is aligned from the trailing end so units stay over units.
private fun numericOps(oldSegments: List<Segment>, newSegments: List<Segment>): List<TextTransitionOp> {
  val n = oldSegments.size
  val m = newSegments.size
  var prefix = 0
  while (prefix < n &&
      prefix < m &&
      oldSegments[prefix].text == newSegments[prefix].text &&
      !containsDigit(oldSegments[prefix].text)) {
    prefix++
  }
  var suffix = 0
  while (suffix < n - prefix &&
      suffix < m - prefix &&
      oldSegments[n - 1 - suffix].text == newSegments[m - 1 - suffix].text &&
      !containsDigit(oldSegments[n - 1 - suffix].text)) {
    suffix++
  }

  val ops = mutableListOf<TextTransitionOp>()
  for (k in 0 until prefix) {
    ops.add(TextTransitionOp(SegmentChange.KEPT, oldSegments[k].range, newSegments[k].range))
  }

  val reversedMiddle = mutableListOf<TextTransitionOp>()
  val oldMiddle = n - prefix - suffix
  val newMiddle = m - prefix - suffix
  for (k in 0 until maxOf(oldMiddle, newMiddle)) {
    val o = if (k < oldMiddle) oldSegments[n - suffix - 1 - k] else null
    val w = if (k < newMiddle) newSegments[m - suffix - 1 - k] else null
    if (o != null && w != null && o.text == w.text) {
      reversedMiddle.add(TextTransitionOp(SegmentChange.KEPT, o.range, w.range))
      continue
    }
    // Reversed below, so INSERTED goes first to end up after REMOVED.
    if (w != null) reversedMiddle.add(TextTransitionOp(SegmentChange.INSERTED, null, w.range))
    if (o != null) reversedMiddle.add(TextTransitionOp(SegmentChange.REMOVED, o.range, null))
  }
  ops.addAll(reversedMiddle.asReversed())

  for (k in suffix - 1 downTo 0) {
    ops.add(TextTransitionOp(SegmentChange.KEPT, oldSegments[n - 1 - k].range, newSegments[m - 1 - k].range))
  }
  return ops
}

// Longest common subsequence; ties prefer removing from the old text first.
private fun lcsOps(oldSegments: List<Segment>, newSegments: List<Segment>): List<TextTransitionOp> {
  val n = oldSegments.size
  val m = newSegments.size
  val lcs = Array(n + 1) { IntArray(m + 1) }
  for (i in n - 1 downTo 0) {
    for (j in m - 1 downTo 0) {
      lcs[i][j] =
          if (oldSegments[i].text == newSegments[j].text) lcs[i + 1][j + 1] + 1
          else maxOf(lcs[i + 1][j], lcs[i][j + 1])
    }
  }
  val ops = mutableListOf<TextTransitionOp>()
  var i = 0
  var j = 0
  while (i < n || j < m) {
    if (i < n && j < m && oldSegments[i].text == newSegments[j].text) {
      ops.add(TextTransitionOp(SegmentChange.KEPT, oldSegments[i].range, newSegments[j].range))
      i++
      j++
    } else if (i < n && (j == m || lcs[i + 1][j] >= lcs[i][j + 1])) {
      ops.add(TextTransitionOp(SegmentChange.REMOVED, oldSegments[i].range, null))
      i++
    } else {
      ops.add(TextTransitionOp(SegmentChange.INSERTED, null, newSegments[j].range))
      j++
    }
  }
  return ops
}
