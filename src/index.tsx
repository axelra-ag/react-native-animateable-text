import Animated from 'react-native-reanimated';
import { AnimateableText as RawAnimateableText } from './AnimateableText';

const AnimateableText = Animated.createAnimatedComponent(
  RawAnimateableText
) as typeof RawAnimateableText;

export default AnimateableText;
