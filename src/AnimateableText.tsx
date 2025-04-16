import React from 'react'
import type { Ref, ForwardRefExoticComponent, RefAttributes } from "react";
import { forwardRef, useCallback, useMemo, useRef, useState } from "react";
import { type GestureResponderEvent, processColor } from "react-native";
import ReactNativeViewAttributes from "react-native/Libraries/Components/View/ReactNativeViewAttributes";
import Touchable from "react-native/Libraries/Components/Touchable/Touchable";
import createReactNativeComponentClass from "react-native/Libraries/Renderer/shims/createReactNativeComponentClass";
import nullthrows from "nullthrows";
import type { AnimateableTextProps } from "./TextProps";
import type { Text as IText } from "react-native";

const PRESS_RECT_OFFSET = { top: 20, left: 20, right: 20, bottom: 30 };

const viewConfig = {
  validAttributes: {
    ...ReactNativeViewAttributes.UIView,
    isHighlighted: true,
    isPressable: true,
    numberOfLines: true,
    ellipsizeMode: true,
    allowFontScaling: true,
    dynamicTypeRamp: true,
    maxFontSizeMultiplier: true,
    disabled: true,
    selectable: true,
    selectionColor: true,
    adjustsFontSizeToFit: true,
    minimumFontScale: true,
    textBreakStrategy: true,
    onTextLayout: true,
    onInlineViewLayout: true,
    dataDetectorType: true,
    android_hyphenationFrequency: true,
    lineBreakStrategyIOS: true,
    text: true,
  },
  directEventTypes: {
    topTextLayout: { registrationName: "onTextLayout" },
    topInlineViewLayout: { registrationName: "onInlineViewLayout" },
  },
  uiViewClassName: "JBAnimatedText",
};

const isTouchable = (props: AnimateableTextProps): boolean =>
  props.onPress != null ||
  props.onLongPress != null ||
  props.onStartShouldSetResponder != null;

const RCTText = createReactNativeComponentClass(
  viewConfig.uiViewClassName,
  () => viewConfig,
);

const TouchableText = forwardRef<IText, AnimateableTextProps>(
  (props, forwardedRef) => {
    const [isHighlighted, setHighlighted] = useState(false);
    const touchableRef = useRef<Record<string, any>>({});
    const hasAttachedTouchHandlers = useRef(false);
    const TouchableWithMixin = Touchable as typeof Touchable & {
      Mixin: Record<string, () => void>;
      TOUCH_TARGET_DEBUG: boolean;
    };
    const attachTouchHandlers = useCallback(() => {
      if (hasAttachedTouchHandlers.current) return;
      hasAttachedTouchHandlers.current = true;

      for (const key in TouchableWithMixin.Mixin) {
        if (typeof TouchableWithMixin.Mixin[key] === "function") {
          touchableRef.current[key] = TouchableWithMixin.Mixin[key].bind(
            touchableRef.current,
          );
        }
      }

      touchableRef.current.touchableHandleActivePressIn = () => {
        if (!props.suppressHighlighting && isTouchable(props)) {
          setHighlighted(true);
        }
      };

      touchableRef.current.touchableHandleActivePressOut = () => {
        if (!props.suppressHighlighting && isTouchable(props)) {
          setHighlighted(false);
        }
      };

      touchableRef.current.touchableHandlePress = (
        event: GestureResponderEvent,
      ) => {
        props.onPress?.(event);
      };

      touchableRef.current.touchableHandleLongPress = (
        event: GestureResponderEvent,
      ) => {
        props.onLongPress?.(event);
      };

      touchableRef.current.touchableGetPressRectOffset = () =>
        props.pressRetentionOffset ?? PRESS_RECT_OFFSET;
    }, [hasAttachedTouchHandlers, props, setHighlighted, touchableRef]);

    const responseHandlers = useMemo(() => {
      if (!isTouchable(props)) return undefined;

      return {
        onStartShouldSetResponder: (): boolean => {
          const shouldSet =
            (props.onStartShouldSetResponder?.() ?? false) ||
            isTouchable(props);

          if (shouldSet) {
            attachTouchHandlers();
          }
          return shouldSet;
        },
        onResponderGrant: (event: GestureResponderEvent) => {
          nullthrows(touchableRef.current.touchableHandleResponderGrant)?.(
            event,
          );
          props.onResponderGrant?.(event);
        },
        onResponderMove: (event: GestureResponderEvent) => {
          nullthrows(touchableRef.current.touchableHandleResponderMove)?.(
            event,
          );
          props.onResponderMove?.(event);
        },
        onResponderRelease: (event: GestureResponderEvent) => {
          nullthrows(touchableRef.current.touchableHandleResponderRelease)?.(
            event,
          );
          props.onResponderRelease?.(event);
        },
        onResponderTerminate: (event: GestureResponderEvent) => {
          nullthrows(touchableRef.current.touchableHandleResponderTerminate)?.(
            event,
          );
          props.onResponderTerminate?.(event);
        },
        onResponderTerminationRequest: (): boolean => {
          const defaultRequest = nullthrows(
            touchableRef.current.touchableHandleResponderTerminationRequest,
          )();
          return props.onResponderTerminationRequest?.() ?? defaultRequest;
        },
      };
    }, [props, attachTouchHandlers]);

    let selectionColor: (symbol & { __TYPE__: "Color" }) | undefined;
    if (props.selectionColor != null) {
      selectionColor = processColor(props.selectionColor) as
        | (symbol & { __TYPE__: "Color" })
        | undefined;
    }

    const renderedProps: AnimateableTextProps & {
      ref: Ref<IText>;
      isHighlighted: boolean;
      selectionColor: (symbol & { __TYPE__: "Color" }) | undefined;
    } = {
      ...props,
      ...(responseHandlers ?? {}),
      ref: forwardedRef,
      isHighlighted,
      selectionColor,
    };

    if (isTouchable(props)) {
      renderedProps.isHighlighted = isHighlighted;
    }

    if (
      __DEV__ &&
      TouchableWithMixin.TOUCH_TARGET_DEBUG &&
      props.onPress != null
    ) {
      renderedProps.style = [props.style, { color: "magenta" }];
    }

    return <RCTText {...renderedProps} />;
  },
);

TouchableText.displayName = "TouchableText";

export const AnimateableText =
  TouchableText as unknown as ForwardRefExoticComponent<
    AnimateableTextProps & RefAttributes<IText>
  >;
