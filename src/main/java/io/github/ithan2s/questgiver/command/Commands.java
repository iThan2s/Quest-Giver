package io.github.ithan2s.questgiver.command;

import io.github.ithan2s.questgiver.QuestGiverPlugin;
import io.github.ithan2s.questgiver.npc.GiverCommands;

/** Registers every /quest subcommand. */
public final class Commands {
    private Commands() {
    }

    public static void registerAll(QuestGiverPlugin plugin, CommandTree tree) {
        tree.register("help", null, false, (sender, args) -> tree.sendHelp(sender, ""));
        tree.register("reload", "questgiver.admin.reload", false, (sender, args) -> {
            long start = System.currentTimeMillis();
            plugin.reloadAll();
            plugin.messages().send(sender, "admin.reloaded", "ms", System.currentTimeMillis() - start);
        });
        PlayerCommands.register(plugin, tree);
        GiverCommands.register(plugin, tree);
        io.github.ithan2s.questgiver.squad.SquadCommands.register(plugin, tree);
        io.github.ithan2s.questgiver.wander.WanderCommands.register(plugin, tree);
        io.github.ithan2s.questgiver.leaderboard.LeaderboardCommands.register(plugin, tree);
        io.github.ithan2s.questgiver.event.EventCommands.register(plugin, tree);
        io.github.ithan2s.questgiver.admin.AdminCommands.register(plugin, tree);
    }
}
