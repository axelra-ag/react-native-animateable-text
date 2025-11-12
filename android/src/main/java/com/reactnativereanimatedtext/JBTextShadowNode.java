/*
 * Custom - onBeforeLayout (forked)
 */

package com.reactnativereanimatedtext;

import android.annotation.TargetApi;
import android.os.Build;
import com.facebook.react.uimanager.NativeViewHierarchyOptimizer;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.facebook.react.views.text.ReactTextShadowNode;
import com.facebook.react.views.text.ReactTextViewManagerCallback;

import java.lang.reflect.Field;

@TargetApi(Build.VERSION_CODES.M)
public class JBTextShadowNode extends ReactTextShadowNode {
  protected String mText = "";

private static final Field mPreparedSpannableTextField;
  static {
    try {
      mPreparedSpannableTextField = ReactTextShadowNode.class.getDeclaredField("mPreparedSpannableText");
      mPreparedSpannableTextField.setAccessible(true);
    } catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
  }

  public JBTextShadowNode() {
    this(null);
  }

  public JBTextShadowNode(ReactTextViewManagerCallback reactTextViewManagerCallback) {
    super(reactTextViewManagerCallback);
  }

  @Override
  public void onBeforeLayout(NativeViewHierarchyOptimizer nativeViewHierarchyOptimizer) {
    // ADDED
    try {
    // END ADDED
      mPreparedSpannableTextField.set(this, spannedFromShadowNode(
        this,
        /* text (e.g. from `value` prop): */ mText, // EDITED
        /* supportsInlineViews: */ true,
        nativeViewHierarchyOptimizer));
      markUpdated();
    // ADDED
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
    // END ADDED
  }

  @ReactProp(name = "text")
  public void setText(String newText) {
    mText = newText;
    markUpdated();
  }
}
