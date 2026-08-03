package net.ucucraft.worldevents.events.endstone.backup;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.UUID;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Per-run state, persisted as {@code manifest.yml} so an admin can read it directly. Which chunks
 * still need restoring is never tracked here: it is exactly "which {@code chunk_*.bin.gz} files are
 * still present in the run directory" ({@link BackupStore#listChunkFiles}), so a restored chunk's
 * backup file is deleted and there is no separate progress list to fall out of sync.
 */
public final class BlightManifest {

    private final File file;

    public String eventId;
    public String world;
    public UUID worldId;
    public ManifestState state;
    public Instant endsAt;

    private BlightManifest(File file) {
        this.file = file;
    }

    public static BlightManifest create(File file, String eventId, String world, UUID worldId, Instant endsAt) {
        BlightManifest manifest = new BlightManifest(file);
        manifest.eventId = eventId;
        manifest.world = world;
        manifest.worldId = worldId;
        manifest.state = ManifestState.BACKING_UP;
        manifest.endsAt = endsAt;
        return manifest;
    }

    public static BlightManifest load(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        BlightManifest manifest = new BlightManifest(file);
        manifest.eventId = yaml.getString("event-id", "");
        manifest.world = yaml.getString("world", "");
        manifest.worldId = UUID.fromString(yaml.getString("world-id", new UUID(0, 0).toString()));
        manifest.state = ManifestState.valueOf(yaml.getString("state", ManifestState.BACKING_UP.name()));
        String endsAt = yaml.getString("ends-at");
        manifest.endsAt = endsAt != null ? Instant.parse(endsAt) : null;
        return manifest;
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("event-id", eventId);
        yaml.set("world", world);
        yaml.set("world-id", worldId.toString());
        yaml.set("state", state.name());
        yaml.set("ends-at", endsAt != null ? endsAt.toString() : null);

        File dir = file.getParentFile();
        dir.mkdirs();
        File tmp = new File(dir, file.getName() + ".tmp");
        try {
            yaml.save(tmp);
            try (FileOutputStream fos = new FileOutputStream(tmp, true)) {
                fos.getFD().sync();
            }
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save blight manifest at " + file, e);
        }
    }
}
