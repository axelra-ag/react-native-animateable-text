import Animated from 'react-native-reanimated';
import { AnimateableText as RawAnimateableText } from './AnimateableText';

Animated.addWhitelistedNativeProps({ text: true, transitionKey: true });

const AnimateableText = Animated.createAnimatedComponent(RawAnimateableText);

export default AnimateableText;
export type {
  AnimateableTextProps,
  ContentTransition,
  ContentTransitionDirection,
  ContentTransitionScope,
  ContentTransitionType,
  ContentTransitionUnit,
  Reveal,
  RevealType,
  TransitionKey,
  TransitionKeyValue,
} from './TextProps';
