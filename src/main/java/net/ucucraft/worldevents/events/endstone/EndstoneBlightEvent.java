package net.ucucraft.worldevents.events.endstone;

import net.ucucraft.worldevents.event.EventTrigger;
import net.ucucraft.worldevents.event.StopReason;
import net.ucucraft.worldevents.event.WorldEvent;
import net.ucucraft.worldevents.event.WorldEventContext;
import net.ucucraft.worldevents.lang.Msg;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Thin dispatcher: all the state and logic live in {@link BlightService}, which is shared and
 * plugin-lifetime, so a {@code /we reload} rebuilding this instance never orphans an in-progress run.
 */
public final class EndstoneBlightEvent extends WorldEvent {

    private final BlightService service;

    public EndstoneBlightEvent(String id, WorldEventContext context, ConfigurationSection config,
                                BlightService service) {
        super(id, context, config);
        this.service = service;
    }

    @Override
    protected boolean canStart(EventTrigger trigger) {
        BlightSettings parsed = BlightSettings.parse(settings(), context().plugin().getLogger());
        BlightService.PrepareResult result = service.prepare(parsed);
        if (result == BlightService.PrepareResult.OK) {
            return true;
        }
        Msg msg = switch (result) {
            case UNAVAILABLE -> Msg.BLIGHT_UNAVAILABLE;
            case BUSY -> Msg.BLIGHT_BUSY;
            default -> Msg.BLIGHT_NO_REGION;
        };
        if (trigger == EventTrigger.SCHEDULE) {
            context().plugin().getLogger().warning(displayName() + " refused to start: " + context().lang().raw(msg));
        } else {
            context().lang().broadcast(msg, placeholders());
        }
        return false;
    }

    @Override
    protected void onStart(EventTrigger trigger) {
        service.begin(id(), displayName());
    }

    @Override
    protected void onStop(StopReason reason) {
        service.end(reason);
    }
}
