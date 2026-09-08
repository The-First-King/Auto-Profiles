package com.mine.autoprofile.utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import com.mine.autoprofile.models.Schedule;
import com.mine.autoprofile.receivers.ScheduleReceiver;

public class AlarmHelper {

    /**
     * Entry point used when a rule is created/edited (ScheduleActivity), restored
     * (BootReceiver) or re-registered (MainActivity). If "now" already falls inside
     * the rule's window, the START event fires immediately.
     *
     * @return true if the schedule is still "alive" (at least one alarm was set, or
     *         setting alarms is currently suppressed by the master switch);
     *         false if the schedule has no upcoming events at all - callers may
     *         auto-disable the rule in that case.
     */
    public static boolean scheduleAlarm(Context context, long ruleId, long profileId, String timeString) {
        return scheduleAlarm(context, ruleId, profileId, timeString, true);
    }

    /**
     * @param allowImmediateStart pass false when rescheduling from ScheduleReceiver,
     *                            otherwise the START event would re-fire in a loop.
     */
    public static boolean scheduleAlarm(Context context, long ruleId, long profileId,
                                        String timeString, boolean allowImmediateStart) {
        // Soft kill switch: while OFF, the app must not register any alarms,
        // regardless of who asks (UI, BootReceiver, ScheduleReceiver reschedule).
        // Return true: being disabled by the master switch is not "schedule over".
        if (!ProfileSwitcher.isMasterEnabled(context)) {
            Log.i("AutoProfile", "Master switch OFF - not scheduling alarms for rule " + ruleId);
            return true;
        }

        Schedule schedule = Schedule.parse(timeString);
        if (schedule == null) {
            Log.e("AutoProfile", "Rule " + ruleId + ": unparseable schedule value: " + timeString);
            return false;
        }

        long now = System.currentTimeMillis();
        long[] w = ScheduleCalculator.windows(schedule, now);
        long curStart = w[0], nextStart = w[2], earliestEnd = w[3];

        boolean anyAlarm = false;

        // 1. START alarm (Request Code: ruleId * 2)
        if (allowImmediateStart && curStart != -1 && !hasRevertSaved(context, ruleId)) {
            // We are inside a window right now (rule just saved / re-registered)
            // -> apply it immediately. hasRevertSaved() prevents a second immediate
            // fire when alarms are re-registered (app open / update) mid-window.
            setAlarm(context, (int) ruleId * 2, ruleId, profileId, timeString, now + 1500, true);
            anyAlarm = true;
        } else if (nextStart != -1) {
            setAlarm(context, (int) ruleId * 2, ruleId, profileId, timeString, nextStart, true);
            anyAlarm = true;
        }

        // 2. END alarm (Request Code: ruleId * 2 + 1) - the earliest window end
        //    after "now": the current window's end when inside one (this keeps the
        //    final revert working even when no future START exists), otherwise the
        //    next window's end.
        if (earliestEnd != -1) {
            setAlarm(context, (int) ruleId * 2 + 1, ruleId, profileId, timeString, earliestEnd, false);
            anyAlarm = true;
        }

        if (!anyAlarm) {
            Log.i("AutoProfile", "Rule " + ruleId + ": schedule has no upcoming events ("
                    + schedule.describe() + ")");
        }
        return anyAlarm;
    }

    /** Cancels both the START and END alarms of a rule (used by delete/disable/edit). */
    public static void cancelAlarms(Context context, long ruleId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        for (int requestCode : new int[]{(int) ruleId * 2, (int) ruleId * 2 + 1}) {
            Intent intent = new Intent(context, ScheduleReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context, requestCode, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
        }
        Log.i("AutoProfile", "Cancelled alarms for rule " + ruleId);
    }

    private static boolean hasRevertSaved(Context context, long ruleId) {
        return context.getSharedPreferences(ProfileSwitcher.PREF_NAME, Context.MODE_PRIVATE)
                .contains(ProfileSwitcher.REVERT_KEY_PREFIX + ruleId);
    }

    private static void setAlarm(Context context, int requestCode, long ruleId, long profileId,
                                 String timeString, long triggerTime, boolean isStart) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, ScheduleReceiver.class);
        intent.putExtra("RULE_ID", ruleId);
        intent.putExtra("PROFILE_ID", profileId);
        intent.putExtra("TRIGGER_VALUE", timeString);
        intent.putExtra("IS_START_EVENT", isStart);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        boolean exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || alarmManager.canScheduleExactAlarms();

        if (exactAllowed) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        } else {
            // Android 12+ denied "Alarms & reminders": don't silently drop the alarm,
            // fall back to a windowed (inexact) alarm so the rule still works, just less precisely.
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerTime, 10 * 60 * 1000L, pendingIntent);
            Log.w("AutoProfile", "Exact alarm permission missing - scheduled INEXACT alarm instead. "
                    + "Ask the user to enable 'Alarms & reminders' for precise switching.");
        }
        Log.i("AutoProfile", "Scheduled " + (isStart ? "START" : "END")
                + " alarm for rule " + ruleId + " at " + triggerTime + " (exact=" + exactAllowed + ")");
    }
}
