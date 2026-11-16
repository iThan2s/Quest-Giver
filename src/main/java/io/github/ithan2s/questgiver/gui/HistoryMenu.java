package io.github.ithan2s.questgiver.gui;

import io.github.ithan2s.questgiver.QuestGiverPlugin;
import io.github.ithan2s.questgiver.config.Messages;
import io.github.ithan2s.questgiver.npc.GiverDefinition;
import io.github.ithan2s.questgiver.progress.QuestService;
import io.github.ithan2s.questgiver.storage.Storage;
import io.github.ithan2s.questgiver.util.Items;
import io.github.ithan2s.questgiver.util.Money;
import io.github.ithan2s.questgiver.util.Tasks;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/** Quests the player finished with this giver (newest first), with the date and rewards earned. */
public final class HistoryMenu extends Menu {
    private HistoryMenu(QuestGiverPlugin plugin, Player player, GiverDefinition giver, List<Storage.Payout> rows) {
        super(6, plugin.messages().raw("menu.history-title", "giver", giver.name));
        Messages msg = plugin.messages();
        SimpleDateFormat fmt = new SimpleDateFormat(msg.raw("menu.date-format"));
        int slot = 0;
        for (Storage.Payout p : rows) {
            if (slot >= 45) break;
            List<String> lore = new ArrayList<>(msg.list("menu.history-entry", "date", fmt.format(new Date(p.timestamp())),
                    "money", Money.format(p.money()), "xp", QuestService.formatXp(p.xp()),
                    "items", p.itemSummary() == null || p.itemSummary().isEmpty() ? msg.raw("quest.no-items")
                            : p.itemSummary(),
                    "giver", p.giverName() == null ? "" : p.giverName()));
            if (p.squad() != null) {
                lore.add(msg.raw("menu.history-squad", "squad", p.squad()));
            }
            set(slot++, Items.icon(p.source().equals("bounty") ? Material.DRAGON_EGG : Material.PAPER,
                    msg.raw("menu.history-name", "quest", p.questName()), lore));
        }
        if (rows.isEmpty()) {
            set(22, Items.icon(Material.GRAY_STAINED_GLASS_PANE, msg.raw("menu.history-empty"), List.of()));
        }
        set(49, Items.icon(Material.ARROW, msg.raw("menu.back"), List.of()),
                e -> GiverMenu.open(plugin, player, giver));
        fill(45, 53, Material.BLACK_STAINED_GLASS_PANE);
    }

    /** Loads history on the database thread, then opens the menu on the main thread. */
    public static void open(QuestGiverPlugin plugin, Player player, GiverDefinition giver) {
        UUID uuid = player.getUniqueId();
        String giverId = giver.id;
        long since = plugin.data().get(player).historySince;
        plugin.storage().supply(c -> Storage.history(c, uuid, giverId, since, 45)).thenAccept(rows -> Tasks.main(() -> {
            if (player.isOnline()) {
                new HistoryMenu(plugin, player, giver, rows).open(player);
            }
        }));
    }
}
