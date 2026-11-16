package io.github.ithan2s.questgiver.progress;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NightRulesTest {
    @Test
    void nightIsBetween13000And23000() {
        assertFalse(NightRules.isNight(6000));
        assertTrue(NightRules.isNight(13000));
        assertTrue(NightRules.isNight(18000));
        assertFalse(NightRules.isNight(23000));
        assertTrue(NightRules.isNight(24000 * 5 + 14000));
    }

    @Test
    void sameNightAcrossMidnightHasSameIndex() {
        long day = 24000L * 7;
        assertEquals(NightRules.nightIndex(day + 13500), NightRules.nightIndex(day + 22900));
        // 23999 and 24000+1000 are not night; next night differs.
        assertNotEquals(NightRules.nightIndex(day + 13500), NightRules.nightIndex(day + 24000 + 13500));
    }

    @Test
    void countResetsOnNewNight() {
        assertEquals(1, NightRules.countKill(null, "world:5", 0));
        assertEquals(8, NightRules.countKill("world:5", "world:5", 7));
        assertEquals(1, NightRules.countKill("world:5", "world:6", 19));
        assertEquals(1, NightRules.countKill("world:5", "other:5", 19));
    }
}
