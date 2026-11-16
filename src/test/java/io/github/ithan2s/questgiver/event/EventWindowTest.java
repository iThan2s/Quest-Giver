package io.github.ithan2s.questgiver.event;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventWindowTest {
    @Test
    void yearlyWindow() {
        assertEquals(2026, EventWindow.activeYear(LocalDate.of(2026, 12, 15), "12-01", "12-31"));
        assertEquals(2026, EventWindow.activeYear(LocalDate.of(2026, 12, 1), "12-01", "12-31"));
        assertEquals(-1, EventWindow.activeYear(LocalDate.of(2026, 11, 30), "12-01", "12-31"));
    }

    @Test
    void windowWrappingNewYearKeepsStartYear() {
        assertEquals(2026, EventWindow.activeYear(LocalDate.of(2026, 12, 25), "12-20", "01-05"));
        assertEquals(2026, EventWindow.activeYear(LocalDate.of(2027, 1, 3), "12-20", "01-05"));
        assertEquals(-1, EventWindow.activeYear(LocalDate.of(2027, 1, 6), "12-20", "01-05"));
    }

    @Test
    void fixedDates() {
        assertEquals(2026, EventWindow.activeYear(LocalDate.of(2026, 12, 2), "2026-12-01", "2027-01-02"));
        assertEquals(-1, EventWindow.activeYear(LocalDate.of(2027, 12, 2), "2026-12-01", "2027-01-02"));
    }

    @Test
    void noDatesMeansManualOnly() {
        assertEquals(-1, EventWindow.activeYear(LocalDate.of(2026, 12, 2), "", ""));
    }
}
