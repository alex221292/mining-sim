package miningsim;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayDeque;
import java.util.Random;
import org.junit.jupiter.api.Test;
import miningsim.structural.RockConnectivity;
import miningsim.world.Material;
import miningsim.world.World;

class RockConnectivityTest {
    private static void finish(RockConnectivity c, int budget) { while (c.pending()) assertTrue(c.advance(budget) <= budget); }

    @Test void ringConcavityAndSingleCell() {
        World one = new World(1, 1); one.set(0, 0, Material.STONE);
        RockConnectivity single = new RockConnectivity(one); finish(single, 1);
        assertEquals(1, single.componentCount()); assertEquals(1, single.componentSize(0));
        World bedrock = new World(1, 1, World.Mode.BEDROCK_ENABLED);
        RockConnectivity empty = new RockConnectivity(bedrock); finish(empty, 1); assertEquals(0, empty.componentCount());
        World w = new World(5, 5);
        for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++)
            if (x == 0 || y == 0 || x == 3 || y == 3) w.set(x, y, Material.STONE);
        w.set(4, 3, Material.STONE);
        RockConnectivity c = new RockConnectivity(w); finish(c, 2);
        assertEquals(1, c.componentCount()); assertEquals(13, c.componentSize(0));
        assertEquals(-1, c.componentAt(1, 1));
    }

    @Test void onlyOrthogonalFoundationContactCounts() {
        World w = new World(3, 4, World.Mode.BEDROCK_ENABLED);
        w.set(0, 1, Material.STONE); w.set(1, 2, Material.STONE);
        RockConnectivity c = new RockConnectivity(w); finish(c, 1);
        assertEquals(2, c.componentCount()); assertFalse(c.foundationConnected(0)); assertTrue(c.foundationConnected(1));
        long rev = c.revision(); w.set(0, 3, Material.AIR); w.set(0, 3, Material.BEDROCK);
        assertEquals(rev, c.revision()); assertFalse(c.pending());
    }

    @Test void topologyFoundationBreaksAndReplacement() {
        World w = new World(66, 67, World.Mode.BEDROCK_ENABLED);
        for (int[] p : new int[][]{{63,63},{64,63},{63,64},{64,64},{64,65},{62,62}}) w.set(p[0], p[1], Material.STONE);
        RockConnectivity c = new RockConnectivity(w); finish(c, 1);
        assertEquals(2, c.componentCount()); assertEquals(62 + 62 * 66, c.componentId(0));
        assertFalse(c.foundationConnected(0)); assertTrue(c.foundationConnected(1));
        assertEquals(c.componentAt(63, 63), c.componentAt(64, 65));
        assertEquals(c.bondKey(64, 65, 64, 64), c.bondKey(64, 64, 64, 65));
        assertTrue(c.breakBond(64, 64, 64, 65)); assertFalse(c.breakBond(64, 65, 64, 64));
        assertThrows(IllegalStateException.class, c::componentCount); finish(c, 3);
        assertEquals(3, c.componentCount()); assertNotEquals(c.componentAt(64, 64), c.componentAt(64, 65));
        w.set(64, 65, Material.AIR); w.set(64, 65, Material.STONE); finish(c, 7);
        assertEquals(c.componentAt(64, 64), c.componentAt(64, 65));
        assertThrows(IllegalArgumentException.class, () -> c.breakBond(0, 0, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> c.bondKey(0, 0, 1, 1));
    }

    @Test void editsRestartOnlyWhenRelevantAndClearInvalidates() {
        World w = new World(5, 5); RockConnectivity c = new RockConnectivity(w);
        c.advance(1); w.set(1, 1, Material.STONE); assertEquals(1, c.restarts());
        c.advance(1); w.set(1, 1, Material.AIR); w.set(1, 1, Material.STONE);
        assertTrue(c.pending()); assertThrows(IllegalStateException.class, c::componentCount);
        finish(c, 1); assertEquals(1, c.componentCount()); assertFalse(c.foundationConnected(0));
        long revision = c.revision();
        w.set(0, 0, Material.WATER); w.set(0, 0, Material.SAND); w.set(1, 1, Material.STONE);
        w.set(0, 0, Material.BEDROCK); assertEquals(revision, c.revision()); assertFalse(c.pending());
        assertEquals(0, c.advance(1)); w.clear(); assertTrue(c.pending()); finish(c, 2); assertEquals(0, c.componentCount());
        w.setChangeSink(null); assertThrows(IllegalStateException.class, c::pending);
    }

    @Test void singleDimensionLargeAndEmpty() {
        World w = new World(1, 100_000); RockConnectivity c = new RockConnectivity(w); finish(c, 1000);
        assertEquals(0, c.componentCount());
        for (int y = 0; y < w.height(); y++) w.set(0, y, Material.STONE);
        finish(c, 1000); assertEquals(1, c.componentCount()); assertEquals(100_000, c.componentSize(0));
        assertEquals(0, c.componentAt(0, 99_999)); assertFalse(c.foundationConnected(0));
    }

    @Test void randomizedReferenceAndEditOrder() {
        Random r = new Random(112); int width = 12, height = 10;
        for (int trial = 0; trial < 30; trial++) {
            World a = new World(width, height), b = new World(width, height);
            boolean[] stone = new boolean[width * height];
            for (int i = 0; i < stone.length; i++) stone[i] = r.nextBoolean();
            for (int i = 0; i < stone.length; i++) if (stone[i]) a.set(i % width, i / width, Material.STONE);
            for (int i = stone.length - 1; i >= 0; i--) if (stone[i]) b.set(i % width, i / width, Material.STONE);
            RockConnectivity ca = new RockConnectivity(a), cb = new RockConnectivity(b); finish(ca, 1); finish(cb, 17);
            int[] expected = new int[stone.length]; java.util.Arrays.fill(expected, -1);
            int count = 0;
            for (int i = 0; i < stone.length; i++) if (stone[i] && expected[i] == -1) {
                count++; ArrayDeque<Integer> q = new ArrayDeque<>(); q.add(i); expected[i] = i;
                while (!q.isEmpty()) { int p = q.remove(), x = p % width, y = p / width;
                    for (int[] d : new int[][]{{-1,0},{1,0},{0,-1},{0,1}}) {
                        int nx = x + d[0], ny = y + d[1];
                        if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                        int n = ny * width + nx;
                        if (stone[n] && expected[n] == -1) { expected[n] = i; q.add(n); }
                    }
                }
            }
            assertEquals(count, ca.componentCount()); assertEquals(count, cb.componentCount());
            for (int i = 0; i < stone.length; i++) {
                assertEquals(expected[i], ca.componentAt(i % width, i / width));
                assertEquals(expected[i], cb.componentAt(i % width, i / width));
            }
        }
    }
}
