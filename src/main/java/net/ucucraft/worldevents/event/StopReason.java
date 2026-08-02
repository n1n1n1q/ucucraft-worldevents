package net.ucucraft.worldevents.event;

public enum StopReason {

    /** The configured duration elapsed. */
    FINISHED(true),
    COMMAND(true),
    RELOAD(false),
    SHUTDOWN(false);

    private final boolean announced;

    StopReason(boolean announced) {
        this.announced = announced;
    }

    public boolean announced() {
        return announced;
    }
}
