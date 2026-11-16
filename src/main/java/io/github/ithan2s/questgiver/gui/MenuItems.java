package io.github.ithan2s.questgiver.gui;

import io.github.ithan2s.questgiver.QuestGiverPlugin;
import io.github.ithan2s.questgiver.config.Messages;
import io.github.ithan2s.questgiver.progress.ActiveQuest;
import io.github.ithan2s.questgiver.progress.PlayerData;
import io.github.ithan2s.questgiver.progress.QuestService;
import io.github.ithan2s.questgiver.quest.Objective;
import io.github.ithan2s.questgiver.quest.QuestDefinition;
import io.github.ithan2s.questgiver.quest.QuestRegistry;
import io.github.ithan2s.questgiver.quest.Tier;
import io.github.ithan2s.questgiver.squad.Squad;
import io.github.ithan2s.questgiver.util.Durations;
import io.github.ithan2s.questgiver.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Builds the quest icons and tooltips used by every menu. */
public final class MenuItems {
    private final QuestGiverPlugin plugin;

    public MenuItems(QuestGiverPlugin plugin) {
        this.plugin = plugin;
    }

    private Messages msg() {
        return plugin.messages();
    }

    public String tierName(Tier tier) {
        return msg().raw("tier." + tier.key());
    }

    private String typeName(QuestDefinition q) {
        Objective o = q.objectives().getFirst();
        String key = switch (o) {
            case Objective.Advancement a -> "advancement";
            case Objective.Kill k -> "kill";
            case Objective.Fetch f -> "fetch";
            case Objective.Visit v -> "visit";
            case Objective.Breed b -> "breed";
            case Objective.Build b -> "build";
            case Objective.Cure c -> "cure";
            case Objective.CompleteEvent c -> "complete";
        };
        return msg().raw("type." + key);
    }

    /** Lines describing a quest (shared by offers and active quests). */
    private List<String> header(QuestDefinition q) {
        List<String> lore = new ArrayList<>();
        lore.add(msg().raw("menu.description", "description", q.description()));
        lore.add("");
        lore.add(msg().raw("menu.tier", "tier", tierName(q.tier()), "type", typeName(q)));
        if (q.line() != null) {
            QuestRegistry reg = plugin.quests();
            int max = 0;
            for (QuestDefinition other : reg.all()) {
                if (q.line().equals(other.line())) max = Math.max(max, other.level());
            }
            lore.add(msg().raw("menu.level", "level", q.level(), "max", max));
        }
        if (q.isSquadOnly()) {
            lore.add(msg().raw("menu.squad-only", "size", Math.max(q.squadSize(),
                    plugin.getConfig().getInt("squad.min-size-for-squad-quests", 3))));
        }
        if (q.isEvent()) {
            var def = plugin.events().get(q.event());
            lore.add(msg().raw("menu.event-quest", "event", def == null ? q.event() : def.displayName()));
        }
        if (q.hasFetch() && q.rarity().needsConfirm()) {
            lore.add(msg().raw("menu.rarity", "rarity", msg().raw("rarity." + q.rarity().key())));
        }
        return lore;
    }

    public ItemStack offer(Player viewer, QuestDefinition q, boolean squadLeader) {
        List<String> lore = header(q);
        if (q.isWandering()) {
            lore.add(msg().raw("menu.time-limit", "minutes", q.timeLimitMinutes() > 0 ? q.timeLimitMinutes()
                    : plugin.getConfig().getInt("wandering.quest-time-limit-minutes", 60)));
        }
        lore.add("");
        lore.add(msg().raw("menu.rewards"));
        for (String line : plugin.questService().rewardLines(q, 0, false)) {
            lore.add(msg().raw("menu.reward-prefix") + line);
        }
        lore.add("");
        lore.add(msg().raw(q.isSquadOnly() ? "menu.click-accept-squad" : squadLeader ? "menu.click-accept-choice"
                : "menu.click-accept"));
        return Items.icon(q.icon(), msg().raw("menu.offer-name", "tier-color", tierColor(q.tier()), "quest", q.name()),
                lore);
    }

    private String tierColor(Tier tier) {
        return msg().raw("tier-color." + tier.key());
    }

    /** Purple (in progress) or green (ready) pane for an active quest. */
    public ItemStack active(Player viewer, QuestService.Ref r, QuestDefinition q, boolean ready, String clickKey) {
        QuestService qs = plugin.questService();
        ActiveQuest aq = r.quest();
        List<String> lore = new ArrayList<>(header(q));
        lore.add("");
        lore.add(msg().raw("menu.progress"));
        for (String line : qs.progressLines(viewer, aq, q)) {
            lore.add(msg().raw("menu.reward-prefix") + line);
        }
        if (q.hasFetch()) {
            for (Objective o : q.objectives()) {
                if (o instanceof Objective.Fetch f) {
                    int refused = qs.refusedCount(viewer, f);
                    if (refused > 0) lore.add(msg().raw("menu.refused-items", "count", refused));
                }
            }
        }
        lore.add("");
        PlayerData d = plugin.data().get(viewer);
        boolean repeat = r.squad() != null && d.completed.containsKey(qs.completionKey(q, aq.eventRun));
        lore.add(msg().raw("menu.rewards"));
        for (String line : qs.rewardLines(q, aq.deaths(viewer.getUniqueId()), repeat)) {
            lore.add(msg().raw("menu.reward-prefix") + line);
        }
        lore.add("");
        lore.add(msg().raw("menu.given-by", "giver", aq.giverName == null ? "?" : aq.giverName));
        if (r.squad() != null) {
            Squad s = r.squad();
            lore.add(msg().raw("menu.squad-quest", "squad", s.name, "members", aq.participants.size()));
        }
        if (aq.isTimed()) {
            boolean frozen = !q.hasFetch() && aq.readyAt > 0;
            lore.add(frozen ? msg().raw("menu.time-done") : msg().raw("menu.time-left", "time",
                    Durations.format(Durations.remaining(aq.expiresAt, System.currentTimeMillis()))));
        }
        lore.add("");
        lore.add(msg().raw(clickKey));
        Material pane = ready ? Material.LIME_STAINED_GLASS_PANE : Material.PURPLE_STAINED_GLASS_PANE;
        String name = msg().raw(ready ? "menu.ready-name" : "menu.active-name", "quest", q.name());
        return Items.icon(pane, name, lore);
    }
}
