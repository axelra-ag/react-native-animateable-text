import * as React from 'react';
import { Pressable, Text, View } from 'react-native';
import { colors } from './theme';

export function Segmented<T extends string | number>({
  options,
  value,
  onChange,
  format = String,
}: {
  options: readonly T[];
  value: T;
  onChange: (value: T) => void;
  format?: (value: T) => string;
}) {
  return (
    <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 6 }}>
      {options.map((option) => {
        const isSelected = option === value;
        return (
          <Pressable
            key={String(option)}
            onPress={() => onChange(option)}
            style={{
              paddingVertical: 6,
              paddingHorizontal: 10,
              borderRadius: 8,
              borderWidth: 1,
              borderColor: colors.accent,
              backgroundColor: isSelected ? colors.accent : colors.background,
            }}
          >
            <Text style={{ fontSize: 13, color: isSelected ? colors.accentText : colors.accent }}>
              {format(option)}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}
