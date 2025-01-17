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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.os.UserManager;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;

import com.farmerbb.taskbar.R;
import com.farmerbb.taskbar.activity.dark.SelectAppActivityDark;
import com.farmerbb.taskbar.adapter.AppListAdapter;
import com.farmerbb.taskbar.fragment.SelectAppFragment;
import com.farmerbb.taskbar.util.Blacklist;
import com.farmerbb.taskbar.util.BlacklistEntry;
import com.farmerbb.taskbar.util.TopApps;
import com.farmerbb.taskbar.util.U;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SelectAppActivity extends AppCompatActivity {

    private AppListGenerator appListGenerator;
    private ProgressBar progressBar;
    private AppListAdapter hiddenAdapter;
    private AppListAdapter topAppsAdapter;

    private class SelectAppPagerAdapter extends FragmentPagerAdapter {

        SelectAppPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            return SelectAppFragment.newInstance(position);
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch(position) {
                case U.HIDDEN:
                    return getString(R.string.blacklist_dialog_title);
                case U.TOP_APPS:
                    return getString(R.string.top_apps_dialog_title);
            }

            return null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean noShadow = getIntent().hasExtra("no_shadow");

        if(savedInstanceState == null) {
            setContentView(R.layout.configure_start_menu);
            setFinishOnTouchOutside(false);
            setTitle(R.string.start_menu_apps);

            if(noShadow) {
                WindowManager.LayoutParams params = getWindow().getAttributes();
                params.dimAmount = 0;
                getWindow().setAttributes(params);

                if(U.isChromeOs(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1)
                    getWindow().setElevation(0);
            }

            progressBar = findViewById(R.id.progress_bar);
            appListGenerator = new AppListGenerator();
            appListGenerator.execute();
        } else {
            // Workaround for ViewPager disappearing on config change
            finish();

            if(!noShadow)
                new Handler().post(() -> {
                    Intent intent = null;
                    SharedPreferences pref3 = U.getSharedPreferences(this);

                    switch(pref3.getString("theme", "light")) {
                        case "light":
                            intent = new Intent(this, SelectAppActivity.class);
                            break;
                        case "dark":
                            intent = new Intent(this, SelectAppActivityDark.class);
                            break;
                    }

                    startActivity(intent);
                });
        }
    }

    @Override
    public void finish() {
        if(appListGenerator != null && appListGenerator.getStatus() == AsyncTask.Status.RUNNING)
            appListGenerator.cancel(true);

        super.finish();
    }

    public AppListAdapter getAppListAdapter(int type) {
        switch(type) {
            case U.HIDDEN:
                return hiddenAdapter;
            case U.TOP_APPS:
                return topAppsAdapter;
        }

        return null;
    }

    private final class AppListGenerator extends AsyncTask<Void, Void, AppListAdapter[]> {
        @SuppressWarnings("Convert2streamapi")
        @Override
        protected AppListAdapter[] doInBackground(Void... params) {
            UserManager userManager = (UserManager) getSystemService(Context.USER_SERVICE);
            LauncherApps launcherApps = (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);

            final List<UserHandle> userHandles = userManager.getUserProfiles();
            final List<LauncherActivityInfo> list = new ArrayList<>();

            for(UserHandle handle : userHandles) {
                list.addAll(launcherApps.getActivityList(null, handle));
            }

            // Remove any uninstalled apps from the blacklist and top apps
            Blacklist blacklist = Blacklist.getInstance(SelectAppActivity.this);
            TopApps topApps = TopApps.getInstance(SelectAppActivity.this);
            List<String> blacklistedApps = new ArrayList<>();
            List<String> topAppsList = new ArrayList<>();
            List<String> installedApps = new ArrayList<>();

            for(BlacklistEntry entry : blacklist.getBlockedApps()) {
                blacklistedApps.add(entry.getPackageName());
            }

            for(BlacklistEntry entry : topApps.getTopApps()) {
                topAppsList.add(entry.getPackageName());
            }

            for(LauncherActivityInfo appInfo : list) {
                installedApps.add(appInfo.getApplicationInfo().packageName + "/" + appInfo.getName()
                        + ":" + userManager.getSerialNumberForUser(appInfo.getUser()));
                installedApps.add(appInfo.getApplicationInfo().packageName + "/" + appInfo.getName());
                installedApps.add(appInfo.getName());
            }

            for(String packageName : blacklistedApps) {
                if(!installedApps.contains(packageName))
                    blacklist.removeBlockedApp(SelectAppActivity.this, packageName);
            }

            for(String packageName : topAppsList) {
                if(!installedApps.contains(packageName))
                    topApps.removeTopApp(SelectAppActivity.this, packageName);
            }

            Collections.sort(list, (ai1, ai2) -> {
                String label1;
                String label2;

                try {
                    label1 = ai1.getLabel().toString();
                    label2 = ai2.getLabel().toString();
                } catch (OutOfMemoryError e) {
                    System.gc();

                    label1 = ai1.getApplicationInfo().packageName;
                    label2 = ai2.getApplicationInfo().packageName;
                }

                return Collator.getInstance().compare(label1, label2);
            });

            final List<BlacklistEntry> entries = new ArrayList<>();
            for(LauncherActivityInfo appInfo : list) {
                String label;

                try {
                    label = appInfo.getLabel().toString();
                } catch (OutOfMemoryError e) {
                    System.gc();

                    label = appInfo.getApplicationInfo().packageName;
                }

                entries.add(new BlacklistEntry(
                        appInfo.getApplicationInfo().packageName + "/" + appInfo.getName()
                                + ":" + userManager.getSerialNumberForUser(appInfo.getUser()),
                        label));
            }

            return new AppListAdapter[] {
                    new AppListAdapter(SelectAppActivity.this, R.layout.row_blacklist, entries, U.HIDDEN),
                    new AppListAdapter(SelectAppActivity.this, R.layout.row_blacklist, entries, U.TOP_APPS)
            };
        }

        @Override
        protected void onPostExecute(AppListAdapter[] adapters) {
            hiddenAdapter = adapters[U.HIDDEN];
            topAppsAdapter = adapters[U.TOP_APPS];

            SelectAppPagerAdapter pagerAdapter = new SelectAppPagerAdapter(getSupportFragmentManager());
            ViewPager viewPager = findViewById(R.id.pager);
            viewPager.setAdapter(pagerAdapter);

            TabLayout tabLayout = findViewById(R.id.sliding_tabs);
            tabLayout.setupWithViewPager(viewPager);

            findViewById(R.id.configure_start_menu_layout).setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.GONE);
            setFinishOnTouchOutside(true);
        }
    }
}