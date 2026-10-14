package com.reactnativereanimatedtext;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.uimanager.ReactStylesDiffMap;
import com.facebook.react.uimanager.StateWrapper;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.facebook.react.views.text.ReactTextView;
import com.facebook.react.views.text.ReactTextViewManager;

@ReactModule(name = JBAnimatedTextViewManager.REACT_CLASS)
public class JBAnimatedTextViewManager extends ReactTextViewManager {

  public static final String REACT_CLASS = "JBAnimatedText";

  @NonNull
  @Override
  public String getName() {
    return REACT_CLASS;
  }

  @NonNull
  @Override
  public ReactTextView createViewInstance(@NonNull ThemedReactContext context) {
    return new JBAnimatedTextView(context);
  }

  @Nullable
  @Override
  public Object updateState(
      @NonNull ReactTextView view, @NonNull ReactStylesDiffMap props, @NonNull StateWrapper stateWrapper) {
    if (view instanceof JBAnimatedTextView) {
      JBAnimatedTextView animatedView = (JBAnimatedTextView) view;
      animatedView.setFontSizeAdjustedToFit(
          props.hasKey("adjustsFontSizeToFit") && props.getBoolean("adjustsFontSizeToFit", false));
      animatedView.setMaxLineCount(props.hasKey("numberOfLines") ? props.getInt("numberOfLines", 0) : 0);
    }
    return super.updateState(view, props, stateWrapper);
  }

  @Override
  public void updateExtraData(@NonNull ReactTextView view, @NonNull Object extraData) {
    if (view instanceof JBAnimatedTextView) {
      ((JBAnimatedTextView) view).prepareForTextChange();
    }
    super.updateExtraData(view, extraData);
  }

  @ReactProp(name = "contentTransitionType")
  public void setContentTransitionType(ReactTextView view, @Nullable String type) {
    if (view instanceof JBAnimatedTextView) {
      ((JBAnimatedTextView) view).setTransitionMode(TextTransitionMode.Companion.fromString(type));
    }
  }

  @ReactProp(name = "contentTransitionDuration", defaultFloat = 0f)
  public void setContentTransitionDuration(ReactTextView view, float durationMs) {
    if (view instanceof JBAnimatedTextView) {
      ((JBAnimatedTextView) view).setTransitionDurationSeconds(durationMs / 1000.0);
    }
  }

  @ReactProp(name = "contentTransitionDirection")
  public void setContentTransitionDirection(ReactTextView view, @Nullable String direction) {
    if (view instanceof JBAnimatedTextView) {
      ((JBAnimatedTextView) view).setTransitionDirection(direction != null ? direction : "auto");
    }
  }
}
