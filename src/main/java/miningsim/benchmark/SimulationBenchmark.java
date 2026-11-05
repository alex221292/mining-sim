package miningsim.benchmark;

import java.util.Arrays;
import java.util.Locale;
import java.util.Random;
import miningsim.simulation.Simulation;
import miningsim.world.Material;
import miningsim.world.World;

/** Exploratory wall-clock measurements, not a substitute for a microbenchmark harness. */
public final class SimulationBenchmark {
    private static final long SEED = 12345L;
    private static final int WARMUP = 50, BATCHES = 5, TICKS = 40;
    private enum Scenario { EMPTY, STATIC, ACTIVE }

    public static void main(String[] args) {
        Locale.setDefault(Locale.ROOT);
        System.out.printf("Java %s (%s); VM %s; OS %s %s %s; processors %d%n",
                System.getProperty("java.version"), System.getProperty("java.vendor"), System.getProperty("java.vm.name"),
                System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch"),
                Runtime.getRuntime().availableProcessors());
        System.out.printf("seed=%d warmup=%d batches=%d ticks/batch=%d; reset outside timing%n", SEED, WARMUP, BATCHES, TICKS);
        int[][] sizes = {{400, 300}, {800, 600}, {1600, 900}};
        for (int[] size : sizes) for (Scenario scenario : Scenario.values()) run(size[0], size[1], scenario);
    }

    private static Simulation create(int width, int height, Scenario scenario) {
        World world = new World(width, height);
        Random random = new Random(SEED);
        if (scenario != Scenario.EMPTY) {
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                int value = random.nextInt(100);
                Material material = scenario == Scenario.STATIC
                        ? (value < 70 ? Material.STONE : value < 80 ? Material.WOOD : Material.AIR)
                        : (value < 30 ? Material.SAND : value < 60 ? Material.WATER : Material.AIR);
                if (material != Material.AIR) world.set(x, y, material);
            }
        }
        return new Simulation(world, SEED);
    }

    private static void run(int width, int height, Scenario scenario) {
        Simulation warm = create(width, height, scenario);
        for (int i = 0; i < WARMUP; i++) warm.step();
        long[] durations = new long[BATCHES * TICKS];
        int index = 0;
        long total = 0;
        for (int batch = 0; batch < BATCHES; batch++) {
            Simulation simulation = create(width, height, scenario);
            for (int i = 0; i < TICKS; i++) {
                long start = System.nanoTime();
                simulation.step();
                long elapsed = System.nanoTime() - start;
                durations[index++] = elapsed;
                total += elapsed;
            }
        }
        Arrays.sort(durations);
        double avg = total / (double) durations.length / 1_000_000.0;
        double median = (durations[(durations.length - 1) / 2] + durations[durations.length / 2]) / 2.0 / 1_000_000.0;
        double p95 = durations[(int) Math.ceil(durations.length * 0.95) - 1] / 1_000_000.0;
        System.out.printf("%4dx%-4d %-6s avg=%8.3f ms median=%8.3f ms p95=%8.3f ms approx-tps=%8.1f%n",
                width, height, scenario, avg, median, p95, 1000.0 / avg);
    }
}
