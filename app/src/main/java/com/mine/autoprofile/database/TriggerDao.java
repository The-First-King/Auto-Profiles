package com.mine.autoprofile.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.mine.autoprofile.models.Trigger;

import java.util.List;

@Dao
public interface TriggerDao {

    @Insert
    void insert(Trigger trigger);

    @Update
    void update(Trigger trigger);

    @Delete
    void delete(Trigger trigger);

    @Query("SELECT * FROM triggers WHERE ruleId = :ruleId")
    LiveData<List<Trigger>> getTriggersByRuleId(String ruleId);

    @Query("SELECT * FROM triggers WHERE id = :triggerId")
    LiveData<Trigger> getTriggerById(String triggerId);

    @Query("SELECT * FROM triggers WHERE type = :type")
    LiveData<List<Trigger>> getTriggersByType(int type);

    @Query("DELETE FROM triggers WHERE ruleId = :ruleId")
    void deleteTriggersForRule(String ruleId);

    @Query("DELETE FROM triggers")
    void deleteAll();
}
