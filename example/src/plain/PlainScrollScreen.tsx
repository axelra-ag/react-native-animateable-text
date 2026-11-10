import * as React from 'react';
import { ScrollView, Text, View } from 'react-native';
import { ScreenHeader } from '../ui/ScreenHeader';
import { colors, GUTTER } from '../ui/theme';

// Control screen for scroll performance: only Views and RN <Text>, no AnimateableText or
// Reanimated. Roughly as dense as the Animated text screen, so the two can be compared.
const CARD_COUNT = 60;
const PALETTE = ['#E8DEF8', '#FBE3CC', '#D4EDDA', '#D6E4FF', '#FAD4D8', '#E3F2F1'];
const CHIP_COLORS = ['#7832C7', '#1F8A4C', '#C2362F', '#2F6FD6'];

function PlainCard({ index }: { index: number }) {
  const background = PALETTE[index % PALETTE.length];
  return (
    <View style={{ backgroundColor: background, borderRadius: 16, overflow: 'hidden' }}>
      <View style={{ height: 8, backgroundColor: CHIP_COLORS[index % CHIP_COLORS.length] }} />
      <View style={{ padding: 16, gap: 8 }}>
        <Text style={{ fontSize: 17, fontWeight: '600', color: colors.text }}>Card {index + 1}</Text>
        <Text style={{ fontSize: 13, color: colors.muted }}>
          Plain text only. Scroll this screen the same way as the Animated text screen and compare the UI
          frame rate in the performance monitor.
        </Text>
        <Text style={{ fontSize: 34, fontWeight: '700', color: colors.text }}>
          SAR {(index * 1234.5).toFixed(2)}
        </Text>
        <View style={{ flexDirection: 'row', gap: 8 }}>
          {CHIP_COLORS.map((chipColor) => (
            <View
              key={chipColor}
              style={{ backgroundColor: chipColor, borderRadius: 8, paddingHorizontal: 12, paddingVertical: 6 }}
            >
              <Text style={{ fontSize: 13, fontWeight: '600', color: colors.accentText }}>Chip</Text>
            </View>
          ))}
        </View>
      </View>
    </View>
  );
}

export function PlainScrollScreen({ onBack }: { onBack: () => void }) {
  return (
    <View style={{ flex: 1, backgroundColor: colors.background }}>
      <ScreenHeader title="Plain scroll" onBack={onBack} />
      <ScrollView contentContainerStyle={{ paddingHorizontal: GUTTER, paddingBottom: 48, gap: 16 }}>
        {Array.from({ length: CARD_COUNT }, (_, index) => (
          <PlainCard key={index} index={index} />
        ))}
      </ScrollView>
    </View>
  );
}
