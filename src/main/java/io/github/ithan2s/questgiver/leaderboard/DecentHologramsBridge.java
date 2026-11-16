package io.github.ithan2s.questgiver.leaderboard;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Location;

import java.util.List;

/**
 * DecentHolograms implementation. Holograms are created unsaved (saveToFile = false): QuestGiver stores their
 * positions itself and recreates them on startup, so DecentHolograms' own files never go stale.
 * This class is only loaded when DecentHolograms is installed (soft dependency).
 */
final class DecentHologramsBridge implements HologramBridge {
    @Override
    public void show(String name, Location location, List<String> lines) {
        Hologram existing = DHAPI.getHologram(name);
        if (existing == null) {
            DHAPI.createHologram(name, location, false, lines);
        } else {
            DHAPI.setHologramLines(existing, lines);
            if (!sameBlock(existing.getLocation(), location)) {
                DHAPI.moveHologram(existing, location);
            }
        }
    }

    private static boolean sameBlock(Location a, Location b) {
        return a != null && b != null && a.getWorld() != null && a.getWorld().equals(b.getWorld())
                && a.distanceSquared(b) < 0.01;
    }

    @Override
    public void move(String name, Location location) {
        Hologram h = DHAPI.getHologram(name);
        if (h != null) {
            DHAPI.moveHologram(h, location);
        }
    }

    @Override
    public void remove(String name) {
        DHAPI.removeHologram(name);
    }
}
