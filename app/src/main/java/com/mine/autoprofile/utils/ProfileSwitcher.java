package com.mine.autoprofile.utils;

import android.content.Context;
import android.util.Log;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Single place for all LineageOS ProfileManager reflection.
 * Used by ScheduleReceiver (alarms) and MainActivity (manual "Apply now" test),
 * so both paths behave identically and log identically.
 */
public final class ProfileSwitcher {

    public static final String PREF_NAME = "AutoProfilePrefs";
    public static final String REVERT_KEY_PREFIX = "revert_profile_rule_";
    private static final String TAG = "AutoProfile";

    private ProfileSwitcher() {}

    public static String getActiveProfileName(Context context) {
        try {
            Object[] lineage = getLineageClasses(context);
            if (lineage == null) return null;

            Class<?> profileManagerClass = (Class<?>) lineage[0];
            Class<?> profileClass = (Class<?>) lineage[1];
            Object profileManagerInstance = lineage[2];

            Method getActiveProfileMethod = profileManagerClass.getMethod("getActiveProfile");
            Object activeProfile = getActiveProfileMethod.invoke(profileManagerInstance);

            if (activeProfile != null) {
                Method getNameMethod = profileClass.getMethod("getName");
                return (String) getNameMethod.invoke(activeProfile);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting active LineageOS profile", unwrap(e));
        }
        return null;
    }

    /** @return true only if the switch call completed without throwing. */
    public static boolean switchTo(Context context, String profileName) {
        try {
            Object[] lineage = getLineageClasses(context);
            if (lineage == null) {
                Log.e(TAG, "SWITCH FAILED: LineageOS classes/instance unavailable");
                return false;
            }

            Class<?> profileManagerClass = (Class<?>) lineage[0];
            Class<?> profileClass = (Class<?>) lineage[1];
            Object profileManagerInstance = lineage[2];

            Method getProfilesMethod = profileManagerClass.getMethod("getProfiles");
            Object[] profiles = (Object[]) getProfilesMethod.invoke(profileManagerInstance);
            if (profiles == null) {
                Log.e(TAG, "SWITCH FAILED: getProfiles() returned null");
                return false;
            }

            Method getNameMethod = profileClass.getMethod("getName");
            Object targetProfileObj = null;
            StringBuilder available = new StringBuilder();
            for (Object p : profiles) {
                String name = (String) getNameMethod.invoke(p);
                available.append('\'').append(name).append("' ");
                if (profileName.equals(name)) {
                    targetProfileObj = p;
                }
            }

            if (targetProfileObj == null) {
                Log.e(TAG, "SWITCH FAILED: profile '" + profileName
                        + "' not found. Available: " + available);
                return false;
            }

            // LineageOS 19 (and the bundled SDK jar) expose exactly one setter:
            //   public void setActiveProfile(UUID profileUuid)
            // There is no setActiveProfile(Profile) and no setActiveProfile(String).
            Method getUuidMethod = profileClass.getMethod("getUuid");
            Object uuid = getUuidMethod.invoke(targetProfileObj);

            try {
                Method setActiveUuid = profileManagerClass.getMethod(
                        "setActiveProfile", java.util.UUID.class);
                setActiveUuid.invoke(profileManagerInstance, uuid);
            } catch (NoSuchMethodException e) {
                // Only very old CM/Lineage builds still have the String variant
                Method setActiveStr = profileManagerClass.getMethod(
                        "setActiveProfile", String.class);
                setActiveStr.invoke(profileManagerInstance, profileName);
            }

            // Verify: read back the active profile
            String nowActive = getActiveProfileName(context);
            Log.i(TAG, "setActiveProfile('" + profileName + "') completed. Active profile is now: '"
                    + nowActive + "'");
            return profileName.equals(nowActive);

        } catch (Exception e) {
            Throwable real = unwrap(e);
            if (real instanceof SecurityException) {
                Log.e(TAG, "SWITCH FAILED with SecurityException - the app does not hold "
                        + "lineageos.permission.MODIFY_PROFILES on this build", real);
            } else {
                Log.e(TAG, "SWITCH FAILED: " + real.getClass().getSimpleName(), real);
            }
            return false;
        }
    }

    /** Reflection throws InvocationTargetException; the real cause is inside it. */
    private static Throwable unwrap(Throwable t) {
        if (t instanceof InvocationTargetException && t.getCause() != null) {
            return t.getCause();
        }
        return t;
    }

    private static Object[] getLineageClasses(Context context) {
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
                Log.e(TAG, "Failed to load LineageOS classes", e2);
                return null;
            }
        }

        try {
            Method getInstanceMethod = profileManagerClass.getMethod("getInstance", Context.class);
            Object profileManagerInstance = getInstanceMethod.invoke(null, context);
            if (profileManagerInstance == null) {
                Log.e(TAG, "ProfileManager.getInstance() returned null");
                return null;
            }
            return new Object[]{profileManagerClass, profileClass, profileManagerInstance};
        } catch (Exception e) {
            Log.e(TAG, "Failed to get ProfileManager instance", unwrap(e));
            return null;
        }
    }
}
