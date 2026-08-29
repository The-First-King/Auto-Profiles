package com.mine.autoprofile.utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import com.mine.autoprofile.receivers.ScheduleReceiver;
import java.util.Calendar;

public class AlarmHelper {

    private static final long WEEK_MS = 7L * 24 * 60 * 60 * 1000;

    /**
     * Entry point used when a rule is created (ScheduleActivity) or restored (BootReceiver).
     * If "now" already falls inside the rule's time window, the START event fires immediately.
     */
    public static void scheduleAlarm(Context context, long ruleId, long profileId, String timeString) {
        scheduleAlarm(context, ruleId, profileId, timeString, true);
    }

    /**
     * @param allowImmediateStart pass false when rescheduling from ScheduleReceiver,
     *                            otherwise the START event would re-fire in a loop.
     */
    public static void scheduleAlarm(Context context, long ruleId, long profileId,
                                     String timeString, boolean allowImmediateStart) {
        // timeString format expected: "08:00-17:00|2,3,4,5,6"
        try {
            String[] parts = timeString.split("\\|");
            if (parts.length < 2) return;

            String[] times = parts[0].split("-");
            int startMinutes = parseMinutes(times[0]);
            Integer endMinutes = times.length > 1 ? parseMinutes(times[1]) : null;
            String[] daysStr = parts[1].split(",");

            long now = System.currentTimeMillis();

            // Duration of the window; handles intervals that cross midnight (e.g. 22:00-06:00)
            long durationMs = 0;
            if (endMinutes != null) {
                durationMs = ((endMinutes - startMinutes + 24 * 60) % (24 * 60)) * 60_000L;
            }

            // 1. START alarm (Request Code: ruleId * 2)
            long nextStartTime;
            if (allowImmediateStart && durationMs > 0
                    && isWithinInterval(now, startMinutes, durationMs, daysStr)
                    && !hasRevertSaved(context, ruleId)) {
                // The user saved a rule whose window is active right now -> apply it
                // immediately. hasRevertSaved() prevents a second immediate fire when
                // alarms are re-registered (app open / app update) mid-window.
                nextStartTime = now + 1500;
            } else {
                nextStartTime = calculateNextOccurrence(now, startMinutes, daysStr);
            }
            if (nextStartTime > 0) {
                setAlarm(context, (int) ruleId * 2, ruleId, profileId, timeString, nextStartTime, true);
            }

            // 2. END alarm (Request Code: ruleId * 2 + 1), anchored to the start occurrence
            //    so that midnight-crossing windows revert on the correct day.
            if (endMinutes != null && durationMs > 0) {
                long nextEndTime = calculateNextEnd(now, startMinutes, durationMs, daysStr);
                if (nextEndTime > 0) {
                    setAlarm(context, (int) ruleId * 2 + 1, ruleId, profileId, timeString, nextEndTime, false);
                }
            }
        } catch (Exception e) {
            Log.e("AutoProfile", "Error parsing schedule string", e);
        }
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

    private static int parseMinutes(String hhmm) {
        String[] p = hhmm.trim().split(":");
        return Integer.parseInt(p[0].trim()) * 60 + Integer.parseInt(p[1].trim());
    }

    /** Next occurrence of the given clock time on any of the given days, strictly after 'now'. */
    private static long calculateNextOccurrence(long now, int minutesOfDay, String[] daysStr) {
        long best = -1;
        for (String dayStr : daysStr) {
            long t = occurrenceAfter(now, minutesOfDay, Integer.parseInt(dayStr.trim()));
            if (best == -1 || t < best) best = t;
        }
        return best;
    }

    /** Next occurrence of clock time on the given DAY_OF_WEEK, strictly after 'now'. */
    private static long occurrenceAfter(long now, int minutesOfDay, int targetDayOfWeek) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        c.set(Calendar.HOUR_OF_DAY, minutesOfDay / 60);
        c.set(Calendar.MINUTE, minutesOfDay % 60);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);

        int diff = targetDayOfWeek - c.get(Calendar.DAY_OF_WEEK);
        if (diff < 0 || (diff == 0 && c.getTimeInMillis() <= now)) {
            diff += 7;
        }
        c.add(Calendar.DAY_OF_MONTH, diff);
        return c.getTimeInMillis();
    }

    /** True if 'now' is inside [start, start + duration) for any selected day. */
    private static boolean isWithinInterval(long now, int startMinutes, long durationMs, String[] daysStr) {
        for (String dayStr : daysStr) {
            long nextStart = occurrenceAfter(now, startMinutes, Integer.parseInt(dayStr.trim()));
            long lastStart = nextStart - WEEK_MS; // most recent past occurrence on that day
            if (now >= lastStart && now < lastStart + durationMs) return true;
        }
        return false;
    }

    /** Earliest end-of-window moment after 'now', considering windows already in progress. */
    private static long calculateNextEnd(long now, int startMinutes, long durationMs, String[] daysStr) {
        long best = -1;
        for (String dayStr : daysStr) {
            long nextStart = occurrenceAfter(now, startMinutes, Integer.parseInt(dayStr.trim()));
            long lastStart = nextStart - WEEK_MS;
            long candidate = (lastStart + durationMs > now)
                    ? lastStart + durationMs   // window in progress -> revert at its real end
                    : nextStart + durationMs;  // otherwise end of the next window
            if (best == -1 || candidate < best) best = candidate;
        }
        return best;
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
