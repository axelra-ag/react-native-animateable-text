import React from 'react';
import { forwardRef, useCallback, useMemo, useRef, useState } from "react";
import { processColor } from "react-native";
// @ts-ignore
import ReactNativeViewAttributes from "react-native/Libraries/Components/View/ReactNativeViewAttributes";
import Touchable from "react-native/Libraries/Components/Touchable/Touchable";
// @ts-ignore
import createReactNativeComponentClass from "react-native/Libraries/Renderer/shims/createReactNativeComponentClass";
import nullthrows from "nullthrows";
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
const isTouchable = (props) => props.onPress != null ||
    props.onLongPress != null ||
    props.onStartShouldSetResponder != null;
const RCTText = createReactNativeComponentClass(viewConfig.uiViewClassName, () => viewConfig);
const TouchableText = forwardRef((props, forwardedRef) => {
    const [isHighlighted, setHighlighted] = useState(false);
    const touchableRef = useRef({});
    const hasAttachedTouchHandlers = useRef(false);
    const TouchableWithMixin = Touchable;
    const attachTouchHandlers = useCallback(() => {
        if (hasAttachedTouchHandlers.current)
            return;
        hasAttachedTouchHandlers.current = true;
        for (const key in TouchableWithMixin.Mixin) {
            if (typeof TouchableWithMixin.Mixin[key] === "function") {
                touchableRef.current[key] = TouchableWithMixin.Mixin[key].bind(touchableRef.current);
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
        touchableRef.current.touchableHandlePress = (event) => {
            props.onPress?.(event);
        };
        touchableRef.current.touchableHandleLongPress = (event) => {
            props.onLongPress?.(event);
        };
        touchableRef.current.touchableGetPressRectOffset = () => props.pressRetentionOffset ?? PRESS_RECT_OFFSET;
    }, [hasAttachedTouchHandlers, props, setHighlighted, touchableRef]);
    const responseHandlers = useMemo(() => {
        if (!isTouchable(props))
            return undefined;
        return {
            onStartShouldSetResponder: () => {
                const shouldSet = (props.onStartShouldSetResponder?.() ?? false) ||
                    isTouchable(props);
                if (shouldSet) {
                    attachTouchHandlers();
                }
                return shouldSet;
            },
            onResponderGrant: (event) => {
                nullthrows(touchableRef.current.touchableHandleResponderGrant)?.(event);
                props.onResponderGrant?.(event);
            },
            onResponderMove: (event) => {
                nullthrows(touchableRef.current.touchableHandleResponderMove)?.(event);
                props.onResponderMove?.(event);
            },
            onResponderRelease: (event) => {
                nullthrows(touchableRef.current.touchableHandleResponderRelease)?.(event);
                props.onResponderRelease?.(event);
            },
            onResponderTerminate: (event) => {
                nullthrows(touchableRef.current.touchableHandleResponderTerminate)?.(event);
                props.onResponderTerminate?.(event);
            },
            onResponderTerminationRequest: () => {
                const defaultRequest = nullthrows(touchableRef.current.touchableHandleResponderTerminationRequest)();
                return props.onResponderTerminationRequest?.() ?? defaultRequest;
            },
        };
    }, [props, attachTouchHandlers]);
    let selectionColor;
    if (props.selectionColor != null) {
        selectionColor = processColor(props.selectionColor);
    }
    const renderedProps = {
        ...props,
        ...(responseHandlers ?? {}),
        ref: forwardedRef,
        isHighlighted,
        selectionColor,
    };
    if (isTouchable(props)) {
        renderedProps.isHighlighted = isHighlighted;
    }
    if (__DEV__ &&
        TouchableWithMixin.TOUCH_TARGET_DEBUG &&
        props.onPress != null) {
        renderedProps.style = [props.style, { color: "magenta" }];
    }
    return React.createElement(RCTText, { ...renderedProps });
});
TouchableText.displayName = "TouchableText";
export const AnimateableText = TouchableText;
