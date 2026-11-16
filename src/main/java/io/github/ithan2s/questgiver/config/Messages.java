package io.github.ithan2s.questgiver.config;

import io.github.ithan2s.questgiver.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/** All player-facing text, loaded from messages.yml with the bundled file as fallback for missing keys. */
public final class Messages {
    private final Logger logger;
    private YamlConfiguration yaml = new YamlConfiguration();
    private final Set<String> warned = new HashSet<>();

    public Messages(Logger logger) {
        this.logger = logger;
    }

    public void load(File file, java.io.InputStream bundled) {
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);
        if (bundled != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(bundled, StandardCharsets.UTF_8));
            loaded.setDefaults(defaults);
        }
        this.yaml = loaded;
        warned.clear();
    }

    /** Raw string with placeholders replaced. Arguments alternate key, value. */
    public String raw(String key, Object... kv) {
        String value = yaml.getString(key);
        if (value == null) {
            if (warned.add(key)) {
                logger.warning("messages.yml is missing key '" + key + "'");
            }
            return key;
        }
        return Text.format(value, kv);
    }

    public List<String> list(String key, Object... kv) {
        List<String> lines = yaml.getStringList(key);
        if (lines.isEmpty() && yaml.isString(key)) {
            lines = List.of(yaml.getString(key, ""));
        }
        return lines.stream().map(line -> Text.format(line, kv)).toList();
    }

    /** A random line from a list key (used for NPC dialogue). */
    public String random(String key, Object... kv) {
        List<String> lines = list(key, kv);
        if (lines.isEmpty()) {
            return "";
        }
        return lines.get(ThreadLocalRandom.current().nextInt(lines.size()));
    }

    public Component component(String key, Object... kv) {
        return Text.color(raw(key, kv));
    }

    /** Sends a prefixed chat message. Empty messages are not sent, so any line can be disabled by blanking it. */
    public void send(CommandSender to, String key, Object... kv) {
        String text = raw(key, kv);
        if (text.isEmpty()) {
            return;
        }
        to.sendMessage(Text.color(raw("prefix") + text));
    }

    /** Sends without the prefix. */
    public void sendPlain(CommandSender to, String key, Object... kv) {
        String text = raw(key, kv);
        if (!text.isEmpty()) {
            to.sendMessage(Text.color(text));
        }
    }

    public void sendLines(CommandSender to, String key, Object... kv) {
        for (String line : list(key, kv)) {
            to.sendMessage(Text.color(line));
        }
    }

    public void sendRawText(CommandSender to, String legacy) {
        if (legacy != null && !legacy.isEmpty()) {
            to.sendMessage(Text.color(legacy));
        }
    }
}
