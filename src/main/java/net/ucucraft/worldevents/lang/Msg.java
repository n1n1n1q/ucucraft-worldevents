package net.ucucraft.worldevents.lang;

public enum Msg {

    PREFIX("prefix"),

    STATE_RUNNING("state.running"),
    STATE_IDLE("state.idle"),
    NEVER("never"),

    EVENT_STARTED("event.started"),
    EVENT_STOPPED("event.stopped"),

    UNKNOWN_EVENT("command.unknown-event"),
    INVALID_DURATION("command.invalid-duration"),
    INVALID_TIME("command.invalid-time"),

    LIST_HEADER("command.list.header"),
    LIST_ENTRY("command.list.entry"),
    LIST_EMPTY("command.list.empty"),

    INFO_HEADER("command.info.header"),
    INFO_STATE("command.info.state"),
    INFO_DURATION("command.info.duration"),
    INFO_SCHEDULE("command.info.schedule"),
    INFO_NEXT_RUN("command.info.next-run"),
    INFO_NO_NEXT_RUN("command.info.no-next-run"),
    INFO_ENDS_IN("command.info.ends-in"),

    START_SUCCESS("command.start.success"),
    START_ALREADY_RUNNING("command.start.already-running"),

    STOP_SUCCESS("command.stop.success"),
    STOP_NOT_RUNNING("command.stop.not-running"),

    SCHEDULE_SET("command.schedule.set"),

    CANCEL_SUCCESS("command.cancel.success"),
    CANCEL_NOTHING("command.cancel.nothing"),

    TIME_UPDATED("command.time.updated"),

    RELOAD_SUCCESS("command.reload.success"),
    RELOAD_FAILED("command.reload.failed"),

    BLIGHT_UNAVAILABLE("event.blight.unavailable"),
    BLIGHT_BUSY("event.blight.busy"),
    BLIGHT_NO_REGION("event.blight.no-region"),
    BLIGHT_SPREADING("event.blight.spreading"),
    BLIGHT_ACTIVE("event.blight.active"),
    BLIGHT_LOCATION("event.blight.location"),
    BLIGHT_OUTRO_DARKNESS("event.blight.outro.darkness"),
    BLIGHT_OUTRO_TELEPORT("event.blight.outro.teleport"),
    BLIGHT_RESTORING("event.blight.restoring"),
    BLIGHT_RESTORED("event.blight.restored"),
    BLIGHT_RECOVERED("event.blight.recovered"),
    BLIGHT_BLOCKS_LOST_WARNING("event.blight.blocks-lost-warning");

    private final String path;

    Msg(String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }
}
