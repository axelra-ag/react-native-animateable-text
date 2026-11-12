/*
 * Custom
 *
 * transitionKey: a text change runs contentTransition only if the key changed with it. The key
 * can be a string, number, boolean or an array of them, set as a React prop or from
 * useAnimatedProps, so it is normalised here to one canonical string. Mirrors
 * cpp/.../JBAnimatedText/TransitionKey.h; both must pass fixtures/transition-keys.tsv.
 */

package com.reactnativereanimatedtext

import com.facebook.react.bridge.Dynamic
import com.facebook.react.bridge.ReadableType
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

object TransitionKey {
  /** The canonical key of a prop value; null (no key) for a missing or null value. */
  fun fromDynamic(dynamic: Dynamic?): String? {
    if (dynamic == null) return null
    return when (dynamic.type) {
      ReadableType.Null -> null
      ReadableType.Boolean -> encode(dynamic.asBoolean())
      ReadableType.Number -> encode(dynamic.asDouble())
      ReadableType.String -> encode(dynamic.asString())
      ReadableType.Array -> encode(dynamic.asArray()?.toArrayList())
      ReadableType.Map -> encode(emptyMap<String, Any>())
    }
  }

  /**
   * Canonical form (fixtures/transition-keys.tsv): null, true, false, numbers (see [number]),
   * "strings" with \ and " escaped, [arrays,of,keys]; objects (not supported) are {}.
   */
  fun encode(value: Any?): String = StringBuilder().also { append(it, value) }.toString()

  private fun append(out: StringBuilder, value: Any?) {
    when (value) {
      null -> out.append("null")
      is Boolean -> out.append(if (value) "true" else "false")
      is Number -> out.append(number(value.toDouble()))
      is String -> {
        out.append('"')
        for (c in value) {
          if (c == '"' || c == '\\') out.append('\\')
          out.append(c)
        }
        out.append('"')
      }
      is List<*> -> {
        out.append('[')
        value.forEachIndexed { index, item ->
          if (index > 0) out.append(',')
          append(out, item)
        }
        out.append(']')
      }
      else -> out.append("{}")
    }
  }

  /** Integral: decimal (1 and 1.0 are the same key, -0 is 0); anything else: the exact bits. */
  private fun number(value: Double): String =
      if (value.isFinite() && floor(value) == value && abs(value) <= 9007199254740992.0) value.toLong().toString()
      else "0x" + String.format(Locale.ROOT, "%016x", value.toRawBits())
}
