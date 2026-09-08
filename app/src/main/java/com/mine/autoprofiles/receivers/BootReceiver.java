package com.mine.autoprofiles.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.mine.autoprofiles.database.AppDatabase;
import com.mine.autoprofiles.models.FullRule;
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
                Log.i("AutoProfile", "Master switch OFF - not restoring alarms after: " + action);
                return;
            }
            Log.i("AutoProfile", "Restoring scheduled alarms after: " + action);
            
            Executors.newSingleThreadExecutor().execute(() -> {
                AppDatabase db = AppDatabase.getInstance(context);
                List<FullRule> rules = db.ruleDao().getAllRulesWithDetails();
                
                for (FullRule fullRule : rules) {
                    if (fullRule.rule != null && fullRule.rule.isEnabled() && 
                        fullRule.trigger != null && "TIME".equals(fullRule.trigger.getType())) {
                        
                        long ruleId = fullRule.rule.getId();
                        long profileId = fullRule.rule.getProfileId();
                        String triggerValue = fullRule.trigger.getValue();
                        
                        AlarmHelper.scheduleAlarm(context, ruleId, profileId, triggerValue);
                    }
                }
                Log.i("AutoProfile", "All alarms successfully restored.");
            });
        }
    }
}
