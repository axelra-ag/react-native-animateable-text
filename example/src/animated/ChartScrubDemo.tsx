import * as React from 'react';
import { Text, View, type GestureResponderEvent, type LayoutChangeEvent } from 'react-native';
import AnimateableText from 'react-native-animateable-text';
import Animated, {
  useAnimatedProps,
  useAnimatedStyle,
  useFrameCallback,
  useSharedValue,
} from 'react-native-reanimated';
import { Button } from '../ui/Button';
import { DemoCard } from '../ui/DemoCard';
import { colors } from '../ui/theme';
import { formatAmount } from './formatUtils';
import { useContentTransition, useFont } from './settings';

const CHART_HEIGHT = 120;
const POINT_COUNT = 72;
const LINE_WIDTH = 2;
const DOT_SIZE = 10;
// One sweep across the chart and back. At 60 fps the value changes on almost every frame in
// the middle of the sweep and every few frames near the ends.
const AUTO_SCRUB_PERIOD_MS = 2400;

// A fixed, bumpy series (no randomness, so recordings are comparable) that crosses 1,000 and
// 10,000: the amount changes width while scrubbing.
const POINTS: number[] = Array.from({ length: POINT_COUNT }, (_, index) => {
  const t = index / (POINT_COUNT - 1);
  const trend = 400 + 15000 * t * t;
  const waves = 900 * Math.sin(t * 17) + 500 * Math.sin(t * 41 + 1) + 260 * Math.sin(t * 97 + 2);
  return Math.round(Math.max(120, trend + waves * (0.3 + t)) * 100) / 100;
});
const MIN_POINT = Math.min(...POINTS);
const MAX_POINT = Math.max(...POINTS);

function ChartLine({ width }: { width: number }) {
  const step = width / (POINT_COUNT - 1);
  const toY = (value: number) => CHART_HEIGHT - 6 - ((value - MIN_POINT) / (MAX_POINT - MIN_POINT)) * (CHART_HEIGHT - 12);
  return (
    <View pointerEvents="none" style={{ position: 'absolute', left: 0, top: 0, right: 0, bottom: 0 }}>
      {POINTS.slice(1).map((value, index) => {
        const fromY = toY(POINTS[index] ?? value);
        const toYValue = toY(value);
        const length = Math.hypot(step, toYValue - fromY);
        const angle = Math.atan2(toYValue - fromY, step);
        return (
          <View
            key={index}
            style={{
              position: 'absolute',
              left: (index + 0.5) * step - length / 2,
              top: (fromY + toYValue) / 2 - LINE_WIDTH / 2,
              width: length + 0.5,
              height: LINE_WIDTH,
              borderRadius: LINE_WIDTH / 2,
              backgroundColor: colors.accent,
              transform: [{ rotate: `${angle}rad` }],
            }}
          />
        );
      })}
    </View>
  );
}

/**
 * A line chart to scrub: dragging across it (or "Auto scrub") moves a cursor and writes a
 * shared value, which drives the amount and the pill below without a React render.
 */
export function ChartScrubDemo({ autorun = false }: { autorun?: boolean }) {
  const [width, setWidth] = React.useState(0);
  const chartWidth = useSharedValue(0);
  // Cursor position, 0…1 across the chart.
  const position = useSharedValue(0.35);
  const elapsed = useSharedValue(0);
  const transition = useContentTransition();
  const boldFont = useFont(700);
  const mediumFont = useFont(500);

  const frameCallback = useFrameCallback((frame) => {
    elapsed.value += frame.timeSincePreviousFrame ?? 16;
    position.value = 0.5 - 0.5 * Math.cos((elapsed.value / AUTO_SCRUB_PERIOD_MS) * 2 * Math.PI);
  }, false);
  const [isAutoScrubbing, setIsAutoScrubbing] = React.useState(false);
  const setAutoScrubbing = (isActive: boolean) => {
    frameCallback.setActive(isActive);
    setIsAutoScrubbing(isActive);
  };
  React.useEffect(() => {
    if (autorun) setAutoScrubbing(true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [autorun]);

  const handleLayout = (event: LayoutChangeEvent) => {
    setWidth(event.nativeEvent.layout.width);
    chartWidth.value = event.nativeEvent.layout.width;
  };
  const handleTouch = (event: GestureResponderEvent) => {
    if (width <= 0) return;
    position.value = Math.min(1, Math.max(0, event.nativeEvent.locationX / width));
  };

  const amountProps = useAnimatedProps(() => {
    const index = Math.round(position.value * (POINT_COUNT - 1));
    return { text: `SAR ${formatAmount(POINTS[index] ?? 0)}` };
  });
  const pillAmountProps = useAnimatedProps(() => {
    const index = Math.round(position.value * (POINT_COUNT - 1));
    return { text: formatAmount(POINTS[index] ?? 0) };
  });
  const pillChangeProps = useAnimatedProps(() => {
    const index = Math.round(position.value * (POINT_COUNT - 1));
    const change = (((POINTS[index] ?? 0) - (POINTS[0] ?? 1)) / (POINTS[0] ?? 1)) * 100;
    return { text: `${change >= 0 ? '+' : '-'}${Math.abs(change).toFixed(0)}%` };
  });
  const cursorStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: position.value * chartWidth.value }],
  }));
  const dotStyle = useAnimatedStyle(() => {
    const index = Math.round(position.value * (POINT_COUNT - 1));
    const value = POINTS[index] ?? 0;
    const y = CHART_HEIGHT - 6 - ((value - MIN_POINT) / (MAX_POINT - MIN_POINT)) * (CHART_HEIGHT - 12);
    return { transform: [{ translateY: y - DOT_SIZE / 2 }] };
  });

  return (
    <DemoCard
      title="Chart scrub"
      description="Drag across the chart. Every point the cursor passes writes a new amount; while scrubbing fast that is a change on almost every frame. Made for contentTransition 'roll'."
    >
      <AnimateableText
        style={[boldFont, { fontSize: 34, color: colors.text, fontVariant: ['tabular-nums'] }]}
        animatedProps={amountProps}
        contentTransition={transition}
      />
      <View style={{ height: CHART_HEIGHT }} onLayout={handleLayout}>
        {width > 0 ? <ChartLine width={width} /> : null}
        <Animated.View
          pointerEvents="none"
          style={[{ position: 'absolute', left: -DOT_SIZE / 2, top: 0, bottom: 0, width: DOT_SIZE }, cursorStyle]}
        >
          <View
            style={{
              position: 'absolute',
              left: DOT_SIZE / 2 - 0.5,
              top: 0,
              bottom: 0,
              width: 1,
              backgroundColor: colors.muted,
              opacity: 0.5,
            }}
          />
          <Animated.View
            style={[
              {
                width: DOT_SIZE,
                height: DOT_SIZE,
                borderRadius: DOT_SIZE / 2,
                backgroundColor: colors.accent,
                borderWidth: 2,
                borderColor: colors.surface,
              },
              dotStyle,
            ]}
          />
        </Animated.View>
        <View
          style={{ position: 'absolute', left: 0, top: 0, right: 0, bottom: 0 }}
          onStartShouldSetResponder={() => true}
          onMoveShouldSetResponder={() => true}
          onResponderTerminationRequest={() => false}
          onResponderGrant={(event) => {
            setAutoScrubbing(false);
            handleTouch(event);
          }}
          onResponderMove={handleTouch}
        />
      </View>
      <View style={{ alignItems: 'center' }}>
        <View
          style={{
            flexDirection: 'row',
            alignItems: 'center',
            gap: 10,
            paddingVertical: 6,
            paddingHorizontal: 12,
            borderRadius: 14,
            backgroundColor: '#1F8A4C22',
          }}
        >
          <Text style={[mediumFont, { fontSize: 14, color: colors.positive }]}>SAR</Text>
          <AnimateableText
            style={[mediumFont, { fontSize: 14, color: colors.positive, fontVariant: ['tabular-nums'] }]}
            animatedProps={pillAmountProps}
            contentTransition={transition}
          />
          <AnimateableText
            style={[mediumFont, { fontSize: 14, color: colors.positive, fontVariant: ['tabular-nums'] }]}
            animatedProps={pillChangeProps}
            contentTransition={transition}
          />
        </View>
      </View>
      <View style={{ flexDirection: 'row', gap: 8 }}>
        <Button
          title={isAutoScrubbing ? 'Stop auto scrub' : 'Auto scrub'}
          onPress={() => setAutoScrubbing(!isAutoScrubbing)}
        />
      </View>
    </DemoCard>
  );
}
