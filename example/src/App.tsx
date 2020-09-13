import * as React from 'react';
import { StyleSheet, View } from 'react-native';
import AnimateableText from 'react-native-animateable-text';
import Animated, { concat, round } from 'react-native-reanimated';
import { withTimingTransition } from 'react-native-redash';

const AnimatedText = Animated.createAnimatedComponent(AnimateableText);

export default function App() {
  const value = React.useMemo(() => new Animated.Value(1000), []);
  const str = React.useMemo(
    () =>
      concat(
        'Animated ',
        round(withTimingTransition(value, { duration: 1000 }))
      ),
    [value]
  );
  return (
    <View style={styles.container}>
      <AnimatedText text={str} />
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
