package io.github.ithan2s.questgiver.leaderboard;

import io.github.ithan2s.questgiver.QuestGiverPlugin;
import io.github.ithan2s.questgiver.command.CommandTree;
import io.github.ithan2s.questgiver.config.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Locale;

/** /quest leaderboard create|remove|movehere|list (admin) and /quest top. */
public final class LeaderboardCommands {
    private static final String PERM = "questgiver.admin.leaderboard";

    private LeaderboardCommands() {
    }

    public static void register(QuestGiverPlugin plugin, CommandTree tree) {
        Messages msg = plugin.messages();
        LeaderboardService lb = plugin.leaderboard();

        tree.register("top", "questgiver.quest.top", false, (sender, args) -> lb.sendTop(sender));

        tree.register("leaderboard create", PERM, true, (sender, args) -> {
            if (!installed(plugin, sender)) return;
            String id = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : lb.nextId();
            if (!id.matches("[a-z0-9_-]{1,24}")) {
                msg.send(sender, "lb.bad-id");
                return;
            }
            if (lb.boards().containsKey(id)) {
                msg.send(sender, "lb.exists", "id", id);
                return;
            }
            lb.create(id, ((Player) sender).getLocation());
            msg.send(sender, "lb.created", "id", id);
        });
        tree.register("leaderboard remove", PERM, false, (sender, args) -> {
            CommandTree.require(args.length == 1);
            if (!installed(plugin, sender)) return;
            if (!lb.boards().containsKey(args[0])) {
                msg.send(sender, "lb.unknown", "id", args[0]);
                return;
            }
            lb.remove(args[0]);
            msg.send(sender, "lb.removed", "id", args[0]);
        }, (sender, args) -> new ArrayList<>(lb.boards().keySet()));
        tree.register("leaderboard movehere", PERM, true, (sender, args) -> {
            CommandTree.require(args.length == 1);
            if (!installed(plugin, sender)) return;
            if (!lb.boards().containsKey(args[0])) {
                msg.send(sender, "lb.unknown", "id", args[0]);
                return;
            }
            lb.move(args[0], ((Player) sender).getLocation());
            msg.send(sender, "lb.moved", "id", args[0]);
        }, (sender, args) -> new ArrayList<>(lb.boards().keySet()));
        tree.register("leaderboard list", PERM, false, (sender, args) -> {
            if (!installed(plugin, sender)) return;
            if (lb.boards().isEmpty()) {
                msg.send(sender, "lb.none");
                return;
            }
            msg.sendPlain(sender, "lb.list-header");
            for (LeaderboardService.Board b : lb.boards().values()) {
                msg.sendPlain(sender, "lb.list-line", "id", b.id, "world", b.world, "x", (int) b.x, "y", (int) b.y,
                        "z", (int) b.z);
            }
        });
    }

    private static boolean installed(QuestGiverPlugin plugin, CommandSender sender) {
        if (!plugin.leaderboard().available()) {
            plugin.messages().send(sender, "lb.not-installed");
            return false;
        }
        return true;
    }
}
