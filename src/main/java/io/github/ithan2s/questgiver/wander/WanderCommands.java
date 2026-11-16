package io.github.ithan2s.questgiver.wander;

import io.github.ithan2s.questgiver.QuestGiverPlugin;
import io.github.ithan2s.questgiver.command.CommandTree;
import io.github.ithan2s.questgiver.config.Messages;
import io.github.ithan2s.questgiver.quest.QuestDefinition;
import io.github.ithan2s.questgiver.util.Durations;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** /quest wander spawn [here|near|random] | list | clear (admin, mainly for testing). */
public final class WanderCommands {
    private static final String PERM = "questgiver.admin.wander";

    private WanderCommands() {
    }

    public static void register(QuestGiverPlugin plugin, CommandTree tree) {
        Messages msg = plugin.messages();
        WanderManager wm = plugin.wanderers();

        tree.register("wander spawn", PERM, true, (sender, args) -> {
            Player p = (Player) sender;
            String mode = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "here";
            switch (mode) {
                case "here" -> {
                    Wanderer w = wm.spawnNear(p);
                    if (w != null) msg.send(p, "wander.spawned-here", "name", w.name);
                }
                case "near" -> {
                    if (!wm.spawnNearPlayer(p)) msg.send(p, "wander.no-spot");
                }
                case "random" -> {
                    if (!wm.spawnRandomSpot(p)) msg.send(p, "wander.no-spot");
                }
                default -> throw new CommandTree.UsageException();
            }
        }, (sender, args) -> List.of("here", "near", "random"));

        tree.register("wander list", PERM, false, (sender, args) -> {
            msg.sendPlain(sender, "wander.list-header", "count", wm.all().size(),
                    "chance", Math.round(wm.chance() * 100));
            long now = System.currentTimeMillis();
            for (Wanderer w : wm.all()) {
                QuestDefinition q = plugin.quests().get(w.questId);
                msg.sendPlain(sender, "wander.list-line", "name", w.name, "quest", q == null ? w.questId : q.name(),
                        "world", w.world, "x", (int) w.x, "y", (int) w.y, "z", (int) w.z,
                        "time", Durations.format(Durations.remaining(w.despawnAt, now)));
            }
        });

        tree.register("wander clear", PERM, false, (sender, args) -> {
            int n = wm.all().size();
            wm.clear();
            msg.send(sender, "wander.cleared", "count", n);
        });
    }
}
