import React from 'react';
import type { TextProps as NativeTextProps, Text as IText } from 'react-native';

export type AnimateableTextProps = Omit<NativeTextProps, 'children'> & {
  forwardedRef?: React.Ref<IText>;
  text?: string;
};
