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

    public static void scheduleAlarm(Context context, long ruleId, long profileId, String timeString) {
        // timeString format expected: "08:00-17:00|2,3,4,5,6"
        try {
            String[] parts = timeString.split("\\|");
            if (parts.length < 2) return;

            String[] times = parts[0].split("-");
            String startTimeStr = times[0]; // e.g. "08:00"
            String endTimeStr = times.length > 1 ? times[1] : null; // e.g. "17:00"
            String[] daysStr = parts[1].split(",");

            // 1. Schedule Start Alarm (Request Code: ruleId * 2)
            long nextStartTime = calculateNextTriggerTime(startTimeStr, daysStr);
            if (nextStartTime != -1) {
                setExactAlarm(context, (int) ruleId * 2, ruleId, profileId, timeString, nextStartTime, true);
            }

            // 2. Schedule End Alarm (Request Code: ruleId * 2 + 1)
            if (endTimeStr != null) {
                long nextEndTime = calculateNextTriggerTime(endTimeStr, daysStr);
                if (nextEndTime != -1) {
                    setExactAlarm(context, (int) ruleId * 2 + 1, ruleId, profileId, timeString, nextEndTime, false);
                }
            }
        } catch (Exception e) {
            Log.e("AutoProfile", "Error parsing schedule string", e);
        }
    }

    private static long calculateNextTriggerTime(String timeStr, String[] daysStr) {
        String[] timeParts = timeStr.split(":");
        int targetHour = Integer.parseInt(timeParts[0]);
        int targetMinute = Integer.parseInt(timeParts[1]);

        Calendar calendar = Calendar.getInstance();
        long currentTime = calendar.getTimeInMillis();
        long nextTriggerTime = -1;

        for (String dayStr : daysStr) {
            int targetDayOfWeek = Integer.parseInt(dayStr.trim());
            Calendar tempCalendar = Calendar.getInstance();
            tempCalendar.set(Calendar.HOUR_OF_DAY, targetHour);
            tempCalendar.set(Calendar.MINUTE, targetMinute);
            tempCalendar.set(Calendar.SECOND, 0);
            tempCalendar.set(Calendar.MILLISECOND, 0);

            int currentDayOfWeek = tempCalendar.get(Calendar.DAY_OF_WEEK);
            int dayDifference = targetDayOfWeek - currentDayOfWeek;
            
            if (dayDifference < 0 || (dayDifference == 0 && tempCalendar.getTimeInMillis() <= currentTime)) {
                dayDifference += 7; // Move to next week
            }
            
            tempCalendar.add(Calendar.DAY_OF_MONTH, dayDifference);

            if (nextTriggerTime == -1 || tempCalendar.getTimeInMillis() < nextTriggerTime) {
                nextTriggerTime = tempCalendar.getTimeInMillis();
            }
        }
        return nextTriggerTime;
    }

    private static void setExactAlarm(Context context, int requestCode, long ruleId, long profileId, String timeString, long triggerTime, boolean isStart) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        // Android 12+ requires explicit permission check for Exact Alarms
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.e("AutoProfile", "Exact alarm permission missing! App will fail to trigger schedules.");
            return;
        }

        Intent intent = new Intent(context, ScheduleReceiver.class);
        intent.putExtra("RULE_ID", ruleId);
        intent.putExtra("PROFILE_ID", profileId);
        intent.putExtra("TRIGGER_VALUE", timeString);
        intent.putExtra("IS_START_EVENT", isStart);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        Log.i("AutoProfile", "Scheduled " + (isStart ? "START" : "END") + " alarm for rule " + ruleId + " at " + triggerTime);
    }
}
