package io.github.ithan2s.questgiver.reward;

import io.github.ithan2s.questgiver.QuestGiverPlugin;
import io.github.ithan2s.questgiver.itemtag.ItemTags;
import io.github.ithan2s.questgiver.quest.ItemSpec;
import io.github.ithan2s.questgiver.quest.RewardSpec;
import io.github.ithan2s.questgiver.util.Items;
import io.github.ithan2s.questgiver.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/** Pays out money (Vault), XP levels, items and console commands. */
public final class RewardService {
    /** What a player actually received. */
    public record Paid(double money, double xp, List<ItemStack> items, double multiplier, double deathPenalty,
                       boolean repeat) {
        public int itemCount() {
            return items.stream().mapToInt(ItemStack::getAmount).sum();
        }

        public String itemSummary() {
            List<String> parts = new ArrayList<>();
            for (ItemStack i : items) {
                Component name = i.effectiveName();
                parts.add((i.getAmount() > 1 ? i.getAmount() + "x " : "") + Text.plain(name));
            }
            return String.join(", ", parts);
        }
    }

    private final QuestGiverPlugin plugin;
    private final Economy economy;

    public RewardService(QuestGiverPlugin plugin, Economy economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    public Economy economy() {
        return economy;
    }

    public double deathPenalty(int deaths) {
        FileConfiguration c = plugin.getConfig();
        if (!c.getBoolean("death-penalty.enabled", true)) {
            return 0;
        }
        return RewardMath.deathPenalty(deaths, c.getDouble("death-penalty.per-death", 0.10),
                c.getDouble("death-penalty.max", 0.50));
    }

    public double multiplier(int deaths, boolean repeat) {
        FileConfiguration c = plugin.getConfig();
        boolean enabled = c.getBoolean("death-penalty.enabled", true);
        return RewardMath.multiplier(enabled ? deaths : 0, c.getDouble("death-penalty.per-death", 0.10),
                c.getDouble("death-penalty.max", 0.50), repeat, c.getDouble("squad.repeat-multiplier", 0.5));
    }

    /**
     * Pays a reward. Money and XP are docked by the death penalty (and halved for squad repeats); loot never is.
     *
     * @param eventLore lore line added to every item (seasonal events), or null
     */
    public Paid grant(Player player, RewardSpec spec, int deaths, boolean repeat, String eventLore, String questId) {
        double mult = multiplier(deaths, repeat);
        double money = RewardMath.money(spec.money(), mult);
        double xp = RewardMath.xp(spec.xp(), mult);
        economy.deposit(player, money);
        giveXp(player, xp);
        List<ItemStack> items = createItems(spec.items(), eventLore);
        Items.give(player, items);
        runCommands(player, spec.commands(), questId);
        return new Paid(money, xp, items, mult, deathPenalty(deaths), repeat);
    }

    /** Builds, tags and (for events) lore-marks reward items. */
    public List<ItemStack> createItems(List<ItemSpec> specs, String eventLore) {
        Random random = ThreadLocalRandom.current();
        boolean tag = plugin.getConfig().getBoolean("item-tags.tag-rewards", true);
        List<ItemStack> out = new ArrayList<>();
        for (ItemSpec spec : specs) {
            for (ItemStack item : spec.create(random)) {
                if (eventLore != null && !eventLore.isEmpty()) {
                    item.editMeta(meta -> {
                        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
                        lore.add(Text.item(eventLore));
                        meta.lore(lore);
                    });
                }
                if (tag) {
                    ItemTags.tag(item, ItemTags.REWARD);
                }
                out.add(item);
            }
        }
        return out;
    }

    /** Adds levels on top of the current level; a fractional part is paid as points toward the next level. */
    public static void giveXp(Player player, double levels) {
        if (levels <= 0) {
            return;
        }
        int whole = (int) Math.floor(levels);
        if (whole > 0) {
            player.giveExpLevels(whole);
        }
        int points = RewardMath.fractionToPoints(levels - whole, player.getExpToLevel());
        if (points > 0) {
            player.giveExp(points, false);
        }
    }

    public void runCommands(Player player, List<String> commands, String questId) {
        for (String cmd : commands) {
            String line = cmd.replace("{player}", player.getName())
                    .replace("{uuid}", player.getUniqueId().toString())
                    .replace("{quest}", questId == null ? "" : questId);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line.startsWith("/") ? line.substring(1) : line);
        }
    }
}
