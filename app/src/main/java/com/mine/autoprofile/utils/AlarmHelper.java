package com.mine.autoprofile.utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import com.mine.autoprofile.receivers.ScheduleReceiver;
import java.util.Calendar;

public class AlarmHelper {

    public static void scheduleAlarm(Context context, long ruleId, long profileId, String timeString) {
        // timeString format expected: "08:00-17:00|2,3,4,5,6"
        try {
            String[] parts = timeString.split("\\|");
            if (parts.length < 2) return;

            String startTime = parts[0].split("-")[0]; // e.g. "08:00"
            String[] timeParts = startTime.split(":");
            int targetHour = Integer.parseInt(timeParts[0]);
            int targetMinute = Integer.parseInt(timeParts[1]);

            String[] daysStr = parts[1].split(",");
            
            // Find the next closest matching day and time
            Calendar calendar = Calendar.getInstance();
            long currentTime = calendar.getTimeInMillis();
            long nextTriggerTime = -1;

            for (String dayStr : daysStr) {
                int targetDayOfWeek = Integer.parseInt(dayStr.trim()); // 1=Sun, 2=Mon...
                
                Calendar tempCalendar = Calendar.getInstance();
                tempCalendar.set(Calendar.HOUR_OF_DAY, targetHour);
                tempCalendar.set(Calendar.MINUTE, targetMinute);
                tempCalendar.set(Calendar.SECOND, 0);
                tempCalendar.set(Calendar.MILLISECOND, 0);

                // Adjust to the target day of the week
                int currentDayOfWeek = tempCalendar.get(Calendar.DAY_OF_WEEK);
                int dayDifference = targetDayOfWeek - currentDayOfWeek;
                
                if (dayDifference < 0 || (dayDifference == 0 && tempCalendar.getTimeInMillis() <= currentTime)) {
                    dayDifference += 7; // Move to next week if the time today has already passed
                }
                
                tempCalendar.add(Calendar.DAY_OF_MONTH, dayDifference);

                if (nextTriggerTime == -1 || tempCalendar.getTimeInMillis() < nextTriggerTime) {
                    nextTriggerTime = tempCalendar.getTimeInMillis();
                }
            }

            if (nextTriggerTime != -1) {
                Intent intent = new Intent(context, ScheduleReceiver.class);
                intent.putExtra("PROFILE_ID", profileId);

                // Use ruleId as the request code so multiple rules don't overwrite each other's alarms
                PendingIntent pendingIntent = PendingIntent.getBroadcast(
                        context, (int) ruleId, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );

                AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                if (alarmManager != null) {
                    // Set an exact alarm that triggers even in Doze mode
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTriggerTime, pendingIntent);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
