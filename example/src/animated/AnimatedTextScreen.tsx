import * as React from 'react';
import { ScrollView, View } from 'react-native';
import AnimateableText from 'react-native-animateable-text';
import {
  interpolateColor,
  useAnimatedProps,
  useAnimatedStyle,
  useFrameCallback,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { Slider } from '@miblanchard/react-native-slider';
import { Button } from '../ui/Button';
import { DemoCard } from '../ui/DemoCard';
import { ScreenHeader } from '../ui/ScreenHeader';
import { colors, GUTTER } from '../ui/theme';
import { formatAmount, formatStopwatch, toArabicDigits } from './formatUtils';

const SENTENCES = [
  'Short line.',
  'A longer sentence that wraps onto a second line and gets truncated when it runs past two lines of text.',
  'Arabic mixed in: الرصيد الحالي ١٬٢٠٤٫٥٠ ريال',
];

const randomAmount = () => Math.round(Math.random() * 250_000_00) / 100;

function SliderDemo() {
  const value = useSharedValue('0');
  const animatedProps = useAnimatedProps(() => ({ text: value.value }));
  return (
    <DemoCard
      title="Slider → shared value"
      description="The original demo. The slider writes a shared value; the text updates without a React render."
    >
      <AnimateableText
        style={{ fontSize: 34, fontWeight: '700', color: colors.accent, textAlign: 'center' }}
        animatedProps={animatedProps}
      />
      <Slider
        minimumValue={0}
        maximumValue={10000}
        step={1}
        minimumTrackTintColor={colors.accent}
        thumbTintColor={colors.accent}
        onValueChange={([next]) => {
          value.value = `${next}`;
        }}
      />
    </DemoCard>
  );
}

function CountUpDemo() {
  const amount = useSharedValue(1204.5);
  const latinProps = useAnimatedProps(() => ({ text: `SAR ${formatAmount(amount.value)}` }));
  const arabicProps = useAnimatedProps(() => ({ text: `${toArabicDigits(formatAmount(amount.value))} ر.س` }));
  const animateTo = (target: number) => {
    amount.value = withTiming(target, { duration: 1200 });
  };
  return (
    <DemoCard
      title="Balance count-up"
      description="withTiming drives a number; a worklet formats it every frame (Latin and Arabic-Indic digits)."
    >
      <AnimateableText style={{ fontSize: 30, fontWeight: '700', color: colors.text }} animatedProps={latinProps} />
      <AnimateableText
        style={{ fontSize: 30, fontWeight: '700', color: colors.text, writingDirection: 'rtl', textAlign: 'right' }}
        animatedProps={arabicProps}
      />
      <View style={{ flexDirection: 'row', gap: 8 }}>
        <Button title="Random" onPress={() => animateTo(randomAmount())} />
        <Button title="Zero" isSecondary onPress={() => animateTo(0)} />
      </View>
    </DemoCard>
  );
}

function PercentageDemo() {
  const percent = useSharedValue(4.2);
  const animatedProps = useAnimatedProps(() => ({
    text: `${percent.value >= 0 ? '+' : ''}${percent.value.toFixed(2)}%`,
  }));
  const animatedStyle = useAnimatedStyle(() => ({
    color: interpolateColor(percent.value, [-0.01, 0.01], [colors.negative, colors.positive]),
  }));
  return (
    <DemoCard
      title="Text + animated style"
      description="Text and colour driven by the same shared value, like Malaa's PerformanceBadge."
    >
      <AnimateableText style={[{ fontSize: 30, fontWeight: '700' }, animatedStyle]} animatedProps={animatedProps} />
      <Slider
        minimumValue={-25}
        maximumValue={25}
        value={4.2}
        minimumTrackTintColor={colors.accent}
        thumbTintColor={colors.accent}
        onValueChange={([next]) => {
          percent.value = next;
        }}
      />
    </DemoCard>
  );
}

function StopwatchDemo() {
  const elapsed = useSharedValue(0);
  const frameCallback = useFrameCallback((frame) => {
    elapsed.value += frame.timeSincePreviousFrame ?? 0;
  }, false);
  const animatedProps = useAnimatedProps(() => ({ text: formatStopwatch(elapsed.value) }));
  const [isRunning, setIsRunning] = React.useState(false);
  const handleToggle = () => {
    frameCallback.setActive(!isRunning);
    setIsRunning(!isRunning);
  };
  return (
    <DemoCard
      title="Stopwatch (every frame)"
      description="useFrameCallback updates the text on every frame; tabular-nums keeps the width stable."
    >
      <AnimateableText
        style={{ fontSize: 36, fontWeight: '600', color: colors.text, fontVariant: ['tabular-nums'] }}
        animatedProps={animatedProps}
      />
      <View style={{ flexDirection: 'row', gap: 8 }}>
        <Button title={isRunning ? 'Stop' : 'Start'} onPress={handleToggle} />
        <Button
          title="Reset"
          isSecondary
          onPress={() => {
            elapsed.value = 0;
          }}
        />
      </View>
    </DemoCard>
  );
}

function PlainPropDemo() {
  const [count, setCount] = React.useState(0);
  return (
    <DemoCard
      title="Plain text prop"
      description="No shared value: React state passed as the text prop, to cover the non-Reanimated path."
    >
      <AnimateableText style={{ fontSize: 30, fontWeight: '700', color: colors.text }} text={`Count: ${count}`} />
      <View style={{ flexDirection: 'row', gap: 8 }}>
        <Button title="+1" onPress={() => setCount(count + 1)} />
        <Button title="Reset" isSecondary onPress={() => setCount(0)} />
      </View>
    </DemoCard>
  );
}

function MultilineDemo() {
  const index = useSharedValue(0);
  const animatedProps = useAnimatedProps(() => ({ text: SENTENCES[index.value % SENTENCES.length] }));
  return (
    <DemoCard
      title="Multi-line + numberOfLines={2}"
      description="Text of different lengths; the layout and the … truncation follow RN's Text."
    >
      <View style={{ minHeight: 48 }}>
        <AnimateableText style={{ fontSize: 17, color: colors.text }} numberOfLines={2} animatedProps={animatedProps} />
      </View>
      <View style={{ flexDirection: 'row', gap: 8 }}>
        <Button
          title="Next text"
          onPress={() => {
            index.value += 1;
          }}
        />
      </View>
    </DemoCard>
  );
}

export function AnimatedTextScreen({ onBack }: { onBack: () => void }) {
  return (
    <View style={{ flex: 1, backgroundColor: colors.background }}>
      <ScreenHeader title="Animated text" onBack={onBack} />
      <ScrollView contentContainerStyle={{ paddingHorizontal: GUTTER, paddingBottom: 48, gap: 16 }}>
        <SliderDemo />
        <CountUpDemo />
        <PercentageDemo />
        <StopwatchDemo />
        <PlainPropDemo />
        <MultilineDemo />
      </ScrollView>
    </View>
  );
}
