package net.ucucraft.worldevents.events.endstone.backup;

public enum RestoreMode {
    /** Write every backed-up position unconditionally. Idempotent, which is what makes crash recovery simple. */
    ALL,
    /** Only restore positions whose current block still matches the conversion material. Costs a read per block. */
    UNCHANGED
}
