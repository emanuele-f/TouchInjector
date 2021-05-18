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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import com.emanuelef.touchinjector.MainActivity;
import com.emanuelef.touchinjector.R;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class SocketIME extends Service {
    private static final String TAG = "SocketIME";
    private static final String MY_NOTIFY_CHAN = "SocketIME";
    private static final int ONGOING_NOTIFICATION_ID = 1;

    private IInputHandler mInputHandler;
    private ServerSocket mSocket;
    private Socket mClient;
    private Thread mThread;
    private boolean mRunning = true;
    private static SocketIME mInstance = null;

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        super.onCreate();

        InputInjector.start();
        mInputHandler = new InputHandlerBS();
        mClient = null;

        try {
            mSocket = new ServerSocket(7070);
        } catch (IOException e) {
            e.printStackTrace();
            throw new IllegalStateException();
        }

        startForeground(ONGOING_NOTIFICATION_ID, setupNotification());

        mThread = new Thread(this::runInBackground);
        mThread.start();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");

        InputInjector.end();

        Log.d(TAG, "onDestroy done");

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    public static SocketIME getInstance() {
        return mInstance;
    }

    private Notification setupNotification() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            NotificationChannel chan = new NotificationChannel(MY_NOTIFY_CHAN,
                    MY_NOTIFY_CHAN, NotificationManager.IMPORTANCE_LOW); // low: no sound
            nm.createNotificationChannel(chan);
        }

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(this, 0, notificationIntent, 0);

        Notification notification =
                new NotificationCompat.Builder(this, MY_NOTIFY_CHAN)
                        .setContentTitle("AltInputService")
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentIntent(pendingIntent)
                        .build();

        return notification;
    }

    private void runInBackground() {
        mInstance = this;

        while(mRunning && !Thread.currentThread().isInterrupted()) {
            try {
                Log.d(TAG, "Waiting for a client...");
                mClient = mSocket.accept();

                Log.d(TAG, "Client accepted: " + mClient);

                try {
                    InputStream input = mClient.getInputStream();
                    InputStreamReader reader = new InputStreamReader(input);
                    Scanner scanner = new Scanner(reader);

                    mInputHandler.reset();

                    while (mRunning && !Thread.currentThread().isInterrupted() && scanner.hasNext()) {
                        String msg = scanner.next();
                        handleCommand(msg);
                    }
                } finally {
                    try {
                        mClient.close();
                        mClient = null;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    mInputHandler.reset();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        stopForeground(true /* remove notification */);
        stopSelf();
        mInstance = null;
    }

    public void stop() {
        if(!mRunning)
            return;

        mRunning = false;

        try {
            if(mSocket != null) {
                mSocket.close();
            }
            if (mClient != null) {
                mClient.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        while((mThread != null) && (mThread.isAlive())) {
            try {
                Log.d(TAG, "Joining thread...");
                mThread.join();
            } catch (InterruptedException e) {
                Log.e(TAG, "Joining thread failed");
            }
        }

        mThread = null;
    }

    private void handleCommand(String cmd) {
        Log.d(TAG, "handleCommand: " + cmd);

        String[] parts = cmd.split("\\|");

        if(parts.length < 1)
            return;

        String ev = parts[0];

        if((parts.length == 2) && (ev.equals("K_DOWN") || ev.equals("K_UP"))) {
            boolean pressed = ev.equals("K_DOWN");
            int val = Integer.parseInt(parts[1]);

            if((val >= 0) && (val < GamepadKey.values().length)) {
                GamepadKey key = GamepadKey.values()[val];
                Log.d(TAG, "onKey[" + (pressed ? "PRESS" : "RELEASE") + "] " + key.name());

                if(key != GamepadKey.K_UNKNOWN)
                    mInputHandler.onKey(key, pressed);
            }
        } else if((parts.length == 3) && (ev.equals("R_STICK") || ev.equals("L_STICK"))) {
            boolean isLeft = ev.equals("L_STICK");
            float x = Float.parseFloat(parts[1]);
            float y = Float.parseFloat(parts[2]);

            mInputHandler.onStickMove(isLeft, x, y);
        } else {
            Log.d(TAG, "Invalid command: " + cmd);
        }
    }
}
