package miningsim.structural;

import java.util.Objects;
import miningsim.fragment.ExtractionCommit;
import miningsim.fragment.ExtractionPlanner;
import miningsim.fragment.FragmentRegistry;
import miningsim.physics.RigidBodyOccupancyIndex;
import miningsim.physics.RigidBodySystem;
import miningsim.world.World;

/** Bounded, deterministic topology-based detachment; no stress solve or UI dependencies. */
public final class AutomaticRockDetachment {
    public record Limits(int topologyWork,int quietTicks,int componentCells,int componentsPerTick,int cellsPerTick,int componentChecks) {
        public Limits {
            if(topologyWork<1 || quietTicks<0 || componentCells<1 || componentsPerTick<1 || cellsPerTick<1 || componentChecks<1)
                throw new IllegalArgumentException("Invalid automatic detachment limits");
        }
        public static Limits defaults() { return new Limits(8192,6,1024,4,4096,256); }
    }
    public record Diagnostics(boolean pending,long revision,int work,int unsupported,int eligible,int detached,int detachedCells,
                              int deferred,int tooLarge,int rejected,long total,String failure) { }
    private final World world;
    private final RockConnectivity topology;
    private final FragmentRegistry registry;
    private final RigidBodySystem physics;
    private final RigidBodyOccupancyIndex occupancy;
    private final Limits limits;
    private final boolean instrument;
    private long preparationNanos,transactionNanos,commitNanos;
    private long observedAddition,observedDisruption,scannedRevision=-1,scannedRegistry=-1,total;
    private int quiet,cursor,unsupported,eligible,tooLarge,rejected;
    private int occupancyRetryCountdown;
    private long rejectedOccupancyRevision=-1;
    private String failure="";
    private SettledRockRegistry settledSupport;
    private Diagnostics diagnostics=new Diagnostics(true,0,0,0,0,0,0,0,0,0,0,"");
    public AutomaticRockDetachment(World world,RockConnectivity topology,FragmentRegistry registry,RigidBodySystem physics,
                                   RigidBodyOccupancyIndex occupancy,Limits limits) {
        this(world,topology,registry,physics,occupancy,limits,false);
    }
    public AutomaticRockDetachment(World world,RockConnectivity topology,FragmentRegistry registry,RigidBodySystem physics,
                                   RigidBodyOccupancyIndex occupancy,Limits limits,boolean instrument) {
        this.world=Objects.requireNonNull(world); this.topology=Objects.requireNonNull(topology); this.registry=Objects.requireNonNull(registry);
        this.physics=Objects.requireNonNull(physics); this.occupancy=Objects.requireNonNull(occupancy); this.limits=Objects.requireNonNull(limits);
        this.instrument=instrument;
        if(!topology.owns(world) || !occupancy.owns(world)) throw new IllegalArgumentException("Detachment world mismatch");
    }
    public RockConnectivity topology() { return topology; }
    /** Optional lifecycle support owner, installed before ticking. */
    public void attachSettledSupport(SettledRockRegistry settled) {
        if(settledSupport!=null || !settled.owns(world,topology)) throw new IllegalArgumentException("Invalid settled support owner");
        settledSupport=settled;
    }
    private enum Support { SUPPORTED, UNSUPPORTED, DEFERRED }
    private Support settledSupport(int ordinal,long[] remaining) {
        if(settledSupport==null) return Support.UNSUPPORTED;
        var cells=topology.componentCells(ordinal); double sum=0,left=Double.POSITIVE_INFINITY,right=Double.NEGATIVE_INFINITY;
        if(cells.length>remaining[0]) return Support.DEFERRED;
        remaining[0]-=cells.length;
        var roots=new java.util.TreeSet<Long>();
        for(int cell:cells) {
            int x=cell%world.width(),y=cell/world.width(); sum+=x+0.5;
            if(y+1<world.height()) { long id=settledSupport.ownerAt(x,y+1); if(id>0) { roots.add(id); left=Math.min(left,x); right=Math.max(right,x+1.0); } }
        }
        if(roots.isEmpty()) return Support.UNSUPPORTED;
        if(!settledSupport.supportTransitionsAllowed()) return Support.DEFERRED;
        double center=sum/cells.length; if(center<=left+1e-9 || center>=right-1e-9) return Support.UNSUPPORTED;
        var proven=new java.util.HashSet<Long>();
        for(long id:roots) if(!proven.contains(id)) {
            if(remaining[0]<1) return Support.DEFERRED;
            var graph=new SettledSupportGraph(world,topology,settledSupport,java.util.List.of(id),
                    new SettledSupportGraph.Limits(100,10000,(int)remaining[0]));
            var result=graph.step(); remaining[0]-=result.work();
            if(result.status()!=SettledSupportGraph.Status.COMPLETE) return Support.DEFERRED;
            var cert=graph.certificate();
            if(cert.supportedIds().size()!=graph.analyzedIds().size()) return Support.UNSUPPORTED;
            proven.addAll(cert.supportedIds());
        }
        return Support.SUPPORTED;
    }
    public Diagnostics diagnostics() { return diagnostics; }
    public long preparationNanos() { return preparationNanos; }
    /** Includes prebuilt body, occupancy preparation and World transaction. */
    public long transactionNanos() { return transactionNanos; }
    public long commitNanos() { return commitNanos; }
    /** Called after commands; a disruption after an addition cancels the paint grouping delay. */
    public void tick(long generation) {
        tick(generation,0);
    }
    /** Prior work accounts for the optional reference structural controller's topology pass. */
    public void tick(long generation,int priorWork) {
        if(priorWork<0 || priorWork>limits.topologyWork()) throw new IllegalArgumentException("Invalid prior topology work");
        long addition=topology.lastAdditionRevision(),disruption=topology.lastDisruptionRevision();
        if(addition!=observedAddition && addition>disruption) quiet=limits.quietTicks();
        if(disruption!=observedDisruption && disruption>addition) quiet=0;
        observedAddition=addition; observedDisruption=disruption;
        if(occupancyRetryCountdown>0) occupancyRetryCountdown--;
        int remaining=limits.topologyWork()-priorWork;
        int work=priorWork+(topology.pending() && remaining>0?topology.advance(remaining):0);
        int detached=0,cells=0,deferred=0;
        preparationNanos=transactionNanos=commitNanos=0;
        long revision=topology.revision();
        if(!topology.pending()) {
            if(scannedRevision!=revision || scannedRegistry!=registry.revision()) {
                scannedRevision=revision; scannedRegistry=registry.revision(); cursor=unsupported=eligible=tooLarge=rejected=0; failure="";
                rejectedOccupancyRevision=-1; occupancyRetryCountdown=0;
            } else if(rejectedOccupancyRevision>=0 && occupancyRetryCountdown==0
                    && occupancy.snapshot().diagnostics().revision()!=rejectedOccupancyRevision) {
                cursor=unsupported=eligible=tooLarge=rejected=0; failure=""; rejectedOccupancyRevision=-1;
            }
            if(quiet>0) {
                for(int i=0;i<Math.min(topology.componentCount(),limits.componentChecks());i++)
                    if(!topology.foundationConnected(i)) deferred++;
            }
            else {
                int checked=0,attempted=0,plannedCells=0; long[] supportWork={limits.topologyWork()};
                while(cursor<topology.componentCount() && checked++<limits.componentChecks()) {
                    int ordinal=cursor++, id=topology.componentId(ordinal), size=topology.componentSize(ordinal);
                    if(topology.foundationConnected(ordinal)) continue;
                    unsupported++;
                    if(size>limits.componentCells()) { tooLarge++; failure="TOO_LARGE component "+id+" ("+size+" cells)"; continue; }
                    if(settledSupport!=null && settledSupport.ownerAt(id%world.width(),id/world.width())==0) {
                        var support=settledSupport(ordinal,supportWork);
                        if(support==Support.SUPPORTED) { unsupported--; continue; }
                        if(support==Support.DEFERRED) { cursor--; unsupported--; deferred++; failure="Settled support analysis pending or budget exceeded"; break; }
                    }
                    if(size>limits.cellsPerTick()) { rejected++; failure="CELL_BUDGET component "+id+" ("+size+" cells)"; continue; }
                    if(attempted>=limits.componentsPerTick() || size>limits.cellsPerTick()-plannedCells) { cursor--; unsupported--; break; }
                    attempted++; plannedCells+=size;
                    eligible++;
                    long start=instrument?System.nanoTime():0;
                    var plan=ExtractionPlanner.prepare(world,topology,id%world.width(),id/world.width(),limits.componentCells());
                    if(instrument) preparationNanos+=System.nanoTime()-start;
                    if(plan.status()!=ExtractionPlanner.Status.READY) { rejected++; failure=plan.status().name(); continue; }
                    long nextTotal=Math.incrementExact(total);
                    try {
                        start=instrument?System.nanoTime():0;
                        var result=physics.extract(plan.plan(),occupancy,generation,instrument);
                        if(instrument) transactionNanos+=System.nanoTime()-start;
                        if(instrument) commitNanos+=physics.extractionCommitNanos();
                        if(result.status()!=ExtractionCommit.Status.COMMITTED) { rejected++; failure=result.status().name(); continue; }
                    } catch(RigidBodyOccupancyIndex.BudgetException ex) {
                        rejected++; failure=ex.getMessage(); rejectedOccupancyRevision=occupancy.snapshot().diagnostics().revision();
                        occupancyRetryCountdown=60; continue;
                    }
                    detached++; cells+=size; total=nextTotal;
                    // The extraction invalidates topology; never reuse component labels from before it.
                    break;
                }
            }
        }
        if(quiet>0) quiet--;
        diagnostics=new Diagnostics(topology.pending(),topology.revision(),work,unsupported,eligible,detached,cells,deferred,tooLarge,rejected,total,failure);
    }
}
