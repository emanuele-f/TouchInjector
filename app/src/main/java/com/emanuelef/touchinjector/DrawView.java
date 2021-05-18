/*
 * This file is part of TouchInjector.
 *
 * TouchInjector is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * TouchInjector is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with TouchInjector.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright 2021 - Emanuele Faranda
 */

package com.emanuelef.touchinjector;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import androidx.annotation.Nullable;

public class DrawView extends View {
    private static final String TAG = "DrawView";
    private static final long FADE_STEP = 33; // 30 fps
    private final HashMap<Integer, Pointer> mPointers;
    private final HashMap<Integer, Pointer> mFading;
    private final Paint mPaint;

    private static class Pointer {
        int mId;
        Path mPath;
        PointF mPos;
        int mFadingStep;

        Pointer(int id) {
            mId = id;
            mPos = null;
            mPath = new Path();
            mFadingStep = 0;
        }
    }

    public DrawView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        mPointers = new HashMap<>();
        mFading = new HashMap<>();
        mPaint = new Paint();

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setColor(0xFF555555);
    }

    private void handlePointerEvent(int idx, MotionEvent event) {
        int pointer = event.getPointerId(idx);
        float x = event.getX(idx);
        float y = event.getY(idx);
        int action = event.getActionMasked();

        //Log.d(TAG, "onTouchEvent: " + pointer + " -> " + action);
        Pointer ptr = mPointers.get(pointer);

        if(ptr == null) {
            ptr = new Pointer(pointer);
            mPointers.put(pointer, ptr);
        }

        switch(action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                ptr.mPath.moveTo(x, y);
                ptr.mPos = new PointF(x, y);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                ptr.mFadingStep = 15; // fade in about 500 ms
                mFading.put(pointer, ptr);
                mPointers.remove(pointer);
                break;
            case MotionEvent.ACTION_MOVE:
                ptr.mPath.lineTo(x, y);
                ptr.mPos = new PointF(x, y);
                break;
            case MotionEvent.ACTION_CANCEL:
                mPointers.clear();
                mFading.clear();
                break;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();

        if(action == MotionEvent.ACTION_MOVE) {
            for(int i = 0; i < event.getPointerCount(); i++)
                handlePointerEvent(i, event);
        } else
            handlePointerEvent(event.getActionIndex(), event);

        invalidate();
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        Iterator<Map.Entry<Integer, Pointer>> it = mPointers.entrySet().iterator();
        mPaint.setStrokeWidth(2.0f);

        // Draw the down pointers
        while(it.hasNext()) {
            Pointer ptr = it.next().getValue();

            canvas.drawPath(ptr.mPath, mPaint);

            if(ptr.mPos != null)
                canvas.drawCircle(ptr.mPos.x, ptr.mPos.y, 80.0f, mPaint);
        }

        // Draw the previously down pointers
        it = mFading.entrySet().iterator();

        while(it.hasNext()) {
            Pointer ptr = it.next().getValue();

            if((ptr.mPos == null) || (--ptr.mFadingStep <= 0)) {
                it.remove();
                continue;
            }

            mPaint.setStrokeWidth(ptr.mFadingStep * 2.f / 15.f);

            canvas.drawPath(ptr.mPath, mPaint);
            canvas.drawCircle(ptr.mPos.x, ptr.mPos.y, 80.0f, mPaint);
        }

        if(mFading.size() != 0)
            postInvalidateDelayed(FADE_STEP);
    }
}
