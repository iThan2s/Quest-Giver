package io.github.ithan2s.questgiver.extras;

import io.github.ithan2s.questgiver.QuestGiverPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Creepers, zombies, skeletons and piglins killed by a player drop their head 1 time in 10 (configurable per mob).
 * Vanilla charged-creeper head drops are untouched. These heads are valid for head quests.
 */
public final class HeadDropListener implements Listener {
    private static final Map<EntityType, Material> HEADS = Map.of(
            EntityType.CREEPER, Material.CREEPER_HEAD,
            EntityType.ZOMBIE, Material.ZOMBIE_HEAD,
            EntityType.SKELETON, Material.SKELETON_SKULL,
            EntityType.PIGLIN, Material.PIGLIN_HEAD,
            EntityType.WITHER_SKELETON, Material.WITHER_SKELETON_SKULL,
            EntityType.ENDER_DRAGON, Material.DRAGON_HEAD);

    private final QuestGiverPlugin plugin;

    public HeadDropListener(QuestGiverPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity e = event.getEntity();
        if (!plugin.getConfig().getBoolean("head-drops.enabled", true) || e.getKiller() == null) {
            return;
        }
        Material head = HEADS.get(e.getType());
        ConfigurationSection chances = plugin.getConfig().getConfigurationSection("head-drops.chances");
        if (head == null || chances == null) {
            return;
        }
        String key = e.getType().getKey().getKey().toLowerCase(Locale.ROOT);
        double chance = chances.getDouble(key, 0);
        if (chance > 0 && ThreadLocalRandom.current().nextDouble() < chance) {
            event.getDrops().add(new ItemStack(head));
        }
    }
}
