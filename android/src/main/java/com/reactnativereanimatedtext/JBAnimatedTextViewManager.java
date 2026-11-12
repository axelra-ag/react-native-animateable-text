package com.reactnativereanimatedtext;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.Dynamic;
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
    if (view instanceof JBAnimatedTextView) {
      ((JBAnimatedTextView) view).onTextApplied();
    }
  }

  @Nullable
  @Override
  protected ReactTextView prepareToRecycleView(
      @NonNull ThemedReactContext reactContext, @NonNull ReactTextView view) {
    if (view instanceof JBAnimatedTextView) {
      ((JBAnimatedTextView) view).resetForRecycle();
    }
    return super.prepareToRecycleView(reactContext, view);
  }

  // A string, number, boolean or array of those (from a React prop or useAnimatedProps);
  // normalised to one canonical string. Null or missing: no key.
  @ReactProp(name = "transitionKey")
  public void setTransitionKey(ReactTextView view, @Nullable Dynamic key) {
    if (view instanceof JBAnimatedTextView) {
      ((JBAnimatedTextView) view).setTransitionKey(TransitionKey.INSTANCE.fromDynamic(key));
    }
  }

  @ReactProp(name = "revealType")
  public void setRevealType(ReactTextView view, @Nullable String type) {
    if (view instanceof JBAnimatedTextView) {
      ((JBAnimatedTextView) view).setRevealMode(TextTransitionMode.Companion.fromString(type));
    }
  }

  @ReactProp(name = "revealDuration", defaultFloat = 0f)
  public void setRevealDuration(ReactTextView view, float durationMs) {
    if (view instanceof JBAnimatedTextView) {
      ((JBAnimatedTextView) view).setRevealDurationSeconds(durationMs / 1000.0);
    }
  }

  @ReactProp(name = "revealDirection")
  public void setRevealDirection(ReactTextView view, @Nullable String direction) {
    if (view instanceof JBAnimatedTextView) {
      ((JBAnimatedTextView) view).setRevealDirection("down".equals(direction) ? "down" : "up");
    }
  }

  @ReactProp(name = "revealUnit")
  public void setRevealUnit(ReactTextView view, @Nullable String unit) {
    if (view instanceof JBAnimatedTextView) {
      ((JBAnimatedTextView) view).setRevealUnit(TextTransitionUnit.Companion.fromString(unit));
    }
  }

  @ReactProp(name = "revealStagger", defaultFloat = 0f)
  public void setRevealStagger(ReactTextView view, float staggerMs) {
    if (view instanceof JBAnimatedTextView) {
      ((JBAnimatedTextView) view).setRevealStaggerSeconds(Math.max(0f, staggerMs) / 1000.0);
    }
  }

  @ReactProp(name = "revealDelay", defaultFloat = 0f)
  public void setRevealDelay(ReactTextView view, float delayMs) {
    if (view instanceof JBAnimatedTextView) {
      ((JBAnimatedTextView) view).setRevealDelaySeconds(Math.max(0f, delayMs) / 1000.0);
    }
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

  @ReactProp(name = "contentTransitionStagger", defaultFloat = 0f)
  public void setContentTransitionStagger(ReactTextView view, float staggerMs) {
    if (view instanceof JBAnimatedTextView) {
      ((JBAnimatedTextView) view).setTransitionStaggerSeconds(Math.max(0f, staggerMs) / 1000.0);
    }
  }

  @ReactProp(name = "contentTransitionMinInterval", defaultFloat = -1f)
  public void setContentTransitionMinInterval(ReactTextView view, float intervalMs) {
    if (view instanceof JBAnimatedTextView) {
      ((JBAnimatedTextView) view).setTransitionMinIntervalSeconds(intervalMs / 1000.0);
    }
  }

  @ReactProp(name = "contentTransitionDelay", defaultFloat = 0f)
  public void setContentTransitionDelay(ReactTextView view, float delayMs) {
    if (view instanceof JBAnimatedTextView) {
      ((JBAnimatedTextView) view).setTransitionDelaySeconds(Math.max(0f, delayMs) / 1000.0);
    }
  }

  @ReactProp(name = "contentTransitionScope")
  public void setContentTransitionScope(ReactTextView view, @Nullable String scope) {
    if (view instanceof JBAnimatedTextView) {
      ((JBAnimatedTextView) view).setTransitionScope(TextTransitionScope.Companion.fromString(scope));
    }
  }

  @ReactProp(name = "contentTransitionUnit")
  public void setContentTransitionUnit(ReactTextView view, @Nullable String unit) {
    if (view instanceof JBAnimatedTextView) {
      ((JBAnimatedTextView) view).setTransitionUnit(TextTransitionUnit.Companion.fromString(unit));
    }
  }

  @ReactProp(name = "contentTransitionDirection")
  public void setContentTransitionDirection(ReactTextView view, @Nullable String direction) {
    if (view instanceof JBAnimatedTextView) {
      ((JBAnimatedTextView) view).setTransitionDirection(direction != null ? direction : "auto");
    }
  }
}
