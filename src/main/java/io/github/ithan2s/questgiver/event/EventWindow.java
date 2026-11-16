package io.github.ithan2s.questgiver.event;

import java.time.LocalDate;
import java.time.MonthDay;

/**
 * Pure date logic for seasonal events (unit tested). Dates are either yearly ("12-01") or fixed ("2026-12-01").
 * A yearly window may wrap the new year (start "12-20", end "01-05"); its event year is the year it started in.
 */
public final class EventWindow {
    private EventWindow() {
    }

    /** Start date of the window occurrence containing {@code today}, or null if the window isn't active. */
    public static LocalDate activeStart(LocalDate today, String start, String end) {
        if (start == null || end == null || start.isBlank() || end.isBlank()) {
            return null;
        }
        boolean fixed = start.length() > 5;
        if (fixed) {
            LocalDate s = LocalDate.parse(start);
            LocalDate e = LocalDate.parse(fixed && end.length() > 5 ? end : s.getYear() + "-" + end);
            return !today.isBefore(s) && !today.isAfter(e) ? s : null;
        }
        MonthDay s = MonthDay.parse("--" + start);
        MonthDay e = MonthDay.parse("--" + end);
        MonthDay t = MonthDay.from(today);
        if (!s.isAfter(e)) {
            return !t.isBefore(s) && !t.isAfter(e) ? s.atYear(today.getYear()) : null;
        }
        // Wraps the new year.
        if (!t.isBefore(s)) {
            return s.atYear(today.getYear());
        }
        if (!t.isAfter(e)) {
            return s.atYear(today.getYear() - 1);
        }
        return null;
    }

    /** Event year of the active occurrence, or -1 when inactive. */
    public static int activeYear(LocalDate today, String start, String end) {
        LocalDate s = activeStart(today, start, end);
        return s == null ? -1 : s.getYear();
    }
}
