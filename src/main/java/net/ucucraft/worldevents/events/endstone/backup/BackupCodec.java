package net.ucucraft.worldevents.events.endstone.backup;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Binary, gzip-wrapped encoding of a {@link ChunkBackup}. Positions are emitted in scan order
 * (section-major, y descending, then z, x), which is regular enough that gzip alone gets within
 * ~1.5x of a hand-rolled varint encoding for zero extra code.
 */
public final class BackupCodec {

    private static final int MAGIC = 0x55434253;
    private static final byte VERSION = 1;

    private BackupCodec() {
    }

    public static void write(ChunkBackup backup, OutputStream rawOut) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(rawOut))) {
            out.writeInt(MAGIC);
            out.writeByte(VERSION);
            out.writeUTF(backup.world());
            out.writeUTF(backup.worldId().toString());
            out.writeInt(backup.chunkX());
            out.writeInt(backup.chunkZ());
            out.writeShort(backup.minY());
            out.writeShort(backup.maxY());

            List<String> palette = backup.palette();
            out.writeShort(palette.size());
            for (String entry : palette) {
                out.writeUTF(entry);
            }

            int[] positions = backup.positions();
            short[] indices = backup.paletteIndex();
            out.writeInt(positions.length);
            for (int i = 0; i < positions.length; i++) {
                out.writeInt(positions[i]);
                out.writeShort(indices[i]);
            }
        }
    }

    public static ChunkBackup read(InputStream rawIn) throws IOException {
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(rawIn))) {
            int magic = in.readInt();
            if (magic != MAGIC) {
                throw new IOException("Bad blight backup magic: 0x" + Integer.toHexString(magic));
            }
            byte version = in.readByte();
            if (version != VERSION) {
                throw new IOException("Unsupported blight backup version: " + version);
            }

            String world = in.readUTF();
            UUID worldId = UUID.fromString(in.readUTF());
            int chunkX = in.readInt();
            int chunkZ = in.readInt();
            int minY = in.readShort();
            int maxY = in.readShort();

            int paletteSize = in.readUnsignedShort();
            List<String> palette = new ArrayList<>(paletteSize);
            for (int i = 0; i < paletteSize; i++) {
                palette.add(in.readUTF());
            }

            int entryCount = in.readInt();
            int[] positions = new int[entryCount];
            short[] indices = new short[entryCount];
            for (int i = 0; i < entryCount; i++) {
                positions[i] = in.readInt();
                indices[i] = in.readShort();
            }

            return new ChunkBackup(world, worldId, chunkX, chunkZ, minY, maxY, palette, positions, indices);
        }
    }

    public static int packPosition(int localX, int localZ, int y, int minY) {
        return ((localX & 0xF) << 20) | ((localZ & 0xF) << 16) | ((y - minY) & 0xFFFF);
    }

    public static int unpackLocalX(int packed) {
        return (packed >> 20) & 0xF;
    }

    public static int unpackLocalZ(int packed) {
        return (packed >> 16) & 0xF;
    }

    public static int unpackY(int packed, int minY) {
        return (packed & 0xFFFF) + minY;
    }
}
