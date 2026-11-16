package io.github.ithan2s.questgiver.npc;

import io.github.ithan2s.questgiver.util.Keys;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Keeps quest-giver bodies (town and wandering) unhurtable and routes right-clicks to the right menu. */
public final class NpcListener implements Listener {
    private final Map<UUID, Long> lastClick = new HashMap<>();
    private BiConsumer<Player, Entity> townHandler = (p, e) -> { };
    private BiConsumer<Player, Entity> wandererHandler = (p, e) -> { };

    public void onTown(BiConsumer<Player, Entity> handler) {
        this.townHandler = handler;
    }

    public void onWanderer(BiConsumer<Player, Entity> handler) {
        this.wandererHandler = handler;
    }

    public static boolean isNpc(Entity e) {
        var pdc = e.getPersistentDataContainer();
        return pdc.has(Keys.GIVER) || pdc.has(Keys.WANDERER);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEntityEvent event) {
        handleClick(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        handleClick(event);
    }

    private void handleClick(PlayerInteractEntityEvent event) {
        Entity e = event.getRightClicked();
        if (!isNpc(e)) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        Long last = lastClick.get(player.getUniqueId());
        if (last != null && now - last < 400) {
            return;
        }
        lastClick.put(player.getUniqueId(), now);
        if (e.getPersistentDataContainer().has(Keys.GIVER)) {
            townHandler.accept(player, e);
        } else {
            wandererHandler.accept(player, e);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (isNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCombust(EntityCombustEvent event) {
        if (isNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTransform(EntityTransformEvent event) {
        if (isNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLeash(PlayerLeashEntityEvent event) {
        if (isNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPortal(EntityPortalEvent event) {
        if (isNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTrade(VillagerAcquireTradeEvent event) {
        if (isNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCareer(VillagerCareerChangeEvent event) {
        if (isNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    public void forget(UUID player) {
        lastClick.remove(player);
    }
}
