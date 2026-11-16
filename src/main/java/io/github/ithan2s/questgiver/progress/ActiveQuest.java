package io.github.ithan2s.questgiver.progress;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One accepted quest. Solo quests live in the owner's {@link PlayerData}; squad quests live on the squad, with the
 * roster locked into {@link #participants} at the moment of acceptance. Serialised with Gson, so only plain types.
 */
public final class ActiveQuest {
    /** Short unique id of this instance. */
    public String uid;
    public String questId;
    /** Giver that handed out the quest ("wandering" for wanderers). */
    public String giverId;
    /** Name shown in tooltips (town giver's or wanderer's display name, with colour codes). */
    public String giverName;
    public long acceptedAt;
    /** Real-time deadline in epoch millis, 0 when untimed. Keeps running while the player is offline. */
    public long expiresAt;
    /** When every non-fetch objective was finished (0 while still in progress). Stops wandering timers. */
    public long readyAt;
    /** Objective index -> progress count. */
    public Map<String, Integer> progress = new HashMap<>();
    /** Objective index -> biomes already visited (visit-biome objectives). */
    public Map<String, List<String>> visited = new HashMap<>();
    /** Objective index -> "world:nightIndex" for once-per-night kill objectives. */
    public Map<String, String> nightKey = new HashMap<>();
    /** Player uuid -> deaths while this quest was active (each death docks that player's payout). */
    public Map<String, Integer> deaths = new HashMap<>();
    /** Squad id, or null for solo quests. */
    public String squadId;
    /** Locked roster (uuid strings). Null for solo quests. */
    public List<String> participants;
    /** Event run ("winterfest:2026") for event quests, else null. */
    public String eventRun;

    public static ActiveQuest create(String questId, String giverId, String giverName, long now) {
        ActiveQuest q = new ActiveQuest();
        q.uid = UUID.randomUUID().toString().substring(0, 8);
        q.questId = questId;
        q.giverId = giverId;
        q.giverName = giverName;
        q.acceptedAt = now;
        return q;
    }

    public boolean isSquad() {
        return squadId != null;
    }

    public boolean isTimed() {
        return expiresAt > 0;
    }

    public int progress(int objective) {
        return progress.getOrDefault(Integer.toString(objective), 0);
    }

    public void setProgress(int objective, int value) {
        progress.put(Integer.toString(objective), value);
    }

    public List<String> visited(int objective) {
        return visited.computeIfAbsent(Integer.toString(objective), k -> new ArrayList<>());
    }

    public int deaths(UUID player) {
        return deaths.getOrDefault(player.toString(), 0);
    }

    public void addDeath(UUID player) {
        deaths.merge(player.toString(), 1, Integer::sum);
    }

    public boolean hasParticipant(UUID player) {
        return participants != null && participants.contains(player.toString());
    }
}
