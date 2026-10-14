import * as React from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { PARITY_CASES } from '../parity/parityCases';
import { ScreenHeader } from '../ui/ScreenHeader';
import { colors, GUTTER } from '../ui/theme';

type Section = {
  id: 'animated' | 'parity';
  title: string;
  description: string;
};

const SECTIONS: Section[] = [
  {
    id: 'animated',
    title: 'Animated text',
    description:
      'Shared values and plain props driving the text, with contentTransition (none, numeric, blur, fade), direction, duration and font switches.',
  },
  {
    id: 'parity',
    title: 'Parity: Text vs AnimateableText',
    description: `${PARITY_CASES.length} cases rendered side by side. scripts/parity.mjs pixel-diffs them from device screenshots.`,
  },
];

function SectionRow({ section, onPress }: { section: Section; onPress: () => void }) {
  return (
    <Pressable
      onPress={onPress}
      style={({ pressed }) => ({
        backgroundColor: colors.surface,
        borderColor: colors.border,
        borderWidth: 1,
        borderRadius: 16,
        padding: 16,
        gap: 4,
        opacity: pressed ? 0.7 : 1,
      })}
    >
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
        <Text style={{ fontSize: 17, fontWeight: '600', color: colors.text }}>{section.title}</Text>
        <Text style={{ fontSize: 20, color: colors.accent }}>›</Text>
      </View>
      <Text style={{ fontSize: 13, color: colors.muted }}>{section.description}</Text>
    </Pressable>
  );
}

export function HomeScreen({ onOpen }: { onOpen: (id: Section['id']) => void }) {
  return (
    <View style={{ flex: 1, backgroundColor: colors.background }}>
      <ScreenHeader title="Animateable Text" />
      <ScrollView contentContainerStyle={{ paddingHorizontal: GUTTER, paddingBottom: 48, gap: 12 }}>
        {SECTIONS.map((section) => (
          <SectionRow key={section.id} section={section} onPress={() => onOpen(section.id)} />
        ))}
      </ScrollView>
    </View>
  );
}
