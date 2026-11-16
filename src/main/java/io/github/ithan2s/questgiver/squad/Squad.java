package io.github.ithan2s.questgiver.squad;

import io.github.ithan2s.questgiver.progress.ActiveQuest;
import io.github.ithan2s.questgiver.progress.PlayerData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A squad. Persisted as JSON; squad quests (with their locked rosters and shared progress) live here. */
public final class Squad {
    public String id;
    public String name;
    public String leader;
    /** Members in join order (the leader is always first); used to promote the next member when the leader leaves. */
    public List<String> members = new ArrayList<>();
    public List<ActiveQuest> active = new ArrayList<>();

    public transient boolean dirty;
    /** Pending invites: invitee -> expiry (not persisted). */
    public transient Map<UUID, Long> invites = new HashMap<>();

    public static Squad create(String name, UUID leader) {
        Squad s = new Squad();
        s.id = UUID.randomUUID().toString().substring(0, 12);
        s.name = name;
        s.leader = leader.toString();
        s.members.add(leader.toString());
        return s;
    }

    public Squad normalize() {
        if (members == null) members = new ArrayList<>();
        if (active == null) active = new ArrayList<>();
        if (invites == null) invites = new HashMap<>();
        active.forEach(PlayerData::normalize);
        return this;
    }

    public boolean isLeader(UUID uuid) {
        return leader != null && leader.equals(uuid.toString());
    }

    public boolean isMember(UUID uuid) {
        return members.contains(uuid.toString());
    }

    public List<UUID> memberIds() {
        return members.stream().map(UUID::fromString).toList();
    }

    public int size() {
        return members.size();
    }

    public void markDirty() {
        dirty = true;
    }
}
