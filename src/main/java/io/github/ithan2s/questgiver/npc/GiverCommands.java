package io.github.ithan2s.questgiver.npc;

import io.github.ithan2s.questgiver.QuestGiverPlugin;
import io.github.ithan2s.questgiver.command.CommandTree;
import io.github.ithan2s.questgiver.config.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** /quest giver ... (admin). */
public final class GiverCommands {
    private static final String PERM = "questgiver.admin.giver";

    private GiverCommands() {
    }

    public static void register(QuestGiverPlugin plugin, CommandTree tree) {
        Messages msg = plugin.messages();
        GiverManager givers = plugin.givers();

        tree.register("giver create", PERM, true, (sender, args) -> {
            Player p = (Player) sender;
            String id = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : givers.nextId();
            if (!id.matches("[a-z0-9_-]{1,32}")) {
                msg.send(p, "giver.bad-id");
                return;
            }
            if (givers.get(id) != null) {
                msg.send(p, "giver.exists", "giver", id);
                return;
            }
            GiverDefinition d = givers.create(id, p.getLocation());
            givers.select(p, d);
            msg.send(p, "giver.created", "giver", d.id);
        });

        tree.register("giver name", PERM, true, (sender, args) -> {
            CommandTree.require(args.length > 0);
            GiverDefinition d = target(plugin, (Player) sender);
            if (d == null) return;
            d.name = String.join(" ", args);
            givers.save();
            givers.refreshBody(d);
            msg.send(sender, "giver.renamed", "giver", d.id, "name", d.name);
        });

        tree.register("giver skin", PERM, true, (sender, args) -> {
            CommandTree.require(args.length == 1);
            GiverDefinition d = target(plugin, (Player) sender);
            if (d == null) return;
            msg.send(sender, "giver.skin-looking", "player", args[0]);
            givers.applySkin(d, args[0], (Player) sender);
        }, (sender, args) -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());

        tree.register("giver type", PERM, true, (sender, args) -> {
            CommandTree.require(args.length == 1);
            GiverDefinition d = target(plugin, (Player) sender);
            if (d == null) return;
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "player" -> d.type = GiverDefinition.Type.PLAYER;
                case "villager" -> d.type = GiverDefinition.Type.VILLAGER;
                default -> throw new CommandTree.UsageException();
            }
            givers.save();
            if (d.spawned) {
                givers.spawnBody(d);
                givers.save();
            }
            msg.send(sender, "giver.type-set", "giver", d.id, "type", args[0].toLowerCase(Locale.ROOT));
        }, (sender, args) -> List.of("player", "villager"));

        tree.register("giver move", PERM, true, (sender, args) -> {
            GiverDefinition d = target(plugin, (Player) sender);
            if (d == null) return;
            moveHere(givers, d, ((Player) sender).getLocation());
            msg.send(sender, "giver.moved", "giver", d.id);
        });

        tree.register("giver spawn", PERM, true, (sender, args) -> {
            GiverDefinition d = target(plugin, (Player) sender);
            if (d == null) return;
            moveHere(givers, d, ((Player) sender).getLocation());
            msg.send(sender, "giver.spawned", "giver", d.id);
        });

        tree.register("giver despawn", PERM, true, (sender, args) -> {
            GiverDefinition d = target(plugin, (Player) sender);
            if (d == null) return;
            d.spawned = false;
            givers.removeBody(d);
            givers.save();
            msg.send(sender, "giver.despawned", "giver", d.id);
        });

        tree.register("giver remove", PERM, true, (sender, args) -> {
            GiverDefinition d = target(plugin, (Player) sender);
            if (d == null) return;
            givers.remove(d);
            msg.send(sender, "giver.removed", "giver", d.id);
        });

        tree.register("giver select", PERM, true, (sender, args) -> {
            CommandTree.require(args.length == 1);
            GiverDefinition d = givers.get(args[0]);
            if (d == null) {
                msg.send(sender, "giver.unknown", "giver", args[0]);
                return;
            }
            givers.select((Player) sender, d);
            msg.send(sender, "giver.selected", "giver", d.id);
        }, (sender, args) -> givers.ids());

        tree.register("giver list", PERM, false, (sender, args) -> {
            if (givers.all().isEmpty()) {
                msg.send(sender, "giver.none");
                return;
            }
            msg.sendPlain(sender, "giver.list-header");
            for (GiverDefinition d : givers.all()) {
                msg.sendPlain(sender, "giver.list-line", "giver", d.id, "name", d.name,
                        "type", d.type.name().toLowerCase(Locale.ROOT), "pool", d.pool,
                        "world", String.valueOf(d.world), "x", (int) d.x, "y", (int) d.y, "z", (int) d.z,
                        "state", msg.raw(d.spawned ? "giver.state-spawned" : "giver.state-despawned"));
            }
        });
    }

    private static void moveHere(GiverManager givers, GiverDefinition d, Location loc) {
        d.setLocation(loc);
        d.spawned = true;
        var body = givers.body(d);
        if (body != null) {
            body.teleport(d.location());
        } else {
            givers.spawnBody(d);
        }
        givers.save();
    }

    private static GiverDefinition target(QuestGiverPlugin plugin, Player admin) {
        GiverDefinition d = plugin.givers().target(admin);
        if (d == null) {
            plugin.messages().send(admin, plugin.givers().all().isEmpty() ? "giver.none" : "giver.select-first");
        }
        return d;
    }
}
