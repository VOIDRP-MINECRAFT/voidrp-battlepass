package ru.voidrp.battlepass.season;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public final class Season {

    private static final DateTimeFormatter KEY_FMT  = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MONO_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private static LocalDate startDate = null;
    private static LocalDate endDate   = null;
    private static String    key       = null;   // stable storage key (backend-managed)
    private static String    name      = null;   // display name (backend-managed)

    private Season() {}

    /** Config.yml fallback: key is derived from the start date. */
    public static void configure(LocalDate start, LocalDate end) {
        startDate = start;
        endDate   = end;
        key       = null;   // derive from start date
    }

    /** Backend-managed season: explicit stable key + name + dates (overrides config.yml). */
    public static void configure(String seasonKey, String displayName, LocalDate start, LocalDate end) {
        key       = seasonKey;
        name      = displayName;
        startDate = start;
        endDate   = end;
    }

    /**
     * Returns the season storage key.
     * Uses the backend-provided key if set; otherwise "yyyy-MM-dd" of the start date;
     * otherwise falls back to the current "yyyy-MM".
     */
    public static String currentKey() {
        if (key != null && !key.isBlank()) return key;
        return startDate != null ? startDate.format(KEY_FMT)
                                 : LocalDate.now().format(MONO_FMT);
    }

    /** Display name of the active season, or null when unknown (GUI falls back to config). */
    public static String getName() { return name; }

    /** Returns how many days remain until the configured end date (0 if already past). */
    public static int daysUntilReset() {
        LocalDate today = LocalDate.now();
        if (endDate != null) {
            return (int) Math.max(0, ChronoUnit.DAYS.between(today, endDate));
        }
        // fallback: days until 1st of next month
        LocalDate firstNext = today.withDayOfMonth(1).plusMonths(1);
        return (int) (firstNext.toEpochDay() - today.toEpochDay());
    }

    /** True if today falls within [start, end] inclusive. Always true when dates are not set. */
    public static boolean isActive() {
        LocalDate today = LocalDate.now();
        if (startDate != null && endDate != null) {
            return !today.isBefore(startDate) && !today.isAfter(endDate);
        }
        return true;
    }

    public static LocalDate getStartDate() { return startDate; }
    public static LocalDate getEndDate()   { return endDate; }
}
