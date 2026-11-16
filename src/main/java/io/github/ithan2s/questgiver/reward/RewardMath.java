package io.github.ithan2s.questgiver.reward;

/** Pure payout math (unit tested). Loot is never docked; only money and XP go through here. */
public final class RewardMath {
    private RewardMath() {
    }

    /** Fraction docked for deaths: perDeath per death, capped at max (defaults 10% and 50%). */
    public static double deathPenalty(int deaths, double perDeath, double max) {
        if (deaths <= 0 || perDeath <= 0) {
            return 0;
        }
        return Math.min(Math.max(0, max), deaths * perDeath);
    }

    /**
     * Combined multiplier for money and XP. Squad members repeating a quest they already finished get
     * {@code repeatMultiplier} (0.5 by default); it stacks multiplicatively with the death penalty.
     */
    public static double multiplier(int deaths, double perDeath, double maxPenalty, boolean repeat,
                                    double repeatMultiplier) {
        double m = 1.0 - deathPenalty(deaths, perDeath, maxPenalty);
        if (repeat) {
            m *= repeatMultiplier;
        }
        return Math.max(0, m);
    }

    /** Money rounded down to the cent. */
    public static double money(double base, double multiplier) {
        if (base <= 0) {
            return 0;
        }
        return Math.floor(base * multiplier * 100 + 1e-9) / 100.0;
    }

    /** XP levels may become fractional (e.g. 0.9 of a level); the fraction is paid as experience points. */
    public static double xp(int baseLevels, double multiplier) {
        if (baseLevels <= 0) {
            return 0;
        }
        return Math.round(baseLevels * multiplier * 1000) / 1000.0;
    }

    /** Points worth {@code fraction} of the next level, given the points needed for it. */
    public static int fractionToPoints(double fraction, int pointsForNextLevel) {
        if (fraction <= 0) {
            return 0;
        }
        return (int) Math.round(fraction * pointsForNextLevel);
    }
}
