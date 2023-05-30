package com.emansapplication.emanvirtualjoystick.views;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.CallSuper;
import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleableRes;
import androidx.annotation.UiThread;
import androidx.appcompat.content.res.AppCompatResources;

import com.emansapplication.emanvirtualjoystick.R;

/**
 * Joystick view. Inspired by <a href="https://github.com/controlwear/virtual-joystick-android">this repo</a>.
 */
public class JoystickView extends View {

    //region Constants
    @DegreeOfFreedom
    public static final int AXIS_HORIZONTAL = 0x1;
    @DegreeOfFreedom
    public static final int AXIS_VERTICAL = 0x2;

    @DegreeOfFreedom
    public static final int AXIS_BOTH = AXIS_HORIZONTAL | AXIS_VERTICAL;
    @ColorInt
    public static final int DEFAULT_BASE_COLOR = Color.GRAY;
    @ColorInt
    public static final int DEFAULT_STICK_COLOR = Color.BLACK;
    private final TintInfo baseTint = new TintInfo();
    private final TintInfo stickTint = new TintInfo();

    // endregion

    // region Fields

    private boolean enabled = true;
    @Nullable
    private Drawable baseDrawable = new ColorDrawable(DEFAULT_BASE_COLOR);
    @Nullable
    private Drawable stickDrawable = new ColorDrawable(DEFAULT_STICK_COLOR);

    private float joystickScale = 0.75f;
    private float stickSizeRatio = 0.33f;
    private int borderWidth = 0;
    @ColorInt
    private int borderColor = DEFAULT_BASE_COLOR;
    @DegreeOfFreedom
    private int stickAxis = AXIS_BOTH;
    private boolean recenterStick = true;
    private boolean fixedCenter = true;

    @Nullable
    private OnStickMoveListener onStickMoveListener;
    private int listenerMinInterval = 50;

    // endregion

    // region Dynamic variables
    private final Paint mBorderPaint = new Paint();
    private Bitmap mScaledBaseBitmap;
    private Bitmap mScaledStickBitmap;
    private int mCenterX;
    private int mCenterY;
    private boolean isTouched;
    private int mStickX;
    private int mStickY;
    private int mOffsetX;
    private int mOffsetY;
    private int mBorderRadius;
    private int mStickRadius;

    private long mLastListenerCall;

    private boolean mCreatingBaseBmp;
    private boolean mCreatingStickBmp;

    // endregion

    // region Constructors
    public JoystickView(Context context) {
        this(context, null);
    }

    public JoystickView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }


    public JoystickView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray styledAttributes = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.JoystickView,
                defStyleAttr, 0
        );

        this.enabled = styledAttributes.getBoolean(R.styleable.JoystickView_android_enabled, enabled);

        internalSetBaseDrawable(getDrawable(styledAttributes, R.styleable.JoystickView_joystickBaseDrawable, baseDrawable));
        this.baseTint.tintList = coalesce(styledAttributes.getColorStateList(R.styleable.JoystickView_joystickBaseTint), baseTint.tintList);
        this.baseTint.parseTintMode(styledAttributes.getInt(R.styleable.JoystickView_joystickBaseTintMode, -1));

        internalSetStickDrawable(getDrawable(styledAttributes, R.styleable.JoystickView_stickDrawable, stickDrawable));
        this.stickTint.tintList = coalesce(styledAttributes.getColorStateList(R.styleable.JoystickView_stickTint), stickTint.tintList);
        this.stickTint.parseTintMode(styledAttributes.getInt(R.styleable.JoystickView_stickTintMode, -1));

        this.joystickScale = clipFraction(getFraction(styledAttributes, R.styleable.JoystickView_joystickScale, 1, 1, joystickScale));
        this.stickSizeRatio = clipFraction(getFraction(styledAttributes, R.styleable.JoystickView_stickSizeRatio, 1, 1 / joystickScale, stickSizeRatio));

        this.borderWidth = styledAttributes.getDimensionPixelSize(R.styleable.JoystickView_borderWidth, borderWidth);
        this.borderColor = styledAttributes.getColor(R.styleable.JoystickView_borderColor, borderColor);

        this.stickAxis = styledAttributes.getInt(R.styleable.JoystickView_joystickAxis, stickAxis) & AXIS_BOTH;

        this.recenterStick = styledAttributes.getBoolean(R.styleable.JoystickView_recenterStick, recenterStick);
        this.fixedCenter = styledAttributes.getBoolean(R.styleable.JoystickView_fixedCenter, fixedCenter);

        styledAttributes.recycle();

        mBorderPaint.setStyle(Paint.Style.STROKE);
        mBorderPaint.setAntiAlias(true);
        updateBorder();

    }

    // endregion

    // region Properties
    public final boolean isEnabled() {
        return enabled;
    }

    @UiThread
    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            if (!enabled) {
                touchStopped();
            }
        }
    }

    @Nullable
    public final Drawable getBaseDrawable() {
        return baseDrawable;
    }

    @UiThread
    public void setBaseDrawable(@Nullable Drawable drawable) {
        if (internalSetBaseDrawable(drawable)) {
            createScaledBaseBitmap();
            invalidate();
        }
    }

    @UiThread
    protected final boolean internalSetBaseDrawable(@Nullable Drawable drawable) {
        if (changeDrawable(baseDrawable, drawable)) {
            baseDrawable = drawable;
            if (baseDrawable != null) {
                baseDrawable.setCallback(this);
            }
            return true;
        } else {
            return false;
        }
    }


    @UiThread
    public void setBaseColor(@ColorInt int color) {
        if (!replaceWithColor(baseDrawable, color)) {
            setBaseDrawable(new ColorDrawable(color));
        }
    }

    @UiThread
    public void setBaseResource(@DrawableRes int resource) {
        Drawable drawable = AppCompatResources.getDrawable(getContext(), resource);
        setBaseDrawable(drawable);
    }

    @Nullable
    public final ColorStateList getBaseTintColor() {
        return baseTint.tintList;
    }

    @UiThread
    public void setBaseTintColor(@Nullable ColorStateList color) {
        if (baseTint.tintList != color) {
            baseTint.tintList = color;
            createScaledBaseBitmap();
            invalidate();
        }
    }

    @Nullable
    public final PorterDuff.Mode getBaseTintMode() {
        return baseTint.tintMode;
    }

    @UiThread
    public void setBaseTintMode(@Nullable PorterDuff.Mode blendMode) {
        if (baseTint.tintMode != blendMode) {
            baseTint.tintMode = blendMode;
            createScaledBaseBitmap();
            invalidate();
        }
    }

    @Nullable
    public final Drawable getStickDrawable() {
        return stickDrawable;
    }

    @UiThread
    public void setStickDrawable(@Nullable Drawable drawable) {
        if (internalSetStickDrawable(drawable)) {
            createScaledStickBitmap();
            invalidate();
        }
    }

    @UiThread
    protected final boolean internalSetStickDrawable(@Nullable Drawable drawable) {
        if (changeDrawable(stickDrawable, drawable)) {
            stickDrawable = drawable;
            if (stickDrawable != null) {
                stickDrawable.setCallback(this);
            }
            return true;
        } else {
            return false;
        }
    }

    @UiThread
    public void setStickColor(@ColorInt int color) {
        if (!replaceWithColor(stickDrawable, color)) {
            setStickDrawable(new ColorDrawable(color));
        }
    }

    @UiThread
    public void setStickResource(@DrawableRes int resource) {
        Drawable drawable = AppCompatResources.getDrawable(getContext(), resource);
        setBaseDrawable(drawable);
    }

    @Nullable
    public final ColorStateList getStickTintColor() {
        return stickTint.tintList;
    }

    @UiThread
    public void setStickTintColor(@Nullable ColorStateList color) {
        if (stickTint.tintList != color) {
            stickTint.tintList = color;
            createScaledStickBitmap();
            invalidate();
        }
    }

    @Nullable
    public final PorterDuff.Mode getStickTintMode() {
        return stickTint.tintMode;
    }

    @UiThread
    public void setStickTintMode(@Nullable PorterDuff.Mode blendMode) {
        if (stickTint.tintMode != blendMode) {
            stickTint.tintMode = blendMode;
            createScaledStickBitmap();
            invalidate();
        }
    }

    public final float getJoystickScale() {
        return joystickScale;
    }

    @UiThread
    public void setJoystickScale(float scale) {
        scale = clipFraction(scale);
        if (joystickScale != scale) {
            joystickScale = scale;
            updateLayout();
            invalidate();
        }
    }

    public final float getStickSizeRatio() {
        return stickSizeRatio;
    }

    @UiThread
    public void setStickSizeRatio(float ratio) {
        ratio = clipFraction(ratio);
        if (stickSizeRatio != ratio) {
            stickSizeRatio = ratio;
            updateLayout();
            invalidate();
        }
    }

    public final int getBorderWidth() {
        return borderWidth;
    }

    @UiThread
    public void setBorderWidth(int width) {
        if (borderWidth != width) {
            borderWidth = width;
            updateBorder();
            updateLayout();
            invalidate();
        }
    }

    @ColorInt
    public final int getBorderColor() {
        return borderColor;
    }

    @UiThread
    public void setBorderColor(@ColorInt int color) {
        if (borderColor != color) {
            borderColor = color;
            updateBorder();
            invalidate();
        }
    }

    @DegreeOfFreedom
    public final int getJoystickAxis() {
        return stickAxis;
    }

    @UiThread
    public void setJoystickAxis(@DegreeOfFreedom int axis) {
        axis &= AXIS_BOTH;
        if (stickAxis != axis) {
            stickAxis = axis;
            if (checkStickPos())
                invalidate();
        }
    }

    public final boolean getRecenterStick() {
        return recenterStick;
    }

    @UiThread
    public void setRecenterStick(boolean recenterStick) {
        if (this.recenterStick != recenterStick) {
            this.recenterStick = recenterStick;
            if (checkStickPos())
                invalidate();
        }
    }

    public final boolean isCenterFixed() {
        return fixedCenter;
    }

    @UiThread
    public void setFixedCenter(boolean fixedCenter) {
        if (this.fixedCenter != fixedCenter) {
            this.fixedCenter = fixedCenter;
            if (checkStickPos())
                invalidate();
        }
    }

    public void setOnStickMoveListener(@Nullable OnStickMoveListener listener) {
        this.onStickMoveListener = listener;
    }

    // endregion

    // region Stick position

    /**
     * Get the horizontal position of the stick.
     *
     * @return A number between -1 and 1 (0 being the center, -1 the left-most
     * point and 1 the right-most point of the joystick).
     */
    public double getStickX() {
        if (mBorderRadius <= 0) {
            return 0;
        }
        return (double) mStickX / mBorderRadius;
    }

    /**
     * Get the vertical position of the stick.
     *
     * @return A number between -1 and 1 (0 being the center, -1 the top-most
     * point and 1 the bottom-most point of the joystick).
     * @return
     */
    public double getStickY() {
        if (mBorderRadius <= 0) {
            return 0;
        }
        return (double) mStickY / mBorderRadius;
    }

    /**
     * Get the distance of the stick from the center of the joystick, as a
     * number between 0 and 1.
     */
    public double getStickMoveAmplitude() {
        if (mBorderRadius <= 0) {
            return 0;
        }
        return Math.sqrt(mStickX * mStickX + mStickY * mStickY) / mBorderRadius;
    }

    /**
     * Get the oriented, clockwise angle of the stick, in radians.
     * 0 corresponds to an horizontal move to the right.
     *
     * @return An angle between -Pi and Pi, such that
     * "stick_x = amplitude * cos(angle)" and "stick_y = amplitude * sin(angle)".
     */
    public double getStickAngle() {
        return Math.atan2(mStickY, mStickX);
    }

    /**
     * Set the position of the stick using cartesian coordinates.
     * Due to rounding, it cannot be guaranteed that "getStickX()"
     * and "getStickY()" return exactly the value of the parameters when this
     * function returns.
     *
     * @param x,y The horizontal and vertical desired position of
     *            the stick, between -1 and 1. Any value that is not in this range
     *            shall be clipped.
     */
    public void setStickPositionXY(double x, double y) {
        int stick_x = (int) (x * mBorderRadius);
        int stick_y = (int) (y * mBorderRadius);
        if (stick_x != mStickX || stick_y != mStickY) {
            mStickX = stick_x;
            mStickY = stick_y;
            checkStickPos();
            onStickMoved(true);
        }
    }

    /**
     * Set the position of the stick, using polar coordinates.
     * Due to rounding, it cannot be guaranteed that "getStickMoveAmplitude()"
     * and "getStickAngle()" return exactly the value of the parameters when this
     * function returns.
     *
     * @param amplitude The amplitude of the move, between 0 and 1. Any value that is not
     *                  in this range shall be clipped.
     * @param angle     The oriented, clockwise angle of the stick, in radians (0 corresponds to
     *                  an horizontal move to the right).
     */
    public void setStickAmplitudeAngle(double amplitude, double angle) {
        amplitude = clipFraction(amplitude);
        setStickPositionXY(amplitude * Math.cos(angle), amplitude * Math.sin(angle));
    }

    @UiThread
    public void centerStick() {
        if (internalCenterStick()) {
            onStickMoved(true);
        }
    }

    /// Returns true if the position of the stick has changed, false
    /// if the stick was already centered.
    protected final boolean internalCenterStick() {
        boolean changed = (mStickX | mStickY | mOffsetX | mOffsetY) != 0;
        mStickX = mStickY = mOffsetX = mOffsetY = 0;
        return changed;
    }

    /// Checks and fixes the position of the stick. Returns true if
    /// the position of the stick was altered, false if it was already valid.
    @CallSuper
    protected boolean checkStickPos() {
        if (mBorderRadius <= 0 || mStickRadius <= 0) {
            return false;
        }
        boolean changed = false;
        if (fixedCenter && (mOffsetX != 0 || mOffsetY != 0)) {
            mStickX += mOffsetX;
            mStickY += mOffsetY;
            mOffsetX = mOffsetY = 0;
            changed = true;
        }
        final double ampl = getStickMoveAmplitude();
        if (ampl > 1) {
            mStickX /= ampl;
            mStickY /= ampl;
            changed = true;
        }
        if ((stickAxis & AXIS_HORIZONTAL) == 0 && mStickX != 0) {
            mStickX = 0;
            changed = true;
        }
        if ((stickAxis & AXIS_VERTICAL) == 0 && mStickY != 0) {
            mStickY = 0;
            changed = true;
        }
        if (!isTouched && recenterStick) {
            changed |= internalCenterStick();
        }
        return changed;
    }

    // endregion

    // region Design

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mBorderRadius <= 0 || mStickRadius <= 0) {
            return;
        }

        canvas.drawBitmap(mScaledBaseBitmap, mCenterX - mBorderRadius, mCenterY - mBorderRadius, null);
        if (borderWidth > 0) {
            canvas.drawCircle(mCenterX, mCenterY, mBorderRadius, mBorderPaint);
        }
        canvas.drawBitmap(mScaledStickBitmap, mCenterX + mStickX - mStickRadius, mCenterY + mStickY - mStickRadius, null);

    }

    protected int getUsableWidth() {
        return getWidth() - getPaddingStart() - getPaddingEnd();
    }

    protected int getUsableHeight() {
        return getHeight() - getPaddingTop() - getPaddingBottom();
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        if (who == baseDrawable || who == stickDrawable) {
            return true;
        }
        return super.verifyDrawable(who);
    }

    @Override
    public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
        super.scheduleDrawable(who, what, when);
    }

    @Override
    public void invalidateDrawable(@NonNull Drawable drawable) {
        if (drawable == baseDrawable) {
            createScaledBaseBitmap();
        } else if (drawable == stickDrawable) {
            createScaledStickBitmap();
        }
        super.invalidateDrawable(drawable);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int d = Math.min(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
        setMeasuredDimension(d, d);
    }

    @CallSuper
    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        updateLayout();
    }

    @CallSuper
    @Override
    public void requestLayout() {
        super.requestLayout();
        updateLayout();
    }

    private void updateLayout() {

        isTouched = false;
        mOffsetX = mOffsetY = 0;

        int usableWidth = getUsableWidth();
        int usableHeight = getUsableHeight();

        int dimension = (int) (joystickScale * Math.min(usableWidth, usableHeight));
        if (dimension < 0) {
            mBorderRadius = mStickRadius = mCenterX = mCenterY = 0;
            return;
        }

        mBorderRadius = (dimension - borderWidth) / 2;
        mStickRadius = (int) (mBorderRadius * stickSizeRatio);

        centerStick();

        mCenterX = getPaddingStart() + usableWidth / 2;
        mCenterY = getPaddingTop() + usableHeight / 2;

        createScaledBaseBitmap();
        createScaledStickBitmap();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        if (!enabled) {
            return true;
        }

        int x = (int) (event.getX() - mCenterX);
        int y = (int) (event.getY() - mCenterY);

        switch (event.getActionMasked()) {

            case MotionEvent.ACTION_DOWN:
                if (!fixedCenter) {
                    mOffsetX = x;
                    mOffsetY = y;
                }
                // NO break is intended
            case MotionEvent.ACTION_MOVE:
                isTouched = true;
                x -= mOffsetX;
                y -= mOffsetY;

                mStickX = x;
                mStickY = y;
                checkStickPos();
                onStickMoved(false);
                return true;

            case MotionEvent.ACTION_UP:
                touchStopped();
                return true;
        }
        return super.onTouchEvent(event);
    }

    protected void touchStopped() {
        isTouched = false;
        mOffsetX = mOffsetY = 0;
        if (recenterStick) {
            centerStick(); // will invalidate view if needed
        }
    }

    protected void updateBorder() {
        mBorderPaint.setColor(borderColor);
        mBorderPaint.setStrokeWidth(borderWidth);
    }

    protected void createScaledBaseBitmap() {
        if (mCreatingBaseBmp || mBorderRadius <= 0) {
            return; // avoid loop
        }
        mCreatingBaseBmp = true;
        baseTint.apply(baseDrawable);
        mScaledBaseBitmap = getCircledBitmap(baseDrawable, 2 * mBorderRadius, 2 * mBorderRadius);
        mCreatingBaseBmp = false;
    }

    protected void createScaledStickBitmap() {
        if(mCreatingStickBmp || mStickRadius <= 0) {
            return;
        }
        mCreatingStickBmp = true;
        stickTint.apply(stickDrawable);
        mScaledStickBitmap = getCircledBitmap(stickDrawable, 2 * mStickRadius, 2 * mStickRadius);
        mCreatingStickBmp = false;
    }

    protected void onStickMoved(boolean forceCallListener) {
        if (onStickMoveListener != null) {
            long time = System.currentTimeMillis();
            if (forceCallListener || time - mLastListenerCall > listenerMinInterval) {
                mLastListenerCall = time;
                onStickMoveListener.onStickMove(this);
            }
        }
        invalidate();
    }

    // endregion

    // region Private utils

    @Nullable
    private <T> T coalesce(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private double clipFraction(double fraction) {
        return Math.min(1, Math.max(fraction, 0));
    }

    private float clipFraction(float fraction) {
        return Math.min(1, Math.max(fraction, 0));
    }

    @NonNull
    private static Bitmap drawableToBitmap(@Nullable Drawable drawable, int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        if (drawable != null) {
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, width, height);
            drawable.draw(canvas);
        }
        return bitmap;
    }

    /// Crops a round portion of the drawable, making the pixels outside the round region transparent.
    private Bitmap getCircledBitmap(@Nullable Drawable drawable, int width, int height) {
        Bitmap bitmap = drawableToBitmap(drawable, width, height);

        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        if (drawable != null) {
            drawable.setLayoutDirection(getLayoutDirection());

            Canvas canvas = new Canvas(output);
            canvas.drawARGB(0, 0, 0, 0);

            final Paint paint = new Paint();
            paint.setAntiAlias(true);
            canvas.drawRoundRect(0, 0, width, height, width / 2f, height / 2f, paint);
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            canvas.drawBitmap(bitmap, null, new Rect(0, 0, width, height), paint);

        }
        return output;
    }

    /// Unregister the current drawable, if any, and register the new drawable, if provided.
    protected boolean changeDrawable(@Nullable Drawable current, @Nullable Drawable replacement) {
        if (current == replacement) {
            return false;
        }
        if (current != null) {
            current.setVisible(false, false);
            current.setCallback(null);
            unscheduleDrawable(current);
        }
        if (replacement != null) {
            replacement.mutate();
        }
        return true;
    }

    /// Try to change the color of a ColorDrawable. Returns false if the current drawable is not
    /// already a ColorDrawable and must be fully replaced. Internal use only.
    private boolean replaceWithColor(@Nullable Drawable current, @ColorInt int replacement) {
        if (current instanceof ColorDrawable) {
            ((ColorDrawable) current).setColor(replacement);
            return true;
        } else {
            return false;
        }
    }

    /// Load a drawable from a "reference|color" attribute. Throw if the attribute is defined but
    /// is neither a reference nor a color. Returns defaultValue if the attribute is not set.
    protected final Drawable getDrawable(TypedArray styledAttributes, @StyleableRes int attr, Drawable defaultValue) {
        switch (styledAttributes.getType(attr)) {
            case TypedValue.TYPE_INT_COLOR_ARGB8:
            case TypedValue.TYPE_INT_COLOR_RGB8:
            case TypedValue.TYPE_INT_COLOR_ARGB4:
            case TypedValue.TYPE_INT_COLOR_RGB4:
                return new ColorDrawable(styledAttributes.getColor(attr, 0));
            default:
                int res = styledAttributes.getResourceId(attr, 0);
                if (res == 0) {
                    return defaultValue;
                }
                return AppCompatResources.getDrawable(getContext(), res);
        }
    }

    /// Load a fraction, as a float, from a "fraction|float" attribute. Throw if the attribute is defined
    /// but is neither a fraction nor a float. Returns defaultValue if the attribute is not set.
    /// This is used instead of TypedArray::getFraction, because it requires "base" and "pbase" to be 'int' instead of
    // 'float' for no reason.
    protected final float getFraction(TypedArray styledAttributes, @StyleableRes int attr, float base, float pbase, float defaultValue) {
        final TypedValue typedValue = new TypedValue();
        if (!styledAttributes.getValue(attr, typedValue)) {
            return defaultValue;
        }


        int type = typedValue.type;
        if (type == TypedValue.TYPE_NULL) {
            return defaultValue;
        } else if (type == TypedValue.TYPE_FRACTION) {
            return typedValue.getFraction(base, pbase);
        } else if (type == TypedValue.TYPE_FLOAT) {
            return typedValue.getFloat();
        } else {
            throw new UnsupportedOperationException();
        }
    }

    // endregion

    public @interface DegreeOfFreedom {
    }

    private static class TintInfo {
        @Nullable
        public ColorStateList tintList;
        @Nullable
        public PorterDuff.Mode tintMode;

        public void parseTintMode(int blendMode) {
            switch (blendMode) {
                case 3:
                    tintMode = PorterDuff.Mode.SRC_OVER;
                    break;
                case 5:
                    tintMode = PorterDuff.Mode.SRC_IN;
                    break;
                case 9:
                    tintMode = PorterDuff.Mode.SRC_ATOP;
                    break;
                case 14:
                    tintMode = PorterDuff.Mode.MULTIPLY;
                    break;
                case 15:
                    tintMode = PorterDuff.Mode.SCREEN;
                    break;
                case 16:
                    tintMode = PorterDuff.Mode.ADD;
                    break;
                default:
            }

        }

        public void apply(@Nullable Drawable drawable) {
            if (drawable != null) {
                drawable.setTintList(this.tintList);
                drawable.setTintMode(this.tintMode);
            }
        }

    }

    @FunctionalInterface
    public interface OnStickMoveListener {
        void onStickMove(JoystickView view);
    }

}