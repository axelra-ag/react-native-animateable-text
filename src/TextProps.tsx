import type { TextProps as NativeTextProps } from 'react-native';

export type TextProps = Omit<NativeTextProps, 'children'> & {
  text: string;
};
