package com.mine.autoprofiles.models;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "triggers")
public class Trigger {
    @PrimaryKey(autoGenerate = true)
    private long id;

    @NonNull
    private String type; // e.g., "CELL_TOWER", "TIME"

    @NonNull
    private String value; // e.g., cell id or time string

    public Trigger(@NonNull String type, @NonNull String value) {
        this.type = type;
        this.value = value;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    @NonNull
    public String getType() { return type; }
    public void setType(@NonNull String type) { this.type = type; }

    @NonNull
    public String getValue() { return value; }
    public void setValue(@NonNull String value) { this.value = value; }
}
