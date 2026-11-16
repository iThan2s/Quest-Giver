package io.github.ithan2s.questgiver.squad;

import io.github.ithan2s.questgiver.progress.PlayerDataManager;
import io.github.ithan2s.questgiver.storage.Storage;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Holds every squad in memory (they are small) and persists changes on the database thread. */
public final class SquadManager {
    private final Storage storage;
    private final Logger logger;
    private final Map<String, Squad> squads = new LinkedHashMap<>();

    public SquadManager(Storage storage, Logger logger) {
        this.storage = storage;
        this.logger = logger;
    }

    /** Loaded once while the plugin enables, before anyone can join. */
    public void loadAll() {
        try {
            Map<String, String> rows = storage.supply(Storage::loadSquads).get(30, TimeUnit.SECONDS);
            squads.clear();
            for (Map.Entry<String, String> row : rows.entrySet()) {
                Squad s = PlayerDataManager.GSON.fromJson(row.getValue(), Squad.class);
                if (s != null && s.members != null && !s.members.isEmpty()) {
                    squads.put(s.id, s.normalize());
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Could not load squads", e);
        }
    }

    public Collection<Squad> all() {
        return squads.values();
    }

    public Squad of(UUID player) {
        String id = player.toString();
        for (Squad s : squads.values()) {
            if (s.members.contains(id)) {
                return s;
            }
        }
        return null;
    }

    public Squad byName(String name) {
        for (Squad s : squads.values()) {
            if (s.name.equalsIgnoreCase(name)) {
                return s;
            }
        }
        return null;
    }

    public Squad byId(String id) {
        return id == null ? null : squads.get(id);
    }

    public Squad create(String name, UUID leader) {
        Squad s = Squad.create(name, leader);
        squads.put(s.id, s);
        save(s);
        return s;
    }

    public void delete(Squad s) {
        squads.remove(s.id);
        String id = s.id;
        storage.run(c -> {
            Storage.deleteSquad(c, id);
            return null;
        });
    }

    public void save(Squad s) {
        s.dirty = false;
        String json = PlayerDataManager.GSON.toJson(s);
        String id = s.id;
        storage.run(c -> {
            Storage.saveSquad(c, id, json);
            return null;
        });
    }

    public void saveDirty() {
        for (Squad s : squads.values()) {
            if (s.dirty) {
                save(s);
            }
        }
    }

    public void saveAll() {
        squads.values().forEach(this::save);
    }

    public boolean nameTaken(String name) {
        return byName(name.toLowerCase(Locale.ROOT)) != null;
    }
}
