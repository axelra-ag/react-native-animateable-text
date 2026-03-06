package com.reactnativereanimatedtext;

import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;

import com.facebook.react.common.annotations.UnstableReactNativeAPI;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.uimanager.SimpleViewManager;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.facebook.react.views.text.ReactTextView;

import java.util.Objects;

@OptIn(markerClass = UnstableReactNativeAPI.class)
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
        ReactTextView view = new ReactTextView(context);
        view.setTextColor(Color.BLACK);
        return view;
    }

    @ReactProp(name = "text")
    public void setText(ReactTextView view, @Nullable String text) {
        view.setText(text != null ? text : "");
    }

    @ReactProp(name = "color", customType = "Color")
    public void setColor(ReactTextView view, @Nullable Integer color) {
        view.setTextColor(Objects.requireNonNullElse(color, Color.BLACK));
    }

    @ReactProp(name = "fontSize")
    public void setFontSize(ReactTextView view, float fontSize) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, fontSize);
    }

    @ReactProp(name = "fontWeight")
    public void setFontWeight(ReactTextView view, @Nullable String fontWeight) {
        boolean wantBold = false;
        if (fontWeight != null) {
            if (fontWeight.equals("bold") || fontWeight.equals("500") || fontWeight.equals("600") || fontWeight.equals("700") || fontWeight.equals("800") || fontWeight.equals("900")) {
                wantBold = true;
            }
        }
        Typeface current = view.getTypeface();
        int currentStyle = (current != null) ? current.getStyle() : Typeface.NORMAL;
        boolean isItalic = (currentStyle & Typeface.ITALIC) != 0;
        int newStyle = Typeface.NORMAL;
        if (wantBold && isItalic) {
            newStyle = Typeface.BOLD_ITALIC;
        } else if (wantBold) {
            newStyle = Typeface.BOLD;
        } else if (isItalic) {
            newStyle = Typeface.ITALIC;
        }
        view.setTypeface(Typeface.create(current, newStyle));
    }

    @ReactProp(name = "textAlign")
    public void setTextAlign(ReactTextView view, @Nullable String textAlign) {
        if ("center".equals(textAlign)) {
            view.setGravity(Gravity.CENTER_HORIZONTAL);
        } else if ("right".equals(textAlign)) {
            view.setGravity(Gravity.END);
        } else {
            view.setGravity(Gravity.START);
        }
    }

    @ReactProp(name = "numberOfLines")
    public void setNumberOfLines(ReactTextView view, int numberOfLines) {
        if (numberOfLines > 0) {
            view.setMaxLines(numberOfLines);
            view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        } else {
            view.setMaxLines(Integer.MAX_VALUE);
        }
    }
}

