package com.mine.autoprofile.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import com.mine.autoprofile.models.Rule;
import java.util.List;

@Dao
public interface RuleDao {
    @Query("SELECT * FROM rules WHERE enabled = 1")
    List<Rule> getEnabledRules();

    @Insert
    long insert(Rule rule);
}
