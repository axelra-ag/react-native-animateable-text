/*
 * Custom -> used only for codegen
 */

import codegenNativeComponent from 'react-native/Libraries/Utilities/codegenNativeComponent';
import type { ViewProps } from 'react-native';
import type {
  Float,
  WithDefault,
} from 'react-native/Libraries/Types/CodegenTypes';

interface NativeProps extends ViewProps {
  text?: string;
  contentTransitionType?: WithDefault<string, 'none'>;
  contentTransitionDuration?: WithDefault<Float, 0>;
  contentTransitionDirection?: WithDefault<string, 'auto'>;
}

export default codegenNativeComponent<NativeProps>('JBAnimatedText');
