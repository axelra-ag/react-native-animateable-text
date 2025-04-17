import * as React from 'react';
import { Text } from 'react-native';
export const AnimateableText = React.forwardRef((props, ref) => {
    const [text, setText] = React.useState(props.text);
    const animatedTextRef = React.useRef(null);
    // just in case users tried to update the value without
    // a shared value
    React.useEffect(() => {
        if (props.text) {
            setText(props.text);
        }
    }, [props.text]);
    React.useImperativeHandle(ref, () => ({
        setNativeProps: (nativeProps) => {
            if (animatedTextRef.current && nativeProps.text) {
                setText(nativeProps?.text);
            }
        },
    }), []);
    return (React.createElement(Text, { ref: animatedTextRef, ...props }, text));
});
