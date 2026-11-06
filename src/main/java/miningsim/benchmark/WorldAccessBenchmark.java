package miningsim.benchmark;

import java.util.Arrays;
import java.util.Locale;
import miningsim.world.Material;
import miningsim.world.World;

/** Exploratory whole-world scans; setup is outside timing. Not a JMH benchmark. */
public final class WorldAccessBenchmark {
    private static final int WIDTH = 1600, HEIGHT = 900, WARMUP = 12, SAMPLES = 20;
    private static volatile long sink;
    private enum Pattern { READ, WRITE, NEIGHBORS }

    public static void main(String[] args) {
        Locale.setDefault(Locale.ROOT);
        System.out.printf("Java %s %s; OS %s %s; dimensions=%dx%d warmup=%d samples=%d%n",
                System.getProperty("java.version"), System.getProperty("java.vendor"),
                System.getProperty("os.name"), System.getProperty("os.arch"), WIDTH, HEIGHT, WARMUP, SAMPLES);
        for (Pattern pattern : Pattern.values()) run(pattern);
        for (boolean chunked : new boolean[]{false, true}) for (boolean checked : new boolean[]{false, true}) runRaw(chunked, checked);
    }

    private static void run(Pattern pattern) {
        World world = new World(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++)
            world.set(x, y, ((x + y) & 1) == 0 ? Material.STONE : Material.WATER);
        for (int i = 0; i < WARMUP; i++) sweep(world, pattern, i);
        long[] samples = new long[SAMPLES]; long total = 0;
        for (int i = 0; i < SAMPLES; i++) {
            long start = System.nanoTime(); long checksum = sweep(world, pattern, WARMUP + i);
            long elapsed = System.nanoTime() - start;
            // Observe all written cells outside timing, not just the write-loop expression.
            sink = pattern == Pattern.WRITE ? checksum + sweep(world, Pattern.READ, 0) : checksum;
            samples[i] = elapsed; total += elapsed;
        }
        Arrays.sort(samples);
        System.out.printf("%-9s mean=%8.3f median=%8.3f p95=%8.3f ms/sweep checksum=%d%n", pattern,
                total / (double) SAMPLES / 1e6, (samples[9] + samples[10]) / 2.0 / 1e6, samples[18] / 1e6, sink);
    }

    private static long sweep(World world, Pattern pattern, int iteration) {
        long sum = 0;
        for (int y = 1; y < HEIGHT - 1; y++) for (int x = 1; x < WIDTH - 1; x++) {
            if (pattern == Pattern.WRITE) {
                // Alternating complete sweeps guarantee each write changes the cell.
                Material material = ((x + y + iteration) & 1) == 0 ? Material.STONE : Material.WATER;
                world.set(x, y, material);
                sum += material.id();
            } else {
                sum += world.get(x, y).id();
                if (pattern == Pattern.NEIGHBORS) {
                    sum += world.get(x - 1, y).id() + world.get(x + 1, y).id()
                            + world.get(x, y - 1).id() + world.get(x, y + 1).id();
                }
            }
        }
        return sum;
    }

    // Synthetic byte-array controls isolate some layout/check costs, not the World API.
    private static void runRaw(boolean chunked, boolean checked) {
        byte[] flat = new byte[WIDTH * HEIGHT];
        int columns = (WIDTH + 63) / 64, rows = (HEIGHT + 63) / 64;
        byte[][] chunks = new byte[columns * rows][4096];
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            byte id = (byte) (((x + y) & 1) == 0 ? 1 : 3);
            flat[y * WIDTH + x] = id;
            chunks[(y >>> 6) * columns + (x >>> 6)][((y & 63) << 6) | (x & 63)] = id;
        }
        for (int i = 0; i < WARMUP; i++) rawSweep(flat, chunks, columns, chunked, checked);
        long[] samples = new long[SAMPLES]; long total = 0;
        for (int i = 0; i < SAMPLES; i++) {
            long start = System.nanoTime(); long sum = rawSweep(flat, chunks, columns, chunked, checked);
            long elapsed = System.nanoTime() - start; sink = sum; samples[i] = elapsed; total += elapsed;
        }
        Arrays.sort(samples);
        System.out.printf("RAW_%s_%s mean=%8.3f median=%8.3f p95=%8.3f ms/sweep checksum=%d%n",
                chunked ? "CHUNK" : "FLAT", checked ? "CHECKED" : "UNCHECKED", total / (double) SAMPLES / 1e6,
                (samples[9] + samples[10]) / 2.0 / 1e6, samples[18] / 1e6, sink);
    }
    private static long rawSweep(byte[] flat, byte[][] chunks, int columns, boolean chunked, boolean checked) {
        long sum = 0;
        for (int y = 1; y < HEIGHT - 1; y++) for (int x = 1; x < WIDTH - 1; x++) {
            if (checked && (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT)) throw new IndexOutOfBoundsException();
            sum += chunked ? chunks[(y >>> 6) * columns + (x >>> 6)][((y & 63) << 6) | (x & 63)] : flat[y * WIDTH + x];
        }
        return sum;
    }
}
