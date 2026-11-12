/*
 * Custom
 *
 * transitionKey: a text change runs contentTransition only if the key changed with it. The key
 * can be a string, number, boolean or an array of them, set as a React prop or from
 * useAnimatedProps, so it is normalised here from the raw value to one canonical string.
 * Android mirrors this in TransitionKey.kt; both must pass fixtures/transition-keys.tsv.
 */

#pragma once

#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <string>

namespace facebook::react {

inline void appendTransitionKeyNumber(std::string &out, double number)
{
  char buffer[32];
  if (std::isfinite(number) && std::floor(number) == number && std::fabs(number) <= 9007199254740992.0) {
    // Integral: decimal, so 1 and 1.0 are the same key and -0 is 0.
    std::snprintf(buffer, sizeof(buffer), "%lld", (long long)number);
  } else {
    // Anything else: the exact bits, which print the same on every platform.
    uint64_t bits = 0;
    std::memcpy(&bits, &number, sizeof(bits));
    std::snprintf(buffer, sizeof(buffer), "0x%016llx", (unsigned long long)bits);
  }
  out += buffer;
}

/*
 * Canonical form (fixtures/transition-keys.tsv): null, true, false, numbers (see above),
 * "strings" with \ and " escaped, [arrays,of,keys]; objects (not supported) are {}.
 * `Value` is folly::dynamic, or anything with the same accessors (the macOS fixture tests).
 */
template <typename Value>
void appendTransitionKey(std::string &out, const Value &value)
{
  if (value.isNull()) {
    out += "null";
  } else if (value.isBool()) {
    out += value.getBool() ? "true" : "false";
  } else if (value.isNumber()) {
    appendTransitionKeyNumber(out, value.asDouble());
  } else if (value.isString()) {
    out += '"';
    for (char c : value.getString()) {
      if (c == '"' || c == '\\') {
        out += '\\';
      }
      out += c;
    }
    out += '"';
  } else if (value.isArray()) {
    out += '[';
    for (size_t i = 0; i < value.size(); i++) {
      if (i > 0) {
        out += ',';
      }
      appendTransitionKey(out, value[i]);
    }
    out += ']';
  } else {
    out += "{}";
  }
}

template <typename Value>
std::string encodeTransitionKey(const Value &value)
{
  std::string out;
  appendTransitionKey(out, value);
  return out;
}

} // namespace facebook::react
