package io.github.ithan2s.questgiver.wander;

/**
 * The wandering trader's spawn odds (unit tested): each attempt succeeds with the current chance; a failure raises
 * the chance by a step up to a maximum, a success resets it to the starting chance.
 */
public final class WanderChance {
    private WanderChance() {
    }

    public static boolean succeeds(double chance, double roll) {
        return roll < chance;
    }

    public static double next(double current, boolean spawned, double start, double step, double max) {
        if (spawned) {
            return start;
        }
        return Math.min(max, current + step);
    }
}
