# Text transitions — plan

Status: phases 0–2 done (prototype) · Branch: `feat/text-transitions` · Base: `0.19.3` (RN 0.86.3, Fabric only)

## Goal

Let `AnimateableText` animate its text natively, opted into with one prop:

```tsx
<AnimateableText text={balance} contentTransition="numeric" />
<AnimateableText text={title} reveal="blur" />
<AnimateableText text={reply} reveal="stream" />
```

Three effects, one engine:

| Effect | Trigger | Leaving | Arriving | Timing |
|---|---|---|---|---|
| Content transition (SwiftUI `numericText`) | `text` changes | changed segments of old text | changed segments of new text | together, spring |
| Reveal | first non-empty text | nothing | every segment | staggered |
| Stream (AI chat) | `text` grows | nothing | appended segments | steady rate, catches up |

## Principles

1. **100% native.** No Skia, no per-character RN views, no per-frame JS or Reanimated work.
   JS/Reanimated only sets `text`; animation runs on Core Animation (iOS) and
   RenderNode/RenderEffect + `SpringAnimation` (Android).
2. **We never lay out text.** Size, line breaks, and placement come from RN's own
   paragraph layout (our `CParagraphShadowNode` is a byte-for-byte copy of RN's). Pixels come
   from RN's own text layout managers.
3. **At rest, it is `<Text>`.** When no transition is running, the view draws exactly what
   stock RN draws. Animation layers exist only while a transition runs.
4. **Opt-in and zero-cost.** No prop set → no diffing, no extra layers, no behavior change.
5. **Same behavior on both platforms.** One spec for segmentation, diff, and timing, enforced
   by shared test fixtures.

## Non-goals (for now)

- Old architecture (Paper). Dropped since 0.18.
- Rich text / multiple styles inside `text` (needed for markdown AI replies — separate project).
- Web: props are accepted and ignored (text swaps instantly).

## Public API

```ts
type ContentTransition =
  | 'none'
  | 'numeric'
  | 'blur'
  | 'fade'
  | {
      type: 'numeric' | 'blur' | 'fade';
      duration?: number;
      direction?: 'auto' | 'up' | 'down';
    };

type Reveal =
  | 'none'
  | 'blur'
  | 'fade'
  | 'stream'
  | {
      type: 'blur' | 'fade';
      unit?: 'auto' | 'character' | 'word' | 'line';
      stagger?: number;
      duration?: number;
    }
  | {
      type: 'stream';
      charsPerSecond?: number;
    };
```

- Names avoid Reanimated collisions (`transition*` is Reanimated 4 CSS transitions;
  `entering`/`exiting`/`layout` are Reanimated layout animations on the wrapped component).
- JS normalizes shorthand strings into the object form; native receives one flat, typed set
  of props (no free-form objects across the bridge).
- Works with plain `text` and with `animatedProps={{text}}` — native reacts to the text
  change, not to who set it.
- `contentTransition="numeric"` direction `auto`: parse digits from old/new text (Western and
  Arabic-Indic, ignore separators and currency labels); up if increasing, down if
  decreasing, up if unparseable.

## Behavior spec

### Segmentation

| Content | Unit |
|---|---|
| Digits (`0-9`, `٠-٩`), `.` `,` `٫` `٬` `%` `+` `-` | grapheme |
| Latin and other non-joining scripts | grapheme |
| Joining scripts (Arabic) | word |
| `unit` set explicitly | as requested (`character` on joining scripts is downgraded to `word`) |

Graphemes come from platform APIs (`rangeOfComposedCharacterSequence` /
`BreakIterator`) so Arabic diacritics and emoji are never split.

### Diff

- `numeric`: align old and new from the trailing end; unchanged graphemes stay put;
  separators that keep their logical position stay put.
- `blur` / `fade`: longest-common-subsequence over segments; kept segments slide to their
  new position, removed blur/fade out, inserted blur/fade in.
- Stream: if new text starts with the old text → only the suffix is inserted. Otherwise
  fall back to a `fade` content transition.

### Drawing segments

Each moving segment is a crop of the **real** RN layout (old or new) — never re-shaped on its
own. Arabic stays joined because we crop an already-shaped line.

### Fallbacks (cross-fade the whole text instead)

- Truncation (`numberOfLines`) changes where `…` falls.
- `adjustsFontSizeToFit` changes the scale between old and new.
- Old/new have different line counts in `numeric` mode.
- More than N segments animating at once (cap, to be tuned).

### Edge rules

- **Interruption:** a new change mid-transition starts from what is on screen now.
- **Width change:** outgoing segments may draw outside the new bounds (RN views don't clip
  by default); nothing jumps.
- **Selection:** starting a selection (`selectable`) completes the transition instantly.
- **Reduce Motion / Remove animations:** instant swap.
- **Accessibility:** screen readers get the new text immediately; animation is visual only.
- **Recycling:** `prepareForRecycle` cancels any running transition.
- **First mount:** `contentTransition` does not run on mount; `reveal` does.

## Architecture

### JS / TS (`src/`)

- `TextProps.tsx`: add `contentTransition`, `reveal` types.
- `AnimateableText.tsx`: normalize shorthands → flat native props; add them to
  `validAttributes`.
- `specs/JBAnimatedTextNativeComponent.tsx`: add the flat props for codegen.

### C++ (`cpp/`)

- `ParagraphProps.{h,cpp}`: parse the new props in `CParagraphProps` (same pattern as `text`).
- No layout changes. Transitions never affect measurement.

### iOS (`ios/`)

`JBAnimatedTextComponentView : RCTParagraphComponentView` (already exists).

- Override `updateState:` → keep the old `ParagraphState` and layout metrics, call `super`,
  then start a transition if props ask for one and the text changed.
- During a transition: hide RN's internal text subview; add one `CALayer` per moving
  segment; restore when done.
- **Segment pixels:** draw with RN's own
  `RCTTextLayoutManager drawAttributedString:paragraphAttributes:frame:…` into an image,
  then crop by segment rect → identical glyphs to `<Text>`.
- **Segment rects:** spike (Phase 0). Options:
  1. Call RN's internal `_textStorageAndLayoutManagerWithAttributesString:` via a category
     (RN-internal method, not an Apple private API) and query `NSLayoutManager`.
  2. Use public `getRectWithAttributedString:…enumerateAttribute:` with a marker attribute.
- **Blur:** iOS has no public per-layer blur filter (`CAFilter` is private — not allowed in a
  banking app). Pre-render each segment at ~5 blur radii with `CIGaussianBlur` and drive
  `contents` with a keyframe animation alongside opacity/position springs.
- **Timing:** `CASpringAnimation`, parameters tuned against a SwiftUI recording.

### Android (`android/`)

- New `JBAnimatedTextView : ReactTextView`; `JBAnimatedTextViewManager.createViewInstance`
  returns it.
- Intercept `updateState` / `updateExtraData` → keep the old `android.text.Layout`, apply the
  new update, then start a transition.
- Handle both RN 0.86 text paths: `PreparedLayout` (`setPreparedLayout`) and spannable
  (`setText`). Test with the experimental flag on and off.
- **Segment rects:** `Layout.getPrimaryHorizontal`, `getLineForOffset`, `getLineTop/Bottom`.
- **Segment pixels:** record `layout.draw(canvas)` clipped to the segment into one
  `RenderNode` per segment (API 29+).
- **Blur:** `RenderEffect.createBlurEffect` on the RenderNode (API 31+). API < 31: no blur,
  same motion. (Malaa's minSdk is 30, so only Android 11 loses blur.)
- **Timing:** AndroidX `SpringAnimation`, same parameters as iOS.
- Package `minSdkVersion` stays 21; transitions are a no-op below 29.

### Where the engine lives — decision

Segmentation needs platform text APIs, and Android's view is Kotlin/Java, so a shared C++
engine would need a JNI bridge for a ~200-line algorithm.

**Recommendation:** implement segmentation + diff per platform (ObjC++ / Kotlin) against
one spec, and enforce parity with **shared JSON fixtures** (`fixtures/transitions/*.json`:
old text, new text, mode → expected segments) run by both platforms' unit tests.

## Verification

1. **Parity screen** in `example/`: `<Text>` and `<AnimateableText>` side by side, same style,
   screenshot pixel-diff at rest and before/after every transition. Matrix:
   - English / Arabic, LTR / RTL
   - `textAlign` left / center / right
   - `lineHeight`, `letterSpacing`, `fontVariant: ['tabular-nums']`
   - multi-line, `numberOfLines`, `adjustsFontSizeToFit`
   - largest accessibility font size
   - Android: PreparedLayout flag on / off

   Any pixel difference fails.
2. **Fixture tests** for segmentation + diff on both platforms.
3. **Performance:** 20 simultaneous `numeric` transitions hold 60/120 fps on a mid-range
   Android and an older iPhone; no main-thread hitch when a transition starts.
4. **In Malaa:** link via `yalc` (the example already uses it), try `AnimatedMoneyText`,
   `PerformanceBadge`, `DragSlider`.

## Phases

| # | Scope | Done when |
|---|---|---|
| 0 | Bump `example/` to Expo 57 / RN 0.86.3. Parity screen with **no** transitions (proves today's baseline). iOS segment-rect spike. Android text-path check. | Baseline pixel-identical on both platforms; spike picks the iOS rect approach |
| 1 | Prop plumbing JS → C++ → native, no animation. Types, normalization, web no-op. | Props reach native on both platforms; parity still green |
| 2 | Engine + `contentTransition` (`numeric`, `blur`, `fade`), single-line. Fallbacks, interruption, reduce motion, recycling. | Fixtures green on both; parity green at endpoints; perf target met |
| 3 | Malaa integration via `yalc`; release `0.20.0-beta.0`. | `AnimatedMoneyText` uses `contentTransition="numeric"` on device |
| 4 | `reveal` (`blur`, `fade`), stagger, multi-line segment rects. | Parity green; multi-line fixtures green |
| 5 | `reveal="stream"`: rate-limited reveal cursor, line reflow, height follows RN layout (grows per line). | Smooth reveal under bursty input |

## Phase 0 — results

Done on iPhone 17 simulator (iOS 27) and Pixel 10 Pro XL emulator, RN 0.86.3 / Expo 57.

- `example/` bumped to Expo 57 / RN 0.86.3 / Reanimated 4.5.1 with Malaa's iOS build
  settings (`useFrameworks: 'static'`, `enableSceneSupport: true` — iOS 27 kills apps
  without scene lifecycle). Root devDependencies bumped to match; `tsc` clean in both.
- Metro resolves the package from `../src`, so JS edits are live. Note: editing
  `src/AnimateableText.tsx` under Fast Refresh throws "Tried to register two views with the
  same name JBAnimatedText" — reload the app instead.
- Parity harness: `example/src/parity/` + `example/scripts/parity.mjs`.
  - 21 cases; each pair is pixel-diffed from a device screenshot (threshold 0).
  - `--selftest` perturbs only the AnimateableText side (+0.25pt font size, 1-step colour
    nudge); every case must be detected — guards against false passes.
  - Blank crops fail, so "both empty" can't pass.
- **Bug found and fixed:** AnimateableText didn't default `ellipsizeMode` to `'tail'` like
  RN's `<Text>` (native default is `Clip`). On iOS, `numberOfLines` truncation hard-clipped
  instead of showing `…`, and `adjustsFontSizeToFit` did nothing. Fixed in
  `src/AnimateableText.tsx`.
- Baseline, after the fix:

  | | Default font size | Largest accessibility size | Self-test |
  |---|---|---|---|
  | iOS | 21/21 identical | 21/21 (4 compared on visible part) | 21/21 detected |
  | Android | 21/21 identical | 21/21 | 21/21 detected |

- **iOS segment pixels/rects — decided:** RN draws with
  `NSLayoutManager drawGlyphsForGlyphRange:atPoint:`. We build the same text storage via
  RN's `_nsAttributedStringFromAttributedString:` +
  `_textStorageAndLayoutManagerWithAttributesString:paragraphAttributes:size:` and draw
  **each segment's glyph range** into its own layer. No cropping (no neighbour-glyph bleed),
  shaping comes from the full line (Arabic stays joined), rects from
  `boundingRectForGlyphRange:`.
- **Android:** `JBAnimatedTextViewManager` always creates `ReactTextView`, which draws its own
  `TextView` layout (`getLayout()`). Segments come from that `Layout`. RN's
  `enablePreparedTextLayout` is off by default; testing with it on is still open.

## Phase 1 + 2 — results (prototype)

`contentTransition` (`numeric`, `blur`, `fade`, plus `duration` and `direction`) works on
iOS and Android, from both a plain `text` prop and Reanimated `animatedProps`.

**Plumbing.** `contentTransition` is normalised in JS (`src/AnimateableText.tsx`) into three
flat native props: `contentTransitionType`, `contentTransitionDuration` (ms),
`contentTransitionDirection`. They flow through `CParagraphProps` (C++, iOS reads them in
`updateProps`) and `@ReactProp` setters (Android). Types are exported from the package root.

**Plan (segmentation + diff + direction).** `ios/JBTextTransitionPlan.mm` and
`android/.../TextTransitionPlan.kt`, both checked against `fixtures/text-transitions.tsv`
(19 cases: numbers with labels, Arabic-Indic digits, signs, Latin/Arabic words, diacritics,
emoji, ZWJ, flags) by `scripts/test-transition-plan.sh [--android]`. Numeric mode keeps a
shared non-digit prefix/suffix (`SAR `, `%`, ` ر.س`) in place and right-aligns the rest.
Android adds explicit emoji-grapheme rules because the JVM `BreakIterator` used by unit tests
splits skin tones.

**Timing.** A critically damped spring matching SwiftUI's `.smooth(duration:)`:
`p(t) = 1 − (1 + ωt)e^{−ωt}`, `ω = 2π / duration` (default 0.5 s, settles at ~1.47 × duration).
Removed: moves `−direction × 0.5 × lineHeight`, scales to 0.7, fades out by p = 0.7, blurs to
max. Inserted: the reverse; fades in by p = 0.6, sharp by p = 0.8. Kept segments that moved
slide from old to new position. Max blur `clamp(0.15 × lineHeight, 2pt, 10pt)`. `blur` mode:
no motion or scale; `fade`: no blur. Same numbers on both platforms (`JBTextTransitionAnimator`
/ `TextTransitionTiming`).

**iOS.** `JBAnimatedTextComponentView` keeps the last committed state/layout/props; when a
mount changes the text it builds two `NSTextStorage`s with RN's own internal
`RCTTextLayoutManager` methods (guarded with `respondsToSelector:`), hides RN's text view, and
adds one layer per moving segment, drawn with `drawGlyphsForGlyphRange:` (exact RN glyphs; no
cropping). Blur: 4 levels per segment, computed with vImage on a half-size copy and cached per
glyph/attributes (`NSCache`); the layer cross-fades between levels. Preparing a 20-segment
change costs 2–5 ms with a warm cache, 10–13 ms cold (debug build, simulator); was 500 ms with
Core Image.

**Android.** `JBAnimatedTextView : ReactTextView`. Before RN applies new text
(`updateExtraData`) it records the old text into a `RenderNode` and stores every old segment's
rect: `TextView` reuses its `BoringLayout` for single-line text, so the old `Layout` object is
mutated by the update. Segments are `RenderNode`s (old: the recording clipped to the segment;
new: the new layout clipped to the segment), animated by a `ValueAnimator` with the shared
spring; blur via `RenderEffect` (API 31+). The static text is the new layout drawn with the
animated segments clipped out.

**Fallbacks.** Whole-text cross-fade when `numberOfLines` is set, `adjustsFontSizeToFit` is on,
or `numeric` spans more than one line. No transition with Reduce Motion (iOS) / animator scale
0 (Android), before API 29, on a software canvas, or when the view isn't in a window. A change
mid-transition snaps the current one to its end and starts from there.

**Verification.**
- Fixtures: 19/19 on iOS and Android.
- Parity: 24/24 identical on both platforms, including three cases measured after a
  transition settles (`transition-numeric`, `transition-blur-ar`, `transition-fade-multiline`);
  self-test 24/24 detected.
- Visual (video, ffmpeg frame sheets): balance +1 / Random on both platforms, Arabic-Indic
  digits, words in `blur` (Latin per character, Arabic per word, joined), centred counter
  widening (kept digits stay put on screen, then slide).

**Known limitations / to tune with you.**
- Feel: the spring has a long tail (inserted digits sit slightly low for a while); offset 0.5,
  min scale 0.7 and blur 0.15 are first guesses, not matched against a SwiftUI recording yet.
- The text view's size changes instantly (RN layout), so siblings and containers jump unless
  they animate their own layout. With Reanimated `LinearTransition` on the surrounding views
  (toggle in the example's boxed-number demo) the row resizes smoothly around the transition.
- Colour changes that arrive without a text change during a transition are not reflected by
  the in-flight segments until it ends (seen only when colour animates independently).
- Android clips each segment horizontally at its rect, so glyph ink that overhangs a
  neighbour (italics) can be cut during the animation (not at rest).
- Android < 12 has no blur; Android < 10 and iOS without the RN internals have no transition.
- Not yet tested: Reduce Motion paths, recycling in lists, RN's `enablePreparedTextLayout`.

## Retargeting (interruption) — done

Changes that arrive mid-transition (slider scrubbing, fast taps) used to snap the running
transition to its end and replay from the previous value, so the visible text lagged one step
and the last value replayed after release. Now every on-screen segment has its own timeline:

- Two motions, both starting from the segment's current look: **settle** (to rest: no offset,
  full size, opaque, sharp) and **leave** (drift `−direction × 0.5 × lineHeight`, shrink to 0.7,
  fade out by p = 0.7, blur in). Inserted = settle from below/blurred/invisible; kept = settle
  from the old position. From rest these are identical to the phase 2 curves.
- On a new change: current segments that change start leaving from where they are; kept ones
  re-aim at their new position (always redrawn from the new text, so colour updates
  immediately); new ones arrive; segments already leaving hurry out in 0.15 s with a quarter of
  their drift, so scrubbing never smears.
- Every segment of the current text is its own layer/RenderNode during a transition; when all
  have settled or left, RN's own text drawing takes over again.
- iOS: `JBTextTransitionAnimator` is now a stateful controller owned by the view (one
  `CAKeyframeAnimation` set per segment, rebuilt when it retargets). Android: `Transition`
  keeps `current`/`leaving` segments and draws them each frame from their timelines.

Verified on both platforms with a 2 s slider drag (continuous roll, no replay on release),
Flip sign on the percentage (kept digits change colour at once), parity 24/24, self-test 24/24.

### Running parity

```bash
cd example
npx expo start --dev-client --port 8095
node scripts/parity.mjs --platform ios --device <simulator udid>
node scripts/parity.mjs --platform android --device <adb serial>
node scripts/parity.mjs --platform ios --device <simulator udid> --selftest
```

`--pageSize 1` for the largest accessibility sizes. Screenshots and diffs land in
`example/parity-output/` (git-ignored).

## Housekeeping (separate small PRs)

- Exclude `android/build` from the npm package (`files` → `!android/build`), ~768 KB.
- Podspec `s.source` points to `JonnyBurger/…`; should be `axelra-ag/…`.
- Remove the no-op `Animated.addWhitelistedNativeProps` on Reanimated 4 (keep for 3).
- `TouchableText` stale closure: `attachTouchHandlers` binds `props.onPress` once.
- README: tighten `peerDependencies` to match the compatibility table.

## Risks

- **RN coupling:** every RN minor bump may require updating the copied C++ and the iOS/Android
  subclasses. Transitions add more surface (`RCTParagraphComponentView`, `ReactTextView`).
- **iOS internal method:** rect option 1 relies on an RN-internal selector that can be renamed.
- **Android text paths:** RN is moving to `PreparedLayout`; both paths must keep working until
  the old one is removed.
- **Blur quality on iOS:** stepped pre-rendered blur must look continuous — tune step count.

## Open questions

1. Engine per platform + shared fixtures (recommended) vs shared C++ + JNI?
2. Default spring: match SwiftUI's default exactly, or a Malaa design spec?
3. Is a streaming screen planned in Malaa? Decides how early multi-line work lands.
