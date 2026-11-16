package io.github.ithan2s.questgiver.gui;

import io.github.ithan2s.questgiver.QuestGiverPlugin;
import io.github.ithan2s.questgiver.config.Messages;
import io.github.ithan2s.questgiver.npc.GiverDefinition;
import io.github.ithan2s.questgiver.progress.QuestService;
import io.github.ithan2s.questgiver.quest.QuestDefinition;
import io.github.ithan2s.questgiver.squad.Squad;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/** Accept and turn-in flows shared by the town and wandering menus. */
public final class QuestActions {
    private final QuestGiverPlugin plugin;

    public QuestActions(QuestGiverPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Accepts a quest, first asking squad leaders whether it's for them or the squad.
     *
     * @param soloAllowed false when the player may only take it with their squad (a wandering quest they finished)
     * @param onAccepted  runs after a successful accept with true if it was accepted for the squad
     */
    public void accept(Player p, QuestDefinition q, String giverId, String giverName, long timeLimitMs,
                       boolean soloAllowed, Consumer<Boolean> onAccepted) {
        Messages msg = plugin.messages();
        Squad squad = plugin.squads().of(p.getUniqueId());
        boolean leader = squad != null && squad.isLeader(p.getUniqueId()) && squad.size() > 1;
        if (!soloAllowed && !leader) {
            msg.send(p, "wander.already-done");
            plugin.questService().play(p, "effects.deny.sound");
            return;
        }
        if (q.isSquadOnly() && !leader) {
            msg.send(p, squad == null ? "accept.squad-required" : "accept.not-leader", "size",
                    Math.max(q.squadSize(), plugin.getConfig().getInt("squad.min-size-for-squad-quests", 3)));
            plugin.questService().play(p, "effects.deny.sound");
            return;
        }
        if (leader) {
            new ChoiceMenu(msg, plugin.menuItems().offer(p, q, false), squad.name, soloAllowed && !q.isSquadOnly(),
                    () -> doAccept(p, q, false, giverId, giverName, timeLimitMs, onAccepted),
                    () -> doAccept(p, q, true, giverId, giverName, timeLimitMs, onAccepted)).open(p);
            return;
        }
        doAccept(p, q, false, giverId, giverName, timeLimitMs, onAccepted);
    }

    private void doAccept(Player p, QuestDefinition q, boolean forSquad, String giverId, String giverName,
                          long timeLimitMs, Consumer<Boolean> onAccepted) {
        Messages msg = plugin.messages();
        QuestService.AcceptResult result = plugin.questService().accept(p, q, forSquad, giverId, giverName,
                timeLimitMs);
        switch (result) {
            case OK -> {
                msg.send(p, forSquad ? "accept.ok-squad" : "accept.ok", "quest", q.name());
                plugin.questService().play(p, "effects.accept.sound");
                onAccepted.accept(forSquad);
            }
            case CAP_REACHED -> deny(p, "accept.cap", "cap", plugin.questService().cap(p));
            case SQUAD_REQUIRED -> deny(p, "accept.squad-required", "size", q.squadSize());
            case NOT_LEADER -> deny(p, "accept.not-leader");
            case SQUAD_TOO_SMALL -> deny(p, "accept.squad-too-small", "size",
                    Math.max(q.squadSize(), plugin.getConfig().getInt("squad.min-size-for-squad-quests", 3)));
            case SQUAD_HAS_QUEST -> deny(p, "accept.squad-has-quest");
            case NOT_ELIGIBLE -> deny(p, "accept.not-eligible");
        }
    }

    private void deny(Player p, String key, Object... kv) {
        plugin.messages().send(p, key, kv);
        plugin.questService().play(p, "effects.deny.sound");
    }

    /** Hands in a ready quest at a town giver, confirming rare fetch items first. */
    public void turnIn(Player p, GiverDefinition giver, QuestService.Ref r, Runnable after) {
        QuestDefinition q = plugin.quests().get(r.quest().questId);
        if (q == null) {
            return;
        }
        Messages msg = plugin.messages();
        if (q.hasFetch() && q.rarity().needsConfirm()) {
            ConfirmMenu.ask(msg, p, msg.raw("menu.confirm-fetch-title"),
                    plugin.menuItems().active(p, r, q, true, "menu.confirm-fetch-hint"),
                    () -> doTurnIn(p, giver, r, q, after), after);
            return;
        }
        doTurnIn(p, giver, r, q, after);
    }

    private void doTurnIn(Player p, GiverDefinition giver, QuestService.Ref r, QuestDefinition q, Runnable after) {
        Messages msg = plugin.messages();
        QuestService.TurnInResult result = plugin.questService().turnIn(p, giver, r);
        switch (result) {
            case OK -> plugin.givers().say(p, giver, "turn-in", "player", p.getName(), "quest", q.name());
            case MISSING_ITEMS, NOT_READY -> {
                plugin.givers().say(p, giver, "empty-handed", "player", p.getName(), "quest", q.name());
                plugin.questService().play(p, "effects.deny.sound");
            }
            case WRONG_GIVER -> {
                GiverDefinition owner = plugin.givers().get(r.quest().giverId);
                deny(p, "turnin.wrong-giver", "giver", owner == null ? r.quest().giverName : owner.name);
            }
            case EXPIRED -> plugin.questService().play(p, "effects.deny.sound");
            case UNKNOWN_QUEST -> deny(p, "turnin.unknown");
        }
        if (after != null) {
            after.run();
        }
    }
}
