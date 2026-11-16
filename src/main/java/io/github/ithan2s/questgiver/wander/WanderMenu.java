package io.github.ithan2s.questgiver.wander;

import io.github.ithan2s.questgiver.QuestGiverPlugin;
import io.github.ithan2s.questgiver.config.Messages;
import io.github.ithan2s.questgiver.gui.Menu;
import io.github.ithan2s.questgiver.progress.PlayerData;
import io.github.ithan2s.questgiver.quest.QuestDefinition;
import io.github.ithan2s.questgiver.squad.Squad;
import io.github.ithan2s.questgiver.util.Durations;
import io.github.ithan2s.questgiver.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** A wandering quest-giver's single, exclusive quest. The first player or squad to accept claims it. */
public final class WanderMenu extends Menu {
    public WanderMenu(QuestGiverPlugin plugin, Player p, Wanderer w) {
        super(3, plugin.messages().raw("menu.wander-title", "giver", w.name));
        Messages msg = plugin.messages();
        QuestDefinition q = plugin.quests().get(w.questId);
        if (q == null) {
            set(13, Items.icon(Material.BARRIER, msg.raw("wander.no-quest"), List.of()));
            return;
        }
        Squad squad = plugin.squads().of(p.getUniqueId());
        boolean leader = squad != null && squad.isLeader(p.getUniqueId()) && squad.size() > 1;
        set(13, plugin.menuItems().offer(p, q, leader), e -> {
            p.closeInventory();
            WanderManager wm = plugin.wanderers();
            if (!wm.all().contains(w)) {
                msg.send(p, "wander.gone");
                return;
            }
            PlayerData d = plugin.data().get(p);
            if (plugin.questService().activeIds(p).contains(q.id())) {
                msg.send(p, "wander.already-active");
                return;
            }
            // Solo players never repeat a quest; a squad may take one its members already finished.
            boolean soloAllowed = !d.completed.containsKey(q.id());
            plugin.questActions().accept(p, q, QuestDefinition.WANDERING, w.name, wm.timeLimitMs(q), soloAllowed,
                    forSquad -> wm.claimed(w, p));
        });
        long left = Durations.remaining(w.despawnAt, System.currentTimeMillis());
        set(22, Items.icon(Material.CLOCK, msg.raw("wander.leaves-in", "time", Durations.format(left)),
                msg.list("wander.leaves-lore")));
        fill(0, 26, Material.BROWN_STAINED_GLASS_PANE);
    }
}
