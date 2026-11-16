package io.github.ithan2s.questgiver.command;

import io.github.ithan2s.questgiver.QuestGiverPlugin;
import io.github.ithan2s.questgiver.config.Messages;
import io.github.ithan2s.questgiver.progress.QuestService;
import io.github.ithan2s.questgiver.quest.QuestDefinition;
import io.github.ithan2s.questgiver.util.Durations;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** /quest, /quest track, /quest abandon. */
public final class PlayerCommands {
    private PlayerCommands() {
    }

    public static void register(QuestGiverPlugin plugin, CommandTree tree) {
        Messages msg = plugin.messages();
        QuestService qs = plugin.questService();

        tree.register("", "questgiver.quest.use", true, (sender, args) -> listActive(plugin, (Player) sender));

        tree.register("list", "questgiver.quest.list", true,
                (sender, args) -> new io.github.ithan2s.questgiver.gui.ListMenu(plugin, (Player) sender)
                        .open((Player) sender));

        CommandTree.Completer active = (sender, args) ->
                sender instanceof Player p ? qs.activeIds(p) : List.of();

        tree.register("track", "questgiver.quest.track", true, (sender, args) -> {
            CommandTree.require(args.length == 1);
            Player p = (Player) sender;
            QuestService.Ref r = qs.find(p, args[0]);
            if (r == null) {
                msg.send(p, "quest.not-active", "quest", args[0]);
                return;
            }
            qs.track(p, r);
            QuestDefinition q = plugin.quests().get(r.quest().questId);
            msg.send(p, "quest.tracking", "quest", q == null ? r.quest().questId : q.name());
        }, active);

        tree.register("abandon", "questgiver.quest.abandon", true, (sender, args) -> {
            CommandTree.require(args.length == 1);
            Player p = (Player) sender;
            QuestService.Ref r = qs.find(p, args[0]);
            if (r == null) {
                msg.send(p, "quest.not-active", "quest", args[0]);
                return;
            }
            if (!qs.abandon(p, r)) {
                msg.send(p, "squad.leader-only");
            }
        }, active);
    }

    static void listActive(QuestGiverPlugin plugin, Player p) {
        Messages msg = plugin.messages();
        QuestService qs = plugin.questService();
        List<QuestService.Ref> refs = qs.refs(p);
        if (refs.isEmpty()) {
            msg.send(p, "quest.none-active");
            return;
        }
        msg.sendPlain(p, "quest.list-header", "count", qs.activeCount(p), "cap", qs.cap(p));
        long now = System.currentTimeMillis();
        for (QuestService.Ref r : refs) {
            QuestDefinition q = plugin.quests().get(r.quest().questId);
            if (q == null) continue;
            String state = qs.isReady(p, r) ? msg.raw("quest.state-ready") : qs.progressSummary(p, r.quest(), q);
            String time = r.quest().isTimed() ? msg.raw("quest.list-time", "time",
                    Durations.format(Durations.remaining(r.quest().expiresAt, now))) : "";
            String squad = r.squad() != null ? msg.raw("quest.list-squad", "squad", r.squad().name) : "";
            msg.sendPlain(p, "quest.list-line", "quest", q.name(), "id", q.id(), "state", state, "time", time,
                    "squad", squad);
        }
    }

    static boolean isPlayer(CommandSender s) {
        return s instanceof Player;
    }
}
