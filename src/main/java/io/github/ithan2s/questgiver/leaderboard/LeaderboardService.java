package io.github.ithan2s.questgiver.leaderboard;

import com.google.gson.reflect.TypeToken;
import io.github.ithan2s.questgiver.QuestGiverPlugin;
import io.github.ithan2s.questgiver.progress.PlayerDataManager;
import io.github.ithan2s.questgiver.storage.Storage;
import io.github.ithan2s.questgiver.util.Money;
import io.github.ithan2s.questgiver.util.Tasks;
import io.github.ithan2s.questgiver.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Quest leaderboard: players with at least one finished quest, sorted by quests completed, ties broken by money
 * earned from quests (tracked by the plugin, not /bal). Shown in chat with /quest top and as DecentHolograms
 * holograms that update live when someone finishes a quest.
 */
public final class LeaderboardService {
    public static final class Board {
        public String id;
        public String world;
        public double x, y, z;

        Location location() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x, y, z);
        }
    }

    private final QuestGiverPlugin plugin;
    private final Map<String, Board> boards = new LinkedHashMap<>();
    private HologramBridge bridge;
    private boolean refreshQueued;

    public LeaderboardService(QuestGiverPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        if (Bukkit.getPluginManager().isPluginEnabled("DecentHolograms")) {
            try {
                bridge = new DecentHologramsBridge();
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "DecentHolograms is installed but its API could not be used", t);
                bridge = null;
            }
        }
        try {
            String json = plugin.storage().supply(c -> Storage.getKv(c, "leaderboards")).get(10, TimeUnit.SECONDS);
            boards.clear();
            if (json != null) {
                List<Board> list = PlayerDataManager.GSON.fromJson(json, new TypeToken<List<Board>>() { }.getType());
                for (Board b : list) boards.put(b.id, b);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load leaderboards: " + e.getMessage());
        }
    }

    public boolean available() {
        return bridge != null;
    }

    public Map<String, Board> boards() {
        return boards;
    }

    private void persist() {
        String json = PlayerDataManager.GSON.toJson(new ArrayList<>(boards.values()));
        plugin.storage().run(c -> {
            Storage.setKv(c, "leaderboards", json);
            return null;
        });
    }

    private static String holoName(String id) {
        return "questgiver-lb-" + id;
    }

    public Board create(String id, Location loc) {
        Board b = new Board();
        b.id = id;
        b.world = loc.getWorld().getName();
        b.x = loc.getX();
        b.y = loc.getY() + 2.5;
        b.z = loc.getZ();
        boards.put(id, b);
        persist();
        refreshNow();
        return b;
    }

    public void remove(String id) {
        boards.remove(id);
        persist();
        if (bridge != null) bridge.remove(holoName(id));
    }

    public void move(String id, Location loc) {
        Board b = boards.get(id);
        b.world = loc.getWorld().getName();
        b.x = loc.getX();
        b.y = loc.getY() + 2.5;
        b.z = loc.getZ();
        persist();
        if (bridge != null) bridge.move(holoName(id), b.location());
    }

    public String nextId() {
        int n = 1;
        while (boards.containsKey("lb" + n)) n++;
        return "lb" + n;
    }

    /** Debounced refresh after a quest completion (several squad members finishing at once = one update). */
    public void refreshSoon() {
        if (refreshQueued) return;
        refreshQueued = true;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            refreshQueued = false;
            refreshNow();
        }, 20L);
    }

    public void refreshNow() {
        if (bridge == null || boards.isEmpty()) return;
        int size = plugin.getConfig().getInt("leaderboard.size", 10);
        plugin.storage().supply(c -> Storage.top(c, size)).thenAccept(rows -> Tasks.main(() -> {
            List<String> lines = lines(rows);
            for (Board b : boards.values()) {
                Location loc = b.location();
                if (loc == null) continue;
                try {
                    bridge.show(holoName(b.id), loc, lines);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Could not update leaderboard hologram " + b.id, e);
                }
            }
        }));
    }

    private List<String> lines(List<Storage.LeaderRow> rows) {
        List<String> lines = new ArrayList<>(plugin.getConfig().getStringList("leaderboard.title"));
        if (rows.isEmpty()) {
            lines.add(plugin.getConfig().getString("leaderboard.empty", "&7No quests completed yet."));
        }
        int rank = 1;
        String format = plugin.getConfig().getString("leaderboard.line", "&e#{rank} &f{player} &7- &a{completed}");
        for (Storage.LeaderRow r : rows) {
            lines.add(Text.format(format, "rank", rank++, "player", r.name(), "completed", r.completed(),
                    "earned", Money.format(r.earned())));
        }
        return lines;
    }

    /** /quest top in chat. */
    public void sendTop(CommandSender to) {
        int size = plugin.getConfig().getInt("leaderboard.size", 10);
        plugin.storage().supply(c -> Storage.top(c, size)).thenAccept(rows -> Tasks.main(() -> {
            var msg = plugin.messages();
            msg.sendPlain(to, "top.header");
            if (rows.isEmpty()) {
                msg.sendPlain(to, "top.empty");
            }
            int rank = 1;
            for (Storage.LeaderRow r : rows) {
                msg.sendPlain(to, "top.line", "rank", rank++, "player", r.name(), "completed", r.completed(),
                        "earned", Money.format(r.earned()));
            }
        }));
    }

    public void removeAllHolograms() {
        if (bridge == null) return;
        for (Board b : boards.values()) {
            try {
                bridge.remove(holoName(b.id));
            } catch (Exception ignored) {
                // DecentHolograms may already be shutting down.
            }
        }
    }
}
