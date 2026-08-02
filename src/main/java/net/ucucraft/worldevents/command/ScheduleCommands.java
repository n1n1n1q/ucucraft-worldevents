package net.ucucraft.worldevents.command;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.ucucraft.worldevents.WorldEventsPlugin;
import net.ucucraft.worldevents.event.WorldEvent;
import net.ucucraft.worldevents.lang.Msg;
import net.ucucraft.worldevents.schedule.ScheduleFactory;
import net.ucucraft.worldevents.schedule.ScheduleType;
import net.ucucraft.worldevents.schedule.jitter.DistributionType;
import org.bukkit.configuration.ConfigurationSection;

@SuppressWarnings("UnstableApiUsage")
final class ScheduleCommands {

    private static final String[] DELAY_SUGGESTIONS = {"30s", "5m", "30m", "1h", "6h", "12h"};
    private static final String[] TIME_SUGGESTIONS = {"00:00", "06:00", "12:00", "18:00"};

    private ScheduleCommands() {
    }

    /** One-off override of the next run time. */
    static LiteralArgumentBuilder<CommandSourceStack> schedule(WorldEventsPlugin plugin) {
        return Commands.literal("schedule")
                .requires(CommandSupport.permission("schedule"))
                .then(CommandSupport.eventArgument(plugin)
                        .then(Commands.literal("in")
                                .then(Commands.argument("delay", StringArgumentType.word())
                                        .suggests(CommandSupport.suggest(DELAY_SUGGESTIONS))
                                        .executes(context -> CommandSupport.event(context, plugin)
                                                .flatMap(event -> CommandSupport.duration(context, plugin, "delay")
                                                        .map(delay -> arm(context, plugin, event,
                                                                Instant.now().plus(delay))))
                                                .orElse(Command.SINGLE_SUCCESS))))
                        .then(Commands.literal("at")
                                .then(Commands.argument("time", StringArgumentType.word())
                                        .suggests(CommandSupport.suggest(TIME_SUGGESTIONS))
                                        .executes(context -> CommandSupport.event(context, plugin)
                                                .flatMap(event -> nextOccurrence(context, plugin)
                                                        .map(at -> arm(context, plugin, event, at)))
                                                .orElse(Command.SINGLE_SUCCESS)))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> cancel(WorldEventsPlugin plugin) {
        return Commands.literal("cancel")
                .requires(CommandSupport.permission("cancel"))
                .then(CommandSupport.eventArgument(plugin).executes(context -> {
                    CommandSupport.event(context, plugin).ifPresent(event -> {
                        if (!plugin.scheduler().cancel(event.id())) {
                            plugin.lang().send(context.getSource().getSender(), Msg.CANCEL_NOTHING,
                                    event.placeholders());
                            return;
                        }
                        String next = plugin.scheduler().nextRun(event.id())
                                .map(instant -> CommandSupport.formatInstant(instant, plugin.configManager().zone()))
                                .orElse(plugin.lang().raw(Msg.NEVER));
                        plugin.lang().send(context.getSource().getSender(), Msg.CANCEL_SUCCESS,
                                Placeholder.unparsed("event", event.displayName()),
                                Placeholder.unparsed("next", next));
                    });
                    return Command.SINGLE_SUCCESS;
                }));
    }

    /** Persistent schedule changes written back to config.yml. */
    static LiteralArgumentBuilder<CommandSourceStack> time(WorldEventsPlugin plugin) {
        LiteralArgumentBuilder<CommandSourceStack> jitter = Commands.literal("jitter")
                .then(Commands.literal("none").executes(context -> update(context, plugin,
                        section -> section.set("jitter.distribution", DistributionType.NONE.name()))));

        for (DistributionType distribution : List.of(DistributionType.UNIFORM, DistributionType.NORMAL)) {
            jitter = jitter.then(Commands.literal(distribution.name().toLowerCase(Locale.ROOT))
                    .then(Commands.argument("before", StringArgumentType.word())
                            .suggests(CommandSupport.suggest(DELAY_SUGGESTIONS))
                            .then(Commands.argument("after", StringArgumentType.word())
                                    .suggests(CommandSupport.suggest(DELAY_SUGGESTIONS))
                                    .executes(context -> {
                                        if (CommandSupport.duration(context, plugin, "before").isEmpty()
                                                || CommandSupport.duration(context, plugin, "after").isEmpty()) {
                                            return Command.SINGLE_SUCCESS;
                                        }
                                        return update(context, plugin, section -> {
                                            section.set("jitter.distribution", distribution.name());
                                            section.set("jitter.before",
                                                    StringArgumentType.getString(context, "before"));
                                            section.set("jitter.after",
                                                    StringArgumentType.getString(context, "after"));
                                        });
                                    }))));
        }

        return Commands.literal("time")
                .requires(CommandSupport.permission("time"))
                .then(CommandSupport.eventArgument(plugin)
                        .then(Commands.literal("manual").executes(context -> update(context, plugin,
                                section -> section.set("type", ScheduleType.NONE.name()))))
                        .then(Commands.literal("periodic")
                                .then(Commands.argument("interval", StringArgumentType.word())
                                        .suggests(CommandSupport.suggest(DELAY_SUGGESTIONS))
                                        .executes(context -> CommandSupport.duration(context, plugin, "interval")
                                                .map(interval -> update(context, plugin, section -> {
                                                    section.set("type", ScheduleType.PERIODIC.name());
                                                    section.set("interval",
                                                            StringArgumentType.getString(context, "interval"));
                                                }))
                                                .orElse(Command.SINGLE_SUCCESS))))
                        .then(Commands.literal("fixed")
                                .then(Commands.argument("times", StringArgumentType.greedyString())
                                        .suggests(CommandSupport.suggest(TIME_SUGGESTIONS))
                                        .executes(context -> fixed(context, plugin))))
                        .then(Commands.literal("random")
                                .then(Commands.argument("min", StringArgumentType.word())
                                        .suggests(CommandSupport.suggest(DELAY_SUGGESTIONS))
                                        .then(Commands.argument("max", StringArgumentType.word())
                                                .suggests(CommandSupport.suggest(DELAY_SUGGESTIONS))
                                                .executes(context -> random(context, plugin)))))
                        .then(jitter));
    }

    private static int fixed(CommandContext<CommandSourceStack> context, WorldEventsPlugin plugin) {
        String raw = StringArgumentType.getString(context, "times");
        List<String> times = Arrays.stream(raw.split("[,\\s]+")).filter(value -> !value.isBlank()).toList();
        try {
            times.forEach(ScheduleFactory::time);
        } catch (RuntimeException e) {
            plugin.lang().send(context.getSource().getSender(), Msg.INVALID_TIME, Placeholder.unparsed("input", raw));
            return Command.SINGLE_SUCCESS;
        }
        return update(context, plugin, section -> {
            section.set("type", ScheduleType.FIXED_TIME.name());
            section.set("times", times);
        });
    }

    private static int random(CommandContext<CommandSourceStack> context, WorldEventsPlugin plugin) {
        if (CommandSupport.duration(context, plugin, "min").isEmpty()
                || CommandSupport.duration(context, plugin, "max").isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        return update(context, plugin, section -> {
            section.set("type", ScheduleType.RANDOM.name());
            section.set("min-delay", StringArgumentType.getString(context, "min"));
            section.set("max-delay", StringArgumentType.getString(context, "max"));
        });
    }

    private static int update(CommandContext<CommandSourceStack> context, WorldEventsPlugin plugin,
                              Consumer<ConfigurationSection> mutator) {
        CommandSupport.event(context, plugin).ifPresent(event -> {
            ConfigurationSection section = plugin.configManager().orCreateSchedule(event.id());
            if (section == null) {
                return;
            }
            mutator.accept(section);
            plugin.configManager().save();
            plugin.installSchedule(event.id());
            plugin.lang().send(context.getSource().getSender(), Msg.TIME_UPDATED,
                    Placeholder.unparsed("event", event.displayName()),
                    Placeholder.unparsed("schedule", plugin.scheduler().schedule(event.id()).describe()));
        });
        return Command.SINGLE_SUCCESS;
    }

    private static int arm(CommandContext<CommandSourceStack> context, WorldEventsPlugin plugin, WorldEvent event,
                           Instant at) {
        plugin.scheduler().scheduleAt(event.id(), at);
        plugin.lang().send(context.getSource().getSender(), Msg.SCHEDULE_SET,
                Placeholder.unparsed("event", event.displayName()),
                Placeholder.unparsed("time", CommandSupport.formatInstant(at, plugin.configManager().zone())),
                Placeholder.unparsed("in", CommandSupport.formatUntil(at)));
        return Command.SINGLE_SUCCESS;
    }

    private static Optional<Instant> nextOccurrence(CommandContext<CommandSourceStack> context,
                                                    WorldEventsPlugin plugin) {
        String raw = StringArgumentType.getString(context, "time");
        try {
            LocalTime time = ScheduleFactory.time(raw);
            ZoneId zone = plugin.configManager().zone();
            ZonedDateTime now = ZonedDateTime.now(zone);
            ZonedDateTime target = now.with(time);
            return Optional.of((target.isAfter(now) ? target : target.plusDays(1)).toInstant());
        } catch (RuntimeException e) {
            plugin.lang().send(context.getSource().getSender(), Msg.INVALID_TIME, Placeholder.unparsed("input", raw));
            return Optional.empty();
        }
    }
}
