import * as React from 'react';
import { ScrollView, Switch, Text, View } from 'react-native';
import AnimateableText from 'react-native-animateable-text';
import Animated, {
  interpolateColor,
  LinearTransition,
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
import { Segmented } from '../ui/Segmented';
import { colors, GUTTER } from '../ui/theme';
import { formatAmount, formatStopwatch, toArabicDigits } from './formatUtils';
import {
  DEFAULT_SETTINGS,
  DIRECTIONS,
  DURATIONS,
  FONT_LABELS,
  FONTS,
  SettingsProvider,
  TRANSITIONS,
  useContentTransition,
  useFont,
  type Settings,
} from './settings';

const STATUSES = ['Pending', 'Processing', 'Completed', 'Failed'];
const ARABIC_STATUSES = ['قيد الانتظار', 'قيد المعالجة', 'مكتمل', 'فشل التحويل'];
const SENTENCES = [
  'Short line.',
  'A longer sentence that wraps onto a second line and gets truncated when it runs past two lines of text.',
  'Arabic mixed in: الرصيد الحالي ١٬٢٠٤٫٥٠ ريال',
];

const randomAmount = () => Math.round(Math.random() * 250_000_00) / 100;
const randomPercent = () => Math.round((Math.random() * 50 - 25) * 100) / 100;

function SettingRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <View style={{ gap: 6 }}>
      <Text style={{ fontSize: 12, color: colors.muted }}>{label}</Text>
      {children}
    </View>
  );
}

function SettingsCard({ settings, onChange }: { settings: Settings; onChange: (settings: Settings) => void }) {
  return (
    <DemoCard
      title="Settings"
      description="Applied to every demo. Transition 'none' is the plain animated text (no content transition)."
    >
      <SettingRow label="contentTransition">
        <Segmented
          options={TRANSITIONS}
          value={settings.transition}
          onChange={(transition) => onChange({ ...settings, transition })}
        />
      </SettingRow>
      <SettingRow label="direction">
        <Segmented
          options={DIRECTIONS}
          value={settings.direction}
          onChange={(direction) => onChange({ ...settings, direction })}
        />
      </SettingRow>
      <SettingRow label="duration">
        <Segmented
          options={DURATIONS}
          value={settings.duration}
          onChange={(duration) => onChange({ ...settings, duration })}
          format={(value) => (value === 0 ? 'default' : `${value} ms`)}
        />
      </SettingRow>
      <SettingRow label="font">
        <Segmented
          options={FONTS}
          value={settings.font}
          onChange={(font) => onChange({ ...settings, font })}
          format={(value) => FONT_LABELS[value]}
        />
      </SettingRow>
    </DemoCard>
  );
}

function SliderDemo() {
  const value = useSharedValue('0');
  const animatedProps = useAnimatedProps(() => ({ text: value.value }));
  return (
    <DemoCard
      title="Slider → shared value"
      description="The original demo: the slider writes a shared value and the text updates without a React render. With a transition on, every slider step starts a new transition."
    >
      <AnimateableText
        style={[useFont(700), { fontSize: 34, color: colors.accent, textAlign: 'center' }]}
        animatedProps={animatedProps}
        contentTransition={useContentTransition()}
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

function BalanceDemo() {
  const [amount, setAmount] = React.useState(1204.5);
  const transition = useContentTransition();
  const font = useFont(700);
  return (
    <DemoCard
      title="Balance (discrete steps)"
      description="Plain text prop. Arabic-Indic digits and the currency labels stay aligned."
    >
      <AnimateableText
        style={[font, { fontSize: 34, color: colors.text }]}
        text={`SAR ${formatAmount(amount)}`}
        contentTransition={transition}
      />
      <AnimateableText
        style={[font, { fontSize: 34, color: colors.text, writingDirection: 'rtl', textAlign: 'right' }]}
        text={`${toArabicDigits(formatAmount(amount))} ر.س`}
        contentTransition={transition}
      />
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
        <Button title="+1" onPress={() => setAmount(amount + 1)} />
        <Button title="−1" onPress={() => setAmount(amount - 1)} />
        <Button title="+0.25" onPress={() => setAmount(amount + 0.25)} />
        <Button title="×10" onPress={() => setAmount(amount * 10)} />
        <Button title="÷10" onPress={() => setAmount(amount / 10)} />
        <Button title="Random" isSecondary onPress={() => setAmount(randomAmount())} />
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
    color: interpolateColor(percent.value, [-5, 0, 5], [colors.negative, colors.muted, colors.positive]),
  }));
  return (
    <DemoCard
      title="Percentage + colour"
      description="One shared value drives the text and its colour (red → grey → green) while the transition runs. Slider for continuous changes, buttons for discrete ones."
    >
      <AnimateableText
        style={[useFont(700), { fontSize: 34 }, animatedStyle]}
        animatedProps={animatedProps}
        contentTransition={useContentTransition()}
      />
      <Slider
        minimumValue={-25}
        maximumValue={25}
        step={0.01}
        value={4.2}
        minimumTrackTintColor={colors.accent}
        thumbTintColor={colors.accent}
        onValueChange={([next]) => {
          percent.value = next;
        }}
      />
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
        <Button
          title="−5%"
          onPress={() => {
            percent.value = Math.round((percent.value - 5) * 100) / 100;
          }}
        />
        <Button
          title="+5%"
          onPress={() => {
            percent.value = Math.round((percent.value + 5) * 100) / 100;
          }}
        />
        <Button
          title="Flip sign"
          onPress={() => {
            percent.value = -percent.value;
          }}
        />
        <Button
          title="Random"
          isSecondary
          onPress={() => {
            percent.value = randomPercent();
          }}
        />
      </View>
    </DemoCard>
  );
}

const SIDE_BOX = { width: 28, height: 28, borderRadius: 6, backgroundColor: colors.accent };

function BoxedNumberDemo() {
  const [count, setCount] = React.useState(9);
  const [isShowingBounds, setIsShowingBounds] = React.useState(true);
  const [isLayoutAnimated, setIsLayoutAnimated] = React.useState(false);
  // RN lays the text out instantly at its new size; Reanimated can animate the views around it.
  const layout = isLayoutAnimated ? LinearTransition.springify().damping(30).stiffness(220) : undefined;
  return (
    <DemoCard
      title="Centred number between boxes"
      description="A centred row: box, number, box, inside a bordered container. The text view takes its new size immediately (RN layout); kept digits stay put on screen, then slide. Turn on the layout animation to animate the boxes and border with Reanimated."
    >
      <View style={{ alignItems: 'center' }}>
        <Animated.View
          layout={layout}
          style={{
            flexDirection: 'row',
            alignItems: 'center',
            gap: 8,
            padding: 10,
            borderWidth: 1,
            borderColor: colors.accent,
            borderRadius: 12,
          }}
        >
          <Animated.View layout={layout} style={SIDE_BOX} />
          <AnimateableText
            style={[
              useFont(700),
              { fontSize: 44, color: colors.accent, fontVariant: ['tabular-nums'] },
              isShowingBounds ? { backgroundColor: '#7832C722' } : null,
            ]}
            text={`${count}`}
            contentTransition={useContentTransition()}
          />
          <Animated.View layout={layout} style={SIDE_BOX} />
        </Animated.View>
      </View>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
        <Button title="+1" onPress={() => setCount(count + 1)} />
        <Button title="−1" onPress={() => setCount(count - 1)} />
        <Button title="+91" onPress={() => setCount(count + 91)} />
        <Button title="+1000" onPress={() => setCount(count + 1000)} />
        <Button title="÷10" onPress={() => setCount(Math.floor(count / 10))} />
      </View>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
        <Switch value={isShowingBounds} onValueChange={setIsShowingBounds} />
        <Text style={{ fontSize: 13, color: colors.muted }}>Show the text view's bounds</Text>
      </View>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
        <Switch value={isLayoutAnimated} onValueChange={setIsLayoutAnimated} />
        <Text style={{ fontSize: 13, color: colors.muted }}>Animate the row's layout (LinearTransition)</Text>
      </View>
    </DemoCard>
  );
}

function CountUpDemo() {
  const amount = useSharedValue(1204.5);
  const animatedProps = useAnimatedProps(() => ({ text: `SAR ${formatAmount(amount.value)}` }));
  return (
    <DemoCard
      title="Count-up (every frame)"
      description="withTiming changes the number every frame. This is what plain animated text is for; with a transition on, each frame restarts it."
    >
      <AnimateableText
        style={[useFont(700), { fontSize: 30, color: colors.text }]}
        animatedProps={animatedProps}
        contentTransition={useContentTransition()}
      />
      <View style={{ flexDirection: 'row', gap: 8 }}>
        <Button
          title="Random"
          onPress={() => {
            amount.value = withTiming(randomAmount(), { duration: 1200 });
          }}
        />
        <Button
          title="Zero"
          isSecondary
          onPress={() => {
            amount.value = withTiming(0, { duration: 1200 });
          }}
        />
      </View>
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
        style={[useFont(500), { fontSize: 36, color: colors.text, fontVariant: ['tabular-nums'] }]}
        animatedProps={animatedProps}
        contentTransition={useContentTransition()}
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

function WordsDemo() {
  const [index, setIndex] = React.useState(0);
  const transition = useContentTransition();
  const font = useFont(500);
  return (
    <DemoCard title="Words" description="Latin animates per character, Arabic per word so the letters stay joined.">
      <AnimateableText
        style={[font, { fontSize: 26, color: colors.text }]}
        text={STATUSES[index % STATUSES.length]}
        contentTransition={transition}
      />
      <AnimateableText
        style={[font, { fontSize: 26, color: colors.text, writingDirection: 'rtl', textAlign: 'right' }]}
        text={ARABIC_STATUSES[index % ARABIC_STATUSES.length]}
        contentTransition={transition}
      />
      <View style={{ flexDirection: 'row', gap: 8 }}>
        <Button title="Next status" onPress={() => setIndex(index + 1)} />
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
      description="Layout and … truncation follow RN's Text. With numberOfLines set, transitions fall back to a whole-text cross-fade."
    >
      <View style={{ minHeight: 48 }}>
        <AnimateableText
          style={[useFont(500), { fontSize: 17, color: colors.text }]}
          numberOfLines={2}
          animatedProps={animatedProps}
          contentTransition={useContentTransition()}
        />
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
  const [settings, setSettings] = React.useState<Settings>(DEFAULT_SETTINGS);
  return (
    <SettingsProvider value={settings}>
      <View style={{ flex: 1, backgroundColor: colors.background }}>
        <ScreenHeader title="Animated text" onBack={onBack} />
        <ScrollView contentContainerStyle={{ paddingHorizontal: GUTTER, paddingBottom: 48, gap: 16 }}>
          <SettingsCard settings={settings} onChange={setSettings} />
          <SliderDemo />
          <BalanceDemo />
          <PercentageDemo />
          <BoxedNumberDemo />
          <CountUpDemo />
          <StopwatchDemo />
          <WordsDemo />
          <MultilineDemo />
        </ScrollView>
      </View>
    </SettingsProvider>
  );
}
