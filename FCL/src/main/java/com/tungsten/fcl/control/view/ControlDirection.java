package com.tungsten.fcl.control.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;

import com.tungsten.fcl.FCLApplication;
import com.tungsten.fcl.R;
import com.tungsten.fcl.control.EditViewDialog;
import com.tungsten.fcl.control.GameMenu;
import com.tungsten.fcl.control.data.BaseInfoData;
import com.tungsten.fcl.control.data.ControlDirectionData;
import com.tungsten.fcl.control.data.ControlDirectionStyle;
import com.tungsten.fcl.control.data.CustomControl;
import com.tungsten.fcl.control.data.DirectionEventData;
import com.tungsten.fcl.util.AndroidUtils;
import com.tungsten.fclauncher.bridge.FCLBridge;
import com.tungsten.fclcore.fakefx.beans.binding.Bindings;
import com.tungsten.fclcore.fakefx.beans.property.BooleanProperty;
import com.tungsten.fclcore.fakefx.beans.property.BooleanPropertyBase;
import com.tungsten.fclcore.fakefx.beans.property.ObjectProperty;
import com.tungsten.fclcore.fakefx.beans.property.SimpleBooleanProperty;
import com.tungsten.fclcore.fakefx.beans.property.SimpleObjectProperty;
import com.tungsten.fclcore.task.Schedulers;
import com.tungsten.fcllibrary.component.dialog.FCLAlertDialog;
import com.tungsten.fcllibrary.util.ConvertUtils;

import java.util.UUID;

/**
 * Custom control direction view.
 */
@SuppressLint("ViewConstructor")
public class ControlDirection extends RelativeLayout implements CustomView {

    @Nullable
    private final GameMenu menu;
    private final boolean displayMode;
    private final Path boundaryPath;
    private final Paint boundaryPaint;
    private final int screenWidth;
    private final int screenHeight;

    private static final double ANGLE_0 = 0;
    private static final double ANGLE_360 = 360;
    private static final double ANGLE_8D_OF_0P = 22.5;
    private static final double ANGLE_8D_OF_1P = 67.5;
    private static final double ANGLE_8D_OF_2P = 112.5;
    private static final double ANGLE_8D_OF_3P = 157.5;
    private static final double ANGLE_8D_OF_4P = 202.5;
    private static final double ANGLE_8D_OF_5P = 247.5;
    private static final double ANGLE_8D_OF_6P = 292.5;
    private static final double ANGLE_8D_OF_7P = 337.5;

    private final BooleanProperty readyProperty = new SimpleBooleanProperty(this, "ready", false);

    public BooleanProperty readyProperty() {
        return readyProperty;
    }

    public void setReady(boolean ready) {
        readyProperty.set(ready);
    }

    public boolean isReady() {
        return readyProperty.get();
    }

    private BooleanProperty visibilityProperty;

    private final BooleanProperty parentVisibilityProperty = new SimpleBooleanProperty(this, "parentVisibility", true);

    public BooleanProperty parentVisibilityProperty() {
        return parentVisibilityProperty;
    }

    public void setParentVisibility(boolean parentVisibility) {
        parentVisibilityProperty.set(parentVisibility);
    }

    public boolean isParentVisibility() {
        return parentVisibilityProperty.get();
    }

    private final ObjectProperty<ControlDirectionData> dataProperty = new SimpleObjectProperty<>(this, "data", new ControlDirectionData(UUID.randomUUID().toString()));

    public ObjectProperty<ControlDirectionData> dataProperty() {
        return dataProperty;
    }

    public void setData(ControlDirectionData data) {
        dataProperty.set(data);
    }

    public ControlDirectionData getData() {
        return dataProperty.get();
    }

    public ControlDirection(Context context, @Nullable GameMenu menu, boolean displayMode) {
        super(context);
        this.menu = menu;
        this.displayMode = displayMode;
        setClickable(true);

        boundaryPath = new Path();
        boundaryPaint = new Paint();
        boundaryPaint.setAntiAlias(true);
        boundaryPaint.setColor(Color.RED);
        boundaryPaint.setStyle(Paint.Style.STROKE);
        boundaryPaint.setStrokeWidth(3);
        screenWidth = AndroidUtils.getScreenWidth(FCLApplication.getCurrentActivity());
        screenHeight = AndroidUtils.getScreenHeight(FCLApplication.getCurrentActivity());

        setWillNotDraw(false);

        post(() -> {
            notifyData();
            if (menu != null) {
                menu.editModeProperty().addListener(invalidate -> {
                    notifyData();
                    cancelAllEvent();
                });
            }
            dataProperty.addListener(invalidate -> Schedulers.androidUIThread().execute(() -> {
                notifyData();
                cancelAllEvent();
                getData().addListener(i -> Schedulers.androidUIThread().execute(() -> {
                    notifyData();
                    cancelAllEvent();
                }));
            }));
            getData().addListener(invalidate -> Schedulers.androidUIThread().execute(() -> {
                notifyData();
                cancelAllEvent();
            }));
            if (menu != null) {
                menu.showViewBoundariesProperty().addListener(invalidate -> invalidate());
            }
            setReady(true);
        });
    }

    public ControlDirection(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.menu = null;
        this.displayMode = true;
        setClickable(true);

        boundaryPath = new Path();
        boundaryPaint = new Paint();
        boundaryPaint.setAntiAlias(true);
        boundaryPaint.setColor(Color.RED);
        boundaryPaint.setStyle(Paint.Style.STROKE);
        boundaryPaint.setStrokeWidth(3);
        screenWidth = AndroidUtils.getScreenWidth(FCLApplication.getCurrentActivity());
        screenHeight = AndroidUtils.getScreenHeight(FCLApplication.getCurrentActivity());

        post(() -> {
            notifyData();
            dataProperty.addListener(invalidate -> {
                Schedulers.androidUIThread().execute(this::notifyData);
                getData().addListener(i -> Schedulers.androidUIThread().execute(this::notifyData));
            });
            getData().addListener(invalidate -> Schedulers.androidUIThread().execute(this::notifyData));
            setReady(true);
        });
    }

    private void notifyData() {
        ControlDirectionData data = getData();

        refreshBaseInfo(data);
        post(() -> refreshStyle(data));
    }

    public void setSize(int size) {
        ViewGroup.LayoutParams params = getLayoutParams();
        params.width = size;
        params.height = size;
        setLayoutParams(params);
    }

    private void refreshBaseInfo(ControlDirectionData data) {
        // Size
        int size;
        if (data.getBaseInfo().getSizeType() == BaseInfoData.SizeType.ABSOLUTE) {
            size = ConvertUtils.dip2px(getContext(), data.getBaseInfo().getAbsoluteWidth());
        } else {
            size = data.getBaseInfo().getPercentageWidth().getReference() == BaseInfoData.PercentageSize.Reference.SCREEN_WIDTH ?
                    (int) (screenWidth * (data.getBaseInfo().getPercentageWidth().getSize() / 1000f)) :
                    (int) (screenHeight * (data.getBaseInfo().getPercentageWidth().getSize() / 1000f));
        }
        setSize(size);

        // Position
        post(() -> {
            int x;
            int y;
            x = (int) ((screenWidth - size) * (data.getBaseInfo().getXPosition() / 1000f));
            y = (int) ((screenHeight - size) * (data.getBaseInfo().getYPosition() / 1000f));
            if (!displayMode) {
                setX(x);
                setY(y);
            }
        });

        // Visibility
        if (!displayMode && menu != null) {
            visibilityProperty().unbind();
            if (menu.isEditMode()) {
                visibilityProperty().bind(Bindings.createBooleanBinding(() -> menu.getViewGroup() != null && menu.getViewGroup().getViewData().directionList().stream().anyMatch(it -> it.getId().equals(getData().getId())),
                        menu.editModeProperty(), menu.viewGroupProperty()));
            } else {
                visibilityProperty().bind(Bindings.createBooleanBinding(() -> isParentVisibility() && (data.getBaseInfo().getVisibilityType() == BaseInfoData.VisibilityType.ALWAYS ||
                                (data.getBaseInfo().getVisibilityType() == BaseInfoData.VisibilityType.IN_GAME && menu.getCursorMode() == FCLBridge.CursorDisabled) ||
                                (data.getBaseInfo().getVisibilityType() == BaseInfoData.VisibilityType.MENU && menu.getCursorMode() == FCLBridge.CursorEnabled)),
                        menu.cursorModeProperty(), parentVisibilityProperty()));
            }
            visibilityProperty().addListener(observable -> {
                if (!visibilityProperty.get()) {
                    cancelAllEvent();
                }
            });
        }
    }

    private final AppCompatButton centerBtn = new AppCompatButton(getContext());
    private final AppCompatButton upBtn = new AppCompatButton(getContext());
    private final AppCompatButton downBtn = new AppCompatButton(getContext());
    private final AppCompatButton leftBtn = new AppCompatButton(getContext());
    private final AppCompatButton rightBtn = new AppCompatButton(getContext());
    private final AppCompatButton upLeftBtn = new AppCompatButton(getContext());
    private final AppCompatButton upRightBtn = new AppCompatButton(getContext());
    private final AppCompatButton downLeftBtn = new AppCompatButton(getContext());
    private final AppCompatButton downRightBtn = new AppCompatButton(getContext());

    private final AppCompatButton[] buttons = new AppCompatButton[] {
            centerBtn,
            upBtn,
            downBtn,
            leftBtn,
            rightBtn,
            upLeftBtn,
            upRightBtn,
            downLeftBtn,
            downRightBtn
    };

    private final AppCompatButton area = new AppCompatButton(getContext());
    private final AppCompatButton rocker = new AppCompatButton(getContext());

    private int rockerSize;

    private GradientDrawable drawableNormal;
    private GradientDrawable drawablePressed;

    private void refreshStyle(ControlDirectionData data) {
        int viewSize;
        if (data.getBaseInfo().getSizeType() == BaseInfoData.SizeType.ABSOLUTE) {
            viewSize = ConvertUtils.dip2px(getContext(), data.getBaseInfo().getAbsoluteWidth());
        } else {
            viewSize = data.getBaseInfo().getPercentageWidth().getReference() == BaseInfoData.PercentageSize.Reference.SCREEN_WIDTH ?
                    (int) (screenWidth * (data.getBaseInfo().getPercentageWidth().getSize() / 1000f)) :
                    (int) (screenHeight * (data.getBaseInfo().getPercentageWidth().getSize() / 1000f));
        }
        if (data.getStyle().getStyleType() == ControlDirectionStyle.Type.BUTTON) {
            int size = (viewSize * (1000 - (2 * getData().getStyle().getButtonStyle().getInterval()))) / 3000;
            int p0 = 0;
            int p1 = size + ((viewSize * getData().getStyle().getButtonStyle().getInterval()) / 1000);
            int p2 = viewSize - size;
            drawableNormal = new GradientDrawable();
            drawableNormal.setCornerRadius(ConvertUtils.dip2px(getContext(), data.getStyle().getButtonStyle().getCornerRadius() / 10f));
            drawableNormal.setStroke(ConvertUtils.dip2px(getContext(), data.getStyle().getButtonStyle().getStrokeWidth() / 10f), data.getStyle().getButtonStyle().getStrokeColor());
            drawableNormal.setColor(data.getStyle().getButtonStyle().getFillColor());
            drawablePressed = new GradientDrawable();
            drawablePressed.setCornerRadius(ConvertUtils.dip2px(getContext(), data.getStyle().getButtonStyle().getCornerRadiusPressed() / 10f));
            drawablePressed.setStroke(ConvertUtils.dip2px(getContext(), data.getStyle().getButtonStyle().getStrokeWidthPressed() / 10f), data.getStyle().getButtonStyle().getStrokeColorPressed());
            drawablePressed.setColor(data.getStyle().getButtonStyle().getFillColorPressed());
            removeAllViews();
            for (AppCompatButton b : buttons) {
                addView(b);
                ViewGroup.LayoutParams layoutParams = b.getLayoutParams();
                layoutParams.width = size;
                layoutParams.height = size;
                b.setClickable(false);
                b.setLayoutParams(layoutParams);
                b.setGravity(Gravity.CENTER);
                b.setPadding(0, 0, 0, 0);
                b.setAllCaps(false);
                b.setTextSize(data.getStyle().getButtonStyle().getTextSize());
                b.setTextColor(data.getStyle().getButtonStyle().getTextColor());
                b.setBackground(drawableNormal);
            }
            setButtonPosition(centerBtn, p1, p1);
            setButtonPosition(upBtn, p1, p0);
            setButtonPosition(downBtn, p1, p2);
            setButtonPosition(leftBtn, p0, p1);
            setButtonPosition(rightBtn, p2, p1);
            setButtonPosition(upLeftBtn, p0, p0);
            setButtonPosition(upRightBtn, p2, p0);
            setButtonPosition(downLeftBtn, p0, p2);
            setButtonPosition(downRightBtn, p2, p2);
            centerBtn.setText("◆");
            upBtn.setText("▲");
            downBtn.setText("▼");
            leftBtn.setText("◀");
            rightBtn.setText("▶");
            upLeftBtn.setText("◤");
            upRightBtn.setText("◥");
            downLeftBtn.setText("◣");
            downRightBtn.setText("◢");
            upLeftBtn.setVisibility(GONE);
            upRightBtn.setVisibility(GONE);
            downLeftBtn.setVisibility(GONE);
            downRightBtn.setVisibility(GONE);
        } else {
            rockerSize = (viewSize * getData().getStyle().getRockerStyle().getRockerSize()) / 1000;
            GradientDrawable drawableArea = new GradientDrawable();
            drawableArea.setCornerRadius((float) (viewSize * data.getStyle().getRockerStyle().getBgCornerRadius()) / 1000);
            drawableArea.setStroke(ConvertUtils.dip2px(getContext(), data.getStyle().getRockerStyle().getBgStrokeWidth() / 10f), data.getStyle().getRockerStyle().getBgStrokeColor());
            drawableArea.setColor(data.getStyle().getRockerStyle().getBgFillColor());
            GradientDrawable drawableRocker = new GradientDrawable();
            drawableRocker.setCornerRadius((float) (rockerSize * data.getStyle().getRockerStyle().getRockerCornerRadius()) / 1000);
            drawableRocker.setStroke(ConvertUtils.dip2px(getContext(), data.getStyle().getRockerStyle().getRockerStrokeWidth() / 10f), data.getStyle().getRockerStyle().getRockerStrokeColor());
            drawableRocker.setColor(data.getStyle().getRockerStyle().getRockerFillColor());
            removeAllViews();
            addView(area, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            addView(rocker, new LayoutParams(rockerSize, rockerSize));
            area.setClickable(false);
            rocker.setClickable(false);
            area.setBackground(drawableArea);
            rocker.setBackground(drawableRocker);
            setButtonPosition(area, 0, 0);
            setButtonPosition(rocker, (viewSize / 2) - (rockerSize / 2), (viewSize / 2) - (rockerSize / 2));
        }
    }

    private void setButtonPosition(AppCompatButton button, int x, int y) {
        button.setX(x);
        button.setY(y);
    }

    private void setButtonStyle(AppCompatButton button, boolean press) {
        if (press) {
            button.setTextSize(getData().getStyle().getButtonStyle().getTextSizePressed());
            button.setTextColor(getData().getStyle().getButtonStyle().getTextColorPressed());
            button.setBackground(drawablePressed);
        } else {
            button.setTextSize(getData().getStyle().getButtonStyle().getTextSize());
            button.setTextColor(getData().getStyle().getButtonStyle().getTextColor());
            button.setBackground(drawableNormal);
        }
    }

    @Override
    public void requestLayout() {
        super.requestLayout();
        post(() -> {
            measure(MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.EXACTLY));
            layout(getLeft(), getTop(), getRight(), getBottom());
        });
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (menu != null && menu.isShowViewBoundaries() && !displayMode) {
            boundaryPath.moveTo(0, 0);
            boundaryPath.lineTo(getWidth(), 0);
            boundaryPath.lineTo(getWidth(), getHeight());
            boundaryPath.lineTo(0, getHeight());
            boundaryPath.lineTo(0, 0);
            canvas.drawPath(boundaryPath, boundaryPaint);
        }
    }

    private float downX;
    private float downY;
    private float positionX;
    private float positionY;
    private long downTime;
    private int clickCount = 0;
    private long firstClickTime;
    private boolean doubleClickEvent = false;
    private boolean startClick = false;
    private boolean startRecord = false;

    private final Handler handler = new Handler();
    private final Runnable deleteRunnable = () -> {
        Vibrator vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
        vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE));
        FCLAlertDialog.Builder builder = new FCLAlertDialog.Builder(getContext());
        builder.setAlertLevel(FCLAlertDialog.AlertLevel.ALERT);
        builder.setCancelable(false);
        builder.setMessage(getContext().getString(R.string.edit_direction_delete));
        builder.setPositiveButton(this::deleteView);
        builder.setNegativeButton(() -> {
            setX(positionX);
            setY(positionY);
        });
        builder.create().show();
    };

    private void deleteView() {
        if (menu != null) {
            menu.getViewManager().removeView(getData());
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (menu != null && menu.isEditMode()) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getX();
                    downY = event.getY();
                    positionX = getX();
                    positionY = getY();
                    downTime = System.currentTimeMillis();
                    handler.postDelayed(deleteRunnable, 400);
                    break;
                case MotionEvent.ACTION_MOVE:
                    int deltaX = (int) ((event.getX() - downX) * menu.getMenuSetting().getMouseSensitivity());
                    int deltaY = (int) ((event.getY() - downY) * menu.getMenuSetting().getMouseSensitivity());
                    float targetX = Math.max(0, Math.min(screenWidth - getWidth(), getX() + deltaX));
                    float targetY = Math.max(0, Math.min(screenHeight - getHeight(), getY() + deltaY));
                    setX(targetX);
                    setY(targetY);
                    if ((Math.abs(event.getX() - downX) > 1 || Math.abs(event.getY() - downY) > 1) && System.currentTimeMillis() - downTime < 400) {
                        handler.removeCallbacks(deleteRunnable);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(deleteRunnable);
                    if (System.currentTimeMillis() - downTime <= 100
                            && Math.abs(event.getX() - downX) <= 10
                            && Math.abs(event.getY() - downY) <= 10) {
                        setX(positionX);
                        setY(positionY);
                        EditViewDialog dialog = new EditViewDialog(getContext(), getData().clone(), menu, view -> {
                            ControlDirectionData newData = ((ControlDirectionData) view).clone();
                            getData().setBaseInfo(newData.getBaseInfo());
                            getData().setStyle(newData.getStyle());
                            getData().setEvent(newData.getEvent());
                            menu.getViewManager().saveController();
                        });
                        dialog.show();
                    } else {
                        getData().getBaseInfo().setXPosition((int) ((1000 * getX()) / (screenWidth - getMeasuredWidth())));
                        getData().getBaseInfo().setYPosition((int) ((1000 * getY()) / (screenHeight - getMeasuredHeight())));
                        menu.getViewManager().saveController();
                    }
                    break;
            }
        } else if (menu != null && !menu.isEditMode()) {
            if (getData().getStyle().getStyleType() == ControlDirectionStyle.Type.BUTTON) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getX();
                        downY = event.getY();
                        downTime = System.currentTimeMillis();
                        handleButtonEvent((int) event.getX(), (int) event.getY());
                        break;
                    case MotionEvent.ACTION_MOVE:
                        handleButtonEvent((int) event.getX(), (int) event.getY());
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (System.currentTimeMillis() - downTime <= 100
                                && Math.abs(event.getX() - downX) <= 10
                                && Math.abs(event.getY() - downY) <= 10) {
                            clickCount++;
                            if (clickCount == 1) {
                                firstClickTime = System.currentTimeMillis();
                            }
                            if (clickCount == 2) {
                                if (System.currentTimeMillis() - firstClickTime < 400) {
                                    handleDoubleEvent(!doubleClickEvent);
                                    clickCount = 0;
                                } else {
                                    clickCount = 1;
                                    firstClickTime = System.currentTimeMillis();
                                }
                            }
                        }
                        cancelAllEvent();
                        break;
                }
            } else {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        if (getData().getEvent().getFollowOption() == DirectionEventData.FollowOption.FOLLOW ||
                                (getData().getEvent().getFollowOption() == DirectionEventData.FollowOption.CENTER_FOLLOW
                                        && event.getX() >= (float) ((getMeasuredWidth() / 2) - (rockerSize / 2))
                                        && event.getX() <= (float) ((getMeasuredWidth() / 2) + (rockerSize / 2))
                                        && event.getY() >= (float) ((getMeasuredWidth() / 2) - (rockerSize / 2))
                                        && event.getY() <= (float) ((getMeasuredWidth() / 2) + (rockerSize / 2)))) {
                            downTime = System.currentTimeMillis();
                            startClick = true;
                            int deltaX = (int) (event.getX() - (getMeasuredWidth() / 2));
                            int deltaY = (int) (event.getY() - (getMeasuredWidth() / 2));
                            int targetX = (int) (getX() + deltaX);
                            int targetY = (int) (getY() + deltaY);
                            setX(targetX);
                            setY(targetY);
                        } else {
                            startClick = false;
                            handleRockerEvent((int) event.getX(), (int) event.getY());
                        }
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (!startRecord) {
                            downX = event.getX();
                            downY = event.getY();
                            startRecord = true;
                        } else if (!startClick || Math.abs(event.getX() - downX) > 10 || Math.abs(event.getY() - downY) > 10) {
                            handleRockerEvent((int) event.getX(), (int) event.getY());
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        startRecord = false;
                        if (startClick
                                && System.currentTimeMillis() - downTime <= 100
                                && Math.abs(event.getX() - downX) <= 10
                                && Math.abs(event.getY() - downY) <= 10) {
                            startClick = false;
                            clickCount++;
                            if (clickCount == 1) {
                                firstClickTime = System.currentTimeMillis();
                            }
                            if (clickCount == 2) {
                                if (System.currentTimeMillis() - firstClickTime < 400) {
                                    handleDoubleEvent(!doubleClickEvent);
                                    clickCount = 0;
                                } else {
                                    clickCount = 1;
                                    firstClickTime = System.currentTimeMillis();
                                }
                            }
                        }
                        cancelAllEvent();
                        break;
                }
            }
        } else {
            return true;
        }
        return true;
    }

    private void handleButtonEvent(int x, int y) {
        int size = (getMeasuredWidth() * (1000 - (2 * getData().getStyle().getButtonStyle().getInterval()))) / 3000;
        int p1 = size + ((getMeasuredWidth() * getData().getStyle().getButtonStyle().getInterval()) / 1000);
        int p2 = getMeasuredWidth() - size;
        if (x <= size && y <= size) {
            // up left
            handleMoveEvent(true, false, true, false);
        } else if (x >= p1 && x <= p1 + size && y <= size) {
            // up
            handleMoveEvent(true, false, false, false);
        } else if (x >= p2 && y <= size) {
            // up right
            handleMoveEvent(true, false, false, true);
        } else if (x <= size && y >= p1 && y <= p1 + size) {
            // left
            handleMoveEvent(false, false, true, false);
        } else if (x >= p1 && x <= p1 + size && y >= p1 && y <= p1 + size) {
            // center
            handleMoveEvent(false, false, false, false);
        } else if (x >= p2 && y >= p1 && y <= p1 + size) {
            // right
            handleMoveEvent(false, false, false, true);
        } else if (x < size && y >= p2) {
            // down left
            handleMoveEvent(false, true, true, false);
        } else if (x >= p1 && x <= p1 + size && y >= p2) {
            // down
            handleMoveEvent(false, true, false, false);
        } else if (x >= p2 && y >= p2) {
            // down right
            handleMoveEvent(false, true, false, true);
        }
    }

    private void handleRockerEvent(int x, int y) {
        int maxDistance = (getMeasuredWidth() / 2) - (rockerSize / 2);
        Point centerPoint = new Point(getMeasuredWidth() / 2, getMeasuredWidth() / 2);
        Point touchPoint = new Point(x, y);
        Point position = getRockerPositionPoint(centerPoint, touchPoint, maxDistance);
        rocker.setX(position.x - (float) (rockerSize / 2));
        rocker.setY(position.y - (float) (rockerSize / 2));
    }

    private Point getRockerPositionPoint(Point centerPoint, Point touchPoint, float maxDistance) {
        float lenX = (float) (touchPoint.x - centerPoint.x);
        float lenY = (float) (touchPoint.y - centerPoint.y);
        float lenXY = (float) Math.sqrt(lenX * lenX + lenY * lenY);
        double radian = Math.acos(lenX / lenXY) * (touchPoint.y < centerPoint.y ? -1 : 1);
        double angle = ConvertUtils.radian2Angle(radian);
        if (lenXY <= maxDistance) {
            handleAngleEvent(angle);
            return touchPoint;
        } else {
            int showPointX = (int) (centerPoint.x + maxDistance * Math.cos(radian));
            int showPointY = (int) (centerPoint.y + maxDistance * Math.sin(radian));
            handleAngleEvent(angle);
            return new Point(showPointX, showPointY);
        }
    }

    public enum Direction {
        DIRECTION_LEFT,
        DIRECTION_RIGHT,
        DIRECTION_UP,
        DIRECTION_DOWN,
        DIRECTION_UP_LEFT,
        DIRECTION_UP_RIGHT,
        DIRECTION_DOWN_LEFT,
        DIRECTION_DOWN_RIGHT,
        DIRECTION_CENTER
    }

    private Direction tempDirection = Direction.DIRECTION_CENTER;

    private void handleAngleEvent(double angle) {
        if (menu != null) {
            if ((ANGLE_0 <= angle && ANGLE_8D_OF_0P > angle || ANGLE_8D_OF_7P <= angle && ANGLE_360 > angle) && tempDirection != Direction.DIRECTION_RIGHT) {
                // right
                tempDirection = Direction.DIRECTION_RIGHT;
                handleMoveEvent(false, false, false, true);
            } else if (ANGLE_8D_OF_0P <= angle && ANGLE_8D_OF_1P > angle && tempDirection != Direction.DIRECTION_DOWN_RIGHT) {
                // down right
                tempDirection = Direction.DIRECTION_DOWN_RIGHT;
                handleMoveEvent(false, true, false, true);
            } else if (ANGLE_8D_OF_1P <= angle && ANGLE_8D_OF_2P > angle && tempDirection != Direction.DIRECTION_DOWN) {
                // down
                tempDirection = Direction.DIRECTION_DOWN;
                handleMoveEvent(false, true, false, false);
            } else if (ANGLE_8D_OF_2P <= angle && ANGLE_8D_OF_3P > angle && tempDirection != Direction.DIRECTION_DOWN_LEFT) {
                // down left
                tempDirection = Direction.DIRECTION_DOWN_LEFT;
                handleMoveEvent(false, true, true, false);
            } else if (ANGLE_8D_OF_3P <= angle && ANGLE_8D_OF_4P > angle && tempDirection != Direction.DIRECTION_LEFT) {
                // left
                tempDirection = Direction.DIRECTION_LEFT;
                handleMoveEvent(false, false, true, false);
            } else if (ANGLE_8D_OF_4P <= angle && ANGLE_8D_OF_5P > angle && tempDirection != Direction.DIRECTION_UP_LEFT) {
                // up left
                tempDirection = Direction.DIRECTION_UP_LEFT;
                handleMoveEvent(true, false, true, false);
            } else if (ANGLE_8D_OF_5P <= angle && ANGLE_8D_OF_6P > angle && tempDirection != Direction.DIRECTION_UP) {
                // up
                tempDirection = Direction.DIRECTION_UP;
                handleMoveEvent(true, false, false, false);
            } else if (ANGLE_8D_OF_6P <= angle && ANGLE_8D_OF_7P > angle && tempDirection != Direction.DIRECTION_UP_RIGHT) {
                // up right
                tempDirection = Direction.DIRECTION_UP_RIGHT;
                handleMoveEvent(true, false, false, true);
            }
        }
    }

    private void handleMoveEvent(boolean up, boolean down, boolean left, boolean right) {
        if (menu != null) {
            menu.getInput().sendKeyEvent(getData().getEvent().getUpKeycode(), up);
            menu.getInput().sendKeyEvent(getData().getEvent().getDownKeycode(), down);
            menu.getInput().sendKeyEvent(getData().getEvent().getLeftKeycode(), left);
            menu.getInput().sendKeyEvent(getData().getEvent().getRightKeycode(), right);
        }
        if (getData().getStyle().getStyleType() == ControlDirectionStyle.Type.BUTTON) {
            if (up && !down && !left && !right) {
                upLeftBtn.setVisibility(VISIBLE);
                upRightBtn.setVisibility(VISIBLE);
                downLeftBtn.setVisibility(GONE);
                downRightBtn.setVisibility(GONE);
            } else if (!up && down && !left && !right) {
                upLeftBtn.setVisibility(GONE);
                upRightBtn.setVisibility(GONE);
                downLeftBtn.setVisibility(VISIBLE);
                downRightBtn.setVisibility(VISIBLE);
            } else if (!up && !down && left && !right) {
                upLeftBtn.setVisibility(VISIBLE);
                upRightBtn.setVisibility(GONE);
                downLeftBtn.setVisibility(VISIBLE);
                downRightBtn.setVisibility(GONE);
            } else if (!up && !down && !left && right) {
                upLeftBtn.setVisibility(GONE);
                upRightBtn.setVisibility(VISIBLE);
                downLeftBtn.setVisibility(GONE);
                downRightBtn.setVisibility(VISIBLE);
            } else if (!up && !down && !left) {
                upLeftBtn.setVisibility(GONE);
                upRightBtn.setVisibility(GONE);
                downLeftBtn.setVisibility(GONE);
                downRightBtn.setVisibility(GONE);
            }
            setButtonStyle(centerBtn, !up && !down && !left && !right);
            setButtonStyle(upBtn, up && !down && !left && !right);
            setButtonStyle(downBtn, !up && down && !left && !right);
            setButtonStyle(leftBtn, !up && !down && left && !right);
            setButtonStyle(rightBtn, !up && !down && !left && right);
            setButtonStyle(upLeftBtn, up && !down && left && !right);
            setButtonStyle(upRightBtn, up && !down && !left && right);
            setButtonStyle(downLeftBtn, !up && down && left && !right);
            setButtonStyle(downRightBtn, !up && down && !left && right);
        }
    }

    private void handleDoubleEvent(boolean enable) {
        if (getData().getEvent().isSneak() && menu != null) {
            doubleClickEvent = enable;
            menu.getInput().sendKeyEvent(getData().getEvent().getSneakKeycode(), enable);
        }
    }

    private void cancelAllEvent() {
        if (getData().getStyle().getStyleType() == ControlDirectionStyle.Type.BUTTON) {
            upLeftBtn.setVisibility(GONE);
            upRightBtn.setVisibility(GONE);
            downLeftBtn.setVisibility(GONE);
            downRightBtn.setVisibility(GONE);
            for (AppCompatButton b : buttons) {
                setButtonStyle(b, false);
            }
        } else {
            int x;
            int y;
            x = (int) ((screenWidth - getMeasuredWidth()) * (getData().getBaseInfo().getXPosition() / 1000f));
            y = (int) ((screenHeight - getMeasuredHeight()) * (getData().getBaseInfo().getYPosition() / 1000f));
            if (!displayMode) {
                setX(x);
                setY(y);
            }
            setButtonPosition(area, 0, 0);
            setButtonPosition(rocker, (getMeasuredWidth() / 2) - (rockerSize / 2), (getMeasuredWidth() / 2) - (rockerSize / 2));
            tempDirection = Direction.DIRECTION_CENTER;
        }
        if (menu != null) {
            menu.getInput().sendKeyEvent(getData().getEvent().getUpKeycode(), false);
            menu.getInput().sendKeyEvent(getData().getEvent().getDownKeycode(), false);
            menu.getInput().sendKeyEvent(getData().getEvent().getLeftKeycode(), false);
            menu.getInput().sendKeyEvent(getData().getEvent().getRightKeycode(), false);
        }
    }

    public final BooleanProperty visibilityProperty() {
        if (visibilityProperty == null) {
            visibilityProperty = new BooleanPropertyBase() {

                public void invalidated() {
                    Schedulers.androidUIThread().execute(() -> {
                        boolean visible = get();
                        setVisibility(visible ? VISIBLE : GONE);
                    });
                }

                public Object getBean() {
                    return this;
                }

                public String getName() {
                    return "visibility";
                }
            };
        }

        return visibilityProperty;
    }

    @Override
    public CustomControl.ViewType getType() {
        return CustomControl.ViewType.CONTROL_DIRECTION;
    }

    @Override
    public String getViewId() {
        return getData().getId();
    }

    @Override
    public void switchParentVisibility() {
        setParentVisibility(!isParentVisibility());
    }
}
