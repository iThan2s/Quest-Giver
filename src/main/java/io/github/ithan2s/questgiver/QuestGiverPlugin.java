package io.github.ithan2s.questgiver;

import io.github.ithan2s.questgiver.command.CommandTree;
import io.github.ithan2s.questgiver.command.Commands;
import io.github.ithan2s.questgiver.config.Messages;
import io.github.ithan2s.questgiver.dragon.DragonService;
import io.github.ithan2s.questgiver.extras.HeadDropListener;
import io.github.ithan2s.questgiver.extras.SpawnerListener;
import io.github.ithan2s.questgiver.leaderboard.LeaderboardService;
import io.github.ithan2s.questgiver.event.EventManager;
import io.github.ithan2s.questgiver.gui.GiverMenu;
import io.github.ithan2s.questgiver.gui.MenuHooks;
import io.github.ithan2s.questgiver.gui.MenuItems;
import io.github.ithan2s.questgiver.gui.MenuListener;
import io.github.ithan2s.questgiver.gui.QuestActions;
import io.github.ithan2s.questgiver.itemtag.ItemTagListener;
import io.github.ithan2s.questgiver.npc.GiverDefinition;
import io.github.ithan2s.questgiver.npc.GiverManager;
import io.github.ithan2s.questgiver.npc.NpcListener;
import io.github.ithan2s.questgiver.npc.SkinService;
import io.github.ithan2s.questgiver.progress.PlayerDataManager;
import io.github.ithan2s.questgiver.progress.ProgressListener;
import io.github.ithan2s.questgiver.progress.QuestService;
import io.github.ithan2s.questgiver.progress.TrackerService;
import io.github.ithan2s.questgiver.quest.QuestDefinition;
import io.github.ithan2s.questgiver.quest.QuestRegistry;
import io.github.ithan2s.questgiver.reward.Economy;
import io.github.ithan2s.questgiver.reward.RewardCapValidator;
import io.github.ithan2s.questgiver.reward.RewardService;
import io.github.ithan2s.questgiver.squad.SquadManager;
import io.github.ithan2s.questgiver.storage.Storage;
import io.github.ithan2s.questgiver.util.Keys;
import io.github.ithan2s.questgiver.wander.WanderManager;
import io.github.ithan2s.questgiver.weekly.WeeklyService;
import io.github.ithan2s.questgiver.wander.WanderMenu;
import io.github.ithan2s.questgiver.wander.Wanderer;
import io.github.ithan2s.questgiver.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public final class QuestGiverPlugin extends JavaPlugin implements Listener {
    private final Messages messages = new Messages(getLogger());
    private Storage storage;
    private PlayerDataManager data;
    private SkinService skins;
    private GiverManager givers;
    private NpcListener npcListener;
    private final QuestRegistry quests = new QuestRegistry(getLogger());
    private RewardService rewards;
    private SquadManager squads;
    private EventManager events;
    private QuestService questService;
    private TrackerService tracker;
    private ProgressListener progressListener;
    private ItemTagListener itemTagListener;
    private final MenuItems menuItems = new MenuItems(this);
    private final QuestActions questActions = new QuestActions(this);
    private final MenuHooks menuHooks = new MenuHooks();
    private WanderManager wanderers;
    private WeeklyService weekly;
    private DragonService dragon;
    private LeaderboardService leaderboard;
    private YamlConfiguration questsYaml;
    private YamlConfiguration giversYaml;

    @Override
    public void onEnable() {
        Tasks.init(this);
        Keys.init(this);
        saveBundled("config.yml");
        saveBundled("quests.yml");
        saveBundled("givers.yml");
        saveBundled("messages.yml");
        loadFiles();

        storage = new Storage(getLogger());
        try {
            storage.open(new File(getDataFolder(), "data.db"));
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Could not open the SQLite database; disabling QuestGiver.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        data = new PlayerDataManager(storage, getLogger());
        loadQuests();
        rewards = new RewardService(this, new Economy(getLogger()));
        rewards.economy().hook();
        squads = new SquadManager(storage, getLogger());
        squads.loadAll();
        events = new EventManager(storage, getLogger());
        events.load(getConfig());
        questService = new QuestService(this);
        tracker = new TrackerService(this);
        progressListener = new ProgressListener(this);
        itemTagListener = new ItemTagListener(this);
        configureItemTags();
        wanderers = new WanderManager(this);
        wanderers.load();
        weekly = new WeeklyService(this);
        dragon = new DragonService(this);
        menuHooks.add(weekly::decorate);
        menuHooks.add(dragon::decorate);
        leaderboard = new LeaderboardService(this);
        leaderboard.load();

        skins = new SkinService(storage);
        givers = new GiverManager(this, skins);
        givers.load(giversYaml);
        npcListener = new NpcListener();

        var pm = getServer().getPluginManager();
        pm.registerEvents(this, this);
        pm.registerEvents(givers, this);
        pm.registerEvents(npcListener, this);
        pm.registerEvents(progressListener, this);
        pm.registerEvents(itemTagListener, this);
        pm.registerEvents(new MenuListener(getLogger()), this);
        pm.registerEvents(wanderers, this);
        pm.registerEvents(dragon, this);
        pm.registerEvents(new SpawnerListener(this), this);
        pm.registerEvents(new HeadDropListener(this), this);
        npcListener.onWanderer((player, entity) -> {
            Wanderer w = wanderers.byEntity(entity);
            if (w == null) {
                entity.remove();
                return;
            }
            if (!player.hasPermission("questgiver.quest.use")) {
                messages.send(player, "general.no-permission");
                return;
            }
            wanderers.say(player, w, "greeting");
            new WanderMenu(this, player, w).open(player);
        });
        npcListener.onTown((player, entity) -> {
            GiverDefinition giver = givers.byEntity(entity);
            if (giver == null) {
                return;
            }
            if (!player.hasPermission("questgiver.quest.use")) {
                messages.send(player, "general.no-permission");
                return;
            }
            givers.greet(player, giver);
            GiverMenu.open(this, player, giver);
        });
        for (Player online : Bukkit.getOnlinePlayers()) {
            data.loadBlocking(online.getUniqueId(), online.getName());
        }

        CommandTree tree = new CommandTree(() -> messages, getLogger());
        Commands.registerAll(this, tree);
        registerCommand("quest", "Quest-Giver commands", List.of("quests"), tree);

        long autosave = Math.max(10, getConfig().getLong("storage.autosave-seconds", 60)) * 20L;
        Bukkit.getScheduler().runTaskTimer(this, data::saveDirty, autosave, autosave);
        Bukkit.getScheduler().runTaskTimer(this, squads::saveDirty, autosave, autosave);
        Bukkit.getScheduler().runTaskTimer(this, givers::ensureBodies, 40L, 100L);
        Bukkit.getScheduler().runTaskTimer(this, progressListener::onTick, 1L, 1L);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            questService.tickTimers();
            tracker.tickTimers();
        }, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (data.getIfLoaded(p.getUniqueId()) != null && questService.needsBiomeTick(p)) {
                    questService.onBiomeTick(p);
                }
            }
        }, 40L, 40L);
        Bukkit.getScheduler().runTaskTimer(this, events::tick, 200L, 1200L);
        Bukkit.getScheduler().runTaskTimer(this, wanderers::sweep, 600L, 600L);
        // DecentHolograms finishes loading after us; give it a moment before placing holograms.
        Bukkit.getScheduler().runTaskLater(this, leaderboard::refreshNow, 60L);
        events.listeners((def, run) -> Bukkit.getOnlinePlayers().forEach(p ->
                        messages.send(p, "event.started", "event", def.displayName())),
                (def, run) -> {
                    questService.onEventEnded(run);
                    Bukkit.getOnlinePlayers().forEach(p -> messages.send(p, "event.ended", "event", def.displayName()));
                });
        getLogger().info("QuestGiver enabled.");
    }

    @Override
    public void onDisable() {
        if (tracker != null) {
            tracker.clearAll();
        }
        if (leaderboard != null) {
            leaderboard.removeAllHolograms();
        }
        if (data != null) {
            data.saveAll();
        }
        if (squads != null) {
            squads.saveAll();
        }
        if (storage != null) {
            storage.close();
        }
    }

    private void saveBundled(String name) {
        if (!new File(getDataFolder(), name).exists()) {
            saveResource(name, false);
        }
    }

    private void loadFiles() {
        reloadConfig();
        messages.load(new File(getDataFolder(), "messages.yml"), getResource("messages.yml"));
        questsYaml = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "quests.yml"));
        giversYaml = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "givers.yml"));
    }

    private void loadQuests() {
        int n = quests.load(questsYaml, getConfig().getConfigurationSection("quests.fetch-rarity-defaults"));
        getLogger().info("Loaded " + n + " quests.");
        validateRewards();
    }

    /** Warns about quests that pay more than the dragon egg bounty or sit outside their tier's range. */
    private void validateRewards() {
        List<RewardCapValidator.Entry> entries = new ArrayList<>();
        for (QuestDefinition q : quests.all()) {
            entries.add(new RewardCapValidator.Entry(q.id(), q.tier().key(), q.pool(), q.isBounty(),
                    q.rewards().money(), q.rewards().xp()));
        }
        Map<String, RewardCapValidator.Range> ranges = new HashMap<>();
        var section = getConfig().getConfigurationSection("reward-caps.tier-ranges");
        if (section != null) {
            for (String tier : section.getKeys(false)) {
                List<Integer> r = section.getIntegerList(tier);
                if (r.size() == 4) {
                    ranges.put(tier, new RewardCapValidator.Range(r.get(0), r.get(1), r.get(2), r.get(3)));
                }
            }
        }
        List<String> warnings = RewardCapValidator.validate(entries,
                getConfig().getDouble("reward-caps.max-money", 5000), getConfig().getInt("reward-caps.max-xp", 20),
                ranges, getConfig().getBoolean("reward-caps.warn-tier-ranges", true));
        warnings.forEach(w -> getLogger().warning("[reward caps] " + w));
    }

    private void configureItemTags() {
        itemTagListener.configure(getConfig().getBoolean("item-tags.tag-creative", true),
                getConfig().getBoolean("item-tags.tag-commands", true),
                getConfig().getStringList("item-tags.watched-commands"));
    }

    /** /quest reload: re-reads every file and re-applies it without touching player progress. */
    public void reloadAll() {
        data.saveAll();
        squads.saveAll();
        loadFiles();
        loadQuests();
        events.load(getConfig());
        configureItemTags();
        rewards.economy().hook();
        tracker.refreshAll();
        wanderers.schedule();
        leaderboard.refreshNow();
        givers.load(giversYaml);
        givers.all().forEach(givers::refreshBody);
        givers.ensureBodies();
    }

    // ---- Player lifecycle ----

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        if (!data.preload(event.getUniqueId(), event.getName())) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, messages.component("general.data-load-failed"));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        data.get(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoinReminders(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline()) {
                weekly.onJoin(p);
            }
        }, 60L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        data.unload(event.getPlayer());
    }

    // ---- Accessors ----

    public Messages messages() {
        return messages;
    }

    public Storage storage() {
        return storage;
    }

    public PlayerDataManager data() {
        return data;
    }

    public SkinService skins() {
        return skins;
    }

    public GiverManager givers() {
        return givers;
    }

    public NpcListener npcListener() {
        return npcListener;
    }

    public QuestRegistry quests() {
        return quests;
    }

    public RewardService rewards() {
        return rewards;
    }

    public SquadManager squads() {
        return squads;
    }

    public EventManager events() {
        return events;
    }

    public QuestService questService() {
        return questService;
    }

    public TrackerService tracker() {
        return tracker;
    }

    public MenuItems menuItems() {
        return menuItems;
    }

    public QuestActions questActions() {
        return questActions;
    }

    public MenuHooks menuHooks() {
        return menuHooks;
    }

    public WanderManager wanderers() {
        return wanderers;
    }

    public WeeklyService weekly() {
        return weekly;
    }

    public DragonService dragon() {
        return dragon;
    }

    public LeaderboardService leaderboard() {
        return leaderboard;
    }

    public YamlConfiguration questsYaml() {
        return questsYaml;
    }

    public YamlConfiguration giversYaml() {
        return giversYaml;
    }
}
