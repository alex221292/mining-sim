package miningsim.persistence;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.CheckedOutputStream;
import miningsim.world.Material;
import miningsim.world.World;

/** MSIM v1: streams remain owned by the caller. */
public final class WorldIO {
    private static final int MAGIC = 0x4d53494d, VERSION = 1;
    public static final int MAX_DIMENSION = 4096, MAX_CELLS = 4_194_304;
    private WorldIO() { }

    public static void write(World world, OutputStream output) throws IOException {
        validateDimensions(world.width(), world.height());
        CRC32 crc = new CRC32();
        DataOutputStream checked = new DataOutputStream(new CheckedOutputStream(output, crc));
        checked.writeInt(MAGIC); checked.writeInt(VERSION);
        checked.writeInt(world.width()); checked.writeInt(world.height()); checked.writeInt(World.CHUNK_SIZE);
        for (int cy = 0; cy < world.height(); cy += World.CHUNK_SIZE)
            for (int cx = 0; cx < world.width(); cx += World.CHUNK_SIZE)
                for (int y = cy; y < Math.min(cy + World.CHUNK_SIZE, world.height()); y++)
                    for (int x = cx; x < Math.min(cx + World.CHUNK_SIZE, world.width()); x++)
                        checked.writeByte(world.get(x, y).id());
        checked.flush();
        DataOutputStream raw = new DataOutputStream(output);
        raw.writeInt((int) crc.getValue()); raw.flush();
    }

    public static World read(InputStream input) throws IOException {
        CRC32 crc = new CRC32();
        DataInputStream checked = new DataInputStream(new CheckedInputStream(input, crc));
        try {
            if (checked.readInt() != MAGIC) throw new IOException("Invalid MSIM magic");
            int version = checked.readInt();
            if (version != VERSION) throw new IOException("Unsupported MSIM version: " + version);
            int width = checked.readInt(), height = checked.readInt(), chunkSize = checked.readInt();
            validateDimensions(width, height);
            if (chunkSize != World.CHUNK_SIZE) throw new IOException("Invalid chunk size: " + chunkSize);
            World world = new World(width, height);
            for (int cy = 0; cy < height; cy += chunkSize)
                for (int cx = 0; cx < width; cx += chunkSize)
                    for (int y = cy; y < Math.min(cy + chunkSize, height); y++)
                        for (int x = cx; x < Math.min(cx + chunkSize, width); x++) {
                            int id = checked.readUnsignedByte();
                            Material material;
                            try { material = Material.fromId((byte) id); }
                            catch (IllegalArgumentException e) { throw new IOException("Invalid material ID " + id + " at " + x + "," + y, e); }
                            if (material != Material.AIR) world.set(x, y, material);
                        }
            long actual = crc.getValue();
            DataInputStream raw = new DataInputStream(input);
            long expected = Integer.toUnsignedLong(raw.readInt());
            if (actual != expected) throw new IOException("MSIM checksum mismatch");
            if (raw.read() != -1) throw new IOException("Unexpected trailing data after MSIM checksum");
            return world;
        } catch (EOFException e) {
            throw new IOException("Truncated MSIM file", e);
        }
    }

    public static World read(Path path) throws IOException {
        try (InputStream input = new BufferedInputStream(Files.newInputStream(path))) { return read(input); }
    }

    /** Write to a sibling temporary file first, so an interrupted write does not truncate the destination. */
    public static void write(World world, Path path) throws IOException {
        Path absolute = path.toAbsolutePath();
        Path temp = Files.createTempFile(absolute.getParent(), ".msim-", ".tmp");
        try {
            try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(temp))) { write(world, output); }
            try { Files.move(temp, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }

    private static void validateDimensions(int width, int height) throws IOException {
        if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION)
            throw new IOException("Invalid or oversized world dimensions: " + width + "x" + height);
        long cells;
        try { cells = Math.multiplyExact((long) width, (long) height); }
        catch (ArithmeticException e) { throw new IOException("World dimensions overflow", e); }
        if (cells > MAX_CELLS) throw new IOException("World cell count exceeds limit: " + cells);
    }
}
