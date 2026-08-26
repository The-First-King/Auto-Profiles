package com.mine.autoprofile.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.mine.autoprofile.database.AppDatabase;
import com.mine.autoprofile.models.Profile;
import java.lang.reflect.Method;
import java.util.concurrent.Executors;

public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        long profileId = intent.getLongExtra("PROFILE_ID", -1);
        if (profileId == -1) {
            Log.e("AutoProfile", "AlarmReceiver received intent without valid PROFILE_ID");
            return;
        }

        Log.i("AutoProfile", "Alarm triggered for profileId: " + profileId);

        // Run database query and LineageOS interaction on a background thread
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(context);
            Profile profile = db.profileDao().getProfileById(profileId);

            if (profile == null || profile.getName() == null) {
                Log.e("AutoProfile", "Profile not found in database for ID: " + profileId);
                return;
            }

            switchLineageProfile(context, profile.getName());
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
                Log.e("AutoProfile", "Failed to load LineageOS classes via DexClassLoader in AlarmReceiver", e2);
                return;
            }
        }

        try {
            Method getInstanceMethod = profileManagerClass.getMethod("getInstance", Context.class);
            profileManagerInstance = getInstanceMethod.invoke(null, context);

            if (profileManagerInstance == null) {
                Log.e("AutoProfile", "ProfileManager instance is null");
                return;
            }

            // Verify profiles are enabled in the system
            Method isEnabledMethod = profileManagerClass.getMethod("isProfilesEnabled");
            boolean isEnabled = (Boolean) isEnabledMethod.invoke(profileManagerInstance);
            if (!isEnabled) {
                Log.w("AutoProfile", "LineageOS profiles are disabled in system settings.");
                return;
            }

            // Find the matching profile object from the system
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
                Log.e("AutoProfile", "Profile name '" + profileName + "' not found in system profiles.");
                return;
            }

            // Apply the active profile (supports object or string signatures across ROM versions)
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
                    Log.e("AutoProfile", "Failed to invoke setActiveProfile with String signature", ex);
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
