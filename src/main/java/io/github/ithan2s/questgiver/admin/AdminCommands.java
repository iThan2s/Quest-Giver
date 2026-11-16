package io.github.ithan2s.questgiver.admin;

import io.github.ithan2s.questgiver.QuestGiverPlugin;
import io.github.ithan2s.questgiver.command.CommandTree;
import io.github.ithan2s.questgiver.config.Messages;
import io.github.ithan2s.questgiver.progress.ActiveQuest;
import io.github.ithan2s.questgiver.progress.PlayerData;
import io.github.ithan2s.questgiver.progress.PlayerDataManager;
import io.github.ithan2s.questgiver.progress.QuestService;
import io.github.ithan2s.questgiver.quest.Objective;
import io.github.ithan2s.questgiver.quest.QuestDefinition;
import io.github.ithan2s.questgiver.storage.Storage;
import io.github.ithan2s.questgiver.util.Durations;
import io.github.ithan2s.questgiver.util.Money;
import io.github.ithan2s.questgiver.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

/** /quest admin reset|complete|give|progress|stats. Work for offline players too. */
public final class AdminCommands {
    private AdminCommands() {
    }

    public static void register(QuestGiverPlugin plugin, CommandTree tree) {
        Messages msg = plugin.messages();
        QuestService qs = plugin.questService();
        CommandTree.Completer playerThenQuest = (sender, args) -> args.length <= 1
                ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList() : plugin.quests().ids();

        tree.register("admin reset", "questgiver.admin.reset", false, (sender, args) -> {
            CommandTree.require(args.length == 1 || args.length == 2);
            QuestDefinition q = args.length == 2 ? quest(plugin, sender, args[1]) : null;
            if (args.length == 2 && q == null) return;
            resolve(plugin, sender, args[0], (uuid, name) -> plugin.data().modify(uuid, d -> {
                if (q == null) {
                    resetAll(d);
                } else {
                    resetQuest(d, q);
                }
            }, ok -> {
                if (!ok) {
                    msg.send(sender, "admin.no-data", "player", name);
                    return;
                }
                Player online = Bukkit.getPlayer(uuid);
                if (online != null) plugin.tracker().update(online);
                plugin.leaderboard().refreshSoon();
                msg.send(sender, q == null ? "admin.reset-all" : "admin.reset-quest", "player", name,
                        "quest", q == null ? "" : q.name());
            }));
        }, playerThenQuest);

        tree.register("admin complete", "questgiver.admin.complete", false, (sender, args) -> {
            CommandTree.require(args.length == 2);
            QuestDefinition q = quest(plugin, sender, args[1]);
            if (q == null) return;
            resolve(plugin, sender, args[0], (uuid, name) -> {
                Player online = Bukkit.getPlayer(uuid);
                if (online != null) {
                    QuestService.Ref r = qs.find(online, q.id());
                    int deaths = 0;
                    String giverId = "admin";
                    String giverName = msg.raw("admin.giver-name");
                    String run = null;
                    if (r != null) {
                        deaths = r.quest().deaths(uuid);
                        giverId = r.quest().giverId;
                        giverName = r.quest().giverName;
                        run = r.quest().eventRun;
                        qs.remove(r);
                    }
                    qs.completeFor(online, q, deaths, giverId, giverName, null, run);
                    msg.send(sender, "admin.completed", "player", name, "quest", q.name());
                    return;
                }
                // Offline: pay on next login, and drop the active quest now.
                QuestService.Pending pending = new QuestService.Pending(q.id(), 0, "admin", msg.raw("admin.giver-name"),
                        null, null, System.currentTimeMillis());
                String json = PlayerDataManager.GSON.toJson(pending);
                plugin.data().modify(uuid, d -> d.active.removeIf(a -> a.questId.equals(q.id())), ok -> {
                    if (!ok) {
                        msg.send(sender, "admin.no-data", "player", name);
                        return;
                    }
                    plugin.storage().run(c -> {
                        Storage.addPending(c, uuid, json);
                        return null;
                    });
                    msg.send(sender, "admin.completed-offline", "player", name, "quest", q.name());
                });
            });
        }, playerThenQuest);

        tree.register("admin give", "questgiver.admin.give", false, (sender, args) -> {
            CommandTree.require(args.length == 2);
            QuestDefinition q = quest(plugin, sender, args[1]);
            if (q == null) return;
            resolve(plugin, sender, args[0], (uuid, name) -> {
                Player online = Bukkit.getPlayer(uuid);
                long limit = q.isWandering() ? plugin.wanderers().timeLimitMs(q) : 0;
                if (online != null) {
                    if (qs.activeIds(online).contains(q.id())) {
                        msg.send(sender, "admin.already-active", "player", name, "quest", q.name());
                        return;
                    }
                    qs.give(online, q, msg.raw("admin.giver-name"), limit);
                    msg.send(online, "admin.given-you", "quest", q.name());
                    msg.send(sender, "admin.given", "player", name, "quest", q.name());
                    return;
                }
                String run = q.isEvent() ? plugin.events().activeRun(q.event()) : null;
                String giverName = msg.raw("admin.giver-name");
                plugin.data().modify(uuid, d -> {
                    if (d.active.stream().noneMatch(a -> a.questId.equals(q.id()))) {
                        ActiveQuest aq = ActiveQuest.create(q.id(), "admin", giverName, System.currentTimeMillis());
                        aq.eventRun = run;
                        if (limit > 0) aq.expiresAt = aq.acceptedAt + limit;
                        d.active.add(aq);
                    }
                }, ok -> msg.send(sender, ok ? "admin.given" : "admin.no-data", "player", name, "quest", q.name()));
            });
        }, playerThenQuest);

        tree.register("admin progress", "questgiver.admin.progress", false, (sender, args) -> {
            CommandTree.require(args.length == 1);
            resolve(plugin, sender, args[0], (uuid, name) -> plugin.data().snapshot(uuid, d -> {
                if (d == null) {
                    msg.send(sender, "admin.no-data", "player", name);
                    return;
                }
                showProgress(plugin, sender, uuid, name, d);
            }));
        }, (sender, args) -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());

        tree.register("admin stats", "questgiver.admin.stats", false, (sender, args) -> sendStats(plugin, sender));
    }

    private static QuestDefinition quest(QuestGiverPlugin plugin, CommandSender sender, String id) {
        QuestDefinition q = plugin.quests().get(id);
        if (q == null) {
            plugin.messages().send(sender, "admin.unknown-quest", "quest", id);
        }
        return q;
    }

    private static void resolve(QuestGiverPlugin plugin, CommandSender sender, String name,
                                BiConsumer<UUID, String> then) {
        plugin.data().resolve(name, uuid -> {
            if (uuid == null) {
                plugin.messages().send(sender, "admin.unknown-player", "player", name);
                return;
            }
            Player online = Bukkit.getPlayer(uuid);
            then.accept(uuid, online != null ? online.getName() : name);
        });
    }

    /** Full reset: progress, history page, cooldowns, streaks, leaderboard stats. The payout log stays for stats. */
    static void resetAll(PlayerData d) {
        d.completed.clear();
        d.completedCount = 0;
        d.questEarnings = 0;
        d.reputation.clear();
        d.active.clear();
        d.offers.clear();
        d.abandonCooldowns.clear();
        d.weeklyLastClaim = 0;
        d.weeklyStreak = 0;
        d.weeklyPendingNatural = 0;
        d.weeklyPendingBonus = 0;
        d.dragonLastTurnIn = 0;
        d.tracked = null;
        d.historySince = System.currentTimeMillis();
    }

    /** One quest: forget its completion (every event year), drop it if active, clear its cooldown. */
    static void resetQuest(PlayerData d, QuestDefinition q) {
        d.completed.keySet().removeIf(k -> k.equals(q.id()) || k.startsWith(q.id() + "@"));
        d.active.removeIf(a -> a.questId.equals(q.id()));
        d.abandonCooldowns.remove(q.id());
        d.offers.values().forEach(list -> list.replaceAll(id -> q.id().equals(id) ? null : id));
        if (q.isBounty()) d.dragonLastTurnIn = 0;
    }

    private static void showProgress(QuestGiverPlugin plugin, CommandSender sender, UUID uuid, String name,
                                     PlayerData d) {
        Messages msg = plugin.messages();
        long now = System.currentTimeMillis();
        msg.sendPlain(sender, "admin.progress-header", "player", name);
        msg.sendPlain(sender, "admin.progress-stats", "completed", d.completedCount,
                "earned", Money.format(d.questEarnings), "reputation", d.totalReputation(),
                "distinct", d.completed.size());
        long weeklyLeft = Durations.remaining(d.weeklyLastClaim + plugin.weekly().cooldownMs(), now);
        msg.sendPlain(sender, "admin.progress-weekly", "state", d.weeklyLastClaim == 0 || weeklyLeft == 0
                ? msg.raw("admin.weekly-ready") : Durations.format(weeklyLeft), "streak", d.weeklyStreak);
        List<QuestService.Ref> refs = plugin.questService().refs(uuid, d);
        if (refs.isEmpty()) {
            msg.sendPlain(sender, "admin.progress-none");
        }
        for (QuestService.Ref r : refs) {
            QuestDefinition q = plugin.quests().get(r.quest().questId);
            if (q == null) continue;
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < q.objectives().size(); i++) {
                Objective o = q.objectives().get(i);
                parts.add(o.label() + " " + (o.isFetch() ? "(fetch)" : r.quest().progress(i) + "/" + o.amount()));
            }
            String time = r.quest().isTimed()
                    ? " ⌛ " + Durations.format(Durations.remaining(r.quest().expiresAt, now)) : "";
            msg.sendPlain(sender, "admin.progress-line", "quest", q.name(), "id", q.id(),
                    "progress", String.join(", ", parts) + time, "deaths", r.quest().deaths(uuid),
                    "squad", r.squad() == null ? "" : msg.raw("quest.list-squad", "squad", r.squad().name));
        }
    }

    private static void sendStats(QuestGiverPlugin plugin, CommandSender sender) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        long dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli();
        long weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zone)
                .toInstant().toEpochMilli();
        plugin.storage().supply(c -> new Object[]{Storage.totalsSince(c, dayStart), Storage.totalsSince(c, weekStart),
                Storage.totalsSince(c, 0), Storage.topQuests(c, 5)}).thenAccept(res -> Tasks.main(() -> {
            Messages msg = plugin.messages();
            msg.sendPlain(sender, "stats.header");
            String[] labels = {"stats.today", "stats.week", "stats.all-time"};
            for (int i = 0; i < 3; i++) {
                Storage.Totals t = (Storage.Totals) res[i];
                msg.sendPlain(sender, "stats.line", "period", msg.raw(labels[i]), "money", Money.format(t.money()),
                        "xp", Math.round(t.xp()), "items", t.items(), "count", t.count());
            }
            @SuppressWarnings("unchecked")
            List<Storage.QuestEarning> top = (List<Storage.QuestEarning>) res[3];
            msg.sendPlain(sender, "stats.top-header");
            int rank = 1;
            for (Storage.QuestEarning q : top) {
                msg.sendPlain(sender, "stats.top-line", "rank", rank++, "quest", q.questName(),
                        "money", Money.format(q.money()), "count", q.count());
            }
            if (top.isEmpty()) msg.sendPlain(sender, "stats.none");
        }));
    }
}
