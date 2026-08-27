package com.mine.autoprofile.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.mine.autoprofile.database.AppDatabase;
import com.mine.autoprofile.models.Profile;
import com.mine.autoprofile.utils.AlarmHelper;
import java.lang.reflect.Method;
import java.util.concurrent.Executors;

public class ScheduleReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        long ruleId = intent.getLongExtra("RULE_ID", -1);
        long profileId = intent.getLongExtra("PROFILE_ID", -1);
        boolean isStartEvent = intent.getBooleanExtra("IS_START_EVENT", true);
        String triggerValue = intent.getStringExtra("TRIGGER_VALUE");
        
        if (profileId == -1) return;

        Log.i("AutoProfile", "Schedule triggered! isStartEvent: " + isStartEvent);

        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(context);
            
            // Assume "Default" is your standard system fallback profile name
            String profileToApply = "Default"; 
            
            if (isStartEvent) {
                Profile profile = db.profileDao().getProfileById(profileId);
                if (profile != null && profile.getName() != null) {
                    profileToApply = profile.getName();
                }
            }

            switchLineageProfile(context, profileToApply);

            // Reschedule this rule for the next week
            if (ruleId != -1 && triggerValue != null) {
                AlarmHelper.scheduleAlarm(context, ruleId, profileId, triggerValue);
            }
        });
    }

    private void switchLineageProfile(Context context, String profileName) {
        Class<?> profileManagerClass;
        Class<?> profileClass;
        Object profileManagerInstance;

        try {
            // 1. Try standard reflection
            profileManagerClass = Class.forName("lineageos.app.ProfileManager");
            profileClass = Class.forName("lineageos.app.Profile");
        } catch (ClassNotFoundException e1) {
            try {
                // 2. Fallback: DexClassLoader for LineageOS system jar
                String libPath = "/system/framework/org.lineageos.platform.jar";
                dalvik.system.DexClassLoader classLoader = new dalvik.system.DexClassLoader(
                        libPath, context.getCodeCacheDir().getAbsolutePath(), null, context.getClassLoader());

                profileManagerClass = classLoader.loadClass("lineageos.app.ProfileManager");
                profileClass = classLoader.loadClass("lineageos.app.Profile");
            } catch (Exception e2) {
                Log.e("AutoProfile", "Failed to load LineageOS classes", e2);
                return;
            }
        }

        try {
            Method getInstanceMethod = profileManagerClass.getMethod("getInstance", Context.class);
            profileManagerInstance = getInstanceMethod.invoke(null, context);

            if (profileManagerInstance == null) return;

            Method getProfilesMethod = profileManagerClass.getMethod("getProfiles");
            Object[] profiles = (Object[]) getProfilesMethod.invoke(profileManagerInstance);
            if (profiles == null) return;

            Method getNameMethod = profileClass.getMethod("getName");
            Object targetProfileObj = null;

            for (Object p : profiles) {
                String name = (String) getNameMethod.invoke(p);
                if (profileName.equals(name)) {
                    targetProfileObj = p;
                    break;
                }
            }

            if (targetProfileObj == null) {
                Log.e("AutoProfile", "Profile name '" + profileName + "' not found.");
                return;
            }

            boolean success = false;
            try {
                Method setActiveMethod = profileManagerClass.getMethod("setActiveProfile", profileClass);
                setActiveMethod.invoke(profileManagerInstance, targetProfileObj);
                success = true;
            } catch (NoSuchMethodException e) {
                try {
                    Method setActiveMethodStr = profileManagerClass.getMethod("setActiveProfile", String.class);
                    setActiveMethodStr.invoke(profileManagerInstance, profileName);
                    success = true;
                } catch (Exception ex) {
                    Log.e("AutoProfile", "Failed setActiveProfile with String", ex);
                }
            }

            if (success) {
                Log.i("AutoProfile", "Successfully switched LineageOS profile to: " + profileName);
            }
        } catch (Exception e) {
            Log.e("AutoProfile", "Error switching LineageOS profile", e);
        }
    }
}
