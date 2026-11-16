package io.github.ithan2s.questgiver.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/** Runs work back on the main server thread. */
public final class Tasks {
    private static Plugin plugin;

    private Tasks() {
    }

    public static void init(Plugin owner) {
        plugin = owner;
    }

    public static void main(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
        } else if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    public static void later(long ticks, Runnable runnable) {
        Bukkit.getScheduler().runTaskLater(plugin, runnable, ticks);
    }

    public static void async(Runnable runnable) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
    }
}
