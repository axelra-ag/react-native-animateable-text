/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.reactnativereanimatedtext;

import androidx.annotation.Nullable;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.views.text.ReactTextShadowNode;
import com.facebook.react.views.text.ReactTextViewManager;
import com.facebook.react.views.text.ReactTextViewManagerCallback;
import androidx.annotation.NonNull;

@ReactModule(name = JBTextViewManager.REACT_CLASS)
public class JBTextViewManager
  extends ReactTextViewManager {

  public static final String REACT_CLASS = "JBAnimatedText";

  @NonNull
  @Override
  public String getName() {
    return REACT_CLASS;
  }

  @Override
  public ReactTextShadowNode createShadowNodeInstance() {
    return new JBTextShadowNode();
  }

  public JBTextShadowNode createShadowNodeInstance(
    @Nullable ReactTextViewManagerCallback reactTextViewManagerCallback) {
    return new JBTextShadowNode(reactTextViewManagerCallback);
  }
}

