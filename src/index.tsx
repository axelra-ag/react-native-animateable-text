import { NativeModules } from 'react-native';
export const AnimatedText = require('./AnimatedText');
type ReanimatedTextType = {
  multiply(a: number, b: number): Promise<number>;
};

const { ReanimatedText } = NativeModules;

export default ReanimatedText as ReanimatedTextType;
