import type { Ref } from "react";
import type {
  TextProps as NativeTextProps,
  Text as IText,
  GestureResponderEvent,
} from "react-native";

export type AnimateableTextProps = Omit<NativeTextProps, "children"> & {
  forwardedRef?: Ref<IText>;
  text?: string;

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
