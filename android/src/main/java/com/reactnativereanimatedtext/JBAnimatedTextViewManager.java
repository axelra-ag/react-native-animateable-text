package com.reactnativereanimatedtext;

import androidx.annotation.NonNull;

import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.views.text.ReactTextViewManager;

@ReactModule(name = JBAnimatedTextViewManager.REACT_CLASS)
public class JBAnimatedTextViewManager extends ReactTextViewManager {

  public static final String REACT_CLASS = "JBAnimatedText";

  @NonNull
  @Override
  public String getName() {
    return REACT_CLASS;
  }
}
