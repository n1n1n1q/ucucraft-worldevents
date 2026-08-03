package net.ucucraft.worldevents.events.endstone.backup;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/** Owns the on-disk layout under {@code plugins/<plugin>/<directory>/<runId>/}. */
public final class BackupStore {

    private final File root;
    private final Logger logger;

    public BackupStore(File pluginDataFolder, String directory, Logger logger) {
        this.root = new File(pluginDataFolder, directory);
        this.logger = logger;
        root.mkdirs();
    }

    public File runDir(String runId) {
        return new File(root, runId);
    }

    public File manifestFile(String runId) {
        return new File(runDir(runId), "manifest.yml");
    }

    public File chunkFile(String runId, int x, int z) {
        return new File(runDir(runId), "chunk_" + x + "_" + z + ".bin.gz");
    }

    /** Writes {@code *.tmp} then fsyncs and atomically moves it into place: the backup is either
     *  fully present or not there at all, never half-written. */
    public void writeChunk(String runId, ChunkBackup backup) {
        File dir = runDir(runId);
        dir.mkdirs();
        File target = chunkFile(runId, backup.chunkX(), backup.chunkZ());
        File tmp = new File(dir, target.getName() + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tmp)) {
            // BackupCodec closes the gzip stream it wraps around this one (needed to end the Deflater and
            // write the gzip trailer), which would also close fos and leave a closed descriptor for the
            // sync below. Shield fos from that close so it is still open when we fsync it.
            BackupCodec.write(backup, new FilterOutputStream(fos) {
                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    out.write(b, off, len);
                }

                @Override
                public void close() throws IOException {
                    flush();
                }
            });
            fos.getFD().sync();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write blight backup for chunk "
                    + backup.chunkX() + "," + backup.chunkZ() + " in run " + runId, e);
        }
        try {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public ChunkBackup readChunk(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            return BackupCodec.read(fis);
        }
    }

    /** Backup files still present for a run: exactly the chunks not yet restored. */
    public File[] listChunkFiles(String runId) {
        File[] files = runDir(runId).listFiles((dir, name) -> name.startsWith("chunk_") && name.endsWith(".bin.gz"));
        return files != null ? files : new File[0];
    }

    public List<String> listRunIds() {
        File[] dirs = root.listFiles(File::isDirectory);
        List<String> ids = new ArrayList<>();
        if (dirs == null) {
            return ids;
        }
        for (File dir : dirs) {
            if (!dir.getName().endsWith(".broken")) {
                ids.add(dir.getName());
            }
        }
        return ids;
    }

    public boolean hasManifest(String runId) {
        return manifestFile(runId).isFile();
    }

    public void deleteChunkFile(String runId, int x, int z) {
        chunkFile(runId, x, z).delete();
    }

    public void deleteRun(String runId) {
        deleteRecursively(runDir(runId));
    }

    /** Never delete a backup that failed to apply cleanly: move the whole run dir aside instead. */
    public void quarantine(String runId) {
        File dir = runDir(runId);
        File target = new File(root, runId + ".broken");
        try {
            if (target.exists()) {
                deleteRecursively(target);
            }
            Files.move(dir.toPath(), target.toPath());
        } catch (IOException e) {
            logger.warning("Failed to quarantine blight run '" + runId + "': " + e.getMessage());
        }
    }

    private void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }
}
