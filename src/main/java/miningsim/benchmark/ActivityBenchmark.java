package miningsim.benchmark;

import java.util.Arrays;
import java.util.Locale;
import java.util.Random;
import miningsim.simulation.Simulation;
import miningsim.world.Material;
import miningsim.world.World;

/** Exploratory paired scenarios. Timing and instrumentation use separate runs. */
public final class ActivityBenchmark {
    private static final long SEED = 12345;
    private static final int WARM = 50, BATCHES = 5, TICKS = 40;
    private enum Scenario { EMPTY, STATIC, ACTIVE, SETTLED, LOCAL }

    public static void main(String[] args) {
        Locale.setDefault(Locale.ROOT);
        System.out.printf("Java %s %s; %s %s %s; processors=%d; seed=%d; warm=%d batches=%d ticks=%d%n",
                System.getProperty("java.version"), System.getProperty("java.vendor"), System.getProperty("os.name"),
                System.getProperty("os.version"), System.getProperty("os.arch"), Runtime.getRuntime().availableProcessors(), SEED, WARM, BATCHES, TICKS);
        boolean diagnostic = args.length > 0 && args[0].equals("diagnose");
        Simulation.Mode[] modes = diagnostic
                ? new Simulation.Mode[]{Simulation.Mode.FULL_SCAN, Simulation.Mode.DIAGNOSTIC_FULL_TRACKED, Simulation.Mode.DIAGNOSTIC_TILED_TRACKED, Simulation.Mode.ACTIVE_REGIONS}
                : new Simulation.Mode[]{Simulation.Mode.FULL_SCAN, Simulation.Mode.ACTIVE_REGIONS};
        for (int[] size : new int[][] {{400,300}, {800,600}, {1600,900}})
            for (Scenario scenario : diagnostic ? new Scenario[]{Scenario.ACTIVE} : Scenario.values()) for (Simulation.Mode mode : modes)
                run(size[0], size[1], scenario, mode);
    }

    private static Simulation create(int width, int height, Scenario scenario, Simulation.Mode mode, boolean metrics) {
        World w = new World(width, height); Random random = new Random(SEED);
        if (scenario == Scenario.STATIC || scenario == Scenario.ACTIVE) {
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                int value = random.nextInt(100);
                Material material = scenario == Scenario.STATIC
                        ? (value < 70 ? Material.STONE : value < 80 ? Material.WOOD : Material.AIR)
                        : (value < 30 ? Material.SAND : value < 60 ? Material.WATER : Material.AIR);
                if (material != Material.AIR) w.set(x, y, material);
            }
        } else if (scenario == Scenario.SETTLED) {
            for (int x = 0; x < width; x++) {
                w.set(x, height - 1, Material.STONE);
                for (int y = height - 13; y < height - 1; y++) w.set(x, y, Material.WATER);
            }
        } else if (scenario == Scenario.LOCAL) {
            for (int y = 10; y < 30; y++) for (int x = 10; x < 30; x++)
                w.set(x, y, (x + y) % 2 == 0 ? Material.WATER : Material.SAND);
        }
        return new Simulation(w, SEED, mode, metrics);
    }

    private static void run(int width, int height, Scenario scenario, Simulation.Mode mode) {
        Simulation warm = create(width, height, scenario, mode, false);
        for (int i = 0; i < WARM; i++) warm.step();
        long[] samples = new long[BATCHES * TICKS]; int n = 0; long total = 0;
        for (int b = 0; b < BATCHES; b++) {
            Simulation sim = create(width, height, scenario, mode, false);
            for (int i = 0; i < TICKS; i++) {
                long start = System.nanoTime(); sim.step(); long elapsed = System.nanoTime() - start;
                samples[n++] = elapsed; total += elapsed;
            }
        }
        long active = 0, cells = 0, moves = 0, collect = 0, reset = 0, tick = 0;
        Simulation measured = create(width, height, scenario, mode, true);
        int tiles = measured.totalTiles();
        for (int i = 0; i < TICKS; i++) {
            measured.step(); active += measured.activeTiles(); cells += measured.visitedCells(); moves += measured.movements();
            collect += measured.collectNanos(); reset += measured.resetNanos(); tick += measured.tickNanos();
        }
        Arrays.sort(samples);
        double avg = total / (double) n / 1e6;
        double median = (samples[(n - 1) / 2] + samples[n / 2]) / 2.0 / 1e6;
        double p95 = samples[(int) Math.ceil(n * .95) - 1] / 1e6;
        System.out.printf("%dx%d %-7s %-14s avg=%8.3f median=%8.3f p95=%8.3f active=%6.1f/%d (%5.2f%%) cells=%9.0f (%5.2f%%) moves/tick=%8.0f collect=%7.3fms reset=%7.3fms observed=%7.3fms%n",
                width, height, scenario, mode, avg, median, p95, active / (double) TICKS, tiles, 100.0 * active / TICKS / tiles,
                cells / (double) TICKS, 100.0 * cells / TICKS / ((long) width * height), moves / (double) TICKS,
                collect / (double) TICKS / 1e6, reset / (double) TICKS / 1e6, tick / (double) TICKS / 1e6);
    }
}
