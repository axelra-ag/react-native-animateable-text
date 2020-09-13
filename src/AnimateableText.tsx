/**
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
 * @flow
 * @format
 */

'use strict';
const DeprecatedTextPropTypes = require('react-native/Libraries/DeprecatedPropTypes/DeprecatedTextPropTypes');
const React = require('react');
const ReactNativeViewAttributes = require('react-native/Libraries/Components/View/ReactNativeViewAttributes');
const Touchable = require('react-native/Libraries/Components/Touchable/Touchable');

const createReactNativeComponentClass = require('react-native/Libraries/Renderer/shims/createReactNativeComponentClass');
const nullthrows = require('nullthrows');
const processColor = require('react-native/Libraries/StyleSheet/processColor');
import type {
  TextProps,
  Text as IText,
  GestureResponderEvent,
  HostComponent,
} from 'react-native';

type PressRetentionOffset = {
  top: number;
  left: number;
  bottom: number;
  right: number;
};

type ResponseHandlers = {
  onStartShouldSetResponder: () => boolean;
  onResponderGrant: (event: GestureResponderEvent, dispatchID: string) => void;
  onResponderMove: (event: GestureResponderEvent) => void;
  onResponderRelease: (event: GestureResponderEvent) => void;
  onResponderTerminate: (event: GestureResponderEvent) => void;
  onResponderTerminationRequest: () => boolean;
};

type Props = TextProps & {
  forwardedRef?: React.Ref<IText>;
  text: string;
};

type State = {
  touchable: {
    touchState?: string;
    responderID?: number;
  };
  isHighlighted: boolean;
  createResponderHandlers: () => ResponseHandlers;
  responseHandlers?: ResponseHandlers;
};

const PRESS_RECT_OFFSET = { top: 20, left: 20, right: 20, bottom: 30 };

const viewConfig = {
  validAttributes: {
    ...ReactNativeViewAttributes.UIView,
    isHighlighted: true,
    numberOfLines: true,
    ellipsizeMode: true,
    allowFontScaling: true,
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
    text: true,
  },
  directEventTypes: {
    topTextLayout: {
      registrationName: 'onTextLayout',
    },
    topInlineViewLayout: {
      registrationName: 'onInlineViewLayout',
    },
  },
  uiViewClassName: 'JBAnimatedText',
};

/**
 * A React component for displaying text.
 *
 * See https://reactnative.dev/docs/text.html
 */
class TouchableText extends React.ComponentClass<Props, State> {
  static defaultProps = {
    accessible: true,
    allowFontScaling: true,
    ellipsizeMode: 'tail',
  };

  touchableGetPressRectOffset?: () => PressRetentionOffset;
  touchableHandleActivePressIn?: () => void;
  touchableHandleActivePressOut?: () => void;
  touchableHandleLongPress?: (event: GestureResponderEvent) => void;
  touchableHandlePress?: (event: GestureResponderEvent) => void;
  touchableHandleResponderGrant?: (event: GestureResponderEvent) => void;
  touchableHandleResponderMove?: (event: GestureResponderEvent) => void;
  touchableHandleResponderRelease?: (event: GestureResponderEvent) => void;
  touchableHandleResponderTerminate?: (event: GestureResponderEvent) => void;
  touchableHandleResponderTerminationRequest?: () => boolean;

  state = {
    ...Touchable.Mixin.touchableGetInitialState(),
    isHighlighted: false,
    createResponderHandlers: this._createResponseHandlers.bind(this),
    responseHandlers: null,
  };

  static getDerivedStateFromProps(
    nextProps: Props,
    prevState: State
  ): Partial<State> | null {
    return prevState.responseHandlers == null && isTouchable(nextProps)
      ? {
          responseHandlers: prevState.createResponderHandlers(),
        }
      : null;
  }

  static viewConfig = viewConfig;

  render(): React.ReactNode {
    let props = this.props;
    if (isTouchable(props)) {
      props = {
        ...props,
        ...this.state.responseHandlers,
        isHighlighted: this.state.isHighlighted,
      };
    }
    if (props.selectionColor != null) {
      props = {
        ...props,
        selectionColor: processColor(props.selectionColor),
      };
    }
    if (__DEV__) {
      if (Touchable.TOUCH_TARGET_DEBUG && props.onPress != null) {
        props = {
          ...props,
          style: [props.style, { color: 'magenta' }],
        };
      }
    }
    return <RCTText {...props} ref={props.forwardedRef} />;
  }

  _createResponseHandlers(): ResponseHandlers {
    return {
      onStartShouldSetResponder: (): boolean => {
        const { onStartShouldSetResponder } = this.props;
        const shouldSetResponder =
          (onStartShouldSetResponder == null
            ? false
            : onStartShouldSetResponder()) || isTouchable(this.props);

        if (shouldSetResponder) {
          this._attachTouchHandlers();
        }
        return shouldSetResponder;
      },
      onResponderGrant: (event: GestureResponderEvent): void => {
        nullthrows(this.touchableHandleResponderGrant)(event);
        if (this.props.onResponderGrant != null) {
          this.props.onResponderGrant.call(this, event);
        }
      },
      onResponderMove: (event: GestureResponderEvent): void => {
        nullthrows(this.touchableHandleResponderMove)(event);
        if (this.props.onResponderMove != null) {
          this.props.onResponderMove.call(this, event);
        }
      },
      onResponderRelease: (event: GestureResponderEvent): void => {
        nullthrows(this.touchableHandleResponderRelease)(event);
        if (this.props.onResponderRelease != null) {
          this.props.onResponderRelease.call(this, event);
        }
      },
      onResponderTerminate: (event: GestureResponderEvent): void => {
        nullthrows(this.touchableHandleResponderTerminate)(event);
        if (this.props.onResponderTerminate != null) {
          this.props.onResponderTerminate.call(this, event);
        }
      },
      onResponderTerminationRequest: (): boolean => {
        const { onResponderTerminationRequest } = this.props;
        if (!nullthrows(this.touchableHandleResponderTerminationRequest)()) {
          return false;
        }
        if (onResponderTerminationRequest == null) {
          return true;
        }
        return onResponderTerminationRequest();
      },
    };
  }

  /**
   * Lazily attaches Touchable.Mixin handlers.
   */
  _attachTouchHandlers(): void {
    if (this.touchableGetPressRectOffset != null) {
      return;
    }
    for (const key in Touchable.Mixin) {
      if (typeof Touchable.Mixin[key] === 'function') {
        this[key] = Touchable.Mixin[key].bind(this);
      }
    }
    this.touchableHandleActivePressIn = (): void => {
      if (!this.props.suppressHighlighting && isTouchable(this.props)) {
        this.setState({ isHighlighted: true });
      }
    };
    this.touchableHandleActivePressOut = (): void => {
      if (!this.props.suppressHighlighting && isTouchable(this.props)) {
        this.setState({ isHighlighted: false });
      }
    };
    this.touchableHandlePress = (event: GestureResponderEvent): void => {
      if (this.props.onPress != null) {
        this.props.onPress(event);
      }
    };
    this.touchableHandleLongPress = (event: GestureResponderEvent): void => {
      if (this.props.onLongPress != null) {
        this.props.onLongPress(event);
      }
    };
    this.touchableGetPressRectOffset = (): PressRetentionOffset =>
      this.props.pressRetentionOffset == null
        ? PRESS_RECT_OFFSET
        : this.props.pressRetentionOffset;
  }
}

const isTouchable = (props: Props): boolean =>
  props.onPress != null ||
  props.onLongPress != null ||
  // @ts-expect-error
  props.onStartShouldSetResponder != null;

const RCTText = createReactNativeComponentClass(
  viewConfig.uiViewClassName,
  () => viewConfig
);

const Text = (props: Props, forwardedRef?: React.Ref<IText>) => {
  // @ts-expect-error
  return <TouchableText {...props} forwardedRef={forwardedRef} />;
};
const TextToExport = React.forwardRef(Text);
TextToExport.displayName = 'Text';

// TODO: Deprecate this.
/* $FlowFixMe(>=0.89.0 site=react_native_fb) This comment suppresses an error
 * found when Flow v0.89 was deployed. To see the error, delete this comment
 * and run Flow. */
TextToExport.propTypes = DeprecatedTextPropTypes;

type TextStatics = {
  propTypes: typeof DeprecatedTextPropTypes;
};

export const AnimateableText = TextToExport as React.ComponentClass<
  Props,
  React.ElementRef<HostComponent<TextProps>>
> &
  TextStatics;
