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
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.ImageView;

import org.florescu.android.util.BitmapUtil;
import org.florescu.android.util.PixelUtil;

import java.math.BigDecimal;

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
public class RangeSeekBar<T extends Number> extends ImageView {
    /**
     * Default color of a {@link RangeSeekBar}, #FF33B5E5. This is also known as "Ice Cream Sandwich" blue.
     */
    public static final int ACTIVE_COLOR = Color.argb(0xFF, 0x33, 0xB5, 0xE5);
    /**
     * An invalid pointer id.
     */
    public static final int INVALID_POINTER_ID = 255;

    // Localized constants from MotionEvent for compatibility
    // with API < 8 "Froyo".
    public static final int ACTION_POINTER_INDEX_MASK = 0x0000ff00, ACTION_POINTER_INDEX_SHIFT = 8;

    public static final Integer DEFAULT_MINIMUM = 0;
    public static final Integer DEFAULT_MAXIMUM = 100;
    public static final int HEIGHT_IN_DP = 30;
    public static final int TEXT_LATERAL_PADDING_IN_DP = 3;

    private static final int INITIAL_PADDING_IN_DP = 8;
    private static final int DEFAULT_TEXT_SIZE_IN_DP = 14;
    private static final int DEFAULT_TEXT_DISTANCE_TO_BUTTON_IN_DP = 8;
    private static final int DEFAULT_TEXT_DISTANCE_TO_TOP_IN_DP = 8;

    private static final int LINE_HEIGHT_IN_DP = 1;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint();

    private Bitmap thumbImage;
    private Bitmap thumbPressedImage;
    private Bitmap thumbDisabledImage;

    private float thumbHalfWidth;
    private float thumbHalfHeight;

    private float padding;
    protected T absoluteMinValue, absoluteMaxValue;
    protected NumberType numberType;
    protected double absoluteMinValuePrim, absoluteMaxValuePrim;
    protected double normalizedMinValue = 0d;
    protected double normalizedMaxValue = 1d;
    protected double minDeltaForDefault = 0;
    private Thumb pressedThumb = null;
    private boolean notifyWhileDragging = false;
    private OnRangeSeekBarChangeListener<T> listener;

    private float downMotionX;

    private int activePointerId = INVALID_POINTER_ID;

    private int scaledTouchSlop;

    private boolean isDragging;

    private int textOffset;
    private int textSize;
    private int distanceToTop;
    private RectF rect;

    private boolean singleThumb;
    private boolean alwaysActive;
    private boolean showLabels;
    private boolean showTextAboveThumbs;
    private float internalPad;
    private int activeColor;
    private int defaultColor;
    private int textAboveThumbsColor;

    private boolean thumbShadow;
    private int thumbShadowXOffset;
    private int thumbShadowYOffset;
    private int thumbShadowBlur;
    private Path thumbShadowPath;
    private Path translatedThumbShadowPath = new Path();
    private Matrix thumbShadowMatrix = new Matrix();

    private boolean activateOnDefaultValues;


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
        int thumbNormal = R.drawable.seek_thumb_normal;
        int thumbPressed = R.drawable.seek_thumb_pressed;
        int thumbDisabled = R.drawable.seek_thumb_disabled;
        int thumbShadowColor;
        int defaultShadowColor = Color.argb(75, 0, 0, 0);
        int defaultShadowYOffset = PixelUtil.dpToPx(context, 2);
        int defaultShadowXOffset = PixelUtil.dpToPx(context, 0);
        int defaultShadowBlur = PixelUtil.dpToPx(context, 2);

        if (attrs == null) {
            setRangeToDefaultValues();
            internalPad = PixelUtil.dpToPx(context, INITIAL_PADDING_IN_DP);
            barHeight = PixelUtil.dpToPx(context, LINE_HEIGHT_IN_DP);
            activeColor = ACTIVE_COLOR;
            defaultColor = Color.GRAY;
            alwaysActive = false;
            showTextAboveThumbs = true;
            textAboveThumbsColor = Color.WHITE;
            thumbShadowColor = defaultShadowColor;
            thumbShadowXOffset = defaultShadowXOffset;
            thumbShadowYOffset = defaultShadowYOffset;
            thumbShadowBlur = defaultShadowBlur;
            activateOnDefaultValues = false;
        } else {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.RangeSeekBar, 0, 0);
            try {
                setRangeValues(
                        extractNumericValueFromAttributes(a, R.styleable.RangeSeekBar_absoluteMinValue, DEFAULT_MINIMUM),
                        extractNumericValueFromAttributes(a, R.styleable.RangeSeekBar_absoluteMaxValue, DEFAULT_MAXIMUM)
                );
                showTextAboveThumbs = a.getBoolean(R.styleable.RangeSeekBar_valuesAboveThumbs, true);
                textAboveThumbsColor = a.getColor(R.styleable.RangeSeekBar_textAboveThumbsColor, Color.WHITE);
                singleThumb = a.getBoolean(R.styleable.RangeSeekBar_singleThumb, false);
                showLabels = a.getBoolean(R.styleable.RangeSeekBar_showLabels, true);
                internalPad = a.getDimensionPixelSize(R.styleable.RangeSeekBar_internalPadding, INITIAL_PADDING_IN_DP);
                barHeight = a.getDimensionPixelSize(R.styleable.RangeSeekBar_barHeight, LINE_HEIGHT_IN_DP);
                activeColor = a.getColor(R.styleable.RangeSeekBar_activeColor, ACTIVE_COLOR);
                defaultColor = a.getColor(R.styleable.RangeSeekBar_defaultColor, Color.GRAY);
                alwaysActive = a.getBoolean(R.styleable.RangeSeekBar_alwaysActive, false);

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
                thumbShadow = a.getBoolean(R.styleable.RangeSeekBar_thumbShadow, false);
                thumbShadowColor = a.getColor(R.styleable.RangeSeekBar_thumbShadowColor, defaultShadowColor);
                thumbShadowXOffset = a.getDimensionPixelSize(R.styleable.RangeSeekBar_thumbShadowXOffset, defaultShadowXOffset);
                thumbShadowYOffset = a.getDimensionPixelSize(R.styleable.RangeSeekBar_thumbShadowYOffset, defaultShadowYOffset);
                thumbShadowBlur = a.getDimensionPixelSize(R.styleable.RangeSeekBar_thumbShadowBlur, defaultShadowBlur);

                activateOnDefaultValues = a.getBoolean(R.styleable.RangeSeekBar_activateOnDefaultValues, false);
            } finally {
                a.recycle();
            }
        }

        if (thumbImage == null) {
            thumbImage = BitmapFactory.decodeResource(getResources(), thumbNormal);
        }
        if (thumbPressedImage == null) {
            thumbPressedImage = BitmapFactory.decodeResource(getResources(), thumbPressed);
        }
        if (thumbDisabledImage == null) {
            thumbDisabledImage = BitmapFactory.decodeResource(getResources(), thumbDisabled);
        }

        thumbHalfWidth = 0.5f * thumbImage.getWidth();
        thumbHalfHeight = 0.5f * thumbImage.getHeight();

        setValuePrimAndNumberType();

        textSize = PixelUtil.dpToPx(context, DEFAULT_TEXT_SIZE_IN_DP);
        distanceToTop = PixelUtil.dpToPx(context, DEFAULT_TEXT_DISTANCE_TO_TOP_IN_DP);
        textOffset = !showTextAboveThumbs ? 0 : this.textSize + PixelUtil.dpToPx(context,
                DEFAULT_TEXT_DISTANCE_TO_BUTTON_IN_DP) + this.distanceToTop;

        rect = new RectF(padding,
                textOffset + thumbHalfHeight - barHeight / 2,
                getWidth() - padding,
                textOffset + thumbHalfHeight + barHeight / 2);

        // make RangeSeekBar focusable. This solves focus handling issues in case EditText widgets are being used along with the RangeSeekBar within ScrollViews.
        setFocusable(true);
        setFocusableInTouchMode(true);
        scaledTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();

        if (thumbShadow) {
            // We need to remove hardware acceleration in order to blur the shadow
            setLayerType(LAYER_TYPE_SOFTWARE, null);
            shadowPaint.setColor(thumbShadowColor);
            shadowPaint.setMaskFilter(new BlurMaskFilter(thumbShadowBlur, BlurMaskFilter.Blur.NORMAL));
            thumbShadowPath = new Path();
            thumbShadowPath.addCircle(0,
                    0,
                    thumbHalfHeight,
                    Path.Direction.CW);
        }
    }

    public void setRangeValues(T minValue, T maxValue) {
        this.absoluteMinValue = minValue;
        this.absoluteMaxValue = maxValue;
        setValuePrimAndNumberType();
    }

    public void setTextAboveThumbsColor(int textAboveThumbsColor) {
        this.textAboveThumbsColor = textAboveThumbsColor;
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

    public boolean isDragging() {
        return mIsDragging;
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
        this.thumbShadowPath = thumbShadowPath;
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
                activePointerId = event.getPointerId(event.getPointerCount() - 1);
                pointerIndex = event.findPointerIndex(activePointerId);
                downMotionX = event.getX(pointerIndex);

                pressedThumb = evalPressedThumb(downMotionX);

                // Only handle thumb presses.
                if (pressedThumb == null) {
                    return super.onTouchEvent(event);
                }

                setPressed(true);
                invalidate();
                onStartTrackingTouch();
                trackTouchEvent(event);
                attemptClaimDrag();

                break;
            case MotionEvent.ACTION_MOVE:
                if (pressedThumb != null) {

                    if (isDragging) {
                        trackTouchEvent(event);
                    } else {
                        // Scroll to follow the motion event
                        pointerIndex = event.findPointerIndex(activePointerId);
                        final float x = event.getX(pointerIndex);

                        if (Math.abs(x - downMotionX) > scaledTouchSlop) {
                            setPressed(true);
                            invalidate();
                            onStartTrackingTouch();
                            trackTouchEvent(event);
                            attemptClaimDrag();
                        }
                    }

                    if (notifyWhileDragging && listener != null) {
                        listener.onRangeSeekBarValuesChanged(this, getSelectedMinValue(), getSelectedMaxValue());
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                if (isDragging) {
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

                pressedThumb = null;
                invalidate();
                if (listener != null) {
                    listener.onRangeSeekBarValuesChanged(this, getSelectedMinValue(), getSelectedMaxValue());
                }
                break;
            case MotionEvent.ACTION_POINTER_DOWN: {
                final int index = event.getPointerCount() - 1;
                // final int index = ev.getActionIndex();
                downMotionX = event.getX(index);
                activePointerId = event.getPointerId(index);
                invalidate();
                break;
            }
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(event);
                invalidate();
                break;
            case MotionEvent.ACTION_CANCEL:
                if (isDragging) {
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
        if (pointerId == activePointerId) {
            // This was our active pointer going up. Choose
            // a new active pointer and adjust accordingly.
            // TODO: Make this decision more intelligent.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            downMotionX = ev.getX(newPointerIndex);
            activePointerId = ev.getPointerId(newPointerIndex);
        }
    }

    private void trackTouchEvent(MotionEvent event) {
        final int pointerIndex = event.findPointerIndex(activePointerId);
        final float x = event.getX(pointerIndex);

        if (Thumb.MIN.equals(pressedThumb) && !singleThumb) {
            setNormalizedMinValue(screenToNormalized(x));
        } else if (Thumb.MAX.equals(pressedThumb)) {
            setNormalizedMaxValue(screenToNormalized(x));
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
        isDragging = true;
    }

    /**
     * This is called when the user either releases his touch or the touch is canceled.
     */
    void onStopTrackingTouch() {
        isDragging = false;
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
                + (!showTextAboveThumbs ? 0 : PixelUtil.dpToPx(getContext(), HEIGHT_IN_DP))
                + (thumbShadow ? thumbShadowYOffset + thumbShadowBlur : 0);
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

        paint.setTextSize(textSize);
        paint.setStyle(Style.FILL);
        paint.setColor(defaultColor);
        paint.setAntiAlias(true);
        float minMaxLabelSize = 0;

        if (showLabels) {
            // draw min and max labels
            String minLabel = getContext().getString(R.string.demo_min_label);
            String maxLabel = getContext().getString(R.string.demo_max_label);
            minMaxLabelSize = Math.max(paint.measureText(minLabel), paint.measureText(maxLabel));
            float minMaxHeight = textOffset + thumbHalfHeight + textSize / 3;
            canvas.drawText(minLabel, 0, minMaxHeight, paint);
            canvas.drawText(maxLabel, getWidth() - minMaxLabelSize, minMaxHeight, paint);
        }
        padding = internalPad + minMaxLabelSize + thumbHalfWidth;

        // draw seek bar background line
        rect.left = padding;
        rect.right = getWidth() - padding;
        canvas.drawRect(rect, paint);

        boolean selectedValuesAreDefault = (normalizedMinValue <= minDeltaForDefault && normalizedMaxValue >= 1 - minDeltaForDefault);

        int colorToUseForButtonsAndHighlightedLine = !alwaysActive && !activateOnDefaultValues && selectedValuesAreDefault ?
                defaultColor : // default values
                activeColor;   // non default, filter is active

        // draw seek bar active range line
        rect.left = normalizedToScreen(normalizedMinValue);
        rect.right = normalizedToScreen(normalizedMaxValue);

        paint.setColor(colorToUseForButtonsAndHighlightedLine);
        canvas.drawRect(rect, paint);

        // draw minimum thumb (& shadow if requested) if not a single thumb control
        if (!singleThumb) {
            if (thumbShadow) {
                drawThumbShadow(normalizedToScreen(normalizedMinValue), canvas);
            }
            drawThumb(normalizedToScreen(normalizedMinValue), Thumb.MIN.equals(pressedThumb), canvas,
                    selectedValuesAreDefault);
        }

        // draw maximum thumb & shadow (if necessary)
        if (thumbShadow) {
            drawThumbShadow(normalizedToScreen(normalizedMaxValue), canvas);
        }
        drawThumb(normalizedToScreen(normalizedMaxValue), Thumb.MAX.equals(pressedThumb), canvas,
                selectedValuesAreDefault);

        // draw the text if sliders have moved from default edges
        if (showTextAboveThumbs && (activateOnDefaultValues || !selectedValuesAreDefault)) {
            paint.setTextSize(textSize);
            paint.setColor(textAboveThumbsColor);

            String minText = valueToString(getSelectedMinValue());
            String maxText = valueToString(getSelectedMaxValue());
            float minTextWidth = paint.measureText(minText);
            float maxTextWidth = paint.measureText(maxText);
            // keep the position so that the labels don't get cut off
            float minPosition = Math.max(0f, normalizedToScreen(normalizedMinValue) - minTextWidth * 0.5f);
            float maxPosition = Math.min(getWidth() - maxTextWidth, normalizedToScreen(normalizedMaxValue) - maxTextWidth * 0.5f);

            if (!singleThumb) {
                // check if the labels overlap, or are too close to each other
                int spacing = PixelUtil.dpToPx(getContext(), TEXT_LATERAL_PADDING_IN_DP);
                float overlap = minPosition + minTextWidth - maxPosition + spacing;
                if (overlap > 0f) {
                    // we could move them the same ("overlap * 0.5f")
                    // but we rather move more the one which is farther from the ends, as it has more space
                    minPosition -= overlap * normalizedMinValue / (normalizedMinValue + 1-normalizedMaxValue);
                    maxPosition += overlap * (1-normalizedMaxValue) / (normalizedMinValue + 1-normalizedMaxValue);
                }
                canvas.drawText(minText,
                        minPosition,
                        distanceToTop + textSize,
                        paint);

            }

            canvas.drawText(maxText,
                    maxPosition,
                    distanceToTop + textSize,
                    paint);
        }

    }

    protected String valueToString(T value) {
        return String.valueOf(value);
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
    private void drawThumb(float screenCoord, boolean pressed, Canvas canvas, boolean areSelectedValuesDefault) {
        Bitmap buttonToDraw;
        if (!activateOnDefaultValues && areSelectedValuesDefault) {
            buttonToDraw = thumbDisabledImage;
        } else {
            buttonToDraw = pressed ? thumbPressedImage : thumbImage;
        }

        canvas.drawBitmap(buttonToDraw, screenCoord - thumbHalfWidth,
                textOffset,
                paint);
    }

    /**
     * Draws a drop shadow beneath the slider thumb.
     *
     * @param screenCoord the x-coordinate of the slider thumb
     * @param canvas      the canvas on which to draw the shadow
     */
    private void drawThumbShadow(float screenCoord, Canvas canvas) {
        thumbShadowMatrix.setTranslate(screenCoord + thumbShadowXOffset, textOffset + thumbHalfHeight + thumbShadowYOffset);
        translatedThumbShadowPath.set(thumbShadowPath);
        translatedThumbShadowPath.transform(thumbShadowMatrix);
        canvas.drawPath(translatedThumbShadowPath, shadowPaint);
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
        return Math.abs(touchX - normalizedToScreen(normalizedThumbValue)) <= thumbHalfWidth;
    }

    /**
     * Sets normalized min value to value so that 0 <= value <= normalized max value <= 1. The View will get invalidated when calling this method.
     *
     * @param value The new normalized min value to set.
     */
    private void setNormalizedMinValue(double value) {
        normalizedMinValue = Math.max(0d, Math.min(1d, Math.min(value, normalizedMaxValue)));
        invalidate();
    }

    /**
     * Sets normalized max value to value so that 0 <= normalized min value <= value <= 1. The View will get invalidated when calling this method.
     *
     * @param value The new normalized max value to set.
     */
    private void setNormalizedMaxValue(double value) {
        normalizedMaxValue = Math.max(0d, Math.min(1d, Math.max(value, normalizedMinValue)));
        invalidate();
    }

    /**
     * Converts a normalized value to a Number object in the value space between absolute minimum and maximum.
     */
    @SuppressWarnings("unchecked")
    protected T normalizedToValue(double normalized) {
        double v = absoluteMinValuePrim + normalized * (absoluteMaxValuePrim - absoluteMinValuePrim);
        // TODO parameterize this rounding to allow variable decimal points
        return (T) numberType.toNumber(Math.round(v * 100) / 100d);
    }

    /**
     * Converts the given Number value to a normalized double.
     *
     * @param value The Number value to normalize.
     * @return The normalized double.
     */
    protected double valueToNormalized(T value) {
        if (0 == absoluteMaxValuePrim - absoluteMinValuePrim) {
            // prevent division by zero, simply return 0.
            return 0d;
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
        return (float) (padding + normalizedCoord * (getWidth() - 2 * padding));
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
     * Thumb constants (min and max).
     */
    private enum Thumb {
        MIN, MAX
    }

    /**
     * Utility enumeration used to convert between Numbers and doubles.
     *
     * @author Stephan Tittel (stephan.tittel@kom.tu-darmstadt.de)
     */
    protected enum NumberType {
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

    /**
     * Callback listener interface to notify about changed range values.
     *
     * @param <T> The Number type the RangeSeekBar has been declared with.
     * @author Stephan Tittel (stephan.tittel@kom.tu-darmstadt.de)
     */
    public interface OnRangeSeekBarChangeListener<T extends Number> {

        void onRangeSeekBarValuesChanged(RangeSeekBar<T> bar, T minValue, T maxValue);
    }

}
