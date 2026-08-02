package net.ucucraft.worldevents.command;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.ucucraft.worldevents.WorldEventsPlugin;
import net.ucucraft.worldevents.event.WorldEvent;
import net.ucucraft.worldevents.lang.Msg;
import net.ucucraft.worldevents.util.Durations;

@SuppressWarnings("UnstableApiUsage")
final class CommandSupport {

    static final String PERMISSION = "worldevents.command";

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private CommandSupport() {
    }

    static Predicate<CommandSourceStack> permission(String node) {
        return source -> source.getSender().hasPermission(PERMISSION + "." + node);
    }

    static RequiredArgumentBuilder<CommandSourceStack, String> eventArgument(WorldEventsPlugin plugin) {
        return Commands.argument("event", StringArgumentType.word())
                .suggests((context, builder) -> {
                    String prefix = builder.getRemainingLowerCase();
                    plugin.manager().ids().stream()
                            .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(prefix))
                            .forEach(builder::suggest);
                    return builder.buildFuture();
                });
    }

    static SuggestionProvider<CommandSourceStack> suggest(String... values) {
        return (context, builder) -> {
            String prefix = builder.getRemainingLowerCase();
            Arrays.stream(values)
                    .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .forEach(builder::suggest);
            return builder.buildFuture();
        };
    }

    /** Resolves the {@code event} argument, messaging the sender when it is unknown. */
    static Optional<WorldEvent> event(CommandContext<CommandSourceStack> context, WorldEventsPlugin plugin) {
        String id = StringArgumentType.getString(context, "event");
        Optional<WorldEvent> event = plugin.manager().event(id);
        if (event.isEmpty()) {
            plugin.lang().send(context.getSource().getSender(), Msg.UNKNOWN_EVENT, Placeholder.unparsed("id", id));
        }
        return event;
    }

    /** Parses a duration argument, messaging the sender when it is malformed. */
    static Optional<Duration> duration(CommandContext<CommandSourceStack> context, WorldEventsPlugin plugin,
                                       String name) {
        String raw = StringArgumentType.getString(context, name);
        try {
            return Optional.of(Durations.parse(raw));
        } catch (RuntimeException e) {
            plugin.lang().send(context.getSource().getSender(), Msg.INVALID_DURATION,
                    Placeholder.unparsed("input", raw));
            return Optional.empty();
        }
    }

    static String formatInstant(Instant instant, ZoneId zone) {
        return TIME_FORMAT.format(instant.atZone(zone));
    }

    static String formatUntil(Instant instant) {
        return Durations.format(Duration.between(Instant.now(), instant));
    }
}
