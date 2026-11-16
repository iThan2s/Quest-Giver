package io.github.ithan2s.questgiver.itemtag;

import io.github.ithan2s.questgiver.util.Keys;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Marks items that must never count for fetch quests: items from creative mode, from /give-style commands, and items
 * the plugin itself handed out as rewards.
 */
public final class ItemTags {
    public static final String CREATIVE = "creative";
    public static final String COMMAND = "command";
    public static final String REWARD = "reward";

    private ItemTags() {
    }

    public static void tag(ItemStack item, String origin) {
        if (item == null || item.isEmpty() || isTagged(item)) {
            return;
        }
        item.editPersistentDataContainer(pdc -> pdc.set(Keys.ORIGIN, PersistentDataType.STRING, origin));
    }

    public static boolean isTagged(ItemStack item) {
        return item != null && !item.isEmpty() && item.getPersistentDataContainer().has(Keys.ORIGIN);
    }

    public static String origin(ItemStack item) {
        return item == null || item.isEmpty() ? null
                : item.getPersistentDataContainer().get(Keys.ORIGIN, PersistentDataType.STRING);
    }
}
