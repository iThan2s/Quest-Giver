package io.github.ithan2s.questgiver.gui;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfferRulesTest {
    private static List<OfferRules.Candidate> solo(int n) {
        List<OfferRules.Candidate> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(new OfferRules.Candidate("solo" + i, false, false));
        return out;
    }

    private static List<OfferRules.Candidate> squad(int n) {
        List<OfferRules.Candidate> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(new OfferRules.Candidate("squad" + i, true, false));
        return out;
    }

    @Test
    void fillsFiveDistinctSlots() {
        List<String> offers = OfferRules.fill(null, 5, solo(20), 2, -1, new Random(1));
        assertEquals(5, offers.size());
        assertEquals(5, new HashSet<>(offers).size());
        assertTrue(offers.stream().allMatch(Objects::nonNull));
    }

    @RepeatedTest(50)
    void atMostTwoSquadQuests() {
        List<OfferRules.Candidate> c = new ArrayList<>(squad(10));
        c.addAll(solo(2));
        List<String> offers = OfferRules.fill(null, 5, c, 2, -1, new Random());
        assertTrue(OfferRules.squadCount(offers, c) <= 2);
        // Both solo quests are always on offer.
        assertTrue(offers.contains("solo0") && offers.contains("solo1"));
    }

    @Test
    void tooManySquadQuestsAreTrimmed() {
        List<OfferRules.Candidate> c = new ArrayList<>(squad(5));
        c.addAll(solo(5));
        List<String> current = List.of("squad0", "squad1", "squad2", "squad3", "squad4");
        List<String> offers = OfferRules.fill(current, 5, c, 2, -1, new Random(3));
        assertEquals(2, OfferRules.squadCount(offers, c));
        assertEquals("squad0", offers.get(0));
        assertEquals("squad1", offers.get(1));
    }

    @Test
    void keepsValidOffersAndReplacesAcceptedOnes() {
        List<OfferRules.Candidate> c = solo(10);
        List<String> current = Arrays.asList("solo1", null, "solo3", "solo4", "solo5");
        List<String> offers = OfferRules.fill(current, 5, c, 2, -1, new Random(2));
        assertEquals("solo1", offers.get(0));
        assertEquals("solo3", offers.get(2));
        assertTrue(offers.get(1) != null && !List.of("solo1", "solo3", "solo4", "solo5").contains(offers.get(1)));
    }

    @Test
    void offersNoLongerEligibleAreReplaced() {
        List<OfferRules.Candidate> c = solo(6);
        List<String> offers = OfferRules.fill(List.of("gone", "solo1", "solo1", "solo2", "solo3"), 5, c, 2, -1,
                new Random(4));
        assertEquals(5, new HashSet<>(offers).size());
        assertTrue(!offers.contains("gone"));
    }

    @Test
    void eventSlotHoldsAnEventQuest() {
        List<OfferRules.Candidate> c = new ArrayList<>(solo(10));
        c.add(new OfferRules.Candidate("winter1", false, true));
        c.add(new OfferRules.Candidate("winter2", false, true));
        List<String> offers = OfferRules.fill(List.of("solo0", "solo1", "solo2", "solo3", "solo4"), 5, c, 2, 4,
                new Random(5));
        assertTrue(offers.get(4).startsWith("winter"));
        long events = offers.stream().filter(id -> id.startsWith("winter")).count();
        assertEquals(1, events);
        assertEquals(List.of("solo0", "solo1", "solo2", "solo3"), offers.subList(0, 4));
    }

    @Test
    void eventQuestsNeverOutsideEventSlot() {
        List<OfferRules.Candidate> c = new ArrayList<>(solo(2));
        for (int i = 0; i < 5; i++) c.add(new OfferRules.Candidate("winter" + i, false, true));
        List<String> offers = OfferRules.fill(List.of("winter0", "winter1", "solo0", "solo1", "winter2"), 5, c, 2, 4,
                new Random(6));
        for (int i = 0; i < 4; i++) {
            assertTrue(offers.get(i) == null || offers.get(i).startsWith("solo"), "slot " + i + " = " + offers.get(i));
        }
        assertEquals("winter2", offers.get(4));
    }

    @Test
    void eventSlotFallsBackToNormalQuest() {
        List<String> offers = OfferRules.fill(null, 5, solo(10), 2, 4, new Random(7));
        assertTrue(offers.get(4).startsWith("solo"));
    }

    @Test
    void eventEndedClearsEventOffer() {
        // The event quest is no longer a candidate once the event ends.
        List<String> offers = OfferRules.fill(List.of("solo0", "solo1", "solo2", "solo3", "winter1"), 5, solo(10), 2,
                -1, new Random(8));
        assertTrue(offers.get(4).startsWith("solo"));
    }

    @Test
    void emptySlotsWhenNothingLeft() {
        List<String> offers = OfferRules.fill(null, 5, solo(3), 2, -1, new Random(9));
        assertEquals(3, offers.stream().filter(Objects::nonNull).count());
        assertNull(offers.get(3));
        assertNull(offers.get(4));
    }
}
