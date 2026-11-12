# Text transitions — plan

Status: phases 0–2 and 4 done (prototype; `reveal="stream"` not implemented) · Branch: `feat/text-transitions` · Base: `0.19.3` (RN 0.86.3, Fabric only)

## Goal

Let `AnimateableText` animate its text natively, opted into with one prop:

```tsx
<AnimateableText text={balance} contentTransition="numeric" />
<AnimateableText text={title} reveal="blur" />
<AnimateableText text={reply} reveal="stream" /> // planned, not implemented
```

Three effects, one engine:

| Effect | Trigger | Leaving | Arriving | Timing |
|---|---|---|---|---|
| Content transition (SwiftUI `numericText`) | `text` changes | changed segments of old text | changed segments of new text | together, spring |
| Reveal | first non-empty text | nothing | every segment | staggered |
| Stream (AI chat) — not implemented | `text` grows | nothing | appended segments | steady rate, catches up |

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
  | 'none' | 'numeric' | 'roll' | 'blur' | 'fade'
  | {
      type: 'none' | 'numeric' | 'roll' | 'blur' | 'fade';
      duration?: number;            // ms; default 500, roll 200
      direction?: 'auto' | 'up' | 'down'; // default auto, roll up
      unit?: 'auto' | 'word' | 'whole';
      stagger?: number;             // ms; default 0, roll 30; spread capped at 300 ms
      scope?: 'all' | 'numbers' | 'changed'; // roll only; default all
      minInterval?: number;         // ms, roll only; default automatic, 0 off
      delay?: number;               // ms; default 0 (see "contentTransition delay")
    };

// Implemented (see "reveal and transitionKey — done" below).
type Reveal =
  | 'none' | 'roll' | 'numeric' | 'blur' | 'fade'
  | {
      type: 'roll' | 'numeric' | 'blur' | 'fade';
      duration?: number;            // ms; per-type defaults as contentTransition (roll 200)
      direction?: 'up' | 'down';    // default 'up'
      unit?: 'auto' | 'word' | 'whole';
      stagger?: number;             // ms; default 0, roll 30; spread capped at 300 ms
      delay?: number;               // ms; default 0
    };

// Planned, not implemented: 'stream' (and a { type: 'stream'; charsPerSecond?: number } form).
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
- **`transitionKey`:** when set, only text changes that come with a new key run
  `contentTransition` (see below).

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
| 4 | `reveal` (`roll`, `numeric`, `blur`, `fade`), stagger, delay. **Done** (see below). | Parity green; reveal fixtures green |
| 5 | `reveal="stream"`: rate-limited reveal cursor, line reflow, height follows RN layout (grows per line). **Not implemented.** | Smooth reveal under bursty input |

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

## `unit` — done

`contentTransition={{ type, unit }}` with `unit: 'auto' | 'word' | 'whole'` (default `auto`):

- `auto`: graphemes, Arabic grouped into words (unchanged behaviour).
- `word`: whitespace-separated words animate as one piece, every script; whitespace is its own
  piece. Numeric mode still keeps a shared non-digit prefix/suffix (`SAR `) in place.
- `whole`: the entire old text leaves and the entire new text arrives. Direction is still
  resolved from the numbers, so `numeric` + `whole` rolls the whole value up/down. The
  "numeric over several lines → fade" fallback doesn't apply to `whole`.

Native prop `contentTransitionUnit`; segmentation in both plan files takes the unit; Android
captures old rects for the unit's segments plus the whole text. Fixtures: the mode column
accepts `mode:unit` (28 rows). Parity case `transition-blur-whole`. Example: `unit` switch in
the settings card.

## Scrubbing performance fix — done

Found in Malaa (investment calculator chart scrub, 4 animated texts): with `blur` the UI
dropped from 60 to ~5 FPS over ~10 s and RAM grew ~270 MB. Cause: leaving segments restarted
their 0.15 s "hurry" on every change, so with a change every frame they never finished; the
pile, and the per-change work of re-animating it, grew without bound.

Fixes (both platforms):
- A segment hurries out once; ended or invisible (opacity ≤ 0.01) segments are removed
  immediately; leaving segments are capped (1 per current segment on Android, 2 on iOS,
  minimum 8).
- Kept segments with the same glyphs and colour reuse their layer/node instead of being
  redrawn; segments already at rest get no animation; only segments whose timeline changed
  are re-animated (iOS).
- iOS: sharp glyph images cached (keyed by glyphs, attributes, canvas and sub-pixel offset),
  both image caches bounded by bytes (6 MB each) instead of entry count.
- Android: no snapshot of the old text while a transition runs (the segments are the old
  text); no per-frame list allocations.
- Android blur: every `RenderEffect` is an offscreen pass, so per-segment blur meant ~100
  passes per frame while scrubbing 4 texts. Blur is now quantised to 4 levels (like iOS) and
  segments are grouped per level into one `RenderNode` per level that carries the effect: at
  most 3 blur passes per text per frame. Benchmarked (3 rounds each): per-segment blur with or
  without compositing layers, quantised or not, all ~38–42 ms median frame; grouped ~31–34 ms;
  `none` ~24–29 ms.
- Mistakes made and reverted on the way: dropping blur from hurrying segments (blur snapped
  to sharp each frame while sliding) and a cap of 1 leaving segment per character (visible
  segments popped). Hurrying segments keep their normal blur; the cap is 2 on both platforms
  and evicts the most faded first.

Stress card in the example (4 texts changing every frame, 20–30 s):

| | Memory | CPU / frames |
|---|---|---|
| iOS before (blur) | +160 MB in 20 s, still rising | ~92% CPU |
| iOS after (blur) | flat | ~48% CPU |
| iOS `none` | flat | ~50% CPU |
| Android grouped blur | flat | p50 31–34 ms, ~1.3% janky |
| Android `none` | flat | p50 24–29 ms, ~0.2% janky |

(Simulator/emulator, debug builds; the emulator is noisy, so compare medians of several runs.)

## `roll`, `stagger`, `scope`, `minInterval` — done

`roll` is a fourth content transition, made for values that change quickly (chart scrubbing,
slider dragging), measured from a banking-app reference video. `numeric` is unchanged.

```ts
contentTransition="roll"
// ≡ { type: 'roll', direction: 'up', duration: 200, stagger: 30, unit: 'auto', scope: 'all' }
```

- **Look** (motion table, both timing files): rise 0.25 × line height (≈ 0.3 em), no shrink,
  blur 0.2 × numeric's. Leaving glyphs go up and fade, arriving ones rise from below. Same
  spring; at 200 ms a segment is ~90% settled after 125 ms.
- **Plan**: numeric's segmentation and slots (shared non-digit prefix/suffix, the rest aligned
  from the trailing end), caps and fallbacks. `scope` (roll only) picks what is replaced on a
  change: `all` (default) every slot, unchanged ones too; `numbers` every slot but the shared
  prefix/suffix (`SAR ` stays put); `changed` only what changed (numeric's diff).
- **`stagger`** (ms, every type; default 0, 30 for roll): each changed position starts
  `index × stagger` after the first changed one, in logical order (unchanged positions in
  between count). A removed segment and its replacement share an index. The spread is capped
  at 300 ms by shortening the step. The index is computed in both plan files (`staggerIndex`)
  and checked by the fixtures' last column (43 rows).
- **Retargeting with stagger**: a staggered leave is a pending leave (the segment carries on
  until its turn; it no longer follows its slot when the width changes, see "Leaving segments
  never move sideways" below). On a new change, segments that
  have started leaving hurry out; ones still waiting keep their schedule; kept ones re-aim at
  once. An arriving segment replaced before it showed (still waiting, or fainter than
  `takeoverOpacity`, roll only: 0.9) hands its timeline to its replacement.
- **Throttle** (roll only): a change that arrives before the previous roll has landed is held
  back; one roll to the latest text runs when the wait is up, and the last change is always
  shown. Wait: `minInterval` ms (`0` = off), by default the roll's stagger spread +
  0.65 × duration (≈ 130 ms for one digit, 350–450 ms for a whole amount). Tried first without
  it and with a fixed 130 ms: every digit was permanently mid-roll and pale while scrubbing.
- Native props added: `contentTransitionStagger`, `contentTransitionScope`,
  `contentTransitionMinInterval`.
- Example: `roll` + stagger / scope / min interval rows in the settings card, a chart-scrub
  demo (drag or "Auto scrub"), "Auto drag" on the slider demo, deep-link query for settings,
  `only=` and `autorun=1`. Parity cases `transition-roll` (width change) and
  `transition-roll-mask`.

Verified: fixtures 43/43 on iOS and Android; parity 27/27 identical and self-test 27/27
detected on both; frame sheets of auto scrub on iOS (clean separate waves, label stays with
`numbers`, width change slides). Not measured yet: frame pacing and memory against `none`
(stress card, both platforms), and Android frame sheets with the final throttle.

## `reveal` and `transitionKey` — done

### `reveal`

```tsx
<AnimateableText text={amount} reveal="roll" contentTransition={{ type: 'roll', stagger: 40, duration: 300 }} />
<AnimateableText text={title} reveal={{ type: 'blur', unit: 'word', stagger: 40, delay: 300 }} />
```

- Native props: `revealType`, `revealDuration` (ms), `revealDirection` (`up`/`down`),
  `revealUnit`, `revealStagger` (ms), `revealDelay` (ms). JS defaults: as `contentTransition`'s
  per type (`roll`: 200 ms, stagger 30), direction `up`, delay 0. Web: ignored.
- **When:** once per mounted view, the first time it shows non-empty text: on mount with text,
  or on the first `'' → text` change after mounting empty. That first show uses `reveal`, not
  `contentTransition`, whatever `transitionKey` says. Later changes use `contentTransition`.
  With `reveal` `'none'` (default) nothing changes: `'' → text` after mount is an ordinary
  change (runs `contentTransition`, subject to `transitionKey`), and the mount itself is instant.
- **Look:** every segment arrives from nothing with the type's arriving motion (roll: rises
  0.25 × line height from below, fades in, light blur), staggered in logical order from the
  first segment, after `delay`. The plan is `revealPlanForText:` / `TextTransitionPlan.reveal`:
  the plan from empty text (every segment inserted, numeric/LCS/whole agree), checked by the
  `reveal:<type>[:unit]` fixture rows (old text `∅`). `numberOfLines` / `adjustsFontSizeToFit`
  fall back to a whole-text fade-in; multi-line text reveals per segment.
- **Where it starts:** iOS mounts (`finalizeUpdates`) before the view is in a window, so the
  reveal is pending (RN's text view hidden) until `didMoveToWindow`. Android starts it on the
  first draw. Either way it plays when the text first appears on screen.
- **Changes during a reveal** retarget like any change (waiting segments keep their schedule and
  hand it to their replacements; nothing snaps). With `contentTransition` `'none'`, a change
  during a reveal carries on with the reveal's look (no delay). The reveal never holds changes
  back (no roll throttle).
- **Delay:** a change during the delay doesn't cut it short: no segment arrives before the
  reveal's delay is over (`_arrivalsNotBefore` / `arrivalsNotBeforeMs` in the animators). Found
  in a recording: an update at 100 ms with a 300 ms delay first started the reveal at ~150 ms.
- Reduce Motion (iOS) / animator scale 0 (Android): shown instantly. Accessibility gets the text
  immediately (only RN's text subview is hidden; the paragraph view's label is the text).
  Recycling (`prepareForRecycle` / `prepareToRecycleView`) resets "already revealed" and cancels
  a running or pending reveal.

### `transitionKey`

```tsx
<AnimateableText text={isHidden ? '*****' : amount} transitionKey={isHidden} contentTransition="roll" />
const animatedProps = useAnimatedProps(() => ({ text: …, transitionKey: hidden.value }));
<AnimateableText text={label} transitionKey={[isHidden, currency]} contentTransition="roll" />
```

Like SwiftUI's `.animation(_:value:)`. Top-level prop (not inside `contentTransition`):
`string | number | boolean | null | undefined`, or an array of those (compared like a React
dependency array).

- **Rule:** without a key (`undefined`, or `null` at the top level), every text change runs
  `contentTransition` (unchanged behaviour). With a key, a text change animates only if the key
  differs from the key at the previous text change. A key change without a text change therefore
  arms the next text change: key-then-text in separate commits animates. Text-then-key in
  separate commits can't animate (the text change sees the old key; the key change arms the
  next text change instead). Set both in the same update: as props in the same render, or
  returned together from the same `useAnimatedProps`. Native compares the key at change time,
  so it doesn't matter which path the text took.
- **From rest** a change that doesn't animate swaps instantly: no layers/nodes, no roll throttle
  hold. **While a transition is still running**, such a change retargets it as any change does
  (never snaps); it doesn't stop the running animation.
- **Encoding:** the raw prop value is normalised natively (C++ `TransitionKey.h` from the
  `RawValue`/`folly::dynamic`; Android `TransitionKey.kt` from `Dynamic`), so it works from
  `useAnimatedProps` on the UI thread too. JS passes the value through unchanged. Canonical
  string, same on both platforms (`fixtures/transition-keys.tsv`, 28 rows): `null` (also
  `undefined` inside an array), `true`/`false`, integral numbers as decimal integers (`1` and
  `1.0` equal, `-0` is `0`), other numbers as `0x` + the IEEE-754 bits, `"strings"` with `\` and
  `"` escaped, `[arrays]` (nested arrays work), objects (not supported) all `{}`. So `1`, `"1"`
  and `true` are different keys. On Android, if RN sends diffed props from C++
  (`getDiffProps`), Java sees C++'s canonical string; it re-encodes it as a string, which is
  still equal for equal keys.
- **Where:** the decision is one line in each view (iOS `textMayHaveChanged`, Android
  `prepareForTextChange` / `onTextApplied`), not in the plan files; covered by the example's
  "transitionKey: hide / show" card and recordings, not by plan fixtures.
- Native prop: `transitionKey` (also in `validAttributes` and Reanimated's
  `addWhitelistedNativeProps`).

### Verification

- Fixtures: plan 53/53 (10 new `reveal:` rows) and keys 28/28 on iOS (macOS build) and Android
  (JVM).
- Parity: 27/27 identical, self-test 27/27 detected, iOS (iPhone 18 Pro sim, iOS 27.2) and
  Android (Pixel 10 Pro XL emulator).
- Frame sheets (both platforms, `only=key&autorun=1`): hide → `*****` rolls on all three nodes,
  the decimals node rolls out to `''` and back in, show rolls back, value changes while visible
  swap instantly (both paths), key-then-text (150 ms apart) rolls, the array key rolls only on
  currency changes. Reveal (`only=reveal&autorun=1`): on mount, empty → load, update at 100 ms
  (retargets, no snap), delay 300 ms (first ink ~316 ms after mount; 1.03 s for empty → load).
- Stress card, 30 s: iOS RSS flat (blur 500 → 458 MB, ~50% CPU; none 469 → 456 MB; roll
  483 → 454 MB). Android PSS flat (310–324 MB), 3 alternating runs: frame p50 blur 53/44/42 ms,
  none 31/31/30 ms (slower than the earlier 31–34 / 24–29 ms on both, emulator noise likely;
  nothing per frame changed).
- Not run: an iOS 18 simulator (no runtime installed), a physical device.

### Example

- `transitionKey: hide / show` (`only=key`): Malaa's config `{ type: 'roll', stagger: 40,
  duration: 300 }`; (1) `text` + `transitionKey` props, (2) integer + decimals from
  `useAnimatedProps` returning `{ text, transitionKey }` (decimals `'' ` while hidden), (3)
  `transitionKey={[isHidden, currency]}`. Hide/Show, Change value (instant), Auto change,
  "key, then text" (key first, text 150 ms later), currency switch. `autorun=1` steps through
  hide, show, change ×2, key-then-text hide/show, currency, change, currency (1.3 s apart).
- `reveal` (`only=reveal`, `reveal=<type>`, `revealDelay=<ms>`): Remount, Mount empty then load
  (700 ms), Remount + update at 100 ms; type and delay switches. `autorun=1` cycles the three
  mounts every 2.2 s.

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

## `contentTransition` delay and "leaving segments never move sideways" — done

### `delay`

```tsx
// Malaa's split amount: decimals start where the integer's wave ends.
<AnimateableText animatedProps={integerProps} contentTransition={MONEY} reveal={MONEY} />
<AnimateableText
  animatedProps={decimalProps}
  contentTransition={{ ...MONEY, delay: followingDelay(integerPositions) }}
  reveal={{ ...MONEY, delay: followingDelay(integerPositions) }}
/>
// followingDelay(n) = n × min(40, 300 / (n − 1)): the integer's n positions with its capped step.
```

- `contentTransition.delay` (ms, every type, default 0), same meaning as `reveal.delay`: the
  whole schedule (leaving, kept and arriving positions, with their stagger) starts `delay` after
  the change. The old text stays on screen meanwhile: from rest the segments are set up at once
  (old text visible, new text invisible and waiting), so RN's text view is hidden during the
  delay. Native prop `contentTransitionDelay`; web ignores it.
- **A change during the delay joins the pending transition**: it re-aims at the newest text and
  keeps the original start (same rule as a change during the reveal's delay). Implemented in
  the animators: every position's schedule counts from a base time, which is `now + delay`, or
  the running transition's start if that is still in the future (`_transitionStart` /
  `transitionStartMs`, replacing the reveal-only `_arrivalsNotBefore`). A change that arrives
  after the start retargets as before and gets its own delay (each animating change is
  delayed, so two nodes keep their wave on a retarget too).
- Kept segments: ones at rest start their slide at the base time (they sit still during the
  delay); ones already moving re-aim at once; ones still waiting for their start keep it. A
  kept segment waiting for a delayed start is visible, so "waiting" (an invisible arrival, the
  only kind that is dropped or handed over on a retarget) now also requires an invisible start
  look.
- **`transitionKey`**: a change that doesn't animate ignores the delay. From rest it is instant
  (unchanged). If the running transition is still in its delay it joins it (keeps the start, so
  nothing snaps and the newest text is what lands); otherwise it retargets at once.
- **Roll throttle**: a change while the transition is still in its delay is never held (nothing
  has moved yet; it re-aims instead). The automatic wait (stagger spread + 0.65 × duration) and
  an explicit `minInterval` count from the transition's start, i.e. after its delay.
- Reduce Motion / animator scale 0: instant, as before. The first show is `reveal`'s.
- Text going back to the on-screen value during the delay is not special-cased: at the start,
  the old text leaves and the same text arrives (consistent with the neighbour node's wave).

### Leaving segments never move sideways

Found in Malaa: with `roll` (scope `all`), `1,234.55 → *****` piled glyphs up (`34.55,2`): old
digits still waiting for their stagger turn slid left into the narrower new slots before
leaving (the "waiting leave follows its slot" rule). Unhide had a milder version (stars spread
out while the digits arrived). Now, matching the banking-app reference:

- A leaving segment, including one still waiting for its turn, keeps its x from the moment it
  is told to leave (`hasFixedX` / `fixedTranslateX`) and only rises/fades there. The view-offset
  shift (the view moved under the text) still applies, so it stays put on screen.
- Arriving segments arrive straight at their final x. Kept (unchanged, not replaced) segments
  still slide to their new x.
- The slot-following block was removed from both animators.
- First recording after that (chart auto-scrub): getting narrower was clean, but getting wider
  (`597.15 → 1,745.31`) drew new digits at their final x over old digits still waiting for
  their turn (`1,7455` for a few frames). Rule added: a waiting leave starts no later than the
  first arrival that lands on it on screen (horizontal overlap > 30 % of the narrower glyph, same
  line; `overlapsOnScreen`). Each glyph's own leave still starts from its own x; the wave
  becomes left-to-right on screen.

### Two nodes as one wave: `staggerOffset` / `staggerTotal`? — not added

The 300 ms cap only shortens a node's step when its own spread would pass 300 ms (with
`stagger: 40`, from 9 positions on). Below that, `delay = positions × 40` on the second node is
exact. Above it, the consumer can get the same result with today's API: compute one step for
the whole wave, `step = min(40, 300 / (totalPositions − 1))`, pass `stagger: step` to both nodes
and `delay: integerPositions × step` to the second (the first node's own spread then never hits
the cap). `staggerOffset` + `staggerTotal` would only move this arithmetic into native code
(two more props on both platforms) and still need the consumer to count the positions (which
change between visible and masked text), so it isn't worth it now. Cross-view coordination (a
shared wave id) would remove the counting but needs a native registry: not worth it for one
screen.

### Verification

- Fixtures: plan 53/53 and keys 28/28 on iOS (macOS build) and Android (JVM). The plan didn't
  change (delay and the leave rules live in the animators).
- `tsc`: root clean; example 12 errors (the known ones, none new).
- Parity (final build): iOS 27/27 identical (3 of 4 runs; one run was 26/27 and its output was
  overwritten before the case could be identified; machine load average ~150 from an unrelated
  process, `--dwell 7000` needed for the screenshots to keep up), self-test 27/27 detected (no
  blank crops). Android 27/27, self-test 27/27.
- Frame sheets, iOS and Android (`only=key&autorun=1`): hide `1,234.55 → *****` without glyph
  pile-up (old digits rise/fade where they are); show; 2a's decimals start after the
  integer's wave (hide, show, roll value, reveal) while 2b's start with it; row 4 (`delay: 300`)
  with a second change 120 ms later holds the old value, then rolls once straight to the newest
  one. Chart auto-scrub (roll) with width changes in both directions is clean on both platforms.
- Stress card, 30 s: iOS RSS flat (blur 238 → 206 MB, ~44 % CPU; none 214 → 152 MB; roll
  218 → 208 MB). Android PSS flat (343–352 MB); 3 alternating runs, frame p50 blur 46/48/48 ms,
  none 31/32/31 ms.
- Known limitation: a delayed node next to one that gets wider (2a on a rolled value change
  `987.65 → 15,374.30`) keeps its old text at its old screen x during the delay, so the wider
  neighbour's new digits draw over it until it leaves. Malaa isn't affected (value changes
  there don't animate; hide makes the integer narrower; on show the decimals have no old text).

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
