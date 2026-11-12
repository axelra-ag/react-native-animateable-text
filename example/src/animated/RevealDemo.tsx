import * as React from 'react';
import { Text, View } from 'react-native';
import AnimateableText, { type Reveal, type RevealType } from 'react-native-animateable-text';
import { useAnimatedProps, useSharedValue } from 'react-native-reanimated';
import { Button } from '../ui/Button';
import { DemoCard } from '../ui/DemoCard';
import { Segmented } from '../ui/Segmented';
import { colors } from '../ui/theme';
import { formatAmount } from './formatUtils';
import { useFont } from './settings';
import { MONEY_TRANSITION } from './TransitionKeyDemo';

export const REVEAL_TYPES: readonly Exclude<RevealType, 'none'>[] = ['roll', 'numeric', 'blur', 'fade'];
export const REVEAL_DELAYS: readonly number[] = [0, 300, 800];

type Mount = 'loaded' | 'empty-then-load' | 'update-right-after';

const AMOUNT = 12345.67;
const UPDATED_AMOUNT = 12399.1;
const LOAD_DELAY_MS = 700;
const UPDATE_DELAY_MS = 100;

/** Mounted fresh on every "Remount" (keyed by the parent). */
function RevealedTexts({ mount, reveal }: { mount: Mount; reveal: Reveal }) {
  const font = useFont(700);
  const [text, setText] = React.useState(mount === 'empty-then-load' ? '' : `SAR ${formatAmount(AMOUNT)}`);
  const value = useSharedValue(mount === 'empty-then-load' ? -1 : AMOUNT);
  const animatedProps = useAnimatedProps(() => ({ text: value.value < 0 ? '' : formatAmount(value.value) }));
  React.useEffect(() => {
    if (mount === 'loaded') return;
    const delay = mount === 'empty-then-load' ? LOAD_DELAY_MS : UPDATE_DELAY_MS;
    const next = mount === 'empty-then-load' ? AMOUNT : UPDATED_AMOUNT;
    const timer = setTimeout(() => {
      setText(`SAR ${formatAmount(next)}`);
      value.value = next;
    }, delay);
    return () => clearTimeout(timer);
  }, [mount, value]);
  return (
    <View style={{ gap: 8, minHeight: 120 }}>
      <AnimateableText
        style={[font, { fontSize: 30, color: colors.text, fontVariant: ['tabular-nums'] }]}
        text={text}
        reveal={reveal}
        contentTransition={MONEY_TRANSITION}
      />
      <AnimateableText
        style={[font, { fontSize: 30, color: colors.accent, fontVariant: ['tabular-nums'] }]}
        animatedProps={animatedProps}
        reveal={reveal}
        contentTransition={MONEY_TRANSITION}
      />
      <AnimateableText
        style={[font, { fontSize: 24, color: colors.text, writingDirection: 'rtl', textAlign: 'right' }]}
        text={text === '' ? '' : 'الرصيد المتاح'}
        reveal={reveal}
      />
    </View>
  );
}

const AUTORUN_MOUNTS: readonly Mount[] = ['loaded', 'empty-then-load', 'update-right-after'];

/**
 * `reveal`: runs once per mounted view, the first time it shows non-empty text. `params`
 * (deep link): reveal (type), revealDelay (ms); autorun=1 cycles the three mounts.
 */
export function RevealDemo({ autorun = false, params = {} }: { autorun?: boolean; params?: Record<string, string> }) {
  const [type, setType] = React.useState<Exclude<RevealType, 'none'>>(
    REVEAL_TYPES.includes(params.reveal as never) ? (params.reveal as Exclude<RevealType, 'none'>) : 'roll'
  );
  const [delay, setDelay] = React.useState(params.revealDelay != null ? Number(params.revealDelay) || 0 : 0);
  const [mount, setMount] = React.useState<{ id: number; mount: Mount }>({ id: 0, mount: 'loaded' });
  const remount = (next: Mount) => setMount((current) => ({ id: current.id + 1, mount: next }));

  React.useEffect(() => {
    if (!autorun) return;
    let index = 0;
    const timer = setInterval(() => {
      remount(AUTORUN_MOUNTS[index % AUTORUN_MOUNTS.length]);
      index += 1;
    }, 2200);
    return () => clearInterval(timer);
  }, [autorun]);

  const reveal = React.useMemo<Reveal>(() => ({ type, delay }), [type, delay]);
  return (
    <DemoCard
      title="reveal"
      description="Animates the text in the first time a mounted view shows non-empty text (on mount, or on the first '' → text). Later changes use contentTransition (roll here). Lines: text prop, useAnimatedProps, Arabic (per word)."
    >
      <RevealedTexts key={mount.id} mount={mount.mount} reveal={reveal} />
      <Segmented options={REVEAL_TYPES} value={type} onChange={setType} />
      <Segmented
        options={REVEAL_DELAYS}
        value={delay}
        onChange={setDelay}
        format={(value) => (value === 0 ? 'no delay' : `delay ${value} ms`)}
      />
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
        <Button title="Remount" onPress={() => remount('loaded')} />
        <Button title="Mount empty, then load" isSecondary onPress={() => remount('empty-then-load')} />
        <Button title="Remount + update at 100 ms" isSecondary onPress={() => remount('update-right-after')} />
      </View>
      <Text style={{ fontSize: 12, color: colors.muted }}>
        Mount #{mount.id}: {mount.mount}
      </Text>
    </DemoCard>
  );
}
