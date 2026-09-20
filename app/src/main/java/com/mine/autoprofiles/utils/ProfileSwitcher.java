package com.mine.autoprofiles.utils;

import android.content.Context;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Single place for all LineageOS ProfileManager reflection.
 *
 * Used by ScheduleReceiver and MainActivity so both paths behave
 * consistently and produce consistent logs.
 */
public final class ProfileSwitcher {

    public static final String PREF_NAME = "AutoProfilePrefs";
    public static final String REVERT_KEY_PREFIX = "revert_profile_rule_";
    public static final String MASTER_ENABLED_KEY = "master_enabled";

    /**
     * Per-rule flag: this CELL rule's location is currently considered
     * "entered".
     */
    public static final String CELL_ACTIVE_KEY_PREFIX =
            "cell_active_rule_";

    private static final String TAG = "AutoProfile";

    private ProfileSwitcher() {
        // Utility class.
    }

    /**
     * Soft kill switch: when false, the whole app is inert.
     */
    public static boolean isMasterEnabled(Context context) {
        return context
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean(MASTER_ENABLED_KEY, true);
    }

    public static void setMasterEnabled(
            Context context,
            boolean enabled) {
        context
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(MASTER_ENABLED_KEY, enabled)
                .apply();
    }

    /**
     * If this rule's time window is currently applied, switch back to that
     * profile and clear the saved state.
     *
     * Called when a rule is deleted, disabled, or edited mid-window so the
     * phone is not left stuck on the scheduled profile.
     */
    public static void revertIfActive(
            Context context,
            long ruleId) {
        android.content.SharedPreferences prefs =
                context.getSharedPreferences(
                        PREF_NAME,
                        Context.MODE_PRIVATE);

        String key = REVERT_KEY_PREFIX + ruleId;

        // A CELL rule being reverted is by definition no longer entered.
        prefs.edit()
                .remove(CELL_ACTIVE_KEY_PREFIX + ruleId)
                .apply();

        String profileToRevert = prefs.getString(key, null);

        if (profileToRevert != null) {
            Log.i(
                    TAG,
                    "Rule " + ruleId
                            + " removed/changed mid-window - reverting to '"
                            + profileToRevert + "'");

            switchTo(context, profileToRevert);
            prefs.edit().remove(key).apply();
        }
    }

    public static String getActiveProfileName(Context context) {
        try {
            Object[] lineage = getLineageClasses(context);

            if (lineage == null) {
                return null;
            }

            Class<?> profileManagerClass = (Class<?>) lineage[0];
            Class<?> profileClass = (Class<?>) lineage[1];
            Object profileManagerInstance = lineage[2];

            Method getActiveProfileMethod =
                    profileManagerClass.getMethod("getActiveProfile");

            Object activeProfile =
                    getActiveProfileMethod.invoke(profileManagerInstance);

            if (activeProfile != null) {
                Method getNameMethod =
                        profileClass.getMethod("getName");

                return (String) getNameMethod.invoke(activeProfile);
            }
        } catch (Exception e) {
            Log.e(
                    TAG,
                    "Error getting active LineageOS profile",
                    unwrap(e));
        }

        return null;
    }

    /**
     * Returns true only if the switch call completed successfully and the
     * active profile could be verified.
     */
    public static boolean switchTo(
            Context context,
            String profileName) {
        try {
            Object[] lineage = getLineageClasses(context);

            if (lineage == null) {
                Log.e(
                        TAG,
                        "SWITCH FAILED: LineageOS classes/instance unavailable");
                return false;
            }

            Class<?> profileManagerClass = (Class<?>) lineage[0];
            Class<?> profileClass = (Class<?>) lineage[1];
            Object profileManagerInstance = lineage[2];

            Method getProfilesMethod =
                    profileManagerClass.getMethod("getProfiles");

            Object[] profiles =
                    (Object[]) getProfilesMethod.invoke(
                            profileManagerInstance);

            if (profiles == null) {
                Log.e(
                        TAG,
                        "SWITCH FAILED: getProfiles() returned null");
                return false;
            }

            Method getNameMethod =
                    profileClass.getMethod("getName");

            Object targetProfileObj = null;
            StringBuilder available = new StringBuilder();

            for (Object profile : profiles) {
                String name =
                        (String) getNameMethod.invoke(profile);

                available
                        .append('\'')
                        .append(name)
                        .append("' ");

                if (profileName.equals(name)) {
                    targetProfileObj = profile;
                }
            }

            if (targetProfileObj == null) {
                Log.e(
                        TAG,
                        "SWITCH FAILED: profile '"
                                + profileName
                                + "' not found. Available: "
                                + available);
                return false;
            }

            Method getUuidMethod =
                    profileClass.getMethod("getUuid");

            Object uuid =
                    getUuidMethod.invoke(targetProfileObj);

            try {
                Method setActiveUuid =
                        profileManagerClass.getMethod(
                                "setActiveProfile",
                                java.util.UUID.class);

                setActiveUuid.invoke(
                        profileManagerInstance,
                        uuid);
            } catch (NoSuchMethodException e) {
                Method setActiveStr =
                        profileManagerClass.getMethod(
                                "setActiveProfile",
                                String.class);

                setActiveStr.invoke(
                        profileManagerInstance,
                        profileName);
            }

            // Verify: read back the active profile.
            String nowActive =
                    getActiveProfileName(context);

            Log.i(
                    TAG,
                    "setActiveProfile('"
                            + profileName
                            + "') completed. Active profile is now: '"
                            + nowActive
                            + "'");

            return profileName.equals(nowActive);

        } catch (Exception e) {
            Throwable real = unwrap(e);

            if (real instanceof SecurityException) {
                Log.e(
                        TAG,
                        "SWITCH FAILED with SecurityException - the app "
                                + "does not hold "
                                + "lineageos.permission.MODIFY_PROFILES "
                                + "on this build",
                        real);
            } else {
                Log.e(
                        TAG,
                        "SWITCH FAILED: "
                                + real.getClass().getSimpleName(),
                        real);
            }

            return false;
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof InvocationTargetException
                && throwable.getCause() != null) {
            return throwable.getCause();
        }

        return throwable;
    }

    /**
    * Loads the LineageOS classes through normal class loading.
    *
    * On non-LineageOS devices, Class.forName() fails and this method returns
    * null. Callers then treat the ProfileManager API as unavailable.
    */
    private static Object[] getLineageClasses(Context context) {
        try {
            Class<?> profileManagerClass =
                    Class.forName("lineageos.app.ProfileManager");

            Class<?> profileClass =
                    Class.forName("lineageos.app.Profile");

            Method getInstanceMethod =
                    profileManagerClass.getMethod(
                            "getInstance",
                            Context.class);

            Object profileManagerInstance =
                    getInstanceMethod.invoke(null, context);

            if (profileManagerInstance == null) {
                Log.e(
                        TAG,
                        "ProfileManager.getInstance() returned null");
                return null;
            }

            return new Object[]{
                    profileManagerClass,
                    profileClass,
                    profileManagerInstance
            };

        } catch (ClassNotFoundException e) {
            Log.i(
                    TAG,
                    "LineageOS ProfileManager API is not available "
                            + "on this device");
            return null;

        } catch (Exception e) {
            Log.e(
                    TAG,
                    "Failed to get ProfileManager instance",
                    unwrap(e));
            return null;
        }
    }
}
