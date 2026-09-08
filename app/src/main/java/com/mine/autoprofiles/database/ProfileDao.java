package com.mine.autoprofiles.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import com.mine.autoprofiles.models.Profile;
import java.util.List;

@Dao
public interface ProfileDao {
    @Query("SELECT * FROM profiles")
    List<Profile> getAllProfiles();

    @Query("SELECT * FROM profiles WHERE id = :id")
    Profile getProfileById(long id);

    @Insert
    long insert(Profile profile);
}
