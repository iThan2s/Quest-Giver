package io.github.ithan2s.questgiver.progress;

/**
 * "In one night" kill quests (unit tested). Night is world time 13000-23000. Every night has an index so kills from
 * an earlier night are discarded when the next night starts.
 */
public final class NightRules {
    public static final long NIGHT_START = 13000;
    public static final long NIGHT_END = 23000;

    private NightRules() {
    }

    public static boolean isNight(long timeOfDay) {
        long t = Math.floorMod(timeOfDay, 24000L);
        return t >= NIGHT_START && t < NIGHT_END;
    }

    /** Index of the night containing {@code fullTime} (only meaningful when it is night). */
    public static long nightIndex(long fullTime) {
        return Math.floorDiv(fullTime - NIGHT_START, 24000L);
    }

    /**
     * Progress after one more kill: resets to 1 when the stored night differs from the current one.
     *
     * @return new count
     */
    public static int countKill(String storedNight, String currentNight, int storedCount) {
        if (storedNight == null || !storedNight.equals(currentNight)) {
            return 1;
        }
        return storedCount + 1;
    }
}
