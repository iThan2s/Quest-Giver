package io.github.ithan2s.questgiver.squad;

import io.github.ithan2s.questgiver.progress.ActiveQuest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SquadTest {
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();

    private Squad squad() {
        Squad s = Squad.create("Raiders", a);
        s.members.add(b.toString());
        s.members.add(c.toString());
        return s;
    }

    @Test
    void creatorLeads() {
        Squad s = Squad.create("Raiders", a);
        assertTrue(s.isLeader(a));
        assertEquals(1, s.size());
    }

    @Test
    void leaderLeavingPromotesNextByJoinOrder() {
        Squad s = squad();
        Squad.Removal r = s.remove(a);
        assertTrue(r.wasLeader());
        assertEquals(b.toString(), r.newLeader());
        assertTrue(s.isLeader(b));
        assertFalse(s.isMember(a));
    }

    @Test
    void memberLeavingKeepsLeader() {
        Squad s = squad();
        Squad.Removal r = s.remove(b);
        assertFalse(r.wasLeader());
        assertNull(r.newLeader());
        assertTrue(s.isLeader(a));
    }

    @Test
    void leavingDropsYouFromLockedRosters() {
        Squad s = squad();
        ActiveQuest q = ActiveQuest.create("warden_slayer", "town", "Town", 0);
        q.squadId = s.id;
        q.participants = new ArrayList<>(s.members);
        s.active.add(q);
        s.remove(c);
        assertFalse(q.hasParticipant(c));
        assertTrue(q.hasParticipant(a));
        assertEquals(1, s.active.size());
    }

    @Test
    void lateJoinerIsNotOnTheLockedRoster() {
        Squad s = Squad.create("Raiders", a);
        ActiveQuest q = ActiveQuest.create("warden_slayer", "town", "Town", 0);
        q.participants = new ArrayList<>(s.members);
        s.active.add(q);
        s.members.add(b.toString());
        assertFalse(q.hasParticipant(b));
    }

    @Test
    void questWithNobodyLeftIsDropped() {
        Squad s = squad();
        ActiveQuest q = ActiveQuest.create("x", "town", "Town", 0);
        q.participants = new ArrayList<>(java.util.List.of(b.toString()));
        s.active.add(q);
        s.remove(b);
        assertTrue(s.active.isEmpty());
    }

    @Test
    void lastMemberLeavingEmptiesSquad() {
        Squad s = Squad.create("Solo", a);
        assertTrue(s.remove(a).empty());
    }
}
