package io.github.ithan2s.questgiver.weekly;

import io.github.ithan2s.questgiver.util.Durations;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreakRulesTest {
    private static final long WEEK = 7 * Durations.DAY;
    private static final long WINDOW = 14 * Durations.DAY;
    private static final long T0 = 1_800_000_000_000L;

    @Test
    void firstClaimIsAvailable() {
        assertTrue(StreakRules.canClaim(0, T0, WEEK));
    }

    @Test
    void sevenDaysFromLastClaim() {
        assertFalse(StreakRules.canClaim(T0, T0 + WEEK - 1, WEEK));
        assertTrue(StreakRules.canClaim(T0, T0 + WEEK, WEEK));
        assertEquals(T0 + WEEK, StreakRules.nextClaimAt(T0, WEEK));
    }

    @Test
    void consecutiveWeeksBuildStreak() {
        int s = StreakRules.nextStreak(0, 0, T0, WINDOW);
        assertEquals(0, s);
        s = StreakRules.nextStreak(T0, s, T0 + WEEK, WINDOW);
        assertEquals(1, s);
        s = StreakRules.nextStreak(T0 + WEEK, s, T0 + 2 * WEEK + Durations.DAY, WINDOW);
        assertEquals(2, s);
    }

    @Test
    void missingAWeekResets() {
        assertEquals(0, StreakRules.nextStreak(T0, 4, T0 + WINDOW + 1, WINDOW));
        assertEquals(0, StreakRules.currentStreak(T0, 4, T0 + WINDOW + 1, WINDOW));
        assertEquals(4, StreakRules.currentStreak(T0, 4, T0 + WEEK, WINDOW));
    }

    @Test
    void bonusCapsAtFive() {
        assertEquals(0, StreakRules.bonus(0, 5));
        assertEquals(3, StreakRules.bonus(3, 5));
        assertEquals(5, StreakRules.bonus(9, 5));
    }
}
