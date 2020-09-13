import * as React from 'react';
import { StyleSheet, View } from 'react-native';
import AnimatedText from 'react-native-animateable-text';

export default function App() {
  return (
    <View style={styles.container}>
      <AnimatedText text="string" />
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
