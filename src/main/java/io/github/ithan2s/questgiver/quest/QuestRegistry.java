package io.github.ithan2s.questgiver.quest;

import io.github.ithan2s.questgiver.util.Text;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/** Loads and indexes quests.yml. Broken entries are skipped with a console warning instead of failing the load. */
public final class QuestRegistry {
    private final Logger logger;
    private Map<String, QuestDefinition> quests = new LinkedHashMap<>();
    private ConfigurationSection rarityDefaults;

    public QuestRegistry(Logger logger) {
        this.logger = logger;
    }

    public int load(YamlConfiguration yaml, ConfigurationSection rarityDefaults) {
        this.rarityDefaults = rarityDefaults;
        Map<String, QuestDefinition> loaded = new LinkedHashMap<>();
        ConfigurationSection root = yaml.getConfigurationSection("quests");
        if (root == null) {
            logger.warning("quests.yml has no 'quests' section.");
            quests = loaded;
            return 0;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            try {
                loaded.put(id.toLowerCase(Locale.ROOT), read(id.toLowerCase(Locale.ROOT), s));
            } catch (Exception e) {
                logger.warning("Skipping quest '" + id + "' in quests.yml: " + e.getMessage());
            }
        }
        for (QuestDefinition q : loaded.values()) {
            for (String req : q.requires()) {
                if (!loaded.containsKey(req)) {
                    logger.warning("Quest '" + q.id() + "' requires unknown quest '" + req + "'.");
                }
            }
        }
        quests = loaded;
        return loaded.size();
    }

    private QuestDefinition read(String id, ConfigurationSection s) {
        String name = s.getString("name", Text.pretty(id));
        Tier tier = Tier.parse(s.getString("tier", "easy"));
        String pool = s.getString("pool", "town").toLowerCase(Locale.ROOT);
        String event = s.getString("event");
        Rarity rarity = Rarity.parse(s.getString("rarity", tier == Tier.LEGENDARY || tier == Tier.BOUNTY
                ? "legendary" : tier == Tier.HARD ? "rare" : "common"));
        List<Objective> objectives = new ArrayList<>();
        for (Map<?, ?> o : s.getMapList("objectives")) {
            objectives.add(objective(o));
        }
        if (objectives.isEmpty()) {
            throw new IllegalArgumentException("no objectives");
        }
        String iconName = s.getString("icon");
        Material icon = iconName == null ? defaultIcon(objectives.getFirst()) : Material.matchMaterial(iconName);
        if (icon == null) {
            icon = Material.PAPER;
        }
        String description = s.getString("description", describe(objectives));
        int bountyDays = s.getInt("bounty-cooldown-days", 0);
        return new QuestDefinition(id, name, description, pool, event == null ? null : event.toLowerCase(Locale.ROOT),
                tier, rarity, s.getString("line"), s.getInt("level", 1),
                s.getStringList("requires").stream().map(r -> r.toLowerCase(Locale.ROOT)).toList(),
                s.getInt("squad", 0), icon, List.copyOf(objectives),
                rewards(s.getConfigurationSection("rewards"), objectives.stream().anyMatch(Objective::isFetch)
                        ? rarityDefaults == null ? null : rarityDefaults.getConfigurationSection(rarity.key()) : null),
                bountyDays, s.getInt("time-limit-minutes", 0));
    }

    /** @param fetchDefaults rarity defaults for fetch quests that leave money/xp out, or null */
    private static RewardSpec rewards(ConfigurationSection s, ConfigurationSection fetchDefaults) {
        if (s == null) {
            return fetchDefaults == null ? RewardSpec.EMPTY : new RewardSpec(fetchDefaults.getDouble("money"),
                    fetchDefaults.getInt("xp"), List.of(), List.of(), -1);
        }
        double money = s.contains("money") || fetchDefaults == null ? s.getDouble("money", 0)
                : fetchDefaults.getDouble("money", 0);
        int xp = s.contains("xp") || fetchDefaults == null ? s.getInt("xp", 0) : fetchDefaults.getInt("xp", 0);
        List<ItemSpec> items = new ArrayList<>();
        for (Object o : s.getList("items", List.of())) {
            items.add(ItemSpec.parse(o));
        }
        return new RewardSpec(money, xp, List.copyOf(items),
                s.getStringList("commands"), s.getInt("reputation", -1));
    }

    private static Objective objective(Map<?, ?> o) {
        String type = str(o, "type", "").toLowerCase(Locale.ROOT);
        int amount = o.get("amount") instanceof Number n ? n.intValue() : 1;
        String label = o.get("label") == null ? null : String.valueOf(o.get("label"));
        switch (type) {
            case "advancement" -> {
                String key = str(o, "key", "");
                NamespacedKey nk = NamespacedKey.fromString(key.contains(":") ? key : "minecraft:" + key);
                if (nk == null) {
                    throw new IllegalArgumentException("bad advancement key " + key);
                }
                return new Objective.Advancement(nk, label);
            }
            case "kill" -> {
                Set<EntityType> types = EnumSet.noneOf(EntityType.class);
                for (String e : list(o.get("entity"))) {
                    types.add(entity(e));
                }
                boolean hostile = bool(o, "hostile");
                if (types.isEmpty() && !hostile) {
                    throw new IllegalArgumentException("kill objective needs 'entity' or 'hostile: true'");
                }
                String auto = hostile && types.isEmpty() ? "Hostile mobs"
                        : String.join(" / ", types.stream().map(t -> plural(Text.pretty(t.getKey().getKey()))).toList());
                if (bool(o, "captain")) auto = Text.pretty(types.isEmpty() ? "raider" : types.iterator().next()
                        .getKey().getKey()) + " captains";
                return new Objective.Kill(Set.copyOf(types), hostile, amount, bool(o, "night"), bool(o, "captain"),
                        bool(o, "resummoned"), label != null ? label : auto);
            }
            case "fetch" -> {
                Set<Material> materials = EnumSet.noneOf(Material.class);
                String first = null;
                for (String m : list(o.get("item"))) {
                    first = first == null ? m : first;
                    materials.addAll(materials(m));
                }
                if (materials.isEmpty()) {
                    throw new IllegalArgumentException("fetch objective needs 'item'");
                }
                String auto = first.startsWith("#") ? Text.pretty(first.substring(1))
                        : Text.pretty(materials.iterator().next().getKey().getKey());
                return new Objective.Fetch(Set.copyOf(materials), amount, bool(o, "distinct"), bool(o, "with-bees"),
                        label != null ? label : auto);
            }
            case "visit" -> {
                List<NamespacedKey> biomes = new ArrayList<>();
                for (String b : list(o.get("biomes"))) {
                    NamespacedKey key = NamespacedKey.fromString(b.contains(":") ? b : "minecraft:" + b);
                    if (key == null || io.papermc.paper.registry.RegistryAccess.registryAccess()
                            .getRegistry(io.papermc.paper.registry.RegistryKey.BIOME).get(key) == null) {
                        throw new IllegalArgumentException("unknown biome " + b);
                    }
                    biomes.add(key);
                }
                return new Objective.Visit(List.copyOf(biomes), label != null ? label : "Biomes visited");
            }
            case "breed" -> {
                EntityType t = entity(str(o, "entity", ""));
                return new Objective.Breed(t, amount, label != null ? label : plural(Text.pretty(t.getKey().getKey()))
                        + " bred");
            }
            case "build" -> {
                EntityType t = entity(str(o, "entity", "snow_golem"));
                return new Objective.Build(t, amount, label != null ? label : plural(Text.pretty(t.getKey().getKey()))
                        + " built");
            }
            case "cure" -> {
                return new Objective.Cure(amount, label != null ? label : "Zombie villagers cured");
            }
            case "complete" -> {
                String event = str(o, "event", "").toLowerCase(Locale.ROOT);
                return new Objective.CompleteEvent(event, amount,
                        label != null ? label : Text.pretty(event) + " quests finished");
            }
            default -> throw new IllegalArgumentException("unknown objective type '" + type + "'");
        }
    }

    /** "#pottery_sherds" style groups, an item tag "#minecraft:logs", or a single material. */
    private static Set<Material> materials(String s) {
        Set<Material> out = EnumSet.noneOf(Material.class);
        if (s.equalsIgnoreCase("#pottery_sherds") || s.equalsIgnoreCase("#pottery_sherd")) {
            for (Material m : Material.values()) {
                if (!m.isLegacy() && m.name().endsWith("_POTTERY_SHERD")) {
                    out.add(m);
                }
            }
            return out;
        }
        if (s.startsWith("#")) {
            NamespacedKey key = NamespacedKey.fromString(s.substring(1).contains(":") ? s.substring(1)
                    : "minecraft:" + s.substring(1));
            Tag<Material> tag = key == null ? null : org.bukkit.Bukkit.getTag(Tag.REGISTRY_ITEMS, key, Material.class);
            if (tag == null) {
                throw new IllegalArgumentException("unknown item tag " + s);
            }
            out.addAll(tag.getValues());
            return out;
        }
        Material m = Material.matchMaterial(s);
        if (m == null) {
            throw new IllegalArgumentException("unknown item " + s);
        }
        out.add(m);
        return out;
    }

    private static EntityType entity(String s) {
        EntityType t = Registry.ENTITY_TYPE.get(NamespacedKey.minecraft(s.toLowerCase(Locale.ROOT)));
        if (t == null) {
            throw new IllegalArgumentException("unknown mob " + s);
        }
        return t;
    }

    private static String str(Map<?, ?> o, String key, String def) {
        Object v = o.get(key);
        return v == null ? def : String.valueOf(v);
    }

    private static boolean bool(Map<?, ?> o, String key) {
        return Boolean.TRUE.equals(o.get(key));
    }

    private static List<String> list(Object v) {
        if (v == null) {
            return List.of();
        }
        if (v instanceof Collection<?> c) {
            return c.stream().map(String::valueOf).toList();
        }
        return List.of(String.valueOf(v));
    }

    /** Simple English plural for mob names ("Skeleton" -> "Skeletons", "Enderman" -> "Endermen"). */
    public static String plural(String s) {
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.endsWith("drowned") || lower.endsWith("sheep") || lower.endsWith("fish")) return s;
        if (lower.endsWith("man")) return s.substring(0, s.length() - 3) + "men";
        if (lower.endsWith("ch") || lower.endsWith("sh") || lower.endsWith("s") || lower.endsWith("x")) return s + "es";
        if (lower.endsWith("y") && !lower.endsWith("ey")) return s.substring(0, s.length() - 1) + "ies";
        return s + "s";
    }

    private static String describe(List<Objective> objectives) {
        List<String> parts = new ArrayList<>();
        for (Objective o : objectives) {
            parts.add(switch (o) {
                case Objective.Advancement a -> "Earn the advancement " + Text.pretty(a.key().getKey());
                case Objective.Kill k -> "Kill " + k.amount() + " " + k.label().toLowerCase(Locale.ROOT)
                        + (k.night() ? " in one night" : "");
                case Objective.Fetch f -> "Bring " + f.amount() + " " + f.label().toLowerCase(Locale.ROOT);
                case Objective.Visit v -> "Visit " + String.join(", ",
                        v.biomes().stream().map(b -> Text.pretty(b.getKey())).toList());
                case Objective.Breed b -> "Breed " + b.amount() + " " + Text.pretty(b.type().getKey().getKey());
                case Objective.Build b -> "Build " + b.amount() + " " + Text.pretty(b.type().getKey().getKey());
                case Objective.Cure c -> "Cure " + c.amount() + " zombie villager" + (c.amount() > 1 ? "s" : "");
                case Objective.CompleteEvent c -> "Finish " + c.amount() + " other " + Text.pretty(c.event()) + " quests";
            });
        }
        return String.join(", ", parts);
    }

    private static Material defaultIcon(Objective o) {
        return switch (o) {
            case Objective.Advancement a -> {
                var adv = org.bukkit.Bukkit.getAdvancement(a.key());
                yield adv != null && adv.getDisplay() != null ? adv.getDisplay().icon().getType() : Material.KNOWLEDGE_BOOK;
            }
            case Objective.Kill k -> {
                Material egg = k.types().size() == 1
                        ? Material.matchMaterial(k.types().iterator().next().getKey().getKey() + "_spawn_egg") : null;
                yield egg != null ? egg : Material.IRON_SWORD;
            }
            case Objective.Fetch f -> f.materials().iterator().next();
            case Objective.Visit v -> Material.FILLED_MAP;
            case Objective.Breed b -> Material.WHEAT;
            case Objective.Build b -> Material.CARVED_PUMPKIN;
            case Objective.Cure c -> Material.GOLDEN_APPLE;
            case Objective.CompleteEvent c -> Material.NETHER_STAR;
        };
    }

    public QuestDefinition get(String id) {
        return id == null ? null : quests.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<QuestDefinition> all() {
        return quests.values();
    }

    /** The quest with the given line and level, if any. */
    public QuestDefinition level(String line, int level) {
        for (QuestDefinition q : quests.values()) {
            if (line.equals(q.line()) && q.level() == level) {
                return q;
            }
        }
        return null;
    }

    public List<QuestDefinition> pool(String pool) {
        return quests.values().stream().filter(q -> q.pool().equals(pool)).toList();
    }

    public List<String> ids() {
        return new ArrayList<>(quests.keySet());
    }
}
