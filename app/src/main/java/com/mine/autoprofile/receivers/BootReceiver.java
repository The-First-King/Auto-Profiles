package com.mine.autoprofile.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.mine.autoprofile.database.AppDatabase;
import com.mine.autoprofile.models.FullRule;
import com.mine.autoprofile.utils.AlarmHelper;
import java.util.List;
import java.util.concurrent.Executors;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.i("AutoProfile", "Device rebooted. Restoring scheduled alarms...");
            
            Executors.newSingleThreadExecutor().execute(() -> {
                AppDatabase db = AppDatabase.getInstance(context);
                List<FullRule> rules = db.ruleDao().getAllRulesWithDetails();
                
                for (FullRule fullRule : rules) {
                    // Changed nullptr to standard Java null
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
