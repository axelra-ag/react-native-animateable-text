import Animated from 'react-native-reanimated';
import { AnimateableText as RawAnimateableText } from './AnimateableText';

Animated.addWhitelistedNativeProps({ text: true });

const AnimateableText = Animated.createAnimatedComponent(RawAnimateableText);

export default AnimateableText;
