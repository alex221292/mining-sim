package miningsim.app;

import miningsim.simulation.Simulation;

public final class SimulationControl {
    private final Simulation simulation;
    private boolean paused;
    public SimulationControl(Simulation simulation) { this.simulation = simulation; }
    public boolean paused() { return paused; }
    public void togglePause() { paused = !paused; }
    public boolean tick() { if (paused) return false; simulation.step(); return true; }
    public boolean singleStep() { if (!paused) return false; simulation.step(); return true; }
}
