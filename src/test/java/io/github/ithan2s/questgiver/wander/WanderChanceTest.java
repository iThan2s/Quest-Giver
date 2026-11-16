package io.github.ithan2s.questgiver.wander;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WanderChanceTest {
    @Test
    void risesByStepUpToMax() {
        double c = 0.25;
        c = WanderChance.next(c, false, 0.25, 0.25, 0.75);
        assertEquals(0.50, c, 1e-9);
        c = WanderChance.next(c, false, 0.25, 0.25, 0.75);
        assertEquals(0.75, c, 1e-9);
        c = WanderChance.next(c, false, 0.25, 0.25, 0.75);
        assertEquals(0.75, c, 1e-9);
    }

    @Test
    void successResets() {
        assertEquals(0.25, WanderChance.next(0.75, true, 0.25, 0.25, 0.75), 1e-9);
    }

    @Test
    void rollBelowChanceSucceeds() {
        assertTrue(WanderChance.succeeds(0.25, 0.10));
        assertFalse(WanderChance.succeeds(0.25, 0.25));
        assertFalse(WanderChance.succeeds(0.25, 0.90));
    }
}
