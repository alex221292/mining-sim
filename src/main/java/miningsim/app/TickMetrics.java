package miningsim.app;

/** Bounded, single-threaded timing window; values are recorded outside physics. */
public final class TickMetrics {
    private final long[] samples;
    private int next, count;
    private long sum;

    public TickMetrics(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        samples = new long[capacity];
    }

    public void record(long nanos) {
        if (nanos < 0) throw new IllegalArgumentException("duration must not be negative");
        sum -= samples[next];
        samples[next] = nanos;
        sum += nanos;
        next = (next + 1) % samples.length;
        if (count < samples.length) count++;
    }

    public double lastMillis() { return count == 0 ? 0 : samples[(next + samples.length - 1) % samples.length] / 1_000_000.0; }
    public double averageMillis() { return count == 0 ? 0 : sum / (double) count / 1_000_000.0; }
    public double maxMillis() {
        long max = 0;
        for (int i = 0; i < count; i++) max = Math.max(max, samples[i]);
        return max / 1_000_000.0;
    }
}
