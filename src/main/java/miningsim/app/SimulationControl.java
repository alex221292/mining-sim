package miningsim.app;

import miningsim.simulation.Simulation;

public final class SimulationControl {
    private final Runnable simulation;
    private final TickMetrics metrics = new TickMetrics(120);
    private boolean paused;
    public SimulationControl(Simulation simulation) { this(simulation::step); }
    public SimulationControl(Runnable simulation) { this.simulation = java.util.Objects.requireNonNull(simulation); }
    public boolean paused() { return paused; }
    public void togglePause() { paused = !paused; }
    public TickMetrics metrics() { return metrics; }
    private void step() {
        long start = System.nanoTime();
        simulation.run();
        metrics.record(System.nanoTime() - start);
    }
    public boolean tick() { if (paused) return false; step(); return true; }
    public boolean singleStep() { if (!paused) return false; step(); return true; }
}
