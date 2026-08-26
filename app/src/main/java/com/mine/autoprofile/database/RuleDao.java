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
    @Transaction
    @Query("SELECT * FROM rules")
    List<FullRule> getAllRulesWithDetails();

    @Insert
    long insert(Rule rule);
}
