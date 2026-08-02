package net.ucucraft.worldevents.schedule;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.random.RandomGenerator;

import net.ucucraft.worldevents.schedule.jitter.DistributionType;
import net.ucucraft.worldevents.schedule.jitter.Jitter;
import net.ucucraft.worldevents.util.Durations;
import org.bukkit.configuration.ConfigurationSection;

/**
 * The only place schedule config keys are read.
 */
public final class ScheduleFactory {

    private final RandomGenerator random;

    public ScheduleFactory(RandomGenerator random) {
        this.random = random;
    }

    public EventSchedule create(ConfigurationSection section, ZoneId zone) {
        if (section == null) {
            return EventSchedule.NONE;
        }

        EventSchedule base = switch (enumOf(ScheduleType.class, section.getString("type"), ScheduleType.NONE)) {
            case NONE -> EventSchedule.NONE;
            case PERIODIC -> new PeriodicSchedule(Durations.parse(section.getString("interval", "6h")));
            case FIXED_TIME -> new FixedTimeSchedule(
                    times(section.getStringList("times")), zone, days(section.getStringList("days")));
            case RANDOM -> new RandomSchedule(
                    Durations.parse(section.getString("min-delay", "1h")),
                    Durations.parse(section.getString("max-delay", "6h")),
                    random);
        };

        Jitter jitter = jitter(section.getConfigurationSection("jitter"));
        if (base == EventSchedule.NONE || jitter == Jitter.NONE) {
            return base;
        }
        return new JitteredSchedule(base, jitter, random);
    }

    private Jitter jitter(ConfigurationSection section) {
        if (section == null) {
            return Jitter.NONE;
        }
        DistributionType distribution = enumOf(
                DistributionType.class, section.getString("distribution"), DistributionType.NONE);
        Duration before = Durations.parse(section.getString("before", "0s"));
        Duration after = Durations.parse(section.getString("after", "0s"));
        return distribution.create(before, after);
    }

    private static List<LocalTime> times(List<String> raw) {
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("FIXED_TIME schedule needs at least one entry in 'times'");
        }
        return raw.stream().map(ScheduleFactory::time).toList();
    }

    public static LocalTime time(String raw) {
        String value = raw.trim();
        return LocalTime.parse(value.indexOf(':') == 1 ? "0" + value : value);
    }

    private static Set<DayOfWeek> days(List<String> raw) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        raw.forEach(day -> days.add(DayOfWeek.valueOf(day.trim().toUpperCase(Locale.ROOT))));
        return days;
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, String raw, E fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }
}
