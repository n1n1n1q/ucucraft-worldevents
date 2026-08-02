package net.ucucraft.worldevents.util;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the compact duration format admins actually write ({@code 6h}, {@code 1h30m}, {@code 20m}),
 * falling back to {@link Duration#parse} for ISO-8601 input.
 */
public final class Durations {

    private static final Pattern COMPACT = Pattern.compile("(\\d+)([dhms])");

    private Durations() {
    }

    public static Duration parse(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("duration is empty");
        }
        String value = input.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if (value.startsWith("p")) {
            return Duration.parse(value);
        }

        Matcher matcher = COMPACT.matcher(value);
        Duration total = Duration.ZERO;
        int consumed = 0;
        while (matcher.find()) {
            long amount = Long.parseLong(matcher.group(1));
            total = total.plus(switch (matcher.group(2)) {
                case "d" -> Duration.ofDays(amount);
                case "h" -> Duration.ofHours(amount);
                case "m" -> Duration.ofMinutes(amount);
                default -> Duration.ofSeconds(amount);
            });
            consumed = matcher.end();
        }
        if (consumed != value.length()) {
            throw new IllegalArgumentException("invalid duration: " + input);
        }
        return total;
    }

    public static Duration parseOr(String input, Duration fallback) {
        try {
            return parse(input);
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public static String format(Duration duration) {
        long seconds = Math.max(0, duration.getSeconds());
        StringBuilder builder = new StringBuilder();
        appendUnit(builder, seconds / 86400, "d");
        appendUnit(builder, seconds % 86400 / 3600, "h");
        appendUnit(builder, seconds % 3600 / 60, "m");
        long remainder = seconds % 60;
        if (remainder > 0 || builder.isEmpty()) {
            builder.append(remainder).append('s');
        }
        return builder.toString().trim();
    }

    private static void appendUnit(StringBuilder builder, long amount, String unit) {
        if (amount > 0) {
            builder.append(amount).append(unit).append(' ');
        }
    }
}
