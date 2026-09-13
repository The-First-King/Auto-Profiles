package com.mine.autoprofiles.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.mine.autoprofiles.database.AppDatabase;
import com.mine.autoprofiles.models.FullRule;
import com.mine.autoprofiles.services.TriggerMonitorService;
import com.mine.autoprofiles.utils.AlarmHelper;
import java.util.List;
import java.util.concurrent.Executors;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            if (!com.mine.autoprofiles.utils.ProfileSwitcher.isMasterEnabled(context)) {
                Log.i("AutoProfile", "Master switch OFF - not restoring after: " + action);
                return;
            }

            // Restart cell-tower monitoring. From a receiver the app is in the
            // background, so startForegroundService() is required (API 26+);
            // BOOT_COMPLETED and MY_PACKAGE_REPLACED are exempt from the
            // Android 12 background-FGS-launch restrictions.
            Log.i("AutoProfile", "Starting TriggerMonitorService after: " + action);
            try {
                context.startForegroundService(
                        new Intent(context, TriggerMonitorService.class));
            } catch (Exception e) {
                Log.e("AutoProfile", "Failed to start monitor service on boot", e);
            }

            Log.i("AutoProfile", "Restoring scheduled alarms after: " + action);
            Executors.newSingleThreadExecutor().execute(() -> {
                AppDatabase db = AppDatabase.getInstance(context);
                List<FullRule> rules = db.ruleDao().getAllRulesWithDetails();

                for (FullRule fullRule : rules) {
                    if (fullRule.rule != null && fullRule.rule.isEnabled() &&
                        fullRule.trigger != null && "TIME".equals(fullRule.trigger.getType())) {

                        AlarmHelper.scheduleAlarm(context, fullRule.rule.getId(),
                                fullRule.rule.getProfileId(), fullRule.trigger.getValue());
                    }
                }
                Log.i("AutoProfile", "All alarms successfully restored.");
            });
        }
    }
}
