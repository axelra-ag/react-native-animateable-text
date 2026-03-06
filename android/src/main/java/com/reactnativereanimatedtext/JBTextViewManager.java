package com.reactnativereanimatedtext;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.uimanager.SimpleViewManager;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.facebook.react.views.text.ReactTextView;

@ReactModule(name = JBTextViewManager.REACT_CLASS)
public class JBTextViewManager extends SimpleViewManager<ReactTextView> {

    public static final String REACT_CLASS = "JBAnimatedText";

    @NonNull
    @Override
    public String getName() {
        return REACT_CLASS;
    }

    @NonNull
    @Override
    public ReactTextView createViewInstance(@NonNull ThemedReactContext context) {
        return new ReactTextView(context);
    }

    @ReactProp(name = "text")
    public void setText(ReactTextView view, @Nullable String text) {
        view.setText(text != null ? text : "");
    }
}

