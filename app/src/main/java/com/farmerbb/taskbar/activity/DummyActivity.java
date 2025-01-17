/* Copyright 2016 Braden Farmer
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
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserManager;
import android.provider.Settings;
import android.view.View;

import com.farmerbb.taskbar.R;
import com.farmerbb.taskbar.util.ApplicationType;
import com.farmerbb.taskbar.util.FreeformHackHelper;
import com.farmerbb.taskbar.util.U;

public class DummyActivity extends Activity {

    boolean shouldFinish = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(new View(this));
    }

    @SuppressLint("RestrictedApi")
    @TargetApi(Build.VERSION_CODES.N)
    @Override
    protected void onResume() {
        super.onResume();
        if(shouldFinish)
            finish();
        else {
            shouldFinish = true;

            if(getIntent().hasExtra("uninstall")) {
                UserManager userManager = (UserManager) getSystemService(USER_SERVICE);

                Intent intent = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + getIntent().getStringExtra("uninstall")));
                intent.putExtra(Intent.EXTRA_USER, userManager.getUserForSerialNumber(getIntent().getLongExtra("user_id", 0)));

                try {
                    startActivity(intent);
                } catch (ActivityNotFoundException e) { /* Gracefully fail */ }
            } else if(getIntent().hasExtra("accessibility")) {
                AlertDialog.Builder builder = new AlertDialog.Builder(U.wrapContext(this));
                builder.setTitle(R.string.permission_dialog_title)
                        .setMessage(R.string.enable_accessibility)
                        .setNegativeButton(R.string.action_cancel, (dialog, which) -> new Handler().post(this::finish))
                        .setPositiveButton(R.string.action_activate, (dialog, which) -> {
                            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);

                            SharedPreferences pref = U.getSharedPreferences(this);
                            if(pref.getBoolean("disable_animations", false))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);

                            U.launchApp(this, () -> {
                                try {
                                    startActivity(intent, U.getActivityOptionsBundle(this, ApplicationType.APPLICATION));
                                    U.showToastLong(this, R.string.usage_stats_message);
                                } catch (ActivityNotFoundException e) {
                                    U.showToast(this, R.string.lock_device_not_supported);

                                    finish();
                                }
                            });
                        });

                AlertDialog dialog = builder.create();
                dialog.show();
                dialog.setCancelable(false);
            } else if(getIntent().hasExtra("start_freeform_hack")) {
                SharedPreferences pref = U.getSharedPreferences(this);
                if(U.hasFreeformSupport(this)
                        && pref.getBoolean("freeform_hack", false)
                        && !FreeformHackHelper.getInstance().isFreeformHackActive()) {
                    U.startFreeformHack(this, true);
                }

                finish();
            } else if(getIntent().hasExtra("show_permission_dialog"))
                U.showPermissionDialog(U.wrapContext(this), null, this::finish);
            else if(getIntent().hasExtra("show_recent_apps_dialog"))
                U.showRecentAppsDialog(U.wrapContext(this), null, this::finish);
            else
                finish();
        }
    }
}