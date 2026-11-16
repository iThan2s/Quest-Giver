package io.github.ithan2s.questgiver.npc;

import io.github.ithan2s.questgiver.util.Text;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.key.Key;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;

/** Spawns invulnerable, immovable, persistent NPC bodies (vanilla mannequin or villager). */
public final class NpcFactory {
    private NpcFactory() {
    }

    public static Entity spawnMannequin(Location loc, NamespacedKey tag, String id, String name,
                                        ResolvableProfile profile, String description) {
        return loc.getWorld().spawn(loc, Mannequin.class, m -> {
            common(m, tag, id, name);
            m.setImmovable(true);
            if (profile != null) {
                m.setProfile(profile);
            }
            m.setDescription(description == null || description.isEmpty() ? null : Text.color(description));
        });
    }

    public static Entity spawnVillager(Location loc, NamespacedKey tag, String id, String name, String profession) {
        return loc.getWorld().spawn(loc, Villager.class, v -> {
            common(v, tag, id, name);
            v.setAI(false);
            applyProfession(v, profession);
            v.setVillagerLevel(5);
        });
    }

    private static void common(LivingEntity e, NamespacedKey tag, String id, String name) {
        e.getPersistentDataContainer().set(tag, PersistentDataType.STRING, id);
        e.setPersistent(true);
        e.setInvulnerable(true);
        e.setSilent(true);
        e.setCollidable(false);
        e.setGravity(false);
        e.setAI(false);
        e.customName(Text.color(name));
        e.setCustomNameVisible(true);
    }

    public static void rename(Entity e, String name) {
        e.customName(Text.color(name));
        e.setCustomNameVisible(true);
    }

    public static void applyProfession(Villager v, String profession) {
        Villager.Profession p = Registry.VILLAGER_PROFESSION.get(
                Key.key(profession == null ? "librarian" : profession.toLowerCase(Locale.ROOT)));
        if (p != null) {
            v.setProfession(p);
        }
    }
}
