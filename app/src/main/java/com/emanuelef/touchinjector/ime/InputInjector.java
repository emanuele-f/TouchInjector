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
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.MotionEvent.PointerProperties;
import android.view.MotionEvent.PointerCoords;

import com.emanuelef.touchinjector.ParcelableUtil;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

public class InputInjector {
    private static final String TAG = "InputInjector";
    private static final int ACTION_STOP = 0xFF;

    private static InputInjector mInstance;
    private final HashMap<Integer, Pointer> mPointers;
    private final Thread mThread;
    private final LinkedBlockingQueue<PointersState> mQueue;

    private long mDelay;
    private long mLastEventMillis;
    DataOutputStream mOutputStream;

    private InputInjector() {
        mPointers = new HashMap<>();
        mQueue = new LinkedBlockingQueue<>(500);
        mDelay = 0;
        mLastEventMillis = 0;

        mThread = new Thread(() -> {
            try {
                while(true) {
                    PointersState state = mQueue.take();

                    if(state.mAction == ACTION_STOP)
                        break;

                    long millis = SystemClock.uptimeMillis();
                    long doAt = mLastEventMillis + state.mDelay;

                    if(millis < doAt) {
                        long toSleep = doAt - millis;
                        Thread.sleep(toSleep);
                        millis = SystemClock.uptimeMillis();
                    }

                    MotionEvent event = sendMotionEvent(state, millis, millis);
                    //Log.d(TAG, "EV: " + event.toString());
                    mLastEventMillis = millis;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        mThread.start();
    }

    private static class Pointer {
        int mId;
        PointF mPos;

        Pointer(int id, PointF pos) {
            mId = id;
            mPos = new PointF(pos.x, pos.y);
        }
    }

    public static void start() {
        mInstance = new InputInjector();
    }

    public static void end() {
        if(mInstance == null)
            return;

        mInstance.stop();
        mInstance = null;
    }

    public static InputInjector getInstance() {
        return mInstance;
    }

    private void stop() {
        PointersState state = new PointersState();
        state.mAction = ACTION_STOP;

        try {
            mQueue.put(state);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        while(mThread.isAlive()) {
            try {
                Log.d(TAG, "Joining thread...");
                mThread.join();
            } catch (InterruptedException e) {
                Log.e(TAG, "Joining thread failed");
            }
        }
    }

    static class PointersState {
        int mAction;
        int mNumPointers;
        PointerCoords[] mPointerCoords;
        PointerProperties[] mPointerProps;
        long mDelay;

        PointersState() {}
    }

    private PointersState buildMotionEvent(int pointer, int action, long delay) {
        int numPointers = mPointers.size();

        PointerCoords[] mPointerCoords = new PointerCoords[numPointers];
        PointerProperties[] mPointerProps = new PointerProperties[numPointers];

        Iterator<Map.Entry<Integer, Pointer>> it = mPointers.entrySet().iterator();
        int j = 0;

        while(it.hasNext()) {
            Pointer ptr = it.next().getValue();
            int i = ((ptr.mId == pointer) ? 0 : ++j);

            mPointerProps[i] = new PointerProperties();
            mPointerProps[i].id = ptr.mId;
            mPointerProps[i].toolType = MotionEvent.TOOL_TYPE_UNKNOWN;

            mPointerCoords[i] = new PointerCoords();
            mPointerCoords[i].pressure = 1.0f;
            mPointerCoords[i].size = 1.0f;
            mPointerCoords[i].x = ptr.mPos.x;
            mPointerCoords[i].y = ptr.mPos.y;

            //Log.d(TAG, "MotionEvent: " + mPointerCoords[i].x + ", " + mPointerCoords[i].y);
        }

        PointersState state = new PointersState();
        state.mNumPointers = numPointers;
        state.mPointerCoords = mPointerCoords;
        state.mPointerProps = mPointerProps;
        state.mAction = action;
        state.mDelay = delay;

        //Log.d(TAG, "MotionEvent: " + numPointers + " -> " + action);

        return state;
    }

    public void addDelay(long millis) {
        mDelay += millis;
    }

    private void postMotionEvent(int pointer, int action) {
        PointersState state = buildMotionEvent(pointer, action, mDelay);

        try {
            mQueue.add(state);
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }

        mDelay = 1;
    }

    private MotionEvent sendMotionEvent(PointersState state, long millis, long when) {
        MotionEvent event = MotionEvent.obtain(millis, when, state.mAction,
                state.mNumPointers, state.mPointerProps, state.mPointerCoords,
                0, 0, 1, 1, 0, 0,
                InputDevice.SOURCE_TOUCHSCREEN, 0);

        try {
            if(mOutputStream == null) {
                Socket mSocket = new Socket("127.0.0.1", 7171);
                mSocket.setTcpNoDelay(true);

                OutputStream output = mSocket.getOutputStream();
                mOutputStream = new DataOutputStream(output);
            }

            byte [] evBytes = ParcelableUtil.marshall(event);
            mOutputStream.writeInt(evBytes.length);
            mOutputStream.write(evBytes);
        } catch (IOException e) {
            mOutputStream = null;
            e.printStackTrace();
        }

        return event;
    }

    public void touchDown(int pointer, PointF pos) {
        Pointer ptr = mPointers.get(pointer);

        if(ptr != null) {
            touchMove(pointer, pos);
            return;
        }

        ptr = new Pointer(pointer, pos);
        mPointers.put(pointer, ptr);

        int action = (mPointers.size() == 1) ? MotionEvent.ACTION_DOWN : MotionEvent.ACTION_POINTER_DOWN;
        postMotionEvent(pointer, action);
    }

    public void touchUp(int pointer) {
        Pointer ptr = mPointers.get(pointer);

        if(ptr == null) {
            Log.w(TAG, "Pointer not found: " + pointer);
            return;
        }

        int action = (mPointers.size() == 1) ? MotionEvent.ACTION_UP : MotionEvent.ACTION_POINTER_UP;
        postMotionEvent(pointer, action);

        mPointers.remove(pointer);
    }

    public void touchMove(int pointer, PointF pos) {
        Pointer ptr = mPointers.get(pointer);

        if(ptr == null) {
            Log.w(TAG, "Pointer not found: " + pointer);
            return;
        }

        ptr.mPos = pos;
        postMotionEvent(pointer, MotionEvent.ACTION_MOVE);
    }

    public void cancel() {
        if(mPointers.size() == 0)
            return;

        Pointer some = mPointers.entrySet().iterator().next().getValue();
        mDelay = 0;
        postMotionEvent(some.mId, MotionEvent.ACTION_CANCEL);

        mPointers.clear();
    }
}
