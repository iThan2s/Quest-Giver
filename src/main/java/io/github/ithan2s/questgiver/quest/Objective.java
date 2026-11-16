package io.github.ithan2s.questgiver.quest;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;

import java.util.List;
import java.util.Set;

/** One condition of a quest. A quest is done when every objective is done. */
public sealed interface Objective {
    int amount();

    String label();

    /** Fetch objectives are checked against the inventory at turn-in instead of being tracked. */
    default boolean isFetch() {
        return false;
    }

    record Advancement(NamespacedKey key, String label) implements Objective {
        public int amount() {
            return 1;
        }
    }

    /**
     * @param types       mob types that count; empty with {@code anyHostile} means any hostile mob
     * @param night       kills only count at night, and the count resets when a new night starts
     * @param captain     only raid/patrol captains (ominous-banner carriers)
     * @param resummoned  only Ender Dragons whose fight had already been won before (re-summoned)
     */
    record Kill(Set<EntityType> types, boolean anyHostile, int amount, boolean night, boolean captain,
                boolean resummoned, String label) implements Objective {
    }

    /**
     * @param distinct  count different item types (e.g. 3 different pottery sherds), one of each is taken
     * @param withBees  bee nests/hives must contain bees
     */
    record Fetch(Set<Material> materials, int amount, boolean distinct, boolean withBees, String label)
            implements Objective {
        public boolean isFetch() {
            return true;
        }
    }

    record Visit(List<NamespacedKey> biomes, String label) implements Objective {
        public int amount() {
            return biomes.size();
        }
    }

    record Breed(EntityType type, int amount, String label) implements Objective {
    }

    record Build(EntityType type, int amount, String label) implements Objective {
    }

    record Cure(int amount, String label) implements Objective {
    }

    /** Finish N other quests of the same seasonal event (in the same event year). */
    record CompleteEvent(String event, int amount, String label) implements Objective {
    }
}
