#!/bin/bash
# Runs the shared transition-plan fixtures against the iOS (macOS build) and,
# with --android, the Android implementation.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_DIR="$(mktemp -d)"
trap 'rm -rf "$BUILD_DIR"' EXIT

clang++ -std=c++17 -fobjc-arc -framework Foundation \
  -I "$ROOT/ios" \
  "$ROOT/ios/JBTextTransitionPlan.mm" \
  "$ROOT/tests/ios/TextTransitionPlanTests.mm" \
  -o "$BUILD_DIR/transition-plan-tests"
"$BUILD_DIR/transition-plan-tests" "$ROOT/fixtures/text-transitions.tsv"

if [[ "${1:-}" == "--android" ]]; then
  cd "$ROOT/example/android"
  ./gradlew :react-native-animateable-text:testDebugUnitTest --console=plain -q
  echo "Android transition plan: fixtures passed"
fi
