import type { TextProps as NativeTextProps, Text as IText } from 'react-native';
import type { SharedValue } from 'react-native-reanimated';

export type AnimateableTextProps = Omit<NativeTextProps, 'children'> & {
  forwardedRef?: React.Ref<IText>;
  animatedProps?: Partial<
    Omit<NativeTextProps, 'children'> & { text?: string }
  >;
  text?: string | SharedValue<string>;
};
