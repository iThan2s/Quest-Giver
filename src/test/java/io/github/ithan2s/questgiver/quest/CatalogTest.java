package io.github.ithan2s.questgiver.quest;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sanity checks on the bundled quests.yml against the design plan's catalog. */
class CatalogTest {
    private static Map<String, Map<String, Object>> quests;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void load() throws Exception {
        try (InputStream in = CatalogTest.class.getResourceAsStream("/quests.yml")) {
            Map<String, Object> root = new Yaml().load(in);
            quests = (Map<String, Map<String, Object>>) (Map<?, ?>) root.get("quests");
        }
    }

    private static String pool(Map<String, Object> q) {
        return (String) q.getOrDefault("pool", "town");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> rewards(Map<String, Object> q) {
        return (Map<String, Object>) q.get("rewards");
    }

    @Test
    void countsMatchTheDesign() {
        long town = quests.values().stream().filter(q -> pool(q).equals("town") && !q.containsKey("event")).count();
        long wandering = quests.values().stream().filter(q -> pool(q).equals("wandering")).count();
        long event = quests.values().stream().filter(q -> "winterfest".equals(q.get("event"))).count();
        assertEquals(108, town, "100 town quests + 8 head quests");
        assertEquals(30, wandering);
        assertEquals(12, event);
    }

    @Test
    void nothingPaysMoreThanTheDragonEgg() {
        for (var e : quests.entrySet()) {
            Map<String, Object> r = rewards(e.getValue());
            double money = ((Number) r.get("money")).doubleValue();
            int xp = ((Number) r.get("xp")).intValue();
            assertTrue(money <= 5000 && xp <= 20, e.getKey());
            if (!"bounty".equals(e.getValue().get("tier"))) {
                assertTrue(money < 5000 || xp < 20, e.getKey() + " ties the bounty");
            }
        }
        Map<String, Object> bounty = rewards(quests.get("dragon_egg_bounty"));
        assertEquals(5000, ((Number) bounty.get("money")).intValue());
        assertEquals(20, ((Number) bounty.get("xp")).intValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void objectivesUseKnownTypes() {
        Set<String> types = Set.of("advancement", "kill", "fetch", "visit", "breed", "build", "cure", "complete");
        for (var e : quests.entrySet()) {
            List<Map<String, Object>> objectives = (List<Map<String, Object>>) e.getValue().get("objectives");
            assertTrue(objectives != null && !objectives.isEmpty(), e.getKey());
            for (Map<String, Object> o : objectives) {
                assertTrue(types.contains(o.get("type")), e.getKey() + ": " + o.get("type"));
            }
        }
    }

    @Test
    void thirtyThreeAdvancementQuestsAndOneLevelOnly() {
        long n = 0;
        for (var e : quests.entrySet()) {
            Object first = ((List<?>) e.getValue().get("objectives")).getFirst();
            if ("advancement".equals(((Map<?, ?>) first).get("type"))) {
                n++;
                assertTrue(!e.getValue().containsKey("line"), e.getKey() + " is an advancement and must be one level");
            }
        }
        assertEquals(33, n);
    }

    @Test
    void questLinesHaveContiguousLevels() {
        Map<String, List<Integer>> lines = new TreeMap<>();
        for (var q : quests.values()) {
            if (q.containsKey("line")) {
                lines.computeIfAbsent((String) q.get("line"), k -> new ArrayList<>()).add((Integer) q.get("level"));
            }
        }
        for (var e : lines.entrySet()) {
            List<Integer> levels = new ArrayList<>(e.getValue());
            levels.sort(Integer::compare);
            for (int i = 0; i < levels.size(); i++) {
                assertEquals(i + 1, levels.get(i), e.getKey());
            }
        }
        assertEquals(List.of(1, 2, 3, 4), lines.get("skeleton_hunter"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void chainsPointAtRealQuests() {
        for (var e : quests.entrySet()) {
            for (String req : (List<String>) e.getValue().getOrDefault("requires", List.of())) {
                assertTrue(quests.containsKey(req), e.getKey() + " requires " + req);
            }
        }
        assertEquals(List.of("we_need_to_go_deeper"), quests.get("a_terrible_fortress").get("requires"));
        assertEquals(List.of("a_terrible_fortress"), quests.get("blaze_breaker_1").get("requires"));
    }

    @Test
    void squadQuestsAreMarked() {
        Map<String, Integer> squads = new HashMap<>();
        for (var e : quests.entrySet()) {
            if (e.getValue().containsKey("squad")) squads.put(e.getKey(), (Integer) e.getValue().get("squad"));
            boolean named = ((String) e.getValue().get("name")).contains("(Squad)");
            assertEquals(named, e.getValue().containsKey("squad"), e.getKey());
        }
        assertEquals(7, squads.size());
        assertTrue(squads.values().stream().allMatch(n -> n >= 3));
    }
}
