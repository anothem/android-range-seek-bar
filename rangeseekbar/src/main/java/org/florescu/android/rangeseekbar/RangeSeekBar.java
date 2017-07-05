/*
Copyright 2015 Alex Florescu
Copyright 2014 Stephan Tittel and Yahoo Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package org.florescu.android.rangeseekbar;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.ColorRes;
import android.support.annotation.NonNull;
import android.support.v4.util.Pair;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import org.florescu.android.util.BitmapUtil;
import org.florescu.android.util.PixelUtil;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Widget that lets users select a minimum and maximum value on a given numerical range.
 * The range value types can be one of Long, Double, Integer, Float, Short, Byte or BigDecimal.<br>
 * <br>
 * Improved {@link android.view.MotionEvent} handling for smoother use, anti-aliased painting for improved aesthetics.
 *
 * @param <T> The Number type of the range values. One of Long, Double, Integer, Float, Short, Byte or BigDecimal.
 * @author Stephan Tittel (stephan.tittel@kom.tu-darmstadt.de)
 * @author Peter Sinnott (psinnott@gmail.com)
 * @author Thomas Barrasso (tbarrasso@sevenplusandroid.org)
 * @author Alex Florescu (alex@florescu.org)
 * @author Michael Keppler (bananeweizen@gmx.de)
 */
public class RangeSeekBar<T extends Number> extends android.support.v7.widget.AppCompatImageView {
  /**
   * Default color of a {@link RangeSeekBar}, #FF33B5E5. This is also known as "Ice Cream Sandwich" blue.
   */
  public static final int ACTIVE_COLOR = Color.argb(0xFF, 0x00, 0xDC, 0xE8);
  /**
   * An invalid pointer id.
   */
  public static final int INVALID_POINTER_ID = 255;

  public enum RangeType {
    LINEAR,
    PREDEFINED,
    CUBIC,
    GENERATED
  }

  // Localized constants from MotionEvent for compatibility
  // with API < 8 "Froyo".
  public static final int ACTION_POINTER_INDEX_MASK = 0x0000ff00, ACTION_POINTER_INDEX_SHIFT = 8;

  public static final Integer DEFAULT_MINIMUM = 0;
  public static final Integer DEFAULT_MAXIMUM = 100;
  public static final int HEIGHT_IN_DP = 30;
  public static final int TEXT_LATERAL_PADDING_IN_DP = 0;
  public static final double DEFAULT_MINIMUM_DISTANCE = 0.01;

  private static final int INITIAL_PADDING_IN_DP = 16;
  private static final float DEFAULT_TEXT_SIZE_IN_SP = 11.3f;
  private static final int DEFAULT_TEXT_DISTANCE_TO_BUTTON_IN_DP = 8;
  private static final int DEFAULT_TEXT_DISTANCE_TO_TOP_IN_DP = 8;
  private static final int DEFAULT_TEXT_SEPERATION_IN_DP = 8;
  private static final double DEFAULT_STEP = 1d;
  private static final double DEFAULT_SNAP_TOLERANCE_PERCENT = 10d;
  private static final List<Integer> DEFAULT_INCREMENTS = Arrays.asList(100, 1000, 10000);
  private static final List<Integer> DEFAULT_INCREMENT_RANGES = Arrays.asList(10000, 100000);

  private static final int LINE_HEIGHT_IN_DP = 2;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint shadowPaint = new Paint();

  private static final RangeType DEFAULT_RANGE_TYPE = RangeType.LINEAR;

  private Bitmap thumbImage;
  private Bitmap thumbPressedImage;
  private Bitmap thumbDisabledImage;

  private float mThumbHalfWidth;
  private float mThumbHalfHeight;

  private float padding;
  private T absoluteMinValue, absoluteMaxValue;
  private NumberType numberType;
  private double absoluteMinValuePrim, absoluteMaxValuePrim;
  private double normalizedMinValue = 0d;
  private double normalizedMaxValue = 1d;
  private Thumb pressedThumb = null;
  private boolean notifyWhileDragging = false;
  private OnRangeSeekBarChangeListener<T> listener;

  private float mDownMotionX;

  private int mActivePointerId = INVALID_POINTER_ID;

  private int mScaledTouchSlop;

  private boolean mIsDragging;

  private int mTextOffset;
  private int mTextSize;
  private int mDistanceToTop;
  private RectF mRect;

  private boolean mSingleThumb;
  private boolean mAlwaysActive;
  private boolean mShowLabels;
  private boolean mShowTextAboveThumbs;
  private float mInternalPad;
  private int mActiveColor;
  private int mDefaultColor;
  private int mTextAboveThumbsColor;
  private int offset;
  private float textSeperation;
  private double step;
  private double snapTolerance;
  private double minimumDistance;

  private boolean mThumbShadow;
  private int mThumbShadowXOffset;
  private int mThumbShadowYOffset;
  private int mThumbShadowBlur;
  private Path mThumbShadowPath;
  private Path mTranslatedThumbShadowPath = new Path();
  private Matrix mThumbShadowMatrix = new Matrix();

  private RangeType rangeType;
  private ArrayList<T> predefinedRangeValues;
  private String customMinValueLabel;
  private String customMaxValueLabel;
  private double cubicMultiplier = 1.0;
  private int minimumCubicStepDigits = 3;
  private int maximumCubicRangeValue = 1000000;

  private List<Integer> increments;
  private List<Integer> incrementRanges;

  private boolean mActivateOnDefaultValues;

  private NumberFormat formatter;

  public RangeSeekBar(Context context) {
    super(context);
    init(context, null);
  }

  public RangeSeekBar(Context context, AttributeSet attrs) {
    super(context, attrs);
    init(context, attrs);
  }

  public RangeSeekBar(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    init(context, attrs);
  }

  @SuppressWarnings("unchecked")
  private T extractNumericValueFromAttributes(TypedArray a, int attribute, int defaultValue) {
    TypedValue tv = a.peekValue(attribute);
    if (tv == null) {
      return (T) Integer.valueOf(defaultValue);
    }

    int type = tv.type;
    if (type == TypedValue.TYPE_FLOAT) {
      return (T) Float.valueOf(a.getFloat(attribute, defaultValue));
    } else {
      return (T) Integer.valueOf(a.getInteger(attribute, defaultValue));
    }
  }

  private void init(Context context, AttributeSet attrs) {
    float barHeight;
    int thumbNormal = R.drawable.seek_thumb;
    int thumbPressed = R.drawable.seek_thumb_pressed;
    int thumbDisabled = R.drawable.seek_thumb_disabled;
    int thumbShadowColor;
    int defaultShadowColor = Color.argb(75, 0, 0, 0);
    int defaultShadowYOffset = PixelUtil.dpToPx(context, 2);
    int defaultShadowXOffset = PixelUtil.dpToPx(context, 0);
    int defaultShadowBlur = PixelUtil.dpToPx(context, 2);

    offset = PixelUtil.dpToPx(context, TEXT_LATERAL_PADDING_IN_DP);
    textSeperation = PixelUtil.dpToPx(context, DEFAULT_TEXT_SEPERATION_IN_DP);
    step = DEFAULT_STEP;
    snapTolerance = DEFAULT_SNAP_TOLERANCE_PERCENT / 100;
    minimumDistance = DEFAULT_MINIMUM_DISTANCE;
    increments =  DEFAULT_INCREMENTS;
    incrementRanges = DEFAULT_INCREMENT_RANGES;

    if (attrs == null) {
      rangeType = DEFAULT_RANGE_TYPE;
      setRangeToDefaultValues();
      mInternalPad = PixelUtil.dpToPx(context, INITIAL_PADDING_IN_DP);
      barHeight = PixelUtil.dpToPx(context, LINE_HEIGHT_IN_DP);
      mActiveColor = ACTIVE_COLOR;
      mDefaultColor = Color.parseColor("#e5e8eb");
      mAlwaysActive = true;
      mShowTextAboveThumbs = true;
      mTextAboveThumbsColor = Color.WHITE;
      thumbShadowColor = defaultShadowColor;
      mThumbShadowXOffset = defaultShadowXOffset;
      mThumbShadowYOffset = defaultShadowYOffset;
      mThumbShadowBlur = defaultShadowBlur;
      mActivateOnDefaultValues = true;
    } else {
      TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.RangeSeekBar, 0, 0);
      try {
        switch (a.getInt(R.styleable.RangeSeekBar_rangeType, 0)) {
          case 0:
            rangeType = RangeType.LINEAR;
            break;
          case 1:
            rangeType = RangeType.PREDEFINED;
            break;
          case 2:
            rangeType = RangeType.CUBIC;
            break;
          default:
            rangeType = RangeType.LINEAR;
        }
        setRangeValues(
            extractNumericValueFromAttributes(a, R.styleable.RangeSeekBar_absoluteMinValue, DEFAULT_MINIMUM),
            extractNumericValueFromAttributes(a, R.styleable.RangeSeekBar_absoluteMaxValue, DEFAULT_MAXIMUM)
        );
        mShowTextAboveThumbs = a.getBoolean(R.styleable.RangeSeekBar_valuesAboveThumbs, true);
        mTextAboveThumbsColor = a.getColor(R.styleable.RangeSeekBar_textAboveThumbsColor, Color.WHITE);
        mSingleThumb = a.getBoolean(R.styleable.RangeSeekBar_singleThumb, false);
        mShowLabels = a.getBoolean(R.styleable.RangeSeekBar_showLabels, true);
        mInternalPad = PixelUtil.dpToPx(context, a.getInt(R.styleable.RangeSeekBar_internalPadding, INITIAL_PADDING_IN_DP));
        barHeight = PixelUtil.dpToPx(context, a.getInt(R.styleable.RangeSeekBar_barHeight, LINE_HEIGHT_IN_DP));
        mActiveColor = a.getColor(R.styleable.RangeSeekBar_activeColor, ACTIVE_COLOR);
        mDefaultColor = a.getColor(R.styleable.RangeSeekBar_defaultColor, Color.parseColor("#e5e8eb"));
        mAlwaysActive = a.getBoolean(R.styleable.RangeSeekBar_alwaysActive, true);
        customMinValueLabel = a.getString(R.styleable.RangeSeekBar_minValueLabel);
        customMaxValueLabel = a.getString(R.styleable.RangeSeekBar_maxValueLabel);

        Drawable normalDrawable = a.getDrawable(R.styleable.RangeSeekBar_thumbNormal);
        if (normalDrawable != null) {
          thumbImage = BitmapUtil.drawableToBitmap(normalDrawable);
        }
        Drawable disabledDrawable = a.getDrawable(R.styleable.RangeSeekBar_thumbDisabled);
        if (disabledDrawable != null) {
          thumbDisabledImage = BitmapUtil.drawableToBitmap(disabledDrawable);
        }
        Drawable pressedDrawable = a.getDrawable(R.styleable.RangeSeekBar_thumbPressed);
        if (pressedDrawable != null) {
          thumbPressedImage = BitmapUtil.drawableToBitmap(pressedDrawable);
        }
        mThumbShadow = a.getBoolean(R.styleable.RangeSeekBar_thumbShadow, false);
        thumbShadowColor = a.getColor(R.styleable.RangeSeekBar_thumbShadowColor, defaultShadowColor);
        mThumbShadowXOffset = a.getDimensionPixelSize(R.styleable.RangeSeekBar_thumbShadowXOffset, defaultShadowXOffset);
        mThumbShadowYOffset = a.getDimensionPixelSize(R.styleable.RangeSeekBar_thumbShadowYOffset, defaultShadowYOffset);
        mThumbShadowBlur = a.getDimensionPixelSize(R.styleable.RangeSeekBar_thumbShadowBlur, defaultShadowBlur);

        mActivateOnDefaultValues = a.getBoolean(R.styleable.RangeSeekBar_activateOnDefaultValues, true);
      } finally {
        a.recycle();
      }
    }

    Resources resources = getResources();
    int px = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 18, resources.getDisplayMetrics());

    if (thumbImage == null) {
      thumbImage = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
      Drawable thumbDrawableNormal = getResources().getDrawable(thumbNormal);
      thumbDrawableNormal.setBounds(0, 0, px, px);
      thumbDrawableNormal.draw(new Canvas(thumbImage));
    }
    if (thumbPressedImage == null) {
      Drawable thumbDrawablePressed = getResources().getDrawable(thumbPressed);
      thumbDrawablePressed.setBounds(0, 0, px, px);
      thumbPressedImage = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
      thumbDrawablePressed.draw(new Canvas(thumbPressedImage));
    }
    if (thumbDisabledImage == null) {
      Drawable thumbDrawableDisabled = getResources().getDrawable(thumbDisabled);
      thumbDrawableDisabled.setBounds(0, 0, px, px);
      thumbDisabledImage = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
      thumbDrawableDisabled.draw(new Canvas(thumbDisabledImage));
    }

    mThumbHalfWidth = 0.5f * thumbImage.getWidth();
    mThumbHalfHeight = 0.5f * thumbImage.getHeight();

    setValuePrimAndNumberType();

    mTextSize = PixelUtil.spToPx(context, DEFAULT_TEXT_SIZE_IN_SP);
    mDistanceToTop = PixelUtil.dpToPx(context, DEFAULT_TEXT_DISTANCE_TO_TOP_IN_DP);
    mTextOffset = !mShowTextAboveThumbs ? 0 : this.mTextSize + PixelUtil.dpToPx(context,
        DEFAULT_TEXT_DISTANCE_TO_BUTTON_IN_DP) + this.mDistanceToTop;

    mRect = new RectF(padding,
        mTextOffset + mThumbHalfHeight - barHeight / 2,
        getWidth() - padding,
        mTextOffset + mThumbHalfHeight + barHeight / 2);

    // make RangeSeekBar focusable. This solves focus handling issues in case EditText widgets are being used along with the RangeSeekBar within ScrollViews.
    setFocusable(true);
    setFocusableInTouchMode(true);
    mScaledTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();

    if (mThumbShadow) {
      // We need to remove hardware acceleration in order to blur the shadow
      setLayerType(LAYER_TYPE_SOFTWARE, null);
      shadowPaint.setColor(thumbShadowColor);
      shadowPaint.setMaskFilter(new BlurMaskFilter(mThumbShadowBlur, BlurMaskFilter.Blur.NORMAL));
      mThumbShadowPath = new Path();
      mThumbShadowPath.addCircle(0,
          0,
          mThumbHalfHeight,
          Path.Direction.CW);
    }
  }

  public void setRangeValues(T minValue, T maxValue) {
    setRangeValues(minValue, maxValue, true);
  }

  public void setRangeValues(T minValue, T maxValue, boolean generateNewValues) {
    this.absoluteMinValue = minValue;
    this.absoluteMaxValue = maxValue;
    if (minValue.intValue() >= maxValue.intValue()) {
      throw new IllegalArgumentException("Min value must be less than max value");
    }
    if (rangeType == RangeType.CUBIC) {
      cubicMultiplier = Math.cbrt(absoluteMaxValue.intValue() - absoluteMinValue.intValue());
    } else if (rangeType == RangeType.GENERATED && generateNewValues) {
      generateCustomSteps();
    }
    updateMinDistance();
    setValuePrimAndNumberType();
    invalidate();
  }

  public void setIncrements(@NonNull List<Integer> increments, @NonNull List<Integer> incrementRanges) {
    if (increments.size() != (incrementRanges.size() + 1)) {
      throw new IllegalArgumentException("increments must have exactly one value more than incrementRanges");
    }
    this.increments = increments;
    this.incrementRanges = incrementRanges;
    generateCustomSteps();
  }

  private void generateCustomSteps() {
    int stepValue = absoluteMinValue.intValue();
    ArrayList<Number> steps = new ArrayList<>();
    int increment = increments.get(0);

    steps.add(stepValue);
    while (stepValue < absoluteMaxValue.intValue()) {
      stepValue += increment;
      steps.add(stepValue);

      for (int i = 0; i < incrementRanges.size(); i++) {
        if (stepValue >= incrementRanges.get(i)) {
          increment = increments.get(i + 1);
        }
      }
    }

    setPredefinedRangeValues((ArrayList<T>) steps);
  }

  public void setTextAboveThumbsColor(int textAboveThumbsColor) {
    this.mTextAboveThumbsColor = textAboveThumbsColor;
    invalidate();
  }

  public void setTextAboveThumbsColorResource(@ColorRes int resId) {
    setTextAboveThumbsColor(getResources().getColor(resId));
  }

  @SuppressWarnings("unchecked")
  // only used to set default values when initialised from XML without any values specified
  private void setRangeToDefaultValues() {
    this.absoluteMinValue = (T) DEFAULT_MINIMUM;
    this.absoluteMaxValue = (T) DEFAULT_MAXIMUM;
    setValuePrimAndNumberType();
  }

  private void setValuePrimAndNumberType() {
    absoluteMinValuePrim = absoluteMinValue.doubleValue();
    absoluteMaxValuePrim = absoluteMaxValue.doubleValue();
    numberType = NumberType.fromNumber(absoluteMinValue);
  }

  @SuppressWarnings("unused")
  public void resetSelectedValues() {
    setSelectedMinValue(absoluteMinValue);
    setSelectedMaxValue(absoluteMaxValue);
  }

  @SuppressWarnings("unused")
  public boolean isNotifyWhileDragging() {
    return notifyWhileDragging;
  }

  /**
   * Should the widget notify the listener callback while the user is still dragging a thumb? Default is false.
   */
  @SuppressWarnings("unused")
  public void setNotifyWhileDragging(boolean flag) {
    this.notifyWhileDragging = flag;
  }

  /**
   * Returns the absolute minimum value of the range that has been set at construction time.
   *
   * @return The absolute minimum value of the range.
   */
  public T getAbsoluteMinValue() {
    return absoluteMinValue;
  }

  /**
   * Returns the absolute maximum value of the range that has been set at construction time.
   *
   * @return The absolute maximum value of the range.
   */
  public T getAbsoluteMaxValue() {
    return absoluteMaxValue;
  }

  /**
   * Returns the currently selected min value.
   *
   * @return The currently selected min value.
   */
  public T getSelectedMinValue() {
    return normalizedToValue(normalizedMinValue);
  }

  /**
   * Sets the currently selected minimum value. The widget will be invalidated and redrawn.
   *
   * @param value The Number value to set the minimum value to. Will be clamped to given absolute minimum/maximum range.
   */
  public void setSelectedMinValue(T value) {
    // in case absoluteMinValue == absoluteMaxValue, avoid division by zero when normalizing.
    if (0 == (absoluteMaxValuePrim - absoluteMinValuePrim)) {
      setNormalizedMinValue(0d);
    } else {
      setNormalizedMinValue(valueToNormalized(value));
    }
  }

  /**
   * Sets the min distance sliders can be from each other
   */
  private void updateMinDistance() {
    switch (rangeType) {
      case LINEAR:
        minimumDistance = normalizedMaxValue / ((absoluteMaxValue.doubleValue() - absoluteMinValue.doubleValue()) / step);
        break;
      case GENERATED:
      case PREDEFINED:
        if (predefinedRangeValues != null) {
          minimumDistance = normalizedMaxValue / (predefinedRangeValues.size() - 1);
        }
        break;
      case CUBIC:
        minimumDistance = DEFAULT_MINIMUM_DISTANCE;
        break;
    }
  }

  /**
   * Returns the currently selected max value.
   *
   * @return The currently selected max value.
   */
  public T getSelectedMaxValue() {
    return normalizedToValue(normalizedMaxValue);
  }

  /**
   * Sets the currently selected maximum value. The widget will be invalidated and redrawn.
   *
   * @param value The Number value to set the maximum value to. Will be clamped to given absolute minimum/maximum range.
   */
  public void setSelectedMaxValue(T value) {
    // in case absoluteMinValue == absoluteMaxValue, avoid division by zero when normalizing.
    if (0 == (absoluteMaxValuePrim - absoluteMinValuePrim)) {
      setNormalizedMaxValue(1d);
    } else {
      setNormalizedMaxValue(valueToNormalized(value));
    }
  }

  /**
   * Sets the seekbar step
   *
   * @param step the step to display
   */
  public void setStep(double step) {
    this.step = step;
    updateMinDistance();
    invalidate();
  }

  /**
   * Registers given listener callback to notify about changed selected values.
   *
   * @param listener The listener to notify about changed selected values.
   */
  @SuppressWarnings("unused")
  public void setOnRangeSeekBarChangeListener(OnRangeSeekBarChangeListener<T> listener) {
    this.listener = listener;
  }

  /**
   * Set the path that defines the shadow of the thumb. This path should be defined assuming
   * that the center of the shadow is at the top left corner (0,0) of the canvas. The
   * {@link #drawThumbShadow(float, Canvas)} method will place the shadow appropriately.
   *
   * @param thumbShadowPath The path defining the thumb shadow
   */
  @SuppressWarnings("unused")
  public void setThumbShadowPath(Path thumbShadowPath) {
    this.mThumbShadowPath = thumbShadowPath;
  }

  /**
   * Set a text formatter to display text above thumbs in the desired format
   *
   * @param formatter The formatter for the numbers
   */
  @SuppressWarnings("unused")
  public void setTextFormatter(NumberFormat formatter) {
    this.formatter = formatter;
  }

  /**
   * Handles thumb selection and movement. Notifies listener callback on certain events.
   */
  @Override
  public boolean onTouchEvent(@NonNull MotionEvent event) {

    if (!isEnabled()) {
      return false;
    }

    int pointerIndex;

    final int action = event.getAction();
    switch (action & MotionEvent.ACTION_MASK) {

      case MotionEvent.ACTION_DOWN:
        // Remember where the motion event started
        mActivePointerId = event.getPointerId(event.getPointerCount() - 1);
        pointerIndex = event.findPointerIndex(mActivePointerId);
        mDownMotionX = event.getX(pointerIndex);

        pressedThumb = evalPressedThumb(mDownMotionX);

        // Only handle thumb presses.
        if (pressedThumb == null) {
          pressedThumb = getClosestThumb(mDownMotionX);
          if (pressedThumb == null) {
            return false;
          }
        }

        if (listener != null) {
          listener.onRangeSeekBarPressed(this, Thumb.MAX.equals(pressedThumb) ? getSelectedMaxValue() : getSelectedMinValue(), pressedThumb);
        }

        setPressed(true);
        invalidate();
        onStartTrackingTouch();
        trackTouchEvent(event);
        attemptClaimDrag();

        break;
      case MotionEvent.ACTION_MOVE:
        if (pressedThumb != null) {

          if (mIsDragging) {
            trackTouchEvent(event);
          } else {
            // Scroll to follow the motion event
            pointerIndex = event.findPointerIndex(mActivePointerId);
            final float x = event.getX(pointerIndex);

            if (Math.abs(x - mDownMotionX) > mScaledTouchSlop) {
              setPressed(true);
              invalidate();
              onStartTrackingTouch();
              trackTouchEvent(event);
              attemptClaimDrag();
            }
          }

          if (notifyWhileDragging && listener != null) {
            listener.onRangeSeekBarValueChanged(this, Thumb.MAX.equals(pressedThumb) ? getSelectedMaxValue() : getSelectedMinValue(), pressedThumb);
          }
        }
        break;
      case MotionEvent.ACTION_UP:
        if (mIsDragging) {
          trackTouchEvent(event);
          onStopTrackingTouch();
          setPressed(false);
        } else {
          // Touch up when we never crossed the touch slop threshold
          // should be interpreted as a tap-seek to that location.
          onStartTrackingTouch();
          trackTouchEvent(event);
          onStopTrackingTouch();
        }

        if (listener != null) {
          listener.onRangeSeekBarValueChanged(this, Thumb.MAX.equals(pressedThumb) ? getSelectedMaxValue() : getSelectedMinValue(), pressedThumb);
        }

        pressedThumb = null;
        invalidate();

        break;
      case MotionEvent.ACTION_POINTER_DOWN: {
        final int index = event.getPointerCount() - 1;
        // final int index = ev.getActionIndex();
        mDownMotionX = event.getX(index);
        mActivePointerId = event.getPointerId(index);
        invalidate();
        break;
      }
      case MotionEvent.ACTION_POINTER_UP:
        onSecondaryPointerUp(event);
        invalidate();
        break;
      case MotionEvent.ACTION_CANCEL:
        if (mIsDragging) {
          onStopTrackingTouch();
          setPressed(false);
        }
        invalidate(); // see above explanation
        break;
    }
    return true;
  }

  private void onSecondaryPointerUp(MotionEvent ev) {
    final int pointerIndex = (ev.getAction() & ACTION_POINTER_INDEX_MASK) >> ACTION_POINTER_INDEX_SHIFT;

    final int pointerId = ev.getPointerId(pointerIndex);
    if (pointerId == mActivePointerId) {
      // This was our active pointer going up. Choose
      // a new active pointer and adjust accordingly.
      // TODO: Make this decision more intelligent.
      final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
      mDownMotionX = ev.getX(newPointerIndex);
      mActivePointerId = ev.getPointerId(newPointerIndex);
    }
  }

  private void trackTouchEvent(MotionEvent event) {
    final int pointerIndex = event.findPointerIndex(mActivePointerId);
    final float x = event.getX(pointerIndex);

    if (Thumb.MIN.equals(pressedThumb) && !mSingleThumb) {
      setNormalizedMinValue(screenToNormalized(x + mThumbHalfWidth));
    } else if (Thumb.MAX.equals(pressedThumb)) {
      double normalized = screenToNormalized(x - mThumbHalfWidth + (mSingleThumb ? (1 - x / ((float) getWidth())) * padding : 0));
      setNormalizedMaxValue(normalized);
    }
  }

  /**
   * Tries to claim the user's drag motion, and requests disallowing any ancestors from stealing events in the drag.
   */
  private void attemptClaimDrag() {
    if (getParent() != null) {
      getParent().requestDisallowInterceptTouchEvent(true);
    }
  }

  /**
   * This is called when the user has started touching this widget.
   */
  void onStartTrackingTouch() {
    mIsDragging = true;
  }

  /**
   * This is called when the user either releases his touch or the touch is canceled.
   */
  void onStopTrackingTouch() {
    mIsDragging = false;
  }

  /**
   * Ensures correct size of the widget.
   */
  @Override
  protected synchronized void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int width = 200;
    if (MeasureSpec.UNSPECIFIED != MeasureSpec.getMode(widthMeasureSpec)) {
      width = MeasureSpec.getSize(widthMeasureSpec);
    }

    int height = thumbImage.getHeight()
        + (!mShowTextAboveThumbs ? 0 : PixelUtil.dpToPx(getContext(), HEIGHT_IN_DP))
        + (mThumbShadow ? mThumbShadowYOffset + mThumbShadowBlur : 0);
    if (MeasureSpec.UNSPECIFIED != MeasureSpec.getMode(heightMeasureSpec)) {
      height = Math.min(height, MeasureSpec.getSize(heightMeasureSpec));
    }
    setMeasuredDimension(width, height);
  }

  /**
   * Draws the widget on the given canvas.
   */
  @Override
  protected synchronized void onDraw(@NonNull Canvas canvas) {
    super.onDraw(canvas);

    paint.setTextSize(mTextSize);
    paint.setStyle(Style.FILL);
    paint.setColor(mDefaultColor);
    paint.setAntiAlias(true);
    float minMaxLabelSize = 0;

    if (mShowLabels) {
      // draw min and max labels
      String minLabel = getContext().getString(R.string.demo_min_label);
      String maxLabel = getContext().getString(R.string.demo_max_label);
      minMaxLabelSize = Math.max(paint.measureText(minLabel), paint.measureText(maxLabel));
      float minMaxHeight = mTextOffset + mThumbHalfHeight + mTextSize / 3;
      canvas.drawText(minLabel, 0, minMaxHeight, paint);
      canvas.drawText(maxLabel, getWidth() - minMaxLabelSize, minMaxHeight, paint);
    }

    padding = mInternalPad + minMaxLabelSize;

    // draw seek bar background line
    mRect.left = padding;
    mRect.right = getWidth() - padding;
    canvas.drawRect(mRect, paint);

    padding = mThumbHalfWidth + minMaxLabelSize;

    boolean selectedValuesAreDefault = (getSelectedMinValue().equals(getAbsoluteMinValue()) &&
        getSelectedMaxValue().equals(getAbsoluteMaxValue()));

    int colorToUseForButtonsAndHighlightedLine = !isEnabled() ? mDefaultColor : (!mAlwaysActive && !mActivateOnDefaultValues && selectedValuesAreDefault ?
        mDefaultColor : // default values
        mActiveColor);   // non default, filter is active


    padding = mInternalPad + minMaxLabelSize + (mThumbHalfWidth * 2);

    // draw seek bar active range line
    mRect.left = normalizedToScreen(normalizedMinValue) - mThumbHalfWidth;
    mRect.right = normalizedToScreen(normalizedMaxValue) + mThumbHalfWidth;

    paint.setColor(colorToUseForButtonsAndHighlightedLine);
    canvas.drawRect(mRect, paint);

    // draw minimum thumb (& shadow if requested) if not a single thumb control
    if (!mSingleThumb) {
      if (mThumbShadow) {
        drawThumbShadow(normalizedToScreen(normalizedMinValue), canvas);
      }
      drawThumb(normalizedToScreen(normalizedMinValue), Thumb.MIN.equals(pressedThumb), canvas,
          selectedValuesAreDefault, false);
    }

    // draw maximum thumb & shadow (if necessary)
    if (mThumbShadow) {
      drawThumbShadow(normalizedToScreen(normalizedMaxValue), canvas);
    }
    drawThumb(normalizedToScreen(normalizedMaxValue), Thumb.MAX.equals(pressedThumb), canvas,
        selectedValuesAreDefault, true);

    // draw the text
    if (mShowTextAboveThumbs) {
      paint.setTextSize(mTextSize);
      paint.setColor(mTextAboveThumbsColor);

      drawThumbText(canvas);
    }

  }

  /**
   * Overridden to save instance state when device orientation changes. This method is called automatically if you assign an id to the RangeSeekBar widget using the {@link #setId(int)} method. Other members of this class than the normalized min and max values don't need to be saved.
   */
  @Override
  protected Parcelable onSaveInstanceState() {
    final Bundle bundle = new Bundle();
    bundle.putParcelable("SUPER", super.onSaveInstanceState());
    bundle.putDouble("MIN", normalizedMinValue);
    bundle.putDouble("MAX", normalizedMaxValue);
    return bundle;
  }

  /**
   * Overridden to restore instance state when device orientation changes. This method is called automatically if you assign an id to the RangeSeekBar widget using the {@link #setId(int)} method.
   */
  @Override
  protected void onRestoreInstanceState(Parcelable parcel) {
    final Bundle bundle = (Bundle) parcel;
    super.onRestoreInstanceState(bundle.getParcelable("SUPER"));
    normalizedMinValue = bundle.getDouble("MIN");
    normalizedMaxValue = bundle.getDouble("MAX");
  }

  /**
   * Draws the "normal" resp. "pressed" thumb image on specified x-coordinate.
   *
   * @param screenCoord The x-coordinate in screen space where to draw the image.
   * @param pressed     Is the thumb currently in "pressed" state?
   * @param canvas      The canvas to draw upon.
   */
  private void drawThumb(float screenCoord, boolean pressed, Canvas canvas, boolean areSelectedValuesDefault, boolean isMax) {
    Bitmap buttonToDraw;
    if (!isEnabled() || (!mActivateOnDefaultValues && areSelectedValuesDefault)) {
      buttonToDraw = thumbDisabledImage;
    } else {
      buttonToDraw = pressed ? thumbPressedImage : thumbImage;
    }

    canvas.drawBitmap(buttonToDraw, screenCoord - (isMax ? 0 : (mThumbHalfWidth * 2)),
        mTextOffset,
        paint);
  }

  /**
   * Draws a drop shadow beneath the slider thumb.
   *
   * @param screenCoord the x-coordinate of the slider thumb
   * @param canvas      the canvas on which to draw the shadow
   */
  private void drawThumbShadow(float screenCoord, Canvas canvas) {
    mThumbShadowMatrix.setTranslate(screenCoord + mThumbShadowXOffset, mTextOffset + mThumbHalfHeight + mThumbShadowYOffset);
    mTranslatedThumbShadowPath.set(mThumbShadowPath);
    mTranslatedThumbShadowPath.transform(mThumbShadowMatrix);
    canvas.drawPath(mTranslatedThumbShadowPath, shadowPaint);
  }

  /**
   * Decides which (if any) thumb is touched by the given x-coordinate.
   *
   * @param touchX The x-coordinate of a touch event in screen space.
   * @return The pressed thumb or null if none has been touched.
   */
  private Thumb evalPressedThumb(float touchX) {
    Thumb result = null;
    boolean minThumbPressed = isInThumbRange(touchX, normalizedMinValue);
    boolean maxThumbPressed = isInThumbRange(touchX, normalizedMaxValue);
    if (minThumbPressed && maxThumbPressed) {
      // if both thumbs are pressed (they lie on top of each other), choose the one with more room to drag. this avoids "stalling" the thumbs in a corner, not being able to drag them apart anymore.
      result = (touchX / getWidth() > 0.5f) ? Thumb.MIN : Thumb.MAX;
    } else if (minThumbPressed) {
      result = Thumb.MIN;
    } else if (maxThumbPressed) {
      result = Thumb.MAX;
    }
    return result;
  }

  /**
   * Decides if given x-coordinate in screen space needs to be interpreted as "within" the normalized thumb x-coordinate.
   *
   * @param touchX               The x-coordinate in screen space to check.
   * @param normalizedThumbValue The normalized x-coordinate of the thumb to check.
   * @return true if x-coordinate is in thumb range, false otherwise.
   */
  private boolean isInThumbRange(float touchX, double normalizedThumbValue) {
    return Math.abs(touchX - normalizedToScreen(normalizedThumbValue)) <= mThumbHalfWidth;
  }

  /**
   * Sets normalized min value to value so that 0 <= value <= normalized max value <= 1. The View will get invalidated when calling this method.
   *
   * @param value The new normalized min value to set.
   */
  private void setNormalizedMinValue(double value) {
    normalizedMinValue = Math.max(0d, Math.min(1d, Math.min(value, normalizedMaxValue - minimumDistance)));
    invalidate();
  }

  /**
   * Sets normalized max value to value so that 0 <= normalized min value <= value <= 1. The View will get invalidated when calling this method.
   *
   * @param value The new normalized max value to set.
   */
  private void setNormalizedMaxValue(double value) {
    normalizedMaxValue = Math.max(0d, Math.min(1d, Math.max(value, normalizedMinValue + (mSingleThumb ? 0 : minimumDistance))));
    invalidate();
  }

  /**
   * Converts a normalized value to a Number object in the value space between absolute minimum and maximum.
   */
  @SuppressWarnings("unchecked")
  private T normalizedToValue(double normalized) {
    double v = absoluteMinValuePrim + normalized * (absoluteMaxValuePrim - absoluteMinValuePrim);
    switch (rangeType) {
      case LINEAR:
        return (T) numberType.toNumber(Math.round(v / step) * step);
      case GENERATED:
      case PREDEFINED:
        if (predefinedRangeValues == null) return (T) numberType.toNumber(0);
        int index = (int)(normalized * (predefinedRangeValues.size() - 1));
        if (index >= predefinedRangeValues.size()) index = predefinedRangeValues.size() - 1;
        return predefinedRangeValues.get(index);
      case CUBIC:
        int minAddition = 0;
        int minimumAllowedValue = (int) Math.pow(10, minimumCubicStepDigits - 1);
        if (absoluteMinValue.doubleValue() < minimumAllowedValue) {
          if (normalized == 0) {
            return (T) numberType.toNumber(0);
          }
          minAddition = minimumAllowedValue;
        }
        double rawValue = Math.pow(normalized * cubicMultiplier, 3) + absoluteMinValue.doubleValue() + minAddition;
        int round = (int) Math.max(Math.pow(10, (int) Math.log10(rawValue) - 1), minimumAllowedValue);
        return (T) numberType.toNumber(((int) rawValue / round) * round);
    }
    return (T) numberType.toNumber(Math.round(v / step) * step);
  }

  /**
   * Converts the given Number value to a normalized double.
   *
   * @param value The Number value to normalize.
   * @return The normalized double.
   */
  private double valueToNormalized(T value) {
    if (0 == absoluteMaxValuePrim - absoluteMinValuePrim) {
      // prevent division by zero, simply return 0.
      return 0d;
    }
    if (rangeType == RangeType.PREDEFINED || rangeType == RangeType.GENERATED) {
      if (predefinedRangeValues == null) return 1.0;
      return predefinedRangeValues.indexOf(value) / (predefinedRangeValues.size() - 1.0);
    }
    return (value.doubleValue() - absoluteMinValuePrim) / (absoluteMaxValuePrim - absoluteMinValuePrim);
  }

  /**
   * Converts a normalized value into screen space.
   *
   * @param normalizedCoord The normalized value to convert.
   * @return The converted value in screen space.
   */
  private float normalizedToScreen(double normalizedCoord) {
    return (float) ((mSingleThumb ? 0 : padding) + normalizedCoord * (getWidth() - (mSingleThumb ? 1 : 2) * padding));
  }

  /**
   * Normalizes to screen accounting for long text
   *
   * @param normalizedCoord normalized value to convert
   * @param halfWidth the half width of the object being drawn
   * @return the normalized coord after accounting for min/max drawing range
   */
  private float normalizedToScreen(double normalizedCoord, float halfWidth) {
    normalizedCoord = normalizedToScreen(normalizedCoord);
    float normalizedPadding = padding / 2;
    float max = getWidth() - mThumbHalfWidth - normalizedPadding;
    if (normalizedCoord - halfWidth - (mSingleThumb ? 0 : normalizedPadding) < mThumbHalfWidth) {
      return (float) Math.max(halfWidth + (mSingleThumb ? -mThumbHalfWidth : mThumbHalfWidth + normalizedPadding), normalizedCoord);
    } else if (normalizedCoord + halfWidth > max) {
      return (float) Math.min(max - halfWidth, normalizedCoord);
    } else {
      return (float) normalizedCoord;
    }
  }

  /**
   * Draws text above thumbs accounting for collision
   *
   * @param canvas The canvas to draw on
   */
  private void drawThumbText(final Canvas canvas) {
    T selectedMinValue =  getSelectedMinValue();
    T selectedMaxValue =  getSelectedMaxValue();
    String minText = formatter == null ? String.valueOf(selectedMinValue) : formatter.format(selectedMinValue);
    String maxText = formatter == null ? String.valueOf(selectedMaxValue) : formatter.format(selectedMaxValue);

    if (customMinValueLabel != null && selectedMinValue.equals(absoluteMinValue)) {
      minText = customMinValueLabel;
    }
    if (customMaxValueLabel != null && selectedMaxValue.equals(absoluteMaxValue)) {
      maxText = customMaxValueLabel;
    }

    float minTextWidth = paint.measureText(minText) + offset;
    float minTextHalfWidth = minTextWidth / 2;
    float maxTextWidth = paint.measureText(maxText) + offset;
    float maxTextHalfWidth = maxTextWidth / 2;

    float minTextX = normalizedToScreen(normalizedMinValue, minTextHalfWidth) - minTextHalfWidth - mThumbHalfWidth;
    float maxTextX = normalizedToScreen(normalizedMaxValue, maxTextHalfWidth) - maxTextHalfWidth + mThumbHalfWidth;

    if (!mSingleThumb) {
      Pair<Float, Float> values = getTextOverlapOffset(minTextX, maxTextX, minTextWidth, maxTextWidth);
      minTextX = values.first;
      maxTextX = values.second;

      canvas.drawText(minText,
          minTextX,
          mDistanceToTop + mTextSize,
          paint);
    }
    canvas.drawText(maxText,
        maxTextX,
        mDistanceToTop + mTextSize,
        paint);
  }

  /**
   * Returns an offset for thumb text if the two texts are to overlap
   *
   * @param minTextX The X value for the min text
   * @param maxTextX The X value for the max text
   * @param minTextWidth The width of the text for the min thumb label
   * @param maxTextWidth the width of the text for the max thumb label
   * @return any offset necessary to properly display text without overlap
   */
  private Pair<Float, Float> getTextOverlapOffset(float minTextX, float maxTextX, float minTextWidth, float maxTextWidth) {
    float diff = textSeperation + minTextX + minTextWidth - maxTextX;
    if (diff > 0) {
      float normalizedPadding = padding / 2;
      float absoluteMaxX = getWidth() - maxTextWidth - normalizedPadding;
      float minOffset = diff * minTextWidth / (minTextWidth + maxTextWidth);
      float maxOffset = diff - minOffset;
      float newMin = minTextX - minOffset;
      float newMax = maxTextX + maxOffset;
      if (newMin < normalizedPadding) {
        minTextX = normalizedPadding;
        maxTextX += textSeperation + minTextWidth - maxTextX + normalizedPadding;
      } else if (newMax > absoluteMaxX) {
        maxTextX = absoluteMaxX;
        minTextX -= textSeperation + minTextX + minTextWidth - absoluteMaxX;
      } else {
        minTextX = newMin;
        maxTextX = newMax;
      }
    }
    return new Pair<>(minTextX, maxTextX);
  }

  /**
   * Gets the thumb nearest a touch in the SeekBar's range
   *
   * @param touchX The touch location on the SeekBar
   * @return The thumb closest to the touch location
   */
  private Thumb getClosestThumb(float touchX) {
    double xValue = screenToNormalized(touchX);
    double maxDiff = Math.abs(xValue - normalizedMaxValue);
    double minDiff = Math.abs(xValue - normalizedMinValue);
    if (mSingleThumb) {
      if (maxDiff <= snapTolerance) {
        return Thumb.MAX;
      }
    } else if (minDiff <= snapTolerance || maxDiff <= snapTolerance) {
      double diff = maxDiff - minDiff;
      if (diff == 0d) {
        return xValue < normalizedMinValue ? Thumb.MIN : Thumb.MAX;
      }
      return diff < 0 ? Thumb.MAX : Thumb.MIN;
    }
    return null;
  }

  /**
   * Converts screen space x-coordinates into normalized values.
   *
   * @param screenCoord The x-coordinate in screen space to convert.
   * @return The normalized value.
   */
  private double screenToNormalized(float screenCoord) {
    int width = getWidth();
    if (width <= 2 * padding) {
      // prevent division by zero, simply return 0.
      return 0d;
    } else {
      double result = (screenCoord - padding) / (width - 2 * padding);
      return Math.min(1d, Math.max(0d, result));
    }
  }

  /**
   * Sets the values for a predefined range type
   *
   * @param values the values to be set
   */
  public void setPredefinedRangeValues(ArrayList<T> values) {
    if (values.size() < 1) {
      throw new IllegalArgumentException("Predefined range values must have length >= 1");
    }
    this.predefinedRangeValues = values;
    if (rangeType != RangeType.GENERATED) {
      rangeType = RangeType.PREDEFINED;
    }
    setRangeValues(values.get(0), values.get(values.size() - 1), false);
    invalidate();
  }

  public void setRangeType(RangeType rangeType) {
    this.rangeType = rangeType;
    invalidate();
  }

  /**
   * Thumb constants (min and max).
   */
  public enum Thumb {
    MIN, MAX
  }

  /**
   * Utility enumeration used to convert between Numbers and doubles.
   *
   * @author Stephan Tittel (stephan.tittel@kom.tu-darmstadt.de)
   */
  private enum NumberType {
    LONG, DOUBLE, INTEGER, FLOAT, SHORT, BYTE, BIG_DECIMAL;

    public static <E extends Number> NumberType fromNumber(E value) throws IllegalArgumentException {
      if (value instanceof Long) {
        return LONG;
      }
      if (value instanceof Double) {
        return DOUBLE;
      }
      if (value instanceof Integer) {
        return INTEGER;
      }
      if (value instanceof Float) {
        return FLOAT;
      }
      if (value instanceof Short) {
        return SHORT;
      }
      if (value instanceof Byte) {
        return BYTE;
      }
      if (value instanceof BigDecimal) {
        return BIG_DECIMAL;
      }
      throw new IllegalArgumentException("Number class '" + value.getClass().getName() + "' is not supported");
    }

    public Number toNumber(double value) {
      switch (this) {
        case LONG:
          return (long) value;
        case DOUBLE:
          return value;
        case INTEGER:
          return (int) value;
        case FLOAT:
          return (float) value;
        case SHORT:
          return (short) value;
        case BYTE:
          return (byte) value;
        case BIG_DECIMAL:
          return BigDecimal.valueOf(value);
      }
      throw new InstantiationError("can't convert " + this + " to a Number object");
    }
  }

  public boolean isDefaultSelection() {
    return normalizedMaxValue == 1 && normalizedMinValue == 0;
  }

  /**
   * Callback listener interface to notify about changed range values.
   *
   * @param <T> The Number type the RangeSeekBar has been declared with.
   * @author Stephan Tittel (stephan.tittel@kom.tu-darmstadt.de)
   */
  public interface OnRangeSeekBarChangeListener<T> {
    void onRangeSeekBarValueChanged(RangeSeekBar<?> bar, T value, Thumb index);
    void onRangeSeekBarPressed(RangeSeekBar<?> bar, T value, Thumb index);
  }

}