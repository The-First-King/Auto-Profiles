package com.mine.autoprofile.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import com.mine.autoprofile.models.Rule;
import com.mine.autoprofile.models.FullRule;
import java.util.List;

@Dao
public interface RuleDao {
    // Used by MainActivity and RuleAdapter to show the full UI list
    @Transaction
    @Query("SELECT * FROM rules")
    List<FullRule> getAllRulesWithDetails();

    // Restored: Used by your CellTowerReceiver to fetch active rules
    @Query("SELECT * FROM rules WHERE enabled = 1")
    List<Rule> getEnabledRules();

    @Insert
    long insert(Rule rule);
}
