package io.github.ithan2s.questgiver.itemtag;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tags items obtained from the creative inventory or through item-granting commands (/give, /i, /item, ...).
 * Commands don't say which items they created, so the inventories of online players are snapshotted before the
 * command runs and every stack that appeared or grew by the next tick is tagged (plus items dropped that tick, which
 * is where /give puts overflow).
 */
public final class ItemTagListener implements Listener {
    private final Plugin plugin;
    private boolean tagCreative = true;
    private boolean tagCommands = true;
    private Set<String> watched = new HashSet<>();
    private boolean watchingDrops;

    public ItemTagListener(Plugin plugin) {
        this.plugin = plugin;
    }

    public void configure(boolean tagCreative, boolean tagCommands, List<String> commands) {
        this.tagCreative = tagCreative;
        this.tagCommands = tagCommands;
        this.watched = new HashSet<>();
        for (String c : commands) {
            watched.add(c.toLowerCase(Locale.ROOT).replaceFirst("^/", ""));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreative(InventoryCreativeEvent event) {
        if (!tagCreative || !(event.getWhoClicked() instanceof Player p) || p.getGameMode() != GameMode.CREATIVE) {
            return;
        }
        ItemStack cursor = event.getCursor();
        if (!cursor.isEmpty() && !ItemTags.isTagged(cursor)) {
            ItemStack tagged = cursor.clone();
            ItemTags.tag(tagged, ItemTags.CREATIVE);
            event.setCursor(tagged);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        watch(event.getMessage());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        watch(event.getCommand());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (watchingDrops) {
            ItemStack stack = event.getEntity().getItemStack();
            ItemTags.tag(stack, ItemTags.COMMAND);
            event.getEntity().setItemStack(stack);
        }
    }

    private void watch(String commandLine) {
        if (!tagCommands) {
            return;
        }
        String label = commandLine.trim().replaceFirst("^/", "").split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        String bare = label.contains(":") ? label.substring(label.indexOf(':') + 1) : label;
        if (!watched.contains(label) && !watched.contains(bare)) {
            return;
        }
        Map<UUID, ItemStack[]> before = new HashMap<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            ItemStack[] contents = p.getInventory().getContents();
            ItemStack[] copy = new ItemStack[contents.length];
            for (int i = 0; i < contents.length; i++) {
                copy[i] = contents[i] == null ? null : contents[i].clone();
            }
            before.put(p.getUniqueId(), copy);
        }
        watchingDrops = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            watchingDrops = false;
            for (Map.Entry<UUID, ItemStack[]> e : before.entrySet()) {
                Player p = Bukkit.getPlayer(e.getKey());
                if (p == null) {
                    continue;
                }
                ItemStack[] now = p.getInventory().getContents();
                ItemStack[] old = e.getValue();
                boolean changed = false;
                for (int i = 0; i < now.length && i < old.length; i++) {
                    ItemStack n = now[i];
                    if (n == null || n.isEmpty() || ItemTags.isTagged(n)) {
                        continue;
                    }
                    ItemStack o = old[i];
                    boolean appeared = o == null || o.isEmpty() || !o.isSimilar(n);
                    boolean grew = !appeared && n.getAmount() > o.getAmount();
                    if (appeared || grew) {
                        ItemTags.tag(n, ItemTags.COMMAND);
                        now[i] = n;
                        changed = true;
                    }
                }
                if (changed) {
                    p.getInventory().setContents(now);
                }
            }
        });
    }
}
