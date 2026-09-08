package com.mine.autoprofile.database;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.mine.autoprofile.models.Profile;
import com.mine.autoprofile.models.Rule;
import com.mine.autoprofile.models.Trigger;

@Database(entities = {Profile.class, Rule.class, Trigger.class}, version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase INSTANCE;

    public abstract ProfileDao profileDao();
    public abstract RuleDao ruleDao();
    public abstract TriggerDao triggerDao();

    /** v1 -> v2: rules gain an optional user-given name (location rules). */
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE rules ADD COLUMN name TEXT");
        }
    };

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "auto_profiles.db")
                            .addMigrations(MIGRATION_1_2)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
