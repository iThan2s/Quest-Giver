package io.github.ithan2s.questgiver.gui;

import io.github.ithan2s.questgiver.QuestGiverPlugin;
import io.github.ithan2s.questgiver.config.Messages;
import io.github.ithan2s.questgiver.npc.GiverDefinition;
import io.github.ithan2s.questgiver.progress.PlayerData;
import io.github.ithan2s.questgiver.progress.QuestService;
import io.github.ithan2s.questgiver.quest.QuestDefinition;
import io.github.ithan2s.questgiver.squad.Squad;
import io.github.ithan2s.questgiver.util.Items;
import io.github.ithan2s.questgiver.util.Money;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The town quest-giver's menu (6 rows):
 * <pre>
 *  row 1  offers (5 quests to pick from; the last slot is the event slot while an event runs)
 *  row 2  ready to turn in (green panes) - click to claim
 *  row 3-4 active quests in progress (purple panes) - click to track, shift-right-click to abandon
 *  row 6  history · weekly reward · your stats · dragon egg bounty · close
 * </pre>
 */
public final class GiverMenu extends Menu {
    public static final int HISTORY = 45;
    public static final int WEEKLY = 47;
    public static final int STATS = 49;
    public static final int BOUNTY = 51;
    public static final int CLOSE = 53;

    private final QuestGiverPlugin plugin;
    private final Player player;
    private final GiverDefinition giver;

    public GiverMenu(QuestGiverPlugin plugin, Player player, GiverDefinition giver) {
        super(6, plugin.messages().raw("menu.giver-title", "giver", giver.name));
        this.plugin = plugin;
        this.player = player;
        this.giver = giver;
        build();
    }

    public static void open(QuestGiverPlugin plugin, Player player, GiverDefinition giver) {
        new GiverMenu(plugin, player, giver).open(player);
    }

    private void reopen() {
        if (player.isOnline()) {
            open(plugin, player, giver);
        }
    }

    private void build() {
        Messages msg = plugin.messages();
        QuestService qs = plugin.questService();
        MenuItems items = plugin.menuItems();
        Squad squad = plugin.squads().of(player.getUniqueId());
        boolean leader = squad != null && squad.isLeader(player.getUniqueId()) && squad.size() > 1;

        // Row 1: offers, centred.
        List<String> offers = qs.offers(player, giver);
        int start = (9 - offers.size()) / 2;
        for (int i = 0; i < offers.size(); i++) {
            String id = offers.get(i);
            QuestDefinition q = plugin.quests().get(id);
            int slot = start + i;
            if (q == null) {
                set(slot, Items.icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE, msg.raw("menu.no-offer"),
                        msg.list("menu.no-offer-lore")));
                continue;
            }
            set(slot, items.offer(player, q, leader), e -> plugin.questActions().accept(player, q, giver.id,
                    giver.name, 0, true, forSquad -> {
                        qs.consumeOffer(player, giver, q.id());
                        plugin.givers().say(player, giver, "accept", "player", player.getName(), "quest", q.name());
                        reopen();
                    }));
        }

        // Rows 2-4: ready and in-progress quests.
        List<QuestService.Ref> ready = new ArrayList<>();
        List<QuestService.Ref> inProgress = new ArrayList<>();
        for (QuestService.Ref r : qs.refs(player)) {
            if (qs.isReady(player, r) && qs.canTurnInAt(r, giver)) ready.add(r);
            else inProgress.add(r);
        }
        int slot = 9;
        for (QuestService.Ref r : ready) {
            if (slot > 17) break;
            QuestDefinition q = plugin.quests().get(r.quest().questId);
            if (q == null) continue;
            set(slot++, items.active(player, r, q, true, "menu.click-turn-in"),
                    e -> plugin.questActions().turnIn(player, giver, r, this::reopen));
        }
        slot = 18;
        for (QuestService.Ref r : inProgress) {
            if (slot > 35) break;
            QuestDefinition q = plugin.quests().get(r.quest().questId);
            if (q == null) continue;
            String hint = !qs.canTurnInAt(r, giver) ? "menu.click-other-giver" : "menu.click-track";
            set(slot++, items.active(player, r, q, false, hint), e -> {
                if (e.getClick() == ClickType.SHIFT_RIGHT) {
                    confirmAbandon(r, q);
                    return;
                }
                if (q.hasFetch() && QuestService.objectivesDone(r.quest(), q)) {
                    plugin.givers().say(player, giver, "empty-handed", "player", player.getName(), "quest", q.name());
                }
                qs.track(player, r);
                msg.send(player, "quest.tracking", "quest", q.name());
                reopen();
            });
        }
        if (ready.isEmpty()) {
            set(13, Items.icon(Material.GRAY_STAINED_GLASS_PANE, msg.raw("menu.nothing-ready"), List.of()));
        }

        // Bottom row.
        set(HISTORY, Items.icon(Material.WRITABLE_BOOK, msg.raw("menu.history"), msg.list("menu.history-lore")),
                e -> HistoryMenu.open(plugin, player, giver));
        set(STATS, statsItem());
        set(CLOSE, Items.icon(Material.BARRIER, msg.raw("menu.close"), List.of()), e -> player.closeInventory());
        plugin.menuHooks().decorate(this, player, giver);

        fill(0, 8, Material.BLACK_STAINED_GLASS_PANE);
        fill(36, 53, Material.BLACK_STAINED_GLASS_PANE);
    }

    /** Lets other modules (weekly reward, dragon bounty) place their buttons. */
    public void button(int slot, ItemStack item, Runnable onClick) {
        set(slot, item, e -> onClick.run());
    }

    private ItemStack statsItem() {
        Messages msg = plugin.messages();
        PlayerData d = plugin.data().get(player);
        QuestService qs = plugin.questService();
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        head.editMeta(org.bukkit.inventory.meta.SkullMeta.class, m -> m.setOwningPlayer(player));
        List<String> lore = msg.list("menu.stats-lore", "completed", d.completedCount,
                "earned", Money.format(d.questEarnings), "reputation", d.reputation.getOrDefault(giver.id, 0),
                "total-reputation", d.totalReputation(), "active", qs.activeCount(player), "cap", qs.cap(player));
        return Items.named(head, io.github.ithan2s.questgiver.util.Text.item(msg.raw("menu.stats", "player",
                player.getName())), io.github.ithan2s.questgiver.util.Text.itemLines(lore));
    }

    private void confirmAbandon(QuestService.Ref r, QuestDefinition q) {
        Messages msg = plugin.messages();
        if (r.squad() != null && !r.squad().isLeader(player.getUniqueId())) {
            msg.send(player, "squad.leader-only");
            return;
        }
        ConfirmMenu.ask(msg, player, msg.raw("menu.abandon-title"),
                plugin.menuItems().active(player, r, q, false, "menu.abandon-hint"), () -> {
                    plugin.questService().abandon(player, r);
                    reopen();
                }, this::reopen);
    }

    public Player player() {
        return player;
    }

    public GiverDefinition giver() {
        return giver;
    }
}
