package com.mine.autoprofile.models;

import androidx.room.Embedded;
import androidx.room.Relation;

public class FullRule {
    @Embedded
    public Rule rule;

    @Relation(parentColumn = "profileId", entityColumn = "id")
    public Profile profile;

    @Relation(parentColumn = "triggerId", entityColumn = "id")
    public Trigger trigger;
}
