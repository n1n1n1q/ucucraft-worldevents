package net.ucucraft.worldevents.events.endstone.backup;

public enum ManifestState {
    /** Backup files are still being written; nothing has been converted yet. */
    BACKING_UP,
    /** Backups are fsynced; the write engine is turning blocks into the conversion material. */
    CONVERTING,
    /** Conversion finished; the region is live and minable. */
    ACTIVE,
    /** The write engine is putting the original blocks back. */
    RESTORING
}
