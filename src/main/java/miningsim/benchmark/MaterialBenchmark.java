package miningsim.benchmark;

import java.util.Arrays;
import java.util.Locale;
import java.util.Random;
import miningsim.simulation.Simulation;
import miningsim.world.Material;
import miningsim.world.World;

/** Separate scenarios; the historical benchmarks remain unchanged. */
public final class MaterialBenchmark {
    private enum Scenario { MIXED, COLLAPSE, SETTLED }
    private static final long SEED = 12345;

    public static void main(String[] args) {
        Locale.setDefault(Locale.ROOT);
        System.out.printf("Java %s (%s), %s %s %s, processors=%d; seed=%d warmup=50 batches=5 ticks=40%n",
                System.getProperty("java.version"), System.getProperty("java.vendor"), System.getProperty("os.name"),
                System.getProperty("os.version"), System.getProperty("os.arch"), Runtime.getRuntime().availableProcessors(), SEED);
        for (int[] size : new int[][]{{400, 300}, {800, 600}, {1600, 900}})
            for (Scenario scenario : Scenario.values()) run(size[0], size[1], scenario);
    }

    private static Simulation create(int width, int height, Scenario scenario) {
        World w = new World(width, height); Random random = new Random(SEED);
        if (scenario == Scenario.MIXED) {
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                int value = random.nextInt(100);
                Material m = value < 15 ? Material.SAND : value < 30 ? Material.WATER : value < 45 ? Material.GRAVEL
                        : value < 60 ? Material.SOIL : Material.AIR;
                if (m != Material.AIR) w.set(x, y, m);
            }
        } else {
            for (int x = 0; x < width; x++) for (int y = height / 2; y < height; y++) w.set(x, y, Material.STONE);
            for (int x = width / 2 - 10; x < width / 2 + 10; x++)
                for (int y = height / 2 - 20; y < height / 2; y++) w.set(x, y, Material.SOIL);
            if (scenario == Scenario.COLLAPSE)
                for (int x = width / 2 - 10; x < width / 2 + 10; x++)
                    for (int y = height / 2; y < height / 2 + 50; y++) w.set(x, y, Material.AIR);
        }
        return new Simulation(w, SEED);
    }
    private static void run(int width, int height, Scenario scenario) {
        Simulation warm = create(width, height, scenario); for (int i = 0; i < 50; i++) warm.step();
        long[] times = new long[200]; long sum = 0; int n = 0;
        for (int batch = 0; batch < 5; batch++) {
            Simulation sim = create(width, height, scenario);
            for (int i = 0; i < 40; i++) { long start = System.nanoTime(); sim.step(); long t = System.nanoTime() - start; times[n++] = t; sum += t; }
        }
        Arrays.sort(times);
        System.out.printf("%dx%d %-8s avg=%.3f median=%.3f p95=%.3f ms/tick%n", width, height, scenario,
                sum / 200.0 / 1e6, (times[99] + times[100]) / 2.0 / 1e6, times[189] / 1e6);
    }
}
