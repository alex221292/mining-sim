package miningsim.physics;

import java.util.Objects;
import miningsim.simulation.CellularDisplacementPlanner;
import miningsim.simulation.Simulation;
import miningsim.world.Material;
import miningsim.world.World;
import java.util.TreeMap;
import java.util.ArrayList;

/** Single-owner physics, displacement and occupancy transaction. Cellular movement is separate. */
public final class CoupledSimulationStep {
    public enum CellularOutcome { NOT_RUN, COMPLETED, FAILED }
    public enum FailureStage { PHYSICS, OCCUPANCY, SWEEP, PLANNING, WORLD_COMMIT, CELLULAR }
    public record Diagnostics(long ticks,int sand,int gravel,int soil,int water,long distance,
                              int maxDistance,long candidates,long checks,long occupancyRevision,
                              long sweepCandidates,long sweepPoses,boolean physicsSucceeded,CellularOutcome cellularOutcome) { }
    public record PhaseTimes(long physicsNanos,long occupancyNanos,long planningNanos,long commitNanos,long cellularNanos) { }
    private final World world;
    private final RigidBodySystem physics;
    private final RigidBodyOccupancyIndex occupancy;
    private final CellularDisplacementPlanner.Limits limits;
    private final boolean instrument;
    private long physicsNanos,occupancyNanos,planningNanos,commitNanos,cellularNanos;
    private Diagnostics diagnostics=new Diagnostics(0,0,0,0,0,0,0,0,0,0,0,0,false,CellularOutcome.NOT_RUN);
    private String lastFailure;
    private FailureStage lastFailureStage;
    private RigidBodyOccupancyIndex.Snapshot committedSnapshot;
    private record Pose(double x,double y,double angle) { }
    private record SweepWork(long candidates,long poses) { }
    private static final class Trace {
        final RigidBody body;
        final ArrayList<Pose> poses=new ArrayList<>();
        Trace(RigidBody body) { this.body=body; }
        void include(RigidBody body) { poses.add(new Pose(body.xCells(),body.yCells(),body.angleRadians())); }
    }

    public CoupledSimulationStep(World world,RigidBodySystem physics,RigidBodyOccupancyIndex occupancy,CellularDisplacementPlanner.Limits limits) {
        this(world,physics,occupancy,limits,false);
    }
    public CoupledSimulationStep(World world,RigidBodySystem physics,RigidBodyOccupancyIndex occupancy,CellularDisplacementPlanner.Limits limits,boolean instrument) {
        this.world=Objects.requireNonNull(world); this.physics=Objects.requireNonNull(physics);
        this.occupancy=Objects.requireNonNull(occupancy); this.limits=Objects.requireNonNull(limits);
        this.instrument=instrument;
        if(!physics.coupledTo(world)) throw new IllegalArgumentException("Physics must use coupled static policy and the same world");
        if(occupancy.snapshot().width()!=world.width() || occupancy.snapshot().height()!=world.height())
            throw new IllegalArgumentException("Occupancy dimensions do not match world");
    }
    public Diagnostics diagnostics() { return diagnostics; }
    public String lastFailure() { return lastFailure; }
    public FailureStage lastFailureStage() { return lastFailureStage; }
    public PhaseTimes phaseTimes() { return new PhaseTimes(physicsNanos,occupancyNanos,planningNanos,commitNanos,cellularNanos); }
    public void step() {
        var checkpoint=physics.coupledCheckpoint(); long worldRevision=world.mutationRevision();
        var traces=new TreeMap<Long,Trace>();
        FailureStage stage=FailureStage.PHYSICS;
        try {
            long nextTick=Math.incrementExact(diagnostics.ticks());
            long start=instrument?System.nanoTime():0;
            physics.setCoupledTrace(body -> traces.computeIfAbsent(body.id(),id->new Trace(body)).include(body));
            try { physics.step(); } finally { physics.setCoupledTrace(null); }
            long physicsTime=instrument?System.nanoTime()-start:0; start=instrument?System.nanoTime():0;
            stage=FailureStage.OCCUPANCY;
            var prepared=occupancy.prepare(physics,nextTick);
            long occupancyTime=instrument?System.nanoTime()-start:0; start=instrument?System.nanoTime():0;
            stage=FailureStage.SWEEP;
            var sweep=rejectUnresolvedSweep(traces,prepared.snapshot());
            stage=FailureStage.PLANNING;
            var plan=CellularDisplacementPlanner.plan(world,prepared.snapshot(),limits);
            int sand=0,gravel=0,soil=0,water=0;
            for(var move:plan.moves()) switch(move.material()) {
                case SAND -> sand++; case GRAVEL -> gravel++; case SOIL -> soil++; case WATER -> water++;
                default -> throw new IllegalStateException("Non-displaceable plan material");
            }
            var next=new Diagnostics(nextTick,sand,gravel,soil,water,plan.totalDistance(),plan.maxDistance(),
                    plan.candidates(),plan.reachabilityChecks(),prepared.snapshot().diagnostics().revision(),sweep.candidates(),sweep.poses(),true,CellularOutcome.NOT_RUN);
            long planningTime=instrument?System.nanoTime()-start:0; start=instrument?System.nanoTime():0;
            stage=FailureStage.WORLD_COMMIT;
            var result=world.replaceAllAndPublishOccupancy(plan.changes(),prepared);
            if(result==World.BatchResult.REJECTED) throw new IllegalStateException("Displacement batch rejected");
            long commitTime=instrument?System.nanoTime()-start:0;
            committedSnapshot=prepared.snapshot(); diagnostics=next; lastFailure=null; lastFailureStage=null;
            physicsNanos=physicsTime; occupancyNanos=occupancyTime; planningNanos=planningTime; commitNanos=commitTime; cellularNanos=0;
        } catch(RuntimeException ex) {
            // Throwing callbacks violate World.ChangeSink's contract; world writes cannot be rolled back here.
            if(world.mutationRevision()==worldRevision) checkpoint.restore();
            lastFailure=ex.getClass().getSimpleName()+": "+ex.getMessage();
            lastFailureStage=stage;
            throw ex;
        }
    }
    /** Intermediate actual cell polygons are tested separately from final occupancy. */
    private SweepWork rejectUnresolvedSweep(TreeMap<Long,Trace> traces,RigidBodyOccupancyIndex.Snapshot target) {
        long work=0, poseWork=0;
        for(var trace:traces.values()) {
            Pose previous=null;
            for(var pose:trace.poses) {
                double travel=previous==null?0:Math.hypot(pose.x()-previous.x(),pose.y()-previous.y())
                        + trace.body.geometry().maxRadiusCells()*Math.abs(pose.angle()-previous.angle());
                double requested=Math.max(1,Math.ceil(travel/0.125));
                if(!Double.isFinite(requested) || requested>Integer.MAX_VALUE || requested>limits.maxReachabilityChecks())
                    throw new IllegalStateException("Swept pose budget exceeded for body "+trace.body.id());
                int samples=(int)requested;
                for(int s=1;s<=samples;s++) {
                    poseWork=Math.incrementExact(poseWork);
                    if(poseWork>limits.maxReachabilityChecks()) throw new IllegalStateException("Swept pose budget exceeded: "+poseWork+" > "+limits.maxReachabilityChecks());
                    double t=previous==null?1:(double)s/samples;
                    double cx=previous==null?pose.x():previous.x()+(pose.x()-previous.x())*t;
                    double cy=previous==null?pose.y():previous.y()+(pose.y()-previous.y())*t;
                    double angle=previous==null?pose.angle():previous.angle()+(pose.angle()-previous.angle())*t;
                    for(var cell:trace.body.fragment().descriptor().cells()) {
                        double[] px=new double[4],py=new double[4]; int[] dx={0,1,1,0},dy={0,0,1,1};
                        double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX;
                        for(int i=0;i<4;i++) {
                            var p=trace.body.geometry().transform(cell.x()+dx[i],cell.y()+dy[i],cx,cy,angle);
                            px[i]=p.x(); py[i]=p.y(); minX=Math.min(minX,p.x()); maxX=Math.max(maxX,p.x()); minY=Math.min(minY,p.y()); maxY=Math.max(maxY,p.y());
                        }
                        int left=(int)Math.max(0,Math.min(world.width(),Math.floor(minX))),right=(int)Math.max(0,Math.min(world.width(),Math.ceil(maxX)));
                        int top=(int)Math.max(0,Math.min(world.height(),Math.floor(minY))),bottom=(int)Math.max(0,Math.min(world.height(),Math.ceil(maxY)));
                        work=Math.addExact(work,Math.multiplyExact((long)(right-left),bottom-top));
                        if(work>limits.maxCandidates()) throw new IllegalStateException("Swept candidate budget exceeded: "+work+" > "+limits.maxCandidates());
                        for(int y=top;y<bottom;y++) for(int x=left;x<right;x++) {
                            var material=world.get(x,y);
                            if(!target.occupied(x,y) && CellularDisplacementPlanner.classify(material)==CellularDisplacementPlanner.Kind.DISPLACEABLE
                                    && RigidBodyOccupancyIndex.intersectionArea(px,py,x,y)>RigidBodyOccupancyIndex.AREA_EPSILON)
                                throw new IllegalStateException("Intermediate swept material obstruction at "+x+","+y+" ("+material+") for body "+trace.body.id());
                        }
                    }
                }
                previous=pose;
            }
        }
        return new SweepWork(work,poseWork);
    }
    /** Separate, non-transactional cellular phase after successful coupled publication. */
    public void stepCells(Simulation simulation) {
        Objects.requireNonNull(simulation);
        if(!simulation.owns(world)) throw new IllegalArgumentException("Simulation belongs to a different world");
        if(diagnostics.ticks()==0 || diagnostics.cellularOutcome()!=CellularOutcome.NOT_RUN) throw new IllegalStateException("Cellular phase unavailable");
        if(occupancy.snapshot()!=committedSnapshot) throw new IllegalStateException("Coupled occupancy changed before cellular phase");
        simulation.setOccupancy(committedSnapshot);
        long start=instrument?System.nanoTime():0;
        try { simulation.step(); cellularNanos=instrument?System.nanoTime()-start:0; updateCellularOutcome(CellularOutcome.COMPLETED); }
        catch(RuntimeException ex) { updateCellularOutcome(CellularOutcome.FAILED); lastFailure=ex.getClass().getSimpleName()+": "+ex.getMessage(); lastFailureStage=FailureStage.CELLULAR; throw ex; }
    }
    private void updateCellularOutcome(CellularOutcome outcome) {
        var d=diagnostics;
        diagnostics=new Diagnostics(d.ticks(),d.sand(),d.gravel(),d.soil(),d.water(),d.distance(),d.maxDistance(),d.candidates(),d.checks(),d.occupancyRevision(),d.sweepCandidates(),d.sweepPoses(),d.physicsSucceeded(),outcome);
    }
}
