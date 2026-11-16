package io.github.ithan2s.questgiver.reward;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Checks quest payouts against the design's caps when quests load: nothing may pay more than the dragon egg bounty
 * ($5,000 / 20 levels by default), and town quests should sit inside their tier's money and level ranges.
 */
public final class RewardCapValidator {
    /** Inclusive money and level range for a tier. */
    public record Range(double minMoney, double maxMoney, int minXp, int maxXp) {
    }

    public record Entry(String id, String tier, String pool, boolean bounty, double money, int xp) {
    }

    private RewardCapValidator() {
    }

    public static List<String> validate(List<Entry> quests, double capMoney, int capXp, Map<String, Range> tierRanges,
                                        boolean checkTierRanges) {
        List<String> warnings = new ArrayList<>();
        for (Entry q : quests) {
            if (!q.bounty() && (q.money() > capMoney || q.xp() > capXp)) {
                warnings.add(String.format("Quest '%s' pays $%,.0f / %d levels, more than the dragon egg bounty cap "
                        + "($%,.0f / %d levels).", q.id(), q.money(), q.xp(), capMoney, capXp));
            }
            if (!checkTierRanges || q.bounty() || !"town".equals(q.pool())) {
                continue;
            }
            Range r = tierRanges.get(q.tier());
            if (r == null) {
                continue;
            }
            if (q.money() < r.minMoney() || q.money() > r.maxMoney() || q.xp() < r.minXp() || q.xp() > r.maxXp()) {
                warnings.add(String.format("Quest '%s' (%s) pays $%,.0f / %d levels, outside the %s range "
                                + "$%,.0f-%,.0f / %d-%d levels.", q.id(), q.tier(), q.money(), q.xp(), q.tier(),
                        r.minMoney(), r.maxMoney(), r.minXp(), r.maxXp()));
            }
        }
        return warnings;
    }
}
