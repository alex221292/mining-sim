package miningsim.physics;

import java.util.Objects;
import miningsim.structural.RockConnectivity;
import miningsim.structural.SettledRockRegistry;
import miningsim.world.World;

/** Owner-thread headless quiet-window observation; must be called after every attempted coupled tick. */
public final class SettlingEligibility {
    public enum Status { ELIGIBLE, QUIET_WINDOW, NOT_SLEEPING, NOT_SUPPORTED, SUPPORT_UNAVAILABLE, FAILED_TICK }
    private final int quietTicks,maxSupportCells;
    private long bodyId=-1,lastTick=-1,lastAttempt=-1,terrainRevision=-1,topologyRevision=-1,settledRevision=-1;
    private double anchorX,anchorY,anchorAngle;
    private RigidBody.State lastState;
    private int quiet;
    public SettlingEligibility() { this(120,100_000); }
    public SettlingEligibility(int quietTicks,int maxSupportCells) {
        if(quietTicks<1 || maxSupportCells<1) throw new IllegalArgumentException("Invalid eligibility limits");
        this.quietTicks=quietTicks; this.maxSupportCells=maxSupportCells;
    }
    public int observedQuietTicks() { return quiet; }
    public Status observe(RigidBody body,RigidBodySystem physics,CoupledSimulationStep coupled,World world,
                          RockConnectivity topology,SettledRockRegistry settled) {
        Objects.requireNonNull(body); Objects.requireNonNull(physics); Objects.requireNonNull(coupled); Objects.requireNonNull(world);
        Objects.requireNonNull(topology); Objects.requireNonNull(settled);
        if(!coupled.owns(world,physics) || !settled.owns(world,topology)) throw new IllegalArgumentException("Eligibility owners differ");
        long tick=coupled.diagnostics().ticks(),attempt=coupled.attempts();
        if(physics.body(body.id())!=body || coupled.lastFailure()!=null || !coupled.diagnostics().physicsSucceeded() || tick==0) {
            quiet=0; lastTick=tick; lastAttempt=attempt; return Status.FAILED_TICK;
        }
        if(!body.sleeping() || Math.hypot(body.velocityXMetresPerSecond(),body.velocityYMetresPerSecond())>=0.02
                || Math.abs(body.angularVelocityRadiansPerSecond())>=0.02) {
            quiet=0; lastTick=tick; lastAttempt=attempt; return Status.NOT_SLEEPING;
        }
        var support=SettlingSupport.check(body,world,topology,settled,maxSupportCells);
        if(support.status()!=SettlingSupport.Status.SUPPORTED) {
            quiet=0; lastTick=tick; lastAttempt=attempt;
            return support.status()==SettlingSupport.Status.NOT_SUPPORTED?Status.NOT_SUPPORTED:Status.SUPPORT_UNAVAILABLE;
        }
        long terrain=world.rigidTerrainRevision(),revision=topology.revision(),settledGeneration=settled.revision();
        boolean continuous=bodyId==body.id() && lastTick>=0 && tick==lastTick+1 && attempt==lastAttempt+1 && terrain==terrainRevision
                && revision==topologyRevision && settledGeneration==settledRevision
                && Math.hypot(body.xCells()-anchorX,body.yCells()-anchorY)<0.05
                && Math.abs(body.angleRadians()-anchorAngle)<0.002;
        if(!continuous) { quiet=0; anchorX=body.xCells(); anchorY=body.yCells(); anchorAngle=body.angleRadians(); }
        quiet=Math.min(quietTicks,quiet+1); bodyId=body.id(); lastTick=tick; lastAttempt=attempt;
        lastState=body.state();
        terrainRevision=terrain; topologyRevision=revision; settledRevision=settledGeneration;
        return quiet>=quietTicks?Status.ELIGIBLE:Status.QUIET_WINDOW;
    }
    boolean validFor(RigidBody body,RigidBodySystem physics,CoupledSimulationStep coupled,World world,
                     RockConnectivity topology,SettledRockRegistry settled) {
        if(quiet<quietTicks || !coupled.owns(world,physics) || !settled.owns(world,topology) || physics.body(bodyId)!=body
                || coupled.lastFailure()!=null || coupled.diagnostics().ticks()!=lastTick || coupled.attempts()!=lastAttempt
                || !body.state().equals(lastState) || world.rigidTerrainRevision()!=terrainRevision
                || topology.pending() || topology.revision()!=topologyRevision || settled.revision()!=settledRevision) return false;
        return SettlingSupport.check(body,world,topology,settled,maxSupportCells).status()==SettlingSupport.Status.SUPPORTED;
    }
    boolean placementSupported(java.util.List<SettledRockPlacementPlanner.Cell> cells,World world,RockConnectivity topology,SettledRockRegistry settled) {
        return SettlingSupport.checkPlacement(cells,world,topology,settled,maxSupportCells).status()==SettlingSupport.Status.SUPPORTED;
    }
}
