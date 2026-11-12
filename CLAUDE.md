# react-native-animateable-text — Claude Code instructions

A fork of React Native's `<Text>` whose content can be driven by a Reanimated shared value
(`animatedProps={{ text }}`) without a React render. Fabric (new architecture) only.

Current work: native text transitions (`contentTransition`, `reveal`, `transitionKey`). Plan, status, and
decisions: [`docs/text-transitions-plan.md`](./docs/text-transitions-plan.md). Read it before
touching transition code and update it when a decision changes.

## How it works

| Layer | Files | What it does |
|---|---|---|
| JS | `src/index.tsx`, `src/AnimateableText.tsx`, `src/TextProps.tsx` | Registers the `JBAnimatedText` host component (`createReactNativeComponentClass` + `validAttributes`), wraps it with `Animated.createAnimatedComponent`, handles press/highlight like RN's `<Text>`. |
| Codegen | `src/specs/JBAnimatedTextNativeComponent.tsx` | Native props for codegen (`JBAnimatedTextCodegen`). |
| C++ (shared) | `cpp/react/renderer/components/JBAnimatedText/` | A copy of RN's paragraph shadow node. `CParagraphProps` adds the `text` prop and the transition props; `CBaseTextShadowNode::buildAttributedString` builds one fragment from `text` instead of child nodes. Measurement and layout are RN's. `TransitionKey.h` (custom) encodes `transitionKey`. |
| iOS | `ios/JBAnimatedTextComponentView.{h,mm}` | `JBAnimatedTextComponentView : RCTParagraphComponentView`. RN's own paragraph view draws it at rest; runs `contentTransition` on text changes. |
| Android | `android/src/main/java/com/reactnativereanimatedtext/` | `JBAnimatedTextViewManager extends ReactTextViewManager` → `JBAnimatedTextView : ReactTextView`, which draws its own `TextView` layout at rest and runs `contentTransition` on text changes. |

### Content transitions

`contentTransition` (`numeric` / `roll` / `blur` / `fade`, optional `duration` ms, `direction`,
`unit`: `auto` / `word` / `whole`, `stagger` ms, `scope`: `all` / `numbers` / `changed`,
`minInterval` ms, and `delay` ms) is normalised in JS into eight flat native props (`contentTransitionType`,
`contentTransitionDuration`, `contentTransitionDirection`, `contentTransitionUnit`,
`contentTransitionStagger`, `contentTransitionScope`, `contentTransitionMinInterval`,
`contentTransitionDelay`), parsed in `CParagraphProps` (iOS) and
`@ReactProp` setters (Android). `reveal` (`roll` / `numeric` / `blur` / `fade`, `duration`,
`direction` `up`/`down`, `unit`, `stagger`, `delay` ms) becomes six more (`revealType`,
`revealDuration`, `revealDirection`, `revealUnit`, `revealStagger`, `revealDelay`), and
`transitionKey` is passed through as is (string / number / boolean / array) and encoded natively.

- `reveal` runs once per mounted view, the first time it shows non-empty text (mount with text,
  or the first `'' → text`); that change uses `reveal`, not `contentTransition`, whatever the
  key. With `reveal` `none` (default) `'' → text` is an ordinary change. iOS keeps it pending
  until `didMoveToWindow` (mount happens off-window); Android starts it on the first draw. Plan:
  `revealPlanForText:` / `TextTransitionPlan.reveal` (fixture mode `reveal:<type>[:unit]`).
  Recycling resets it. Changes during a reveal retarget it (with `contentTransition` `none`, they
  keep the reveal's look).
- `transitionKey` (top-level prop): when set, a text change runs `contentTransition` only if the
  key differs from the key at the previous text change (a key change alone arms the next text
  change). From rest, other text changes swap instantly (no layers/nodes, no roll hold); while a
  transition runs they retarget it. Canonical encoding: `TransitionKey.h` (iOS, from the raw
  prop value) / `TransitionKey.kt` (Android, from `Dynamic`), checked by
  `fixtures/transition-keys.tsv`. The decision itself lives in the views
  (`textMayHaveChanged` / `prepareForTextChange` + `onTextApplied`).

- `roll` is for values that change quickly (chart scrubbing, sliders). It uses `numeric`'s
  segmentation, slots, caps and fallbacks (`isNumericMode:` / `isNumeric`). `scope` (roll only)
  picks what is replaced on a change: `all` (default) every segment, unchanged ones too;
  `numbers` everything but a shared non-digit prefix/suffix (`SAR ` stays put); `changed` only
  what changed (numeric's diff). It has its own row in
  the motion table: rise 0.25 × line height (≈ 0.3 em), no shrink, light blur. JS defaults for
  `roll`: `direction: 'up'`, `duration: 200`, `stagger: 30`.
- `roll` is throttled: a change that arrives before the previous roll has landed is held back
  (the segments keep showing the old text while RN already has the new one), and one roll to
  the latest text runs when the wait is up; the last change is always shown. The wait is
  `minInterval` (ms; `0` = off) or, by default, the roll's stagger spread + 0.65 × duration
  (`minIntervalFactor` in the motion table). iOS: `shouldHoldChange` / `holdChangeWithViewOffset:`
  / `showHeldChange` in the component view (timer); Android: `heldChange` in `onDraw`. Other
  types are not throttled.
- `stagger` (any type; default 0) delays each changed position by `index × stagger` after the
  first changed position, in logical order; the total spread is capped at 300 ms. The index
  (`staggerIndex`) is computed in the plan files and checked by the fixtures' last column.
- `delay` (any type; default 0; `contentTransition` and `reveal`) shifts the whole schedule:
  every position (leaving, kept, arriving) starts `delay` after the change, and the old text
  stays on screen meanwhile (from rest the segments are set up at once and wait). A change that
  arrives while the running transition is still in its delay joins it: it re-aims at the newest
  text and keeps the original start (animators: `_transitionStart` / `transitionStartMs`;
  `isWaitingToStart`). Later changes get their own delay. With `transitionKey`, a change that
  doesn't animate ignores the delay (instant from rest; joins a transition still in its
  delay; otherwise retargets at once). The roll throttle doesn't hold a change while the
  transition is in its delay (it re-aims instead), and its wait counts from the transition's
  start (after the delay). Two nodes side by side (Malaa's integer + decimals) read as one
  wave when the second's `delay` is the first's positions × its (capped) stagger step.

| Piece | iOS | Android |
|---|---|---|
| Plan: segmentation, diff, direction (pure, unit-tested) | `ios/JBTextTransitionPlan.{h,mm}` | `TextTransitionPlan.kt` |
| Timing + per-segment looks (keep in sync) | `ios/JBTextTransitionAnimator.mm` | `TextTransitionTiming.kt` |
| Rendering (stateful: per-segment timelines, retargets mid-transition) | `JBTextTransitionAnimator` (CALayers, vImage blur levels, cache) | `JBAnimatedTextView.Transition` (RenderNodes; blur quantised to 4 levels, one `RenderEffect` group node per level) |
| Hook | `JBAnimatedTextComponentView` `finalizeUpdates:` → `textMayHaveChanged`, `didMoveToWindow` (reveal) | `JBAnimatedTextViewManager.updateExtraData` → `prepareForTextChange()` / `onTextApplied()`; reveal in `onDraw` |
| `transitionKey` encoding | `cpp/.../TransitionKey.h` | `TransitionKey.kt` |

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
- **Stagger and delay only shift a segment's start.** A staggered leave is a pending leave: the
  segment carries on until its turn. Leaving segments, including ones still waiting for their
  turn, never move sideways: they rise/fade from their own x (`hasFixedX` /
  `fixedTranslateX`), even when the text's width changes; arriving segments arrive straight at
  their final x; only kept segments slide. (Waiting leaves used to follow their slot, which
  piled glyphs up on `1,234.55 → *****`.) So that a width change never draws a new glyph over
  an old one, a waiting leave starts no later than the first arrival that lands on it on
  screen (`overlapsOnScreen`, end of `transitionFrom:` / `retarget`). On a new change, segments that have started leaving
  hurry out; ones still waiting to leave keep their schedule; kept segments that are moving
  re-aim at once (at rest: they move when the transition starts). An arriving
  segment that is replaced before it showed (still waiting, or fainter than the mode's
  `takeoverOpacity`; `roll` only) hands its timeline to its replacement, so scrubbing never
  pushes trailing digits back or leaves fast-changing digits invisible.
- **Changes every frame must stay bounded.** Leaving segments hurry out once and are capped;
  unchanged segments are reused. After touching the animators, run the example's "Stress: 4
  texts every frame" card for 30 s with `blur` and check memory stays flat (iOS:
  `ps -o rss= -p <pid>`; Android: `dumpsys meminfo` + `dumpsys gfxinfo`) and close to `none`.
  The emulator is noisy: compare medians of 3 alternating runs, never single runs.
- **Android: never put a `RenderEffect` on individual segment nodes.** Each effect is an
  offscreen pass; blur goes through the per-level group nodes.
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
fixtures/   Shared transition-plan and transition-key fixtures (iOS + Android)
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
| `animated` | Animated text | Everything in one screen. Cards `key` (transitionKey hide/show: props; useAnimatedProps integer + decimals with Malaa's decimals `delay` = integer positions × 40 ms (2a) and without (2b); array key `[isHidden, currency]`; a `delay: 300` node (4); "Roll value", "Roll twice, 120 ms apart" (second change during the delay), "Remount" (reveal of 2 and 4)) and `reveal` (Remount / Mount empty then load / Remount + update at 100 ms; type + delay) use Malaa's `{ type: 'roll', stagger: 40, duration: 300 }`. A settings card (contentTransition `none`/`numeric`/`roll`/`blur`/`fade`, direction, unit `auto`/`word`/`whole`, duration, stagger, roll scope `all`/`numbers`/`changed`, roll min interval, delay, font: System or Malaa's IBM Plex Sans Arabic) applies to every demo: chart scrub (drag, or "Auto scrub"), stress, original slider (drag, or "Auto drag"), balance (Latin + Arabic-Indic), percentage slider with colour (red/grey/green), centred number between boxes (bounds tint + optional Reanimated `LinearTransition` on the row), count-up and stopwatch (every-frame updates), words (Latin + Arabic), multi-line with `numberOfLines`. `transitions` is an alias. Query (for recordings and measurements without touching the screen): `transition`, `direction`, `duration`, `stagger`, `unit`, `scope`, `minInterval`, `delay`, `font`, `only=key,reveal,chart,stress,slider,…` (show just those cards), `autorun=1` (start auto scrub / stress / auto drag, step through the key card, cycle reveal mounts), `reveal`, `revealDelay` (reveal card). |
| `parity` | Parity | `<Text>` vs `<AnimateableText>` side by side (27 cases). Query: `page`, `pageSize`, `perturb=1`, `autoAdvanceMs`, `runId`. |

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

Expected now: 27/27 identical on iOS and Android (21/21 at the largest size before the
`transition-*` cases were added); self-test 27/27 detected. Pages with `transition-*` cases
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

iOS compiles `ios/JBTextTransitionPlan.mm` with `tests/ios/TextTransitionPlanTests.mm`, and
`cpp/.../TransitionKey.h` with `tests/ios/TransitionKeyTests.mm`, for macOS; Android runs
`android/src/test/.../TextTransitionPlanTest.kt` and `TransitionKeyTest.kt` through the
example's Gradle (`:react-native-animateable-text:testDebugUnitTest`).
`fixtures/transition-keys.tsv`: key as JSON, canonical string. Both read
`fixtures/text-transitions.tsv`: mode (or `mode:unit`, `mode:unit:scope`), direction option, old, new, expected direction,
expected ops (`=kept -removed +inserted`, `␠` for a space, `∅` for empty text; mode `reveal:<type>[:unit]` runs the reveal plan, old text `∅`), expected stagger
(the stagger index of every removed/inserted op in order, `∅` if none).

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
