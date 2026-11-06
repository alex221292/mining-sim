package miningsim;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import miningsim.persistence.WorldIO;
import miningsim.simulation.Simulation;
import miningsim.world.Material;
import miningsim.world.World;

class WorldIOTest {
    private static byte[] bytes(World world) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); WorldIO.write(world, out); return out.toByteArray();
    }
    private static World load(byte[] data) throws IOException { return WorldIO.read(new ByteArrayInputStream(data)); }
    private static void same(World a, World b) {
        assertEquals(a.width(), b.width()); assertEquals(a.height(), b.height());
        for (int y = 0; y < a.height(); y++) for (int x = 0; x < a.width(); x++) assertEquals(a.get(x, y), b.get(x, y));
    }
    private static void crc(byte[] data) {
        CRC32 crc = new CRC32(); crc.update(data, 0, data.length - 4);
        ByteBuffer.wrap(data).putInt(data.length - 4, (int) crc.getValue());
    }
    private static void invalid(byte[] data, String message) {
        IOException ex = assertThrows(IOException.class, () -> load(data));
        assertTrue(ex.getMessage().toLowerCase().contains(message.toLowerCase()), ex.getMessage());
    }

    @Test void roundTripsEmptyAndMixedSizesWithStableBytes() throws IOException {
        for (int[] size : new int[][]{{1,1},{63,63},{64,64},{65,65},{130,66}}) {
            World w = new World(size[0], size[1]); byte[] empty = bytes(w); same(w, load(empty));
            assertEquals(24 + size[0] * size[1], empty.length);
            for (int y = 0; y < w.height(); y++) for (int x = 0; x < w.width(); x++)
                w.set(x, y, Material.fromId((byte) ((x + 3 * y) % 7)));
            byte[] saved = bytes(w); World restored = load(saved); same(w, restored);
            assertArrayEquals(saved, bytes(restored)); assertArrayEquals(saved, bytes(w));
        }
    }

    @Test void encodingUsesChunkThenLocalRowOrderAndNoEdgePadding() throws IOException {
        World w = new World(65, 2); w.set(63, 0, Material.SAND); w.set(64, 0, Material.WATER);
        w.set(0, 1, Material.WOOD); w.set(64, 1, Material.SOIL);
        byte[] data = bytes(w);
        assertEquals(2, data[20 + 63]); assertEquals(4, data[20 + 64]);
        assertEquals(3, data[20 + 128]); assertEquals(6, data[20 + 129]);
        assertEquals(154, data.length); same(w, load(data));
    }

    @Test void pathRoundTrip(@TempDir Path dir) throws IOException {
        World w = new World(65, 65); w.set(64, 64, Material.GRAVEL);
        Path path = dir.resolve("world.msim"); WorldIO.write(w, path); same(w, WorldIO.read(path));
        w.set(0, 0, Material.SOIL); WorldIO.write(w, path); same(w, WorldIO.read(path));
    }

    @Test void rejectedSaveDoesNotTruncateExistingFile(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("existing.msim"); byte[] previous = {1, 2, 3}; Files.write(path, previous);
        assertThrows(IOException.class, () -> WorldIO.write(new World(4097, 1), path));
        assertArrayEquals(previous, Files.readAllBytes(path));
    }

    @Test void rejectsBadHeadersAndDimensionsBeforeAllocation() throws IOException {
        byte[] good = bytes(new World(1, 1));
        byte[] bad = good.clone(); bad[0] = 0; invalid(bad, "magic");
        bad = good.clone(); ByteBuffer.wrap(bad).putInt(4, 2); invalid(bad, "version");
        for (int width : new int[]{0, -1, 4097, Integer.MAX_VALUE}) {
            bad = good.clone(); ByteBuffer.wrap(bad).putInt(8, width); invalid(bad, "dimensions");
        }
        bad = good.clone(); ByteBuffer.wrap(bad).putInt(12, 0); invalid(bad, "dimensions");
        bad = good.clone(); ByteBuffer.wrap(bad).putInt(8, 4096).putInt(12, 4096); invalid(bad, "count");
        bad = good.clone(); ByteBuffer.wrap(bad).putInt(16, 32); invalid(bad, "chunk size");
    }

    @Test void rejectsEveryTruncationInvalidIdChecksumAndTrailingData() throws IOException {
        byte[] good = bytes(new World(65, 2));
        for (int length = 0; length < good.length; length++) invalid(Arrays.copyOf(good, length), "truncated");
        byte[] bad = good.clone(); bad[20] = (byte) 127; crc(bad); invalid(bad, "material ID");
        bad = good.clone(); bad[20] = 7; crc(bad); invalid(bad, "material ID");
        bad = good.clone(); bad[20] = Material.SAND.id(); invalid(bad, "checksum");
        bad = good.clone(); bad[bad.length - 1] ^= 1; invalid(bad, "checksum");
        bad = Arrays.copyOf(good, good.length + 1); invalid(bad, "trailing");
    }

    @Test void loadedWorldSimulatesAndInitializesActivity() throws IOException {
        for (Simulation.Mode mode : new Simulation.Mode[]{Simulation.Mode.FULL_SCAN, Simulation.Mode.ACTIVE_REGIONS}) {
            World original = new World(65, 65); original.set(63, 63, Material.SAND);
            World restored = load(bytes(original));
            Simulation s = new Simulation(restored, 7, mode, false); s.step();
            assertEquals(Material.SAND, restored.get(63, 64));
            assertEquals(Material.SAND, original.get(63, 63));
        }
    }
}
