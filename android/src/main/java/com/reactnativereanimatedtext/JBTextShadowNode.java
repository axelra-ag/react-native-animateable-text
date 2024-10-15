/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
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
    try {
      mPreparedSpannableTextField.set(this, spannedFromShadowNode(
        this,
        /* text (e.g. from `value` prop): */ mText,
        /* supportsInlineViews: */ true,
        nativeViewHierarchyOptimizer));
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  @ReactProp(name = "text")
  public void setText(String newText) {
    mText = newText;
    markUpdated();
  }
}
