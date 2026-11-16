package io.github.ithan2s.questgiver.progress;

import io.github.ithan2s.questgiver.QuestGiverPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.boss.DragonBattle;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ZombieVillager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/** Turns gameplay events into quest progress. Spawner and trial-spawner kills count like any other kill. */
public final class ProgressListener implements Listener {
    private record Placement(UUID player, Location location, long tick) {
    }

    private final QuestGiverPlugin plugin;
    /** Recent carved-pumpkin placements, to credit whoever built a snow or iron golem. */
    private final Deque<Placement> pumpkins = new ArrayDeque<>();
    private long tick;

    public ProgressListener(QuestGiverPlugin plugin) {
        this.plugin = plugin;
    }

    public void onTick() {
        tick++;
        while (!pumpkins.isEmpty() && tick - pumpkins.peekFirst().tick() > 40) {
            pumpkins.pollFirst();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || victim instanceof Player) {
            return;
        }
        boolean resummoned = false;
        if (victim.getType() == EntityType.ENDER_DRAGON) {
            DragonBattle battle = victim.getWorld().getEnderDragonBattle();
            resummoned = battle != null && battle.hasBeenPreviouslyKilled();
        }
        plugin.questService().onKill(killer, victim, resummoned);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        plugin.questService().onAdvancement(event.getPlayer(), event.getAdvancement().getKey());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (event.getBreeder() instanceof Player p) {
            plugin.questService().onBreed(p, event.getEntity().getType());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Material type = event.getBlockPlaced().getType();
        if (type == Material.CARVED_PUMPKIN || type == Material.JACK_O_LANTERN) {
            pumpkins.addLast(new Placement(event.getPlayer().getUniqueId(), event.getBlockPlaced().getLocation(), tick));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGolem(CreatureSpawnEvent event) {
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason != CreatureSpawnEvent.SpawnReason.BUILD_SNOWMAN
                && reason != CreatureSpawnEvent.SpawnReason.BUILD_IRONGOLEM) {
            return;
        }
        Location at = event.getLocation();
        Placement best = null;
        for (Placement p : pumpkins) {
            if (p.location().getWorld().equals(at.getWorld()) && p.location().distanceSquared(at) <= 9) {
                best = p;
            }
        }
        if (best != null) {
            Player builder = plugin.getServer().getPlayer(best.player());
            if (builder != null) {
                plugin.questService().onBuild(builder, event.getEntityType());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCure(EntityTransformEvent event) {
        if (event.getTransformReason() == EntityTransformEvent.TransformReason.CURED
                && event.getEntity() instanceof ZombieVillager zv && zv.getConversionPlayer() != null) {
            Player p = zv.getConversionPlayer().getPlayer();
            if (p != null) {
                plugin.questService().onCure(p);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        plugin.questService().onDeath(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                plugin.questService().onJoin(p);
                plugin.tracker().update(p);
            }
        }, 20L);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        plugin.tracker().remove(event.getPlayer());
        plugin.npcListener().forget(event.getPlayer().getUniqueId());
    }
}
