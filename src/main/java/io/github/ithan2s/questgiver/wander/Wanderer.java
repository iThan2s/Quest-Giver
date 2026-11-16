package io.github.ithan2s.questgiver.wander;

import java.util.UUID;

/** A wandering quest-giver currently in the world (persisted so it despawns on time across restarts). */
public final class Wanderer {
    public String id;
    public String entity;
    public String world;
    public double x, y, z;
    public String questId;
    public String name;
    public String skin;
    public long spawnedAt;
    public long despawnAt;

    public UUID entityId() {
        return entity == null ? null : UUID.fromString(entity);
    }
}
