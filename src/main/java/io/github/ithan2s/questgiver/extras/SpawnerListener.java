package io.github.ithan2s.questgiver.extras;

import io.github.ithan2s.questgiver.QuestGiverPlugin;
import io.github.ithan2s.questgiver.util.Keys;
import io.github.ithan2s.questgiver.util.Text;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;

/**
 * Silk Touch spawners: an iron, diamond or netherite pickaxe with Silk Touch drops the spawner with its mob type;
 * any other pickaxe (or no Silk Touch) breaks it normally with no drop. Placed spawners keep their mob type.
 */
public final class SpawnerListener implements Listener {
    private final QuestGiverPlugin plugin;

    public SpawnerListener(QuestGiverPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean allowedTool(ItemStack tool) {
        if (tool == null || tool.isEmpty() || !tool.containsEnchantment(Enchantment.SILK_TOUCH)) {
            return false;
        }
        List<String> allowed = plugin.getConfig().getStringList("spawners.allowed-pickaxes");
        if (allowed.isEmpty()) {
            allowed = List.of("IRON_PICKAXE", "DIAMOND_PICKAXE", "NETHERITE_PICKAXE");
        }
        return allowed.stream().anyMatch(m -> m.equalsIgnoreCase(tool.getType().name()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!plugin.getConfig().getBoolean("spawners.enabled", true) || block.getType() != Material.SPAWNER
                || event.getPlayer().getGameMode() == GameMode.CREATIVE
                || !allowedTool(event.getPlayer().getInventory().getItemInMainHand())) {
            return;
        }
        if (!(block.getState() instanceof CreatureSpawner spawner)) {
            return;
        }
        EntityType type = spawner.getSpawnedType();
        ItemStack item = new ItemStack(Material.SPAWNER);
        item.editMeta(BlockStateMeta.class, meta -> {
            meta.setBlockState(spawner);
            if (type != null) {
                meta.getPersistentDataContainer().set(Keys.SPAWNER_TYPE, PersistentDataType.STRING,
                        type.getKey().toString());
                meta.lore(List.of(Text.item(plugin.messages().raw("spawner.lore", "mob",
                        Text.pretty(type.getKey().getKey())))));
            }
        });
        event.setExpToDrop(0);
        event.setDropItems(false);
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), item);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack hand = event.getItemInHand();
        if (hand.getType() != Material.SPAWNER) {
            return;
        }
        String key = hand.getPersistentDataContainer().get(Keys.SPAWNER_TYPE, PersistentDataType.STRING);
        if (key == null) {
            return;
        }
        NamespacedKey nk = NamespacedKey.fromString(key.toLowerCase(Locale.ROOT));
        EntityType type = nk == null ? null : Registry.ENTITY_TYPE.get(nk);
        if (type == null) {
            return;
        }
        Block block = event.getBlockPlaced();
        // Vanilla strips spawner data for non-operators, so set the type once the block exists.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (block.getType() == Material.SPAWNER && block.getState() instanceof CreatureSpawner s) {
                s.setSpawnedType(type);
                s.update(true, false);
            }
        });
    }
}
