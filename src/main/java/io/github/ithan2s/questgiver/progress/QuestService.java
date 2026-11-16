package io.github.ithan2s.questgiver.progress;

import io.github.ithan2s.questgiver.QuestGiverPlugin;
import io.github.ithan2s.questgiver.config.Messages;
import io.github.ithan2s.questgiver.event.EventManager;
import io.github.ithan2s.questgiver.itemtag.ItemTags;
import io.github.ithan2s.questgiver.npc.GiverDefinition;
import io.github.ithan2s.questgiver.quest.Objective;
import io.github.ithan2s.questgiver.quest.QuestDefinition;
import io.github.ithan2s.questgiver.quest.QuestRegistry;
import io.github.ithan2s.questgiver.quest.Tier;
import io.github.ithan2s.questgiver.quest.TierRules;
import io.github.ithan2s.questgiver.reward.RewardService;
import io.github.ithan2s.questgiver.squad.Squad;
import io.github.ithan2s.questgiver.squad.SquadManager;
import io.github.ithan2s.questgiver.storage.Storage;
import io.github.ithan2s.questgiver.util.Durations;
import io.github.ithan2s.questgiver.util.Items;
import io.github.ithan2s.questgiver.util.Money;
import io.github.ithan2s.questgiver.util.Text;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.block.Beehive;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Raider;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The quest engine: eligibility, accepting, progress, turn-in, completion, abandoning and timers. */
public final class QuestService {
    /** A player's view of an active quest: either their own (owner set) or their squad's (squad set). */
    public record Ref(ActiveQuest quest, PlayerData owner, Squad squad) {
        public void dirty() {
            if (owner != null) owner.markDirty();
            if (squad != null) squad.markDirty();
        }
    }

    public enum Eligibility { OK, COMPLETED, ACTIVE, LEVEL_LOCKED, CHAIN_LOCKED, TIER_LOCKED, HAS_ADVANCEMENT, COOLDOWN,
        EVENT_INACTIVE }

    public enum AcceptResult { OK, CAP_REACHED, NOT_ELIGIBLE, SQUAD_REQUIRED, NOT_LEADER, SQUAD_TOO_SMALL,
        SQUAD_HAS_QUEST }

    public enum TurnInResult { OK, NOT_READY, MISSING_ITEMS, WRONG_GIVER, EXPIRED, UNKNOWN_QUEST }

    /** Pending reward for a squad member who was offline at turn-in, delivered on their next login. */
    public record Pending(String questId, int deaths, String giverId, String giverName, String squad, String eventRun,
                          long completedAt) {
    }

    private final QuestGiverPlugin plugin;
    private Runnable onComplete = () -> { };

    public QuestService(QuestGiverPlugin plugin) {
        this.plugin = plugin;
    }

    public void onComplete(Runnable hook) {
        this.onComplete = hook;
    }

    private QuestRegistry registry() {
        return plugin.quests();
    }

    private PlayerDataManager data() {
        return plugin.data();
    }

    private SquadManager squads() {
        return plugin.squads();
    }

    private EventManager events() {
        return plugin.events();
    }

    private Messages msg() {
        return plugin.messages();
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    // ------------------------------------------------------------------------------------------------------------
    // Lookups
    // ------------------------------------------------------------------------------------------------------------

    public List<Ref> refs(Player p) {
        return refs(p.getUniqueId(), data().get(p));
    }

    public List<Ref> refs(UUID uuid, PlayerData d) {
        List<Ref> out = new ArrayList<>();
        if (d != null) {
            for (ActiveQuest q : d.active) {
                out.add(new Ref(q, d, null));
            }
        }
        Squad s = squads().of(uuid);
        if (s != null) {
            for (ActiveQuest q : s.active) {
                if (q.hasParticipant(uuid)) {
                    out.add(new Ref(q, null, s));
                }
            }
        }
        return out;
    }

    /** Finds an active quest by instance uid, quest id or (case-insensitive) name. */
    public Ref find(Player p, String token) {
        String t = token.toLowerCase(Locale.ROOT);
        for (Ref r : refs(p)) {
            QuestDefinition q = registry().get(r.quest().questId);
            if (r.quest().uid.equalsIgnoreCase(t) || r.quest().questId.equals(t)
                    || (q != null && Text.strip(q.name()).equalsIgnoreCase(token.replace('_', ' ')))) {
                return r;
            }
        }
        return null;
    }

    public List<String> activeIds(Player p) {
        return refs(p).stream().map(r -> r.quest().questId).toList();
    }

    public int cap(Player p) {
        List<Integer> limits = new ArrayList<>();
        for (PermissionAttachmentInfo info : p.getEffectivePermissions()) {
            String perm = info.getPermission();
            if (info.getValue() && perm.startsWith("questgiver.limit.")) {
                try {
                    limits.add(Integer.parseInt(perm.substring("questgiver.limit.".length())));
                } catch (NumberFormatException ignored) {
                    // questgiver.limit.* etc.
                }
            }
        }
        return TierRules.activeCap(cfg().getInt("quests.max-active", 5), limits);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Eligibility
    // ------------------------------------------------------------------------------------------------------------

    /** Completion key: the quest id, or "id@year" for event quests so next year's event can be played again. */
    public String completionKey(QuestDefinition q, String eventRun) {
        if (!q.isEvent()) {
            return q.id();
        }
        String run = eventRun != null ? eventRun : events().activeRun(q.event());
        String year = run == null ? "0" : run.substring(run.lastIndexOf(':') + 1);
        return q.id() + "@" + year;
    }

    public boolean isCompleted(PlayerData d, QuestDefinition q) {
        return d.completed.containsKey(completionKey(q, null));
    }

    public Map<Tier, Integer> tierThresholds() {
        Map<Tier, Integer> out = new EnumMap<>(Tier.class);
        for (Tier t : Tier.values()) {
            out.put(t, cfg().getInt("quests.tier-unlocks." + t.key(), 0));
        }
        return out;
    }

    public Eligibility eligibility(Player p, PlayerData d, QuestDefinition q) {
        if (q.isEvent() && !events().isActive(q.event())) {
            return Eligibility.EVENT_INACTIVE;
        }
        if (isCompleted(d, q)) {
            return Eligibility.COMPLETED;
        }
        for (Ref r : refs(p.getUniqueId(), d)) {
            if (r.quest().questId.equals(q.id())) {
                return Eligibility.ACTIVE;
            }
        }
        if (q.line() != null && q.level() > 1) {
            QuestDefinition prev = registry().level(q.line(), q.level() - 1);
            if (prev != null && !isCompleted(d, prev)) {
                return Eligibility.LEVEL_LOCKED;
            }
        }
        for (String req : q.requires()) {
            QuestDefinition r = registry().get(req);
            if (r != null && !isCompleted(d, r)) {
                return Eligibility.CHAIN_LOCKED;
            }
        }
        if (!TierRules.unlocked(q.tier(), d.completedCount, tierThresholds())) {
            return Eligibility.TIER_LOCKED;
        }
        for (Objective o : q.objectives()) {
            if (o instanceof Objective.Advancement a && hasAdvancement(p, a.key())) {
                return Eligibility.HAS_ADVANCEMENT;
            }
        }
        Long cooldown = d.abandonCooldowns.get(q.id());
        if (cooldown != null && cooldown > System.currentTimeMillis()) {
            return Eligibility.COOLDOWN;
        }
        return Eligibility.OK;
    }

    public static boolean hasAdvancement(Player p, NamespacedKey key) {
        Advancement adv = Bukkit.getAdvancement(key);
        return adv != null && p.getAdvancementProgress(adv).isDone();
    }

    // ------------------------------------------------------------------------------------------------------------
    // Accepting
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Accepts a quest for the player (or, with forSquad, for the player's squad, locking its current roster).
     *
     * @param timeLimitMs real-time limit (wandering quests), 0 for none
     */
    public AcceptResult accept(Player p, QuestDefinition q, boolean forSquad, String giverId, String giverName,
                               long timeLimitMs) {
        PlayerData d = data().get(p);
        if (refs(p).size() >= cap(p)) {
            return AcceptResult.CAP_REACHED;
        }
        Squad s = squads().of(p.getUniqueId());
        if (q.isSquadOnly() && !forSquad) {
            return AcceptResult.SQUAD_REQUIRED;
        }
        if (forSquad) {
            if (s == null || !s.isLeader(p.getUniqueId())) {
                return AcceptResult.NOT_LEADER;
            }
            int min = Math.max(q.squadSize(), cfg().getInt("squad.min-size-for-squad-quests", 3));
            if (q.isSquadOnly() && s.size() < min) {
                return AcceptResult.SQUAD_TOO_SMALL;
            }
            if (s.active.stream().anyMatch(a -> a.questId.equals(q.id()))) {
                return AcceptResult.SQUAD_HAS_QUEST;
            }
        }
        long now = System.currentTimeMillis();
        ActiveQuest aq = ActiveQuest.create(q.id(), giverId, giverName, now);
        if (q.isEvent()) {
            aq.eventRun = events().activeRun(q.event());
        }
        if (timeLimitMs > 0) {
            aq.expiresAt = now + timeLimitMs;
        }
        Ref ref;
        if (forSquad) {
            aq.squadId = s.id;
            aq.participants = new ArrayList<>(s.members);
            s.active.add(aq);
            ref = new Ref(aq, null, s);
        } else {
            d.active.add(aq);
            ref = new Ref(aq, d, null);
        }
        initialProgress(ref, q, p);
        if (d.tracked == null || find(p, d.tracked) == null) {
            d.tracked = aq.uid;
        }
        ref.dirty();
        d.markDirty();
        if (s != null && forSquad) {
            squads().save(s);
            for (UUID member : s.memberIds()) {
                Player m = Bukkit.getPlayer(member);
                if (m != null && m != p) {
                    msg().send(m, "squad.quest-accepted", "leader", p.getName(), "quest", q.name());
                    plugin.tracker().update(m);
                }
            }
        }
        afterProgress(ref, q, p, false);
        plugin.tracker().update(p);
        return AcceptResult.OK;
    }

    private void initialProgress(Ref ref, QuestDefinition q, Player p) {
        ActiveQuest aq = ref.quest();
        for (int i = 0; i < q.objectives().size(); i++) {
            Objective o = q.objectives().get(i);
            if (o instanceof Objective.Advancement a) {
                boolean has = hasAdvancement(p, a.key());
                if (!has && ref.squad() != null) {
                    for (String id : aq.participants) {
                        Player m = Bukkit.getPlayer(UUID.fromString(id));
                        has |= m != null && hasAdvancement(m, a.key());
                    }
                }
                if (has) {
                    aq.setProgress(i, 1);
                }
            } else if (o instanceof Objective.CompleteEvent c) {
                aq.setProgress(i, Math.min(c.amount(), countEventCompletions(data().get(p), c.event(), q.id(),
                        aq.eventRun)));
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Progress
    // ------------------------------------------------------------------------------------------------------------

    public void onKill(Player killer, LivingEntity victim, boolean resummonedDragon) {
        for (Ref r : refs(killer)) {
            QuestDefinition q = registry().get(r.quest().questId);
            if (q == null || r.quest().readyAt > 0) {
                continue;
            }
            boolean changed = false;
            for (int i = 0; i < q.objectives().size(); i++) {
                if (!(q.objectives().get(i) instanceof Objective.Kill k) || !matches(k, victim, resummonedDragon)) {
                    continue;
                }
                ActiveQuest aq = r.quest();
                if (k.night()) {
                    World w = victim.getWorld();
                    if (w.getEnvironment() != World.Environment.NORMAL || !NightRules.isNight(w.getTime())) {
                        continue;
                    }
                    String key = nightKey(w);
                    String idx = Integer.toString(i);
                    int n = NightRules.countKill(aq.nightKey.get(idx), key, aq.progress(i));
                    aq.nightKey.put(idx, key);
                    aq.setProgress(i, Math.min(n, k.amount()));
                } else if (aq.progress(i) < k.amount()) {
                    aq.setProgress(i, aq.progress(i) + 1);
                } else {
                    continue;
                }
                changed = true;
                actionBar(killer, q, k.label(), aq.progress(i), k.amount());
            }
            if (changed) {
                r.dirty();
                afterProgress(r, q, killer, true);
            }
        }
    }

    private static String nightKey(World w) {
        return w.getName() + ":" + NightRules.nightIndex(w.getFullTime());
    }

    private static boolean matches(Objective.Kill k, LivingEntity victim, boolean resummonedDragon) {
        boolean typeOk = k.types().isEmpty() ? k.anyHostile() && victim instanceof Enemy
                : k.types().contains(victim.getType());
        if (!typeOk) {
            return false;
        }
        if (k.captain() && !isCaptain(victim)) {
            return false;
        }
        return !k.resummoned() || (victim.getType() == EntityType.ENDER_DRAGON && resummonedDragon);
    }

    /** Raid and patrol captains carry the ominous banner on their head. */
    public static boolean isCaptain(LivingEntity e) {
        if (!(e instanceof Raider raider)) {
            return false;
        }
        if (raider.isPatrolLeader()) {
            return true;
        }
        EntityEquipment eq = raider.getEquipment();
        return eq != null && eq.getHelmet().getType() == Material.WHITE_BANNER;
    }

    public void onAdvancement(Player p, NamespacedKey key) {
        progressSimple(p, o -> o instanceof Objective.Advancement a && a.key().equals(key), 1, true);
    }

    public void onBreed(Player p, EntityType type) {
        progressSimple(p, o -> o instanceof Objective.Breed b && b.type() == type, 1, false);
    }

    public void onBuild(Player p, EntityType type) {
        progressSimple(p, o -> o instanceof Objective.Build b && b.type() == type, 1, false);
    }

    public void onCure(Player p) {
        progressSimple(p, o -> o instanceof Objective.Cure, 1, false);
    }

    private void progressSimple(Player p, java.util.function.Predicate<Objective> match, int step, boolean setToMax) {
        for (Ref r : refs(p)) {
            QuestDefinition q = registry().get(r.quest().questId);
            if (q == null || r.quest().readyAt > 0) {
                continue;
            }
            boolean changed = false;
            for (int i = 0; i < q.objectives().size(); i++) {
                Objective o = q.objectives().get(i);
                if (!match.test(o) || r.quest().progress(i) >= o.amount()) {
                    continue;
                }
                int value = setToMax ? o.amount() : r.quest().progress(i) + step;
                r.quest().setProgress(i, Math.min(o.amount(), value));
                changed = true;
                actionBar(p, q, o.label(), r.quest().progress(i), o.amount());
            }
            if (changed) {
                r.dirty();
                afterProgress(r, q, p, true);
            }
        }
    }

    /** Called every couple of seconds for each online player with a visit-biome objective. */
    public void onBiomeTick(Player p) {
        NamespacedKey biome = p.getLocation().getBlock().getBiome().getKey();
        for (Ref r : refs(p)) {
            QuestDefinition q = registry().get(r.quest().questId);
            if (q == null || r.quest().readyAt > 0) {
                continue;
            }
            boolean changed = false;
            for (int i = 0; i < q.objectives().size(); i++) {
                if (q.objectives().get(i) instanceof Objective.Visit v && v.biomes().contains(biome)) {
                    List<String> visited = r.quest().visited(i);
                    if (!visited.contains(biome.toString())) {
                        visited.add(biome.toString());
                        r.quest().setProgress(i, visited.size());
                        changed = true;
                        msg().send(p, "quest.biome-visited", "biome", Text.pretty(biome.getKey()), "quest", q.name(),
                                "have", visited.size(), "need", v.amount());
                    }
                }
            }
            if (changed) {
                r.dirty();
                afterProgress(r, q, p, true);
            }
        }
    }

    public boolean needsBiomeTick(Player p) {
        for (Ref r : refs(p)) {
            QuestDefinition q = registry().get(r.quest().questId);
            if (q != null && r.quest().readyAt == 0
                    && q.objectives().stream().anyMatch(o -> o instanceof Objective.Visit)) {
                return true;
            }
        }
        return false;
    }

    public void onDeath(Player p) {
        boolean any = false;
        for (Ref r : refs(p)) {
            if (r.quest().readyAt > 0) {
                continue;
            }
            r.quest().addDeath(p.getUniqueId());
            r.dirty();
            any = true;
        }
        if (any && cfg().getBoolean("death-penalty.enabled", true)) {
            msg().send(p, "quest.death-penalty", "percent",
                    Math.round(plugin.rewards().deathPenalty(maxDeaths(p)) * 100));
        }
    }

    private int maxDeaths(Player p) {
        int max = 0;
        for (Ref r : refs(p)) {
            max = Math.max(max, r.quest().deaths(p.getUniqueId()));
        }
        return max;
    }

    /** Re-counts "finish N event quests" objectives after the player finishes an event quest. */
    public void recountEventObjectives(Player p) {
        PlayerData d = data().get(p);
        for (Ref r : refs(p)) {
            QuestDefinition q = registry().get(r.quest().questId);
            if (q == null || r.quest().readyAt > 0) {
                continue;
            }
            boolean changed = false;
            for (int i = 0; i < q.objectives().size(); i++) {
                if (q.objectives().get(i) instanceof Objective.CompleteEvent c) {
                    int n = Math.min(c.amount(), countEventCompletions(d, c.event(), q.id(), r.quest().eventRun));
                    if (n != r.quest().progress(i)) {
                        r.quest().setProgress(i, n);
                        changed = true;
                    }
                }
            }
            if (changed) {
                r.dirty();
                afterProgress(r, q, p, true);
            }
        }
    }

    private int countEventCompletions(PlayerData d, String event, String excludeId, String eventRun) {
        String run = eventRun != null ? eventRun : events().activeRun(event);
        if (run == null) {
            return 0;
        }
        String suffix = "@" + run.substring(run.lastIndexOf(':') + 1);
        int n = 0;
        for (QuestDefinition q : registry().all()) {
            if (event.equals(q.event()) && !q.id().equals(excludeId) && d.completed.containsKey(q.id() + suffix)) {
                n++;
            }
        }
        return n;
    }

    private void actionBar(Player p, QuestDefinition q, String label, int have, int need) {
        String text = msg().raw("quest.progress-actionbar", "quest", q.name(), "label", label, "have", have,
                "need", need);
        if (!text.isEmpty()) {
            p.sendActionBar(Text.color(text));
        }
    }

    /** After any progress change: mark quests with no fetch objectives ready, notify, refresh trackers. */
    private void afterProgress(Ref r, QuestDefinition q, Player actor, boolean notify) {
        ActiveQuest aq = r.quest();
        if (aq.readyAt == 0 && objectivesDone(aq, q) && !q.onlyFetch()) {
            aq.readyAt = System.currentTimeMillis();
            r.dirty();
            for (Player m : participantsOnline(r, actor)) {
                if (!q.hasFetch()) {
                    msg().send(m, "quest.ready", "quest", q.name(), "giver",
                            aq.giverId.equals(QuestDefinition.WANDERING) ? msg().raw("quest.town-giver")
                                    : aq.giverName);
                    play(m, "effects.ready.sound");
                }
            }
        }
        for (Player m : participantsOnline(r, actor)) {
            plugin.tracker().update(m);
        }
    }

    public List<Player> participantsOnline(Ref r, Player fallback) {
        List<Player> out = new ArrayList<>();
        if (r.squad() == null) {
            Player owner = Bukkit.getPlayer(r.owner().uuid());
            if (owner != null) out.add(owner);
        } else {
            for (String id : r.quest().participants) {
                Player m = Bukkit.getPlayer(UUID.fromString(id));
                if (m != null && r.squad().isMember(m.getUniqueId())) out.add(m);
            }
        }
        if (out.isEmpty() && fallback != null) out.add(fallback);
        return out;
    }

    /** Every tracked (non-fetch) objective is finished. */
    public static boolean objectivesDone(ActiveQuest aq, QuestDefinition q) {
        for (int i = 0; i < q.objectives().size(); i++) {
            Objective o = q.objectives().get(i);
            if (!o.isFetch() && aq.progress(i) < o.amount()) {
                return false;
            }
        }
        return true;
    }

    /** Ready to hand in for this viewer: objectives done, fetch items in their inventory, not expired. */
    public boolean isReady(Player viewer, Ref r) {
        QuestDefinition q = registry().get(r.quest().questId);
        if (q == null || expired(r, q, System.currentTimeMillis())) {
            return false;
        }
        return objectivesDone(r.quest(), q) && (!q.hasFetch() || hasFetchItems(viewer, q));
    }

    // ------------------------------------------------------------------------------------------------------------
    // Fetch items
    // ------------------------------------------------------------------------------------------------------------

    public static boolean validFetchItem(ItemStack s, Objective.Fetch f) {
        if (s == null || s.isEmpty() || !f.materials().contains(s.getType()) || ItemTags.isTagged(s)) {
            return false;
        }
        return !f.withBees() || hasBees(s);
    }

    private static boolean hasBees(ItemStack s) {
        return s.getItemMeta() instanceof BlockStateMeta bsm && bsm.hasBlockState()
                && bsm.getBlockState() instanceof Beehive hive && hive.getEntityCount() > 0;
    }

    public int fetchCount(Player p, Objective.Fetch f) {
        if (f.distinct()) {
            Set<Material> types = EnumSet.noneOf(Material.class);
            for (ItemStack s : p.getInventory().getStorageContents()) {
                if (validFetchItem(s, f)) types.add(s.getType());
            }
            return types.size();
        }
        return Items.count(p, s -> validFetchItem(s, f));
    }

    /** Matching items the player holds that will be refused (creative, /give or reward items). */
    public int refusedCount(Player p, Objective.Fetch f) {
        return Items.count(p, s -> f.materials().contains(s.getType()) && ItemTags.isTagged(s));
    }

    public boolean hasFetchItems(Player p, QuestDefinition q) {
        for (Objective o : q.objectives()) {
            if (o instanceof Objective.Fetch f && fetchCount(p, f) < f.amount()) {
                return false;
            }
        }
        return true;
    }

    private void takeFetchItems(Player p, QuestDefinition q) {
        for (Objective o : q.objectives()) {
            if (!(o instanceof Objective.Fetch f)) {
                continue;
            }
            if (f.distinct()) {
                Set<Material> taken = new LinkedHashSet<>();
                for (ItemStack s : p.getInventory().getStorageContents()) {
                    if (taken.size() >= f.amount()) break;
                    if (validFetchItem(s, f) && !taken.contains(s.getType())) taken.add(s.getType());
                }
                for (Material m : taken) {
                    Items.remove(p, s -> s.getType() == m && validFetchItem(s, f), 1);
                }
            } else {
                Items.remove(p, s -> validFetchItem(s, f), f.amount());
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Turn-in and completion
    // ------------------------------------------------------------------------------------------------------------

    public boolean expired(Ref r, QuestDefinition q, long now) {
        ActiveQuest aq = r.quest();
        if (!aq.isTimed() || now <= aq.expiresAt) {
            return false;
        }
        // Objective quests finished within the limit can be handed in later; fetch quests must be delivered in time.
        return q.hasFetch() || aq.readyAt == 0 || aq.readyAt > aq.expiresAt;
    }

    public boolean canTurnInAt(Ref r, GiverDefinition giver) {
        String id = r.quest().giverId;
        return id.equals(giver.id) || id.equals(QuestDefinition.WANDERING) || id.equals("admin")
                || plugin.givers().get(id) == null;
    }

    /** Hands in a quest at a giver. Confirmation for rare fetch items happens in the menu before this. */
    public TurnInResult turnIn(Player p, GiverDefinition giver, Ref r) {
        QuestDefinition q = registry().get(r.quest().questId);
        if (q == null) {
            return TurnInResult.UNKNOWN_QUEST;
        }
        if (!canTurnInAt(r, giver)) {
            return TurnInResult.WRONG_GIVER;
        }
        if (expired(r, q, System.currentTimeMillis())) {
            fail(r, q, "quest.expired");
            return TurnInResult.EXPIRED;
        }
        if (!objectivesDone(r.quest(), q)) {
            return TurnInResult.NOT_READY;
        }
        if (q.hasFetch() && !hasFetchItems(p, q)) {
            return TurnInResult.MISSING_ITEMS;
        }
        if (q.hasFetch()) {
            takeFetchItems(p, q);
        }
        remove(r);
        ActiveQuest aq = r.quest();
        if (r.squad() == null) {
            completeFor(p, q, aq.deaths(p.getUniqueId()), aq.giverId, aq.giverName, null, aq.eventRun);
        } else {
            distributeSquad(p, q, r);
        }
        return TurnInResult.OK;
    }

    private void distributeSquad(Player turner, QuestDefinition q, Ref r) {
        Squad s = r.squad();
        ActiveQuest aq = r.quest();
        boolean range = cfg().getBoolean("squad.range-check.enabled", false);
        double max = cfg().getDouble("squad.range-check.max-distance", 100);
        boolean sameWorldOnly = cfg().getBoolean("squad.range-check.same-world", true);
        for (String id : aq.participants) {
            UUID uuid = UUID.fromString(id);
            if (!s.isMember(uuid)) {
                continue;
            }
            Player m = Bukkit.getPlayer(uuid);
            if (m == null) {
                if (range) {
                    msg().send(turner, "squad.member-missed-offline", "player", nameOf(uuid));
                    continue;
                }
                Pending pending = new Pending(q.id(), aq.deaths(uuid), aq.giverId, aq.giverName, s.name, aq.eventRun,
                        System.currentTimeMillis());
                String json = PlayerDataManager.GSON.toJson(pending);
                plugin.storage().run(c -> {
                    Storage.addPending(c, uuid, json);
                    return null;
                });
                continue;
            }
            if (range && m != turner && !inRange(turner, m, max, sameWorldOnly)) {
                msg().send(m, "squad.missed-out-of-range", "quest", q.name());
                msg().send(turner, "squad.member-missed-range", "player", m.getName());
                continue;
            }
            completeFor(m, q, aq.deaths(uuid), aq.giverId, aq.giverName, s.name, aq.eventRun);
        }
    }

    private static boolean inRange(Player a, Player b, double max, boolean sameWorldOnly) {
        if (!a.getWorld().equals(b.getWorld())) {
            // Different worlds only count when neither a distance nor the same world is required.
            return !sameWorldOnly && max <= 0;
        }
        return max <= 0 || a.getLocation().distanceSquared(b.getLocation()) <= max * max;
    }

    private String nameOf(UUID uuid) {
        String n = Bukkit.getOfflinePlayer(uuid).getName();
        return n == null ? uuid.toString().substring(0, 8) : n;
    }

    /**
     * Pays one player for a finished quest and records it (history, leaderboard, reputation, stats).
     *
     * @param squadName squad the quest was done with, or null for solo. Squad members who already finished the quest
     *                  get money and XP multiplied by squad.repeat-multiplier.
     */
    public void completeFor(Player m, QuestDefinition q, int deaths, String giverId, String giverName, String squadName,
                            String eventRun) {
        PlayerData d = data().get(m);
        String key = completionKey(q, eventRun);
        boolean repeat = squadName != null && d.completed.containsKey(key);
        String lore = null;
        if (q.isEvent()) {
            EventManager.Definition def = events().get(q.event());
            String year = eventRun == null ? String.valueOf(events().year(q.event()))
                    : eventRun.substring(eventRun.lastIndexOf(':') + 1);
            if (def != null) {
                lore = Text.format(def.lore(), "year", year, "event", Text.strip(def.displayName()));
            }
        }
        RewardService.Paid paid = plugin.rewards().grant(m, q.rewards(), deaths, repeat, lore, q.id());
        long now = System.currentTimeMillis();
        d.completed.putIfAbsent(key, now);
        d.completedCount++;
        d.questEarnings += paid.money();
        int rep = q.rewards().reputation() >= 0 ? q.rewards().reputation()
                : cfg().getInt("quests.reputation." + q.tier().key(), 1);
        d.reputation.merge(giverId == null ? "town" : giverId, rep, Integer::sum);
        d.markDirty();
        data().save(d);

        Storage.Payout payout = new Storage.Payout(m.getUniqueId(), m.getName(), q.isBounty() ? "bounty" : "quest",
                q.id(), Text.strip(q.name()), giverId, giverName == null ? "" : Text.strip(giverName), paid.money(),
                paid.xp(), paid.itemCount(), paid.itemSummary(), squadName, now);
        plugin.storage().run(c -> {
            Storage.insertPayout(c, payout);
            return null;
        });

        msg().send(m, "quest.completed", "quest", q.name());
        msg().sendPlain(m, "quest.reward-summary", "money", Money.format(paid.money()),
                "xp", formatXp(paid.xp()), "items", paid.items().isEmpty() ? msg().raw("quest.no-items")
                        : paid.itemSummary(), "reputation", rep);
        if (paid.deathPenalty() > 0) {
            msg().sendPlain(m, "quest.reward-penalty", "percent", Math.round(paid.deathPenalty() * 100),
                    "deaths", deaths);
        }
        if (repeat) {
            msg().sendPlain(m, "quest.reward-repeat", "percent",
                    Math.round(cfg().getDouble("squad.repeat-multiplier", 0.5) * 100));
        }
        celebrate(m);
        recountEventObjectives(m);
        if (d.tracked != null && find(m, d.tracked) == null) {
            d.tracked = null;
        }
        plugin.tracker().update(m);
        onComplete.run();
    }

    public static String formatXp(double xp) {
        if (Math.abs(xp - Math.rint(xp)) < 1e-6) {
            long n = Math.round(xp);
            return n + (n == 1 ? " level" : " levels");
        }
        return String.format(Locale.US, "%.1f levels", xp);
    }

    private void celebrate(Player m) {
        play(m, "effects.complete.sound");
        String particle = cfg().getString("effects.complete.particle", "TOTEM_OF_UNDYING");
        try {
            Particle type = Particle.valueOf(particle.toUpperCase(Locale.ROOT));
            if (type.getDataType() == Void.class) {
                m.getWorld().spawnParticle(type, m.getLocation().add(0, 1, 0),
                        cfg().getInt("effects.complete.particle-count", 40), 0.5, 0.8, 0.5, 0.2);
            }
        } catch (IllegalArgumentException ignored) {
            // Invalid particle name in config: no particles.
        }
    }

    /** Plays a sound from config (a sound key like "entity.player.levelup"; empty disables it). */
    public void play(Player p, String path) {
        String key = cfg().getString(path, "");
        if (key == null || key.isEmpty()) {
            return;
        }
        try {
            p.playSound(Sound.sound(Key.key(key.toLowerCase(Locale.ROOT)), Sound.Source.MASTER,
                    (float) cfg().getDouble(path.replace(".sound", ".volume"), 1.0),
                    (float) cfg().getDouble(path.replace(".sound", ".pitch"), 1.0)));
        } catch (Exception ignored) {
            // Invalid key.
        }
    }

    /** Delivers rewards for squad quests that were handed in while this player was offline. */
    public void deliverPending(Player p) {
        UUID uuid = p.getUniqueId();
        plugin.storage().supply(c -> Storage.takePending(c, uuid)).thenAccept(rows -> {
            if (rows.isEmpty()) return;
            io.github.ithan2s.questgiver.util.Tasks.main(() -> {
                if (!p.isOnline()) {
                    // Logged out again before delivery: put them back.
                    for (String json : rows) {
                        plugin.storage().run(c -> {
                            Storage.addPending(c, uuid, json);
                            return null;
                        });
                    }
                    return;
                }
                for (String json : rows) {
                    Pending pending = PlayerDataManager.GSON.fromJson(json, Pending.class);
                    QuestDefinition q = registry().get(pending.questId());
                    if (q == null) {
                        plugin.getLogger().warning("Dropping offline reward for unknown quest " + pending.questId());
                        continue;
                    }
                    msg().send(p, "squad.offline-reward", "quest", q.name(), "squad", pending.squad());
                    completeFor(p, q, pending.deaths(), pending.giverId(), pending.giverName(), pending.squad(),
                            pending.eventRun());
                }
            });
        });
    }

    // ------------------------------------------------------------------------------------------------------------
    // Abandon, fail, timers
    // ------------------------------------------------------------------------------------------------------------

    public void remove(Ref r) {
        if (r.owner() != null) {
            r.owner().active.remove(r.quest());
            r.owner().markDirty();
        } else if (r.squad() != null) {
            r.squad().active.remove(r.quest());
            squads().save(r.squad());
        }
    }

    /** @return false if a non-leader tried to abandon a squad quest */
    public boolean abandon(Player p, Ref r) {
        if (r.squad() != null && !r.squad().isLeader(p.getUniqueId())) {
            return false;
        }
        QuestDefinition q = registry().get(r.quest().questId);
        List<Player> notify = participantsOnline(r, p);
        remove(r);
        long cooldown = cfg().getLong("quests.abandon-cooldown-minutes", 10) * Durations.MINUTE;
        if (cooldown > 0) {
            data().get(p).abandonCooldowns.put(r.quest().questId, System.currentTimeMillis() + cooldown);
            data().get(p).markDirty();
        }
        String name = q == null ? r.quest().questId : q.name();
        for (Player m : notify) {
            msg().send(m, m == p ? "quest.abandoned" : "squad.quest-abandoned", "quest", name, "leader", p.getName());
            plugin.tracker().update(m);
        }
        return true;
    }

    public void fail(Ref r, QuestDefinition q, String messageKey) {
        List<Player> notify = participantsOnline(r, null);
        remove(r);
        String name = q == null ? r.quest().questId : q.name();
        for (Player m : notify) {
            msg().send(m, messageKey, "quest", name);
            plugin.tracker().update(m);
        }
    }

    /** Runs every second: fails timed quests whose real-time limit ran out (squads included, online or not). */
    public void tickTimers() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = data().getIfLoaded(p.getUniqueId());
            if (d == null) continue;
            for (ActiveQuest aq : new ArrayList<>(d.active)) {
                QuestDefinition q = registry().get(aq.questId);
                Ref r = new Ref(aq, d, null);
                if (q != null && expired(r, q, now)) {
                    fail(r, q, "quest.expired");
                }
            }
        }
        for (Squad s : squads().all()) {
            for (ActiveQuest aq : new ArrayList<>(s.active)) {
                QuestDefinition q = registry().get(aq.questId);
                Ref r = new Ref(aq, null, s);
                if (q != null && expired(r, q, now)) {
                    fail(r, q, "quest.expired");
                }
            }
        }
    }

    /** On login: expire timed quests that ran out while offline and drop quests of events that ended. */
    public void onJoin(Player p) {
        PlayerData d = data().get(p);
        long now = System.currentTimeMillis();
        for (ActiveQuest aq : new ArrayList<>(d.active)) {
            QuestDefinition q = registry().get(aq.questId);
            Ref r = new Ref(aq, d, null);
            if (q == null) {
                continue;
            }
            if (aq.eventRun != null && !events().isRunActive(aq.eventRun)) {
                fail(r, q, "quest.event-ended");
            } else if (expired(r, q, now)) {
                fail(r, q, "quest.expired-offline");
            }
        }
        d.abandonCooldowns.values().removeIf(until -> until < now);
        deliverPending(p);
    }

    /** An event ended: remove its unfinished quests from online players and squads (offline players on login). */
    public void onEventEnded(String run) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = data().getIfLoaded(p.getUniqueId());
            if (d == null) continue;
            for (ActiveQuest aq : new ArrayList<>(d.active)) {
                if (run.equals(aq.eventRun)) {
                    fail(new Ref(aq, d, null), registry().get(aq.questId), "quest.event-ended");
                }
            }
        }
        for (Squad s : squads().all()) {
            for (ActiveQuest aq : new ArrayList<>(s.active)) {
                if (run.equals(aq.eventRun)) {
                    fail(new Ref(aq, null, s), registry().get(aq.questId), "quest.event-ended");
                }
            }
        }
    }

    public void track(Player p, Ref r) {
        PlayerData d = data().get(p);
        d.tracked = r.quest().uid;
        d.markDirty();
        plugin.tracker().update(p);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Display helpers
    // ------------------------------------------------------------------------------------------------------------

    /** Progress for display; fetch objectives show what the viewer is carrying, night kills reset after dawn. */
    public int displayProgress(Player viewer, ActiveQuest aq, int index, Objective o) {
        if (o instanceof Objective.Fetch f) {
            return Math.min(f.amount(), fetchCount(viewer, f));
        }
        if (o instanceof Objective.Kill k && k.night() && aq.readyAt == 0) {
            String stored = aq.nightKey.get(Integer.toString(index));
            World w = viewer.getWorld();
            String stillValid = stored == null ? null : stored.substring(0, stored.lastIndexOf(':'));
            World storedWorld = stillValid == null ? null : Bukkit.getWorld(stillValid);
            if (storedWorld == null || !NightRules.isNight(storedWorld.getTime())
                    || !stored.equals(nightKey(storedWorld))) {
                return 0;
            }
            return aq.progress(index);
        }
        return Math.min(o.amount(), aq.progress(index));
    }

    public List<String> progressLines(Player viewer, ActiveQuest aq, QuestDefinition q) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < q.objectives().size(); i++) {
            Objective o = q.objectives().get(i);
            int have = displayProgress(viewer, aq, i, o);
            String key = o instanceof Objective.Advancement ? "progress.advancement" : "progress.line";
            lines.add(msg().raw(have >= o.amount() ? key + "-done" : key, "label", o.label(), "have", have,
                    "need", o.amount()));
        }
        return lines;
    }

    public String progressSummary(Player viewer, ActiveQuest aq, QuestDefinition q) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < q.objectives().size(); i++) {
            Objective o = q.objectives().get(i);
            parts.add(o.label() + ": " + displayProgress(viewer, aq, i, o) + "/" + o.amount());
        }
        return String.join(", ", parts);
    }

    public double progressFraction(Player viewer, ActiveQuest aq, QuestDefinition q) {
        double sum = 0;
        for (int i = 0; i < q.objectives().size(); i++) {
            Objective o = q.objectives().get(i);
            sum += Math.min(1.0, displayProgress(viewer, aq, i, o) / (double) Math.max(1, o.amount()));
        }
        return q.objectives().isEmpty() ? 0 : sum / q.objectives().size();
    }

    /** Reward lines for tooltips; with deaths/repeat the docked money and XP are shown. */
    public List<String> rewardLines(QuestDefinition q, int deaths, boolean repeat) {
        List<String> lines = new ArrayList<>();
        double mult = plugin.rewards().multiplier(deaths, repeat);
        if (q.rewards().money() > 0) {
            lines.add(msg().raw("menu.reward-money", "money",
                    Money.format(io.github.ithan2s.questgiver.reward.RewardMath.money(q.rewards().money(), mult))));
        }
        if (q.rewards().xp() > 0) {
            lines.add(msg().raw("menu.reward-xp", "xp",
                    formatXp(io.github.ithan2s.questgiver.reward.RewardMath.xp(q.rewards().xp(), mult))));
        }
        for (var item : q.rewards().items()) {
            lines.add(msg().raw("menu.reward-item", "item", item.describe()));
        }
        if (mult < 1.0) {
            int pct = (int) Math.round((1 - mult) * 100);
            lines.add(msg().raw("menu.reward-docked", "percent", pct, "deaths", deaths));
        }
        return lines;
    }
}
