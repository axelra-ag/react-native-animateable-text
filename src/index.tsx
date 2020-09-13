import { NativeModules } from 'react-native';

type ReanimatedTextType = {
  multiply(a: number, b: number): Promise<number>;
};

const { ReanimatedText } = NativeModules;

export default ReanimatedText as ReanimatedTextType;
