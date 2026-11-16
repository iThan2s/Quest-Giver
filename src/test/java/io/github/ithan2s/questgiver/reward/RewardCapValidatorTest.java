package io.github.ithan2s.questgiver.reward;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardCapValidatorTest {
    private static final Map<String, RewardCapValidator.Range> RANGES = Map.of(
            "easy", new RewardCapValidator.Range(100, 300, 1, 2),
            "legendary", new RewardCapValidator.Range(2000, 3500, 10, 15));

    @Test
    void bountyMayHitTheCap() {
        var e = new RewardCapValidator.Entry("dragon_egg_bounty", "bounty", "town", true, 5000, 20);
        assertTrue(RewardCapValidator.validate(List.of(e), 5000, 20, RANGES, true).isEmpty());
    }

    @Test
    void questAboveCapWarns() {
        var e = new RewardCapValidator.Entry("greedy", "legendary", "town", false, 6000, 12);
        List<String> w = RewardCapValidator.validate(List.of(e), 5000, 20, RANGES, true);
        assertEquals(2, w.size()); // over the cap and outside the legendary range
    }

    @Test
    void xpAboveCapWarns() {
        var e = new RewardCapValidator.Entry("xp", "legendary", "wandering", false, 3000, 25);
        assertEquals(1, RewardCapValidator.validate(List.of(e), 5000, 20, RANGES, true).size());
    }

    @Test
    void wanderingQuestsSkipTierRanges() {
        var e = new RewardCapValidator.Entry("bone_collector", "easy", "wandering", false, 400, 2);
        assertTrue(RewardCapValidator.validate(List.of(e), 5000, 20, RANGES, true).isEmpty());
    }

    @Test
    void townQuestOutsideTierRangeWarnsOnlyWhenEnabled() {
        var e = new RewardCapValidator.Entry("cheap", "easy", "town", false, 50, 1);
        assertEquals(1, RewardCapValidator.validate(List.of(e), 5000, 20, RANGES, true).size());
        assertTrue(RewardCapValidator.validate(List.of(e), 5000, 20, RANGES, false).isEmpty());
    }
}
