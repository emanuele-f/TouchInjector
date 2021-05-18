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

public class InputHandlerBS implements IInputHandler {
    private boolean mIsSpecial;
    private final InputInjector mInjector;
    private final VirtualStick mLeftStick;
    private final VirtualStick mFireStick;
    private final VirtualStick mSpecialStick;
    private final VirtualStick mGadgetStick;
    private final int EMOJI_POINTER = 2;

    /* Pins coordinates */
    private static class Pins {
        static final PointF SELECTOR = new PointF(1870, 270);
        static final PointF TOP_LEFT = new PointF(1570, 270);
        static final PointF TOP_CENTER = new PointF(1720, 270);
        static final PointF BOTTOM_LEFT = new PointF(1570, 400);
        static final PointF BOTTOM_CENTER = new PointF(1720, 400);
        static final PointF BOTTOM_RIGHT = new PointF(1870, 400);
    }

    InputHandlerBS() {
        mInjector = InputInjector.getInstance();

        /* Analog sticks coordinates */
        mLeftStick = new VirtualStick(0,360, 800, 160);
        mFireStick = new VirtualStick(1,1780, 650, 160);
        mSpecialStick = new VirtualStick(1,1450, 770, 280);
        mGadgetStick = new VirtualStick(1,1618, 910, 160);
        // EMOJI_POINTER: 2

        mIsSpecial = false;
    }

    @Override
    public void onKey(GamepadKey key, boolean pressed) {
        if(key == GamepadKey.K_LT) {
            swapRightStick(pressed);
            return;
        }

        if(!pressed)
            return;

        switch (key) {
            case K_B:
            case K_RT:
                pressInPlace(getRightStick());
                break;
            case K_A:
                mSpecialStick.press();
                break;
            case K_Y:
                mGadgetStick.press();
                break;
            case K_HOME:
                reset();
                break;

            // Emoji
            case K_UP:
                pressPin(Pins.BOTTOM_RIGHT);
                break;
            case K_LEFT:
                pressPin(Pins.TOP_LEFT);
                break;
            case K_RIGHT:
                pressPin(Pins.TOP_CENTER);
                break;
            case K_DOWN:
                pressPin(Pins.BOTTOM_CENTER);
                break;
            case K_SELECT:
                pressPin(Pins.BOTTOM_LEFT);
                break;
        }
    }

    // Press a stick and then restore its previous position
    private void pressInPlace(VirtualStick stick) {
        boolean wasPressed = stick.isPressed();
        PointF oldPos = stick.getPosition();
        oldPos = new PointF(oldPos.x, oldPos.y);

        stick.press();

        if(wasPressed) {
            mInjector.addDelay(30);
            stick.moveTo(oldPos, 50);
        }
    }

    private void pressPin(PointF coords) {
        mInjector.touchDown(EMOJI_POINTER, Pins.SELECTOR);
        mInjector.addDelay(10);
        mInjector.touchUp(EMOJI_POINTER);

        mInjector.addDelay(50);

        mInjector.touchDown(EMOJI_POINTER, coords);
        mInjector.addDelay(10);
        mInjector.touchUp(EMOJI_POINTER);
    }

    @Override
    public void onStickMove(boolean isLeftJoycon, float x, float y) {
        VirtualStick stick = isLeftJoycon ? mLeftStick : getRightStick();

        if((x != 0) || (y != 0)) {
            PointF toPos = new PointF(x, y);
            stick.moveTo(toPos, 0);
        } else if(stick.isPressed()) {
            stick.moveToCenter(50);
            mInjector.addDelay(20);
            stick.release();
            mInjector.addDelay(20);
        }
    }

    private VirtualStick getRightStick() {
        return(mIsSpecial ? mSpecialStick : mFireStick);
    }

    private void swapRightStick(boolean isSpecial) {
        if(mIsSpecial == isSpecial)
            return;

        VirtualStick mOldStick = getRightStick();
        mIsSpecial = isSpecial;

        //PointF oldPos = mOldStick.getPosition();
        //oldPos = new PointF(oldPos.x, oldPos.y);

        if(mOldStick.isPressed()) {
            mOldStick.moveToCenter(50);
            mInjector.addDelay(50);
            mOldStick.release();

            // TODO: not working properly due to missing interpolation
            //mInjector.addDelay(10);
            //getRightStick().moveTo(oldPos, 50);
        }
    }

    @Override
    public void reset() {
        mLeftStick.release();
        mFireStick.release();
        mSpecialStick.release();
        mGadgetStick.release();

        mInjector.cancel();
    }
}
