package com.reactnativereanimatedtext;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

import com.facebook.react.bridge.ReactContext;
import com.facebook.react.uimanager.PixelUtil;

import java.util.HashMap;
import java.util.Map;

public class JBAnimatedTextComponentView extends AppCompatTextView {
    private static final String TAG = "JBAnimatedTextView";
    
    private String mText = "";
    private @Nullable Integer mColor;
    private float mFontSize = 14f;
    private @Nullable String mFontFamily;
    private @Nullable String mFontWeight;
    private @Nullable String mFontStyle;
    private @Nullable String mTextAlign;
    private int mNumberOfLines = 0;
    private float mLineHeight = Float.NaN;
    private float mLetterSpacing = 0f;
    private @Nullable String mTextDecorationLine;
    
    private final Map<String, Object> mStyleCache = new HashMap<>();
    private boolean mNeedsStyleUpdate = false;

    public JBAnimatedTextComponentView(@NonNull Context context) {
        super(context);
        setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        
        // Apply default styles
        mFontSize = 14f;
        // Set default text color to match Android's default (usually black or follows theme)
        mColor = getCurrentTextColor();
        mNeedsStyleUpdate = true;
    }

    public void setText(String text) {
        if (!TextUtils.equals(mText, text)) {
            mText = text != null ? text : "";
            mNeedsStyleUpdate = true;
            updateTextWithStyles();
        }
    }

    public void setColor(@Nullable Integer color) {
        if (!objectEquals(mColor, color)) {
            mColor = color;
            mStyleCache.put("color", color);
            mNeedsStyleUpdate = true;
            updateTextWithStyles();
        }
    }

    public void setFontSize(float fontSize) {
        if (mFontSize != fontSize) {
            mFontSize = fontSize;
            mStyleCache.put("fontSize", fontSize);
            mNeedsStyleUpdate = true;
            updateTextWithStyles();
        }
    }

    public void setFontFamily(@Nullable String fontFamily) {
        if (!TextUtils.equals(mFontFamily, fontFamily)) {
            mFontFamily = fontFamily;
            mStyleCache.put("fontFamily", fontFamily);
            mNeedsStyleUpdate = true;
            updateTextWithStyles();
        }
    }

    public void setFontWeight(@Nullable String fontWeight) {
        if (!TextUtils.equals(mFontWeight, fontWeight)) {
            mFontWeight = fontWeight;
            mStyleCache.put("fontWeight", fontWeight);
            mNeedsStyleUpdate = true;
            updateTextWithStyles();
        }
    }

    public void setFontStyle(@Nullable String fontStyle) {
        if (!TextUtils.equals(mFontStyle, fontStyle)) {
            mFontStyle = fontStyle;
            mStyleCache.put("fontStyle", fontStyle);
            mNeedsStyleUpdate = true;
            updateTextWithStyles();
        }
    }

    public void setTextAlign(@Nullable String textAlign) {
        if (!TextUtils.equals(mTextAlign, textAlign)) {
            mTextAlign = textAlign;
            applyTextAlign();
        }
    }

    public void setNumberOfLines(int numberOfLines) {
        if (mNumberOfLines != numberOfLines) {
            mNumberOfLines = numberOfLines;
            applyNumberOfLines();
        }
    }

    public void setLineHeight(float lineHeight) {
        if (mLineHeight != lineHeight) {
            mLineHeight = lineHeight;
            applyLineHeight();
        }
    }

    public void setTextDecorationLine(@Nullable String textDecorationLine) {
        if (!TextUtils.equals(mTextDecorationLine, textDecorationLine)) {
            mTextDecorationLine = textDecorationLine;
            mStyleCache.put("textDecorationLine", textDecorationLine);
            mNeedsStyleUpdate = true;
            updateTextWithStyles();
        }
    }

    public void setLetterSpacing(float letterSpacing) {
        if (mLetterSpacing != letterSpacing) {
            mLetterSpacing = letterSpacing;
            mStyleCache.put("letterSpacing", letterSpacing);
            mNeedsStyleUpdate = true;
            updateTextWithStyles();
        }
    }

    private void updateTextWithStyles() {
        if (mText.isEmpty()) {
            super.setText("", BufferType.SPANNABLE);
            return;
        }
        
        if (!mNeedsStyleUpdate) {
            return;
        }

        try {
            SpannableStringBuilder spannable = new SpannableStringBuilder(mText);
            int length = spannable.length();

        if (mColor != null) {
            spannable.setSpan(
                new ForegroundColorSpan(mColor),
                0,
                length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }

        spannable.setSpan(
            new AbsoluteSizeSpan((int) PixelUtil.toPixelFromSP(mFontSize)),
            0,
            length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        int typefaceStyle = Typeface.NORMAL;
        if (isBold(mFontWeight)) {
            typefaceStyle |= Typeface.BOLD;
        }
        if ("italic".equals(mFontStyle)) {
            typefaceStyle |= Typeface.ITALIC;
        }

        if (typefaceStyle != Typeface.NORMAL) {
            spannable.setSpan(
                new StyleSpan(typefaceStyle),
                0,
                length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }

        if (mFontFamily != null) {
            Typeface typeface = getTypefaceForFontFamily(mFontFamily, typefaceStyle);
            if (typeface != null) {
                spannable.setSpan(
                    new CustomTypefaceSpan(typeface),
                    0,
                    length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
        }

            if (mTextDecorationLine != null) {
                applyTextDecoration(spannable, mTextDecorationLine, length);
            }

            super.setText(spannable, BufferType.SPANNABLE);
            mNeedsStyleUpdate = false;

            // Reapply letter spacing and line height after setting text since both
            // depend on mFontSize (see applyLetterSpacing / applyLineHeight) and
            // neither is encoded in the spannable. Without re-applying here, a
            // prop order where lineHeight/letterSpacing arrive before fontSize
            // leaves the value computed against the default 14sp instead of the
            // configured size.
            applyLetterSpacing();
            applyLineHeight();
        } catch (Exception e) {
            // Fallback to plain text if styling fails
            super.setText(mText, BufferType.NORMAL);
            mNeedsStyleUpdate = false;
        }
    }

    private void applyTextAlign() {
        if (mTextAlign == null) {
            setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            return;
        }

        switch (mTextAlign) {
            case "left":
            case "start":
                setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                break;
            case "right":
            case "end":
                setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                break;
            case "center":
                setGravity(Gravity.CENTER);
                break;
            case "justify":
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD);
                }
                setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                break;
            default:
                setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                break;
        }
    }

    private void applyNumberOfLines() {
        if (mNumberOfLines == 0) {
            setMaxLines(Integer.MAX_VALUE);
            setEllipsize(null);
        } else {
            setMaxLines(mNumberOfLines);
            setEllipsize(TextUtils.TruncateAt.END);
        }
    }

    private void applyLineHeight() {
        if (!Float.isNaN(mLineHeight)) {
            float lineHeightPx = PixelUtil.toPixelFromDIP(mLineHeight);
            // Use the configured font size (mFontSize), NOT getTextSize(). The
            // view's intrinsic text size stays at Android's theme default (~14sp)
            // because rendering size is applied via AbsoluteSizeSpan in
            // updateTextWithStyles, not via super.setTextSize. setLineSpacing(add,
            // mult) is applied on top of the font-metrics line height, which is
            // derived from the rendered glyph size, so `add` must be computed
            // against the rendered size or each line gets extra
            // (renderedSize − 14sp) of spurious spacing.
            float currentTextSize = PixelUtil.toPixelFromSP(mFontSize);
            setLineSpacing(lineHeightPx - currentTextSize, 1.0f);
        }
    }

    private void applyLetterSpacing() {
        // Convert from DP to EM units (Android uses EM for letter spacing)
        // React Native uses DP, so we need to convert to pixels first
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            // Convert letter spacing from DP to pixels
            float letterSpacingPx = PixelUtil.toPixelFromDIP(mLetterSpacing);
            // Use the configured font size (set via setFontSize / @ReactProp "fontSize"),
            // NOT getTextSize(). The view's intrinsic text size stays at Android's
            // theme default (~14sp) because rendering size is applied via
            // AbsoluteSizeSpan in updateTextWithStyles, not via super.setTextSize.
            // TextView.setLetterSpacing(em) scales the spacing by the rendered glyph
            // size, so we must compute EM against the rendered size or the result is
            // wrong by a factor of (renderedSize / 14sp).
            float textSizePx = PixelUtil.toPixelFromSP(mFontSize);

            if (textSizePx > 0) {
                // Calculate EM value: letter spacing in pixels / text size in pixels
                float letterSpacingEm = letterSpacingPx / textSizePx;
                super.setLetterSpacing(letterSpacingEm);
            }
        }
    }

    private void applyTextDecoration(SpannableStringBuilder spannable, String decoration, int length) {
        if (decoration.contains("underline")) {
            spannable.setSpan(
                new android.text.style.UnderlineSpan(),
                0,
                length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        if (decoration.contains("line-through")) {
            spannable.setSpan(
                new android.text.style.StrikethroughSpan(),
                0,
                length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
    }

    private boolean isBold(@Nullable String fontWeight) {
        if (fontWeight == null) {
            return false;
        }
        return "bold".equals(fontWeight) || 
               "700".equals(fontWeight) || 
               "800".equals(fontWeight) || 
               "900".equals(fontWeight);
    }

    private @Nullable Typeface getTypefaceForFontFamily(String fontFamily, int style) {
        try {
            ReactContext reactContext = (ReactContext) getContext();
            return com.facebook.react.common.assets.ReactFontManager.getInstance()
                .getTypeface(fontFamily, style, reactContext.getAssets());
        } catch (Exception e) {
            android.util.Log.w(TAG, "Could not get typeface for font family: " + fontFamily, e);
            return null;
        }
    }

    private boolean objectEquals(@Nullable Object a, @Nullable Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    public void reapplyAllStyles() {
        mNeedsStyleUpdate = true;
        updateTextWithStyles();
        applyTextAlign();
        applyNumberOfLines();
        applyLineHeight();
        applyLetterSpacing();
    }

    private static class CustomTypefaceSpan extends android.text.style.MetricAffectingSpan {
        private final Typeface mTypeface;

        CustomTypefaceSpan(Typeface typeface) {
            mTypeface = typeface;
        }

        @Override
        public void updateDrawState(android.text.TextPaint paint) {
            paint.setTypeface(mTypeface);
        }

        @Override
        public void updateMeasureState(@NonNull android.text.TextPaint paint) {
            paint.setTypeface(mTypeface);
        }
    }
}
