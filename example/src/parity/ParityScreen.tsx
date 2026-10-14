import * as React from 'react';
import { Dimensions, PixelRatio, Platform, Pressable, Text, View } from 'react-native';
import AnimateableText from 'react-native-animateable-text';
import { useAnimatedProps, useSharedValue } from 'react-native-reanimated';
import { BACK_LINK_TOP } from '../ui/theme';
import { PARITY_CASES, type ParityCase } from './parityCases';

const GUTTER = 16;
const TRANSITION_START_MS = 100;
// Default transition settles in ~0.73 s; measure well after that.
const TRANSITION_SETTLE_MS = 1600;
const COLUMN_GAP = 16;
const COLUMN_WIDTH = Math.floor((Dimensions.get('window').width - GUTTER * 2 - COLUMN_GAP) / 2);

type Rect = { x: number; y: number; width: number; height: number };
type MeasuredCase = { id: string; text: Rect; animateable: Rect };

// Self-test: a small change on the AnimateableText side that every case must catch.
// Font size, not letterSpacing (Android ignores letterSpacing for Arabic), plus a
// one-step colour nudge for adjustsFontSizeToFit, which re-fits any font size.
const nudgeColor = (color: unknown) => {
  const hex = typeof color === 'string' && /^#[0-9a-f]{6}$/i.test(color) ? color : '#000000';
  const blue = parseInt(hex.slice(5, 7), 16);
  return `${hex.slice(0, 5)}${(blue === 255 ? 254 : blue + 1).toString(16).padStart(2, '0')}`;
};
const perturbStyle = (style: ParityCase['style']) => ({
  ...style,
  fontSize: (style?.fontSize ?? 14) + 0.25,
  color: nudgeColor(style?.color),
});

function AnimatedPropsText({ item, style }: { item: ParityCase; style: ParityCase['style'] }) {
  const text = useSharedValue(item.text);
  const animatedProps = useAnimatedProps(() => ({ text: text.value }));
  return <AnimateableText style={style} {...item.props} animatedProps={animatedProps} />;
}

// Mounts on `transition.from`, then switches to the target text, so the measured
// frame is the end state of a transition.
function TransitionedText({ item, style }: { item: ParityCase; style: ParityCase['style'] }) {
  const [text, setText] = React.useState(item.transition?.from ?? item.text);
  React.useEffect(() => {
    const timer = setTimeout(() => setText(item.text), TRANSITION_START_MS);
    return () => clearTimeout(timer);
  }, [item.text]);
  return <AnimateableText style={style} {...item.props} text={text} contentTransition={item.transition?.type} />;
}

function ParityRow({
  item,
  isPerturbed,
  textRef,
  animateableRef,
}: {
  item: ParityCase;
  isPerturbed: boolean;
  textRef: React.Ref<View>;
  animateableRef: React.Ref<View>;
}) {
  const animateableStyle = isPerturbed ? perturbStyle(item.style) : item.style;
  return (
    <View style={{ marginBottom: 14 }}>
      <Text style={{ fontSize: 10, color: '#888', marginBottom: 4 }}>{item.id}</Text>
      <View style={{ flexDirection: 'row', gap: COLUMN_GAP }}>
        <View ref={textRef} collapsable={false} style={{ width: COLUMN_WIDTH }}>
          <Text style={item.style} {...item.props}>
            {item.text}
          </Text>
        </View>
        <View ref={animateableRef} collapsable={false} style={{ width: COLUMN_WIDTH }}>
          {item.transition ? (
            <TransitionedText item={item} style={animateableStyle} />
          ) : item.useAnimatedProps ? (
            <AnimatedPropsText item={item} style={animateableStyle} />
          ) : (
            <AnimateableText style={animateableStyle} {...item.props} text={item.text} />
          )}
        </View>
      </View>
    </View>
  );
}

const measure = (view: View | null) =>
  new Promise<Rect>((resolve) => {
    if (!view) {
      resolve({ x: 0, y: 0, width: 0, height: 0 });
      return;
    }
    view.measureInWindow((x, y, width, height) => resolve({ x, y, width, height }));
  });

export function ParityScreen({
  page,
  pageSize,
  isPerturbed,
  autoAdvanceMs,
  runId,
  onPageChange,
  onBack,
}: {
  page: number;
  pageSize: number;
  isPerturbed: boolean;
  autoAdvanceMs: number;
  runId: string;
  onPageChange: (page: number) => void;
  onBack: () => void;
}) {
  const pageCount = Math.ceil(PARITY_CASES.length / pageSize);
  const cases = PARITY_CASES.slice(page * pageSize, page * pageSize + pageSize);
  const textRefs = React.useRef<Record<string, View | null>>({});
  const animateableRefs = React.useRef<Record<string, View | null>>({});

  React.useEffect(() => {
    if (page >= pageCount) {
      console.log(`PARITY_DONE ${JSON.stringify({ runId, pageCount })}`);
      return;
    }
    let cancelled = false;
    let advanceTimer: ReturnType<typeof setTimeout> | undefined;
    const timer = setTimeout(async () => {
      const measured: MeasuredCase[] = [];
      for (const item of cases) {
        measured.push({
          id: item.id,
          text: await measure(textRefs.current[item.id] ?? null),
          animateable: await measure(animateableRefs.current[item.id] ?? null),
        });
      }
      if (cancelled) return;
      const window = Dimensions.get('window');
      console.log(
        `PARITY_PAGE ${JSON.stringify({
          runId,
          platform: Platform.OS,
          page,
          pageCount,
          scale: PixelRatio.get(),
          window: { width: window.width, height: window.height },
          caseCount: measured.length,
        })}`
      );
      for (const item of measured) {
        console.log(`PARITY_CASE ${JSON.stringify({ runId, page, ...item })}`);
      }
      if (autoAdvanceMs > 0) {
        advanceTimer = setTimeout(() => onPageChange(page + 1), autoAdvanceMs);
      }
    }, cases.some((item) => item.transition) ? TRANSITION_SETTLE_MS : 600);
    return () => {
      cancelled = true;
      clearTimeout(timer);
      clearTimeout(advanceTimer);
    };
  }, [page, pageSize]);

  return (
    <View style={{ flex: 1, backgroundColor: '#FFFFFF', paddingTop: 140, paddingHorizontal: GUTTER }}>
      <Pressable onPress={onBack} hitSlop={12} style={{ position: 'absolute', top: BACK_LINK_TOP, left: GUTTER }}>
        <Text style={{ fontSize: 15, color: '#7832C7' }}>‹ Sections</Text>
      </Pressable>
      <View style={{ flexDirection: 'row', alignItems: 'center', marginBottom: 12, gap: 12 }}>
        <Pressable onPress={() => onPageChange(Math.max(0, page - 1))}>
          <Text style={{ fontSize: 14, color: '#7832C7' }}>Prev</Text>
        </Pressable>
        <Text style={{ fontSize: 12, color: '#888' }}>
          Parity {Math.min(page + 1, pageCount)}/{pageCount} · Text | AnimateableText{isPerturbed ? ' · self-test' : ''}
        </Text>
        <Pressable onPress={() => onPageChange(Math.min(pageCount, page + 1))}>
          <Text style={{ fontSize: 14, color: '#7832C7' }}>Next</Text>
        </Pressable>
      </View>
      {cases.map((item) => (
        <ParityRow
          key={item.id}
          item={item}
          isPerturbed={isPerturbed}
          textRef={(view) => {
            textRefs.current[item.id] = view;
          }}
          animateableRef={(view) => {
            animateableRefs.current[item.id] = view;
          }}
        />
      ))}
    </View>
  );
}
