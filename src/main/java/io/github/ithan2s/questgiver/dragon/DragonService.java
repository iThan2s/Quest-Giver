package io.github.ithan2s.questgiver.dragon;

import io.github.ithan2s.questgiver.QuestGiverPlugin;
import io.github.ithan2s.questgiver.config.Messages;
import io.github.ithan2s.questgiver.gui.ConfirmMenu;
import io.github.ithan2s.questgiver.gui.GiverMenu;
import io.github.ithan2s.questgiver.itemtag.ItemTags;
import io.github.ithan2s.questgiver.npc.GiverDefinition;
import io.github.ithan2s.questgiver.progress.PlayerData;
import io.github.ithan2s.questgiver.progress.QuestService;
import io.github.ithan2s.questgiver.quest.QuestDefinition;
import io.github.ithan2s.questgiver.util.Durations;
import io.github.ithan2s.questgiver.util.Items;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.boss.DragonBattle;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Dragon egg features: re-killed Ender Dragons leave a new egg on the exit portal (vanilla only gives one), and the
 * permanent dragon egg bounty at the town quest-giver with a per-player cooldown and a confirmation prompt.
 */
public final class DragonService implements Listener {
    private final QuestGiverPlugin plugin;

    public DragonService(QuestGiverPlugin plugin) {
        this.plugin = plugin;
    }

    private Messages msg() {
        return plugin.messages();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDragonDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon dragon)) {
            return;
        }
        World world = dragon.getWorld();
        DragonBattle battle = world.getEnderDragonBattle();
        // On the first kill vanilla places the egg itself; this only handles re-summoned dragons.
        if (battle == null || !battle.hasBeenPreviouslyKilled()) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() >= plugin.getConfig().getDouble("dragon-egg.extra-drop-chance", 1.0)) {
            return;
        }
        // The exit portal appears when the death animation ends (about 10 seconds).
        Bukkit.getScheduler().runTaskLater(plugin, () -> placeEgg(world, battle), 220L);
    }

    private void placeEgg(World world, DragonBattle battle) {
        Location portal = battle.getEndPortalLocation();
        if (portal == null) {
            portal = new Location(world, 0, world.getHighestBlockYAt(0, 0), 0);
        }
        Block top = world.getHighestBlockAt(portal.getBlockX(), portal.getBlockZ());
        Block above = top.getRelative(0, 1, 0);
        if (above.getType().isAir() && top.getType() != Material.DRAGON_EGG) {
            above.setType(Material.DRAGON_EGG);
        } else {
            world.dropItemNaturally(above.getLocation().add(0.5, 0.5, 0.5), new ItemStack(Material.DRAGON_EGG));
        }
        for (Player p : world.getPlayers()) {
            msg().send(p, "bounty.egg-dropped");
        }
    }

    /** The bounty quest for this giver's pool (dragon_egg_bounty in the bundled quests.yml). */
    public QuestDefinition bounty(GiverDefinition giver) {
        for (QuestDefinition q : plugin.quests().all()) {
            if (q.isBounty() && q.pool().equals(giver.pool)) {
                return q;
            }
        }
        return null;
    }

    private long cooldownLeft(PlayerData d, QuestDefinition q) {
        return Durations.remaining(d.dragonLastTurnIn + q.bountyDays() * Durations.DAY, System.currentTimeMillis());
    }

    public ItemStack button(Player p, QuestDefinition q) {
        PlayerData d = plugin.data().get(p);
        long left = cooldownLeft(d, q);
        List<String> lore = new ArrayList<>();
        lore.add(msg().raw("menu.description", "description", q.description()));
        lore.add("");
        lore.add(msg().raw("menu.rewards"));
        for (String line : plugin.questService().rewardLines(q, 0, false)) {
            lore.add(msg().raw("menu.reward-prefix") + line);
        }
        lore.add("");
        lore.add(left > 0 ? msg().raw("bounty.cooldown", "time", Durations.format(left)) : msg().raw("bounty.click"));
        return Items.icon(Material.DRAGON_EGG, msg().raw("bounty.name", "quest", q.name()), lore);
    }

    public void turnIn(Player p, GiverDefinition giver, QuestDefinition q) {
        PlayerData d = plugin.data().get(p);
        long left = cooldownLeft(d, q);
        if (left > 0) {
            msg().send(p, "bounty.on-cooldown", "time", Durations.format(left));
            plugin.questService().play(p, "effects.deny.sound");
            return;
        }
        QuestService qs = plugin.questService();
        if (!qs.hasFetchItems(p, q)) {
            plugin.givers().say(p, giver, "empty-handed", "player", p.getName(), "quest", q.name());
            boolean hasTagged = Items.count(p, s -> s.getType() == Material.DRAGON_EGG && ItemTags.isTagged(s)) > 0;
            msg().send(p, hasTagged ? "bounty.egg-refused" : "bounty.no-egg");
            qs.play(p, "effects.deny.sound");
            return;
        }
        ConfirmMenu.ask(msg(), p, msg().raw("bounty.confirm-title"), button(p, q), () -> {
            // Re-check: the inventory may have changed while the dialog was open.
            if (cooldownLeft(plugin.data().get(p), q) > 0 || !qs.hasFetchItems(p, q)) {
                msg().send(p, "bounty.no-egg");
                return;
            }
            Items.remove(p, s -> s.getType() == Material.DRAGON_EGG && !ItemTags.isTagged(s), 1);
            PlayerData data = plugin.data().get(p);
            data.dragonLastTurnIn = System.currentTimeMillis();
            data.markDirty();
            plugin.givers().say(p, giver, "turn-in", "player", p.getName(), "quest", q.name());
            qs.completeFor(p, q, 0, giver.id, giver.name, null, null);
            GiverMenu.open(plugin, p, giver);
        }, () -> GiverMenu.open(plugin, p, giver));
    }

    /** Adds the bounty button to the giver menu. */
    public void decorate(GiverMenu menu, Player p, GiverDefinition giver) {
        QuestDefinition q = bounty(giver);
        if (q != null) {
            menu.button(GiverMenu.BOUNTY, button(p, q), () -> turnIn(p, giver, q));
        }
    }
}
