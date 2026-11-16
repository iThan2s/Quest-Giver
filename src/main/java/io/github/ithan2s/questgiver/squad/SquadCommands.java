package io.github.ithan2s.questgiver.squad;

import io.github.ithan2s.questgiver.QuestGiverPlugin;
import io.github.ithan2s.questgiver.command.CommandTree;
import io.github.ithan2s.questgiver.config.Messages;
import io.github.ithan2s.questgiver.progress.ActiveQuest;
import io.github.ithan2s.questgiver.quest.QuestDefinition;
import io.github.ithan2s.questgiver.util.Text;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * /quest squad assemble|add|accept|leave|kick|list|disband. The player who assembles a squad leads it; only the
 * leader adds, kicks, disbands or accepts quests for the squad. If the leader leaves, the next member (by join
 * order) is promoted.
 */
public final class SquadCommands {
    private static final String PERM = "questgiver.squad.use";

    private SquadCommands() {
    }

    public static void register(QuestGiverPlugin plugin, CommandTree tree) {
        Messages msg = plugin.messages();
        SquadManager squads = plugin.squads();

        tree.register("squad assemble", PERM, true, (sender, args) -> {
            CommandTree.require(args.length >= 1);
            Player p = (Player) sender;
            String name = String.join(" ", args).trim();
            if (squads.of(p.getUniqueId()) != null) {
                msg.send(p, "squad.already-in");
                return;
            }
            if (!name.matches("[A-Za-z0-9_ -]{2,16}")) {
                msg.send(p, "squad.bad-name");
                return;
            }
            if (squads.nameTaken(name)) {
                msg.send(p, "squad.name-taken", "squad", name);
                return;
            }
            squads.create(name, p.getUniqueId());
            msg.send(p, "squad.assembled", "squad", name);
        });

        tree.register("squad add", PERM, true, (sender, args) -> {
            CommandTree.require(args.length == 1);
            Player p = (Player) sender;
            Squad s = leaderSquad(plugin, p);
            if (s == null) return;
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                msg.send(p, "squad.player-offline", "player", args[0]);
                return;
            }
            if (target == p || s.isMember(target.getUniqueId())) {
                msg.send(p, "squad.already-member", "player", target.getName());
                return;
            }
            if (squads.of(target.getUniqueId()) != null) {
                msg.send(p, "squad.target-in-squad", "player", target.getName());
                return;
            }
            if (s.size() + pendingInvites(s) >= plugin.getConfig().getInt("squad.max-size", 6)) {
                msg.send(p, "squad.full", "max", plugin.getConfig().getInt("squad.max-size", 6));
                return;
            }
            long expires = System.currentTimeMillis()
                    + plugin.getConfig().getLong("squad.invite-expire-seconds", 120) * 1000L;
            s.invites.put(target.getUniqueId(), expires);
            msg.send(p, "squad.invite-sent", "player", target.getName());
            String text = msg.raw("prefix") + msg.raw("squad.invite-received", "leader", p.getName(), "squad", s.name);
            target.sendMessage(Text.color(text)
                    .clickEvent(ClickEvent.runCommand("/quest squad accept " + s.name))
                    .hoverEvent(HoverEvent.showText(Text.color(msg.raw("squad.invite-hover")))));
        }, (sender, args) -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());

        tree.register("squad accept", PERM, true, (sender, args) -> {
            Player p = (Player) sender;
            if (squads.of(p.getUniqueId()) != null) {
                msg.send(p, "squad.already-in");
                return;
            }
            Squad invited = null;
            long now = System.currentTimeMillis();
            for (Squad s : squads.all()) {
                Long until = s.invites.get(p.getUniqueId());
                if (until == null) continue;
                if (until < now) {
                    s.invites.remove(p.getUniqueId());
                    continue;
                }
                if (args.length == 0 || s.name.equalsIgnoreCase(String.join(" ", args))) {
                    invited = s;
                }
            }
            if (invited == null) {
                msg.send(p, "squad.no-invite");
                return;
            }
            if (invited.size() >= plugin.getConfig().getInt("squad.max-size", 6)) {
                msg.send(p, "squad.full", "max", plugin.getConfig().getInt("squad.max-size", 6));
                return;
            }
            invited.invites.remove(p.getUniqueId());
            invited.members.add(p.getUniqueId().toString());
            squads.save(invited);
            broadcast(invited, msg.raw("squad.joined", "player", p.getName(), "squad", invited.name), plugin);
            if (!invited.active.isEmpty()) {
                msg.send(p, "squad.joined-locked");
            }
        });

        tree.register("squad leave", PERM, true, (sender, args) -> {
            Player p = (Player) sender;
            Squad s = squads.of(p.getUniqueId());
            if (s == null) {
                msg.send(p, "squad.not-in");
                return;
            }
            removeMember(plugin, s, p.getUniqueId(), p.getName(), "squad.left");
            msg.send(p, "squad.you-left", "squad", s.name);
            plugin.tracker().update(p);
        });

        tree.register("squad kick", PERM, true, (sender, args) -> {
            CommandTree.require(args.length == 1);
            Player p = (Player) sender;
            Squad s = leaderSquad(plugin, p);
            if (s == null) return;
            UUID target = null;
            String targetName = args[0];
            for (UUID m : s.memberIds()) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(m);
                if (op.getName() != null && op.getName().equalsIgnoreCase(args[0])) {
                    target = m;
                    targetName = op.getName();
                }
            }
            if (target == null || target.equals(p.getUniqueId())) {
                msg.send(p, "squad.not-member", "player", args[0]);
                return;
            }
            removeMember(plugin, s, target, targetName, "squad.kicked");
            Player kicked = Bukkit.getPlayer(target);
            if (kicked != null) {
                msg.send(kicked, "squad.you-were-kicked", "squad", s.name);
                plugin.tracker().update(kicked);
            }
        }, (sender, args) -> {
            if (!(sender instanceof Player p)) return List.of();
            Squad s = squads.of(p.getUniqueId());
            if (s == null) return List.of();
            List<String> names = new ArrayList<>();
            for (UUID m : s.memberIds()) {
                String n = Bukkit.getOfflinePlayer(m).getName();
                if (n != null && !m.equals(p.getUniqueId())) names.add(n);
            }
            return names;
        });

        tree.register("squad list", PERM, true, (sender, args) -> {
            Player p = (Player) sender;
            Squad s = squads.of(p.getUniqueId());
            if (s == null) {
                msg.send(p, "squad.not-in");
                return;
            }
            msg.sendPlain(p, "squad.list-header", "squad", s.name, "size", s.size(),
                    "max", plugin.getConfig().getInt("squad.max-size", 6));
            for (UUID m : s.memberIds()) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(m);
                msg.sendPlain(p, s.isLeader(m) ? "squad.list-leader" : "squad.list-member",
                        "player", op.getName() == null ? m.toString().substring(0, 8) : op.getName(),
                        "status", msg.raw(op.isOnline() ? "squad.online" : "squad.offline"));
            }
            if (!s.active.isEmpty()) {
                msg.sendPlain(p, "squad.list-quests");
                for (ActiveQuest aq : s.active) {
                    QuestDefinition q = plugin.quests().get(aq.questId);
                    if (q == null) continue;
                    msg.sendPlain(p, "squad.list-quest", "quest", q.name(),
                            "progress", plugin.questService().progressSummary(p, aq, q),
                            "members", aq.participants.size(),
                            "you", msg.raw(aq.hasParticipant(p.getUniqueId()) ? "squad.list-you-in"
                                    : "squad.list-you-out"));
                }
            }
        });

        tree.register("squad disband", PERM, true, (sender, args) -> {
            Player p = (Player) sender;
            Squad s = leaderSquad(plugin, p);
            if (s == null) return;
            broadcast(s, msg.raw("squad.disbanded", "squad", s.name), plugin);
            squads.delete(s);
            for (UUID m : s.memberIds()) {
                Player online = Bukkit.getPlayer(m);
                if (online != null) plugin.tracker().update(online);
            }
        });
    }

    private static int pendingInvites(Squad s) {
        long now = System.currentTimeMillis();
        s.invites.values().removeIf(until -> until < now);
        return s.invites.size();
    }

    private static Squad leaderSquad(QuestGiverPlugin plugin, Player p) {
        Squad s = plugin.squads().of(p.getUniqueId());
        if (s == null) {
            plugin.messages().send(p, "squad.not-in");
            return null;
        }
        if (!s.isLeader(p.getUniqueId())) {
            plugin.messages().send(p, "squad.leader-only");
            return null;
        }
        return s;
    }

    /**
     * Removes a member (leave or kick). They lose their share of every squad quest. If the leader leaves, the next
     * member by join order becomes leader; an empty squad is deleted.
     */
    public static void removeMember(QuestGiverPlugin plugin, Squad s, UUID member, String name, String messageKey) {
        Messages msg = plugin.messages();
        Squad.Removal result = s.remove(member);
        if (result.empty()) {
            plugin.squads().delete(s);
            return;
        }
        broadcast(s, msg.raw(messageKey, "player", name, "squad", s.name), plugin);
        if (result.newLeader() != null) {
            String newLeader = Bukkit.getOfflinePlayer(UUID.fromString(result.newLeader())).getName();
            broadcast(s, msg.raw("squad.promoted", "player", newLeader == null ? "?" : newLeader), plugin);
        }
        plugin.squads().save(s);
    }

    private static void broadcast(Squad s, String line, QuestGiverPlugin plugin) {
        String text = plugin.messages().raw("squad.prefix") + line;
        for (UUID m : s.memberIds()) {
            Player online = Bukkit.getPlayer(m);
            if (online != null) {
                online.sendMessage(Text.color(text));
            }
        }
    }
}
