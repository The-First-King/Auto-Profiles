package com.mine.autoprofiles.models;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

import java.util.UUID;

@Entity(
    tableName = "rules",
    foreignKeys = @ForeignKey(
        entity = Profile.class,
        parentColumns = "id",
        childColumns = "profileId",
        onDelete = ForeignKey.CASCADE
    )
)
public class Rule {

    @PrimaryKey
    private String id;
    
    private String name;
    private String profileId;
    private boolean isEnabled;
    private int priority;
    private long createdAt;
    private long updatedAt;

    public Rule() {
        this.id = UUID.randomUUID().toString();
        this.isEnabled = true;
        this.priority = 0;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public Rule(String name, String profileId) {
        this();
        this.name = name;
        this.profileId = profileId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.updatedAt = System.currentTimeMillis();
    }

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
        this.updatedAt = System.currentTimeMillis();
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public void setEnabled(boolean enabled) {
        isEnabled = enabled;
        this.updatedAt = System.currentTimeMillis();
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
        this.updatedAt = System.currentTimeMillis();
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }
}
