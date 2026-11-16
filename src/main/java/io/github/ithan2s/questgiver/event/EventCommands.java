package io.github.ithan2s.questgiver.event;

import io.github.ithan2s.questgiver.QuestGiverPlugin;
import io.github.ithan2s.questgiver.command.CommandTree;
import io.github.ithan2s.questgiver.config.Messages;

import java.util.ArrayList;
import java.util.Locale;

/** /quest event start|stop <name> and /quest event list (admin). */
public final class EventCommands {
    private static final String PERM = "questgiver.admin.event";

    private EventCommands() {
    }

    public static void register(QuestGiverPlugin plugin, CommandTree tree) {
        Messages msg = plugin.messages();
        EventManager events = plugin.events();
        CommandTree.Completer names = (sender, args) -> new ArrayList<>(events.definitions().keySet());

        tree.register("event start", PERM, false, (sender, args) -> {
            CommandTree.require(args.length == 1);
            EventManager.Definition d = events.get(args[0]);
            if (d == null) {
                msg.send(sender, "event.unknown", "event", args[0]);
                return;
            }
            if (events.isActive(d.id())) {
                msg.send(sender, "event.already-running", "event", d.displayName());
                return;
            }
            events.start(d.id());
            msg.send(sender, "event.admin-started", "event", d.displayName(), "run", events.activeRun(d.id()));
        }, names);

        tree.register("event stop", PERM, false, (sender, args) -> {
            CommandTree.require(args.length == 1);
            EventManager.Definition d = events.get(args[0]);
            if (d == null) {
                msg.send(sender, "event.unknown", "event", args[0]);
                return;
            }
            if (!events.isActive(d.id())) {
                msg.send(sender, "event.not-running", "event", d.displayName());
                return;
            }
            events.stop(d.id());
            msg.send(sender, "event.admin-stopped", "event", d.displayName());
        }, names);

        tree.register("event list", PERM, false, (sender, args) -> {
            msg.sendPlain(sender, "event.list-header");
            for (EventManager.Definition d : events.definitions().values()) {
                long quests = plugin.quests().all().stream().filter(q -> d.id().equals(q.event())).count();
                String dates = d.start().isEmpty() ? msg.raw("event.manual-only")
                        : d.start() + " → " + d.end();
                msg.sendPlain(sender, "event.list-line", "event", d.displayName(), "id", d.id().toLowerCase(Locale.ROOT),
                        "state", msg.raw(events.isActive(d.id()) ? "event.state-on" : "event.state-off"),
                        "dates", dates, "quests", quests);
            }
        });
    }
}
