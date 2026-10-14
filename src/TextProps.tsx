import type { Ref } from "react";
import type {
  TextProps as NativeTextProps,
  Text as IText,
  GestureResponderEvent,
} from "react-native";

export type ContentTransitionType = "none" | "numeric" | "blur" | "fade";

export type ContentTransitionDirection = "auto" | "up" | "down";

/**
 * Animates the text natively whenever `text` changes.
 * - `numeric`: changed characters blur and slide vertically (SwiftUI `numericText`).
 * - `blur`: changed segments blur out and in, in place.
 * - `fade`: changed segments cross-fade.
 */
export type ContentTransition =
  | ContentTransitionType
  | {
      type: ContentTransitionType;
      /** Milliseconds. Defaults to a spring tuned to SwiftUI's default. */
      duration?: number;
      /** `numeric` only. `auto` compares the old and new numbers. */
      direction?: ContentTransitionDirection;
    };

export type AnimateableTextProps = Omit<NativeTextProps, "children"> & {
  forwardedRef?: Ref<IText>;
  text?: string;
  contentTransition?: ContentTransition;

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
