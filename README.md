# react-native-animateable-text

A fork of React Native's `<Text/>` component that supports Reanimated Shared Values as text!

## Compatibility 
(🚨 Make sure you use the correct version for your RN project)
<table>
  <tr>
    <th>Animateable Text Version</th>
    <th>RN Version</th>
    <th>Old Arch</th>
    <th>New Arch (Fabric)</th>
  </tr>
    <tr>
    <td> ^0.15.0</td>
    <td> ^0.77.0</td>
    <td>✅</td>
    <td>✅</td>
  </tr>
  <tr>
    <td> ^0.14.2</td>
    <td> ^0.76.0</td>
    <td>✅</td>
    <td>✅</td>
  </tr>
  <tr>
    <td> ^0.13.0</td>
    <td> ^0.75.0</td>
    <td>✅</td>
    <td>🛑</td>
  </tr>
  <tr>
    <td> ^0.12.0</td>
    <td> ^0.74.0</td>
    <td>✅</td>
    <td>🛑</td>
  </tr>
  <tr>
    <td> ^0.11.0</td>
    <td> ^0.71.7</td>
    <td>✅</td>
    <td>🛑</td>
  </tr>
  <tr>
    <td> ^0.10.0</td>
    <td> ^0.68</td>
    <td>✅</td>
    <td>🛑</td>
  </tr>
  <tr>
    <td> ^0.9.1</td>
    <td> ^0.67</td>
    <td>✅</td>
    <td>🛑</td>
  </tr>
  <tr>
    <td> ^0.8.0</td>
    <td> ^0.66</td>
    <td>✅</td>
    <td>🛑</td>
  </tr>
  <tr>
    <td> ^0.7.0</td>
    <td> ^0.65</td>
    <td>✅</td>
    <td>🛑</td>
  </tr>
  <tr>
    <td> ^0.6.0</td>
    <td> ^0.64</td>
    <td>✅</td>
    <td>🛑</td>
  </tr>
  <tr>
    <td> ^0.5.9</td>
    <td> ^0.63</td>
    <td>✅</td>
    <td>🛑</td>
  </tr>
</table>

## Installation

First make sure you have reanimated already installed and linked from [here](https://docs.swmansion.com/react-native-reanimated/docs/fundamentals/getting-started/) then run
```sh
yarn add react-native-animateable-text
```

then for *Expo* managed projects you need to prebuild your project, and for *ReactNative* bare projects you should run
```sh
npx pod-install
```



## Usage (Reanimated 2/3)

> Note about Reanimated 2: The library does not work with Alpha 9 until RC1. Make sure to update to RC2 or later!

Use it the same as a `<Text/>` component, except instead of passing the text as a child node, pass it using the `text` props.

```tsx
import AnimateableText from 'react-native-animateable-text';

const Example: React.FC = () => {
  const reanimatedText = useSharedValue('World');

  const animatedProps = useAnimatedProps(() => {
    return {
      text: reanimatedText.value,
    };
  });

  return (
    <AnimateableText
      animatedProps={animatedProps}
      // all other <Text /> props are also available
    />;
};
```


## [OMG, why would you build this?](https://www.npmjs.com/package/react-native-reanimated/v/1.4.0#omg-why-would-you-build-this-motivation)

We want to animate numbers based on gestures as fast as possible, for example for charts displaying financial data. Updating native state is too slow and not feasible for a smooth experience. Using `createAnimatedComponent` doesn't allow you to animate the text since the children of Text are separate nodes rather than just props.

The best way so far has been to use the `<ReText>` component from [react-native-redash](https://wcandillon.github.io/react-native-redash-v1-docs/strings#retext), which allows you to render a string from a Reanimated Text node. However, under the hood, it uses a `<TextInput/>` and animates it's `value` prop.

This naturally comes with a few edge cases, for example:

<ul>

<li>
*Flicker*: When changing values too fast, the text can be cut off and show an ellipsis. The problem gets worse the slower the device and the more congested the render queue is. Watch this GIF at 0.2x speed carefully: <br/>
<img src="https://user-images.githubusercontent.com/1629785/99287990-458d4600-283b-11eb-8d5e-0c1129189c89.gif"/>

</li>
<li>
*Inconsistent styling*: When styling a <code>TextInput</code>, you need to add more styles and spacing to make it align with the default <code>Text</code> styles. (Behavior in screenshot happens only on Android)
</li> <img src="https://user-images.githubusercontent.com/1629785/99298147-8c823800-2849-11eb-9939-e326dd8d9f25.png" width="388"/> <br/>

<li>
*Lack of full capabilities*: Not all props are available. With Animateable Text, you can use props that you cannot use on a TextInput, such as <code>selectable</code> (Android), <code>dataDetectorType</code> or <code>onTextLayout</code>.
<br/>
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
