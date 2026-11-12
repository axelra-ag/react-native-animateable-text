import type { Ref } from "react";
import type {
  TextProps as NativeTextProps,
  Text as IText,
  GestureResponderEvent,
} from "react-native";

export type ContentTransitionType =
  | "none"
  | "numeric"
  | "roll"
  | "blur"
  | "fade";

export type ContentTransitionDirection = "auto" | "up" | "down";

/**
 * What animates as one piece.
 * - `auto`: only what changed; characters, with Arabic grouped into words.
 * - `word`: whitespace-separated words, in every script.
 * - `whole`: the entire text, even parts that didn't change.
 */
export type ContentTransitionUnit = "auto" | "word" | "whole";

/**
 * `roll` only: which characters roll when the text changes.
 * - `all`: every character, unchanged ones too.
 * - `numbers`: the number rolls as a whole (unchanged digits too); a label that stays the
 *   same before or after it (`SAR `, `%`, ` ر.س`) stays put.
 * - `changed`: only the characters that changed.
 */
export type ContentTransitionScope = "all" | "numbers" | "changed";

/**
 * Animates the text natively whenever `text` changes.
 * - `numeric`: changed characters blur and slide vertically (SwiftUI `numericText`).
 * - `roll`: characters rise a short way and fade, one after another in reading order.
 *   `scope` picks which ones: all of them (default), the number only, or only what changed. Made for values that change quickly (chart scrubbing, sliders). Defaults:
 *   `direction: 'up'`, `duration: 200`, `stagger: 30`.
 * - `blur`: changed segments blur out and in, in place.
 * - `fade`: changed segments cross-fade.
 */
export type ContentTransition =
  | ContentTransitionType
  | {
      type: ContentTransitionType;
      /**
       * Milliseconds. Defaults to a spring tuned to SwiftUI's default (500), or 200 for
       * `roll`.
       */
      duration?: number;
      /**
       * `numeric` and `roll`. `auto` compares the old and new numbers. Defaults to `auto`,
       * or `up` for `roll`.
       */
      direction?: ContentTransitionDirection;
      /**
       * Milliseconds between the starts of neighbouring changed positions, in reading
       * order from the first one that changed. The total spread is capped at 300 ms.
       * Defaults to 0 (all together), or 30 for `roll`.
       */
      stagger?: number;
      /** `roll` only. Defaults to `all`. */
      scope?: ContentTransitionScope;
      /**
       * `roll` only. Milliseconds between two rolls. Changes that arrive sooner are held
       * back; when the time is up the text rolls once to the latest value, so the last
       * change is always shown. By default a roll waits until the previous one has landed
       * (its stagger spread plus ~0.65 × duration). `0` rolls on every change.
       */
      minInterval?: number;
      /**
       * Milliseconds between a change and the start of its transition (every position's
       * stagger schedule, leaving and arriving, shifts by it). The old text stays on screen
       * meanwhile. A change that arrives during the delay joins the pending transition (it
       * re-aims at the newest text and keeps the original start). With `transitionKey`, changes
       * that don't animate ignore it. Defaults to 0.
       *
       * Two texts side by side read as one wave when the second one's `delay` is the first
       * one's length × `stagger`.
       */
      delay?: number;
      /** Defaults to `auto`. */
      unit?: ContentTransitionUnit;
    };

export type RevealType = "none" | "roll" | "numeric" | "blur" | "fade";

/**
 * Animates the text in the first time the view shows non-empty text: on mount with text, or
 * on the first change from empty text after mounting empty. Runs once per mounted view; later
 * changes use `contentTransition`. Every segment arrives with the type's arriving motion
 * (`roll`: rises from below and fades in), one after another in reading order.
 */
export type Reveal =
  | RevealType
  | {
      type: Exclude<RevealType, "none">;
      /** Milliseconds. Same defaults as `contentTransition` (500, or 200 for `roll`). */
      duration?: number;
      /** Where segments come from: `up` rises from below (default), `down` drops from above. */
      direction?: "up" | "down";
      /** Defaults to `auto`. */
      unit?: ContentTransitionUnit;
      /**
       * Milliseconds between neighbouring segments, from the first one. The total spread is
       * capped at 300 ms. Defaults to 0, or 30 for `roll`.
       */
      stagger?: number;
      /** Milliseconds before the reveal starts. Defaults to 0. */
      delay?: number;
    };

/** One value of a `transitionKey`. */
export type TransitionKeyValue = string | number | boolean | null | undefined;

/**
 * Like SwiftUI's `.animation(_:value:)`: when set, a text change runs `contentTransition`
 * only if the key changed with it; other text changes swap instantly. An array compares like
 * a React dependency array (any element changed, or the length). Set it in the same update as
 * the text: as a prop next to `text`, or returned with `text` from `useAnimatedProps`.
 */
export type TransitionKey = TransitionKeyValue | TransitionKeyValue[];

export type AnimateableTextProps = Omit<NativeTextProps, "children"> & {
  forwardedRef?: Ref<IText>;
  text?: string;
  contentTransition?: ContentTransition;
  /** Defaults to `none`. */
  reveal?: Reveal;
  /**
   * Without it (`undefined`), every text change runs `contentTransition`. With it, only text
   * changes that come with a new key do. A key change without a text change arms the next text
   * change. Values: string, number, boolean, or an array of those.
   */
  transitionKey?: TransitionKey;

  // Include optional responder props
  onStartShouldSetResponder?: () => boolean;
  onResponderGrant?: (event: GestureResponderEvent) => void;
  onResponderMove?: (event: GestureResponderEvent) => void;
  onResponderRelease?: (event: GestureResponderEvent) => void;
  onResponderTerminate?: (event: GestureResponderEvent) => void;
  onResponderTerminationRequest?: () => boolean;

  // Highlighting behavior
  suppressHighlighting?: boolean;

  // Touchable-specific offset
  pressRetentionOffset?: {
    top: number;
    left: number;
    bottom: number;
    right: number;
  };
};
