import * as React from 'react';
import { Text, View } from 'react-native';
import { colors } from './theme';

export function DemoCard({
  title,
  description,
  children,
}: {
  title: string;
  description: string;
  children: React.ReactNode;
}) {
  return (
    <View
      style={{
        backgroundColor: colors.surface,
        borderColor: colors.border,
        borderWidth: 1,
        borderRadius: 16,
        padding: 16,
        gap: 12,
      }}
    >
      <View style={{ gap: 4 }}>
        <Text style={{ fontSize: 16, fontWeight: '600', color: colors.text }}>{title}</Text>
        <Text style={{ fontSize: 13, color: colors.muted }}>{description}</Text>
      </View>
      {children}
    </View>
  );
}
