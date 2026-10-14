package com.reactnativereanimatedtext

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

// Runs fixtures/text-transitions.tsv, shared with the iOS implementation.
class TextTransitionPlanTest {
  private fun fromFixture(value: String) = if (value == "∅") "" else value

  @Test
  fun matchesSharedFixtures() {
    val fixtures = File("../fixtures/text-transitions.tsv")
    val failures = mutableListOf<String>()
    var total = 0
    fixtures.readLines().filter { it.isNotEmpty() && !it.startsWith("#") }.forEach { line ->
      val columns = line.split("\t")
      val oldText = fromFixture(columns[2])
      val newText = fromFixture(columns[3])
      // Mode column: "mode" or "mode:unit".
      val modeAndUnit = columns[0].split(":")
      val plan =
          TextTransitionPlan.plan(
              oldText,
              newText,
              TextTransitionMode.fromString(modeAndUnit[0]),
              columns[1],
              TextTransitionUnit.fromString(modeAndUnit.getOrNull(1)))
      val direction = if (plan.direction > 0) "up" else "down"
      val ops = plan.fixtureDescription(oldText, newText)
      total++
      if (direction != columns[4] || ops != columns[5]) {
        failures.add(
            "${columns[0]}  ${columns[2]} → ${columns[3]}\n" +
                "  expected: ${columns[4]}  ${columns[5]}\n  actual:   $direction  $ops")
      }
    }
    println("Android transition plan: ${total - failures.size}/$total fixtures passed")
    assertEquals(failures.joinToString("\n"), 0, failures.size)
  }
}
