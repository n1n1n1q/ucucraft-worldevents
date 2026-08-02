package net.ucucraft.worldevents.command;

import java.time.Duration;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.ucucraft.worldevents.WorldEventsPlugin;
import net.ucucraft.worldevents.event.EventTrigger;
import net.ucucraft.worldevents.event.StopReason;
import net.ucucraft.worldevents.lang.Msg;

@SuppressWarnings("UnstableApiUsage")
final class LifecycleCommands {

    private LifecycleCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> start(WorldEventsPlugin plugin) {
        return Commands.literal("start")
                .requires(CommandSupport.permission("start"))
                .then(CommandSupport.eventArgument(plugin)
                        .executes(context -> start(context, plugin, null))
                        .then(Commands.argument("duration", StringArgumentType.word())
                                .suggests(CommandSupport.suggest("30s", "5m", "10m", "1h"))
                                .executes(context -> CommandSupport.duration(context, plugin, "duration")
                                        .map(duration -> start(context, plugin, duration))
                                        .orElse(Command.SINGLE_SUCCESS))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> stop(WorldEventsPlugin plugin) {
        return Commands.literal("stop")
                .requires(CommandSupport.permission("stop"))
                .then(CommandSupport.eventArgument(plugin).executes(context -> {
                    CommandSupport.event(context, plugin).ifPresent(event -> {
                        boolean stopped = event.stop(StopReason.COMMAND);
                        plugin.lang().send(context.getSource().getSender(),
                                stopped ? Msg.STOP_SUCCESS : Msg.STOP_NOT_RUNNING, event.placeholders());
                    });
                    return Command.SINGLE_SUCCESS;
                }));
    }

    private static int start(CommandContext<CommandSourceStack> context, WorldEventsPlugin plugin,
                             Duration duration) {
        CommandSupport.event(context, plugin).ifPresent(event -> {
            boolean started = event.start(EventTrigger.COMMAND, duration);
            plugin.lang().send(context.getSource().getSender(),
                    started ? Msg.START_SUCCESS : Msg.START_ALREADY_RUNNING, event.placeholders());
        });
        return Command.SINGLE_SUCCESS;
    }
}
