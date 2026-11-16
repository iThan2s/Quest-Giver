package io.github.ithan2s.questgiver.quest;

import java.util.Map;

/** Difficulty unlocks (unit tested): a tier opens once the player has finished enough quests. */
public final class TierRules {
    private TierRules() {
    }

    public static boolean unlocked(Tier tier, int completed, Map<Tier, Integer> thresholds) {
        if (tier == Tier.BOUNTY) {
            return true;
        }
        return completed >= thresholds.getOrDefault(tier, 0);
    }

    /** The cap on active quests: the highest questgiver.limit.N the player has, else the configured default. */
    public static int activeCap(int configuredDefault, Iterable<Integer> permissionLimits) {
        int cap = configuredDefault;
        for (int n : permissionLimits) {
            cap = Math.max(cap, n);
        }
        return cap;
    }
}
