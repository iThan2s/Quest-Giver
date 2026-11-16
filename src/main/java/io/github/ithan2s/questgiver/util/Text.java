package io.github.ithan2s.questgiver.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Colour-code helpers. Every configurable string uses '&' codes; hex colours are written as &#RRGGBB. */
public final class Text {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&').hexColors().build();

    private Text() {
    }

    public static Component color(String legacy) {
        if (legacy == null || legacy.isEmpty()) {
            return Component.empty();
        }
        return LEGACY.deserialize(legacy);
    }

    /** Same as {@link #color} but never italic: item names and lore default to italic otherwise. */
    public static Component item(String legacy) {
        return color(legacy).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static List<Component> itemLines(List<String> legacy) {
        List<Component> out = new ArrayList<>(legacy.size());
        for (String line : legacy) {
            out.add(item(line));
        }
        return out;
    }

    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** Strips colour codes from a legacy string. */
    public static String strip(String legacy) {
        return plain(color(legacy));
    }

    /** "iron_pickaxe" -> "Iron Pickaxe". */
    public static String pretty(String key) {
        String k = key.contains(":") ? key.substring(key.indexOf(':') + 1) : key;
        k = k.contains("/") ? k.substring(k.lastIndexOf('/') + 1) : k;
        StringBuilder sb = new StringBuilder();
        for (String part : k.toLowerCase(Locale.ROOT).split("_")) {
            if (part.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    public static String roman(int n) {
        if (n <= 0 || n > 3999) {
            return Integer.toString(n);
        }
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] numerals = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            while (n >= values[i]) {
                n -= values[i];
                sb.append(numerals[i]);
            }
        }
        return sb.toString();
    }

    /** Replaces {key} placeholders. Arguments alternate key, value. */
    public static String format(String template, Object... kv) {
        if (template == null) {
            return "";
        }
        String out = template;
        for (int i = 0; i + 1 < kv.length; i += 2) {
            out = out.replace("{" + kv[i] + "}", String.valueOf(kv[i + 1]));
        }
        return out;
    }
}
