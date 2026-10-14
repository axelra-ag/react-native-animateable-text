import Animated from 'react-native-reanimated';
import { AnimateableText as RawAnimateableText } from './AnimateableText';

Animated.addWhitelistedNativeProps({ text: true });

const AnimateableText = Animated.createAnimatedComponent(RawAnimateableText);

export default AnimateableText;
export type {
  AnimateableTextProps,
  ContentTransition,
  ContentTransitionDirection,
  ContentTransitionType,
} from './TextProps';
