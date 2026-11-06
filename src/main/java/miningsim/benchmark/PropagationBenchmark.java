package miningsim.benchmark;

import java.util.Arrays;
import java.util.Locale;
import java.util.Random;
import miningsim.simulation.Simulation;
import miningsim.world.Material;
import miningsim.world.World;

/** Exploratory scenarios; initialization and instrumentation are outside timing. */
public final class PropagationBenchmark {
    private static final int WIDTH = 800, HEIGHT = 600, WARMUP = 30, BATCHES = 5, TICKS = 40;
    private static final long SEED = 12345;
    private enum Scenario { LOCAL, DISTANT, DENSE, SETTLED, CROSSING }
    private record Fixture(World world, Simulation simulation) { }

    public static void main(String[] args) {
        Locale.setDefault(Locale.ROOT);
        System.out.printf("Java %s %s; %s %s %s; cpus=%d; %dx%d seed=%d warmup=%d batches=%d ticks=%d%n",
                System.getProperty("java.version"), System.getProperty("java.vendor"), System.getProperty("os.name"),
                System.getProperty("os.version"), System.getProperty("os.arch"), Runtime.getRuntime().availableProcessors(),
                WIDTH, HEIGHT, SEED, WARMUP, BATCHES, TICKS);
        for (Scenario scenario : Scenario.values()) for (Simulation.Mode mode : new Simulation.Mode[]{Simulation.Mode.FULL_SCAN, Simulation.Mode.ACTIVE_REGIONS})
            run(scenario, mode);
        if (args.length > 0 && args[0].equals("trace")) trace();
    }

    private static Fixture create(Scenario scenario, Simulation.Mode mode, boolean instrument) {
        World w = new World(WIDTH, HEIGHT);
        if (scenario == Scenario.DENSE) {
            Random random = new Random(SEED);
            for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
                int value = random.nextInt(100);
                if (value < 30) w.set(x, y, Material.SAND); else if (value < 60) w.set(x, y, Material.WATER);
            }
        } else if (scenario == Scenario.SETTLED) {
            for (int x = 0; x < WIDTH; x++) {
                w.set(x, HEIGHT - 1, Material.STONE);
                for (int y = HEIGHT - 13; y < HEIGHT - 1; y++) w.set(x, y, Material.WATER);
            }
        } else if (scenario == Scenario.CROSSING) {
            for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) w.set(x, y, Material.STONE);
            for (int i = 0; i < 130; i++) w.set(i, i, Material.AIR);
        }
        Simulation sim = new Simulation(w, SEED, mode, instrument);
        if (scenario != Scenario.DENSE) for (int i = 0; i < 4; i++) sim.step();
        if (scenario == Scenario.LOCAL) w.set(63, 63, Material.SAND);
        if (scenario == Scenario.DISTANT) for (int x : new int[]{31, 256, 512, 767}) w.set(x, 63, Material.SAND);
        if (scenario == Scenario.CROSSING) w.set(58, 58, Material.WATER);
        return new Fixture(w, sim);
    }

    private static void run(Scenario scenario, Simulation.Mode mode) {
        Simulation warm = create(scenario, mode, false).simulation();
        for (int i = 0; i < WARMUP; i++) warm.step();
        long[] samples = new long[BATCHES * TICKS]; long total = 0; int n = 0;
        for (int batch = 0; batch < BATCHES; batch++) {
            Simulation sim = create(scenario, mode, false).simulation();
            for (int i = 0; i < TICKS; i++) {
                long start = System.nanoTime(); sim.step(); long elapsed = System.nanoTime() - start;
                samples[n++] = elapsed; total += elapsed;
            }
        }
        Simulation metrics = create(scenario, mode, true).simulation();
        long tiles = 0, cells = 0, moves = 0;
        for (int i = 0; i < TICKS; i++) { metrics.step(); tiles += metrics.activeTiles(); cells += metrics.visitedCells(); moves += metrics.movements(); }
        Arrays.sort(samples);
        System.out.printf("%-8s %-14s mean=%.3f median=%.3f p95=%.3f ms tiles=%.1f/%d cells=%.0f moves=%.1f%n",
                scenario, mode, total / (double) n / 1e6, (samples[99] + samples[100]) / 2.0 / 1e6, samples[189] / 1e6,
                tiles / (double) TICKS, metrics.totalTiles(), cells / (double) TICKS, moves / (double) TICKS);
    }

    private static void trace() {
        Fixture fixture = create(Scenario.LOCAL, Simulation.Mode.ACTIVE_REGIONS, true);
        Simulation sim = fixture.simulation();
        for (int tick = 1; tick <= 545; tick++) {
            sim.step();
            if (tick <= 3 || tick == 32 || tick == 64 || tick == 536 || tick == 540 || tick == 545)
                System.out.printf("trace tick=%d tiles=%d cells=%d moves=%d%n", tick, sim.activeTiles(), sim.visitedCells(), sim.movements());
        }
        fixture.world().set(64, 0, Material.SAND); sim.step();
        System.out.printf("trace disturbance tiles=%d cells=%d moves=%d%n", sim.activeTiles(), sim.visitedCells(), sim.movements());
    }
}
