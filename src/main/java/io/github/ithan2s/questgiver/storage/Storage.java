package io.github.ithan2s.questgiver.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQLite storage. Every query runs on one dedicated thread, so the connection is never shared between threads and
 * writes are applied in the order they were queued. Callers hop back to the main thread with {@code Tasks.main}.
 * The driver (org.xerial sqlite-jdbc) ships with Paper, so nothing is shaded.
 */
public final class Storage {
    private final Logger logger;
    private final ExecutorService executor;
    private Connection connection;

    public Storage(Logger logger) {
        this.logger = logger;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "QuestGiver-Database");
            t.setDaemon(true);
            return t;
        });
    }

    @FunctionalInterface
    public interface SqlTask<T> {
        T run(Connection c) throws Exception;
    }

    public void open(File file) throws Exception {
        // Open the connection on the database thread itself.
        executor.submit(() -> {
            try {
                Class.forName("org.sqlite.JDBC");
                connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
                try (Statement st = connection.createStatement()) {
                    st.execute("PRAGMA journal_mode=WAL");
                    st.execute("PRAGMA synchronous=NORMAL");
                    st.execute("PRAGMA foreign_keys=ON");
                    st.execute("""
                            CREATE TABLE IF NOT EXISTS player_data (
                              uuid TEXT PRIMARY KEY,
                              name TEXT NOT NULL,
                              completed INTEGER NOT NULL DEFAULT 0,
                              earned REAL NOT NULL DEFAULT 0,
                              json TEXT NOT NULL,
                              updated INTEGER NOT NULL)""");
                    st.execute("CREATE INDEX IF NOT EXISTS idx_player_rank ON player_data(completed DESC, earned DESC)");
                    st.execute("CREATE INDEX IF NOT EXISTS idx_player_name ON player_data(name COLLATE NOCASE)");
                    st.execute("""
                            CREATE TABLE IF NOT EXISTS payouts (
                              id INTEGER PRIMARY KEY AUTOINCREMENT,
                              uuid TEXT NOT NULL,
                              name TEXT NOT NULL,
                              source TEXT NOT NULL,
                              quest_id TEXT,
                              quest_name TEXT,
                              giver_id TEXT,
                              giver_name TEXT,
                              money REAL NOT NULL,
                              xp REAL NOT NULL,
                              items INTEGER NOT NULL,
                              item_summary TEXT,
                              squad TEXT,
                              ts INTEGER NOT NULL)""");
                    st.execute("CREATE INDEX IF NOT EXISTS idx_payouts_player ON payouts(uuid, ts)");
                    st.execute("CREATE INDEX IF NOT EXISTS idx_payouts_ts ON payouts(ts)");
                    st.execute("CREATE TABLE IF NOT EXISTS squads (id TEXT PRIMARY KEY, json TEXT NOT NULL)");
                    st.execute("""
                            CREATE TABLE IF NOT EXISTS pending_rewards (
                              id INTEGER PRIMARY KEY AUTOINCREMENT,
                              uuid TEXT NOT NULL,
                              json TEXT NOT NULL)""");
                    st.execute("CREATE INDEX IF NOT EXISTS idx_pending_uuid ON pending_rewards(uuid)");
                    st.execute("CREATE TABLE IF NOT EXISTS kv (k TEXT PRIMARY KEY, v TEXT NOT NULL)");
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).get(30, TimeUnit.SECONDS);
    }

    public <T> CompletableFuture<T> supply(SqlTask<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    future.complete(task.run(connection));
                } catch (Throwable t) {
                    logger.log(Level.SEVERE, "Database task failed", t);
                    future.completeExceptionally(t);
                }
            });
        } catch (Exception rejected) {
            future.completeExceptionally(rejected);
        }
        return future;
    }

    public CompletableFuture<Void> run(SqlTask<?> task) {
        return supply(c -> {
            task.run(c);
            return null;
        }).thenApply(x -> null);
    }

    /** Flushes queued writes and closes the connection. Called on disable. */
    public void close() {
        executor.execute(() -> {
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to close database", e);
            }
        });
        executor.shutdown();
        try {
            if (!executor.awaitTermination(20, TimeUnit.SECONDS)) {
                logger.warning("Database thread did not finish in time; some writes may be lost.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---- Queries. These must only be called from inside supply()/run() tasks. ----

    public static String loadPlayer(Connection c, UUID uuid) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT json FROM player_data WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    public static UUID findPlayerByName(Connection c, String name) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT uuid FROM player_data WHERE name = ? COLLATE NOCASE ORDER BY updated DESC LIMIT 1")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? UUID.fromString(rs.getString(1)) : null;
            }
        }
    }

    public static void savePlayer(Connection c, UUID uuid, String name, int completed, double earned, String json)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO player_data(uuid, name, completed, earned, json, updated) VALUES (?,?,?,?,?,?)
                ON CONFLICT(uuid) DO UPDATE SET name=excluded.name, completed=excluded.completed,
                  earned=excluded.earned, json=excluded.json, updated=excluded.updated""")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setInt(3, completed);
            ps.setDouble(4, earned);
            ps.setString(5, json);
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    public record LeaderRow(UUID uuid, String name, int completed, double earned) {
    }

    /** Players with at least one completed quest, by completions then quest earnings. */
    public static List<LeaderRow> top(Connection c, int limit) throws SQLException {
        List<LeaderRow> rows = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT uuid, name, completed, earned FROM player_data WHERE completed > 0 "
                        + "ORDER BY completed DESC, earned DESC, name ASC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new LeaderRow(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getInt(3),
                            rs.getDouble(4)));
                }
            }
        }
        return rows;
    }

    public record Payout(UUID uuid, String name, String source, String questId, String questName, String giverId,
                         String giverName, double money, double xp, int items, String itemSummary, String squad,
                         long timestamp) {
    }

    public static void insertPayout(Connection c, Payout p) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO payouts(uuid, name, source, quest_id, quest_name, giver_id, giver_name, money, xp, items,
                  item_summary, squad, ts) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)""")) {
            ps.setString(1, p.uuid().toString());
            ps.setString(2, p.name());
            ps.setString(3, p.source());
            ps.setString(4, p.questId());
            ps.setString(5, p.questName());
            ps.setString(6, p.giverId());
            ps.setString(7, p.giverName());
            ps.setDouble(8, p.money());
            ps.setDouble(9, p.xp());
            ps.setInt(10, p.items());
            ps.setString(11, p.itemSummary());
            ps.setString(12, p.squad());
            ps.setLong(13, p.timestamp());
            ps.executeUpdate();
        }
    }

    /** Quest history for one player and giver ("wandering" quests show at every town giver). */
    public static List<Payout> history(Connection c, UUID uuid, String giverId, long since, int limit)
            throws SQLException {
        List<Payout> rows = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM payouts WHERE uuid = ? AND source IN ('quest','bounty') "
                        + "AND (giver_id = ? OR giver_id IN ('wandering', 'admin')) AND ts >= ? ORDER BY ts DESC LIMIT ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, giverId);
            ps.setLong(3, since);
            ps.setInt(4, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(readPayout(rs));
                }
            }
        }
        return rows;
    }

    private static Payout readPayout(ResultSet rs) throws SQLException {
        return new Payout(UUID.fromString(rs.getString("uuid")), rs.getString("name"), rs.getString("source"),
                rs.getString("quest_id"), rs.getString("quest_name"), rs.getString("giver_id"),
                rs.getString("giver_name"), rs.getDouble("money"), rs.getDouble("xp"), rs.getInt("items"),
                rs.getString("item_summary"), rs.getString("squad"), rs.getLong("ts"));
    }

    public record Totals(double money, double xp, long items, long count) {
    }

    public static Totals totalsSince(Connection c, long since) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COALESCE(SUM(money),0), COALESCE(SUM(xp),0), COALESCE(SUM(items),0), COUNT(*) "
                        + "FROM payouts WHERE ts >= ?")) {
            ps.setLong(1, since);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new Totals(rs.getDouble(1), rs.getDouble(2), rs.getLong(3), rs.getLong(4));
            }
        }
    }

    public record QuestEarning(String questId, String questName, double money, long count) {
    }

    public static List<QuestEarning> topQuests(Connection c, int limit) throws SQLException {
        List<QuestEarning> rows = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT quest_id, MAX(quest_name), SUM(money), COUNT(*) FROM payouts WHERE source IN ('quest','bounty') "
                        + "GROUP BY quest_id ORDER BY SUM(money) DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new QuestEarning(rs.getString(1), rs.getString(2), rs.getDouble(3), rs.getLong(4)));
                }
            }
        }
        return rows;
    }

    public static Map<String, String> loadSquads(Connection c) throws SQLException {
        Map<String, String> out = new LinkedHashMap<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT id, json FROM squads")) {
            while (rs.next()) {
                out.put(rs.getString(1), rs.getString(2));
            }
        }
        return out;
    }

    public static void saveSquad(Connection c, String id, String json) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO squads(id, json) VALUES (?,?) ON CONFLICT(id) DO UPDATE SET json=excluded.json")) {
            ps.setString(1, id);
            ps.setString(2, json);
            ps.executeUpdate();
        }
    }

    public static void deleteSquad(Connection c, String id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM squads WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        }
    }

    public static void addPending(Connection c, UUID uuid, String json) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO pending_rewards(uuid, json) VALUES (?,?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, json);
            ps.executeUpdate();
        }
    }

    /** Reads and deletes all pending rewards for a player in one transaction. */
    public static List<String> takePending(Connection c, UUID uuid) throws SQLException {
        List<String> out = new ArrayList<>();
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT json FROM pending_rewards WHERE uuid = ? ORDER BY id")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(rs.getString(1));
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM pending_rewards WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            }
            c.commit();
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(auto);
        }
        return out;
    }

    public static String getKv(Connection c, String key) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT v FROM kv WHERE k = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    public static Map<String, String> getKvPrefix(Connection c, String prefix) throws SQLException {
        Map<String, String> out = new LinkedHashMap<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT k, v FROM kv WHERE k LIKE ?")) {
            ps.setString(1, prefix.replace("%", "\\%") + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getString(1), rs.getString(2));
                }
            }
        }
        return out;
    }

    public static void setKv(Connection c, String key, String value) throws SQLException {
        if (value == null) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM kv WHERE k = ?")) {
                ps.setString(1, key);
                ps.executeUpdate();
            }
            return;
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO kv(k, v) VALUES (?,?) ON CONFLICT(k) DO UPDATE SET v=excluded.v")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }
}
