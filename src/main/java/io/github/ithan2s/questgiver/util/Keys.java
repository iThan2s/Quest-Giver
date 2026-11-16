package io.github.ithan2s.questgiver.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** PersistentDataContainer keys used on entities and items. */
public final class Keys {
    /** Entity tag: id of the town quest-giver this entity represents. */
    public static NamespacedKey GIVER;
    /** Entity tag: id of the wandering quest-giver this entity represents. */
    public static NamespacedKey WANDERER;
    /** Item tag: where an item came from ("creative", "command", "reward"). Tagged items are refused at turn-in. */
    public static NamespacedKey ORIGIN;
    /** Item tag: mob type stored on a picked-up spawner. */
    public static NamespacedKey SPAWNER_TYPE;
    /** Item tag: marks menu icons so they can never leave a menu. */
    public static NamespacedKey MENU_ITEM;

    private Keys() {
    }

    public static void init(Plugin plugin) {
        GIVER = new NamespacedKey(plugin, "giver");
        WANDERER = new NamespacedKey(plugin, "wanderer");
        ORIGIN = new NamespacedKey(plugin, "origin");
        SPAWNER_TYPE = new NamespacedKey(plugin, "spawner_type");
        MENU_ITEM = new NamespacedKey(plugin, "menu_item");
    }
}
