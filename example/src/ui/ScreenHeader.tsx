import * as React from 'react';
import { Pressable, Text, View } from 'react-native';
import { BACK_LINK_TOP, colors, GUTTER, HEADER_TOP } from './theme';

export function ScreenHeader({ title, onBack }: { title: string; onBack?: () => void }) {
  return (
    <View style={{ paddingTop: HEADER_TOP, paddingHorizontal: GUTTER, paddingBottom: 12 }}>
      {onBack ? (
        <Pressable onPress={onBack} hitSlop={12} style={{ position: 'absolute', top: BACK_LINK_TOP, left: GUTTER }}>
          <Text style={{ fontSize: 15, color: colors.accent }}>‹ Sections</Text>
        </Pressable>
      ) : null}
      <Text style={{ fontSize: 28, fontWeight: '700', color: colors.text }}>{title}</Text>
    </View>
  );
}
