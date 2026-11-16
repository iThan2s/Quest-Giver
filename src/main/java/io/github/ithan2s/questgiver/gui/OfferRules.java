package io.github.ithan2s.questgiver.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Pure rules for the quest-giver's offer row (unit tested):
 * <ul>
 *   <li>a fixed number of slots (5); each accepted quest's slot is refilled with a new random quest,</li>
 *   <li>at most {@code maxSquad} squad-only quests at once, so solo quests are always on offer,</li>
 *   <li>while an event runs, one slot (the event slot) holds an event quest when one is available,</li>
 *   <li>event quests never appear outside the event slot; offers that stop being valid are replaced.</li>
 * </ul>
 */
public final class OfferRules {
    public record Candidate(String id, boolean squad, boolean event) {
    }

    private OfferRules() {
    }

    /**
     * @param current    previous offers by slot (null entries are empty slots); may be shorter or longer than slots
     * @param candidates every quest the player could be offered right now
     * @param eventSlot  index of the event slot, or -1 when no event is running
     */
    public static List<String> fill(List<String> current, int slots, List<Candidate> candidates, int maxSquad,
                                    int eventSlot, Random random) {
        Map<String, Candidate> byId = new HashMap<>();
        for (Candidate c : candidates) {
            byId.put(c.id(), c);
        }
        List<String> out = new ArrayList<>(slots);
        for (int i = 0; i < slots; i++) {
            out.add(current != null && i < current.size() ? current.get(i) : null);
        }
        Set<String> used = new HashSet<>();
        boolean eventAvailable = candidates.stream().anyMatch(Candidate::event);

        // 1. Drop invalid, duplicate or misplaced offers.
        for (int i = 0; i < slots; i++) {
            String id = out.get(i);
            Candidate c = id == null ? null : byId.get(id);
            // Event quests belong in the event slot only; a normal quest there makes way when an event quest exists.
            boolean misplaced = c != null && (i == eventSlot ? !c.event() && eventAvailable : c.event());
            if (c == null || used.contains(id) || misplaced) {
                out.set(i, null);
            } else {
                used.add(id);
            }
        }
        // 2. Enforce the squad limit.
        int squad = 0;
        for (int i = 0; i < slots; i++) {
            Candidate c = out.get(i) == null ? null : byId.get(out.get(i));
            if (c != null && c.squad()) {
                if (++squad > maxSquad) {
                    used.remove(c.id());
                    out.set(i, null);
                    squad--;
                }
            }
        }
        // 3. Fill empty slots: the event slot prefers event quests, the rest take normal quests.
        for (int i = 0; i < slots; i++) {
            if (out.get(i) != null) {
                continue;
            }
            Candidate pick = null;
            if (i == eventSlot) {
                pick = pick(candidates, used, squad, maxSquad, true, random);
            }
            if (pick == null) {
                pick = pick(candidates, used, squad, maxSquad, false, random);
            }
            if (pick != null) {
                out.set(i, pick.id());
                used.add(pick.id());
                if (pick.squad()) {
                    squad++;
                }
            }
        }
        return out;
    }

    private static Candidate pick(List<Candidate> candidates, Set<String> used, int squad, int maxSquad,
                                  boolean event, Random random) {
        List<Candidate> pool = new ArrayList<>();
        for (Candidate c : candidates) {
            if (c.event() == event && !used.contains(c.id()) && (!c.squad() || squad < maxSquad)) {
                pool.add(c);
            }
        }
        return pool.isEmpty() ? null : pool.get(random.nextInt(pool.size()));
    }

    /** Number of squad-only quests among the offers. */
    public static int squadCount(List<String> offers, List<Candidate> candidates) {
        Set<String> squadIds = new HashSet<>();
        for (Candidate c : candidates) {
            if (c.squad()) squadIds.add(c.id());
        }
        int n = 0;
        for (String id : offers) {
            if (id != null && squadIds.contains(id)) n++;
        }
        return n;
    }
}
