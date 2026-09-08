package com.mine.autoprofile.database;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import com.mine.autoprofile.models.Profile;
import com.mine.autoprofile.models.Rule;
import com.mine.autoprofile.models.Trigger;

@Database(entities = {Profile.class, Rule.class, Trigger.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase INSTANCE;

    public abstract ProfileDao profileDao();
    public abstract RuleDao ruleDao();
    public abstract TriggerDao triggerDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "auto_profiles.db").build();
                }
            }
        }
        return INSTANCE;
    }
}
