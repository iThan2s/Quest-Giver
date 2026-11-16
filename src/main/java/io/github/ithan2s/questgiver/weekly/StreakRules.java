package io.github.ithan2s.questgiver.weekly;

/**
 * Weekly reward timing (unit tested). The reward is claimable once every cooldown, counted from each player's
 * last claim. Claiming again within the streak window continues the streak; missing it resets the streak.
 */
public final class StreakRules {
    private StreakRules() {
    }

    public static boolean canClaim(long lastClaim, long now, long cooldownMs) {
        return lastClaim <= 0 || now - lastClaim >= cooldownMs;
    }

    public static long nextClaimAt(long lastClaim, long cooldownMs) {
        return lastClaim <= 0 ? 0 : lastClaim + cooldownMs;
    }

    /**
     * Streak after claiming now: 0 on the first claim or after a missed week, otherwise one more than before.
     */
    public static int nextStreak(long lastClaim, int previousStreak, long now, long windowMs) {
        if (lastClaim <= 0 || now - lastClaim > windowMs) {
            return 0;
        }
        return previousStreak + 1;
    }

    /** +1 per consecutive week, capped (default +5). */
    public static int bonus(int streak, int maxBonus) {
        return Math.max(0, Math.min(streak, maxBonus));
    }

    /** Streak to show before claiming: it resets to 0 if the window has already passed. */
    public static int currentStreak(long lastClaim, int streak, long now, long windowMs) {
        return lastClaim > 0 && now - lastClaim <= windowMs ? streak : 0;
    }
}
