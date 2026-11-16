package io.github.ithan2s.questgiver.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class Money {
    private static final ThreadLocal<DecimalFormat> FORMAT = ThreadLocal.withInitial(
            () -> new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US)));

    private Money() {
    }

    /** 1500 -> "$1,500", 1234.5 -> "$1,234.5". */
    public static String format(double amount) {
        return "$" + FORMAT.get().format(amount);
    }
}
