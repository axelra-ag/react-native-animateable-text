import * as React from 'react';
import type {
  ContentTransition,
  ContentTransitionDirection,
  ContentTransitionScope,
  ContentTransitionType,
  ContentTransitionUnit,
} from 'react-native-animateable-text';

export const TRANSITIONS: readonly ContentTransitionType[] = ['none', 'numeric', 'roll', 'blur', 'fade'];
// 'default' leaves the option out, so the type's own default applies (roll: up, 200 ms, 30 ms stagger).
export const DIRECTIONS: readonly DirectionChoice[] = ['default', 'auto', 'up', 'down'];
export const DURATIONS: readonly number[] = [0, 200, 300, 800, 1500];
export const DEFAULT_STAGGER = -1;
export const STAGGERS: readonly number[] = [DEFAULT_STAGGER, 0, 30, 60, 120];
export const UNITS: readonly ContentTransitionUnit[] = ['auto', 'word', 'whole'];
export const DEFAULT_MIN_INTERVAL = -1;
export const MIN_INTERVALS: readonly number[] = [DEFAULT_MIN_INTERVAL, 0, 150, 300, 500, 800];
export const DELAYS: readonly number[] = [0, 100, 200, 400];
export const SCOPES: readonly ContentTransitionScope[] = ['all', 'numbers', 'changed'];
export const FONTS = ['system', 'ibm-plex'] as const;

export type FontChoice = (typeof FONTS)[number];
export type DirectionChoice = 'default' | ContentTransitionDirection;

export const FONT_LABELS: Record<FontChoice, string> = {
  system: 'System',
  'ibm-plex': 'IBM Plex Arabic (Malaa)',
};

// Malaa's fonts (src/assets/fonts), one file per weight.
export const IBM_PLEX_FONTS = {
  'IBMPlexSansArabicSAR-Medium': require('../../assets/fonts/IBMPlexSansArabicSAR-Medium.ttf'),
  'IBMPlexSansArabicSAR-Bold': require('../../assets/fonts/IBMPlexSansArabicSAR-Bold.ttf'),
};

export type Settings = {
  transition: ContentTransitionType;
  direction: DirectionChoice;
  /** 0: the type's default. */
  duration: number;
  /** DEFAULT_STAGGER: the type's default. */
  stagger: number;
  unit: ContentTransitionUnit;
  /** roll only. */
  scope: ContentTransitionScope;
  /** roll only. DEFAULT_MIN_INTERVAL: automatic. */
  minInterval: number;
  /** ms between a change and its transition. */
  delay: number;
  font: FontChoice;
};

export const DEFAULT_SETTINGS: Settings = {
  transition: 'numeric',
  direction: 'default',
  duration: 0,
  stagger: DEFAULT_STAGGER,
  unit: 'auto',
  scope: 'all',
  minInterval: DEFAULT_MIN_INTERVAL,
  delay: 0,
  font: 'system',
};

const SettingsContext = React.createContext<Settings>(DEFAULT_SETTINGS);

export const SettingsProvider = SettingsContext.Provider;

export function useContentTransition(): ContentTransition {
  const { transition, direction, duration, unit, stagger, scope, minInterval, delay } =
    React.useContext(SettingsContext);
  return React.useMemo(
    () => ({
      type: transition,
      direction: direction === 'default' ? undefined : direction,
      duration: duration > 0 ? duration : undefined,
      unit,
      stagger: stagger >= 0 ? stagger : undefined,
      scope,
      minInterval: minInterval >= 0 ? minInterval : undefined,
      delay: delay > 0 ? delay : undefined,
    }),
    [transition, direction, duration, unit, stagger, scope, minInterval, delay]
  );
}

/** Settings from a deep link: animateabletext://animated?transition=roll&stagger=30&duration=200… */
export function settingsFromParams(params: Record<string, string>): Settings {
  const pick = <T,>(options: readonly T[], value: unknown, fallback: T) =>
    options.includes(value as T) ? (value as T) : fallback;
  return {
    transition: pick(TRANSITIONS, params.transition, DEFAULT_SETTINGS.transition),
    direction: pick(DIRECTIONS, params.direction, DEFAULT_SETTINGS.direction),
    duration: params.duration != null ? Number(params.duration) || 0 : DEFAULT_SETTINGS.duration,
    stagger: params.stagger != null ? Number(params.stagger) || 0 : DEFAULT_SETTINGS.stagger,
    unit: pick(UNITS, params.unit, DEFAULT_SETTINGS.unit),
    scope: pick(SCOPES, params.scope, DEFAULT_SETTINGS.scope),
    minInterval: params.minInterval != null ? Number(params.minInterval) || 0 : DEFAULT_SETTINGS.minInterval,
    delay: params.delay != null ? Math.max(0, Number(params.delay) || 0) : DEFAULT_SETTINGS.delay,
    font: pick(FONTS, params.font, DEFAULT_SETTINGS.font),
  };
}

/** Font family + weight for the selected font. Custom fonts carry the weight in the file. */
export function useFont(weight: 500 | 700): { fontFamily?: string; fontWeight?: '500' | '700' } {
  const { font } = React.useContext(SettingsContext);
  if (font === 'ibm-plex') {
    return { fontFamily: weight === 700 ? 'IBMPlexSansArabicSAR-Bold' : 'IBMPlexSansArabicSAR-Medium' };
  }
  return { fontWeight: weight === 700 ? '700' : '500' };
}
