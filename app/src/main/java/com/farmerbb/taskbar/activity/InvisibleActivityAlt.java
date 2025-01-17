/* Copyright 2017 Braden Farmer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.farmerbb.taskbar.activity;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.farmerbb.taskbar.R;
import com.farmerbb.taskbar.util.DisplayInfo;
import com.farmerbb.taskbar.util.FreeformHackHelper;
import com.farmerbb.taskbar.util.MenuHelper;
import com.farmerbb.taskbar.util.U;

public class InvisibleActivityAlt extends InvisibleActivity {

    @SuppressLint("HardwareIds")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean powerButtonWarning = getIntent().hasExtra("power_button_warning");

        DisplayInfo display = U.getDisplayInfo(this);

        setContentView(R.layout.incognito);

        LinearLayout layout = findViewById(R.id.incognitoLayout);
        layout.setLayoutParams(new FrameLayout.LayoutParams(display.width, display.height));

        if(!MenuHelper.getInstance().isStartMenuOpen() && !powerButtonWarning) finish();

        if(powerButtonWarning)
            new Handler().postDelayed(() -> {
                if(FreeformHackHelper.getInstance().isInFreeformWorkspace()) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle(R.string.power_button_warning_title)
                            .setMessage(R.string.power_button_warning_message)
                            .setPositiveButton(R.string.action_i_understand, (dialog, which) -> {
                                SharedPreferences pref = U.getSharedPreferences(this);
                                pref.edit().putString("power_button_warning",
                                        Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID)).apply();

                                finish();
                            });

                    AlertDialog dialog = builder.create();
                    dialog.show();
                    dialog.setCancelable(false);
                }
            }, 100);
    }
}
