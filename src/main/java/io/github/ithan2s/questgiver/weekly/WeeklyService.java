package io.github.ithan2s.questgiver.weekly;

import io.github.ithan2s.questgiver.QuestGiverPlugin;
import io.github.ithan2s.questgiver.config.Messages;
import io.github.ithan2s.questgiver.gui.GiverMenu;
import io.github.ithan2s.questgiver.npc.GiverDefinition;
import io.github.ithan2s.questgiver.progress.PlayerData;
import io.github.ithan2s.questgiver.quest.ItemSpec;
import io.github.ithan2s.questgiver.reward.RewardService;
import io.github.ithan2s.questgiver.storage.Storage;
import io.github.ithan2s.questgiver.util.Durations;
import io.github.ithan2s.questgiver.util.Items;
import io.github.ithan2s.questgiver.util.Money;
import io.github.ithan2s.questgiver.util.Text;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * "Collect your weekly rewards": once every 7 days from the last claim, a base reward plus a d20 roll animated in
 * the title with clicking sounds. Streak bonus +1 per consecutive week (max +5); a natural 1 still fails.
 */
public final class WeeklyService {
    private final QuestGiverPlugin plugin;

    public WeeklyService(QuestGiverPlugin plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    private Messages msg() {
        return plugin.messages();
    }

    public long cooldownMs() {
        return cfg().getLong("weekly.cooldown-days", 7) * Durations.DAY;
    }

    private long windowMs() {
        return cfg().getLong("weekly.streak.window-days", 14) * Durations.DAY;
    }

    private int maxBonus() {
        return cfg().getInt("weekly.streak.max-bonus", 5);
    }

    public boolean available(PlayerData d) {
        return StreakRules.canClaim(d.weeklyLastClaim, System.currentTimeMillis(), cooldownMs());
    }

    /** Bonus the next claim would get. */
    public int nextBonus(PlayerData d) {
        long now = System.currentTimeMillis();
        return StreakRules.bonus(StreakRules.nextStreak(d.weeklyLastClaim, d.weeklyStreak, now, windowMs()),
                maxBonus());
    }

    public ItemStack button(Player p) {
        PlayerData d = plugin.data().get(p);
        long now = System.currentTimeMillis();
        int streak = StreakRules.currentStreak(d.weeklyLastClaim, d.weeklyStreak, now, windowMs());
        if (available(d)) {
            return Items.icon(Material.CHEST, msg().raw("weekly.button-ready"),
                    msg().list("weekly.button-ready-lore", "streak", streak, "bonus", nextBonus(d)));
        }
        long left = Durations.remaining(StreakRules.nextClaimAt(d.weeklyLastClaim, cooldownMs()), now);
        return Items.icon(Material.ENDER_CHEST, msg().raw("weekly.button-wait"),
                msg().list("weekly.button-wait-lore", "time", Durations.format(left), "streak", streak));
    }

    public void claim(Player p, GiverDefinition giver) {
        PlayerData d = plugin.data().get(p);
        long now = System.currentTimeMillis();
        if (d.weeklyPendingNatural > 0) {
            payPending(p, giver);
            return;
        }
        if (!available(d)) {
            long left = Durations.remaining(StreakRules.nextClaimAt(d.weeklyLastClaim, cooldownMs()), now);
            msg().send(p, "weekly.not-yet", "time", Durations.format(left));
            return;
        }
        int streak = StreakRules.nextStreak(d.weeklyLastClaim, d.weeklyStreak, now, windowMs());
        int bonus = StreakRules.bonus(streak, maxBonus());
        int natural = ThreadLocalRandom.current().nextInt(1, 21);
        // Record the claim and the roll before the animation, so leaving mid-roll can't reroll or lose it.
        d.weeklyLastClaim = now;
        d.weeklyStreak = streak;
        d.weeklyPendingNatural = natural;
        d.weeklyPendingBonus = bonus;
        d.markDirty();
        plugin.data().save(d);
        p.closeInventory();
        animate(p, natural, bonus, giver);
    }

    private void animate(Player p, int natural, int bonus, GiverDefinition giver) {
        int duration = Math.max(10, cfg().getInt("weekly.animation.ticks", 50));
        new BukkitRunnable() {
            int tick = 0;
            int nextFlip = 0;
            int gap = 1;

            @Override
            public void run() {
                if (!p.isOnline()) {
                    cancel(); // Paid on next login from the stored roll.
                    return;
                }
                if (tick >= duration) {
                    cancel();
                    payPending(p, giver);
                    return;
                }
                if (tick >= nextFlip) {
                    int face = ThreadLocalRandom.current().nextInt(1, 21);
                    p.showTitle(Title.title(Text.color(msg().raw("weekly.rolling-title", "roll", face)),
                            Text.color(msg().raw("weekly.rolling-subtitle")),
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(600), Duration.ZERO)));
                    sound(p, "weekly.animation.tick-sound", 1f + tick / (float) duration);
                    nextFlip = tick + gap;
                    gap = 1 + tick * 6 / duration; // slows down as the die settles
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Pays the stored roll (end of animation, or on login after leaving mid-roll). */
    public void payPending(Player p, GiverDefinition giver) {
        PlayerData d = plugin.data().get(p);
        if (d.weeklyPendingNatural <= 0) {
            return;
        }
        D20.Result r = D20.resolve(d.weeklyPendingNatural, d.weeklyPendingBonus);
        d.weeklyPendingNatural = 0;
        d.weeklyPendingBonus = 0;
        d.markDirty();
        String tierId = D20.tier(r, tiers());
        ConfigurationSection tier = tierId == null ? null : cfg().getConfigurationSection("weekly.tiers." + tierId);
        ConfigurationSection base = cfg().getConfigurationSection("weekly.base");

        double money = (base == null ? 0 : base.getDouble("money")) + (tier == null ? 0 : tier.getDouble("money"));
        int xp = (base == null ? 0 : base.getInt("xp")) + (tier == null ? 0 : tier.getInt("xp"));
        List<ItemStack> items = new ArrayList<>();
        items.addAll(items(base));
        items.addAll(items(tier));
        plugin.rewards().economy().deposit(p, money);
        RewardService.giveXp(p, xp);
        Items.give(p, items);
        plugin.data().save(d);

        String tierName = msg().raw("weekly.tier." + (tierId == null ? "base" : tierId));
        String totalText = r.criticalFail() ? "1" : r.bonus() > 0
                ? msg().raw("weekly.roll-with-bonus", "natural", r.natural(), "bonus", r.bonus(), "total", r.total())
                : String.valueOf(r.total());
        p.showTitle(Title.title(Text.color(msg().raw(r.criticalFail() ? "weekly.result-title-fail"
                        : r.total() >= 20 ? "weekly.result-title-jackpot" : "weekly.result-title", "roll", r.total())),
                Text.color(msg().raw("weekly.result-subtitle", "tier", tierName, "roll", totalText)),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofMillis(500))));
        msg().send(p, "weekly.result", "roll", totalText, "tier", tierName);
        String summary = items.isEmpty() ? msg().raw("quest.no-items") : summarize(items);
        msg().sendPlain(p, "weekly.reward-summary", "money", Money.format(money), "xp", xp, "items", summary);

        if (r.criticalFail()) {
            sound(p, "weekly.animation.fail-sound", 1f);
            String joke = giver != null ? plugin.givers().dialogue(giver, "weekly-crit-fail", "player", p.getName())
                    : msg().random("dialogue.town.weekly-crit-fail", "player", p.getName());
            if (!joke.isEmpty()) {
                msg().sendPlain(p, "dialogue.format", "giver", giver == null ? "&6Quest Giver" : giver.name,
                        "line", joke);
            }
        } else if (tier != null && tier.getBoolean("fireworks")) {
            sound(p, "weekly.animation.jackpot-sound", 1f);
            fireworks(p);
        } else {
            sound(p, "weekly.animation.result-sound", 1f);
        }
        if (tier != null && tier.getBoolean("announce")) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                msg().send(online, "weekly.jackpot-announce", "player", p.getName());
            }
        }
        long count = items.stream().mapToInt(ItemStack::getAmount).sum();
        Storage.Payout payout = new Storage.Payout(p.getUniqueId(), p.getName(), "weekly", "weekly", "Weekly reward",
                giver == null ? "" : giver.id, giver == null ? "" : Text.strip(giver.name), money, xp, (int) count,
                summary, null, System.currentTimeMillis());
        plugin.storage().run(c -> {
            Storage.insertPayout(c, payout);
            return null;
        });
    }

    private List<ItemStack> items(ConfigurationSection s) {
        List<ItemStack> out = new ArrayList<>();
        if (s == null) {
            return out;
        }
        List<ItemSpec> specs = new ArrayList<>();
        for (Object o : s.getList("items", List.of())) {
            specs.add(ItemSpec.parse(o));
        }
        List<?> randoms = s.getList("random-items", List.of());
        if (!randoms.isEmpty()) {
            Object pick = randoms.get(ThreadLocalRandom.current().nextInt(randoms.size()));
            if (pick instanceof List<?> group) {
                for (Object o : group) specs.add(ItemSpec.parse(o));
            } else {
                specs.add(ItemSpec.parse(pick));
            }
        }
        try {
            out.addAll(plugin.rewards().createItems(specs, null));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Bad weekly reward item: " + e.getMessage());
        }
        return out;
    }

    private static String summarize(List<ItemStack> items) {
        List<String> parts = new ArrayList<>();
        for (ItemStack i : items) {
            parts.add((i.getAmount() > 1 ? i.getAmount() + "x " : "") + Text.plain(i.effectiveName()));
        }
        return String.join(", ", parts);
    }

    public List<D20.TierRange> tiers() {
        List<D20.TierRange> out = new ArrayList<>();
        ConfigurationSection s = cfg().getConfigurationSection("weekly.tiers");
        if (s == null) {
            return out;
        }
        for (String id : s.getKeys(false)) {
            List<Integer> range = s.getIntegerList(id + ".range");
            if (range.size() == 2) {
                out.add(new D20.TierRange(id, range.get(0), range.get(1)));
            }
        }
        return out;
    }

    private void fireworks(Player p) {
        for (int i = 0; i < 3; i++) {
            Firework fw = p.getWorld().spawn(p.getLocation().add(ThreadLocalRandom.current().nextDouble(-2, 2), 1,
                    ThreadLocalRandom.current().nextDouble(-2, 2)), Firework.class);
            var meta = fw.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(Color.ORANGE, Color.YELLOW, Color.FUCHSIA).withFade(Color.WHITE).flicker(true).build());
            meta.setPower(1);
            fw.setFireworkMeta(meta);
        }
    }

    private void sound(Player p, String path, float pitch) {
        String key = cfg().getString(path, "");
        if (key == null || key.isEmpty()) return;
        try {
            p.playSound(Sound.sound(Key.key(key.toLowerCase(Locale.ROOT)), Sound.Source.MASTER, 1f, pitch));
        } catch (Exception ignored) {
            // Bad sound key in config.
        }
    }

    /** Login reminder when a weekly reward is waiting (and paying a roll interrupted by logging out). */
    public void onJoin(Player p) {
        PlayerData d = plugin.data().get(p);
        if (d.weeklyPendingNatural > 0) {
            msg().send(p, "weekly.paying-interrupted");
            payPending(p, null);
            return;
        }
        if (cfg().getBoolean("weekly.login-reminder", true) && available(d) && p.hasPermission("questgiver.quest.use")) {
            msg().send(p, "weekly.reminder", "bonus", nextBonus(d));
        }
    }

    /** Adds the weekly button to the giver menu. */
    public void decorate(GiverMenu menu, Player p, GiverDefinition giver) {
        menu.button(GiverMenu.WEEKLY, button(p), () -> claim(p, giver));
    }
}
