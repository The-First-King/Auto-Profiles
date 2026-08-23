package com.mine.autoprofile.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import com.mine.autoprofile.models.Trigger;
import java.util.List;

@Dao
public interface TriggerDao {
    @Query("SELECT * FROM triggers WHERE type = :type")
    List<Trigger> getTriggersByType(String type);

    @Insert
    long insert(Trigger trigger);
}
