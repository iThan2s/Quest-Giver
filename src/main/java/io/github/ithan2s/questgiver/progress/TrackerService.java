package io.github.ithan2s.questgiver.progress;

import io.github.ithan2s.questgiver.QuestGiverPlugin;
import io.github.ithan2s.questgiver.quest.QuestDefinition;
import io.github.ithan2s.questgiver.util.Durations;
import io.github.ithan2s.questgiver.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Shows the tracked quest's progress (boss bar or sidebar, chosen in config.yml) and a separate countdown boss bar
 * for real-time-limited quests (wandering quests). Pick the tracked quest with /quest track or from /quest list.
 */
public final class TrackerService {
    private final QuestGiverPlugin plugin;
    private final Map<UUID, BossBar> progressBars = new HashMap<>();
    private final Map<UUID, BossBar> timerBars = new HashMap<>();
    private final Set<UUID> sidebars = new HashSet<>();

    public TrackerService(QuestGiverPlugin plugin) {
        this.plugin = plugin;
    }

    private String mode() {
        return plugin.getConfig().getString("tracker.mode", "bossbar").toLowerCase(Locale.ROOT);
    }

    /** The quest shown in the tracker: the one picked with /quest track, else the first unfinished one. */
    public QuestService.Ref tracked(Player p) {
        QuestService qs = plugin.questService();
        PlayerData d = plugin.data().get(p);
        List<QuestService.Ref> refs = qs.refs(p);
        if (d.tracked != null) {
            for (QuestService.Ref r : refs) {
                if (r.quest().uid.equals(d.tracked)) return r;
            }
        }
        for (QuestService.Ref r : refs) {
            if (r.quest().readyAt == 0) return r;
        }
        return refs.isEmpty() ? null : refs.getFirst();
    }

    public void update(Player p) {
        if (!p.isOnline()) {
            return;
        }
        String mode = mode();
        QuestService.Ref r = mode.equals("off") ? null : tracked(p);
        QuestDefinition q = r == null ? null : plugin.quests().get(r.quest().questId);
        if (q == null) {
            hideProgress(p);
            return;
        }
        QuestService qs = plugin.questService();
        boolean ready = qs.isReady(p, r);
        if (mode.equals("sidebar")) {
            showSidebar(p, q, r, ready);
            return;
        }
        String title = plugin.messages().raw(ready ? "tracker.bossbar-ready" : "tracker.bossbar",
                "quest", q.name(), "progress", qs.progressSummary(p, r.quest(), q));
        float progress = ready ? 1f : (float) Math.max(0, Math.min(1, qs.progressFraction(p, r.quest(), q)));
        BossBar bar = progressBars.get(p.getUniqueId());
        if (bar == null) {
            bar = BossBar.bossBar(Text.color(title), progress, color("tracker.color", BossBar.Color.PURPLE),
                    BossBar.Overlay.PROGRESS);
            progressBars.put(p.getUniqueId(), bar);
            p.showBossBar(bar);
        } else {
            bar.name(Text.color(title));
            bar.progress(progress);
            bar.color(ready ? BossBar.Color.GREEN : color("tracker.color", BossBar.Color.PURPLE));
        }
    }

    private void showSidebar(Player p, QuestDefinition q, QuestService.Ref r, boolean ready) {
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        Scoreboard board = p.getScoreboard();
        if (board == main || !sidebars.contains(p.getUniqueId())) {
            if (board != main && !sidebars.contains(p.getUniqueId())) {
                return; // Another plugin owns this player's scoreboard; don't clobber it.
            }
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            p.setScoreboard(board);
            sidebars.add(p.getUniqueId());
        }
        Objective old = board.getObjective("questgiver");
        if (old != null) old.unregister();
        Objective obj = board.registerNewObjective("questgiver", Criteria.DUMMY,
                Text.color(plugin.messages().raw("tracker.sidebar-title", "quest", q.name())));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        List<String> lines = plugin.questService().progressLines(p, r.quest(), q);
        if (ready) lines = new java.util.ArrayList<>(lines);
        if (ready) lines.add(plugin.messages().raw("tracker.sidebar-ready"));
        int score = lines.size();
        for (String line : lines) {
            String entry = LegacyComponentSerializer.legacySection().serialize(Text.color(line));
            // Entries must be unique; pad duplicates with colour resets.
            while (board.getEntries().contains(entry)) entry += "§r";
            obj.getScore(entry).setScore(score--);
        }
    }

    private void hideProgress(Player p) {
        BossBar bar = progressBars.remove(p.getUniqueId());
        if (bar != null) p.hideBossBar(bar);
        if (sidebars.remove(p.getUniqueId())) {
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    /** Every second: countdown bar for the soonest-expiring timed quest. */
    public void tickTimers() {
        long now = System.currentTimeMillis();
        QuestService qs = plugin.questService();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.data().getIfLoaded(p.getUniqueId()) == null) continue;
            QuestService.Ref soonest = null;
            QuestDefinition soonestQ = null;
            for (QuestService.Ref r : qs.refs(p)) {
                QuestDefinition q = plugin.quests().get(r.quest().questId);
                if (q == null || !r.quest().isTimed()) continue;
                boolean frozen = !q.hasFetch() && r.quest().readyAt > 0;
                if (frozen) continue;
                if (soonest == null || r.quest().expiresAt < soonest.quest().expiresAt) {
                    soonest = r;
                    soonestQ = q;
                }
            }
            BossBar bar = timerBars.get(p.getUniqueId());
            if (soonest == null) {
                if (bar != null) {
                    p.hideBossBar(bar);
                    timerBars.remove(p.getUniqueId());
                }
                continue;
            }
            long left = Durations.remaining(soonest.quest().expiresAt, now);
            long total = Math.max(1, soonest.quest().expiresAt - soonest.quest().acceptedAt);
            String title = plugin.messages().raw("tracker.timer", "quest", soonestQ.name(), "time",
                    Durations.clock(left));
            float progress = (float) Math.max(0, Math.min(1, left / (double) total));
            if (bar == null) {
                bar = BossBar.bossBar(Text.color(title), progress, color("tracker.timer-color", BossBar.Color.YELLOW),
                        BossBar.Overlay.NOTCHED_12);
                timerBars.put(p.getUniqueId(), bar);
                p.showBossBar(bar);
            } else {
                bar.name(Text.color(title));
                bar.progress(progress);
                bar.color(left < 5 * Durations.MINUTE ? BossBar.Color.RED
                        : color("tracker.timer-color", BossBar.Color.YELLOW));
            }
        }
    }

    private BossBar.Color color(String path, BossBar.Color def) {
        try {
            return BossBar.Color.valueOf(plugin.getConfig().getString(path, def.name()).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return def;
        }
    }

    public void remove(Player p) {
        BossBar bar = progressBars.remove(p.getUniqueId());
        if (bar != null) p.hideBossBar(bar);
        bar = timerBars.remove(p.getUniqueId());
        if (bar != null) p.hideBossBar(bar);
        sidebars.remove(p.getUniqueId());
    }

    public void refreshAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            hideProgress(p);
            update(p);
        }
    }

    public void clearAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            hideProgress(p);
            BossBar bar = timerBars.remove(p.getUniqueId());
            if (bar != null) p.hideBossBar(bar);
        }
    }
}
