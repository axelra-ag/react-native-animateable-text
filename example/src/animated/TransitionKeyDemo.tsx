import * as React from 'react';
import { Text, View } from 'react-native';
import AnimateableText, { type ContentTransition, type Reveal } from 'react-native-animateable-text';
import { useAnimatedProps, useSharedValue } from 'react-native-reanimated';
import { Button } from '../ui/Button';
import { DemoCard } from '../ui/DemoCard';
import { Segmented } from '../ui/Segmented';
import { colors } from '../ui/theme';
import { formatAmount } from './formatUtils';
import { useFont } from './settings';

// Malaa's shared money config.
const MONEY = { type: 'roll', stagger: 40, duration: 300 } as const;
export const MONEY_TRANSITION: ContentTransition = MONEY;
const MONEY_REVEAL: Reveal = MONEY;
const MONEY_STAGGER = 40;
// The library caps a change's stagger spread at 300 ms by shortening the step.
const MAX_STAGGER_SPREAD = 300;

/**
 * Malaa's split amount: the decimals node starts where the integer node's wave ends, so both
 * read as one left-to-right wave. `positions`: how many positions the integer's wave has.
 */
export function followingDelay(positions: number, stagger = MONEY_STAGGER) {
  if (positions <= 0) return 0;
  const step = positions > 1 ? Math.min(stagger, MAX_STAGGER_SPREAD / (positions - 1)) : stagger;
  return Math.round(positions * step);
}

const MASK = '*****';
const AMOUNTS = [1234.55, 1240.1, 987.65, 15374.3, 1234.55];
const CURRENCIES = ['SAR', 'USD', 'EUR'] as const;
type Currency = (typeof CURRENCIES)[number];
const RATES: Record<Currency, number> = { SAR: 1, USD: 0.2666, EUR: 0.2459 };

function integerPart(value: number) {
  'worklet';
  return formatAmount(Math.trunc(value), 0);
}

function decimalPart(value: number) {
  'worklet';
  return `.${Math.abs(value).toFixed(2).split('.')[1]}`;
}

/** Hide/show with `transitionKey`. Steps for `autorun=1`, one every STEP_MS. */
const STEP_MS = 1300;
const AUTORUN_STEPS = [
  'hide',
  'show',
  'change',
  'roll value',
  'roll twice',
  'remount',
  'key-then-text hide',
  'key-then-text show',
  'currency',
  'change',
  'currency',
] as const;
type Step = (typeof AUTORUN_STEPS)[number];

/**
 * Hide/show with `transitionKey`, like Malaa's balances: the change that comes with a new key
 * (hide / show / currency) animates; value changes while the key stays the same swap instantly.
 */
export function TransitionKeyDemo({ autorun = false }: { autorun?: boolean }) {
  const [amountIndex, setAmountIndex] = React.useState(0);
  // Bumped by "Roll value": part of rows 2 and 4's key, so that value change rolls.
  const [rollEpoch, setRollEpoch] = React.useState(0);
  // Bumped by "Remount": rows 2 and 4 mount again (reveal).
  const [mountEpoch, setMountEpoch] = React.useState(0);
  // Usually equal. "Key, then text" sets the key first and the text one commit later.
  const [keyHidden, setKeyHidden] = React.useState(false);
  const [textHidden, setTextHidden] = React.useState(false);
  const [currency, setCurrency] = React.useState<Currency>('SAR');
  const [lastStep, setLastStep] = React.useState<string>('—');

  // The same state on the UI thread, for the useAnimatedProps path.
  const amountValue = useSharedValue(AMOUNTS[0]);
  const keyHiddenValue = useSharedValue(false);
  const textHiddenValue = useSharedValue(false);
  const rollEpochValue = useSharedValue(0);

  const amount = AMOUNTS[amountIndex % AMOUNTS.length];
  const font = useFont(700);
  const style = [font, { fontSize: 30, color: colors.text, fontVariant: ['tabular-nums' as const] }];

  const integerProps = useAnimatedProps(() => ({
    text: textHiddenValue.value ? MASK : integerPart(amountValue.value),
    transitionKey: [keyHiddenValue.value, rollEpochValue.value],
  }));
  const decimalProps = useAnimatedProps(() => ({
    // Malaa's AnimatedMoneyText: the decimals node becomes empty while hidden.
    text: textHiddenValue.value ? '' : decimalPart(amountValue.value),
    transitionKey: [keyHiddenValue.value, rollEpochValue.value],
  }));
  // The decimals start after the integer's wave: its positions are the longer of the shown
  // integer and the mask (a change has as many positions as its longer text).
  const integerLength = integerPart(amount).length;
  const decimalDelay = followingDelay(Math.max(integerLength, MASK.length));
  const decimalTransition: ContentTransition = { ...MONEY, delay: decimalDelay };
  const decimalReveal: Reveal = { ...MONEY, delay: followingDelay(textHidden ? MASK.length : integerLength) };
  const decimalStyle = [font, { fontSize: 20, color: colors.muted }];

  const setHidden = (isHidden: boolean) => {
    // Same React render for the props path; same UI-thread update for the animated path.
    setKeyHidden(isHidden);
    setTextHidden(isHidden);
    keyHiddenValue.value = isHidden;
    textHiddenValue.value = isHidden;
  };
  const setHiddenKeyFirst = (isHidden: boolean) => {
    setKeyHidden(isHidden);
    keyHiddenValue.value = isHidden;
    setTimeout(() => {
      setTextHidden(isHidden);
      textHiddenValue.value = isHidden;
    }, 150);
  };
  const changeValue = () => {
    const next = amountIndex + 1;
    setAmountIndex(next);
    amountValue.value = AMOUNTS[next % AMOUNTS.length];
  };
  // A value change that comes with a new key (rows 2 and 4), so it rolls.
  const rollValue = () => {
    changeValue();
    setRollEpoch((epoch) => epoch + 1);
    rollEpochValue.value += 1;
  };
  const nextCurrency = () => setCurrency(CURRENCIES[(CURRENCIES.indexOf(currency) + 1) % CURRENCIES.length]);

  const runStep = (step: Step) => {
    setLastStep(step);
    if (step === 'hide') setHidden(true);
    else if (step === 'show') setHidden(false);
    else if (step === 'change') changeValue();
    else if (step === 'roll value') rollValue();
    else if (step === 'roll twice') {
      // The second change arrives while row 4 (and row 2's decimals) are still in their delay.
      rollValue();
      setTimeout(() => runStepRef.current('roll value'), 120);
    } else if (step === 'remount') setMountEpoch((epoch) => epoch + 1);
    else if (step === 'key-then-text hide') setHiddenKeyFirst(true);
    else if (step === 'key-then-text show') setHiddenKeyFirst(false);
    else nextCurrency();
  };
  const runStepRef = React.useRef(runStep);
  runStepRef.current = runStep;
  React.useEffect(() => {
    if (!autorun) return;
    let index = 0;
    const timer = setInterval(() => {
      runStepRef.current(AUTORUN_STEPS[index % AUTORUN_STEPS.length]);
      index += 1;
    }, STEP_MS);
    return () => clearInterval(timer);
  }, [autorun]);

  const [isAutoChanging, setIsAutoChanging] = React.useState(false);
  React.useEffect(() => {
    if (!isAutoChanging) return;
    const timer = setInterval(() => runStepRef.current('change'), 600);
    return () => clearInterval(timer);
  }, [isAutoChanging]);

  return (
    <DemoCard
      title="transitionKey: hide / show"
      description={
        "contentTransition { type: 'roll', stagger: 40, duration: 300 } with transitionKey: only text changes that come with a new key roll (hide, show, currency); value changes while visible swap instantly. 1: text + transitionKey props. 2: integer and decimals from useAnimatedProps returning { text, transitionKey } (decimals → '' while hidden). 3: transitionKey={[isHidden, currency]}. 2a/2b: Malaa's split amount with and without the decimals' delay. 4: delay 300 ms (a change during the delay re-aims it, keeping its start)."
      }
    >
      <Label>1 · props: text + transitionKey={'{isHidden}'}</Label>
      <AnimateableText
        style={style}
        text={textHidden ? MASK : formatAmount(amount)}
        transitionKey={keyHidden}
        contentTransition={MONEY_TRANSITION}
      />
      <Label>
        2a · useAnimatedProps: integer + decimals, decimals delay {decimalDelay} ms (one wave)
      </Label>
      <View key={`2a-${mountEpoch}`} style={{ flexDirection: 'row', alignItems: 'baseline' }}>
        <AnimateableText
          style={style}
          animatedProps={integerProps}
          contentTransition={MONEY_TRANSITION}
          reveal={MONEY_REVEAL}
        />
        <AnimateableText
          style={decimalStyle}
          animatedProps={decimalProps}
          contentTransition={decimalTransition}
          reveal={decimalReveal}
        />
      </View>
      <Label>2b · same, no delay</Label>
      <View key={`2b-${mountEpoch}`} style={{ flexDirection: 'row', alignItems: 'baseline' }}>
        <AnimateableText
          style={style}
          animatedProps={integerProps}
          contentTransition={MONEY_TRANSITION}
          reveal={MONEY_REVEAL}
        />
        <AnimateableText
          style={decimalStyle}
          animatedProps={decimalProps}
          contentTransition={MONEY_TRANSITION}
          reveal={MONEY_REVEAL}
        />
      </View>
      <Label>3 · transitionKey={'{[isHidden, currency]}'}</Label>
      <AnimateableText
        style={style}
        text={textHidden ? MASK : `${currency} ${formatAmount(amount * RATES[currency])}`}
        transitionKey={[keyHidden, currency]}
        contentTransition={MONEY_TRANSITION}
      />
      <Label>4 · props: contentTransition delay 300 ms, transitionKey={'{[isHidden, rollEpoch]}'}</Label>
      <AnimateableText
        key={`4-${mountEpoch}`}
        style={style}
        text={textHidden ? MASK : formatAmount(amount)}
        transitionKey={[keyHidden, rollEpoch]}
        contentTransition={{ ...MONEY, delay: 300 }}
        reveal={{ ...MONEY, delay: 300 }}
      />
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
        <Button title={keyHidden ? 'Show' : 'Hide'} onPress={() => runStep(keyHidden ? 'show' : 'hide')} />
        <Button title="Change value" isSecondary onPress={() => runStep('change')} />
        <Button title="Roll value (2, 4)" isSecondary onPress={() => runStep('roll value')} />
        <Button title="Roll twice, 120 ms apart" isSecondary onPress={() => runStep('roll twice')} />
        <Button title="Remount (reveal 2, 4)" isSecondary onPress={() => runStep('remount')} />
        <Button
          title={isAutoChanging ? 'Stop changes' : 'Auto change'}
          isSecondary
          onPress={() => setIsAutoChanging(!isAutoChanging)}
        />
        <Button
          title={keyHidden ? 'Show: key, then text' : 'Hide: key, then text'}
          isSecondary
          onPress={() => runStep(keyHidden ? 'key-then-text show' : 'key-then-text hide')}
        />
      </View>
      <Segmented options={CURRENCIES} value={currency} onChange={setCurrency} />
      <Text style={{ fontSize: 12, color: colors.muted }}>Last step: {lastStep}</Text>
    </DemoCard>
  );
}

function Label({ children }: { children: React.ReactNode }) {
  return <Text style={{ fontSize: 12, color: colors.muted }}>{children}</Text>;
}
