/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.reactnativereanimatedtext;

import android.text.Spannable;
import android.text.TextUtils;
import android.text.util.Linkify;
import android.view.Gravity;
import android.view.View;
import androidx.annotation.Nullable;
import com.facebook.react.bridge.JSApplicationIllegalArgumentException;
import com.facebook.react.uimanager.BaseViewManager;
import com.facebook.react.uimanager.PixelUtil;
import com.facebook.react.uimanager.Spacing;
import com.facebook.react.uimanager.ViewDefaults;
import com.facebook.react.uimanager.ViewProps;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.facebook.react.uimanager.annotations.ReactPropGroup;
import com.facebook.react.views.text.DefaultStyleValuesUtil;
import com.facebook.react.views.text.ReactTextView;
import com.facebook.yoga.YogaConstants;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**

 */
public abstract class JBTextAnchorViewManager<T extends View, C extends JBBaseTextShadowNode>
  extends BaseViewManager<T, C> {

  private static final int[] SPACING_TYPES = {
    Spacing.ALL, Spacing.LEFT, Spacing.RIGHT, Spacing.TOP, Spacing.BOTTOM,
  };

  // maxLines can only be set in master view (block), doesn't really make sense to set in a span
  @ReactProp(name = ViewProps.NUMBER_OF_LINES, defaultInt = ViewDefaults.NUMBER_OF_LINES)
  public void setNumberOfLines(ReactTextView view, int numberOfLines) {
    view.setNumberOfLines(numberOfLines);
  }

  @ReactProp(name = ViewProps.ELLIPSIZE_MODE)
  public void setEllipsizeMode(ReactTextView view, @Nullable String ellipsizeMode) {
    if (ellipsizeMode == null || ellipsizeMode.equals("tail")) {
      view.setEllipsizeLocation(TextUtils.TruncateAt.END);
    } else if (ellipsizeMode.equals("head")) {
      view.setEllipsizeLocation(TextUtils.TruncateAt.START);
    } else if (ellipsizeMode.equals("middle")) {
      view.setEllipsizeLocation(TextUtils.TruncateAt.MIDDLE);
    } else if (ellipsizeMode.equals("clip")) {
      view.setEllipsizeLocation(null);
    } else {
      throw new JSApplicationIllegalArgumentException("Invalid ellipsizeMode: " + ellipsizeMode);
    }
  }

  @ReactProp(name = ViewProps.ADJUSTS_FONT_SIZE_TO_FIT)
  public void setAdjustFontSizeToFit(ReactTextView view, boolean adjustsFontSizeToFit) {
    view.setAdjustFontSizeToFit(adjustsFontSizeToFit);
  }

  @ReactProp(name = ViewProps.TEXT_ALIGN_VERTICAL)
  public void setTextAlignVertical(ReactTextView view, @Nullable String textAlignVertical) {
    // TODO: Can we call this
  try {

    if (textAlignVertical == null || "auto".equals(textAlignVertical)) {
      Method method = view.getClass().getDeclaredMethod("setGravityVertical");
      method.setAccessible(true);
      method.invoke(view, Gravity.NO_GRAVITY);
    } else if ("top".equals(textAlignVertical)) {
      Method method = view.getClass().getDeclaredMethod("setGravityVertical");
      method.setAccessible(true);
      method.invoke(view, Gravity.TOP);
    } else if ("bottom".equals(textAlignVertical)) {
      Method method = view.getClass().getDeclaredMethod("setGravityVertical");
      method.setAccessible(true);
      method.invoke(view, Gravity.BOTTOM);
    } else if ("center".equals(textAlignVertical)) {
      Method method = view.getClass().getDeclaredMethod("setGravityVertical");
      method.setAccessible(true);
      method.invoke(view, Gravity.CENTER_VERTICAL);
    } else {
      throw new JSApplicationIllegalArgumentException(
        "Invalid textAlignVertical: " + textAlignVertical);
    }

  } catch (NoSuchMethodException e) {
    System.out.println("no such method");
  } catch (IllegalAccessException e) {
    System.out.println("illegal access");
  }
  catch (InvocationTargetException e) {
    System.out.println("invocation target exception");
  }


  }

  @ReactProp(name = "selectable")
  public void setSelectable(ReactTextView view, boolean isSelectable) {
    view.setTextIsSelectable(isSelectable);
  }

  @ReactProp(name = "selectionColor", customType = "Color")
  public void setSelectionColor(ReactTextView view, @Nullable Integer color) {
    if (color == null) {
      view.setHighlightColor(
        DefaultStyleValuesUtil.getDefaultTextColorHighlight(view.getContext()));
    } else {
      view.setHighlightColor(color);
    }
  }

  @ReactPropGroup(
    names = {
      ViewProps.BORDER_RADIUS,
      ViewProps.BORDER_TOP_LEFT_RADIUS,
      ViewProps.BORDER_TOP_RIGHT_RADIUS,
      ViewProps.BORDER_BOTTOM_RIGHT_RADIUS,
      ViewProps.BORDER_BOTTOM_LEFT_RADIUS
    },
    defaultFloat = YogaConstants.UNDEFINED)
  public void setBorderRadius(ReactTextView view, int index, float borderRadius) {
    if (!YogaConstants.isUndefined(borderRadius)) {
      borderRadius = PixelUtil.toPixelFromDIP(borderRadius);
    }

    if (index == 0) {
      view.setBorderRadius(borderRadius);
    } else {
      view.setBorderRadius(borderRadius, index - 1);
    }
  }

  @ReactProp(name = "borderStyle")
  public void setBorderStyle(ReactTextView view, @Nullable String borderStyle) {
    view.setBorderStyle(borderStyle);
  }

  @ReactPropGroup(
    names = {
      ViewProps.BORDER_WIDTH,
      ViewProps.BORDER_LEFT_WIDTH,
      ViewProps.BORDER_RIGHT_WIDTH,
      ViewProps.BORDER_TOP_WIDTH,
      ViewProps.BORDER_BOTTOM_WIDTH,
    },
    defaultFloat = YogaConstants.UNDEFINED)
  public void setBorderWidth(ReactTextView view, int index, float width) {
    if (!YogaConstants.isUndefined(width)) {
      width = PixelUtil.toPixelFromDIP(width);
    }
    view.setBorderWidth(SPACING_TYPES[index], width);
  }

  @ReactPropGroup(
    names = {
      "borderColor",
      "borderLeftColor",
      "borderRightColor",
      "borderTopColor",
      "borderBottomColor"
    },
    customType = "Color")
  public void setBorderColor(ReactTextView view, int index, Integer color) {
    float rgbComponent =
      color == null ? YogaConstants.UNDEFINED : (float) ((int) color & 0x00FFFFFF);
    float alphaComponent = color == null ? YogaConstants.UNDEFINED : (float) ((int) color >>> 24);
    view.setBorderColor(SPACING_TYPES[index], rgbComponent, alphaComponent);
  }

  @ReactProp(name = ViewProps.INCLUDE_FONT_PADDING, defaultBoolean = true)
  public void setIncludeFontPadding(ReactTextView view, boolean includepad) {
    view.setIncludeFontPadding(includepad);
  }

  @ReactProp(name = "disabled", defaultBoolean = false)
  public void setDisabled(ReactTextView view, boolean disabled) {
    view.setEnabled(!disabled);
  }

  @ReactProp(name = "dataDetectorType")
  public void setDataDetectorType(ReactTextView view, @Nullable String type) {
    switch (type) {
      case "phoneNumber":
        view.setLinkifyMask(Linkify.PHONE_NUMBERS);
        break;
      case "link":
        view.setLinkifyMask(Linkify.WEB_URLS);
        break;
      case "email":
        view.setLinkifyMask(Linkify.EMAIL_ADDRESSES);
        break;
      case "all":
        view.setLinkifyMask(Linkify.ALL);
        break;
      case "none":
      default:
        view.setLinkifyMask(0);
        break;
    }
  }

  @ReactProp(name = "onInlineViewLayout")
  public void setNotifyOnInlineViewLayout(ReactTextView view, boolean notifyOnInlineViewLayout) {
    view.setNotifyOnInlineViewLayout(notifyOnInlineViewLayout);
  }
}
