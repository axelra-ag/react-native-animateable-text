import { NativeModules } from 'react-native';
const Input = require('./Input');
type ReanimatedTextType = {
  multiply(a: number, b: number): Promise<number>;
};

const { ReanimatedText } = NativeModules;

export const AnimatedInput = Input;

export default ReanimatedText as ReanimatedTextType;
