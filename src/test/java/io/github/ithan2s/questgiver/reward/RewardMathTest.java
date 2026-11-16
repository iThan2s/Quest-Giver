package io.github.ithan2s.questgiver.reward;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RewardMathTest {
    @Test
    void noDeathsNoPenalty() {
        assertEquals(0.0, RewardMath.deathPenalty(0, 0.10, 0.50));
    }

    @Test
    void tenPercentPerDeath() {
        assertEquals(0.10, RewardMath.deathPenalty(1, 0.10, 0.50), 1e-9);
        assertEquals(0.30, RewardMath.deathPenalty(3, 0.10, 0.50), 1e-9);
    }

    @Test
    void penaltyCapsAtFiftyPercent() {
        assertEquals(0.50, RewardMath.deathPenalty(5, 0.10, 0.50), 1e-9);
        assertEquals(0.50, RewardMath.deathPenalty(12, 0.10, 0.50), 1e-9);
    }

    @Test
    void moneyDockedAndRoundedDown() {
        double m = RewardMath.multiplier(2, 0.10, 0.50, false, 0.5);
        assertEquals(0.8, m, 1e-9);
        assertEquals(1200.0, RewardMath.money(1500, m));
        assertEquals(133.33, RewardMath.money(166.67, m));
    }

    @Test
    void squadRepeatHalvesAndStacksWithDeaths() {
        assertEquals(0.5, RewardMath.multiplier(0, 0.10, 0.50, true, 0.5), 1e-9);
        // 2 deaths (-20%) then halved: 0.8 * 0.5 = 0.4
        assertEquals(0.4, RewardMath.multiplier(2, 0.10, 0.50, true, 0.5), 1e-9);
        assertEquals(1400.0, RewardMath.money(3500, 0.4));
        assertEquals(6.0, RewardMath.xp(15, 0.4), 1e-9);
    }

    @Test
    void maxPenaltyPlusRepeatIsQuarter() {
        assertEquals(0.25, RewardMath.multiplier(9, 0.10, 0.50, true, 0.5), 1e-9);
    }

    @Test
    void fractionalXpBecomesPoints() {
        assertEquals(0.9, RewardMath.xp(1, 0.9), 1e-9);
        assertEquals(15, RewardMath.fractionToPoints(0.5, 30));
        assertEquals(0, RewardMath.fractionToPoints(0, 30));
    }

    @Test
    void zeroBaseStaysZero() {
        assertEquals(0.0, RewardMath.money(0, 1));
        assertEquals(0.0, RewardMath.xp(0, 1));
    }
}
