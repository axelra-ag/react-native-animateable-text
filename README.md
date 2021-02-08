# react-native-animateable-text

A fork of React Native's `<Text/>` component that supports Animated Values as text!

## Compatibility

<table>
  <tr>
    <th>Animateable Text Version</th>
    <th>RN Version</th>
  </tr>
  <tr>
    <td> >= 0.6.0 </td>
    <td> >= 0.64</td>
  </tr>
  <tr>
    <td> >= 0.5.9</td>
    <td> >= 0.63</td>
  </tr>
</table>

## Installation

```sh
yarn add react-native-animateable-text
npx pod-install
```

## Usage (Reanimated 2)

> Note about Reanimated 2: The library does not work with Alpha 9 until RC1. Make sure to update to RC2 or later!

Use it the same as a `<Text/>` component, except instead of passing the text as a child node, pass it using the `text` props.

```tsx
import AnimateableText from 'react-native-animateable-text';

const Example: React.FC = () => {
  const text = useSharedValue('World');

  const animatedText = useDerivedValue(() => `Hello ${text.value}`);
  const animatedProps = useAnimatedProps(() => {
    return {
      text: animatedText.value,
    };
  });

  return (
    <AnimateableText
      animatedProps={animatedProps}
      // same other props as Text component
    />;
};
```

## Usage (Reanimated 1)

```tsx
import AnimateableText from 'react-native-animateable-text';

const Example: React.FC = () => {
  const text = useMemo(() => new Animated.Value('World'), []);

  const animatedText = useMemo(() => concat('Hello', text));

  return (
    <AnimateableText
      text={animatedText}
      // same other props as Text component
    />;
};
```

## [OMG, why would you build this?](https://www.npmjs.com/package/react-native-reanimated/v/1.4.0#omg-why-would-you-build-this-motivation)

We want to animate numbers based on gestures as fast as possible, for example for charts displaying financial data. Updating native state is too slow and not feasible for a smooth experience. Using `createAnimatedComponent` doesn't allow you to animate the text since the children of Text are separate nodes rather than just props.

The best way so far has been to use the `<ReText>` component from [react-native-redash](https://wcandillon.github.io/react-native-redash-v1-docs/strings#retext), which allows you to render a string from a Reanimated Text node. However, under the hood, it uses a `<TextInput/>` and animates it's `value` prop.

This naturally comes with a few edge cases, for example:

<ul>

<li>
Flicker: When changing values too fast, the text can be cut off and show an ellipsis. The problem gets worse the slower the device and the more congested the render queue is. Watch this GIF at 0.2x speed carefully: <br/>
<img src="https://user-images.githubusercontent.com/1629785/99287990-458d4600-283b-11eb-8d5e-0c1129189c89.gif"/>

</li>
<li>
Inconsistent styling: When styling a <code>TextInput</code>, you need to add more styles to make it align with the rest of your text. (Behavior in screenshot happens only on Android)
</li> <img src="https://user-images.githubusercontent.com/1629785/99298147-8c823800-2849-11eb-9939-e326dd8d9f25.png" width="388"/> <br/>

<li>
Lack of full capabilities: Not all props are available. With Animateable Text, you can use props that you cannot use on a TextInput, such as <code>selectable</code>, <code>dataDetectorType</code> or <code>onTextLayout</code>.

<img src="https://user-images.githubusercontent.com/1629785/99299532-a15fcb00-284b-11eb-83d2-d3601825a80a.png" width="388">
</li>
</ul>

## Contributing

See the [contributing guide](CONTRIBUTING.md) to learn how to contribute to the repository and the development workflow.

## Credits

Written by [Jonny Burger](https://jonny.io) for our needs at [Axelra](https://axelra.com).

Thanks to Axelra for sponsoring my time to turn this into an open source project!

<br/>
<img src="https://user-images.githubusercontent.com/1629785/99300604-1ed80b00-284d-11eb-9887-9fb0832ef8de.png" width="150"> <br>
<sub>We are a Swiss Agency specializing in React Native, caring even about the smallest of details.</sub>

## License

MIT
