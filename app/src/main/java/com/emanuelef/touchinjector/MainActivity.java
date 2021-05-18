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

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import com.emanuelef.touchinjector.ime.SocketIME;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private Button mToggleService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mToggleService = findViewById(R.id.toggle_service);
        mToggleService.setOnClickListener(v -> {
            SocketIME service = SocketIME.getInstance();

            if(service != null) {
                Log.d(TAG, "Stopping service...");
                service.stop();
                mToggleService.setText(R.string.start_service);
            } else {
                Log.d(TAG, "Starting service...");
                Intent intent = new Intent(this, SocketIME.class);
                startService(intent);
                mToggleService.setText(R.string.stop_service);
            }
        });
    }

    @Override
    protected void onResume() {
        if(SocketIME.getInstance() == null)
            mToggleService.setText(R.string.start_service);
        else
            mToggleService.setText(R.string.stop_service);

        super.onResume();
    }
}