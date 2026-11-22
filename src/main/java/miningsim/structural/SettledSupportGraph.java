package miningsim.structural;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import miningsim.world.Material;
import miningsim.world.World;

/** Resumable, deterministic vertical-support analysis for one affected contact component. */
public final class SettledSupportGraph {
    /** Deterministic read-only component analysis, independent of publication and coordinator state. */
    public static ComponentSupportAnalysis.Result analyzeComponents(World world,RockConnectivity topology,SettledRockRegistry settled,
            List<Long> seeds,int maxRocks,int maxCells,long maxWork) {
        return ComponentSupportAnalysis.analyze(world,topology,settled,seeds,maxRocks,maxCells,maxWork);
    }
    public enum Status { IN_PROGRESS, COMPLETE, STALE, LIMIT, INVALID }
    public record Limits(int maxRocks,int maxCells,int workPerStep) {
        public Limits { if(maxRocks<1 || maxCells<1 || workPerStep<1) throw new IllegalArgumentException("Invalid support limits"); }
    }
    public record Result(Status status,List<Long> unsupported,int rocks,int cells,long work) {
        public Result { unsupported=List.copyOf(unsupported); }
    }
    public record Timing(long buildNanos,long stabilityNanos,long cascadeNanos) { }
    public record SupportContact(long below,double left,double right) { }
    public record SupportNode(long id,long massMilligrams,double centerX,List<SupportContact> contacts,
                              double unknownUpperLeft,double unknownUpperRight,SettledRockRegistry.Integrity integrity) {
        public SupportNode { contacts=List.copyOf(contacts); }
    }
    /** Read-only derived result; invalid once any of the captured revisions changes. */
    public record Certificate(long assemblyId,List<Long> supportedIds,int supportContacts,
                              long terrainRevision,long topologyRevision,long ownershipRevision) {
        public Certificate { supportedIds=List.copyOf(supportedIds); }
    }
    private record Contact(long below,double left,double right) { } // below=0 means verified immovable foundation
    private static final class Node {
        final SettledRockRegistry.Rock rock;
        final ArrayList<Contact> contacts=new ArrayList<>();
        int cursor,count; double sumX,upperLeft=Double.POSITIVE_INFINITY,upperRight=Double.NEGATIVE_INFINITY;
        double unknownUpperLeft=Double.POSITIVE_INFINITY,unknownUpperRight=Double.NEGATIVE_INFINITY;
        double load,moment; int incoming; boolean forceFailed,forceDone,grounded;
        long chosenA,chosenB;
        boolean stable;
        Node(SettledRockRegistry.Rock rock) { this.rock=rock; }
    }
    private final World world;
    private final RockConnectivity topology;
    private final SettledRockRegistry settled;
    private final Limits limits;
    private final long terrainRevision,topologyRevision,ownershipRevision;
    private final TreeSet<Long> pending=new TreeSet<>();
    private java.util.Set<Long> prospectiveUpperCells=java.util.Set.of();
    private final java.util.function.LongPredicate isSeed;
    private final TreeMap<Long,Node> nodes=new TreeMap<>();
    private final TreeMap<Long,TreeSet<Long>> uppers=new TreeMap<>();
    private final TreeSet<Long> reachable=new TreeSet<>(),reachPending=new TreeSet<>(),unsupported=new TreeSet<>();
    private java.util.Iterator<Long> reachIterator;
    private int phase,outputIndex,seedIndex;
    private java.util.Iterator<Node> orderIterator;
    private Node current,solving;
    private final ArrayList<Node> order=new ArrayList<>();
    private int cells,solveIndex,contactIndex;
    private int visited;
    private long work;
    private boolean changed;
    private final TreeSet<Long> forceReady=new TreeSet<>();
    private Node forceNode;
    private Contact forceFirst,forceLast;
    private int forceContactIndex;
    private final java.util.HashSet<Long> forceTargets=new java.util.HashSet<>();
    private int groundIndex,groundContactIndex;
    private boolean groundChanged;
    private double left,right;
    private Status status=Status.IN_PROGRESS;
    public long work() { return work; }
    private boolean damagedObserved;
    private boolean stopOnDamage;
    public boolean damagedObserved() { return damagedObserved; }
    void stopOnDamage() { stopOnDamage=true; }
    private List<Long> result=List.of();
    private boolean instrument;
    private final long[] phaseNanos=new long[3];
    void enableProfiling(boolean enabled) { instrument=enabled; }
    Timing timing() { return new Timing(phaseNanos[0],phaseNanos[1],phaseNanos[2]); }
    private int timingPhase() { return phase==0 || phase==4?0:phase==1?1:2; }
    public SettledSupportGraph(World world,RockConnectivity topology,SettledRockRegistry settled,List<Long> seeds,Limits limits) {
        this.world=Objects.requireNonNull(world); this.topology=Objects.requireNonNull(topology); this.settled=Objects.requireNonNull(settled);
        this.limits=Objects.requireNonNull(limits); Objects.requireNonNull(seeds);
        if(!settled.owns(world,topology) || seeds.isEmpty() || seeds.stream().anyMatch(id->id==null || id<=0))
            throw new IllegalArgumentException("Invalid support owners or seeds");
        var stableSeeds=new TreeSet<>(seeds); this.isSeed=stableSeeds::contains; pending.add(stableSeeds.first());
        terrainRevision=world.rigidTerrainRevision(); topologyRevision=topology.revision(); ownershipRevision=settled.revision();
    }
    /** Hypothetical occupied cells are used only as upper contact loads, never as World mutations. */
    public SettledSupportGraph(World world,RockConnectivity topology,SettledRockRegistry settled,List<Long> seeds,Limits limits,
                               java.util.Set<Long> prospectiveUpperCells) {
        this(world,topology,settled,seeds,limits);
        this.prospectiveUpperCells=java.util.Set.copyOf(prospectiveUpperCells);
    }
    /** The seed predicate must remain stable unless a tracked revision changes. */
    SettledSupportGraph(World world,RockConnectivity topology,SettledRockRegistry settled,long root,
                        java.util.function.LongPredicate isSeed,Limits limits) {
        this.world=Objects.requireNonNull(world); this.topology=Objects.requireNonNull(topology); this.settled=Objects.requireNonNull(settled);
        this.limits=Objects.requireNonNull(limits); this.isSeed=Objects.requireNonNull(isSeed);
        if(root<=0 || !settled.owns(world,topology)) throw new IllegalArgumentException("Invalid support owners or root");
        pending.add(root); terrainRevision=world.rigidTerrainRevision(); topologyRevision=topology.revision(); ownershipRevision=settled.revision();
    }
    private boolean stale() { return world.rigidTerrainRevision()!=terrainRevision || topology.revision()!=topologyRevision || settled.revision()!=ownershipRevision; }
    private void enqueue(long id) { if(id>0 && !nodes.containsKey(id)) pending.add(id); }
    public Result step() {
        if(status==Status.COMPLETE && stale()) { status=Status.STALE; result=List.of(); }
        if(status!=Status.IN_PROGRESS) return snapshot();
        if(stale()) { status=Status.STALE; return snapshot(); }
        int remaining=limits.workPerStep();
        int clockPhase=timingPhase(); long clock=instrument?System.nanoTime():0;
        while(remaining>0 && status==Status.IN_PROGRESS) {
            if(instrument && clockPhase!=timingPhase()) {
                long now=System.nanoTime(); phaseNanos[clockPhase]+=now-clock; clock=now; clockPhase=timingPhase();
            }
            if(phase==0 && (current!=null || !pending.isEmpty())) {
                if(current==null) {
                    long id=pending.pollFirst();
                    if(nodes.containsKey(id)) continue;
                    remaining--; work++; visited++;
                    if(visited>limits.maxRocks()) { status=Status.LIMIT; break; }
                    SettledRockRegistry.Rock rock;
                    try { rock=settled.rock(id); } catch(IllegalArgumentException ex) { status=Status.INVALID; break; }
                    if(rock.remainingCells()==0) continue;
                    if(rock.integrity()==SettledRockRegistry.Integrity.DAMAGED) { damagedObserved=true; if(stopOnDamage) break; }
                    if(rock.descriptor().cellCount()>limits.maxCells()-cells) { status=Status.LIMIT; break; }
                    current=new Node(rock); nodes.put(id,current);
                    if(remaining==0) continue;
                }
                var descriptor=current.rock.descriptor();
                if(current.cursor==descriptor.cells().size()) {
                    if(current.count!=current.rock.remainingCells()) { status=Status.INVALID; break; }
                    current=null; continue;
                }
                var local=descriptor.cells().get(current.cursor++); cells++; remaining--; work++;
                int x=current.rock.originX()+local.x(),y=current.rock.originY()+local.y();
                if(settled.ownerAt(x,y)!=current.rock.id()) continue;
                if(world.get(x,y)!=Material.STONE) { status=Status.INVALID; break; }
                current.count++; current.sumX+=x+0.5;
                if(y+1<world.height()) {
                    var material=world.get(x,y+1);
                    if(material==Material.BEDROCK) current.contacts.add(new Contact(0,x,x+1.0));
                    else if(material==Material.STONE) {
                        long below=settled.ownerAt(x,y+1);
                        if(below!=0 && below!=current.rock.id()) {
                            current.contacts.add(new Contact(below,x,x+1.0)); enqueue(below);
                            uppers.computeIfAbsent(below,k->new TreeSet<>()).add(current.rock.id());
                        }
                        else if(below==0 && y+2<world.height() && world.get(x,y+2)==Material.BEDROCK)
                            current.contacts.add(new Contact(0,x,x+1.0));
                    }
                }
                if(y>0 && prospectiveUpperCells.contains((long)(y-1)*world.width()+x)) {
                    current.upperLeft=Math.min(current.upperLeft,x); current.upperRight=Math.max(current.upperRight,x+1.0);
                }
                if(y>0 && world.get(x,y-1)==Material.STONE) {
                    long above=settled.ownerAt(x,y-1);
                    if(above!=current.rock.id()) {
                        if(above!=0) enqueue(above);
                        else { current.unknownUpperLeft=Math.min(current.unknownUpperLeft,x); current.unknownUpperRight=Math.max(current.unknownUpperRight,x+1.0); }
                        current.upperLeft=Math.min(current.upperLeft,x); current.upperRight=Math.max(current.upperRight,x+1.0);
                    }
                }
                continue;
            }
            if(phase==2) {
                if(reachIterator!=null && reachIterator.hasNext()) {
                    long id=reachIterator.next(); remaining--; work++;
                    if(reachable.add(id)) reachPending.add(id);
                    continue;
                }
                if(reachPending.isEmpty()) { phase=3; continue; }
                long id=reachPending.pollFirst(); var next=uppers.get(id);
                reachIterator=next==null?null:next.iterator(); remaining--; work++; continue;
            }
            if(phase==3) {
                if(outputIndex>=order.size()) { result=List.copyOf(unsupported); status=Status.COMPLETE; break; }
                var node=order.get(outputIndex++); if(!node.stable && reachable.contains(node.rock.id())) unsupported.add(node.rock.id());
                remaining--; work++; continue;
            }
            if(phase==4) {
                if(orderIterator.hasNext()) {
                    var n=orderIterator.next(); order.add(n); n.load=n.rock.massMilligrams(); n.moment=n.load*n.sumX/n.count;
                    var above=uppers.get(n.rock.id()); n.incoming=above==null?0:above.size(); if(n.incoming==0) forceReady.add(n.rock.id());
                    remaining--; work++;
                } else phase=8;
                continue;
            }
            if(phase==8) {
                if(groundIndex>=order.size()) {
                    if(groundChanged) { groundIndex=0; groundChanged=false; } else phase=6;
                    continue;
                }
                var n=order.get(groundIndex);
                if(n.grounded || groundContactIndex>=n.contacts.size()) { groundIndex++; groundContactIndex=0; remaining--; work++; continue; }
                var c=n.contacts.get(groundContactIndex++); remaining--; work++;
                if(c.below()==0 || nodes.get(c.below()).grounded) { n.grounded=true; groundChanged=true; }
                continue;
            }
            if(phase==6) {
                if(forceNode==null) {
                    if(forceReady.isEmpty()) { phase=1; continue; }
                    forceNode=nodes.get(forceReady.pollFirst()); forceContactIndex=0; forceFirst=forceLast=null;
                }
                if(forceContactIndex<forceNode.contacts.size()) {
                    var c=forceNode.contacts.get(forceContactIndex++); remaining--; work++;
                    if(c.below()!=0 && !nodes.get(c.below()).grounded) continue;
                    if(forceFirst==null || c.left()<forceFirst.left() || c.left()==forceFirst.left() && c.below()<forceFirst.below()) forceFirst=c;
                    if(forceLast==null || c.right()>forceLast.right() || c.right()==forceLast.right() && c.below()<forceLast.below()) forceLast=c;
                    continue;
                }
                double resultant=forceNode.moment/forceNode.load;
                forceNode.forceFailed=forceFirst==null || resultant<=forceFirst.left()+1e-9 || resultant>=forceLast.right()-1e-9
                        || forceNode.unknownUpperLeft<(forceFirst==null?Double.POSITIVE_INFINITY:forceFirst.left())-1e-9
                        || forceNode.unknownUpperRight>(forceLast==null?Double.NEGATIVE_INFINITY:forceLast.right())+1e-9;
                if(!forceNode.forceFailed) {
                    double share=(resultant-forceFirst.left())/(forceLast.right()-forceFirst.left());
                    forceNode.chosenA=forceFirst.below(); forceNode.chosenB=forceLast.below();
                    if(forceFirst.below()!=0) { var n=nodes.get(forceFirst.below()); double f=forceNode.load*(1-share); n.load+=f; n.moment+=f*forceFirst.left(); }
                    if(forceLast.below()!=0) { var n=nodes.get(forceLast.below()); double f=forceNode.load*share; n.load+=f; n.moment+=f*forceLast.right(); }
                }
                forceNode.forceDone=true; forceContactIndex=0; forceTargets.clear(); phase=7; remaining--; work++; continue;
            }
            if(phase==7) {
                if(forceContactIndex<forceNode.contacts.size()) {
                    var c=forceNode.contacts.get(forceContactIndex++); remaining--; work++;
                    if(c.below()!=0) {
                        var n=nodes.get(c.below());
                        if(Double.isFinite(forceNode.unknownUpperLeft)) {
                            n.unknownUpperLeft=Math.min(n.unknownUpperLeft,c.left()); n.unknownUpperRight=Math.max(n.unknownUpperRight,c.right());
                        }
                        if(forceTargets.add(c.below()) && --n.incoming==0) forceReady.add(n.rock.id());
                    }
                    continue;
                }
                forceNode=null; phase=6; continue;
            }
            if(phase==5) {
                if(seedIndex<order.size()) {
                    long id=order.get(seedIndex++).rock.id(); if(isSeed.test(id) && reachable.add(id)) reachPending.add(id);
                    remaining--; work++; continue;
                }
                phase=2; continue;
            }
            if(phase==0) { orderIterator=nodes.values().iterator(); phase=4; continue; }
            if(solveIndex>=order.size()) {
                if(changed) { changed=false; solveIndex=0; continue; }
                phase=5; continue;
            }
            if(solving==null) { solving=order.get(solveIndex); contactIndex=0; left=Double.POSITIVE_INFINITY; right=Double.NEGATIVE_INFINITY; }
            if(solving.stable) { solving=null; solveIndex++; remaining--; work++; continue; }
            if(contactIndex<solving.contacts.size()) {
                var contact=solving.contacts.get(contactIndex++); remaining--; work++;
                Node below=nodes.get(contact.below());
                if(contact.below()==0 || below!=null && below.stable) { left=Math.min(left,contact.left()); right=Math.max(right,contact.right()); }
                continue;
            }
            double center=solving.moment/solving.load;
            boolean chosen=(solving.chosenA==0 || nodes.get(solving.chosenA).stable) && (solving.chosenB==0 || nodes.get(solving.chosenB).stable);
            if(solving.forceDone && !solving.forceFailed && chosen && solving.rock.integrity()==SettledRockRegistry.Integrity.INTACT && center>left+1e-9 && center<right-1e-9
                    && solving.unknownUpperLeft>=left-1e-9 && solving.unknownUpperRight<=right+1e-9
                    && (prospectiveUpperCells.isEmpty() || solving.upperLeft>=left-1e-9 && solving.upperRight<=right+1e-9)) {
                solving.stable=true; changed=true;
            }
            solving=null; solveIndex++; remaining--; work++;
        }
        if(instrument) phaseNanos[clockPhase]+=System.nanoTime()-clock;
        if(status==Status.IN_PROGRESS && stale()) status=Status.STALE;
        return snapshot();
    }
    private Result snapshot() { return new Result(status,result,nodes.size(),cells,work); }
    public List<Long> analyzedIds() { return status==Status.COMPLETE?List.copyOf(nodes.keySet()):List.of(); }
    /** Bounded geometry summary for one completed component, not a reusable stability certificate. */
    public List<SupportNode> supportNodes() {
        if(status!=Status.COMPLETE || stale()) throw new IllegalStateException("Support analysis incomplete or stale");
        return nodes.values().stream().map(n->new SupportNode(n.rock.id(),n.rock.massMilligrams(),n.sumX/n.count,
                n.contacts.stream().map(c->new SupportContact(c.below(),c.left(),c.right())).toList(),
                n.unknownUpperLeft,n.unknownUpperRight,n.rock.integrity())).toList();
    }
    /** A completed certificate is a snapshot, not permission to ignore later mutations. */
    public Certificate certificate() {
        if(status!=Status.COMPLETE || stale()) throw new IllegalStateException("Support analysis incomplete or stale");
        var supported=new ArrayList<Long>(); int contacts=0;
        for(var node:nodes.values()) {
            if(node.stable) supported.add(node.rock.id());
            contacts=Math.addExact(contacts,node.contacts.size());
        }
        return new Certificate(nodes.isEmpty()?0:nodes.firstKey(),supported,contacts,terrainRevision,topologyRevision,ownershipRevision);
    }
}
