package io.github.ithan2s.questgiver.quest;

import org.bukkit.Material;

import java.util.List;

/**
 * One quest (one level of a quest line counts as one quest).
 *
 * @param pool        "town" (or another giver pool) or "wandering"
 * @param event       seasonal event id, or null
 * @param line        quest line id shared by all levels, or null
 * @param level       level within the line (1 for single quests)
 * @param requires    quests that must be finished first (chains)
 * @param squadSize   minimum squad size for squad-only quests, 0 for quests anyone can take
 * @param bountyDays  per-player cooldown in days for repeatable bounties (dragon egg), 0 otherwise
 */
public record QuestDefinition(String id, String name, String description, String pool, String event, Tier tier,
                              Rarity rarity, String line, int level, List<String> requires, int squadSize,
                              Material icon, List<Objective> objectives, RewardSpec rewards, int bountyDays,
                              int timeLimitMinutes) {

    public static final String WANDERING = "wandering";

    public boolean isWandering() {
        return WANDERING.equals(pool);
    }

    public boolean isEvent() {
        return event != null;
    }

    public boolean isBounty() {
        return bountyDays > 0;
    }

    public boolean isSquadOnly() {
        return squadSize > 0;
    }

    public boolean hasFetch() {
        return objectives.stream().anyMatch(Objective::isFetch);
    }

    public boolean onlyFetch() {
        return !objectives.isEmpty() && objectives.stream().allMatch(Objective::isFetch);
    }
}
