package net.ucucraft.worldevents.command;

import java.time.Instant;
import java.time.ZoneId;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.ucucraft.worldevents.WorldEventsPlugin;
import net.ucucraft.worldevents.event.WorldEvent;
import net.ucucraft.worldevents.lang.LangManager;
import net.ucucraft.worldevents.lang.Msg;
import net.ucucraft.worldevents.util.Durations;
import org.bukkit.command.CommandSender;

@SuppressWarnings("UnstableApiUsage")
final class QueryCommands {

    private QueryCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> list(WorldEventsPlugin plugin) {
        return Commands.literal("list")
                .requires(CommandSupport.permission("list"))
                .executes(context -> {
                    CommandSender sender = context.getSource().getSender();
                    LangManager lang = plugin.lang();
                    ZoneId zone = plugin.configManager().zone();

                    if (plugin.manager().events().isEmpty()) {
                        lang.send(sender, Msg.LIST_EMPTY);
                        return Command.SINGLE_SUCCESS;
                    }

                    lang.send(sender, Msg.LIST_HEADER,
                            Placeholder.unparsed("count", String.valueOf(plugin.manager().events().size())));
                    for (WorldEvent event : plugin.manager().events()) {
                        String next = plugin.scheduler().nextRun(event.id())
                                .map(instant -> CommandSupport.formatInstant(instant, zone))
                                .orElse(lang.raw(Msg.NEVER));
                        lang.sendRaw(sender, Msg.LIST_ENTRY,
                                Placeholder.unparsed("event", event.displayName()),
                                Placeholder.unparsed("id", event.id()),
                                Placeholder.component("state", state(lang, event)),
                                Placeholder.unparsed("next", next));
                    }
                    return Command.SINGLE_SUCCESS;
                });
    }

    static LiteralArgumentBuilder<CommandSourceStack> info(WorldEventsPlugin plugin) {
        return Commands.literal("info")
                .requires(CommandSupport.permission("info"))
                .then(CommandSupport.eventArgument(plugin).executes(context -> {
                    CommandSupport.event(context, plugin).ifPresent(event -> {
                        CommandSender sender = context.getSource().getSender();
                        LangManager lang = plugin.lang();
                        ZoneId zone = plugin.configManager().zone();

                        lang.send(sender, Msg.INFO_HEADER, event.placeholders());
                        lang.sendRaw(sender, Msg.INFO_STATE, Placeholder.component("state", state(lang, event)));
                        lang.sendRaw(sender, Msg.INFO_DURATION, Placeholder.unparsed("duration",
                                event.duration().isZero() ? "-" : Durations.format(event.duration())));
                        lang.sendRaw(sender, Msg.INFO_SCHEDULE, Placeholder.unparsed("schedule",
                                plugin.scheduler().schedule(event.id()).describe()));

                        plugin.scheduler().nextRun(event.id()).ifPresentOrElse(
                                next -> lang.sendRaw(sender, Msg.INFO_NEXT_RUN,
                                        Placeholder.unparsed("time", CommandSupport.formatInstant(next, zone)),
                                        Placeholder.unparsed("in", CommandSupport.formatUntil(next))),
                                () -> lang.sendRaw(sender, Msg.INFO_NO_NEXT_RUN));

                        event.endsAt().filter(end -> end.isAfter(Instant.now())).ifPresent(end ->
                                lang.sendRaw(sender, Msg.INFO_ENDS_IN,
                                        Placeholder.unparsed("in", CommandSupport.formatUntil(end))));
                    });
                    return Command.SINGLE_SUCCESS;
                }));
    }

    private static Component state(LangManager lang, WorldEvent event) {
        return lang.render(event.running() ? Msg.STATE_RUNNING : Msg.STATE_IDLE);
    }
}
