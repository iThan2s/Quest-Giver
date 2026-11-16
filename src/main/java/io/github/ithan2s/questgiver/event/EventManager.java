package io.github.ithan2s.questgiver.event;

import io.github.ithan2s.questgiver.storage.Storage;
import io.github.ithan2s.questgiver.util.Text;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

/**
 * Seasonal events. An event is active when an admin started it (/quest event start), or when today falls in its
 * configured date window and an admin hasn't stopped it during that window. Each run is identified by
 * "id:year" so the same event can be played again next year.
 */
public final class EventManager {
    public record Definition(String id, String displayName, String lore, String start, String end, String giverPool) {
    }

    private record Override(boolean on, int year, long at) {
    }

    private final Storage storage;
    private final Logger logger;
    private final Map<String, Definition> definitions = new LinkedHashMap<>();
    private final Map<String, Override> overrides = new HashMap<>();
    private Map<String, Integer> active = new HashMap<>();
    private BiConsumer<Definition, String> onStart = (d, run) -> { };
    private BiConsumer<Definition, String> onEnd = (d, run) -> { };

    public EventManager(Storage storage, Logger logger) {
        this.storage = storage;
        this.logger = logger;
    }

    public void listeners(BiConsumer<Definition, String> onStart, BiConsumer<Definition, String> onEnd) {
        this.onStart = onStart;
        this.onEnd = onEnd;
    }

    public void load(FileConfiguration config) {
        definitions.clear();
        ConfigurationSection s = config.getConfigurationSection("events");
        if (s != null) {
            for (String id : s.getKeys(false)) {
                ConfigurationSection e = s.getConfigurationSection(id);
                if (e == null) continue;
                String key = id.toLowerCase(Locale.ROOT);
                definitions.put(key, new Definition(key, e.getString("display-name", Text.pretty(key)),
                        e.getString("lore", "&b" + Text.pretty(key) + " {year}"), e.getString("start", ""),
                        e.getString("end", ""), e.getString("giver-pool", "town")));
            }
        }
        try {
            Map<String, String> stored = storage.supply(c -> Storage.getKvPrefix(c, "event:")).get(10, TimeUnit.SECONDS);
            overrides.clear();
            for (Map.Entry<String, String> e : stored.entrySet()) {
                String[] p = e.getValue().split(":");
                overrides.put(e.getKey().substring(6), new Override(p[0].equals("on"), Integer.parseInt(p[1]),
                        Long.parseLong(p[2])));
            }
        } catch (Exception e) {
            logger.warning("Could not read event state: " + e.getMessage());
        }
        // Silent recompute: don't fire start/end callbacks for state carried over a restart.
        active = compute();
    }

    public Map<String, Definition> definitions() {
        return definitions;
    }

    public Definition get(String id) {
        return id == null ? null : definitions.get(id.toLowerCase(Locale.ROOT));
    }

    private Map<String, Integer> compute() {
        Map<String, Integer> out = new HashMap<>();
        LocalDate today = LocalDate.now();
        for (Definition d : definitions.values()) {
            Override o = overrides.get(d.id());
            LocalDate windowStart = EventWindow.activeStart(today, d.start(), d.end());
            if (o != null && o.on()) {
                out.put(d.id(), o.year());
            } else if (o != null) {
                LocalDate stoppedOn = Instant.ofEpochMilli(o.at()).atZone(ZoneId.systemDefault()).toLocalDate();
                if (windowStart != null && windowStart.isAfter(stoppedOn)) {
                    out.put(d.id(), windowStart.getYear());
                }
            } else if (windowStart != null) {
                out.put(d.id(), windowStart.getYear());
            }
        }
        return out;
    }

    /** Re-evaluates dates; fires start/end callbacks on changes. Runs every minute. */
    public void tick() {
        Map<String, Integer> now = compute();
        Set<String> ids = new HashSet<>(active.keySet());
        ids.addAll(now.keySet());
        Map<String, Integer> before = active;
        active = now;
        for (String id : ids) {
            Integer was = before.get(id);
            Integer is = now.get(id);
            Definition d = definitions.get(id);
            if (d == null) continue;
            if (was != null && !was.equals(is)) {
                onEnd.accept(d, id + ":" + was);
            }
            if (is != null && !is.equals(was)) {
                onStart.accept(d, id + ":" + is);
            }
        }
    }

    public void start(String id) {
        overrides.put(id, new Override(true, LocalDate.now().getYear(), System.currentTimeMillis()));
        persist(id);
        tick();
    }

    public void stop(String id) {
        overrides.put(id, new Override(false, 0, System.currentTimeMillis()));
        persist(id);
        tick();
    }

    private void persist(String id) {
        Override o = overrides.get(id);
        String value = (o.on() ? "on" : "off") + ":" + o.year() + ":" + o.at();
        storage.run(c -> {
            Storage.setKv(c, "event:" + id, value);
            return null;
        });
    }

    public boolean isActive(String id) {
        return id != null && active.containsKey(id);
    }

    /** "winterfest:2026" while active, else null. */
    public String activeRun(String id) {
        Integer year = id == null ? null : active.get(id);
        return year == null ? null : id + ":" + year;
    }

    public int year(String id) {
        Integer y = id == null ? null : active.get(id);
        return y == null ? -1 : y;
    }

    public boolean isRunActive(String run) {
        if (run == null) return false;
        int i = run.lastIndexOf(':');
        return i > 0 && run.equals(activeRun(run.substring(0, i)));
    }

    public boolean anyActiveFor(String giverPool) {
        for (String id : active.keySet()) {
            Definition d = definitions.get(id);
            if (d != null && d.giverPool().equals(giverPool)) {
                return true;
            }
        }
        return false;
    }

    public String lore(String id) {
        Definition d = get(id);
        int year = year(id);
        return d == null ? null : Text.format(d.lore(), "year", year > 0 ? year : LocalDate.now().getYear(),
                "event", Text.strip(d.displayName()));
    }
}
