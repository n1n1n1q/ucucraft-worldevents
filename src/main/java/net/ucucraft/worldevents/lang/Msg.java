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
    RELOAD_FAILED("command.reload.failed");

    private final String path;

    Msg(String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }
}
