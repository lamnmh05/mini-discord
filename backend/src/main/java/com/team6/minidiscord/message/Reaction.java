package com.team6.minidiscord.message;

import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;

public class Reaction {
    public String emoji;
    public List<ObjectId> userIds = new ArrayList<>();
}
