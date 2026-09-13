package com.mine.autoprofiles.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;
import com.mine.autoprofiles.models.Rule;
import com.mine.autoprofiles.models.FullRule;
import java.util.List;

@Dao
public interface RuleDao {
    // Used by MainActivity and RuleAdapter to show the full UI list
    @Transaction
    @Query("SELECT * FROM rules")
    List<FullRule> getAllRulesWithDetails();

    @Query("SELECT * FROM rules WHERE enabled = 1")
    List<Rule> getEnabledRules();

    @Insert
    long insert(Rule rule);

    @Update
    void update(Rule rule);

    @Query("DELETE FROM rules WHERE id = :ruleId")
    void deleteById(long ruleId);

    // Marks a rule as finished once its schedule has no upcoming events
    @Query("UPDATE rules SET enabled = 0 WHERE id = :ruleId")
    void disableById(long ruleId);
}
