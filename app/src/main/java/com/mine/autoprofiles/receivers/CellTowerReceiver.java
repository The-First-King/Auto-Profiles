package com.mine.autoprofile.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.mine.autoprofile.database.AppDatabase;
import com.mine.autoprofile.models.Rule;
import com.mine.autoprofile.models.Trigger;
import com.mine.autoprofile.services.ProfileManagerService;
import java.util.List;
import java.util.concurrent.Executors;

public class CellTowerReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Evaluate active rules against cell changes asynchronously
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(context);
            List<Rule> enabledRules = db.ruleDao().getEnabledRules();
            for (Rule rule : enabledRules) {
                Trigger trigger = db.triggerDao().getTriggersByType("CELL_TOWER").stream()
                        .filter(t -> t.getId() == rule.getTriggerId())
                        .findFirst()
                        .orElse(null);

                if (trigger != null) {
                    String profileName = db.profileDao().getProfileById(rule.getProfileId()).getName();
                    ProfileManagerService.switchProfile(context, profileName);
                    break;
                }
            }
        });
    }
}
