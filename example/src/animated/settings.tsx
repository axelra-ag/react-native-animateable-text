import * as React from 'react';
import type { TextStyle } from 'react-native';
import type {
  ContentTransition,
  ContentTransitionDirection,
  ContentTransitionType,
} from 'react-native-animateable-text';

export const TRANSITIONS: readonly ContentTransitionType[] = ['none', 'numeric', 'blur', 'fade'];
export const DIRECTIONS: readonly ContentTransitionDirection[] = ['auto', 'up', 'down'];
export const DURATIONS: readonly number[] = [0, 300, 800, 1500];
export const FONTS = ['system', 'ibm-plex'] as const;

export type FontChoice = (typeof FONTS)[number];

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
  direction: ContentTransitionDirection;
  duration: number;
  font: FontChoice;
};

export const DEFAULT_SETTINGS: Settings = {
  transition: 'numeric',
  direction: 'auto',
  duration: 0,
  font: 'system',
};

const SettingsContext = React.createContext<Settings>(DEFAULT_SETTINGS);

export const SettingsProvider = SettingsContext.Provider;

export function useContentTransition(): ContentTransition {
  const { transition, direction, duration } = React.useContext(SettingsContext);
  return { type: transition, direction, duration };
}

/** Font family + weight for the selected font. Custom fonts carry the weight in the file. */
export function useFont(weight: 500 | 700): TextStyle {
  const { font } = React.useContext(SettingsContext);
  if (font === 'ibm-plex') {
    return { fontFamily: weight === 700 ? 'IBMPlexSansArabicSAR-Bold' : 'IBMPlexSansArabicSAR-Medium' };
  }
  return { fontWeight: weight === 700 ? '700' : '500' };
}
