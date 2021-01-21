import type { TextProps as NativeTextProps, Text as IText } from 'react-native';
import type Animated from 'react-native-reanimated';

export type AnimateableTextProps = Omit<NativeTextProps, 'children'> & {
  forwardedRef?: React.Ref<IText>;
  animatedProps?: Partial<
    Omit<NativeTextProps, 'children'> & { text?: string }
  >;
  text?: string | Animated.Node<string>;
};
