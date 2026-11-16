package io.github.ithan2s.questgiver.npc;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** One town quest-giver as stored in givers.yml. Holds no player data. */
public final class GiverDefinition {
    public enum Type { PLAYER, VILLAGER }

    public static final List<String> DIALOGUE_KEYS = List.of("offer", "accept", "turn-in", "empty-handed", "greeting");

    public final String id;
    public Type type = Type.PLAYER;
    public String name;
    public String skin;
    public String skinTexture;
    public String skinSignature;
    public String pool = "town";
    public String profession = "librarian";
    public boolean spawned;
    public String world;
    public double x, y, z;
    public float yaw, pitch;
    public UUID entity;
    public final Map<String, List<String>> dialogue = new LinkedHashMap<>();

    public GiverDefinition(String id) {
        this.id = id;
    }

    public Location location() {
        World w = world == null ? null : Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z, yaw, pitch);
    }

    public void setLocation(Location loc) {
        world = loc.getWorld().getName();
        x = loc.getX();
        y = loc.getY();
        z = loc.getZ();
        yaw = loc.getYaw();
        pitch = 0f;
    }

    public static GiverDefinition read(String id, ConfigurationSection s) {
        GiverDefinition d = new GiverDefinition(id);
        try {
            d.type = Type.valueOf(s.getString("type", "player").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            d.type = Type.PLAYER;
        }
        d.name = s.getString("name", "&6&lQuest Giver");
        d.skin = s.getString("skin", "");
        d.skinTexture = s.getString("skin-texture", "");
        d.skinSignature = s.getString("skin-signature", "");
        d.pool = s.getString("pool", "town");
        d.profession = s.getString("villager-profession", "librarian");
        d.spawned = s.getBoolean("spawned", true);
        ConfigurationSection loc = s.getConfigurationSection("location");
        if (loc != null) {
            d.world = loc.getString("world");
            d.x = loc.getDouble("x");
            d.y = loc.getDouble("y");
            d.z = loc.getDouble("z");
            d.yaw = (float) loc.getDouble("yaw");
            d.pitch = (float) loc.getDouble("pitch");
        }
        String entity = s.getString("entity", "");
        if (!entity.isEmpty()) {
            try {
                d.entity = UUID.fromString(entity);
            } catch (IllegalArgumentException ignored) {
                // Re-bound on the next chunk load.
            }
        }
        ConfigurationSection dlg = s.getConfigurationSection("dialogue");
        if (dlg != null) {
            for (String key : dlg.getKeys(false)) {
                d.dialogue.put(key, new ArrayList<>(dlg.getStringList(key)));
            }
        }
        return d;
    }

    public void write(ConfigurationSection s) {
        s.set("type", type.name().toLowerCase(Locale.ROOT));
        s.set("name", name);
        s.set("skin", skin == null ? "" : skin);
        s.set("skin-texture", skinTexture == null ? "" : skinTexture);
        s.set("skin-signature", skinSignature == null ? "" : skinSignature);
        s.set("pool", pool);
        s.set("villager-profession", profession);
        s.set("spawned", spawned);
        if (world != null) {
            s.set("location.world", world);
            s.set("location.x", x);
            s.set("location.y", y);
            s.set("location.z", z);
            s.set("location.yaw", (double) yaw);
            s.set("location.pitch", (double) pitch);
        }
        s.set("entity", entity == null ? "" : entity.toString());
        for (String key : DIALOGUE_KEYS) {
            s.set("dialogue." + key, dialogue.getOrDefault(key, List.of()));
        }
    }
}
