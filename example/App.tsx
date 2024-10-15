
import * as React from 'react';
import { StyleSheet, View } from 'react-native';
import AnimateableText from 'react-native-animateable-text';
import { useAnimatedProps, useSharedValue } from 'react-native-reanimated';
import {Slider} from '@miblanchard/react-native-slider';

const style = {
  fontSize: 30,
  fontWeight: 'bold' as const,
  color: '#7832C7' as const,
  paddingTop: 0,
  paddingBottom: 0,
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  row: { flexDirection: 'row', paddingLeft: 20, paddingRight: 20 },
});

export default function App() {
  React.useEffect(() => {
    // checking if the app is in new arch or not
    // @ts-ignore
    const uiManager = global?.nativeFabricUIManager ? 'Fabric' : 'Paper';
    console.log(`Using ${uiManager}`);
  }, []);

  const stringSharedValue = useSharedValue('1');

  const animatedProps = useAnimatedProps(() => {
    return {
      text: stringSharedValue.value,
    };
  });

  return (
    <View style={styles.container}>
      <Slider
        containerStyle={{
          width: '80%',
        }}
        minimumValue={0}
        step={1}
        maximumValue={10000}
        minimumTrackTintColor="pink"
        maximumTrackTintColor="violet"
        thumbTintColor="#7832C7"
        onValueChange={(value) => {
          stringSharedValue.value = `${value}`;
        }}
      />
      <AnimateableText selectable style={style} animatedProps={animatedProps} />
    </View>
  );
}
