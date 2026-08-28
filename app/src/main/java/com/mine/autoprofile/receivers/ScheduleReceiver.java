package com.mine.autoprofile.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import com.mine.autoprofile.database.AppDatabase;
import com.mine.autoprofile.models.Profile;
import com.mine.autoprofile.utils.AlarmHelper;
import java.lang.reflect.Method;
import java.util.concurrent.Executors;

public class ScheduleReceiver extends BroadcastReceiver {

    private static final String PREF_NAME = "AutoProfilePrefs";
    private static final String REVERT_KEY_PREFIX = "revert_profile_rule_";

    @Override
    public void onReceive(Context context, Intent intent) {
        long ruleId = intent.getLongExtra("RULE_ID", -1);
        long profileId = intent.getLongExtra("PROFILE_ID", -1);
        boolean isStartEvent = intent.getBooleanExtra("IS_START_EVENT", true);
        String triggerValue = intent.getStringExtra("TRIGGER_VALUE");
        
        if (profileId == -1 || ruleId == -1) return;

        Log.i("AutoProfile", "Schedule triggered! isStartEvent: " + isStartEvent);
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        // Keep the process alive until the background work is done. Without this,
        // onReceive() returns immediately and Android may kill the process before
        // the executor thread ever touches the database or switches the profile.
        final PendingResult pendingResult = goAsync();

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
            AppDatabase db = AppDatabase.getInstance(context);
            
            if (isStartEvent) {
                // 1. Capture and save the current profile before switching
                String activeProfile = getActiveLineageProfile(context);
                if (activeProfile != null) {
                    prefs.edit().putString(REVERT_KEY_PREFIX + ruleId, activeProfile).apply();
                    Log.i("AutoProfile", "Saved profile to revert to later: " + activeProfile);
                }

                // 2. Apply the scheduled profile
                Profile profile = db.profileDao().getProfileById(profileId);
                if (profile != null && profile.getName() != null) {
                    switchLineageProfile(context, profile.getName());
                }
            } else {
                // 1. Revert to the previously saved profile
                String profileToRevert = prefs.getString(REVERT_KEY_PREFIX + ruleId, null);
                if (profileToRevert != null) {
                    switchLineageProfile(context, profileToRevert);
                    prefs.edit().remove(REVERT_KEY_PREFIX + ruleId).apply(); // Cleanup
                } else {
                    Log.w("AutoProfile", "No revert profile found for rule " + ruleId);
                }
            }

            // Reschedule this rule for the next occurrence.
            // allowImmediateStart = false: at this instant "now" is inside the window,
            // so an immediate-start check here would re-fire the START event in a loop.
            if (triggerValue != null) {
                AlarmHelper.scheduleAlarm(context, ruleId, profileId, triggerValue, false);
            }
            } finally {
                pendingResult.finish();
            }
        });
    }

    private String getActiveLineageProfile(Context context) {
        try {
            Object[] lineageClasses = getLineageClasses(context);
            if (lineageClasses == null) return null;
            
            Class<?> profileManagerClass = (Class<?>) lineageClasses[0];
            Class<?> profileClass = (Class<?>) lineageClasses[1];
            Object profileManagerInstance = lineageClasses[2];

            Method getActiveProfileMethod = profileManagerClass.getMethod("getActiveProfile");
            Object activeProfile = getActiveProfileMethod.invoke(profileManagerInstance);

            if (activeProfile != null) {
                Method getNameMethod = profileClass.getMethod("getName");
                return (String) getNameMethod.invoke(activeProfile);
            }
        } catch (Exception e) {
            Log.e("AutoProfile", "Error getting active LineageOS profile", e);
        }
        return null;
    }

    private void switchLineageProfile(Context context, String profileName) {
        try {
            Object[] lineageClasses = getLineageClasses(context);
            if (lineageClasses == null) return;
            
            Class<?> profileManagerClass = (Class<?>) lineageClasses[0];
            Class<?> profileClass = (Class<?>) lineageClasses[1];
            Object profileManagerInstance = lineageClasses[2];

            Method getProfilesMethod = profileManagerClass.getMethod("getProfiles");
            Object[] profiles = (Object[]) getProfilesMethod.invoke(profileManagerInstance);
            if (profiles == null) return;

            Method getNameMethod = profileClass.getMethod("getName");
            Object targetProfileObj = null;

            for (Object p : profiles) {
                if (profileName.equals((String) getNameMethod.invoke(p))) {
                    targetProfileObj = p;
                    break;
                }
            }

            if (targetProfileObj == null) {
                Log.e("AutoProfile", "Profile name '" + profileName + "' not found.");
                return;
            }

            try {
                Method setActiveMethod = profileManagerClass.getMethod("setActiveProfile", profileClass);
                setActiveMethod.invoke(profileManagerInstance, targetProfileObj);
            } catch (NoSuchMethodException e) {
                Method setActiveMethodStr = profileManagerClass.getMethod("setActiveProfile", String.class);
                setActiveMethodStr.invoke(profileManagerInstance, profileName);
            }
            Log.i("AutoProfile", "Successfully switched LineageOS profile to: " + profileName);

        } catch (Exception e) {
            Log.e("AutoProfile", "Error switching LineageOS profile", e);
        }
    }

    // Helper to keep reflection class loading DRY
    private Object[] getLineageClasses(Context context) {
        Class<?> profileManagerClass;
        Class<?> profileClass;
        try {
            profileManagerClass = Class.forName("lineageos.app.ProfileManager");
            profileClass = Class.forName("lineageos.app.Profile");
        } catch (ClassNotFoundException e1) {
            try {
                String libPath = "/system/framework/org.lineageos.platform.jar";
                dalvik.system.DexClassLoader classLoader = new dalvik.system.DexClassLoader(
                        libPath, context.getCodeCacheDir().getAbsolutePath(), null, context.getClassLoader());
                profileManagerClass = classLoader.loadClass("lineageos.app.ProfileManager");
                profileClass = classLoader.loadClass("lineageos.app.Profile");
            } catch (Exception e2) {
                Log.e("AutoProfile", "Failed to load LineageOS classes", e2);
                return null;
            }
        }
        
        try {
            Method getInstanceMethod = profileManagerClass.getMethod("getInstance", Context.class);
            Object profileManagerInstance = getInstanceMethod.invoke(null, context);
            return profileManagerInstance == null ? null : new Object[]{profileManagerClass, profileClass, profileManagerInstance};
        } catch (Exception e) {
            Log.e("AutoProfile", "Failed to get ProfileManager instance", e);
            return null;
        }
    }
}
