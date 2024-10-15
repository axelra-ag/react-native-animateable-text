import Slider from '@react-native-community/slider';
import * as React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import AnimateableText from 'react-native-animateable-text';
import { useAnimatedProps, useSharedValue } from 'react-native-reanimated';

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
        style={{ width: '80%', height: 40 }}
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

      <View style={{height: 100, width: 100, backgroundColor: 'red'}}></View>
      <AnimateableText numberOfLines={1} style={{ height: 100, width: 100, backgroundColor: 'yellow', fontWeight: '900'}} text='rest asdfasdf asdfasd fassdfasdf  asdfasd' />
    </View>
  );
}
