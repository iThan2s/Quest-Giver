package io.github.ithan2s.questgiver.wander;

import com.google.gson.reflect.TypeToken;
import io.github.ithan2s.questgiver.QuestGiverPlugin;
import io.github.ithan2s.questgiver.config.Messages;
import io.github.ithan2s.questgiver.npc.NpcFactory;
import io.github.ithan2s.questgiver.progress.PlayerData;
import io.github.ithan2s.questgiver.progress.PlayerDataManager;
import io.github.ithan2s.questgiver.quest.QuestDefinition;
import io.github.ithan2s.questgiver.storage.Storage;
import io.github.ithan2s.questgiver.util.Durations;
import io.github.ithan2s.questgiver.util.Keys;
import io.github.ithan2s.questgiver.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Wandering quest-givers, spawned like wandering traders: an attempt every 20 minutes at 25% rising by 25% per
 * failure to 75%. They appear near a random player ("they come to you") or at a random spot in the world, offer
 * one quest from the wandering pool, leave once someone accepts, and leave on their own after an hour.
 */
public final class WanderManager implements Listener {
    private final QuestGiverPlugin plugin;
    private final Map<String, Wanderer> wanderers = new LinkedHashMap<>();
    private double chance;
    private BukkitTask attemptTask;

    public WanderManager(QuestGiverPlugin plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    private Messages msg() {
        return plugin.messages();
    }

    public void load() {
        try {
            Map<String, String> kv = plugin.storage().supply(c -> Map.of(
                    "chance", String.valueOf(Storage.getKv(c, "wanderers.chance")),
                    "list", String.valueOf(Storage.getKv(c, "wanderers.list")))).get(10, TimeUnit.SECONDS);
            chance = kv.get("chance").equals("null") ? cfg().getDouble("wandering.start-chance", 0.25)
                    : Double.parseDouble(kv.get("chance"));
            wanderers.clear();
            if (!kv.get("list").equals("null")) {
                List<Wanderer> list = PlayerDataManager.GSON.fromJson(kv.get("list"),
                        new TypeToken<List<Wanderer>>() { }.getType());
                for (Wanderer w : list) {
                    wanderers.put(w.id, w);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load wandering quest-giver state: " + e.getMessage());
            chance = cfg().getDouble("wandering.start-chance", 0.25);
        }
        schedule();
    }

    /** (Re)starts the attempt timer; called on enable and reload. */
    public void schedule() {
        if (attemptTask != null) {
            attemptTask.cancel();
        }
        long period = Math.max(1, cfg().getLong("wandering.attempt-interval-minutes", 20)) * 60L * 20L;
        attemptTask = Bukkit.getScheduler().runTaskTimer(plugin, this::attempt, period, period);
    }

    private void persist() {
        String list = PlayerDataManager.GSON.toJson(new ArrayList<>(wanderers.values()));
        String c = Double.toString(chance);
        plugin.storage().run(conn -> {
            Storage.setKv(conn, "wanderers.chance", c);
            Storage.setKv(conn, "wanderers.list", list);
            return null;
        });
    }

    public java.util.Collection<Wanderer> all() {
        return wanderers.values();
    }

    public Wanderer byEntity(Entity e) {
        String id = e.getPersistentDataContainer().get(Keys.WANDERER, PersistentDataType.STRING);
        return id == null ? null : wanderers.get(id);
    }

    public double chance() {
        return chance;
    }

    /** One spawn attempt (every attempt-interval-minutes). */
    public void attempt() {
        if (!cfg().getBoolean("wandering.enabled", true) || Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }
        if (wanderers.size() >= cfg().getInt("wandering.max-active", 1)) {
            return;
        }
        boolean spawned = WanderChance.succeeds(chance, ThreadLocalRandom.current().nextDouble()) && spawnRandomMode();
        chance = WanderChance.next(chance, spawned, cfg().getDouble("wandering.start-chance", 0.25),
                cfg().getDouble("wandering.chance-step", 0.25), cfg().getDouble("wandering.max-chance", 0.75));
        persist();
    }

    private boolean spawnRandomMode() {
        boolean near = cfg().getBoolean("wandering.modes.near-player.enabled", true);
        boolean random = cfg().getBoolean("wandering.modes.random.enabled", true);
        if (near && random) {
            return ThreadLocalRandom.current().nextBoolean() ? spawnNearPlayer(null) : spawnRandomSpot(null);
        }
        if (near) return spawnNearPlayer(null);
        if (random) return spawnRandomSpot(null);
        return false;
    }

    private boolean worldAllowed(World w) {
        List<String> worlds = cfg().getStringList("wandering.worlds");
        return worlds.isEmpty() ? w.getEnvironment() == World.Environment.NORMAL : worlds.contains(w.getName());
    }

    /** "They come to you": near the given player, or a random eligible online player. */
    public boolean spawnNearPlayer(Player target) {
        List<Player> candidates = new ArrayList<>();
        if (target != null) {
            candidates.add(target);
        } else {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (worldAllowed(p.getWorld()) && !p.isDead()) candidates.add(p);
            }
        }
        if (candidates.isEmpty()) {
            return false;
        }
        Player p = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        int min = cfg().getInt("wandering.modes.near-player.min-distance", 16);
        int max = Math.max(min + 1, cfg().getInt("wandering.modes.near-player.max-distance", 40));
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            double dist = ThreadLocalRandom.current().nextDouble(min, max);
            int x = p.getLocation().getBlockX() + (int) Math.round(Math.cos(angle) * dist);
            int z = p.getLocation().getBlockZ() + (int) Math.round(Math.sin(angle) * dist);
            if (!p.getWorld().isChunkLoaded(x >> 4, z >> 4)) continue;
            Location loc = safeSpot(p.getWorld(), x, z);
            if (loc != null) {
                Wanderer w = spawnAt(loc, pickQuest(plugin.data().get(p)));
                if (w != null) {
                    msg().send(p, "wander.arrived-near", "name", w.name);
                    return true;
                }
            }
        }
        return false;
    }

    /** A random spot within the configured radius of a world's spawn, loaded asynchronously. */
    public boolean spawnRandomSpot(Player admin) {
        List<World> worlds = new ArrayList<>();
        for (World w : Bukkit.getWorlds()) {
            if (worldAllowed(w)) worlds.add(w);
        }
        if (worlds.isEmpty()) {
            return false;
        }
        World world = worlds.get(ThreadLocalRandom.current().nextInt(worlds.size()));
        int radius = Math.max(16, cfg().getInt("wandering.modes.random.radius", 1500));
        Location spawn = world.getSpawnLocation();
        int x = spawn.getBlockX() + ThreadLocalRandom.current().nextInt(-radius, radius + 1);
        int z = spawn.getBlockZ() + ThreadLocalRandom.current().nextInt(-radius, radius + 1);
        world.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk -> Tasks.main(() -> {
            Location loc = safeSpot(world, x, z);
            if (loc == null) {
                if (admin != null) msg().send(admin, "wander.no-spot");
                return;
            }
            Wanderer w = spawnAt(loc, pickQuest(null));
            if (w == null) return;
            if (admin != null) {
                msg().send(admin, "wander.spawned-at", "name", w.name, "world", world.getName(),
                        "x", loc.getBlockX(), "y", loc.getBlockY(), "z", loc.getBlockZ());
            }
            if (cfg().getBoolean("wandering.modes.random.announce", false)) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    msg().send(p, "wander.rumor", "name", w.name, "world", world.getName(),
                            "x", Math.round(loc.getBlockX() / 100.0) * 100, "z", Math.round(loc.getBlockZ() / 100.0) * 100);
                }
            }
        }));
        return true;
    }

    private static Location safeSpot(World world, int x, int z) {
        Block top = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        Material type = top.getType();
        if (top.isLiquid() || !type.isSolid() || type == Material.MAGMA_BLOCK || type == Material.CACTUS
                || top.getY() >= world.getMaxHeight() - 2) {
            return null;
        }
        Block above = top.getRelative(0, 1, 0);
        Block head = top.getRelative(0, 2, 0);
        if (!above.isPassable() || above.isLiquid() || !head.isPassable()) {
            return null;
        }
        return new Location(world, x + 0.5, top.getY() + 1, z + 0.5, ThreadLocalRandom.current().nextFloat() * 360f, 0f);
    }

    /** A wandering quest, preferring ones the nearby player hasn't finished. */
    private QuestDefinition pickQuest(PlayerData near) {
        List<QuestDefinition> pool = new ArrayList<>(plugin.quests().pool(QuestDefinition.WANDERING));
        if (pool.isEmpty()) return null;
        if (near != null) {
            List<QuestDefinition> fresh = pool.stream().filter(q -> !near.completed.containsKey(q.id())).toList();
            if (!fresh.isEmpty()) pool = new ArrayList<>(fresh);
        }
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    private Wanderer spawnAt(Location loc, QuestDefinition quest) {
        if (quest == null) {
            plugin.getLogger().warning("No wandering quests are defined; can't spawn a wandering quest-giver.");
            return null;
        }
        List<Map<?, ?>> looks = cfg().getMapList("wandering.appearances");
        String name = "&6Wandering Quest-Giver";
        String skin = "";
        if (!looks.isEmpty()) {
            Map<?, ?> look = looks.get(ThreadLocalRandom.current().nextInt(looks.size()));
            name = String.valueOf(look.get("name") == null ? name : look.get("name"));
            skin = look.get("skin") == null ? "" : String.valueOf(look.get("skin"));
        }
        Wanderer w = new Wanderer();
        w.id = UUID.randomUUID().toString().substring(0, 8);
        w.questId = quest.id();
        w.name = name;
        w.skin = skin;
        w.world = loc.getWorld().getName();
        w.x = loc.getX();
        w.y = loc.getY();
        w.z = loc.getZ();
        w.spawnedAt = System.currentTimeMillis();
        w.despawnAt = w.spawnedAt + cfg().getLong("wandering.despawn-minutes", 60) * Durations.MINUTE;
        Entity e = NpcFactory.spawnMannequin(loc, Keys.WANDERER, w.id, name, null, msg().raw("wander.description"));
        w.entity = e.getUniqueId().toString();
        wanderers.put(w.id, w);
        persist();
        loc.getWorld().spawnParticle(Particle.PORTAL, loc.clone().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.5);
        if (!skin.isEmpty()) {
            plugin.skins().lookup(skin).thenAccept(s -> {
                Entity body = Bukkit.getEntity(e.getUniqueId());
                if (body instanceof Mannequin m) m.setProfile(s.profile());
            });
        }
        return w;
    }

    public Wanderer spawnNear(Player admin) {
        Location loc = admin.getLocation().clone().add(admin.getLocation().getDirection().setY(0).normalize().multiply(3));
        Location safe = safeSpot(loc.getWorld(), loc.getBlockX(), loc.getBlockZ());
        return spawnAt(safe == null ? admin.getLocation() : safe, pickQuest(plugin.data().get(admin)));
    }

    /** Removes a wanderer (claimed, expired or by an admin). */
    public void remove(Wanderer w, boolean effects) {
        wanderers.remove(w.id);
        persist();
        Entity e = w.entityId() == null ? null : Bukkit.getEntity(w.entityId());
        if (e != null) {
            if (effects) {
                e.getWorld().spawnParticle(Particle.CLOUD, e.getLocation().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.05);
            }
            e.remove();
        }
    }

    public void clear() {
        for (Wanderer w : new ArrayList<>(wanderers.values())) {
            remove(w, true);
        }
    }

    /** Every 30 seconds: wanderers nobody accepted leave after their hour. */
    public void sweep() {
        long now = System.currentTimeMillis();
        for (Wanderer w : new ArrayList<>(wanderers.values())) {
            if (now >= w.despawnAt) {
                Entity e = w.entityId() == null ? null : Bukkit.getEntity(w.entityId());
                if (e != null) {
                    for (Player p : e.getLocation().getNearbyPlayers(24)) {
                        say(p, w, "leave");
                    }
                }
                remove(w, true);
            }
        }
    }

    /** Cleans up bodies of wanderers that left while their chunk was unloaded. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity e : event.getEntities()) {
            String id = e.getPersistentDataContainer().get(Keys.WANDERER, PersistentDataType.STRING);
            if (id == null) continue;
            Wanderer w = wanderers.get(id);
            if (w == null || System.currentTimeMillis() >= w.despawnAt) {
                if (w != null) wanderers.remove(id);
                e.remove();
            } else if (!e.getUniqueId().toString().equals(w.entity)) {
                e.remove();
            }
        }
    }

    public void say(Player p, Wanderer w, String key) {
        QuestDefinition q = plugin.quests().get(w.questId);
        String line = msg().random("dialogue.wanderer." + key, "player", p.getName(),
                "quest", q == null ? "" : q.name());
        if (!line.isEmpty()) {
            msg().sendPlain(p, "dialogue.format", "giver", w.name, "line", line);
        }
    }

    /** A player accepted: the wanderer's job is done and it leaves. */
    public void claimed(Wanderer w, Player by) {
        say(by, w, "accept");
        remove(w, true);
    }

    public long timeLimitMs(QuestDefinition q) {
        long minutes = q.timeLimitMinutes() > 0 ? q.timeLimitMinutes()
                : cfg().getLong("wandering.quest-time-limit-minutes", 60);
        return minutes * Durations.MINUTE;
    }
}
