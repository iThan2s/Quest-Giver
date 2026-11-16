package io.github.ithan2s.questgiver.npc;

import io.github.ithan2s.questgiver.QuestGiverPlugin;
import io.github.ithan2s.questgiver.util.Keys;
import io.github.ithan2s.questgiver.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Town quest-givers. Definitions live in givers.yml; the entity is only a body. Bodies are tagged with the giver id,
 * re-bound whenever their chunk loads and respawned if missing, so killing or removing a body never loses anything.
 */
public final class GiverManager implements Listener {
    private final QuestGiverPlugin plugin;
    private final SkinService skins;
    private final Map<String, GiverDefinition> givers = new LinkedHashMap<>();
    private final Map<UUID, String> selected = new HashMap<>();
    private final Map<UUID, Long> lastGreeting = new HashMap<>();

    public GiverManager(QuestGiverPlugin plugin, SkinService skins) {
        this.plugin = plugin;
        this.skins = skins;
    }

    public void load(YamlConfiguration yaml) {
        givers.clear();
        ConfigurationSection section = yaml.getConfigurationSection("givers");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection s = section.getConfigurationSection(id);
                if (s != null) {
                    givers.put(id.toLowerCase(Locale.ROOT), GiverDefinition.read(id.toLowerCase(Locale.ROOT), s));
                }
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = plugin.giversYaml();
        yaml.set("givers", null);
        for (GiverDefinition d : givers.values()) {
            d.write(yaml.createSection("givers." + d.id));
        }
        try {
            yaml.save(new File(plugin.getDataFolder(), "givers.yml"));
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save givers.yml", e);
        }
    }

    public Collection<GiverDefinition> all() {
        return givers.values();
    }

    public GiverDefinition get(String id) {
        return id == null ? null : givers.get(id.toLowerCase(Locale.ROOT));
    }

    public GiverDefinition byEntity(Entity entity) {
        String id = entity.getPersistentDataContainer().get(Keys.GIVER, PersistentDataType.STRING);
        return id == null ? null : givers.get(id);
    }

    /** Dialogue line for a giver: its own lines from givers.yml, else the defaults in messages.yml. */
    public String dialogue(GiverDefinition giver, String key, Object... kv) {
        List<String> own = giver == null ? List.of() : giver.dialogue.getOrDefault(key, List.of());
        if (!own.isEmpty()) {
            return Text.format(own.get((int) (Math.random() * own.size())), kv);
        }
        return plugin.messages().random("dialogue.town." + key, kv);
    }

    public void say(Player player, GiverDefinition giver, String key, Object... kv) {
        String line = dialogue(giver, key, kv);
        if (!line.isEmpty()) {
            plugin.messages().sendPlain(player, "dialogue.format",
                    "giver", giver == null ? "" : giver.name, "line", line);
        }
    }

    /** Greets a player when they open the menu, at most once a minute. */
    public void greet(Player player, GiverDefinition giver) {
        long now = System.currentTimeMillis();
        Long last = lastGreeting.get(player.getUniqueId());
        if (last == null || now - last > 60_000) {
            lastGreeting.put(player.getUniqueId(), now);
            say(player, giver, "greeting", "player", player.getName());
        }
    }

    // ---- Lifecycle of bodies ----

    public GiverDefinition create(String id, Location loc) {
        GiverDefinition d = new GiverDefinition(id.toLowerCase(Locale.ROOT));
        d.name = plugin.getConfig().getString("npc.default-name", "&6&lQuest Giver");
        d.skin = plugin.getConfig().getString("npc.default-skin", "");
        d.spawned = true;
        d.setLocation(loc);
        givers.put(d.id, d);
        spawnBody(d);
        save();
        if (d.skin != null && !d.skin.isEmpty()) {
            applySkin(d, d.skin, null);
        }
        return d;
    }

    public void remove(GiverDefinition d) {
        removeBody(d);
        givers.remove(d.id);
        selected.values().removeIf(d.id::equals);
        save();
    }

    public Entity body(GiverDefinition d) {
        if (d.entity == null) {
            return null;
        }
        Entity e = Bukkit.getEntity(d.entity);
        return e != null && e.isValid() ? e : null;
    }

    public void spawnBody(GiverDefinition d) {
        Location loc = d.location();
        if (loc == null) {
            plugin.getLogger().warning("Quest-giver '" + d.id + "' is in an unloaded or missing world: " + d.world);
            return;
        }
        removeBody(d);
        Entity e;
        if (d.type == GiverDefinition.Type.VILLAGER) {
            e = NpcFactory.spawnVillager(loc, Keys.GIVER, d.id, d.name, d.profession);
        } else {
            SkinService.Skin skin = d.skinTexture == null || d.skinTexture.isEmpty() ? null
                    : new SkinService.Skin(d.skin, d.skinTexture, d.skinSignature);
            e = NpcFactory.spawnMannequin(loc, Keys.GIVER, d.id, d.name, skin == null ? null : skin.profile(),
                    plugin.getConfig().getString("npc.mannequin-description", "&7Quest Giver"));
        }
        d.entity = e.getUniqueId();
    }

    public void removeBody(GiverDefinition d) {
        Entity e = body(d);
        if (e != null) {
            e.remove();
        }
        // Also sweep loaded duplicates carrying this id.
        Location loc = d.location();
        if (loc != null && loc.isChunkLoaded()) {
            for (Entity other : loc.getChunk().getEntities()) {
                if (d.id.equals(other.getPersistentDataContainer().get(Keys.GIVER, PersistentDataType.STRING))) {
                    other.remove();
                }
            }
        }
        d.entity = null;
    }

    /** Re-applies name and skin to an existing body. */
    public void refreshBody(GiverDefinition d) {
        Entity e = body(d);
        if (e == null) {
            return;
        }
        boolean wrongType = (d.type == GiverDefinition.Type.VILLAGER) != (e instanceof Villager);
        if (wrongType) {
            spawnBody(d);
            return;
        }
        NpcFactory.rename(e, d.name);
        if (e instanceof Mannequin m && d.skinTexture != null && !d.skinTexture.isEmpty()) {
            m.setProfile(new SkinService.Skin(d.skin, d.skinTexture, d.skinSignature).profile());
        }
        if (e instanceof Villager v) {
            NpcFactory.applyProfession(v, d.profession);
        }
    }

    public void applySkin(GiverDefinition d, String playerName, Player feedback) {
        skins.lookup(playerName).whenComplete((skin, err) -> {
            if (err != null || skin == null) {
                if (feedback != null) {
                    plugin.messages().send(feedback, "giver.skin-failed", "player", playerName);
                }
                return;
            }
            d.skin = skin.name();
            d.skinTexture = skin.texture();
            d.skinSignature = skin.signature();
            save();
            refreshBody(d);
            if (feedback != null) {
                plugin.messages().send(feedback, "giver.skin-set", "giver", d.id, "player", skin.name());
            }
        });
    }

    /** Runs every few seconds: respawns missing bodies, snaps moved ones back. */
    public void ensureBodies() {
        for (GiverDefinition d : givers.values()) {
            if (!d.spawned) {
                continue;
            }
            Location loc = d.location();
            if (loc == null || !loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                continue;
            }
            Entity e = body(d);
            if (e == null) {
                e = findTagged(loc, d.id);
                if (e != null) {
                    d.entity = e.getUniqueId();
                }
            }
            if (e == null) {
                spawnBody(d);
                save();
            } else if (e.getLocation().distanceSquared(loc) > 0.25) {
                e.teleport(loc);
            }
        }
    }

    private Entity findTagged(Location loc, String id) {
        for (Entity e : loc.getChunk().getEntities()) {
            if (e.isValid() && id.equals(e.getPersistentDataContainer().get(Keys.GIVER, PersistentDataType.STRING))) {
                return e;
            }
        }
        return null;
    }

    /** Re-binds bodies as their chunks load, removing orphans and duplicates. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        boolean changed = false;
        for (Entity e : event.getEntities()) {
            String id = e.getPersistentDataContainer().get(Keys.GIVER, PersistentDataType.STRING);
            if (id == null) {
                continue;
            }
            GiverDefinition d = givers.get(id);
            if (d == null || !d.spawned) {
                e.remove();
                continue;
            }
            Entity bound = body(d);
            if (bound != null && !bound.getUniqueId().equals(e.getUniqueId())) {
                e.remove();
                continue;
            }
            if (!e.getUniqueId().equals(d.entity)) {
                d.entity = e.getUniqueId();
                changed = true;
            }
            refreshBody(d);
        }
        if (changed) {
            save();
        }
    }

    // ---- Admin selection ----

    public void select(Player admin, GiverDefinition d) {
        selected.put(admin.getUniqueId(), d.id);
    }

    /** The giver an admin command applies to: their selection, else the nearest within 20 blocks, else the only one. */
    public GiverDefinition target(Player admin) {
        GiverDefinition sel = get(selected.get(admin.getUniqueId()));
        if (sel != null) {
            return sel;
        }
        GiverDefinition nearest = null;
        double best = 20 * 20;
        for (GiverDefinition d : givers.values()) {
            Location loc = d.location();
            if (loc != null && loc.getWorld().equals(admin.getWorld())) {
                double dist = loc.distanceSquared(admin.getLocation());
                if (dist < best) {
                    best = dist;
                    nearest = d;
                }
            }
        }
        if (nearest != null) {
            return nearest;
        }
        return givers.size() == 1 ? givers.values().iterator().next() : null;
    }

    public List<String> ids() {
        return new ArrayList<>(givers.keySet());
    }

    public String nextId() {
        if (!givers.containsKey("town")) {
            return "town";
        }
        int n = 2;
        while (givers.containsKey("giver" + n)) {
            n++;
        }
        return "giver" + n;
    }
}
