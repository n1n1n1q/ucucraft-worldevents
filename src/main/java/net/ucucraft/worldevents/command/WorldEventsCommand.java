package net.ucucraft.worldevents.command;

import java.util.logging.Level;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.ucucraft.worldevents.WorldEventsPlugin;
import net.ucucraft.worldevents.lang.Msg;

@SuppressWarnings("UnstableApiUsage")
public final class WorldEventsCommand {

    private WorldEventsCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> build(WorldEventsPlugin plugin) {
        return Commands.literal("worldevent")
                .requires(source -> source.getSender().hasPermission(CommandSupport.PERMISSION))
                .then(QueryCommands.list(plugin))
                .then(QueryCommands.info(plugin))
                .then(LifecycleCommands.start(plugin))
                .then(LifecycleCommands.stop(plugin))
                .then(ScheduleCommands.schedule(plugin))
                .then(ScheduleCommands.cancel(plugin))
                .then(ScheduleCommands.time(plugin))
                .then(reload(plugin))
                .build();
    }

    private static LiteralArgumentBuilder<CommandSourceStack> reload(WorldEventsPlugin plugin) {
        return Commands.literal("reload")
                .requires(CommandSupport.permission("reload"))
                .executes(context -> {
                    try {
                        plugin.reload();
                        plugin.lang().send(context.getSource().getSender(), Msg.RELOAD_SUCCESS);
                    } catch (RuntimeException e) {
                        plugin.getLogger().log(Level.SEVERE, "Reload failed", e);
                        plugin.lang().send(context.getSource().getSender(), Msg.RELOAD_FAILED);
                    }
                    return Command.SINGLE_SUCCESS;
                });
    }
}
