package io.github.ithan2s.questgiver.progress;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.ithan2s.questgiver.storage.Storage;
import io.github.ithan2s.questgiver.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Caches data for online players. Loading happens on the async pre-login thread (through the database thread), so
 * the main thread never waits on SQLite. All mutation of cached data happens on the main thread; saves serialise a
 * snapshot on the main thread and write it on the database thread.
 */
public final class PlayerDataManager {
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final Storage storage;
    private final Logger logger;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public PlayerDataManager(Storage storage, Logger logger) {
        this.storage = storage;
        this.logger = logger;
    }

    /** Called from AsyncPlayerPreLoginEvent: blocks the login thread (not the main thread) until loaded. */
    public boolean preload(UUID uuid, String name) {
        try {
            PlayerData data = storage.supply(c -> parse(Storage.loadPlayer(c, uuid), uuid, name))
                    .get(15, TimeUnit.SECONDS);
            data.name = name;
            cache.put(uuid, data);
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Could not load quest data for " + name, e);
            return false;
        }
    }

    /** Fallback for players who were already online when the plugin enabled (e.g. a plugin reload). */
    public PlayerData loadBlocking(UUID uuid, String name) {
        PlayerData data = cache.get(uuid);
        if (data != null) {
            return data;
        }
        try {
            data = storage.supply(c -> parse(Storage.loadPlayer(c, uuid), uuid, name)).get(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Could not load quest data for " + name + "; using empty data", e);
            data = PlayerData.fresh(uuid, name);
        }
        data.name = name;
        cache.put(uuid, data);
        return data;
    }

    private static PlayerData parse(String json, UUID uuid, String name) {
        if (json == null) {
            return PlayerData.fresh(uuid, name);
        }
        PlayerData data = GSON.fromJson(json, PlayerData.class);
        if (data == null) {
            return PlayerData.fresh(uuid, name);
        }
        data.uuid = uuid.toString();
        return data.normalize();
    }

    /** Data for an online player. Loads synchronously only if somehow missing. */
    public PlayerData get(Player player) {
        PlayerData data = cache.get(player.getUniqueId());
        return data != null ? data : loadBlocking(player.getUniqueId(), player.getName());
    }

    public PlayerData getIfLoaded(UUID uuid) {
        return cache.get(uuid);
    }

    public Iterable<PlayerData> loaded() {
        return cache.values();
    }

    /** Saves and evicts. */
    public void unload(Player player) {
        PlayerData data = cache.remove(player.getUniqueId());
        if (data != null) {
            save(data);
        }
    }

    /** Drops cache entries left by logins that never completed. */
    public void evictOffline() {
        cache.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
    }

    public CompletableFuture<Void> save(PlayerData data) {
        data.dirty = false;
        String json = GSON.toJson(data);
        UUID uuid = data.uuid();
        String name = data.name;
        int completed = data.completedCount;
        double earned = data.questEarnings;
        return storage.run(c -> {
            Storage.savePlayer(c, uuid, name, completed, earned, json);
            return null;
        });
    }

    public void saveDirty() {
        for (PlayerData data : cache.values()) {
            if (data.dirty) {
                save(data);
            }
        }
    }

    public void saveAll() {
        for (PlayerData data : cache.values()) {
            save(data);
        }
    }

    /**
     * Modifies a player's data whether or not they are online. Online: applied immediately on the main thread.
     * Offline: loaded, modified and saved on the database thread (the mutator must then only touch plain data).
     * {@code done} runs on the main thread with true if the player has data.
     */
    public void modify(UUID uuid, Consumer<PlayerData> mutator, Consumer<Boolean> done) {
        PlayerData online = cache.get(uuid);
        if (online != null && Bukkit.getPlayer(uuid) != null) {
            mutator.accept(online);
            online.markDirty();
            save(online);
            done.accept(true);
            return;
        }
        storage.supply(c -> {
            String json = Storage.loadPlayer(c, uuid);
            if (json == null) {
                return false;
            }
            PlayerData data = parse(json, uuid, "?");
            mutator.accept(data);
            Storage.savePlayer(c, uuid, data.name, data.completedCount, data.questEarnings, GSON.toJson(data));
            return true;
        }).whenComplete((ok, err) -> Tasks.main(() -> done.accept(err == null && Boolean.TRUE.equals(ok))));
    }

    /** Read-only snapshot for offline players (callback on the main thread, null if unknown). */
    public void snapshot(UUID uuid, Consumer<PlayerData> callback) {
        PlayerData online = cache.get(uuid);
        if (online != null) {
            callback.accept(online);
            return;
        }
        storage.supply(c -> {
            String json = Storage.loadPlayer(c, uuid);
            return json == null ? null : parse(json, uuid, "?");
        }).whenComplete((data, err) -> Tasks.main(() -> callback.accept(err == null ? data : null)));
    }

    /** Resolves a player name to a uuid: online players first, then stored data. Callback on the main thread. */
    public void resolve(String name, Consumer<UUID> callback) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            callback.accept(online.getUniqueId());
            return;
        }
        storage.supply(c -> Storage.findPlayerByName(c, name))
                .whenComplete((uuid, err) -> Tasks.main(() -> callback.accept(err == null ? uuid : null)));
    }
}
