package io.github.ithan2s.questgiver.quest;

import io.github.ithan2s.questgiver.util.Text;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * A reward item. Written either as a short string or as a map:
 * <pre>
 *   - "bread 16"                              material, amount
 *   - "enchanted_book efficiency:2"           enchantments are key:level (books get stored enchantments)
 *   - "golden_chestplate protection:5"        levels above vanilla max are given exactly as written
 *   - "potion 4 potion:long_fire_resistance"  potion type
 *   - "leather_boots color:red"               dye colour (name or #RRGGBB)
 *   - {item: iron_sword, name: "&cKrampus's Claw", enchants: {sharpness: 4}, lore: ["..."]}
 *   - {random: [music_disc_cat, music_disc_13], picks: 2}   distinct random picks
 * </pre>
 */
public final class ItemSpec {
    private static final Map<String, Color> COLORS = Map.ofEntries(
            Map.entry("red", Color.fromRGB(0xB02E26)), Map.entry("green", Color.fromRGB(0x5E7C16)),
            Map.entry("white", Color.fromRGB(0xF9FFFE)), Map.entry("black", Color.fromRGB(0x1D1D21)),
            Map.entry("blue", Color.fromRGB(0x3C44AA)), Map.entry("yellow", Color.fromRGB(0xFED83D)),
            Map.entry("purple", Color.fromRGB(0x8932B8)), Map.entry("orange", Color.fromRGB(0xF9801D)),
            Map.entry("light_blue", Color.fromRGB(0x3AB3DA)), Map.entry("brown", Color.fromRGB(0x835432)),
            Map.entry("gray", Color.fromRGB(0x474F52)), Map.entry("pink", Color.fromRGB(0xF38BAA)));

    private Material material;
    private int amount = 1;
    private final Map<Enchantment, Integer> enchants = new LinkedHashMap<>();
    private PotionType potion;
    private Color color;
    private String name;
    private List<String> lore = List.of();
    private List<Material> randomFrom;
    private int picks = 1;

    public static ItemSpec parse(Object raw) {
        ItemSpec spec = new ItemSpec();
        if (raw instanceof String s) {
            spec.parseShort(s);
        } else if (raw instanceof Map<?, ?> map) {
            spec.parseMap(map);
        } else if (raw instanceof ConfigurationSection section) {
            spec.parseMap(section.getValues(false));
        } else {
            throw new IllegalArgumentException("Unsupported item entry: " + raw);
        }
        return spec;
    }

    private void parseShort(String s) {
        String[] tokens = s.trim().split("\\s+");
        material = material(tokens[0]);
        for (int i = 1; i < tokens.length; i++) {
            String t = tokens[i];
            if (t.matches("\\d+")) {
                amount = Integer.parseInt(t);
            } else if (t.contains(":")) {
                String key = t.substring(0, t.indexOf(':')).toLowerCase(Locale.ROOT);
                String value = t.substring(t.indexOf(':') + 1);
                switch (key) {
                    case "potion" -> potion = potion(value);
                    case "color" -> color = color(value);
                    default -> enchants.put(enchantment(key), Integer.parseInt(value));
                }
            } else {
                throw new IllegalArgumentException("Can't read '" + t + "' in item '" + s + "'");
            }
        }
    }

    private void parseMap(Map<?, ?> map) {
        Object random = map.get("random");
        if (random instanceof List<?> list) {
            randomFrom = new ArrayList<>();
            for (Object o : list) {
                randomFrom.add(material(String.valueOf(o)));
            }
            picks = map.get("picks") instanceof Number n ? n.intValue() : 1;
            if (picks > randomFrom.size()) {
                throw new IllegalArgumentException("picks is larger than the random list");
            }
            return;
        }
        Object item = map.get("item");
        if (item == null) {
            throw new IllegalArgumentException("Item entry needs 'item' or 'random'");
        }
        parseShort(String.valueOf(item));
        if (map.get("amount") instanceof Number n) {
            amount = n.intValue();
        }
        if (map.get("name") != null) {
            name = String.valueOf(map.get("name"));
        }
        if (map.get("lore") instanceof List<?> l) {
            lore = l.stream().map(String::valueOf).toList();
        }
        if (map.get("color") != null) {
            color = color(String.valueOf(map.get("color")));
        }
        if (map.get("potion") != null) {
            potion = potion(String.valueOf(map.get("potion")));
        }
        Object ench = map.get("enchants");
        Map<?, ?> enchMap = ench instanceof ConfigurationSection cs ? cs.getValues(false)
                : ench instanceof Map<?, ?> m ? m : Map.of();
        for (Map.Entry<?, ?> e : enchMap.entrySet()) {
            enchants.put(enchantment(String.valueOf(e.getKey())), ((Number) e.getValue()).intValue());
        }
    }

    private static Material material(String s) {
        Material m = Material.matchMaterial(s);
        if (m == null || !m.isItem()) {
            throw new IllegalArgumentException("Unknown item '" + s + "'");
        }
        return m;
    }

    private static Enchantment enchantment(String s) {
        Enchantment e = io.papermc.paper.registry.RegistryAccess.registryAccess()
                .getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT).get(NamespacedKey.minecraft(s.toLowerCase(Locale.ROOT)));
        if (e == null) {
            throw new IllegalArgumentException("Unknown enchantment '" + s + "'");
        }
        return e;
    }

    private static PotionType potion(String s) {
        PotionType p = Registry.POTION.get(NamespacedKey.minecraft(s.toLowerCase(Locale.ROOT)));
        if (p == null) {
            throw new IllegalArgumentException("Unknown potion '" + s + "'");
        }
        return p;
    }

    private static Color color(String s) {
        if (s.startsWith("#")) {
            return Color.fromRGB(Integer.parseInt(s.substring(1), 16));
        }
        Color c = COLORS.get(s.toLowerCase(Locale.ROOT));
        if (c == null) {
            throw new IllegalArgumentException("Unknown colour '" + s + "'");
        }
        return c;
    }

    /** Builds fresh stacks (random entries are rolled each time). */
    public List<ItemStack> create(Random random) {
        if (randomFrom != null) {
            List<Material> pool = new ArrayList<>(randomFrom);
            Collections.shuffle(pool, random);
            List<ItemStack> out = new ArrayList<>();
            for (int i = 0; i < picks; i++) {
                out.add(new ItemStack(pool.get(i)));
            }
            return out;
        }
        ItemStack stack = new ItemStack(material, amount);
        stack.editMeta(meta -> {
            for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
                if (meta instanceof EnchantmentStorageMeta book) {
                    book.addStoredEnchant(e.getKey(), e.getValue(), true);
                } else {
                    meta.addEnchant(e.getKey(), e.getValue(), true);
                }
            }
            if (potion != null && meta instanceof PotionMeta pm) {
                pm.setBasePotionType(potion);
            }
            if (color != null && meta instanceof LeatherArmorMeta lm) {
                lm.setColor(color);
            }
            if (name != null) {
                meta.displayName(Text.item(name));
            }
            if (!lore.isEmpty()) {
                meta.lore(Text.itemLines(lore));
            }
        });
        return List.of(stack);
    }

    /** Human-readable summary for tooltips, e.g. "16x Bread" or "Enchanted Book (Efficiency II)". */
    public String describe() {
        if (randomFrom != null) {
            return picks + "x random " + (randomFrom.stream().allMatch(m -> m.name().startsWith("MUSIC_DISC"))
                    ? "music disc" + (picks > 1 ? "s" : "") : "item" + (picks > 1 ? "s" : ""));
        }
        StringBuilder sb = new StringBuilder();
        if (amount > 1) {
            sb.append(amount).append("x ");
        }
        if (name != null) {
            sb.append(Text.strip(name)).append(" (").append(Text.pretty(material.getKey().getKey())).append(")");
        } else if (potion != null) {
            sb.append(Text.pretty(material.getKey().getKey())).append(" of ").append(potionName(potion));
        } else {
            if (color != null) {
                String colorName = COLORS.entrySet().stream().filter(e -> e.getValue().equals(color))
                        .map(Map.Entry::getKey).findFirst().orElse("dyed");
                sb.append(Text.pretty(colorName)).append(' ');
            }
            sb.append(Text.pretty(material.getKey().getKey()));
        }
        if (!enchants.isEmpty()) {
            List<String> parts = new ArrayList<>();
            for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
                String n = Text.pretty(e.getKey().getKey().getKey());
                parts.add(e.getKey().getMaxLevel() == 1 && e.getValue() == 1 ? n : n + " " + Text.roman(e.getValue()));
            }
            sb.append(" (").append(String.join(", ", parts)).append(")");
        }
        return sb.toString();
    }

    private static String potionName(PotionType type) {
        String key = type.getKey().getKey();
        String base = key.replaceFirst("^(long|strong)_", "");
        String out = Text.pretty(base);
        if (key.startsWith("strong_")) out += " II";
        if (key.startsWith("long_")) out += " (extended)";
        return out;
    }

    /** Total item count, for payout statistics. */
    public int count() {
        return randomFrom != null ? picks : amount;
    }
}
