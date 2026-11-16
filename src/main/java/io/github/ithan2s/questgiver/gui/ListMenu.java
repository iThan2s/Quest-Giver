package io.github.ithan2s.questgiver.gui;

import io.github.ithan2s.questgiver.QuestGiverPlugin;
import io.github.ithan2s.questgiver.progress.QuestService;
import io.github.ithan2s.questgiver.quest.QuestDefinition;
import io.github.ithan2s.questgiver.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /quest list: the player's quests and their squad's. Purple panes are in progress, green panes are ready to turn
 * in. Tooltips show the reward, who gave the quest and the time left. Clicking a pane tracks it.
 */
public final class ListMenu extends Menu {
    public ListMenu(QuestGiverPlugin plugin, Player player) {
        super(6, plugin.messages().raw("menu.list-title"));
        QuestService qs = plugin.questService();
        List<QuestService.Ref> refs = qs.refs(player);
        int slot = 0;
        for (QuestService.Ref r : refs) {
            if (slot >= 45) break;
            QuestDefinition q = plugin.quests().get(r.quest().questId);
            if (q == null) continue;
            boolean ready = qs.isReady(player, r);
            set(slot++, plugin.menuItems().active(player, r, q, ready, ready ? "menu.click-track-ready"
                    : "menu.click-track"), e -> {
                qs.track(player, r);
                plugin.messages().send(player, "quest.tracking", "quest", q.name());
                new ListMenu(plugin, player).open(player);
            });
        }
        if (refs.isEmpty()) {
            set(22, Items.icon(Material.GRAY_STAINED_GLASS_PANE, plugin.messages().raw("menu.list-empty"),
                    plugin.messages().list("menu.list-empty-lore")));
        }
        set(49, Items.icon(Material.BARRIER, plugin.messages().raw("menu.close"), List.of()),
                e -> player.closeInventory());
        fill(45, 53, Material.BLACK_STAINED_GLASS_PANE);
    }
}
