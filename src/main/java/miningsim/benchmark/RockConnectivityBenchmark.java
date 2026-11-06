package miningsim.benchmark;

import miningsim.structural.RockConnectivity;
import miningsim.world.Material;
import miningsim.world.World;

/** Exploratory single-run reference cost, not a microbenchmark. */
public final class RockConnectivityBenchmark {
    private static final int BUDGET = 10_000;
    private static void measure(String name, RockConnectivity c) {
        long work = c.totalWork(), start = System.nanoTime(); int calls = 0;
        while (c.pending()) { c.advance(BUDGET); calls++; }
        System.out.printf("%s: units=%d calls=%d elapsed=%.3f ms%n", name, c.totalWork() - work, calls, (System.nanoTime() - start) / 1e6);
    }
    public static void main(String[] args) {
        int width = 800, height = 600;
        World w = new World(width, height);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) w.set(x, y, Material.STONE);
        RockConnectivity c = new RockConnectivity(w);
        System.out.printf("world=%dx%d stone=%d budget=%d%n", width, height, w.occupiedCount(), BUDGET);
        measure("initial", c);
        w.set(400, 300, Material.AIR); measure("one stone removal", c);
        for (int i = 0; i < 1000; i++) w.set(400, 300, (i & 1) == 0 ? Material.STONE : Material.AIR);
        measure("1000 concentrated edits", c);
        w.set(400, 300, Material.WATER); measure("non-structural change", c);
        long start = System.nanoTime(); for (int i = 0; i < 100_000; i++) c.advance(BUDGET);
        System.out.printf("100000 idle advances: units=0 calls=100000 elapsed=%.3f ms%n", (System.nanoTime() - start) / 1e6);
    }
}
