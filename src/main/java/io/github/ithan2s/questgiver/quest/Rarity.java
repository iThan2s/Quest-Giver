package io.github.ithan2s.questgiver.quest;

import java.util.Locale;

/** Fetch-item rarity. Rare and legendary turn-ins ask for confirmation before the items are taken. */
public enum Rarity {
    COMMON, RARE, LEGENDARY;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean needsConfirm() {
        return this != COMMON;
    }

    public static Rarity parse(String s) {
        return valueOf(s.trim().toUpperCase(Locale.ROOT));
    }
}
