package com.reactnativereanimatedtext

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

// Runs fixtures/transition-keys.tsv, shared with the iOS (C++) implementation.
class TransitionKeyTest {
  /** Minimal JSON reader for the fixtures: values as ReadableArray.toArrayList() gives them. */
  private class Json(private val text: String) {
    private var index = 0

    fun read(): Any? {
      skipSpaces()
      val c = text[index]
      return when {
        c == '[' -> {
          index++
          val items = ArrayList<Any?>()
          skipSpaces()
          if (text[index] == ']') {
            index++
          } else {
            do {
              items.add(read())
              skipSpaces()
            } while (text[index++] == ',')
          }
          items
        }
        c == '{' -> {
          var depth = 0
          do {
            if (text[index] == '{') depth++
            if (text[index] == '}') depth--
            index++
          } while (depth > 0)
          HashMap<String, Any?>()
        }
        c == '"' -> {
          index++
          val out = StringBuilder()
          while (text[index] != '"') {
            if (text[index] == '\\') index++
            out.append(text[index++])
          }
          index++
          out.toString()
        }
        text.startsWith("true", index) -> true.also { index += 4 }
        text.startsWith("false", index) -> false.also { index += 5 }
        text.startsWith("null", index) -> null.also { index += 4 }
        else -> {
          val start = index
          while (index < text.length && text[index] in "+-0123456789.eE") index++
          text.substring(start, index).toDouble()
        }
      }
    }

    private fun skipSpaces() {
      while (index < text.length && text[index] == ' ') index++
    }
  }

  @Test
  fun matchesSharedFixtures() {
    val failures = mutableListOf<String>()
    var total = 0
    File("../fixtures/transition-keys.tsv").readLines().filter { it.isNotEmpty() && !it.startsWith("#") }.forEach { line ->
      val columns = line.split("\t")
      val actual = TransitionKey.encode(Json(columns[0]).read())
      total++
      if (actual != columns[1]) failures.add("${columns[0]}\n  expected: ${columns[1]}\n  actual:   $actual")
    }
    println("Android transition keys: ${total - failures.size}/$total fixtures passed")
    assertEquals(failures.joinToString("\n"), 0, failures.size)
  }
}
