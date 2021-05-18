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

package com.emanuelef.touchinjector.ime;

import android.graphics.PointF;
import android.util.Log;

public class VirtualStick {
    private static final String TAG = "VirtualStick";
    private static final double mPi2 = Math.PI / 2;
    private boolean mPressed;
    private final PointF mLastPos;
    private final PointF mCenter;
    private final int mRadius;
    private final int mPointer;
    private final boolean mRoundedStick;
    private final InputInjector mInjector;

    public VirtualStick(int pointer, float cx, float cy, int radius) {
        mRadius = radius;
        mCenter = new PointF(cx, cy);
        mRoundedStick = true;
        mLastPos = new PointF();
        mInjector = InputInjector.getInstance();
        mPointer = pointer;
        mPressed = false;
    }

    // converts joystick coordinates in range [-1, 1] to on-screen coordinates
    private PointF convertCoords(float vx, float vy) {
        PointF toPos;

        if(mRoundedStick) {
            double angle = Math.atan2(vy, vx) + mPi2;
            double hypot = Math.hypot(vx, vy);
            float x = mCenter.x + (int)(Math.sin(angle) * mRadius * hypot);
            float y = mCenter.y + (int)(Math.cos(angle) * mRadius * hypot);
            toPos = new PointF(x, y);
        } else {
            int x = (int)(mCenter.x - mRadius + (vx + 1) * mRadius);
            int y = (int)(mCenter.y - mRadius + (vy + 1) * mRadius);
            toPos = new PointF(x, y);
        }

        return toPos;
    }

    public void moveTo(PointF vPos, long delay) {
        PointF toPos = convertCoords(vPos.x, vPos.y);

        if(!mPressed) {
            Log.d(TAG, mPointer + " touchDown: " + mCenter);
            mInjector.touchDown(mPointer, mCenter);
            mLastPos.set(0, 0);
            mPressed = true;
            delay += 10;
        }

        if(vPos.equals(mLastPos))
            return;

        Log.d(TAG, mPointer + " moveTo: " + toPos);

        if(delay > 0)
            mInjector.addDelay(delay);

        mInjector.touchMove(mPointer, toPos);
        mLastPos.set(vPos.x, vPos.y);
    }

    public void moveToCenter(long delay) {
        moveTo(new PointF(0, 0), delay);
    }

    public void release() {
        if(!mPressed)
            return;

        Log.d(TAG, mPointer + " touchUp");

        mInjector.touchUp(mPointer);
        mPressed = false;
    }

    public void press() {
        Log.d(TAG, mPointer + " press");

        if(!mPressed) {
            mInjector.touchDown(mPointer, mCenter);
            mInjector.addDelay(10);
        }

        mInjector.touchUp(mPointer);
        mLastPos.set(0, 0);
        mPressed = false;
    }

    public boolean isPressed() {
        return mPressed;
    }

    public PointF getPosition() {
        return mLastPos;
    }
}
