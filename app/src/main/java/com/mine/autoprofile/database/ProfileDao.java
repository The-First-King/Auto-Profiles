package com.mine.autoprofile.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.mine.autoprofile.models.Profile;

import java.util.List;

@Dao
public interface ProfileDao {

    @Insert
    void insert(Profile profile);

    @Update
    void update(Profile profile);

    @Delete
    void delete(Profile profile);

    @Query("SELECT * FROM profiles ORDER BY name ASC")
    LiveData<List<Profile>> getAllProfiles();

    @Query("SELECT * FROM profiles WHERE id = :profileId")
    LiveData<Profile> getProfileById(String profileId);

    @Query("SELECT * FROM profiles WHERE isActive = 1 LIMIT 1")
    LiveData<Profile> getActiveProfile();

    @Query("DELETE FROM profiles")
    void deleteAll();
}
