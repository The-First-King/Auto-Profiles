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
                    if (fullRule.getRule().isEnabled() && "TIME".equals(fullRule.getTrigger().getType())) {
                        long ruleId = fullRule.getRule().getId();
                        long profileId = fullRule.getRule().getProfileId();
                        String triggerValue = fullRule.getTrigger().getValue();
                        
                        AlarmHelper.scheduleAlarm(context, ruleId, profileId, triggerValue);
                    }
                }
                Log.i("AutoProfile", "All alarms successfully restored.");
            });
        }
    }
}
