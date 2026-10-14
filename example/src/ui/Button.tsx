import * as React from 'react';
import { Pressable, Text } from 'react-native';
import { colors } from './theme';

export function Button({
  title,
  onPress,
  isSecondary = false,
}: {
  title: string;
  onPress: () => void;
  isSecondary?: boolean;
}) {
  return (
    <Pressable
      onPress={onPress}
      style={({ pressed }) => ({
        paddingVertical: 8,
        paddingHorizontal: 14,
        borderRadius: 10,
        backgroundColor: isSecondary ? colors.background : colors.accent,
        borderWidth: 1,
        borderColor: colors.accent,
        opacity: pressed ? 0.7 : 1,
      })}
    >
      <Text style={{ fontSize: 14, fontWeight: '600', color: isSecondary ? colors.accent : colors.accentText }}>
        {title}
      </Text>
    </Pressable>
  );
}
