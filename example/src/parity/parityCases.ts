import type { TextProps, TextStyle } from 'react-native';
import type { ContentTransitionType, ContentTransitionUnit } from 'react-native-animateable-text';

export type ParityCase = {
  id: string;
  text: string;
  style?: TextStyle;
  props?: Pick<
    TextProps,
    'numberOfLines' | 'ellipsizeMode' | 'adjustsFontSizeToFit' | 'minimumFontScale'
  >;
  useAnimatedProps?: boolean;
  /** AnimateableText starts on `from` and transitions to `text`; measured after it settles. */
  transition?: { from: string; type: ContentTransitionType; unit?: ContentTransitionUnit };
};

const EN_PARAGRAPH =
  'The quick brown fox jumps over the lazy dog while the balance moves from 1,204.50 to 98,310.75 SAR.';
const AR_PARAGRAPH =
  'تحرك الرصيد من ١٬٢٠٤٫٥٠ إلى ٩٨٬٣١٠٫٧٥ ريال خلال الشهر، مع زيادة في الادخار وانخفاض في المصروفات.';

export const PARITY_CASES: ParityCase[] = [
  { id: 'en-basic', text: 'Hello world 123', style: { fontSize: 17 } },
  { id: 'en-bold-number', text: '1,234,567.89', style: { fontSize: 30, fontWeight: '700' } },
  { id: 'ar-basic', text: 'مرحبا بالعالم', style: { fontSize: 20 } },
  { id: 'ar-digits', text: '١٬٢٣٤٫٥٦ ر.س', style: { fontSize: 22, fontWeight: '600' } },
  { id: 'mixed-bidi', text: 'SAR 1,234.50 ريال', style: { fontSize: 18 } },
  { id: 'align-center', text: 'Centered', style: { fontSize: 18, textAlign: 'center' } },
  { id: 'align-right', text: 'Right', style: { fontSize: 18, textAlign: 'right' } },
  {
    id: 'ar-rtl-right',
    text: 'محاذاة لليمين',
    style: { fontSize: 18, writingDirection: 'rtl', textAlign: 'right' },
  },
  { id: 'tabular-nums', text: '111,111.11', style: { fontSize: 22, fontVariant: ['tabular-nums'] } },
  { id: 'letter-spacing', text: 'SPACED 42', style: { fontSize: 16, letterSpacing: 2.5 } },
  { id: 'italic-light', text: 'Light italic', style: { fontSize: 18, fontWeight: '300', fontStyle: 'italic' } },
  { id: 'decorated', text: 'Underlined', style: { fontSize: 18, textDecorationLine: 'underline', color: '#7832C7' } },
  {
    id: 'shadow',
    text: 'Shadow',
    style: { fontSize: 20, textShadowColor: '#00000066', textShadowOffset: { width: 1, height: 2 }, textShadowRadius: 2 },
  },
  { id: 'en-multiline', text: EN_PARAGRAPH, style: { fontSize: 15 } },
  { id: 'en-line-height', text: EN_PARAGRAPH, style: { fontSize: 15, lineHeight: 26 } },
  { id: 'ar-multiline', text: AR_PARAGRAPH, style: { fontSize: 15, writingDirection: 'rtl' } },
  { id: 'lines-1-tail', text: EN_PARAGRAPH, style: { fontSize: 15 }, props: { numberOfLines: 1 } },
  {
    id: 'lines-2-middle',
    text: EN_PARAGRAPH,
    style: { fontSize: 15 },
    props: { numberOfLines: 2, ellipsizeMode: 'middle' },
  },
  {
    id: 'adjusts-font-size',
    text: 'A long balance label 1,234,567.89 SAR',
    style: { fontSize: 24 },
    props: { numberOfLines: 1, adjustsFontSizeToFit: true, minimumFontScale: 0.4 },
  },
  { id: 'padding-bg', text: 'Padded', style: { fontSize: 18, padding: 6, backgroundColor: '#F1E9FA' } },
  { id: 'animated-props', text: '98,310.75', style: { fontSize: 26, fontWeight: '700' }, useAnimatedProps: true },
  {
    id: 'transition-numeric',
    text: 'SAR 98,310.75',
    style: { fontSize: 22, fontWeight: '700' },
    transition: { from: 'SAR 1,204.50', type: 'numeric' },
  },
  {
    id: 'transition-blur-ar',
    text: 'مكتمل',
    style: { fontSize: 20, writingDirection: 'rtl', textAlign: 'right' },
    transition: { from: 'قيد الانتظار', type: 'blur' },
  },
  {
    id: 'transition-blur-whole',
    text: 'Payment completed',
    style: { fontSize: 18 },
    transition: { from: 'Payment pending', type: 'blur', unit: 'whole' },
  },
  {
    id: 'transition-fade-multiline',
    text: EN_PARAGRAPH,
    style: { fontSize: 15 },
    transition: { from: 'Short line.', type: 'fade' },
  },
];
