import * as React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import AnimateableText from 'react-native-animateable-text';
import { PanGestureHandler } from 'react-native-gesture-handler';
import Animated, { concat, multiply, round } from 'react-native-reanimated';
import { ReText, usePanGestureHandler } from 'react-native-redash';

const style = {
  fontSize: 30,
  fontWeight: 'bold' as const,
  color: 'black' as const,
  paddingTop: 0,
  paddingBottom: 0,
};

const styles = StyleSheet.create({
  flex1: {
    flex: 1,
  },
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  row: { flexDirection: 'row', paddingLeft: 20, paddingRight: 20 },
  spacer: {
    height: 20,
  },
  outerBar: {
    width: '90%',
    backgroundColor: 'rgba(0, 0, 0, 0.1)',
    height: 40,
  },
});

export default function App() {
  const panGestureHandler = usePanGestureHandler();
  const str = React.useMemo(
    () => concat(round(multiply(panGestureHandler.translation.x, 1000)), '×'),
    [panGestureHandler.translation.x]
  );

  const bar = React.useMemo(() => {
    return {
      left: panGestureHandler.position.x,
      height: 40,
      width: 10,
      backgroundColor: 'black',
    };
  }, [panGestureHandler.position.x]);

  return (
    <View style={styles.container}>
      <View style={styles.row}>
        <Text style={style}>ReText: </Text>
        <View style={styles.flex1} />
        <ReText text={str} style={style} />
      </View>
      <View style={styles.row}>
        <Text style={style} selectable>
          AnimateableText:{' '}
        </Text>
        <View style={styles.flex1} />
        <AnimateableText selectable text={str} style={style} />
      </View>
      <View style={styles.spacer} />
      <PanGestureHandler {...panGestureHandler.gestureHandler}>
        <Animated.View style={styles.outerBar}>
          <Animated.View style={bar} />
        </Animated.View>
      </PanGestureHandler>
    </View>
  );
}
