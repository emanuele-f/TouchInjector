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

import android.annotation.SuppressLint;
import android.hardware.input.InputManager;
import android.view.InputEvent;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;

public class Main {
    private static class EventInjector {
        // See hardware/input/InputManager.java
        private static final int INJECT_INPUT_EVENT_MODE_WAIT_FOR_RESULT = 1; // async
        private static final int INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH = 2; // sync

        Method injectInputEvent;
        InputManager im;

        @SuppressLint("DiscouragedPrivateApi")
        EventInjector() throws Exception {
            String methodName = "getInstance";
            im = (InputManager) InputManager.class.getDeclaredMethod(methodName)
                    .invoke(null);

            methodName = "injectInputEvent";
            injectInputEvent = InputManager.class.getMethod(methodName, InputEvent.class, Integer.TYPE);
        }

        void injectEvent(InputEvent ev) {
            //Log_d("Injecting event: " + ev.toString());

            try {
                injectInputEvent.invoke(im, ev, INJECT_INPUT_EVENT_MODE_WAIT_FOR_RESULT);
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
    }

    private static void Log_d(String msg) {
        System.out.println(msg);
    }

    // run via adb with:
    // CLASSPATH=`pm path com.emanuelef.touchinjector` app_process /data/local/tmp com.emanuelef.touchinjector.Main
    public static void main(String[] args) {
        EventInjector injector;

        try {
            injector = new EventInjector();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
            return;
        }

        ServerSocket mSocket;
        Socket mClient;

        Log_d("Waiting for the client app to connect...");

        try {
            mSocket = new ServerSocket(7171);
            mClient = mSocket.accept();

            Log_d("Client accepted: " + mClient);

            InputStream input = mClient.getInputStream();
            DataInputStream inputStream = new DataInputStream(input);

            while(true) {
                int size = inputStream.readInt();
                byte[] evBytes = new byte[size];

                inputStream.readFully(evBytes);

                InputEvent obj = ParcelableUtil.unmarshall(evBytes, InputEvent.CREATOR);

                injector.injectEvent(obj);
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new IllegalStateException();
        }
    }
}
