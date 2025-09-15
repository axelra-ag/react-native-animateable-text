/*
 * Custom
 */

package com.reactnativereanimatedtext;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import com.facebook.react.common.MapBuilder;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.uimanager.SimpleViewManager;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.facebook.react.views.text.ReactTextView;
import com.facebook.react.views.text.ReactTextViewManager;
import com.facebook.react.views.text.ReactTextUpdate;
import com.facebook.react.common.assets.ReactFontManager;
import android.text.style.TypefaceSpan;
import android.text.style.StyleSpan;

import java.util.Map;

@ReactModule(name = JBTextViewManager.REACT_CLASS)
public class JBTextViewManager extends SimpleViewManager<ReactTextView> {

  public static final String REACT_CLASS = "JBAnimatedText";
  
  private ReactTextViewManager mInternalTextViewManager;

  public JBTextViewManager() {
    mInternalTextViewManager = new ReactTextViewManager();
    android.util.Log.d("JBTextViewManager", "JBTextViewManager created");
  }

  @NonNull
  @Override
  public String getName() {
    return REACT_CLASS;
  }

  @NonNull
  @Override
  protected ReactTextView createViewInstance(@NonNull ThemedReactContext reactContext) {
    android.util.Log.d("JBTextViewManager", "createViewInstance called");
    ReactTextView view = mInternalTextViewManager.createViewInstance(reactContext);
    android.util.Log.d("JBTextViewManager", "ReactTextView created: " + view);
    return view;
  }

  @ReactProp(name = "text")
  public void setText(ReactTextView view, @Nullable String text) {
    android.util.Log.d("JBTextViewManager", "setText called with: " + text);
    mCurrentText = text;
    updateTextWithCurrentSettings(view);
  }

  @ReactProp(name = "color", customType = "Color")
  public void setColor(ReactTextView view, @Nullable Integer color) {
    android.util.Log.d("JBTextViewManager", "setColor called with: " + color);
    if (color != null) {
      view.setTextColor(color);
    }
  }

  @ReactProp(name = "fontSize", defaultFloat = 14f)
  public void setFontSize(ReactTextView view, float fontSize) {
    android.util.Log.d("JBTextViewManager", "setFontSize called with: " + fontSize);
    view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, fontSize);
  }

  // Store font properties and text for coordination and preservation
  private String mFontFamily = null;
  private String mFontWeight = null;
  private String mFontStyle = null;
  private String mCurrentText = null;

  @ReactProp(name = "fontWeight")
  public void setFontWeight(ReactTextView view, @Nullable String fontWeight) {
    android.util.Log.d("JBTextViewManager", "setFontWeight called with: " + fontWeight);
    mFontWeight = fontWeight;
    updateTextWithCurrentSettings(view);
  }

  @ReactProp(name = "fontFamily")
  public void setFontFamily(ReactTextView view, @Nullable String fontFamily) {
    android.util.Log.d("JBTextViewManager", "setFontFamily called with: " + fontFamily);
    mFontFamily = fontFamily;
    updateTextWithCurrentSettings(view);
  }

  @ReactProp(name = "fontStyle")
  public void setFontStyle(ReactTextView view, @Nullable String fontStyle) {
    android.util.Log.d("JBTextViewManager", "setFontStyle called with: " + fontStyle);
    mFontStyle = fontStyle;
    updateTextWithCurrentSettings(view);
  }

  @ReactProp(name = "textAlign")
  public void setTextAlign(ReactTextView view, @Nullable String textAlign) {
    android.util.Log.d("JBTextViewManager", "setTextAlign called with: " + textAlign);
    if (textAlign != null) {
      switch (textAlign) {
        case "left":
        case "start":
          view.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
          break;
        case "right":
        case "end":
          view.setGravity(android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL);
          break;
        case "center":
          view.setGravity(android.view.Gravity.CENTER);
          break;
        case "justify":
          if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            view.setJustificationMode(android.text.Layout.JUSTIFICATION_MODE_INTER_WORD);
          }
          break;
        default:
          view.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
          break;
      }
    }
  }

  @ReactProp(name = "numberOfLines", defaultInt = 0)
  public void setNumberOfLines(ReactTextView view, int numberOfLines) {
    android.util.Log.d("JBTextViewManager", "setNumberOfLines called with: " + numberOfLines);
    if (numberOfLines == 0) {
      view.setMaxLines(Integer.MAX_VALUE);
    } else {
      view.setMaxLines(numberOfLines);
      view.setEllipsize(android.text.TextUtils.TruncateAt.END);
    }
  }

  @ReactProp(name = "lineHeight", defaultFloat = Float.NaN)
  public void setLineHeight(ReactTextView view, float lineHeight) {
    android.util.Log.d("JBTextViewManager", "setLineHeight called with: " + lineHeight);
    if (!Float.isNaN(lineHeight)) {
      view.setLineSpacing(lineHeight - view.getTextSize(), 1.0f);
    }
  }

  @ReactProp(name = "textDecorationLine")
  public void setTextDecorationLine(ReactTextView view, @Nullable String textDecorationLine) {
    android.util.Log.d("JBTextViewManager", "setTextDecorationLine called with: " + textDecorationLine);
    if (textDecorationLine != null) {
      int paintFlags = view.getPaintFlags();
      
      // Reset decoration flags
      paintFlags &= ~android.graphics.Paint.UNDERLINE_TEXT_FLAG;
      paintFlags &= ~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG;
      
      switch (textDecorationLine) {
        case "underline":
          paintFlags |= android.graphics.Paint.UNDERLINE_TEXT_FLAG;
          break;
        case "line-through":
          paintFlags |= android.graphics.Paint.STRIKE_THRU_TEXT_FLAG;
          break;
        case "underline line-through":
          paintFlags |= android.graphics.Paint.UNDERLINE_TEXT_FLAG;
          paintFlags |= android.graphics.Paint.STRIKE_THRU_TEXT_FLAG;
          break;
        case "none":
        default:
          // No decoration
          break;
      }
      
      view.setPaintFlags(paintFlags);
    }
  }

  // Note: allowFontScaling and maxFontSizeMultiplier methods don't exist on ReactTextView
  // These would need to be handled differently or through the internal text view manager

  @Override
  public @Nullable Map<String, Object> getExportedCustomDirectEventTypeConstants() {
    return MapBuilder.<String, Object>builder()
        .put("onTextLayout", MapBuilder.of("registrationName", "onTextLayout"))
        .build();
  }

  private void updateTextWithCurrentSettings(ReactTextView view) {
    if (mCurrentText == null || mCurrentText.isEmpty()) {
      return;
    }
    
    android.util.Log.d("JBTextViewManager", "Updating text with current font settings");
    
    // Create spannable with font information applied
    android.text.SpannableString spannable = createStyledSpannable(mCurrentText);
    
    try {
      ReactTextUpdate textUpdate = new ReactTextUpdate(
              spannable,
              -1, // UNSET
              false, // containsImages
              0, // paddingLeft
              0, // paddingTop  
              0, // paddingRight
              0, // paddingBottom
              android.view.Gravity.NO_GRAVITY, // textAlign
              android.text.Layout.BREAK_STRATEGY_SIMPLE, // textBreakStrategy  
              android.text.Layout.JUSTIFICATION_MODE_NONE // justificationMode
          );
      
      view.setText(textUpdate);
      android.util.Log.d("JBTextViewManager", "Text updated with styled spannable");
      
      // Force the view to update
      view.requestLayout();
      view.invalidate();
      
    } catch (Exception e) {
      android.util.Log.e("JBTextViewManager", "Error updating text with styling: " + e.getMessage());
      e.printStackTrace();
    }
  }

  private android.text.SpannableString createStyledSpannable(String text) {
    android.text.SpannableString spannable = new android.text.SpannableString(text);
    
    // Apply font family if specified
    if (mFontFamily != null) {
      try {
        // Get typeface using ReactFontManager (which accesses useFonts registry)
        android.graphics.Typeface typeface = ReactFontManager.getInstance().getTypeface(
            mFontFamily, 
            android.graphics.Typeface.NORMAL, 
            null // AssetManager - ReactFontManager handles this internally
        );
        
        if (typeface != null) {
          // Create a custom TypefaceSpan that uses our specific typeface
          TypefaceSpan typefaceSpan = new TypefaceSpan(mFontFamily) {
            @Override
            public void updateDrawState(android.text.TextPaint paint) {
              paint.setTypeface(typeface);
            }
            
            @Override
            public void updateMeasureState(android.text.TextPaint paint) {
              paint.setTypeface(typeface);
            }
          };
          
          spannable.setSpan(typefaceSpan, 0, text.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
          android.util.Log.d("JBTextViewManager", "Applied font family span: " + mFontFamily);
        }
      } catch (Exception e) {
        android.util.Log.w("JBTextViewManager", "Could not apply font family: " + mFontFamily + ", error: " + e.getMessage());
      }
    }
    
    // Apply font weight (bold)
    if (mFontWeight != null) {
      switch (mFontWeight) {
        case "bold":
        case "700":
        case "800":
        case "900":
          spannable.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, text.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
          android.util.Log.d("JBTextViewManager", "Applied bold weight span");
          break;
      }
    }
    
    // Apply font style (italic)
    if (mFontStyle != null && "italic".equals(mFontStyle)) {
      spannable.setSpan(new StyleSpan(android.graphics.Typeface.ITALIC), 0, text.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      android.util.Log.d("JBTextViewManager", "Applied italic style span");
    }
    
    return spannable;
  }
}
