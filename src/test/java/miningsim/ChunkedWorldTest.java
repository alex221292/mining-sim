package miningsim;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Random;
import org.junit.jupiter.api.Test;
import miningsim.simulation.Simulation;
import miningsim.world.Material;
import miningsim.world.World;

class ChunkedWorldTest {
    @Test void dimensionsEdgesAndClear() {
        for (int[] size : new int[][]{{1,1}, {63,63}, {64,64}, {65,65}, {400,300}, {800,600}}) {
            int width = size[0], height = size[1]; World w = new World(width, height);
            assertEquals(width, w.width()); assertEquals(height, w.height());
            assertEquals(Material.AIR, w.get(width - 1, height - 1));
            w.set(width - 1, height - 1, Material.WOOD);
            assertEquals(Material.WOOD, w.get(width - 1, height - 1));
            assertEquals(1, w.occupiedCount());
            w.set(width - 1, height - 1, Material.SAND); assertEquals(1, w.occupiedCount());
            w.set(width - 1, height - 1, Material.AIR); assertEquals(0, w.occupiedCount());
            assertThrows(IndexOutOfBoundsException.class, () -> w.get(width, 0));
            assertThrows(IndexOutOfBoundsException.class, () -> w.get(0, height));
            assertThrows(IndexOutOfBoundsException.class, () -> w.set(-1, 0, Material.STONE));
            assertThrows(IndexOutOfBoundsException.class, () -> w.set(0, -1, Material.STONE));
            w.set(0, 0, Material.STONE); w.set(width - 1, height - 1, Material.GRAVEL);
            w.clear(); assertEquals(0, w.occupiedCount());
            assertEquals(Material.AIR, w.get(0, 0)); assertEquals(Material.AIR, w.get(width - 1, height - 1));
        }
    }

    @Test void fourWayIntersectionPreservesEachMaterialAndClears() {
        World w = new World(130, 130);
        w.set(63, 63, Material.STONE); w.set(64, 63, Material.SAND);
        w.set(63, 64, Material.WATER); w.set(64, 64, Material.SOIL);
        w.set(129, 129, Material.GRAVEL);
        assertEquals(Material.STONE, w.get(63, 63)); assertEquals(Material.SAND, w.get(64, 63));
        assertEquals(Material.WATER, w.get(63, 64)); assertEquals(Material.SOIL, w.get(64, 64));
        assertEquals(Material.GRAVEL, w.get(129, 129)); assertEquals(5, w.occupiedCount());
        w.clear(); assertEquals(0, w.occupiedCount());
        for (int y : new int[]{63, 64, 129}) for (int x : new int[]{63, 64, 129}) assertEquals(Material.AIR, w.get(x, y));
    }

    @Test void particlesCrossChunkBoundariesInBothModes() {
        for (Simulation.Mode mode : new Simulation.Mode[]{Simulation.Mode.FULL_SCAN, Simulation.Mode.ACTIVE_REGIONS}) {
            World w = new World(66, 66); Simulation s = new Simulation(w, 42, mode, false);
            for (int i = 0; i < 4; i++) s.step();
            w.set(63, 63, Material.SAND); s.step(); assertEquals(Material.SAND, w.get(63, 64));
            w.clear(); w.set(63, 63, Material.WATER); w.set(63, 64, Material.STONE); w.set(62, 64, Material.STONE);
            s.step(); assertEquals(Material.WATER, w.get(64, 64));
            w.clear(); w.set(63, 65, Material.WATER); w.set(62, 65, Material.STONE);
            s.step(); assertEquals(Material.WATER, w.get(64, 65));
        }
    }

    @Test void seededSimulationRepeatableAcrossManyChunks() {
        for (Simulation.Mode mode : new Simulation.Mode[]{Simulation.Mode.FULL_SCAN, Simulation.Mode.ACTIVE_REGIONS}) {
            World a = new World(130, 66), b = new World(130, 66); Random random = new Random(31);
            for (int y = 0; y < 66; y++) for (int x = 0; x < 130; x++) {
                Material m = Material.fromId((byte) random.nextInt(7)); a.set(x, y, m); b.set(x, y, m);
            }
            Simulation sa = new Simulation(a, 47, mode, false), sb = new Simulation(b, 47, mode, false);
            for (int tick = 0; tick < 80; tick++) {
                sa.step(); sb.step();
                for (int y = 0; y < 66; y++) for (int x = 0; x < 130; x++) assertEquals(a.get(x, y), b.get(x, y));
            }
        }
    }

    @Test void fullScanMatchesPreChunkStorageReference() {
        World world = new World(130, 66); Random random = new Random(31);
        for (int y = 0; y < 66; y++) for (int x = 0; x < 130; x++)
            world.set(x, y, Material.fromId((byte) random.nextInt(7)));
        Simulation simulation = new Simulation(world, 47);
        for (int tick = 0; tick < 80; tick++) simulation.step();
        long hash = 0xcbf29ce484222325L;
        for (int y = 0; y < 66; y++) for (int x = 0; x < 130; x++) {
            hash ^= world.get(x, y).id(); hash *= 0x100000001b3L;
        }
        // Generated independently using the pre-M3.2 flat World implementation.
        assertEquals(0x616e60ae6b94de46L, hash);
    }
}
