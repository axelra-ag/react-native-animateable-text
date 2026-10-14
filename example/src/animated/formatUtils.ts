// Worklets: these run on the UI thread inside useAnimatedProps.

export function formatAmount(value: number, decimals = 2) {
  'worklet';
  const [integer, fraction] = Math.abs(value).toFixed(decimals).split('.');
  let grouped = '';
  for (let i = 0; i < integer.length; i += 1) {
    if (i > 0 && (integer.length - i) % 3 === 0) grouped += ',';
    grouped += integer[i];
  }
  return `${value < 0 ? '-' : ''}${grouped}${fraction ? `.${fraction}` : ''}`;
}

export function toArabicDigits(text: string) {
  'worklet';
  let out = '';
  for (const char of text) {
    if (char >= '0' && char <= '9') out += String.fromCharCode(0x0660 + Number(char));
    else if (char === ',') out += '٬';
    else if (char === '.') out += '٫';
    else out += char;
  }
  return out;
}

export function formatStopwatch(ms: number) {
  'worklet';
  const totalCentiseconds = Math.floor(ms / 10);
  const minutes = Math.floor(totalCentiseconds / 6000);
  const seconds = Math.floor(totalCentiseconds / 100) % 60;
  const centiseconds = totalCentiseconds % 100;
  const pad = (n: number) => (n < 10 ? `0${n}` : `${n}`);
  return `${pad(minutes)}:${pad(seconds)}.${pad(centiseconds)}`;
}
