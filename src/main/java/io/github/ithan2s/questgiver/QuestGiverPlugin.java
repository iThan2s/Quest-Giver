package io.github.ithan2s.questgiver;

import io.github.ithan2s.questgiver.command.CommandTree;
import io.github.ithan2s.questgiver.command.Commands;
import io.github.ithan2s.questgiver.config.Messages;
import io.github.ithan2s.questgiver.progress.PlayerDataManager;
import io.github.ithan2s.questgiver.storage.Storage;
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
import java.util.List;
import java.util.logging.Level;

public final class QuestGiverPlugin extends JavaPlugin implements Listener {
    private final Messages messages = new Messages(getLogger());
    private Storage storage;
    private PlayerDataManager data;
    private YamlConfiguration questsYaml;
    private YamlConfiguration giversYaml;

    @Override
    public void onEnable() {
        Tasks.init(this);
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

        getServer().getPluginManager().registerEvents(this, this);
        for (Player online : Bukkit.getOnlinePlayers()) {
            data.loadBlocking(online.getUniqueId(), online.getName());
        }

        CommandTree tree = new CommandTree(() -> messages, getLogger());
        Commands.registerAll(this, tree);
        registerCommand("quest", "Quest-Giver commands", List.of("quests"), tree);

        long autosave = Math.max(10, getConfig().getLong("storage.autosave-seconds", 60)) * 20L;
        Bukkit.getScheduler().runTaskTimer(this, data::saveDirty, autosave, autosave);
        getLogger().info("QuestGiver enabled.");
    }

    @Override
    public void onDisable() {
        if (data != null) {
            data.saveAll();
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

    /** /quest reload: re-reads every file and re-applies it without touching player progress. */
    public void reloadAll() {
        data.saveAll();
        loadFiles();
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

    public YamlConfiguration questsYaml() {
        return questsYaml;
    }

    public YamlConfiguration giversYaml() {
        return giversYaml;
    }
}
