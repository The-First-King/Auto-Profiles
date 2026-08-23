package com.mine.autoprofile.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.mine.autoprofile.models.Rule;

import java.util.List;

@Dao
public interface RuleDao {

    @Insert
    void insert(Rule rule);

    @Update
    void update(Rule rule);

    @Delete
    void delete(Rule rule);

    @Query("SELECT * FROM rules WHERE profileId = :profileId ORDER BY priority ASC")
    LiveData<List<Rule>> getRulesByProfileId(String profileId);

    @Query("SELECT * FROM rules WHERE id = :ruleId")
    LiveData<Rule> getRuleById(String ruleId);

    @Query("SELECT * FROM rules WHERE isEnabled = 1 ORDER BY priority ASC")
    LiveData<List<Rule>> getEnabledRules();

    @Query("DELETE FROM rules")
    void deleteAll();
}
