package miningsim;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import miningsim.persistence.WorldIO;
import miningsim.simulation.Simulation;
import miningsim.world.Material;
import miningsim.world.World;

class BedrockTest {
    @Test void initializationAndClearAcrossChunkEdges() {
        for (int n : new int[]{1, 63, 64, 65}) {
            World w = new World(n, n, World.Mode.BEDROCK_ENABLED);
            for (int x = 0; x < n; x++) assertEquals(Material.BEDROCK, w.get(x, n - 1));
            if (n > 1) w.set(n - 1, n - 2, Material.STONE);
            w.clear();
            assertEquals(n, w.occupiedCount());
            for (int x = 0; x < n; x++) assertEquals(Material.BEDROCK, w.get(x, n - 1));
        }
        World legacy = new World(1, 1); legacy.set(0, 0, Material.STONE); legacy.clear();
        assertEquals(Material.AIR, legacy.get(0, 0));
    }

    @Test void resultsEventsAndActivity() {
        World w = new World(65, 65, World.Mode.BEDROCK_ENABLED);
        List<String> events = new ArrayList<>();
        w.setChangeSink(new World.ChangeSink() {
            public void changed(int x, int y, byte oldId, byte newId) { events.add(x + "," + y + ":" + oldId + ">" + newId); }
            public void invalidated() { events.add("all"); }
        });
        w.setActivityTracking(true);
        w.collectActivity(new boolean[9]);
        assertEquals(World.Mutation.REJECTED, w.set(64, 64, Material.AIR));
        assertEquals(World.Mutation.NO_OP, w.set(64, 64, Material.BEDROCK));
        assertEquals(World.Mutation.REJECTED, w.set(64, 63, Material.BEDROCK));
        boolean[] active = new boolean[9]; w.collectActivity(active);
        for (boolean b : active) assertFalse(b);
        assertEquals(List.of("all"), events);
        assertEquals(World.Mutation.CHANGED, w.set(64, 63, Material.STONE));
        assertEquals(World.Mutation.NO_OP, w.set(64, 63, Material.STONE));
        assertEquals(World.Mutation.CHANGED, w.set(63, 63, Material.WATER));
        assertEquals(List.of("all", "64,63:0>1", "63,63:0>3"), events);
        w.clear(); assertEquals("all", events.getLast());
        assertThrows(IndexOutOfBoundsException.class, () -> w.set(65, 0, Material.AIR));
    }

    @Test void materialsCannotDisplaceBedrock() {
        assertEquals(7, Material.BEDROCK.id());
        assertEquals(Material.Category.STATIC, Material.BEDROCK.category());
        for (Material material : new Material[]{Material.SAND, Material.GRAVEL, Material.SOIL, Material.WATER}) {
            World w = new World(65, 4, World.Mode.BEDROCK_ENABLED);
            for (int x = 0; x < 65; x++) w.set(x, 2, material);
            Simulation s = new Simulation(w, 42);
            for (int i = 0; i < 20; i++) s.step();
            for (int x = 0; x < 65; x++) assertEquals(Material.BEDROCK, w.get(x, 3));
            assertEquals(130, w.occupiedCount());
        }
    }

    @Test void v1RefusalDoesNotTouchStreamOrFile(@TempDir Path dir) throws IOException {
        World w = new World(2, 2, World.Mode.BEDROCK_ENABLED);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThrows(IOException.class, () -> WorldIO.write(w, out)); assertEquals(0, out.size());
        Path path = dir.resolve("world.msim"); Files.write(path, new byte[]{1, 2});
        assertThrows(IOException.class, () -> WorldIO.write(w, path));
        assertArrayEquals(new byte[]{1, 2}, Files.readAllBytes(path));
    }
}
