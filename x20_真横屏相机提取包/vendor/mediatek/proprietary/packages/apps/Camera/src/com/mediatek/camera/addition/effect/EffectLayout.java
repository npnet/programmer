/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 *
 * MediaTek Inc. (C) 2014. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */

package com.mediatek.camera.addition.effect;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.StateSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EdgeEffect;
import android.widget.LinearLayout;

import com.android.camera.R;

import com.mediatek.camera.util.Log;

import java.util.LinkedList;

public class EffectLayout extends ViewGroup {
    private static final String TAG = "EffectLayout";
    private BaseAdapter mAdapter;
    private int mColumnCount; // 3
    private int mColumnWidth; // 1280
    private int mColumnHeight; // 720
    private int mMotionY;
    private int mDownPointX;
    private int mDownPointY;
    private int mUpPointX;
    private int mUpPointY;
    private int mTopPosition;
    private int mBottomPosition;
    private int mLastTopRow = -1;
    private int mDisplayWidth;
    private int mDispalyHeight;
    private int mTop;
    private int mPressX;
    private int mPressY;
    private View mPressedView;
    private View mSelectedView;
    private int mSelectedPosition;
    private Handler mHandler;
    private float mDensity = 0;
    private boolean mTouchFocused = false;
    private OnItemClickListener mItemClickListener;
    private static final int MSG_DISMISS = 100;
    private static final int MSG_SCROLL_VIEW = 101;
    private static final int MSG_ITEM_CLICK = 102;
    private static final int MSG_SCROLL_DONE = 103;
    private Context mContext;
    private OnScrollListener mOnScrollListener;
    private int mLastMoveDistance;
    private int mLastDirection  = OnScrollListener.DIRCTION_UNKOWN;
    private static final int SCROLL_STEP = 50;
    private static final int SCROLL_SPEED = 10;
    private boolean mNeedUpdateView = false;
    private EdgeEffect mEdgeGlowTop;
    private EdgeEffect mEdgeGlowBottom;
    private long mDownTime;
    private long mMoveTime;
    private long mUpTime;
    private int mDownRow;
    private static final long TIME_BOUND = 100;

    private static final int DOWN_STATE = 0;
    private static final int MOVE_STATE = 1;
    private static final int UP_STATE = 2;
    private int mEventState = UP_STATE;

    private Drawable mSelectorDrawable;
    private Rect mSelectorRect = new Rect();
    private int mSelectionLeftPadding = 0;
    private int mSelectionTopPadding = 0;
    private int mSelectionRightPadding = 0;
    private int mSelectionBottomPadding = 0;

    private static final int MOVE_DISTANCE = 100;
    private boolean mNeedScrollOut = false;

    private LinkedList<View> mRecycleView = new LinkedList<View>();

    public interface OnItemClickListener {
        void onItemClick(View view, int position);
    }

    public interface OnItemSelectedListener {
        void onItemSelected(View view, int position);
    }

    public EffectLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        mDensity = dm.density;
        mSelectorDrawable = getResources().getDrawable(R.drawable.bg_pressed);
        mSelectorDrawable.setCallback(this);
        setWillNotDraw(false);
        mHandler = new Handler() {

            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                case MSG_DISMISS:
                    View view = (View) msg.obj;
                    //view.setBackgroundResource(R.drawable.unselected_border);
                    break;
                case MSG_SCROLL_VIEW:
                    int[] data = (int[]) msg.obj;
                    scrollViewByDistance(data[0], data[1], data[2]);
                    showSelectedBorder(mSelectedPosition);
                    break;
                case MSG_ITEM_CLICK:
                    onItemClick((View) msg.obj);
                    break;
                case MSG_SCROLL_DONE:
                    scrollDone();
                    break;
                default:
                    break;
                }
            }

        };
    }

    @Override
    public void onLayout(boolean changed, int l, int t, int r, int b) {
        Log.d(TAG, "onLayout(), left:" + l + ", top:" + t + ", right:" + r + ", bottom:" + b);
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            child.layout(child.getLeft(), child.getTop(), child.getRight(), child.getBottom());
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        Log.d(TAG, "onMeasure()");
        super.onMeasure(MeasureSpec.makeMeasureSpec(mDisplayWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(mDispalyHeight, MeasureSpec.EXACTLY));
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            child.measure(mColumnWidth, mColumnHeight);
        }
    }

    @Override
    public void onDraw(Canvas canvas) {
        Log.d(TAG, "onDraw");
        if (mNeedScrollOut) {
            mHandler.post(new Runnable() {

                @Override
                public void run() {
                    Log.d(TAG, "onScrollOut");
                    if (mOnScrollListener != null) {
                        mOnScrollListener.onScrollOut(EffectLayout.this,
                                OnScrollListener.DIRECTION_UP);
                    }
                }
            });

            mNeedScrollOut = false;
        }

    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        Log.d(TAG, "action = " + ev.getAction() + ", mTopPosition = " +
                "" + mTopPosition + ", X = " + ev.getX() + ", Y = " + ev.getY());
        int action = ev.getAction();
        switch(action) {
        case MotionEvent.ACTION_DOWN:
            mDownTime = System.currentTimeMillis();
            mPressX = (int) ev.getX();
            mPressY = mMotionY = (int) ev.getY(); //record current y position
            // if the touch point not in lomo effect view area, return directly.
            if (mTopPosition < mPressY) {
                mEventState = DOWN_STATE;
                // if press down, remove the message which use to scroll lomo effect view.
                if (mHandler != null) {
                    mHandler.removeMessages(MSG_SCROLL_VIEW);
                }
            } else {
                return true;
            }
            releaseEdgeEffects();
            clearPressedState();
            mPressedView = getViewByPosition(mPressX, mPressY);
            mDownRow = computeCurrentRow(mPressY);
            mDownPointX = mPressX;
            mDownPointY = mMotionY;
            break;
        case MotionEvent.ACTION_MOVE:
            mEventState = MOVE_STATE;
            mMoveTime = System.currentTimeMillis();
            int x = (int) ev.getX();
            int y = (int) ev.getY();

            if (isTouchFocus(x, y, true) && !mTouchFocused && mPressedView != null) {
                mSelectorRect.setEmpty();
                setPressed(true);
                mPressedView.setPressed(true);
                positionSelector(mDownRow, mPressedView);
                mTouchFocused = true;
            } else if (!isTouchFocus(x, y, true) && mTouchFocused && mPressedView != null) {
                mSelectorRect.setEmpty();
                setPressed(false);
                mPressedView.setPressed(false);
                mTouchFocused = false;
            }

            int deltaY = y - mMotionY; // user move distance
            mLastMoveDistance = deltaY;

            // make max scroll distance as column height, else lomo effect view display incomplete.
            if (Math.abs(deltaY) > mColumnHeight) {
                deltaY = mColumnHeight * deltaY / Math.abs(deltaY);
            }
            if (Math.abs(deltaY) > 0) {
                scrollViewByDistance(deltaY);
            }
            mMotionY = y;
            break;
        case MotionEvent.ACTION_CANCEL:
            mSelectorRect.setEmpty();
            releaseEdgeEffects();
            invalidate();
            break;
        case MotionEvent.ACTION_UP:
            mEventState = UP_STATE;
            mUpTime = System.currentTimeMillis();
            mUpPointX = (int) ev.getX();
            mUpPointY = (int) ev.getY();
                if (isTouchFocus(mUpPointX, mUpPointY, false) && !mTouchFocused
                        && mPressedView != null) {
                    mSelectorRect.setEmpty();
                    setPressed(true);
                    mPressedView.setPressed(true);
                    positionSelector(mDownRow, mPressedView);
                }
            releaseEdgeEffects();

            // if user still scroll down when in first page, after finger up, ui should
            // rebound if scroll distance lower than mDispalyHeight / 2, otherwise ui should
            // fade out
            if (mTopPosition > 0 && mTopPosition <= mDispalyHeight / 2) {
                scrollViewByDistance(mTopPosition, 0, SCROLL_STEP);
            } else if (mTopPosition > mDispalyHeight / 2) {
                scrollViewByDistance(mTopPosition, mDispalyHeight, SCROLL_STEP);
            }

            // when user's finger up, the current ui should keep nine full effect, so
            // scroll right if the distance of edge on right lower than mColumnHeight / 2
            // otherwise, scroll left.
            if (mTopPosition < 0) {
                int edgeDistance = mTopPosition % mColumnHeight;
                if (Math.abs(edgeDistance) >= mColumnHeight / 2) {
                    scrollViewByDistance(mTopPosition,
                            mTopPosition - (mColumnHeight - Math.abs(edgeDistance)), 20);
                } else {
                    scrollViewByDistance(mTopPosition, mTopPosition - edgeDistance, 20);
                }
            }

                Log.d(TAG, "mUpPointX:" + mUpPointX + ", mUpPointY:" + mUpPointY + ", distance:"
                        + Math.abs(mUpPointY - mDownPointY));
                if (Math.abs(mUpPointY - mDownPointY) > (8 * mDensity + 0.5f)
                    || Math.abs(mUpPointX - mDownPointX) > (8 * mDensity + 0.5f)) {
                // the selected view should be highlight if it is in sight after scroll up or down
                if (isSelectedViewInSight() && mSelectedView != null) {
                    mSelectedView.setBackgroundResource(R.drawable.selected_border);
                }
                invalidate();
                return true;
            }
            mTouchFocused = false;
            break;
        }
        return false;
    }

    public void scrollToSelectedPosition(int selectedPosition) {
        int row = selectedPosition / 3;
        if (row > 1) {
            int distance = (row - 1) * mColumnHeight;
            scrollViewByDistance(mTopPosition, -distance, mColumnHeight / 2);
        } else if (row <= 1) {
            scrollViewByDistance(mTopPosition, 0, mColumnHeight / 2);
        }
    }

    public interface OnScrollListener {
        public static final int DIRCTION_UNKOWN = -1;
        public static final int DIRECTION_DOWN = 0;
        public static final int DIRECTION_UP = 1;
        public void onScrollOut(EffectLayout view, int direction);
        public void onScrollDone(EffectLayout view, int startPosition, int endPosition);
    }

    // scroll from startPoint to endPoint, scroll distance of step every time
    private void scrollViewByDistance(int startPoint, int endPoint, int step) {
        Log.d(TAG, "scrollViewByDistance(), startPoint:" + startPoint + ", endPoint:" + endPoint
                + "," + "step:" + step + ", mEventState:" + mEventState);
        int distance = endPoint - startPoint;
        boolean needScroll = false;
        if (distance <= 0) {
            // scroll up in landscape or scroll left in port
            if ((distance + step) <= 0) {
                needScroll = true;
            }
            scrollViewByDistance((distance + step) <= 0 ? - step : distance);
            startPoint -= step;
        } else {
            // scroll down in landscape or scroll right in port
            if ((distance - step) >= 0) {
                needScroll = true;
            }
            scrollViewByDistance((distance - step) >= 0 ? step : distance);
            startPoint += step;
        }
        int[] data = {startPoint, endPoint, step};
        if (mEventState == UP_STATE) {
            if (needScroll) {
                Message msg = mHandler.obtainMessage(MSG_SCROLL_VIEW, data);
                mHandler.sendMessageDelayed(msg, SCROLL_SPEED);
            } else {
                mHandler.sendEmptyMessage(MSG_SCROLL_DONE);
            }
        }

    }

    private void scrollViewByDistance(int deltaY) {
        if (deltaY >= 0 && mTopPosition <= 0) {
            // do nothing currently
        } else if (deltaY < 0 && mBottomPosition >= mColumnHeight * 3) {
            if (mBottomPosition + deltaY < mColumnHeight * 3) {
                mEdgeGlowBottom.onPull(deltaY / (float) getHeight());
                if (!mEdgeGlowTop.isFinished()) {
                    mEdgeGlowTop.onRelease();
                }
                if (!mEdgeGlowTop.isFinished() || !mEdgeGlowBottom.isFinished()) {
                    postInvalidateOnAnimation();
                }
                deltaY = mColumnHeight * 3 - mBottomPosition;
            }
        }
        Log.d(TAG, "mTopPosition = " + mTopPosition + "deltaY = " + deltaY);
        // the max distance scroll should not exceed mColumnHeight,
        // if the mTopPosition > 0, it is no need to follow this rule.
        if (mTopPosition < 0 && Math.abs(deltaY) > mColumnHeight) {
            if (deltaY > 0) {
                deltaY -= mColumnHeight;
            } else {
                deltaY += mColumnHeight;
            }
        }
        mTopPosition += deltaY;
        mBottomPosition += deltaY;
        scrollBy(0, -deltaY); // scroll to actual user move distance
        if (mTopPosition <= 0) {
            scrollView(mTopPosition);
        }
        if (mTopPosition >= mDispalyHeight) {
            mNeedScrollOut = true;
        }
    }

    private void scrollDone() {
        // notify the LomoEffectsManager to update effect ids
        if (mTopPosition <= 0) {
            int currentRow = -mTopPosition / mColumnHeight;
            int edgeDistance = mTopPosition % mColumnHeight;
            if (Math.abs(edgeDistance) >= mColumnHeight / 2) {
                currentRow += 1;
            }

            int startPosition = currentRow * 3;
            int endPosition = startPosition + 9;
            if (endPosition > mAdapter.getCount()) {
                endPosition = mAdapter.getCount();
            }
            if (mOnScrollListener != null) {
                mOnScrollListener.onScrollDone(EffectLayout.this, startPosition, endPosition);
            }
            mNeedUpdateView = true;
        }
    }

    private View getViewByPosition(int x, int y) {
        int row = computeCurrentRow(y);
        int column = computeCurrentColumn(x);
        View v = null;
        LinearLayout layout = (LinearLayout) getChildAt(row % 4);
        if (layout != null) {
            v = layout.getChildAt(column);
        }
        return v;
    }

    private boolean isTouchFocus(int x, int y, boolean isMoving) {
        int distanceX = Math.abs(x - mPressX);
        int distanceY = Math.abs(y - mPressY);
        int max = (int) (8 * mDensity + 0.5f);
        if (isMoving) {
            // in the process of moving
            long timeInterval = mMoveTime - mDownTime;
            Log.d(TAG, "Moving, distanceX:" + distanceX + ", distanceY:" + distanceY + ", max:"
                    + max + ", " + "timeInterval:" + timeInterval);
  return (distanceX < max && distanceY < max) && timeInterval > TIME_BOUND;
        } else {
            // after up finger
            long timeInterval = mUpTime - mDownTime;
            Log.d(TAG, "Up, distanceX:" + distanceX + ", distanceY:" + distanceY + ", max:" + max
                    + ", " + "timeInterval:" + timeInterval);
            return (distanceX < max && distanceY < max);
        }
    }

    private void scrollView(int topPosition) {
        int topRow = -topPosition / mColumnHeight;
        int count = mAdapter.getCount();
        if (mLastTopRow == topRow) {
            int inRow = topRow + 3;
            final LinearLayout child = (LinearLayout) (getChildAt(inRow % 4));
            if (mNeedUpdateView) {
                int startPoint = inRow * 3;
                for (int i = startPoint; i < startPoint + mColumnCount && i < count; i++) {
                    mAdapter.getView(i, mRecycleView.get(i % 12), child);
                }
                mNeedUpdateView = false;
            }

            mHandler.postDelayed(new Runnable() {

                @Override
                public void run() {
                    if (child != null) {
                        child.setAlpha(1.0f);
                    }
                }
            }, 150);

            return;
        }

        Log.d(TAG, "topRow:" + topRow + ", mLastTopRow:" + mLastTopRow + ",topPosition:"
                + topPosition);
        int outDistance = -topPosition % mColumnHeight;
        int startPoint = 0;
        if (mLastTopRow > topRow) {
            int inRow = topRow;
            int outRow = inRow + 4;
            final LinearLayout child = (LinearLayout) getChildAt(inRow);
            if (child != null) {
                child.layout(0, inRow * mColumnHeight, mColumnWidth * mColumnCount, (inRow + 1)
                        * mColumnHeight);
                mHandler.postDelayed(new Runnable() {

                    @Override
                    public void run() {
                        if (child != null) {
                            child.setAlpha(1.0f);
                        }
                    }
                }, 150);
            }
            startPoint = inRow * 3;

            for (int i = startPoint; i < startPoint + mColumnCount; i++) {
                mAdapter.getView(i, mRecycleView.get(i), child);
                mRecycleView.get(i).setVisibility(View.VISIBLE);
                mRecycleView.get(i).setClickable(true);
            }

        } else {
            if (outDistance >= 0) {
                if (topRow > 0) {
                    int outRow = topRow - 1;
                    int inRow = outRow + 4;

                    final LinearLayout child = (LinearLayout) (getChildAt(outRow));
                    if (child != null) {
                        child.layout(0, inRow * mColumnHeight, mColumnWidth * mColumnCount,
                                (inRow + 1) * mColumnHeight);
                        mHandler.post(new Runnable() {

                            @Override
                            public void run() {
                                if (child != null) {
                                    child.setAlpha(0.0f);
                                }
                            }
                        });
                    }

                    if (outRow - 1 >= 0) {
                        final LinearLayout child2 = (LinearLayout) (getChildAt(outRow - 1));
                        mHandler.post(new Runnable() {

                            @Override
                            public void run() {
                                if (child != null) {
                                    child2.setAlpha(1.0f);
                                }
                            }
                        });
                    }
                    startPoint = inRow * 3;
                    int j = outRow * 3;
                    for (int i = startPoint; i < startPoint + mColumnCount && i < count; i++) {
                        mAdapter.getView(i, mRecycleView.get(j), child);
                        j++;
                    }

                    // if the total number of effect is not times of mColumnCount, the
                    // extra cell view set to be invisible
                    int dis = (startPoint + mColumnCount) - count;
                    for (int i = 0; i < dis; i++) {
                        View view = null;
                        if (child != null) {
                            view = child.getChildAt(mColumnCount - i - 1);
                        }

                        if (view != null) {
                            view.setVisibility(View.INVISIBLE);
                            view.setClickable(false);
                        }
                    }
                } else if (topRow == 0) {
                    int inRow = 3;
                    LinearLayout child = (LinearLayout) (getChildAt(inRow));
                    startPoint = inRow * 3;
                    for (int i = startPoint; i < startPoint + mColumnCount && i < count; i++) {
                        mAdapter.getView(i, mRecycleView.get(i), child);
                    }
                }
            }
        }
        mLastTopRow = topRow;
    }

    public void showSelectedBorder(int selectedPosition) {
        mSelectedPosition = selectedPosition;
        if (isSelectedViewInSight()) {
            int row = selectedPosition / 3;
            ViewGroup group = (ViewGroup) getChildAt(row % 4);
            if (group != null) {
                View v = group.getChildAt(selectedPosition % 3);
                if (v != null) {
                    mSelectedView = v;
                    v.setBackgroundResource(R.drawable.selected_border);
                }
            }
        }
    }

    private boolean isSelectedViewInSight() {
        int selectedViewPos = (mSelectedPosition / 3 + 1) * mColumnHeight;
        if (selectedViewPos >= -mTopPosition
                && selectedViewPos <= -mTopPosition + mDispalyHeight) {
            return true;
        }
        return false;
    }

    private void onItemClick(View view) {
        mSelectorRect.setEmpty();
        setPressed(false);
        if (mPressedView == null) {
            return;
        }
        mPressedView.setPressed(false);

        clearPressedState();
        mSelectedView = view;
        view.setBackgroundResource(R.drawable.selected_border);
        int position = pointToPosition(mUpPointX, mUpPointY);
        if (position >= mAdapter.getCount()) {
            position = mAdapter.getCount() - 1;
        }
        mSelectedPosition = position;
        if (mItemClickListener != null) {
            mItemClickListener.onItemClick(view, position);
        }
    }
    private int pointToPosition(int x, int y) {
        int row = computeCurrentRow(y);
        int column = computeCurrentColumn(x);
        int position = row * mColumnCount + column;
        return position;
    }

    private int computeCurrentRow(int y) {
        return (y - mTopPosition) / mColumnHeight;
    }

    private int computeCurrentColumn(int x) {
        return x / mColumnWidth;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        mItemClickListener = listener;
    }

    public void setColumnCount(int count) {
        mColumnCount = count;
    }

    public int getColumnCount() {
        return mColumnCount;
    }


    public void setColumnWidth(int columnWidth) {
        mColumnWidth = columnWidth;
    }

    public void setColumnHeight(int columnHeight) {
        mColumnHeight = columnHeight;
    }

    public void setDisplaySize(int width, int height) {
        if (mDisplayWidth != width || mDispalyHeight != height) {
            mDisplayWidth = width;
            mDispalyHeight = height;
            requestLayout();
        }
    }


    public void NotifyDataChange() {
        removeAllViews();
    }

    private void bindView() {
        removeAllViews();
        mRecycleView.clear();
        int count = mAdapter.getCount();
        // compute one screen can show how many cells
        int maxCountsInScreen = (mDispalyHeight / mColumnHeight) * mColumnCount;
        // compute should inflate how many cells, since may be two row display incomplete
        count = Math.min(count, maxCountsInScreen + mColumnCount);
        View[] cells = null;
        int j = 0;
        LinearLayout layout;
        Log.d(TAG, "bindView(), maxCountsInScreen:" + maxCountsInScreen + ", count:" + count);
        for (int i = 0; i < count; i++) {

            if (j % mColumnCount == 0) {
                cells = new View[mColumnCount]; // create number as column count(3) array
            }

            final View view = mAdapter.getView(i, null, null);
            if (view == null) {
                continue;
            }
            mRecycleView.add(view);
            view.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View v) {
                    Message msg  = mHandler.obtainMessage(MSG_ITEM_CLICK, v);
                    mHandler.sendMessageDelayed(msg, 200);
                }
            });
            cells[j] = view; // fill array item
            j++;
            if (j == mColumnCount) {
                j = 0;
                layout = new LinearLayout(getContext());
                layout.setMotionEventSplittingEnabled(false);
                addLayout(layout, cells);

            } else if (i >= count - 1 && j > 0) { // fill the last count
                layout = new LinearLayout(getContext());
                addLayout(layout, cells);
            }
        }
    }

    private void addLayout(LinearLayout layout, View[] cells) {
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        addCell(layout, cells);
        addView(layout);
        layout.measure(mColumnWidth * mColumnCount, mColumnHeight);
        layout.layout(0, mTop, mColumnWidth * mColumnCount, mTop + mColumnHeight);
        mTop += mColumnHeight;
    }

    private void addCell(LinearLayout layout, View[] cells) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                mColumnWidth, mColumnHeight);
        for (View view : cells) {
            if (view != null) {
                layout.addView(view, params);
            }
        }
    }

    public void setAdapter(BaseAdapter adapter) {
        mAdapter = adapter;
        mBottomPosition = mColumnHeight * (adapter.getCount() / mColumnCount);
        if (adapter.getCount() % mColumnCount != 0) {
            //may be total cell can't divide by column count
            mBottomPosition += mColumnHeight;
        }
        bindView();
    }

    private void clearPressedState() {
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            LinearLayout layout = (LinearLayout) getChildAt(i);
            int count = layout.getChildCount();
            for (int j = 0; j < count; j++) {
                View v = layout.getChildAt(j);
                v.setBackgroundResource(R.drawable.unselected_border);
            }
        }
    }

    public void setOnScrollListener(OnScrollListener listner) {
        mOnScrollListener = listner;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        // draw selector on top
        drawSelector(canvas);
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        mSelectorDrawable.setState(getDrawableState());
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mSelectorDrawable.setBounds(0, 0, w, h);
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return super.verifyDrawable(who) || (who == mSelectorDrawable);
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
        if (mSelectorDrawable != null) {
            mSelectorDrawable.jumpToCurrentState();
        }
    }

    public void setSelector(int resID) {
        setSelector(getResources().getDrawable(resID));
    }

    public void setSelector(Drawable sel) {
        if (mSelectorDrawable != null) {
            mSelectorDrawable.setCallback(null);
            unscheduleDrawable(mSelectorDrawable);
        }
        mSelectorDrawable = sel;
        Rect padding = new Rect();
        sel.getPadding(padding);
        mSelectionLeftPadding = padding.left;
        mSelectionTopPadding = padding.top;
        mSelectionRightPadding = padding.right;
        mSelectionBottomPadding = padding.bottom;
        sel.setCallback(this);
        updateSelectorState();
    }

    public Drawable getSelector() {
        return mSelectorDrawable;
    }

    private void updateSelectorState() {
        if (mSelectorDrawable != null) {
            if (shouldShowSelector()) {
                mSelectorDrawable.setState(getDrawableState());
            } else {
                mSelectorDrawable.setState(StateSet.NOTHING);
            }
        }
    }

    /**
     * Indicates whether this view is in a state where the selector should be drawn. This will
     * happen if we have focus but are not in touch mode, or we are in the middle of displaying
     * the pressed state for an item.
     *
     * @return True if the selector should be shown
     */
    private boolean shouldShowSelector() {
        return (!isInTouchMode()) || isPressed();
    }

    private void drawSelector(Canvas canvas) {
        if (!mSelectorRect.isEmpty()) {
            final Drawable selector = mSelectorDrawable;
            selector.setBounds(mSelectorRect);
            selector.draw(canvas);
        }
    }

    private void positionSelector(int row, View sel) {
        Rect selectorRect = mSelectorRect;
        selectorRect.set(sel.getLeft(), row * mColumnHeight, sel.getRight(), (row + 1)
                * mColumnHeight);
        positionSelector(selectorRect.left, selectorRect.top, selectorRect.right,
                selectorRect.bottom);
        refreshDrawableState();
    }

    private void positionSelector(int l, int t, int r, int b) {
        mSelectorRect.set(l - mSelectionLeftPadding, t - mSelectionTopPadding, r
                + mSelectionRightPadding, b + mSelectionBottomPadding);
    }

    @Override
    public void setOverScrollMode(int mode) {
        if (mode != OVER_SCROLL_NEVER) {
            if (mEdgeGlowTop == null) {
                Context context = getContext();
                mEdgeGlowTop = new EdgeEffect(context);
                mEdgeGlowBottom = new EdgeEffect(context);
            }
        } else {
            mEdgeGlowTop = null;
            mEdgeGlowBottom = null;
        }
        super.setOverScrollMode(mode);
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        drawEdgeEffects(canvas);
    }

    private void drawEdgeEffects(Canvas canvas) {
        if (mEdgeGlowTop != null) {
            final int scrollY = getScrollY();
            if (!mEdgeGlowTop.isFinished()) {
                final int restoreCount = canvas.save();
                final int width = getWidth();

                canvas.translate(0, Math.min(0, scrollY));
                mEdgeGlowTop.setSize(width, getHeight());
                if (mEdgeGlowTop.draw(canvas)) {
                    postInvalidateOnAnimation();
                }
                canvas.restoreToCount(restoreCount);
            }
            if (!mEdgeGlowBottom.isFinished()) {
                final int restoreCount = canvas.save();
                final int width = getWidth();
                final int height = getHeight();

                canvas.translate(-width,
                        Math.max(getScrollRange(), scrollY) + height);
                canvas.rotate(180, width, 0);
                mEdgeGlowBottom.setSize(width, height);
                if (mEdgeGlowBottom.draw(canvas)) {
                    postInvalidateOnAnimation();
                }
                canvas.restoreToCount(restoreCount);
            }
        }
    }

    private int getScrollRange() {
        int scrollRange = 0;
        if (getChildCount() > 0) {
            View child = getChildAt(0);
            scrollRange = Math.max(0,
                    child.getHeight() - getHeight());
        }
        return scrollRange;
    }

    private void releaseEdgeEffects() {
        if (mEdgeGlowTop != null) {
            mEdgeGlowTop.onRelease();
            mEdgeGlowBottom.onRelease();
        }
    }

    private void finishGlows() {
        if (mEdgeGlowTop != null) {
            mEdgeGlowTop.finish();
            mEdgeGlowBottom.finish();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus()) {
            finishGlows();
            invalidate();
        }
    }
}
