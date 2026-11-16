package io.github.ithan2s.questgiver.weekly;

import java.util.List;

/**
 * Roll for initiative (unit tested): a fair d20 plus the streak bonus, capped at 20. A natural 1 is always a
 * critical fail, whatever the bonus. The total picks the reward tier, so a big streak can reach the jackpot.
 */
public final class D20 {
    public record Result(int natural, int bonus, int total, boolean criticalFail) {
    }

    public record TierRange(String id, int min, int max) {
        boolean contains(int roll) {
            return roll >= min && roll <= max;
        }
    }

    private D20() {
    }

    public static Result resolve(int natural, int bonus) {
        if (natural < 1 || natural > 20) {
            throw new IllegalArgumentException("A d20 shows 1-20, not " + natural);
        }
        if (natural == 1) {
            return new Result(1, bonus, 1, true);
        }
        return new Result(natural, bonus, Math.min(20, natural + Math.max(0, bonus)), false);
    }

    /** The tier for a result; a critical fail always uses the tier containing 1. */
    public static String tier(Result r, List<TierRange> tiers) {
        int roll = r.criticalFail() ? 1 : r.total();
        for (TierRange t : tiers) {
            if (t.contains(roll)) {
                return t.id();
            }
        }
        return null;
    }
}
