package com.mine.autoprofiles.models;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

import java.util.UUID;

@Entity(
    tableName = "triggers",
    foreignKeys = @ForeignKey(
        entity = Rule.class,
        parentColumns = "id",
        childColumns = "ruleId",
        onDelete = ForeignKey.CASCADE
    )
)
public class Trigger {

    public static final int TYPE_TIME = 1;
    public static final int TYPE_CELL_TOWER = 2;
    public static final int TYPE_CALENDAR = 3;
    public static final int TYPE_RECURRING = 4;

    @PrimaryKey
    private String id;
    
    private String ruleId;
    private int type;
    private String triggerData;
    private long createdAt;

    public Trigger() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = System.currentTimeMillis();
    }

    public Trigger(String ruleId, int type, String triggerData) {
        this();
        this.ruleId = ruleId;
        this.type = type;
        this.triggerData = triggerData;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getTriggerData() {
        return triggerData;
    }

    public void setTriggerData(String triggerData) {
        this.triggerData = triggerData;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
