package io.github.ithan2s.questgiver.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DurationsTest {
    @Test
    void remainingNeverNegative() {
        assertEquals(0, Durations.remaining(1000, 5000));
        assertEquals(4000, Durations.remaining(5000, 1000));
    }

    @Test
    void formats() {
        assertEquals("45s", Durations.format(45_000));
        assertEquals("12m 30s", Durations.format(12 * 60_000 + 30_000));
        assertEquals("4h 05m", Durations.format(4 * Durations.HOUR + 5 * Durations.MINUTE));
        assertEquals("6d 23h", Durations.format(7 * Durations.DAY - Durations.HOUR));
        assertEquals("59:59", Durations.clock(Durations.HOUR - 1000));
        assertEquals("1:00:00", Durations.clock(Durations.HOUR));
    }
}
