package miningsim.physics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import miningsim.structural.RockConnectivity;
import miningsim.structural.SettledRockRegistry;
import miningsim.structural.SettledSupportCoordinator;
import miningsim.world.World;

/** Owner-thread lifecycle orchestration; it never advances physics or cellular time itself. */
public final class HeadlessRockLifecycle {
    public record Diagnostics(int rigidBodies,int settledRocks,int pendingSupport,long settled,long reactivated,
                              long rejected,long retries,Map<String,Long> rejectionReasons,String lastReason,long supportNanos,long planningNanos,long commitNanos,
                              long reactivationPlanningNanos,long reactivationCommitNanos,String lastReject) {
        public Diagnostics { rejectionReasons=Map.copyOf(rejectionReasons); }
    }
    private record FailureKey(List<Long> ids,long worldRevision,long topologyRevision,long ownershipRevision,List<RigidBody.State> states) { }
    private record SupportFailureKey(long worldRevision,long topologyRevision,long ownershipRevision,List<Long> ids,List<RigidBody.State> states) { }
    private final World world;
    private final RockConnectivity topology;
    private final RigidBodySystem physics;
    private final RigidBodyOccupancyIndex occupancy;
    private final CoupledSimulationStep coupled;
    private final SettledRockRegistry settled;
    private final SettledSupportCoordinator support;
    private final Map<List<Long>,GroupSettlingEligibility> eligibility=new HashMap<>();
    private final Map<List<Long>,FailureKey> rejected=new HashMap<>();
    private final TreeMap<String,Long> reasons=new TreeMap<>();
    private SupportFailureKey supportFailure;
    private boolean instrument;
    private long settledCount,reactivatedCount,rejectedCount,retries,supportNanos,planningNanos,commitNanos;
    private int pending;
    private String reason="";
    private String lastReject="";
    private boolean reactivatedThisTick;
    private boolean settlingPublication;
    private long reactivationPlanningNanos,reactivationCommitNanos;
    private static final class ContactBudget extends RuntimeException { }

    public HeadlessRockLifecycle(World world,RockConnectivity topology,RigidBodySystem physics,RigidBodyOccupancyIndex occupancy,
                                 CoupledSimulationStep coupled,boolean instrument,SettledSupportCoordinator.Limits limits) {
        this.world=world; this.topology=topology; this.physics=physics; this.occupancy=occupancy; this.coupled=coupled; this.instrument=instrument;
        settled=new SettledRockRegistry(world,topology,4096,2048);
        support=new SettledSupportCoordinator(world,topology,settled,physics,occupancy,limits); support.enableProfiling(instrument);
    }
    public SettledRockRegistry settled() { return settled; }
    public void enableProfiling(boolean enabled) { instrument=enabled; support.enableProfiling(enabled); }
    /** Owner-thread phase diagnostic, also useful for headless publication observers. */
    public boolean inSettlingPublication() { return settlingPublication; }
    public Diagnostics diagnostics() { return new Diagnostics(physics.bodies().size(),settled.liveRocks(),pending,settledCount,reactivatedCount,rejectedCount,retries,reasons,reason,supportNanos,planningNanos,commitNanos,reactivationPlanningNanos,reactivationCommitNanos,lastReject); }
    public void setSupportLimits(SettledSupportCoordinator.Limits limits) { support.setLimits(limits); supportFailure=null; }
    private void reject(String category,String message) { rejectedCount++; reasons.merge(category,1L,Long::sum); reason=message; lastReject=message; }
    /** Runs after commands and before any physics of this tick. */
    public void beforePhysics() {
        reactivatedThisTick=false; planningNanos=commitNanos=reactivationPlanningNanos=reactivationCommitNanos=0;
        var bodies=physics.bodies(); var key=new SupportFailureKey(world.mutationRevision(),topology.revision(),settled.revision(),
                bodies.stream().map(RigidBody::id).toList(),bodies.stream().map(RigidBody::state).toList());
        if(key.equals(supportFailure)) { supportNanos=0; return; }
        if(supportFailure!=null) { retries++; supportFailure=null; }
        long start=instrument?System.nanoTime():0; var result=support.step(coupled.diagnostics().ticks());
        supportNanos=instrument?System.nanoTime()-start:0; pending=result.pendingIds();
        if(result.status()==SettledSupportCoordinator.Status.COMMITTED) {
            reactivatedThisTick=true; reactivatedCount+=result.reactivated().size();
            if(instrument) { reactivationPlanningNanos=support.profile().preparationNanos(); reactivationCommitNanos=support.profile().commitNanos(); }
        }
        if(result.status()==SettledSupportCoordinator.Status.BLOCKED || result.status()==SettledSupportCoordinator.Status.LIMIT) {
            supportFailure=key; reject("support."+result.status(),"support: "+result.reason());
        }
    }
    /** Runs only after a successful coupled tick and its cellular phase. */
    public void afterCellular() {
        var bodies=physics.bodies(); var groups=groups(bodies);
        var active=new java.util.HashSet<>(groups); eligibility.keySet().retainAll(active); rejected.keySet().retainAll(active);
        for(var ids:groups) {
            if(ids.size()>3) { reason="settling: contact group exceeds three bodies"; continue; }
            var observer=eligibility.computeIfAbsent(ids,GroupSettlingEligibility::new);
            boolean ready=observer.observe(physics,coupled,world,topology,settled);
            if(!ready || reactivatedThisTick || !settled.supportTransitionsAllowed()) continue;
            var states=bodies.stream().map(RigidBody::state).toList();
            var key=new FailureKey(ids,world.mutationRevision(),topology.revision(),settled.revision(),states);
            var previous=rejected.get(ids); if(key.equals(previous)) continue;
            if(previous!=null) retries++;
            long start=instrument?System.nanoTime():0;
            var result=GroupSettlingCommit.prepareGroupSettling(ids,physics,coupled,world,occupancy,topology,settled,observer,2_000_000);
            planningNanos=instrument?System.nanoTime()-start:0;
            if(result.status()!=GroupSettlingCommit.Status.READY) { rejected.put(ids,key); reject("settling."+result.status(),"settling: "+result.reason()); break; }
            start=instrument?System.nanoTime():0; World.BatchResult publication;
            settlingPublication=true;
            try { publication=world.commitGroupSettling(result.prepared()); }
            finally { settlingPublication=false; }
            commitNanos=instrument?System.nanoTime()-start:0;
            if(publication!=World.BatchResult.CHANGED) { rejected.put(ids,key); reject("settling.VETO","settling: publication rejected"); break; }
            settledCount+=ids.size(); reason=""; eligibility.clear(); rejected.clear();
            break; // subsequent candidates must be revalidated against the new world
        }
    }
    /** Conservative deterministic occupancy contact components; preparation performs the exact checks. */
    private List<List<Long>> groups(List<RigidBody> bodies) {
        var parents=new TreeMap<Long,Long>();
        var sleeping=new java.util.HashSet<Long>();
        for(var b:bodies) { parents.put(b.id(),b.id()); if(b.sleeping()) sleeping.add(b.id()); }
        if(sleeping.isEmpty()) return List.of();
        if(occupancy.snapshot().diagnostics().occupiedCells()>100_000) { reason="settling: occupancy scan budget exceeded"; return List.of(); }
        var snapshot=occupancy.snapshot(); long[] work={0};
        try { snapshot.forEachCell((x,y)->{
            long[] here=snapshot.bodyIdsAt(x,y); long[] right=snapshot.bodyIdsAt(x+1,y),down=snapshot.bodyIdsAt(x,y+1);
            for(long a:here) if(parents.containsKey(a)) {
                for(long b:here) { if(++work[0]>2_000_000) throw new ContactBudget(); if(parents.containsKey(b)) union(parents,a,b); }
                for(long b:right) { if(++work[0]>2_000_000) throw new ContactBudget(); if(parents.containsKey(b)) union(parents,a,b); }
                for(long b:down) { if(++work[0]>2_000_000) throw new ContactBudget(); if(parents.containsKey(b)) union(parents,a,b); }
            }
        }); } catch(ContactBudget ex) { reason="settling: contact discovery work budget exceeded"; return List.of(); }
        var result=new TreeMap<Long,ArrayList<Long>>();
        for(long id:parents.keySet()) result.computeIfAbsent(find(parents,id),ignored->new ArrayList<>()).add(id);
        return result.values().stream().filter(group->group.stream().allMatch(sleeping::contains)).map(List::copyOf).toList();
    }
    private static long find(TreeMap<Long,Long> parents,long id) {
        long parent=parents.get(id); while(parent!=id) { id=parent; parent=parents.get(id); } return id;
    }
    private static void union(TreeMap<Long,Long> parents,long a,long b) {
        long x=find(parents,a),y=find(parents,b); if(x!=y) parents.put(Math.max(x,y),Math.min(x,y));
    }
}
