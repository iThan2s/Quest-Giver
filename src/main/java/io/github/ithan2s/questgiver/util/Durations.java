package io.github.ithan2s.questgiver.util;

/** Pure time helpers (unit tested). */
public final class Durations {
    public static final long SECOND = 1000L;
    public static final long MINUTE = 60 * SECOND;
    public static final long HOUR = 60 * MINUTE;
    public static final long DAY = 24 * HOUR;

    private Durations() {
    }

    /** Milliseconds left until {@code until}, never negative. */
    public static long remaining(long until, long now) {
        return Math.max(0L, until - now);
    }

    /** "2d 3h", "4h 05m", "12m 30s", "45s". */
    public static String format(long millis) {
        long total = Math.max(0L, millis) / 1000L;
        long days = total / 86400;
        long hours = (total % 86400) / 3600;
        long minutes = (total % 3600) / 60;
        long seconds = total % 60;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + String.format("%02dm", minutes);
        }
        if (minutes > 0) {
            return minutes + "m " + String.format("%02ds", seconds);
        }
        return seconds + "s";
    }

    /** "MM:SS" or "H:MM:SS" for boss-bar countdowns. */
    public static String clock(long millis) {
        long total = Math.max(0L, millis) / 1000L;
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%02d:%02d", m, s);
    }
}
