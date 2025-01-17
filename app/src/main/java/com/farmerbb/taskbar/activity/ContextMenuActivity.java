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

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.UserManager;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import com.farmerbb.taskbar.BuildConfig;
import com.farmerbb.taskbar.R;
import com.farmerbb.taskbar.activity.dark.DesktopIconSelectAppActivityDark;
import com.farmerbb.taskbar.activity.dark.SelectAppActivityDark;
import com.farmerbb.taskbar.util.AppEntry;
import com.farmerbb.taskbar.util.ApplicationType;
import com.farmerbb.taskbar.util.DesktopIconInfo;
import com.farmerbb.taskbar.util.DisplayInfo;
import com.farmerbb.taskbar.util.FeatureFlags;
import com.farmerbb.taskbar.util.FreeformHackHelper;
import com.farmerbb.taskbar.util.IconCache;
import com.farmerbb.taskbar.util.LauncherHelper;
import com.farmerbb.taskbar.util.MenuHelper;
import com.farmerbb.taskbar.util.PinnedBlockedApps;
import com.farmerbb.taskbar.util.SavedWindowSizes;
import com.farmerbb.taskbar.util.U;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.List;

public class ContextMenuActivity extends PreferenceActivity implements Preference.OnPreferenceClickListener {

    private Bundle args;

    String packageName;
    String componentName;
    String appName;
    long userId = 0;

    DesktopIconInfo desktopIcon;

    boolean showStartMenu = false;
    boolean shouldHideTaskbar = false;
    boolean isStartButton = false;
    boolean isOverflowMenu = false;
    boolean isNonAppMenu = false;
    boolean secondaryMenu = false;
    boolean dashboardOrStartMenuAppearing = false;
    boolean contextMenuFix = false;

    List<ShortcutInfo> shortcuts;

    private BroadcastReceiver dashboardOrStartMenuAppearingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            dashboardOrStartMenuAppearing = true;
            finish();
        }
    };

    private BroadcastReceiver finishReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            finish();
        }
    };


    @SuppressLint("RtlHardcoded")
    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("com.farmerbb.taskbar.CONTEXT_MENU_APPEARING"));
        MenuHelper.getInstance().setContextMenuOpen(true);

        args = getIntent().getBundleExtra("args");

        desktopIcon = (DesktopIconInfo) args.getSerializable("desktop_icon");

        isNonAppMenu = !args.containsKey("package_name") && !args.containsKey("app_name");
        showStartMenu = args.getBoolean("launched_from_start_menu", false);
        isStartButton = isNonAppMenu && args.getBoolean("is_start_button", false);
        isOverflowMenu = isNonAppMenu && args.getBoolean("is_overflow_menu", false);
        contextMenuFix = args.containsKey("context_menu_fix");

        // Determine where to position the dialog on screen
        WindowManager.LayoutParams params = getWindow().getAttributes();
        DisplayInfo display = U.getDisplayInfo(this);

        int statusBarHeight = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if(resourceId > 0)
            statusBarHeight = getResources().getDimensionPixelSize(resourceId);

        if(showStartMenu || desktopIcon != null) {
            int x = args.getInt("x", 0);
            int y = args.getInt("y", 0);
            int offset = getResources().getDimensionPixelSize(isOverflowMenu ? R.dimen.context_menu_offset_overflow : R.dimen.context_menu_offset);

            switch(U.getTaskbarPosition(this)) {
                case "bottom_left":
                case "bottom_vertical_left":
                    params.gravity = Gravity.BOTTOM | Gravity.LEFT;
                    params.x = x;
                    params.y = display.height - y - offset;
                    break;
                case "bottom_right":
                case "bottom_vertical_right":
                    params.gravity = Gravity.BOTTOM | Gravity.LEFT;
                    params.x = x - getResources().getDimensionPixelSize(R.dimen.context_menu_width) + offset + offset;
                    params.y = display.height - y - offset;
                    break;
                case "top_left":
                case "top_vertical_left":
                    params.gravity = Gravity.TOP | Gravity.LEFT;
                    params.x = x;
                    params.y = y - offset + statusBarHeight;
                    break;
                case "top_right":
                case "top_vertical_right":
                    params.gravity = Gravity.TOP | Gravity.LEFT;
                    params.x = x - getResources().getDimensionPixelSize(R.dimen.context_menu_width) + offset + offset;
                    params.y = y - offset + statusBarHeight;
                    break;
            }
        } else {
            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("com.farmerbb.taskbar.HIDE_START_MENU"));

            int x = args.getInt("x", display.width);
            int y = args.getInt("y", display.height);
            int offset = getResources().getDimensionPixelSize(R.dimen.icon_size);

            switch(U.getTaskbarPosition(this)) {
                case "bottom_left":
                    params.gravity = Gravity.BOTTOM | Gravity.LEFT;
                    params.x = isStartButton ? 0 : x;
                    params.y = offset;
                    break;
                case "bottom_vertical_left":
                    params.gravity = Gravity.BOTTOM | Gravity.LEFT;
                    params.x = offset;
                    params.y = display.height - y - (isStartButton ? 0 : offset);
                    break;
                case "bottom_right":
                    params.gravity = Gravity.BOTTOM | Gravity.RIGHT;
                    params.x = display.width - x;
                    params.y = offset;
                    break;
                case "bottom_vertical_right":
                    params.gravity = Gravity.BOTTOM | Gravity.RIGHT;
                    params.x = offset;
                    params.y = display.height - y - (isStartButton ? 0 : offset);
                    break;
                case "top_left":
                    params.gravity = Gravity.TOP | Gravity.LEFT;
                    params.x = isStartButton ? 0 : x;
                    params.y = offset;
                    break;
                case "top_vertical_left":
                    params.gravity = Gravity.TOP | Gravity.LEFT;
                    params.x = offset;
                    params.y = isStartButton ? 0 : y - statusBarHeight;
                    break;
                case "top_right":
                    params.gravity = Gravity.TOP | Gravity.RIGHT;
                    params.x = display.width - x;
                    params.y = offset;
                    break;
                case "top_vertical_right":
                    params.gravity = Gravity.TOP | Gravity.RIGHT;
                    params.x = offset;
                    params.y = isStartButton ? 0 : y - statusBarHeight;
                    break;
            }

            if(!U.getTaskbarPosition(this).contains("vertical") && (params.x > display.width / 2))
                params.x = params.x - getResources().getDimensionPixelSize(R.dimen.context_menu_width) + offset;
        }

        params.width = getResources().getDimensionPixelSize(R.dimen.context_menu_width);
        params.dimAmount = 0;

        if(U.isChromeOs(this) && U.getTaskbarPosition(this).contains("bottom")) {
            SharedPreferences pref = U.getSharedPreferences(this);

            if(pref.getBoolean("chrome_os_context_menu_fix", true)
                    && !pref.getBoolean("has_caption", false))
                params.y = params.y - getResources().getDimensionPixelSize(R.dimen.caption_offset);
        }

        getWindow().setAttributes(params);

        if(U.isChromeOs(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1)
            getWindow().setElevation(0);

        View view = findViewById(android.R.id.list);
        if(view != null) view.setPadding(0, 0, 0, 0);

        generateMenu();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("com.farmerbb.taskbar.START_MENU_APPEARING");
        intentFilter.addAction("com.farmerbb.taskbar.DASHBOARD_APPEARING");

        LocalBroadcastManager.getInstance(this).registerReceiver(dashboardOrStartMenuAppearingReceiver, intentFilter);
        LocalBroadcastManager.getInstance(this).registerReceiver(finishReceiver, new IntentFilter("com.farmerbb.taskbar.HIDE_CONTEXT_MENU"));
    }

    @SuppressWarnings("deprecation")
    private void generateMenu() {
        SharedPreferences pref = U.getSharedPreferences(this);

        if(isStartButton) {
            addPreferencesFromResource(R.xml.pref_context_menu_open_settings);
            findPreference("open_taskbar_settings").setOnPreferenceClickListener(this);
            findPreference("start_menu_apps").setOnPreferenceClickListener(this);

            if(pref.getBoolean("freeform_hack", false)
                    && !FeatureFlags.desktopIcons(this)
                    && ((U.launcherIsDefault(this)
                    && !U.isOverridingFreeformHack(this)
                    && FreeformHackHelper.getInstance().isInFreeformWorkspace())
                    || (U.isOverridingFreeformHack(this)
                    && LauncherHelper.getInstance().isOnHomeScreen()))) {
                addPreferencesFromResource(R.xml.pref_context_menu_change_wallpaper);
                findPreference("change_wallpaper").setOnPreferenceClickListener(this);
            }

            if(!args.getBoolean("dont_show_quit", false)) {
                addPreferencesFromResource(R.xml.pref_context_menu_quit);
                findPreference("quit_taskbar").setOnPreferenceClickListener(this);
            }
        } else if(isOverflowMenu) {
            if(getResources().getConfiguration().screenWidthDp >= 600
                    && Build.VERSION.SDK_INT <= Build.VERSION_CODES.M)
                setTitle(R.string.tools);
            else {
                addPreferencesFromResource(R.xml.pref_context_menu_header);
                findPreference("header").setTitle(R.string.tools);
            }

            addPreferencesFromResource(R.xml.pref_context_menu_overflow);
            findPreference("volume").setOnPreferenceClickListener(this);
            findPreference("system_settings").setOnPreferenceClickListener(this);
            findPreference("power_menu").setOnPreferenceClickListener(this);

            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                findPreference("file_manager").setOnPreferenceClickListener(this);
            else
                getPreferenceScreen().removePreference(findPreference("file_manager"));
        } else if(desktopIcon != null && isNonAppMenu) {
            addPreferencesFromResource(R.xml.pref_context_menu_desktop_icons);
            findPreference("add_icon_to_desktop").setOnPreferenceClickListener(this);
            findPreference("arrange_icons").setOnPreferenceClickListener(this);
            findPreference("sort_by_name").setOnPreferenceClickListener(this);
            findPreference("change_wallpaper").setOnPreferenceClickListener(this);
        } else {
            appName = args.getString("app_name");
            packageName = args.getString("package_name");
            componentName = args.getString("component_name");
            userId = args.getLong("user_id", 0);

            if(getResources().getConfiguration().screenWidthDp >= 600
                    && Build.VERSION.SDK_INT <= Build.VERSION_CODES.M)
                setTitle(appName);
            else {
                addPreferencesFromResource(R.xml.pref_context_menu_header);
                findPreference("header").setTitle(appName);
            }

            if(U.hasFreeformSupport(this)
                    && pref.getBoolean("freeform_hack", false)
                    && !U.isGame(this, packageName)) {
                addPreferencesFromResource(R.xml.pref_context_menu_show_window_sizes);
                findPreference("show_window_sizes").setOnPreferenceClickListener(this);
            }

            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                int shortcutCount = getLauncherShortcuts();

                if(shortcutCount > 1) {
                    addPreferencesFromResource(R.xml.pref_context_menu_shortcuts);
                    findPreference("app_shortcuts").setOnPreferenceClickListener(this);
                } else if(shortcutCount == 1)
                    generateShortcuts();
            }

            final PackageManager pm = getPackageManager();
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            ResolveInfo defaultLauncher = pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY);

            if(desktopIcon != null) {
                addPreferencesFromResource(R.xml.pref_context_menu_remove_desktop_icon);
                findPreference("remove_desktop_icon").setOnPreferenceClickListener(this);
            } else if(!packageName.contains(BuildConfig.BASE_APPLICATION_ID)
                    && !packageName.equals(defaultLauncher.activityInfo.packageName)) {
                PinnedBlockedApps pba = PinnedBlockedApps.getInstance(this);

                if(pba.isPinned(componentName)) {
                    addPreferencesFromResource(R.xml.pref_context_menu_pin);
                    findPreference("pin_app").setOnPreferenceClickListener(this);
                    findPreference("pin_app").setTitle(R.string.unpin_app);
                } else if(pba.isBlocked(componentName)) {
                    addPreferencesFromResource(R.xml.pref_context_menu_block);
                    findPreference("block_app").setOnPreferenceClickListener(this);
                    findPreference("block_app").setTitle(R.string.unblock_app);
                } else {
                    final int MAX_NUM_OF_COLUMNS = U.getMaxNumOfEntries(this);

                    if(pba.getPinnedApps().size() < MAX_NUM_OF_COLUMNS) {
                        addPreferencesFromResource(R.xml.pref_context_menu_pin);
                        findPreference("pin_app").setOnPreferenceClickListener(this);
                        findPreference("pin_app").setTitle(R.string.pin_app);
                    }

                    addPreferencesFromResource(R.xml.pref_context_menu_block);
                    findPreference("block_app").setOnPreferenceClickListener(this);
                    findPreference("block_app").setTitle(R.string.block_app);
                }
            }

            addPreferencesFromResource(R.xml.pref_context_menu);

            findPreference("app_info").setOnPreferenceClickListener(this);
            findPreference("uninstall").setOnPreferenceClickListener(this);
        }
    }

    @SuppressWarnings("deprecation")
    private void generateShortcuts() {
        addPreferencesFromResource(R.xml.pref_context_menu_shortcut_list);
        switch(shortcuts.size()) {
            case 5:
                findPreference("shortcut_5").setTitle(getShortcutTitle(shortcuts.get(4)));
                findPreference("shortcut_5").setOnPreferenceClickListener(this);
            case 4:
                findPreference("shortcut_4").setTitle(getShortcutTitle(shortcuts.get(3)));
                findPreference("shortcut_4").setOnPreferenceClickListener(this);
            case 3:
                findPreference("shortcut_3").setTitle(getShortcutTitle(shortcuts.get(2)));
                findPreference("shortcut_3").setOnPreferenceClickListener(this);
            case 2:
                findPreference("shortcut_2").setTitle(getShortcutTitle(shortcuts.get(1)));
                findPreference("shortcut_2").setOnPreferenceClickListener(this);
            case 1:
                findPreference("shortcut_1").setTitle(getShortcutTitle(shortcuts.get(0)));
                findPreference("shortcut_1").setOnPreferenceClickListener(this);
                break;
        }

        switch(shortcuts.size()) {
            case 1:
                getPreferenceScreen().removePreference(findPreference("shortcut_2"));
            case 2:
                getPreferenceScreen().removePreference(findPreference("shortcut_3"));
            case 3:
                getPreferenceScreen().removePreference(findPreference("shortcut_4"));
            case 4:
                getPreferenceScreen().removePreference(findPreference("shortcut_5"));
                break;
        }
    }

    @SuppressWarnings("deprecation")
    private void generateWindowSizes() {
        getPreferenceScreen().removeAll();

        addPreferencesFromResource(R.xml.pref_context_menu_window_size_list);
        findPreference("window_size_standard").setOnPreferenceClickListener(this);
        findPreference("window_size_large").setOnPreferenceClickListener(this);
        findPreference("window_size_fullscreen").setOnPreferenceClickListener(this);
        findPreference("window_size_half_left").setOnPreferenceClickListener(this);
        findPreference("window_size_half_right").setOnPreferenceClickListener(this);
        findPreference("window_size_phone_size").setOnPreferenceClickListener(this);

        String windowSizePref = SavedWindowSizes.getInstance(this).getWindowSize(this, packageName);
        CharSequence title = findPreference("window_size_" + windowSizePref).getTitle();
        findPreference("window_size_" + windowSizePref).setTitle('\u2713' + " " + title);
    }

    @SuppressWarnings("deprecation")
    @TargetApi(Build.VERSION_CODES.N_MR1)
    @Override
    public boolean onPreferenceClick(Preference p) {
        UserManager userManager = (UserManager) getSystemService(USER_SERVICE);
        LauncherApps launcherApps = (LauncherApps) getSystemService(LAUNCHER_APPS_SERVICE);
        boolean appIsValid = isStartButton || isOverflowMenu ||
                !launcherApps.getActivityList(args.getString("package_name"),
                        userManager.getUserForSerialNumber(userId)).isEmpty();
        secondaryMenu = false;

        if(appIsValid) switch(p.getKey()) {
            case "app_info":
                U.launchApp(this, () ->
                        launcherApps.startAppDetailsActivity(
                                ComponentName.unflattenFromString(componentName),
                                userManager.getUserForSerialNumber(userId),
                                null,
                                U.getActivityOptionsBundle(this, ApplicationType.APPLICATION)));

                showStartMenu = false;
                shouldHideTaskbar = true;
                contextMenuFix = false;
                break;
            case "uninstall":
                if(U.hasFreeformSupport(this) && isInMultiWindowMode() && !U.isChromeOs(this)) {
                    Intent intent2 = new Intent(this, DummyActivity.class);
                    intent2.putExtra("uninstall", packageName);
                    intent2.putExtra("user_id", userId);

                    try {
                        startActivity(intent2);
                    } catch (IllegalArgumentException e) { /* Gracefully fail */ }
                } else {
                    Intent intent2 = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + packageName));
                    intent2.putExtra(Intent.EXTRA_USER, userManager.getUserForSerialNumber(userId));

                    try {
                        startActivity(intent2);
                    } catch (ActivityNotFoundException | IllegalArgumentException e) { /* Gracefully fail */ }
                }

                showStartMenu = false;
                shouldHideTaskbar = true;
                contextMenuFix = false;
                break;
            case "open_taskbar_settings":
                U.launchApp(this, () -> {
                    Intent intent2 = new Intent(this, MainActivity.class);
                    intent2.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

                    try {
                        startActivity(intent2, U.getActivityOptionsBundle(this, ApplicationType.APPLICATION));
                    } catch (IllegalArgumentException e) { /* Gracefully fail */ }
                });

                showStartMenu = false;
                shouldHideTaskbar = true;
                contextMenuFix = false;
                break;
            case "quit_taskbar":
                Intent quitIntent = new Intent("com.farmerbb.taskbar.QUIT");
                quitIntent.setPackage(BuildConfig.APPLICATION_ID);
                sendBroadcast(quitIntent);

                showStartMenu = false;
                shouldHideTaskbar = true;
                contextMenuFix = false;
                break;
            case "pin_app":
                PinnedBlockedApps pba = PinnedBlockedApps.getInstance(this);
                if(pba.isPinned(componentName))
                    pba.removePinnedApp(this, componentName);
                else {
                    Intent intent = new Intent();
                    intent.setComponent(ComponentName.unflattenFromString(componentName));

                    LauncherActivityInfo appInfo = launcherApps.resolveActivity(intent, userManager.getUserForSerialNumber(userId));
                    if(appInfo != null) {
                        AppEntry newEntry = new AppEntry(
                                packageName,
                                componentName,
                                appName,
                                IconCache.getInstance(this).getIcon(this, appInfo),
                                true);

                        newEntry.setUserId(userId);
                        pba.addPinnedApp(this, newEntry);
                    }
                }
                break;
            case "block_app":
                PinnedBlockedApps pba2 = PinnedBlockedApps.getInstance(this);
                if(pba2.isBlocked(componentName))
                    pba2.removeBlockedApp(this, componentName);
                else {
                    pba2.addBlockedApp(this, new AppEntry(
                            packageName,
                            componentName,
                            appName,
                            null,
                            false));
                }
                break;
            case "show_window_sizes":
                generateWindowSizes();

                if(U.hasBrokenSetLaunchBoundsApi())
                    U.showToastLong(this, R.string.window_sizes_not_available);

                getListView().setOnItemLongClickListener((parent, view, position, id) -> {
                    String[] windowSizes = { "standard", "large", "fullscreen", "half_left", "half_right", "phone_size" };

                    SavedWindowSizes.getInstance(this).setWindowSize(this, packageName, windowSizes[position]);

                    generateWindowSizes();
                    return true;
                });

                secondaryMenu = true;
                break;
            case "window_size_standard":
            case "window_size_large":
            case "window_size_fullscreen":
            case "window_size_half_left":
            case "window_size_half_right":
            case "window_size_phone_size":
                String windowSize = p.getKey().replace("window_size_", "");

                SharedPreferences pref2 = U.getSharedPreferences(this);
                if(pref2.getBoolean("save_window_sizes", true)) {
                    SavedWindowSizes.getInstance(this).setWindowSize(this, packageName, windowSize);
                }

                U.launchApp(getApplicationContext(), packageName, componentName, userId, windowSize, false, true);

                if(U.hasBrokenSetLaunchBoundsApi())
                    U.cancelToast();

                showStartMenu = false;
                shouldHideTaskbar = true;
                contextMenuFix = false;
                break;
            case "app_shortcuts":
                getPreferenceScreen().removeAll();
                generateShortcuts();

                secondaryMenu = true;
                break;
            case "shortcut_1":
            case "shortcut_2":
            case "shortcut_3":
            case "shortcut_4":
            case "shortcut_5":
                U.startShortcut(getApplicationContext(), packageName, componentName, shortcuts.get(Integer.parseInt(p.getKey().replace("shortcut_", "")) - 1));

                showStartMenu = false;
                shouldHideTaskbar = true;
                contextMenuFix = false;
                break;
            case "start_menu_apps":
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

                if(U.hasFreeformSupport(this)
                        && pref3.getBoolean("freeform_hack", false)
                        && intent != null && isInMultiWindowMode()) {
                    intent.putExtra("no_shadow", true);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);

                    U.startActivityMaximized(getApplicationContext(), intent);
                } else {
                    try {
                        startActivity(intent);
                    } catch (IllegalArgumentException e) { /* Gracefully fail */ }
                }

                showStartMenu = false;
                shouldHideTaskbar = true;
                contextMenuFix = false;
                break;
            case "volume":
                AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
                audio.adjustSuggestedStreamVolume(AudioManager.ADJUST_SAME, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI);

                showStartMenu = false;
                shouldHideTaskbar = true;
                contextMenuFix = false;
                break;
            case "file_manager":
                U.launchApp(this, () -> {
                    Intent fileManagerIntent;

                    if(Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1)
                        fileManagerIntent = new Intent(Intent.ACTION_VIEW);
                    else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                        fileManagerIntent = new Intent("android.provider.action.BROWSE");
                    else {
                        fileManagerIntent = new Intent("android.provider.action.BROWSE_DOCUMENT_ROOT");
                        fileManagerIntent.setComponent(ComponentName.unflattenFromString("com.android.documentsui/.DocumentsActivity"));
                    }

                    fileManagerIntent.addCategory(Intent.CATEGORY_DEFAULT);
                    fileManagerIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    fileManagerIntent.setData(Uri.parse("content://com.android.externalstorage.documents/root/primary"));

                    try {
                        startActivity(fileManagerIntent, U.getActivityOptionsBundle(this, ApplicationType.APPLICATION));
                    } catch (ActivityNotFoundException e) {
                        U.showToast(this, R.string.lock_device_not_supported);
                    } catch (IllegalArgumentException e) { /* Gracefully fail */ }
                });

                showStartMenu = false;
                shouldHideTaskbar = true;
                contextMenuFix = false;
                break;
            case "system_settings":
                U.launchApp(this, () -> {
                    Intent settingsIntent = new Intent(Settings.ACTION_SETTINGS);
                    settingsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                    try {
                        startActivity(settingsIntent, U.getActivityOptionsBundle(this, ApplicationType.APPLICATION));
                    } catch (ActivityNotFoundException e) {
                        U.showToast(this, R.string.lock_device_not_supported);
                    } catch (IllegalArgumentException e) { /* Gracefully fail */ }
                });

                showStartMenu = false;
                shouldHideTaskbar = true;
                contextMenuFix = false;
                break;
            case "power_menu":
                U.sendAccessibilityAction(this, AccessibilityService.GLOBAL_ACTION_POWER_DIALOG);

                showStartMenu = false;
                shouldHideTaskbar = true;
                contextMenuFix = false;
                break;
            case "add_icon_to_desktop":
                Intent intent2 = null;

                SharedPreferences pref4 = U.getSharedPreferences(this);
                switch(pref4.getString("theme", "light")) {
                    case "light":
                        intent2 = new Intent(this, DesktopIconSelectAppActivity.class);
                        break;
                    case "dark":
                        intent2 = new Intent(this, DesktopIconSelectAppActivityDark.class);
                        break;
                }

                if(intent2 != null)
                    intent2.putExtra("desktop_icon", desktopIcon);

                if(U.hasFreeformSupport(this)
                        && pref4.getBoolean("freeform_hack", false)
                        && intent2 != null && isInMultiWindowMode()) {
                    intent2.putExtra("no_shadow", true);
                    intent2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);

                    U.startActivityMaximized(getApplicationContext(), intent2);
                } else {
                    try {
                        startActivity(intent2);
                    } catch (IllegalArgumentException e) { /* Gracefully fail */ }
                }

                showStartMenu = false;
                shouldHideTaskbar = true;
                contextMenuFix = false;
                break;
            case "arrange_icons":
                LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("com.farmerbb.taskbar.ENTER_ICON_ARRANGE_MODE"));
                break;
            case "sort_by_name":
                LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("com.farmerbb.taskbar.SORT_DESKTOP_ICONS"));
                break;
            case "change_wallpaper":
                Intent intent3 = Intent.createChooser(new Intent(Intent.ACTION_SET_WALLPAPER), getString(R.string.set_wallpaper));
                intent3.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                U.startActivityMaximized(getApplicationContext(), intent3);

                showStartMenu = false;
                shouldHideTaskbar = true;
                contextMenuFix = false;
                break;
            case "remove_desktop_icon":
                try {
                    SharedPreferences pref5 = U.getSharedPreferences(this);
                    JSONArray jsonIcons = new JSONArray(pref5.getString("desktop_icons", "[]"));
                    int iconToRemove = -1;

                    for(int i = 0; i < jsonIcons.length(); i++) {
                        DesktopIconInfo info = DesktopIconInfo.fromJson(jsonIcons.getJSONObject(i));
                        if(info != null && info.column == desktopIcon.column && info.row == desktopIcon.row) {
                            iconToRemove = i;
                            break;
                        }
                    }

                    if(iconToRemove > -1) {
                        jsonIcons.remove(iconToRemove);

                        pref5.edit().putString("desktop_icons", jsonIcons.toString()).apply();
                        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("com.farmerbb.taskbar.REFRESH_DESKTOP_ICONS"));
                    }
                } catch (JSONException e) { /* Gracefully fail */ }
                break;
        }

        if(!secondaryMenu) finish();
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(!isFinishing()) finish();
    }

    @Override
    public void finish() {
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("com.farmerbb.taskbar.CONTEXT_MENU_DISAPPEARING"));
        MenuHelper.getInstance().setContextMenuOpen(false);

        if(!dashboardOrStartMenuAppearing) {
            if(showStartMenu)
                LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("com.farmerbb.taskbar.TOGGLE_START_MENU"));
            else {
                LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("com.farmerbb.taskbar.RESET_START_MENU"));

                if(shouldHideTaskbar && U.shouldCollapse(this, true))
                    LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("com.farmerbb.taskbar.HIDE_TASKBAR"));
            }
        }

        SharedPreferences pref = U.getSharedPreferences(this);

        super.finish();
        if(showStartMenu || pref.getBoolean("disable_animations", false))
            overridePendingTransition(0, 0);
    }

    @TargetApi(Build.VERSION_CODES.N_MR1)
    private int getLauncherShortcuts() {
        LauncherApps launcherApps = (LauncherApps) getSystemService(LAUNCHER_APPS_SERVICE);
        if(launcherApps.hasShortcutHostPermission()) {
            UserManager userManager = (UserManager) getSystemService(USER_SERVICE);

            LauncherApps.ShortcutQuery query = new LauncherApps.ShortcutQuery();
            query.setActivity(ComponentName.unflattenFromString(componentName));
            query.setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
                    | LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                    | LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED);

            shortcuts = launcherApps.getShortcuts(query, userManager.getUserForSerialNumber(userId));
            if(shortcuts != null)
                return shortcuts.size();
        }

        return 0;
    }

    @TargetApi(Build.VERSION_CODES.N_MR1)
    private CharSequence getShortcutTitle(ShortcutInfo shortcut) {
        CharSequence longLabel = shortcut.getLongLabel();
        if(longLabel != null && longLabel.length() > 0 && longLabel.length() <= 20)
            return longLabel;
        else
            return shortcut.getShortLabel();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if(secondaryMenu) {
            secondaryMenu = false;

            getPreferenceScreen().removeAll();
            generateMenu();

            getListView().setOnItemLongClickListener(null);

            if(U.hasBrokenSetLaunchBoundsApi())
                U.cancelToast();
        } else {
            if(contextMenuFix && !showStartMenu)
                U.startFreeformHack(this);

            super.onBackPressed();
            if(FreeformHackHelper.getInstance().isInFreeformWorkspace())
                overridePendingTransition(0, 0);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        LocalBroadcastManager.getInstance(this).unregisterReceiver(dashboardOrStartMenuAppearingReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(finishReceiver);
    }
}
