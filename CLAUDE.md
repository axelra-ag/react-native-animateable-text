# react-native-animateable-text — Claude Code instructions

A fork of React Native's `<Text>` whose content can be driven by a Reanimated shared value
(`animatedProps={{ text }}`) without a React render. Fabric (new architecture) only.

Current work: native text transitions (`contentTransition`, `reveal`). Plan, status, and
decisions: [`docs/text-transitions-plan.md`](./docs/text-transitions-plan.md). Read it before
touching transition code and update it when a decision changes.

## How it works

| Layer | Files | What it does |
|---|---|---|
| JS | `src/index.tsx`, `src/AnimateableText.tsx`, `src/TextProps.tsx` | Registers the `JBAnimatedText` host component (`createReactNativeComponentClass` + `validAttributes`), wraps it with `Animated.createAnimatedComponent`, handles press/highlight like RN's `<Text>`. |
| Codegen | `src/specs/JBAnimatedTextNativeComponent.tsx` | Native props for codegen (`JBAnimatedTextCodegen`). |
| C++ (shared) | `cpp/react/renderer/components/JBAnimatedText/` | A copy of RN's paragraph shadow node. `CParagraphProps` adds the `text` prop; `CBaseTextShadowNode::buildAttributedString` builds one fragment from `text` instead of child nodes. Measurement and layout are RN's. |
| iOS | `ios/JBAnimatedTextComponentView.{h,mm}` | `JBAnimatedTextComponentView : RCTParagraphComponentView`. RN's own paragraph view draws it at rest; runs `contentTransition` on text changes. |
| Android | `android/src/main/java/com/reactnativereanimatedtext/` | `JBAnimatedTextViewManager extends ReactTextViewManager` → `JBAnimatedTextView : ReactTextView`, which draws its own `TextView` layout at rest and runs `contentTransition` on text changes. |

### Content transitions

`contentTransition` (`numeric` / `blur` / `fade`, optional `duration` ms and `direction`) is
normalised in JS into three flat native props (`contentTransitionType`,
`contentTransitionDuration`, `contentTransitionDirection`), parsed in `CParagraphProps` (iOS)
and `@ReactProp` setters (Android).

| Piece | iOS | Android |
|---|---|---|
| Plan: segmentation, diff, direction (pure, unit-tested) | `ios/JBTextTransitionPlan.{h,mm}` | `TextTransitionPlan.kt` |
| Timing + per-segment looks (keep in sync) | `ios/JBTextTransitionAnimator.mm` | `TextTransitionTiming.kt` |
| Rendering (stateful: per-segment timelines, retargets mid-transition) | `JBTextTransitionAnimator` (CALayers, vImage blur levels, cache) | `JBAnimatedTextView.Transition` (RenderNodes, `RenderEffect` blur) |
| Hook | `JBAnimatedTextComponentView` `finalizeUpdates:` | `JBAnimatedTextViewManager.updateExtraData` → `prepareForTextChange()` |

Rules:

- **Both platforms implement the same spec.** Any change to segmentation, diff, or direction
  goes into both plan files and gets a row in `fixtures/text-transitions.tsv`. Any change to
  timing or looks goes into both timing files.
- **At rest the view is RN's.** Transition layers/nodes exist only while animating; when they
  finish, RN's own text drawing is shown again. Parity must stay identical, including the
  `transition-*` cases that are measured after a transition settles.
- **iOS draws glyphs with RN's text storage** (internal `RCTTextLayoutManager` methods behind a
  category, guarded with `respondsToSelector:`). Never re-lay out text yourself.
- **Changes mid-transition retarget, never snap.** Segments keep their own timelines (settle /
  leave from their current look); test with the slider demo after touching the animators.
- **Android must capture the old text before RN updates it.** `TextView` reuses its
  `BoringLayout` for single-line text, so a kept reference to the old `Layout` shows the new text.
- Design notes, numbers, and known limitations: `docs/text-transitions-plan.md`.

Rules for this design:

- **Never lay out text ourselves.** Size, line breaks, and drawing come from RN. Anything
  visible must match `<Text>` pixel for pixel (see Parity below).
- **Copied RN files keep their markers.** Changes in `cpp/` are tagged `// EDITED` or
  `// ADDED … // END ADDED`. Keep the tags so the copy can be re-synced. Check for drift
  against the RN version in use:

  ```bash
  diff <(sed 's/CParagraph/Paragraph/g; s| *// EDITED||' cpp/react/renderer/components/JBAnimatedText/ParagraphShadowNode.cpp) example/node_modules/react-native/ReactCommon/react/renderer/components/text/ParagraphShadowNode.cpp
  ```

  On RN 0.86.3 the only difference is the component name.
- **Defaults must match RN's `<Text>` JS.** RN's `Text.js` sends some props unconditionally
  (`ellipsizeMode: 'tail'`). Native defaults differ (`EllipsizeMode::Clip`), so
  `AnimateableText.tsx` must send the same defaults. Missing this broke `numberOfLines`
  truncation and `adjustsFontSizeToFit` on iOS until phase 0.
- **RN version coupling.** Every RN minor can break the copied C++ and the iOS/Android
  subclasses. Bump the README compatibility table and `peerDependencies` together.

## Repo layout

```
src/        JS/TS (published; "react-native" entry points here)
cpp/        Shared C++ (copied RN paragraph code + text prop)
ios/        iOS component view
android/    Android view manager (android/build/ is a stale artifact — don't edit)
lib/        bob build output (generated)
example/    Expo dev-client app (Expo 57, RN 0.86.3, Reanimated 4.5.1)
docs/       Plans and design notes
fixtures/   Shared transition-plan fixtures (iOS + Android)
tests/ios/  macOS runner for the iOS plan fixtures
scripts/    test-transition-plan.sh
```

## Tooling

- Yarn **3.6.4** (berry, `nodeLinker: node-modules`) at the root and in `example/`
  (separate lockfiles). Don't switch package managers.
- Root devDependencies track the RN/Reanimated versions the example uses; type-check both:

  ```bash
  npx tsc --noEmit -p tsconfig.build.json
  cd example && npx tsc --noEmit -p .
  ```

  (`yarn typescript` runs `tsc --noEmit` without a project and fails — there is no root
  `tsconfig.json`.)

- Commits follow Conventional Commits (commitlint).
- `CONTRIBUTING.md`'s workflow (`yarn bootstrap`, `yarn example ios`) predates the Expo
  example; use the commands below.

## Example app

Sections (home screen, or deep link `animateabletext://<route>`):

| Route | Screen | Purpose |
|---|---|---|
| `home` | Sections list | |
| `animated` | Animated text | Everything in one screen. A settings card (contentTransition `none`/`numeric`/`blur`/`fade`, direction, duration, font: System or Malaa's IBM Plex Sans Arabic) applies to every demo: original slider, balance (Latin + Arabic-Indic), percentage slider with colour (red/grey/green), centred number between boxes (bounds tint + optional Reanimated `LinearTransition` on the row), count-up and stopwatch (every-frame updates), words (Latin + Arabic), multi-line with `numberOfLines`. `transitions` is an alias. |
| `parity` | Parity | `<Text>` vs `<AnimateableText>` side by side (24 cases). Query: `page`, `pageSize`, `perturb=1`, `autoAdvanceMs`, `runId`. |

How it is wired:

- Metro resolves `react-native-animateable-text` to `../src` (`example/metro.config.js`), so JS
  edits are live. Root copies of `react`, `react-native`, `react-native-reanimated`,
  `react-native-worklets` are block-listed so only the example's copies load.
- Native code is linked from the repo root via `example/react-native.config.js`; native edits
  need a rebuild.
- `example/ios` and `example/android` are generated (`expo prebuild`) and git-ignored.
- iOS settings mirror Malaa: `useFrameworks: 'static'`, `enableSceneSupport: true`.
  iOS 27 kills apps that don't adopt the scene lifecycle.

### Running it

```bash
cd example
yarn install
LANG=en_US.UTF-8 LC_ALL=en_US.UTF-8 npx expo prebuild --clean
npx expo start --dev-client --port 8095
LANG=en_US.UTF-8 LC_ALL=en_US.UTF-8 npx expo run:ios --device <simulator udid> --no-bundler
ANDROID_HOME=$HOME/Library/Android/sdk npx expo run:android --device <avd name> --no-bundler
```

Then point the dev client at Metro:

```bash
xcrun simctl openurl <udid> "exp+animateable-text-example://expo-development-client/?url=http%3A%2F%2F127.0.0.1%3A8095"
adb -s <serial> reverse tcp:8095 tcp:8095
adb -s <serial> shell am start -a android.intent.action.VIEW -d "'exp+animateable-text-example://expo-development-client/?url=http%3A%2F%2F127.0.0.1%3A8095'"
```

Gotchas:

- CocoaPods fails with `Unicode Normalization not appropriate for ASCII-8BIT` unless `LANG`
  is UTF-8.
- Gradle needs `ANDROID_HOME`.
- `--port` and `--no-bundler` can't be combined. `run:ios`/`run:android` open the app against
  port 8081 afterwards. Re-open it with the URL above.
- Metro started with `CI=1` disables reloads. Expo only streams app logs to an interactive
  terminal. Read JS logs from the device instead (`adb logcat -s ReactNativeJS:I`, or
  `xcrun simctl spawn <udid> log stream --level info --predicate 'subsystem == "com.facebook.react.log"'`).
- Editing `src/AnimateableText.tsx` under Fast Refresh throws
  `Tried to register two views with the same name JBAnimatedText`. Reload the app.
- The dev-client onboarding sheet (first launch) and the dev-tools button (top-right) can
  cover content. Screen headers start at `HEADER_TOP` (130pt) to clear the button.
- Fonts: `example/assets/fonts/IBMPlexSansArabicSAR-*.ttf` are copied from Malaa
  (`src/assets/fonts/`) and loaded at runtime with `expo-font` (`useFonts` in `App.tsx`), so
  switching fonts needs no native rebuild.
- The `Deep imports from the 'react-native' package are deprecated` warnings come from
  `src/AnimateableText.tsx` and are expected for now.

## Parity tests

`example/scripts/parity.mjs` proves AnimateableText renders exactly like `<Text>`.

1. Opens `animateabletext://parity?...&autoAdvanceMs=…&runId=…` once; the app pages through
   the cases itself (iOS asks "Open in…?" per deep link otherwise).
2. For each page the app logs `PARITY_PAGE` / `PARITY_CASE` (rects from `measureInWindow`)
   tagged with the `runId`; the script reads them from the device log.
3. It screenshots the device, crops both sides of each case, and diffs them with `pixelmatch`
   at threshold 0.

```bash
cd example
node scripts/parity.mjs --platform ios --device <simulator udid>
node scripts/parity.mjs --platform android --device <adb serial>
node scripts/parity.mjs --platform ios --device <udid> --selftest
```

Options: `--pageSize` (default 4), `--dwell` ms per page (default 2500), `--label`, `--out`
(default `example/parity-output/<platform>-<label>`, git-ignored; only `.png` files there are
deleted between runs).

Results per case:

| Result | Meaning |
|---|---|
| `PASS` | Pixel-identical. |
| `PASS (visible part only)` | Row taller than the screen; the visible part matched. |
| `FAIL n px differ` | Look at `<id>-text.png`, `<id>-animateable.png`, `<id>-diff.png`. |
| `FAIL size differs` | Layout differs — usually a props/defaults mismatch. |
| `FAIL … rendered nothing` | A blank crop; never counts as a pass. |
| `FAIL off screen` | Lower `--pageSize`. |

`--selftest` (`perturb=1`) adds +0.25pt font size and a one-step colour nudge to the
AnimateableText side only. Every case must be detected; if one passes, the harness is broken.
(Android ignores `letterSpacing` for Arabic, so don't use it as the perturbation.)

Largest accessibility size:

```bash
xcrun simctl ui <udid> content_size accessibility-extra-extra-extra-large
adb -s <serial> shell settings put system font_scale 2.0
node scripts/parity.mjs --platform ios --device <udid> --pageSize 1 --dwell 1800 --label xl
xcrun simctl ui <udid> content_size large
adb -s <serial> shell settings put system font_scale 1.0
```

Expected now: 24/24 identical on iOS and Android (21/21 at the largest size before the
`transition-*` cases were added); self-test 24/24 detected. Pages with `transition-*` cases
wait 1.6 s before measuring so the transition has settled. A rare Android run times out
waiting for page 0; rerun it.

**Run the baseline and the self-test on both platforms after any change to `src/`, `cpp/`,
`ios/`, or `android/`.** A non-identical result is a regression, not noise.

Adding a case: append to `example/src/parity/parityCases.ts` (`id`, `text`, `style`, optional
`props` / `useAnimatedProps`). Keep page heights within one screen at `--pageSize 4`.

## Transition plan tests

```bash
scripts/test-transition-plan.sh
ANDROID_HOME=$HOME/Library/Android/sdk scripts/test-transition-plan.sh --android
```

iOS compiles `ios/JBTextTransitionPlan.mm` with `tests/ios/TextTransitionPlanTests.mm` for
macOS; Android runs `android/src/test/.../TextTransitionPlanTest.kt` through the example's
Gradle (`:react-native-animateable-text:testDebugUnitTest`). Both read
`fixtures/text-transitions.tsv`: mode, direction option, old, new, expected direction,
expected ops (`=kept -removed +inserted`, `␠` for a space, `∅` for empty text).

## Seeing an animation frame by frame

Screenshots are too slow; record video and tile the frames that change (needs `ffmpeg`).

```bash
xcrun simctl io <udid> recordVideo --codec h264 --force rec.mp4   # Ctrl-C to stop
adb -s <serial> shell screenrecord --size 720x1600 --time-limit 5 /sdcard/rec.mp4 && adb pull /sdcard/rec.mp4
ffmpeg -i rec.mp4 -vf "crop=W:H:X:Y,mpdecimate,setpts=N/TB,tile=3x8" -frames:v 1 sheet.png
```

Use the 1500 ms duration in the transitions screen to make frames easier to read.

## Open items

- Android with RN's `enablePreparedTextLayout` flag on is untested (off by default). With it
  on, stock `<Text>` uses `PreparedLayoutTextView` while this package still uses
  `ReactTextView`.
- `yarn typescript` script is broken (see Tooling).
- Housekeeping list in the plan doc (`android/build` in the npm tarball, podspec source URL,
  no-op `addWhitelistedNativeProps`, `TouchableText` stale `onPress` closure).
