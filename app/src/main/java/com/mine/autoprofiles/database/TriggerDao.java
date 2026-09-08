package com.mine.autoprofiles.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import com.mine.autoprofiles.models.Trigger;
import java.util.List;

@Dao
public interface TriggerDao {
    @Query("SELECT * FROM triggers WHERE type = :type")
    List<Trigger> getTriggersByType(String type);

    @Insert
    long insert(Trigger trigger);

    @Update
    void update(Trigger trigger);

    @Query("DELETE FROM triggers WHERE id = :triggerId")
    void deleteById(long triggerId);
}
