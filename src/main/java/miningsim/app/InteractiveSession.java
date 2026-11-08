package miningsim.app;

import java.util.Objects;
import miningsim.fragment.FragmentRegistry;
import miningsim.fragment.ExtractionCommit;
import miningsim.physics.*;
import miningsim.simulation.CellularDisplacementPlanner;
import miningsim.simulation.Simulation;
import miningsim.world.World;

/** One owner thread coordinates a world and its associated simulation systems. */
public final class InteractiveSession {
    private final World world;
    private final StructuralSimulationController structural;
    private final Simulation simulation;
    private final FragmentRegistry registry;
    private final RigidBodySystem physics;
    private final RigidBodyOccupancyIndex occupancy;
    private final CoupledSimulationStep coupled;
    private int failures;
    private String failure;
    private boolean blocked;
    private final java.util.ArrayDeque<Runnable> commands=new java.util.ArrayDeque<>();

    public InteractiveSession(World world, StructuralSimulationController structural) {
        this.world=Objects.requireNonNull(world); this.structural=structural;
        if(structural!=null && structural.world()!=world) throw new IllegalArgumentException("Structural world mismatch");
        simulation=new Simulation(world,12345);
        if(world.mode()==World.Mode.LEGACY) {
            if(structural!=null) throw new IllegalArgumentException("Legacy structural session unsupported");
            registry=null; physics=null; occupancy=null; coupled=null;
        } else {
            registry=structural==null?new FragmentRegistry(1024):structural.registry();
            var query=new StaticCollisionQuery(world,100000,1000000,StaticCollisionQuery.MaterialPolicy.DISPLACE_GRANULAR);
            var solver=new StaticContactSolver(query,StaticContactSolver.Config.defaults());
            physics=new RigidBodySystem(registry,4,solver,RigidBodySystem.MotionConfig.defaults(),
                    new BodyCollisionQuery(new BodyCollisionQuery.Limits(1000000,10000000,4096,4096)),
                    new BodyContactSolver(BodyContactSolver.Config.defaults()));
            occupancy=new RigidBodyOccupancyIndex(world,RigidBodyOccupancyIndex.Limits.defaults());
            coupled=new CoupledSimulationStep(world,physics,occupancy,CellularDisplacementPlanner.Limits.defaults());
        }
    }
    public World world() { return world; }
    public StructuralSimulationController structural() { return structural; }
    public boolean isCoupled() { return coupled!=null; }
    public RigidBodySystem physics() { return physics; }
    public RigidBodyOccupancyIndex occupancy() { return occupancy; }
    public CoupledSimulationStep coupled() { return coupled; }
    public String failure() { return failure; }
    public boolean blocked() { return blocked; }
    public void resume() { blocked=false; failures=0; }
    /** Called by the owner thread; commands are consumed before the next fixed tick. */
    public void enqueue(Runnable command) { commands.addLast(Objects.requireNonNull(command)); }
    public void tick() {
        while(!commands.isEmpty()) commands.removeFirst().run();
        if(structural!=null) structural.tick();
        if(coupled==null) { simulation.step(); return; }
        if(blocked) return;
        try { coupled.step(); }
        catch(IllegalStateException ex) {
            String next=coupled.lastFailureStage()+": "+ex.getMessage();
            failures=next.equals(failure)?failures+1:1; failure=next;
            if(failures>=3) blocked=true;
            return;
        }
        failure=null; failures=0;
        // This phase is intentionally outside the coupled transaction.
        coupled.stepCells(simulation);
    }
    /** F5 uses the already analyzed selected component; the registry remains structural-owned. */
    public ExtractionCommit.Status extractSelected() {
        if(structural==null || coupled==null) return ExtractionCommit.Status.INVALID;
        var result=structural.extractSelected();
        if(result==ExtractionCommit.Status.COMMITTED) {
            physics.synchronize();
            occupancy.rebuild(physics,coupled.diagnostics().ticks());
            resume();
        }
        return result;
    }
}
