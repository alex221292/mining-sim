package miningsim.structural;

import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import miningsim.physics.GroupRockReactivation;
import miningsim.physics.RigidBodyOccupancyIndex;
import miningsim.physics.RigidBodySystem;
import miningsim.world.World;

/** Explicit owner-thread coordinator; call between simulation ticks, independently of cellular stepping. */
public final class SettledSupportCoordinator implements RockConnectivity.SupportChangeObserver {
    public enum Status { IDLE, SCANNING, IN_PROGRESS, COMMITTED, BLOCKED, LIMIT }
    public record Limits(int scanIdsPerTick,int graphRocks,int graphCells,int graphWorkPerTick,
                         int groupBodies,int groupCells,int attemptsPerTick) {
        public Limits {
            if(scanIdsPerTick<1 || graphRocks<1 || graphCells<1 || graphWorkPerTick<1 || groupBodies<1 || groupCells<1 || attemptsPerTick<1)
                throw new IllegalArgumentException("Invalid coordinator limits");
        }
        /** Initial headless limits exercised by the documented 50-rock and 794-cell fixtures. */
        public static Limits measuredDefaults() { return new Limits(100,100,10_000,256,50,10_000,1); }
    }
    public record Result(Status status,List<Long> reactivated,int pendingIds,String reason) {
        public Result { reactivated=List.copyOf(reactivated); }
    }
    public record Profile(long analysisNanos,long buildNanos,long stabilityNanos,long cascadeNanos,
                          long preparationNanos,long commitNanos,int rocks,int cells,long graphWork) { }
    /** Cached completed components only; incomplete while work is pending. */
    public record AssemblyDiagnostics(int assemblies,int supported,int contacts,boolean complete) { }
    private record Assembly(long id,List<Long> ids,int contacts,int supported,int groups) { }
    private final World world;
    private final RockConnectivity topology;
    private final SettledRockRegistry settled;
    private final RigidBodySystem physics;
    private final RigidBodyOccupancyIndex occupancy;
    private Limits limits;
    private final TreeSet<Long> dirty=new TreeSet<>();
    private final java.util.TreeMap<Long,Assembly> assemblies=new java.util.TreeMap<>();
    private final java.util.HashMap<Long,Assembly> assemblyByRock=new java.util.HashMap<>();
    private int supportedCount,contactCount,assemblyCount;
    private long seenOwnershipRevision;
    private long scanCursor=1;
    private boolean scanning=true;
    private SettledSupportGraph graph;
    private IncrementalComponentSupport componentGraph;
    private Assembly componentCache;
    private int componentCacheIndex;
    private List<Long> cleanup;
    private int cleanupIndex;
    private long cleanupTerrain,cleanupTopology,cleanupOwnership;
    private long root;
    private final boolean enabled;
    private boolean eventFault;
    private boolean instrument;
    private long analysisNanos;
    private long componentWork;
    /** Read-only support-analysis work in the last coordinator call. */
    public long componentWork() { return componentWork; }
    public boolean componentAnalysisPending() { return componentGraph!=null; }
    private Profile profile=new Profile(0,0,0,0,0,0,0,0,0);
    public SettledSupportCoordinator(World world,RockConnectivity topology,SettledRockRegistry settled,
            RigidBodySystem physics,RigidBodyOccupancyIndex occupancy,Limits limits) {
        this.world=Objects.requireNonNull(world); this.topology=Objects.requireNonNull(topology); this.settled=Objects.requireNonNull(settled);
        this.physics=Objects.requireNonNull(physics); this.occupancy=Objects.requireNonNull(occupancy); this.limits=Objects.requireNonNull(limits);
        if(!settled.owns(world,topology) || !occupancy.owns(world)) throw new IllegalArgumentException("Mismatched support owners");
        enabled=world.mode()==World.Mode.BEDROCK_ENABLED; seenOwnershipRevision=settled.revision();
        if(enabled) {
            topology.attachSupportObserver(this);
            settled.attachSupportTransitionGate(()->!eventFault && !scanning && graph==null && componentGraph==null && cleanup==null && dirty.isEmpty() && settled.revision()==seenOwnershipRevision);
        }
    }
    /** Change limits only on the owner thread; pending work is retained and safely restarted. */
    public void setLimits(Limits limits) { this.limits=Objects.requireNonNull(limits); graph=null; componentGraph=null; componentCache=null; cleanup=null; }
    /** Diagnostics only; wall-clock time never influences support decisions. */
    public void enableProfiling(boolean enabled) { instrument=enabled; }
    public Profile profile() { return profile; }
    public AssemblyDiagnostics assemblyDiagnostics() {
        return new AssemblyDiagnostics(assemblyCount,supportedCount,contactCount,settled.supportTransitionsAllowed());
    }
    private void invalidateAssembly(long id) {
        var assembly=assemblyByRock.remove(id); if(assembly==null || assemblies.get(assembly.id())!=assembly) return;
        assemblies.remove(assembly.id());
        supportedCount-=assembly.supported(); contactCount-=assembly.contacts(); assemblyCount-=assembly.groups();
    }
    private void markDirty(long id) { if(id>0) { dirty.add(id); invalidateAssembly(id); } }
    private void cacheAssembly(SettledSupportGraph graph) {
        var ids=graph.analyzedIds(); var cert=graph.certificate();
        for(long id:ids) invalidateAssembly(id);
        if(ids.isEmpty() || cert.supportedIds().size()!=ids.size()) return;
        var assembly=new Assembly(cert.assemblyId(),ids,cert.supportContacts(),ids.size(),1);
        assemblies.put(cert.assemblyId(),assembly);
        for(long id:ids) assemblyByRock.put(id,assembly);
        supportedCount+=ids.size(); contactCount+=cert.supportContacts(); assemblyCount++;
    }
    @Override public void changed(int x,int y,long previousOwner) {
        try {
            markDirty(previousOwner);
            if(world.contains(x,y)) markDirty(settled.ownerAt(x,y));
            if(y>0) markDirty(settled.ownerAt(x,y-1));
            if(y+1<world.height()) markDirty(settled.ownerAt(x,y+1));
            seenOwnershipRevision=settled.revision();
        } catch(RuntimeException ex) { eventFault=true; }
    }
    @Override public void invalidated() { scanning=true; scanCursor=1; graph=null; componentGraph=null; componentCache=null; cleanup=null; assemblies.clear(); assemblyByRock.clear(); supportedCount=contactCount=assemblyCount=0; }
    public Result step(long generation) {
        componentWork=0;
        if(generation<0) throw new IllegalArgumentException("Negative generation");
        if(!enabled) return state(Status.IDLE,List.of(),"LEGACY mode");
        if(eventFault) return state(Status.BLOCKED,List.of(),"Support change tracking failed; create a new session");
        try {
            if(settled.revision()!=seenOwnershipRevision) { scanning=true; scanCursor=1; seenOwnershipRevision=settled.revision(); assemblies.clear(); assemblyByRock.clear(); supportedCount=contactCount=assemblyCount=0; }
        } catch(IllegalStateException ex) { return state(Status.BLOCKED,List.of(),"Support ownership unavailable: "+ex.getMessage()); }
        if(cleanup!=null) {
            boolean stale;
            try { stale=world.rigidTerrainRevision()!=cleanupTerrain || topology.revision()!=cleanupTopology || settled.revision()!=cleanupOwnership; }
            catch(IllegalStateException ex) { return state(Status.BLOCKED,List.of(),"Support cleanup unavailable: "+ex.getMessage()); }
            if(stale) {
                cleanup=null; graph=null; componentGraph=null;
            } else {
                int count=0;
                while(cleanupIndex<cleanup.size() && count<Math.min(limits.scanIdsPerTick(),limits.graphWorkPerTick())) { dirty.remove(cleanup.get(cleanupIndex++)); count++; }
                componentWork=count;
                if(cleanupIndex==cleanup.size()) cleanup=null;
                return state(cleanup!=null || scanning || !dirty.isEmpty()?Status.IN_PROGRESS:Status.IDLE,List.of(),"");
            }
        }
        if(scanning) {
            int count=0,end=settled.recordCount();
            while(scanCursor<=end && count<Math.min(limits.scanIdsPerTick(),limits.graphWorkPerTick())) {
                var rock=settled.rock(scanCursor++); if(rock.remainingCells()>0) dirty.add(rock.id());
                count++;
            }
            if(scanCursor>end) scanning=false;
            componentWork=count;
            if(count>0) return state(scanning?Status.SCANNING:Status.IN_PROGRESS,List.of(),"");
        }
        if(scanning) return state(Status.SCANNING,List.of(),"");
        if(componentGraph!=null) return stepComponents(generation);
        if(graph==null && !dirty.isEmpty()) {
            root=dirty.first();
            try { graph=new SettledSupportGraph(world,topology,settled,root,dirty::contains,new SettledSupportGraph.Limits(limits.graphRocks(),limits.graphCells(),limits.graphWorkPerTick())); }
            catch(IllegalStateException ex) { return state(Status.BLOCKED,List.of(),"Support graph unavailable: "+ex.getMessage()); }
            graph.stopOnDamage();
            graph.enableProfiling(instrument);
            analysisNanos=0;
        }
        if(graph==null) return state(scanning?Status.SCANNING:Status.IDLE,List.of(),"");
        long start=instrument?System.nanoTime():0;
        SettledSupportGraph.Result analysis;
        long beforeWork=graph.work();
        try { analysis=graph.step(); }
        catch(RuntimeException ex) { graph=null; return state(Status.BLOCKED,List.of(),"Support analysis failed: "+ex); }
        if(instrument) analysisNanos+=System.nanoTime()-start;
        componentWork=analysis.work()-beforeWork;
        var timing=instrument?graph.timing():null;
        if(instrument) profile=new Profile(analysisNanos,timing.buildNanos(),timing.stabilityNanos(),timing.cascadeNanos(),0,0,analysis.rocks(),analysis.cells(),analysis.work());
        if(graph.damagedObserved() && analysis.status()==SettledSupportGraph.Status.IN_PROGRESS) {
            componentGraph=new IncrementalComponentSupport(world,topology,settled,root,limits.graphRocks(),limits.graphCells()); graph=null;
            return state(Status.IN_PROGRESS,List.of(),"");
        }
        if(analysis.status()==SettledSupportGraph.Status.IN_PROGRESS) return state(Status.IN_PROGRESS,List.of(),"");
        if(analysis.status()==SettledSupportGraph.Status.STALE) { graph=null; return state(Status.IN_PROGRESS,List.of(),"Support inputs changed; analysis will restart"); }
        if(analysis.status()==SettledSupportGraph.Status.LIMIT) { graph=null; return state(Status.LIMIT,List.of(),"Support graph limit exceeded; dirty IDs retained"); }
        if(analysis.status()!=SettledSupportGraph.Status.COMPLETE) { graph=null; return state(Status.BLOCKED,List.of(),"Invalid support component; dirty IDs retained"); }
        if(graph.damagedObserved()) { componentGraph=new IncrementalComponentSupport(world,topology,settled,root,limits.graphRocks(),limits.graphCells()); graph=null; return state(Status.IN_PROGRESS,List.of(),""); }
        var covered=graph.analyzedIds(); var group=analysis.unsupported();
        if(group.isEmpty()) {
            cacheAssembly(graph);
            scheduleCleanup(covered); graph=null;
            return state(Status.IN_PROGRESS,List.of(),"");
        }
        if(group.size()>limits.groupBodies()) { graph=null; return state(Status.LIMIT,List.of(),"Cascade group limit exceeded; dirty IDs retained"); }
        long before=world.mutationRevision();
        try {
            start=instrument?System.nanoTime():0;
            var prepared=GroupRockReactivation.prepareGroupReactivation(group,world,topology,settled,physics,occupancy,generation,
                    new GroupRockReactivation.Limits(limits.groupBodies(),limits.groupCells()),settled.supportPublicationPermit());
            long preparationNanos=instrument?System.nanoTime()-start:0;
            if(instrument) profile=new Profile(analysisNanos,timing.buildNanos(),timing.stabilityNanos(),timing.cascadeNanos(),preparationNanos,0,analysis.rocks(),analysis.cells(),analysis.work());
            if(prepared.status()!=GroupRockReactivation.Status.READY) {
                graph=null; return state(prepared.status()==GroupRockReactivation.Status.BUDGET?Status.LIMIT:Status.BLOCKED,List.of(),prepared.reason());
            }
            start=instrument?System.nanoTime():0; var result=world.commitGroupReactivation(prepared.plan());
            long commitNanos=instrument?System.nanoTime()-start:0;
            if(instrument) profile=new Profile(analysisNanos,timing.buildNanos(),timing.stabilityNanos(),timing.cascadeNanos(),preparationNanos,commitNanos,analysis.rocks(),analysis.cells(),analysis.work());
            if(result!=World.BatchResult.CHANGED) { graph=null; return state(Status.BLOCKED,List.of(),"Group publication vetoed or stale"); }
        } catch(RuntimeException ex) {
            graph=null;
            if(world.mutationRevision()!=before) throw ex;
            return state(Status.BLOCKED,List.of(),"Precommit exception: "+ex);
        }
        scheduleCleanup(covered); graph=null;
        return state(Status.COMMITTED,group,"");
    }
    private Result stepComponents(long generation) {
        long start=instrument?System.nanoTime():0; IncrementalComponentSupport.Status status;
        try { status=componentGraph.step(limits.graphWorkPerTick()); }
        catch(RuntimeException ex) { componentGraph=null; componentCache=null; return state(Status.BLOCKED,List.of(),"Component support analysis failed: "+ex); }
        componentWork=componentGraph.lastWork(); if(instrument) analysisNanos+=System.nanoTime()-start;
        if(instrument) profile=new Profile(analysisNanos,0,0,0,0,0,0,0,componentGraph.work());
        if(status==IncrementalComponentSupport.Status.IN_PROGRESS) return state(Status.IN_PROGRESS,List.of(),"");
        if(status==IncrementalComponentSupport.Status.STALE) { componentGraph=null; componentCache=null; return state(Status.IN_PROGRESS,List.of(),"Support inputs changed; analysis will restart"); }
        if(status!=IncrementalComponentSupport.Status.COMPLETE) { componentGraph=null; componentCache=null; return state(status==IncrementalComponentSupport.Status.LIMIT?Status.LIMIT:Status.BLOCKED,List.of(),"Component support analysis: "+status); }
        var unsupported=componentGraph.unsupported();
        if(unsupported.isEmpty()) {
            if(componentCache==null) {
                componentCache=new Assembly(root,componentGraph.coveredIds(),componentGraph.contactCount(),componentGraph.supportedCount(),componentGraph.assemblyCount());
                componentCacheIndex=0;
            }
            while(componentWork<limits.graphWorkPerTick() && componentCacheIndex<componentCache.ids().size()) {
                long id=componentCache.ids().get(componentCacheIndex++); invalidateAssembly(id); assemblyByRock.put(id,componentCache); componentWork++;
            }
            if(componentCacheIndex<componentCache.ids().size() || componentWork>=limits.graphWorkPerTick()) return state(Status.IN_PROGRESS,List.of(),"");
            componentWork++;
            if(componentCache.groups()>0) {
                assemblies.put(componentCache.id(),componentCache); supportedCount+=componentCache.supported(); contactCount+=componentCache.contacts(); assemblyCount+=componentCache.groups();
            }
            cleanup=componentCache.ids(); cleanupIndex=0; cleanupTerrain=world.rigidTerrainRevision(); cleanupTopology=topology.revision(); cleanupOwnership=settled.revision();
            componentGraph=null; componentCache=null; return state(Status.IN_PROGRESS,List.of(),"");
        }
        if(unsupported.size()>limits.groupBodies()) { componentGraph=null; return state(Status.LIMIT,List.of(),"Cascade component limit exceeded; dirty IDs retained"); }
        long before=world.mutationRevision();
        try {
            var input=unsupported.stream().map(c->new GroupRockReactivation.Component(c.key().settledId(),c.cells())).toList();
            start=instrument?System.nanoTime():0;
            var prepared=GroupRockReactivation.prepareComponentReactivation(input,world,topology,settled,physics,occupancy,generation,
                    new GroupRockReactivation.Limits(limits.groupBodies(),limits.groupCells()),settled.supportPublicationPermit());
            long preparation=instrument?System.nanoTime()-start:0;
            if(prepared.status()!=GroupRockReactivation.Status.READY) { componentGraph=null; return state(prepared.status()==GroupRockReactivation.Status.BUDGET?Status.LIMIT:Status.BLOCKED,List.of(),prepared.reason()); }
            start=instrument?System.nanoTime():0; var publication=world.commitGroupReactivation(prepared.plan());
            long commit=instrument?System.nanoTime()-start:0;
            if(instrument) profile=new Profile(analysisNanos,0,0,0,preparation,commit,0,0,componentGraph.work());
            componentGraph=null;
            if(publication!=World.BatchResult.CHANGED) return state(Status.BLOCKED,List.of(),"Component publication vetoed or stale");
            return state(Status.COMMITTED,unsupported.stream().map(c->c.key().settledId()).toList(),"");
        } catch(RuntimeException ex) {
            componentGraph=null; if(world.mutationRevision()!=before) throw ex;
            return state(Status.BLOCKED,List.of(),"Component precommit exception: "+ex);
        }
    }
    private void scheduleCleanup(List<Long> covered) {
        var ids=new java.util.ArrayList<>(covered);
        if(!ids.contains(root)) ids.add(root);
        cleanup=List.copyOf(ids); cleanupIndex=0;
        cleanupTerrain=world.rigidTerrainRevision(); cleanupTopology=topology.revision(); cleanupOwnership=settled.revision();
    }
    private Result state(Status status,List<Long> ids,String reason) { return new Result(status,ids,dirty.size(),reason); }
}
