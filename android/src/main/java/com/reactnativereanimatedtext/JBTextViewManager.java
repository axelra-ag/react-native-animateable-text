/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.reactnativereanimatedtext;

import android.content.Context;
import android.os.Build;
import android.text.Spannable;
import android.view.Gravity;

import androidx.annotation.Nullable;
import com.facebook.react.common.mapbuffer.MapBuffer;
import com.facebook.react.common.MapBuilder;
import com.facebook.react.common.annotations.VisibleForTesting;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.uimanager.IViewManagerWithChildren;
import com.facebook.react.uimanager.ReactStylesDiffMap;
import com.facebook.react.uimanager.StateWrapper;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.views.text.ReactTextAnchorViewManager;
import com.facebook.react.views.text.ReactTextShadowNode;
import com.facebook.react.views.text.ReactTextUpdate;
import com.facebook.react.views.text.ReactTextView;
import com.facebook.react.views.text.ReactTextViewManager;
import com.facebook.react.views.text.ReactTextViewManagerCallback;
import com.facebook.react.views.text.TextAttributeProps;
import com.facebook.react.views.text.internal.span.TextInlineImageSpan;
import com.facebook.react.views.text.TextLayoutManager;
import com.facebook.yoga.YogaMeasureMode;
import java.util.Map;

/**
 * Concrete class for {@link ReactTextAnchorViewManager} which represents view managers of anchor
 * {@code <Text>} nodes.
 */
@ReactModule(name = ReactTextViewManager.REACT_CLASS)
public class JBTextViewManager
  extends JBTextAnchorViewManager<ReactTextView, JBTextShadowNode>
  implements IViewManagerWithChildren {

  @VisibleForTesting public static final String REACT_CLASS = "JBAnimatedText";
  private static final short TX_STATE_KEY_ATTRIBUTED_STRING = 0;
  private static final short TX_STATE_KEY_PARAGRAPH_ATTRIBUTES = 1;
  // used for text input
  private static final short TX_STATE_KEY_HASH = 2;
  private static final short TX_STATE_KEY_MOST_RECENT_EVENT_COUNT = 3;

  protected @Nullable ReactTextViewManagerCallback mReactTextViewManagerCallback;

  @Override
  public String getName() {
    return REACT_CLASS;
  }

  @Override
  public ReactTextView createViewInstance(ThemedReactContext context) {
    return new ReactTextView(context);
  }

  @Override
  public void updateExtraData(ReactTextView view, Object extraData) {
    ReactTextUpdate update = (ReactTextUpdate) extraData;
    if (update.containsImages()) {
      Spannable spannable = update.getText();
      TextInlineImageSpan.possiblyUpdateInlineImageSpans(spannable, view);
    }
    view.setText(update);
  }

  @Override
  public JBTextShadowNode createShadowNodeInstance() {
    return new JBTextShadowNode();
  }

  public JBTextShadowNode createShadowNodeInstance(
    @Nullable ReactTextViewManagerCallback reactTextViewManagerCallback) {
    return new JBTextShadowNode(reactTextViewManagerCallback);
  }

  @Override
  public Class<JBTextShadowNode> getShadowNodeClass() {
    return JBTextShadowNode.class;
  }

  @Override
  protected void onAfterUpdateTransaction(ReactTextView view) {
    super.onAfterUpdateTransaction(view);
    view.updateView();
  }

  public boolean needsCustomLayoutForChildren() {
    return true;
  }

  @Override
  public Object updateState(
    ReactTextView view, ReactStylesDiffMap props, @Nullable StateWrapper stateWrapper) {
    MapBuffer state = stateWrapper.getStateDataMapBuffer();
    if (state == null) {
      return null;
    }
    else {
      MapBuffer attributedString = state.getMapBuffer(TX_STATE_KEY_ATTRIBUTED_STRING);
      MapBuffer paragraphAttributes = state.getMapBuffer(TX_STATE_KEY_PARAGRAPH_ATTRIBUTES);

      Spannable spanned =
        TextLayoutManager.getOrCreateSpannableForText(
          view.getContext(), attributedString, mReactTextViewManagerCallback);
      view.setSpanned(spanned);

      int textBreakStrategy =
        TextAttributeProps.getTextBreakStrategy(
          paragraphAttributes.getString(TextLayoutManager.PA_KEY_TEXT_BREAK_STRATEGY));

      int currentJustificationMode = 0;
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        currentJustificationMode = view.getJustificationMode();
      }
      return new ReactTextUpdate(
        spanned,
        state.contains(TX_STATE_KEY_MOST_RECENT_EVENT_COUNT) ? state.getInt(TX_STATE_KEY_MOST_RECENT_EVENT_COUNT) : -1,
        false, // TODO add this into local Data
        TextAttributeProps.getTextAlignment(props, TextLayoutManager.isRTL(attributedString), Gravity.LEFT),
        textBreakStrategy,
        TextAttributeProps.getJustificationMode(props, currentJustificationMode));
    }
  }

  @Override
  public @Nullable Map getExportedCustomDirectEventTypeConstants() {
    return MapBuilder.of(
      "topTextLayout", MapBuilder.of("registrationName", "onTextLayout"),
      "topInlineViewLayout", MapBuilder.of("registrationName", "onInlineViewLayout"));
  }

  @Override
  public long measure(
    Context context,
    MapBuffer localData,
    MapBuffer props,
    MapBuffer state,
    float width,
    YogaMeasureMode widthMode,
    float height,
    YogaMeasureMode heightMode,
    @Nullable float[] attachmentsPositions) {

    return TextLayoutManager.measureText(
      context,
      localData,
      props,
      width,
      widthMode,
      height,
      heightMode,
      mReactTextViewManagerCallback,
      attachmentsPositions);
  }

  @Override
  public void setPadding(ReactTextView view, int left, int top, int right, int bottom) {
    view.setPadding(left, top, right, bottom);
  }
}
