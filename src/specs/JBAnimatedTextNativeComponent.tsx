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
  contentTransitionUnit?: WithDefault<string, 'auto'>;
  contentTransitionStagger?: WithDefault<Float, 0>;
  contentTransitionScope?: WithDefault<string, 'all'>;
  contentTransitionMinInterval?: WithDefault<Float, -1>;
  contentTransitionDelay?: WithDefault<Float, 0>;
  // Also a number, boolean or array of those at runtime; normalised natively.
  transitionKey?: string;
  revealType?: WithDefault<string, 'none'>;
  revealDuration?: WithDefault<Float, 0>;
  revealDirection?: WithDefault<string, 'up'>;
  revealUnit?: WithDefault<string, 'auto'>;
  revealStagger?: WithDefault<Float, 0>;
  revealDelay?: WithDefault<Float, 0>;
}

export default codegenNativeComponent<NativeProps>('JBAnimatedText');
