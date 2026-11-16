package io.github.ithan2s.questgiver.leaderboard;

import org.bukkit.Location;

import java.util.List;

/** Hologram backend. Only {@link DecentHologramsBridge} touches the DecentHolograms API. */
public interface HologramBridge {
    void show(String name, Location location, List<String> lines);

    void move(String name, Location location);

    void remove(String name);
}
