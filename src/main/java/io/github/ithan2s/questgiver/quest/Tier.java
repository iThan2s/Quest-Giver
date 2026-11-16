package io.github.ithan2s.questgiver.quest;

import java.util.Locale;

public enum Tier {
    EASY, MEDIUM, HARD, LEGENDARY, BOUNTY;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Tier parse(String s) {
        return valueOf(s.trim().toUpperCase(Locale.ROOT));
    }
}
