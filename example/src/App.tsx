import * as React from 'react';
import { StyleSheet, View } from 'react-native';
import AnimateableText from 'react-native-animateable-text';
import Animated, { concat } from 'react-native-reanimated';

const AnimatedText = Animated.createAnimatedComponent(AnimateableText);

export default function App() {
  const value = React.useMemo(() => new Animated.Value(10), []);
  const str = React.useMemo(() => concat('Animated ', value), [value]);
  return (
    <View style={styles.container}>
      <AnimatedText text={str}></AnimatedText>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
