package miningsim.benchmark;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;
import miningsim.persistence.WorldIO;
import miningsim.world.Material;
import miningsim.world.World;

/** Exploratory filesystem benchmark; no GUI, no simulation ticks. */
public final class PersistenceBenchmark {
    private static final int WARMUP = 3, SAMPLES = 10;
    private static volatile int sink;
    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        System.out.printf("Java %s %s; OS %s %s; cpus=%d; seed=12345 warmup=%d samples=%d; filesystem=%s%n",
                System.getProperty("java.version"), System.getProperty("java.vendor"), System.getProperty("os.name"),
                System.getProperty("os.arch"), Runtime.getRuntime().availableProcessors(), WARMUP, SAMPLES, System.getProperty("java.io.tmpdir"));
        for (int[] size : new int[][]{{400,300},{800,600},{1600,900}}) for (boolean mixed : new boolean[]{false, true})
            run(size[0], size[1], mixed);
    }
    private static void run(int width, int height, boolean mixed) throws Exception {
        World w = new World(width, height); Random random = new Random(12345);
        if (mixed) for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
            w.set(x, y, Material.fromId((byte) random.nextInt(7)));
        Path path = Files.createTempFile("miningsim-benchmark-", ".msim");
        try {
            for (int i = 0; i < WARMUP; i++) { WorldIO.write(w, path); sink = WorldIO.read(path).occupiedCount(); }
            long[] saves = new long[SAMPLES], loads = new long[SAMPLES];
            for (int i = 0; i < SAMPLES; i++) {
                long start = System.nanoTime(); WorldIO.write(w, path); saves[i] = System.nanoTime() - start;
                start = System.nanoTime(); World loaded = WorldIO.read(path); loads[i] = System.nanoTime() - start;
                sink = loaded.occupiedCount();
            }
            System.out.printf("%dx%d %-5s bytes=%d save=%s load=%s ms mean/median/p95%n",
                    width, height, mixed ? "MIXED" : "EMPTY", Files.size(path), stats(saves), stats(loads));
        } finally { Files.deleteIfExists(path); }
    }
    private static String stats(long[] values) {
        long sum = 0; for (long value : values) sum += value;
        Arrays.sort(values);
        return String.format(Locale.ROOT, "%.3f/%.3f/%.3f", sum / (double) values.length / 1e6,
                (values[4] + values[5]) / 2.0 / 1e6, values[9] / 1e6);
    }
}
