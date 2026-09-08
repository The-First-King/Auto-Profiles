package com.mine.autoprofiles.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import com.mine.autoprofiles.database.AppDatabase;
import com.mine.autoprofiles.models.Profile;
import com.mine.autoprofiles.utils.AlarmHelper;
import com.mine.autoprofiles.utils.ProfileSwitcher;
import java.util.concurrent.Executors;

public class ScheduleReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        long ruleId = intent.getLongExtra("RULE_ID", -1);
        long profileId = intent.getLongExtra("PROFILE_ID", -1);
        boolean isStartEvent = intent.getBooleanExtra("IS_START_EVENT", true);
        String triggerValue = intent.getStringExtra("TRIGGER_VALUE");

        if (profileId == -1 || ruleId == -1) return;

        // Soft kill switch: ignore stray alarms fired while the app is disabled
        if (!ProfileSwitcher.isMasterEnabled(context)) {
            Log.i("AutoProfile", "Master switch OFF - ignoring schedule event for rule " + ruleId);
            return;
        }

        Log.i("AutoProfile", "Schedule triggered! isStartEvent=" + isStartEvent + " ruleId=" + ruleId);
        SharedPreferences prefs = context.getSharedPreferences(
                ProfileSwitcher.PREF_NAME, Context.MODE_PRIVATE);
        String revertKey = ProfileSwitcher.REVERT_KEY_PREFIX + ruleId;

        // Keep the process alive until the background work is done.
        final PendingResult pendingResult = goAsync();

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(context);

                if (isStartEvent) {
                    // 1. Save the current profile for the later revert - but never
                    //    overwrite an existing key: re-registration (app update, app
                    //    open) can legitimately re-fire START mid-window, and we must
                    //    not record our own scheduled profile as the "revert" target.
                    if (!prefs.contains(revertKey)) {
                        String activeProfile = ProfileSwitcher.getActiveProfileName(context);
                        if (activeProfile != null) {
                            prefs.edit().putString(revertKey, activeProfile).apply();
                            Log.i("AutoProfile", "Saved profile to revert to later: " + activeProfile);
                        }
                    }

                    // 2. Apply the scheduled profile
                    Profile profile = db.profileDao().getProfileById(profileId);
                    if (profile != null && profile.getName() != null) {
                        boolean ok = ProfileSwitcher.switchTo(context, profile.getName());
                        Log.i("AutoProfile", "START event: switch to '" + profile.getName()
                                + "' verified=" + ok);
                    } else {
                        Log.e("AutoProfile", "Profile id " + profileId + " not found in local DB!");
                    }
                } else {
                    // Revert to the previously saved profile
                    String profileToRevert = prefs.getString(revertKey, null);
                    if (profileToRevert != null) {
                        boolean ok = ProfileSwitcher.switchTo(context, profileToRevert);
                        Log.i("AutoProfile", "END event: revert to '" + profileToRevert
                                + "' verified=" + ok);
                        prefs.edit().remove(revertKey).apply(); // Cleanup
                    } else {
                        Log.w("AutoProfile", "No revert profile found for rule " + ruleId);
                    }
                }

                // Reschedule for the next occurrence. allowImmediateStart = false:
                // at this instant "now" is inside the window, so an immediate-start
                // check here would re-fire the START event in a loop.
                if (triggerValue != null) {
                    boolean alive = AlarmHelper.scheduleAlarm(context, ruleId, profileId, triggerValue, false);
                    if (!alive) {
                        // One-time event finished, or COUNT/UNTIL termination reached:
                        // no upcoming events remain, so mark the rule as done (disabled).
                        db.ruleDao().disableById(ruleId);
                        Log.i("AutoProfile", "Rule " + ruleId + " completed its schedule - disabled.");
                    }
                }
            } finally {
                pendingResult.finish();
            }
        });
    }
}
