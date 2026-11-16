package io.github.ithan2s.questgiver.weekly;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class D20Test {
    private static final List<D20.TierRange> TIERS = List.of(
            new D20.TierRange("critical-fail", 1, 1), new D20.TierRange("base", 2, 9),
            new D20.TierRange("bonus", 10, 15), new D20.TierRange("big-bonus", 16, 19),
            new D20.TierRange("jackpot", 20, 20));

    @Test
    void naturalOneAlwaysFailsEvenWithStreak() {
        D20.Result r = D20.resolve(1, 5);
        assertTrue(r.criticalFail());
        assertEquals(1, r.total());
        assertEquals("critical-fail", D20.tier(r, TIERS));
    }

    @Test
    void tiersByRoll() {
        assertEquals("base", D20.tier(D20.resolve(2, 0), TIERS));
        assertEquals("base", D20.tier(D20.resolve(9, 0), TIERS));
        assertEquals("bonus", D20.tier(D20.resolve(10, 0), TIERS));
        assertEquals("bonus", D20.tier(D20.resolve(15, 0), TIERS));
        assertEquals("big-bonus", D20.tier(D20.resolve(16, 0), TIERS));
        assertEquals("big-bonus", D20.tier(D20.resolve(19, 0), TIERS));
        assertEquals("jackpot", D20.tier(D20.resolve(20, 0), TIERS));
    }

    @Test
    void streakBonusAddsAndCapsAtTwenty() {
        D20.Result r = D20.resolve(17, 5);
        assertFalse(r.criticalFail());
        assertEquals(20, r.total());
        assertEquals("jackpot", D20.tier(r, TIERS));
        assertEquals(12, D20.resolve(9, 3).total());
        assertEquals("bonus", D20.tier(D20.resolve(9, 1), TIERS));
    }

    @Test
    void twoWithBonusIsNotAFail() {
        D20.Result r = D20.resolve(2, 5);
        assertFalse(r.criticalFail());
        assertEquals("base", D20.tier(r, TIERS));
    }

    @Test
    void everyFaceHasATier() {
        for (int face = 1; face <= 20; face++) {
            for (int bonus = 0; bonus <= 5; bonus++) {
                assertTrue(D20.tier(D20.resolve(face, bonus), TIERS) != null);
            }
        }
    }

    @Test
    void rejectsImpossibleFaces() {
        assertThrows(IllegalArgumentException.class, () -> D20.resolve(0, 0));
        assertThrows(IllegalArgumentException.class, () -> D20.resolve(21, 0));
    }
}
