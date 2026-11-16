package io.github.ithan2s.questgiver.quest;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TierRulesTest {
    private static final Map<Tier, Integer> T = Map.of(Tier.EASY, 0, Tier.MEDIUM, 5, Tier.HARD, 15,
            Tier.LEGENDARY, 30);

    @Test
    void tiersUnlockByCompletedQuests() {
        assertTrue(TierRules.unlocked(Tier.EASY, 0, T));
        assertFalse(TierRules.unlocked(Tier.MEDIUM, 4, T));
        assertTrue(TierRules.unlocked(Tier.MEDIUM, 5, T));
        assertFalse(TierRules.unlocked(Tier.LEGENDARY, 29, T));
        assertTrue(TierRules.unlocked(Tier.LEGENDARY, 30, T));
        assertTrue(TierRules.unlocked(Tier.BOUNTY, 0, T));
    }

    @Test
    void capUsesHighestPermission() {
        assertEquals(5, TierRules.activeCap(5, List.of()));
        assertEquals(8, TierRules.activeCap(5, List.of(3, 8, 6)));
        assertEquals(5, TierRules.activeCap(5, List.of(2)));
    }
}
