/**
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
 * @flow strict-local
 * @format
 */

'use strict';

import type { TextProps as NativeTextProps } from 'react-native';

/**
 * @see https://reactnative.dev/docs/text.html#reference
 */
export type TextProps = Omit<NativeTextProps, 'children'> & {
  text: string;
};
