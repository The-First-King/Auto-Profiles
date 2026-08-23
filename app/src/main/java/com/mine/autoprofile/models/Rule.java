package com.mine.autoprofile.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "rules")
public class Rule {
    @PrimaryKey(autoGenerate = true)
    private long id;

    private long profileId;
    private long triggerId;
    private boolean enabled;

    public Rule(long profileId, long triggerId, boolean enabled) {
        this.profileId = profileId;
        this.triggerId = triggerId;
        this.enabled = enabled;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getProfileId() { return profileId; }
    public void setProfileId(long profileId) { this.profileId = profileId; }

    public long getTriggerId() { return triggerId; }
    public void setTriggerId(long triggerId) { this.triggerId = triggerId; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
