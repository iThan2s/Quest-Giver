package io.github.ithan2s.questgiver.progress;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything the plugin remembers about one player. Stored per player (never on an NPC), so removing or respawning
 * a quest-giver resets nothing. Serialised to JSON with Gson; uuids are kept as strings.
 */
public final class PlayerData {
    public String uuid;
    public String name;
    /** Completion key (quest id, or "id@year" for event quests) -> first completion time. */
    public Map<String, Long> completed = new HashMap<>();
    /** Total quests completed (each level counts; repeats in squads and bounties count again). Leaderboard value. */
    public int completedCount;
    /** Money earned from quests only (not weekly rewards). Leaderboard tie-breaker. */
    public double questEarnings;
    /** Cosmetic reputation per giver id. */
    public Map<String, Integer> reputation = new HashMap<>();
    /** Solo quests (town and wandering). Squad quests live on the squad. */
    public List<ActiveQuest> active = new ArrayList<>();
    /** Giver id -> offered quest ids by slot (null/empty = empty slot). */
    public Map<String, List<String>> offers = new HashMap<>();
    /** Quest id -> time the abandon cooldown ends. */
    public Map<String, Long> abandonCooldowns = new HashMap<>();
    public long weeklyLastClaim;
    /** Consecutive weeks claimed before the most recent claim chain broke. */
    public int weeklyStreak;
    /** A weekly roll that was made but not yet paid (player left or server stopped mid-animation); 0 = none. */
    public int weeklyPendingNatural;
    public int weeklyPendingBonus;
    public long dragonLastTurnIn;
    /** History page only shows quests finished after this (set by a full admin reset). */
    public long historySince;
    /** Uid of the quest shown in the tracker, or null for automatic. */
    public String tracked;

    public transient boolean dirty;

    public static PlayerData fresh(UUID uuid, String name) {
        PlayerData d = new PlayerData();
        d.uuid = uuid.toString();
        d.name = name;
        return d;
    }

    public UUID uuid() {
        return UUID.fromString(uuid);
    }

    public void markDirty() {
        dirty = true;
    }

    /** Repairs nulls left by older JSON. */
    public PlayerData normalize() {
        if (completed == null) completed = new HashMap<>();
        if (reputation == null) reputation = new HashMap<>();
        if (active == null) active = new ArrayList<>();
        if (offers == null) offers = new HashMap<>();
        if (abandonCooldowns == null) abandonCooldowns = new HashMap<>();
        for (ActiveQuest q : active) {
            normalize(q);
        }
        return this;
    }

    public static void normalize(ActiveQuest q) {
        if (q.progress == null) q.progress = new HashMap<>();
        if (q.visited == null) q.visited = new HashMap<>();
        if (q.nightKey == null) q.nightKey = new HashMap<>();
        if (q.deaths == null) q.deaths = new HashMap<>();
    }

    public int totalReputation() {
        return reputation.values().stream().mapToInt(Integer::intValue).sum();
    }
}
