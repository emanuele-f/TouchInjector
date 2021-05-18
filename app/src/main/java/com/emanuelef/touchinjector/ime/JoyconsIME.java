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

import android.inputmethodservice.InputMethodService;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

public class JoyconsIME extends InputMethodService {
    private static final String TAG = "JoyconsIME";
    public static int X_AXIS = 0x10;
    public static int Y_AXIS = 0x0f;

    private int mLeftJoycon = -1;
    private int mRightJoycon = -1;
    private IInputHandler mInputHandler;

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        super.onCreate();

        InputInjector.start();
        mInputHandler = new InputHandlerBS();

        reloadJoycons();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");

        InputInjector.end();

        Log.d(TAG, "onDestroy done");

        super.onDestroy();
    }

    private void reloadJoycons() {
        int[] deviceIds = InputDevice.getDeviceIds();

        mRightJoycon = -1;
        mLeftJoycon = -1;

        Log.d(TAG, "Reloading joycon IDs");

        for (int deviceId : deviceIds) {
            InputDevice dev = InputDevice.getDevice(deviceId);

            if(dev.getName().contains("Joy-Con (R)")) {
                Log.d(TAG, "Right joycon found: " + dev.getId());
                mRightJoycon = dev.getId();
            } else if(dev.getName().contains("Joy-Con (L)")) {
                Log.d(TAG, "Left joycon found: " + dev.getId());
                mLeftJoycon = dev.getId();
            }
        }
    }

    private boolean isJoyconDevice(int id) {
        return((id != -1) && ((id == mLeftJoycon) || (id == mRightJoycon)));
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(!isJoyconDevice(event.getDeviceId()))
            return super.onKeyDown(keyCode, event);

        onKey(event.getDeviceId(), keyCode, true);
        return true; // processed
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if(!isJoyconDevice(event.getDeviceId()))
            return super.onKeyDown(keyCode, event);

        onKey(event.getDeviceId(), keyCode, false);
        return true; // processed
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if(!isJoyconDevice(event.getDeviceId()) || (event.getAction() != MotionEvent.ACTION_MOVE))
            return super.onGenericMotionEvent(event);

        //for(int i=0; i<event.getHistorySize(); i++)
        //processAxisEvent(event.getHistoricalAxisValue(X_AXIS, i), event.getHistoricalAxisValue(Y_AXIS, i));

        processAxisEvent(event.getDeviceId(), event.getAxisValue(X_AXIS), event.getAxisValue(Y_AXIS));

        return true; // processed
    }

    private GamepadKey code2Key(boolean left, int keyCode) {
        switch(keyCode) {
            case 0:     return(left ? GamepadKey.K_LT : GamepadKey.K_RT);
            case 97:    return(left ? GamepadKey.K_DOWN : GamepadKey.K_Y);
            case 96:    return(left ? GamepadKey.K_LEFT : GamepadKey.K_B);
            case 98:    return(left ? GamepadKey.K_UP : GamepadKey.K_A);
            case 99:    return(left ? GamepadKey.K_RIGHT : GamepadKey.K_X);
            //case 100:   return(left ? GamepadKey.K_LSL : GamepadKey.K_RSL);
            //case 101:   return(left ? GamepadKey.K_LSR : GamepadKey.K_RSR);
            case 104:   return GamepadKey.K_SELECT;
            case 105:   return GamepadKey.K_START;
            //case 106:   return GamepadKey.K_SHARE;
            case 107:   return(left ? GamepadKey.K_LB : GamepadKey.K_RB);
            case 108:   return GamepadKey.K_RSTICK;
            case 109:   return GamepadKey.K_LSTICK;
            case 110:   return GamepadKey.K_HOME;
            default:
                return GamepadKey.K_UNKNOWN;
        }
    }

    private void onKey(int devId, int keyCode, boolean pressed) {
        boolean isLeft = (devId == mLeftJoycon);
        GamepadKey key = code2Key(isLeft, keyCode);

        Log.d(TAG, "onKey[" + (pressed ? "PRESS" : "RELEASE") + "] " + key.name() + " (" + keyCode + ")");

        if(key != GamepadKey.K_UNKNOWN)
            mInputHandler.onKey(key, pressed);
    }

    private void processAxisEvent(int devId, float x, float y) {
        boolean isLeft = (devId == mLeftJoycon);

        if(isLeft) {
            x = -x;
            y = -y;
        }

        Log.d(TAG, "onMotion[" + (isLeft ? "L" : "R") + "]: " + x + ", " + y);

        mInputHandler.onStickMove(isLeft, x, y);
    }
}
