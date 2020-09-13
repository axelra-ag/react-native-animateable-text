import * as React from 'react';
import { StyleSheet, View } from 'react-native';
import { AnimatedText } from 'react-native-reanimated-text';

export default function App() {
  return (
    <View style={styles.container}>
      <AnimatedText actualText="hi"></AnimatedText>
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
