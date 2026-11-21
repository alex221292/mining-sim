package miningsim.structural;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import miningsim.resource.MassAccounting;
import miningsim.world.Material;
import miningsim.world.World;

/** Bounded synchronous read-only component support analysis. */
public final class ComponentSupportAnalysis {
    public record Key(long settledId,int anchorY,int anchorX) implements Comparable<Key> {
        @Override public int compareTo(Key other) {
            int c=Long.compare(settledId,other.settledId); if(c!=0) return c;
            c=Integer.compare(anchorY,other.anchorY); return c!=0?c:Integer.compare(anchorX,other.anchorX);
        }
    }
    public record Component(Key key,List<SettledRockRegistry.WorldCell> cells,long massMilligrams,
                            miningsim.resource.SubstanceId substance,boolean supported,String reason) {
        public Component { cells=List.copyOf(cells); }
    }
    public record Result(SettledSupportGraph.Status status,List<Component> components,long work) {
        public Result { components=List.copyOf(components); }
    }
    private record Contact(Key below,double left,double right) { }
    private static final class Node {
        final Key key; final List<SettledRockRegistry.WorldCell> cells; final long mass; final miningsim.resource.SubstanceId substance;
        final ArrayList<Contact> contacts=new ArrayList<>();
        final TreeSet<Key> uppers=new TreeSet<>();
        double center,load,moment,unknownLeft=Double.POSITIVE_INFINITY,unknownRight=Double.NEGATIVE_INFINITY;
        Key chosenA,chosenB; int incoming; boolean grounded,forceDone,forceFailed,stable,broken;
        Node(Key key,List<SettledRockRegistry.WorldCell> cells,long mass,miningsim.resource.SubstanceId substance) {
            this.key=key; this.cells=cells; this.mass=mass; this.substance=substance;
            for(var c:cells) center+=c.x()+0.5; center/=cells.size(); load=mass; moment=load*center;
        }
    }
    private static final class Exhausted extends RuntimeException { }
    private static final class Budget {
        final long max; long work;
        Budget(long max) { this.max=max; }
        void add(long count) { if(count<0 || count>max-work) throw new Exhausted(); work+=count; }
    }
    private ComponentSupportAnalysis() { }
    public static Result analyze(World world,RockConnectivity topology,SettledRockRegistry settled,List<Long> seeds,
                                 int maxRocks,int maxCells,long maxWork) {
        Objects.requireNonNull(world); Objects.requireNonNull(topology); Objects.requireNonNull(settled); Objects.requireNonNull(seeds);
        if(!settled.owns(world,topology) || seeds.isEmpty() || seeds.stream().anyMatch(id->id==null || id<=0)
                || maxRocks<1 || maxCells<1 || maxWork<1) throw new IllegalArgumentException("Invalid component analysis inputs");
        long terrain=world.rigidTerrainRevision(),revision=topology.revision(),ownership=settled.revision();
        var budget=new Budget(maxWork);
        try {
            var pending=new TreeSet<Long>(seeds); var visited=new HashSet<Long>(); var nodes=new TreeMap<Key,Node>(); var owners=new HashMap<Long,Key>();
            int cells=0;
            while(!pending.isEmpty()) {
                long id=pending.pollFirst(); if(!visited.add(id)) continue; budget.add(1);
                if(visited.size()>maxRocks) throw new Exhausted();
                var rock=settled.rock(id); if(rock.remainingCells()==0) continue;
                int size=rock.descriptor().cellCount(); if(size>maxCells-cells) throw new Exhausted();
                // Reserve a conservative bound for the helper's traversal and component sorting.
                budget.add((long)size*(20+32-Integer.numberOfLeadingZeros(Math.max(1,size))));
                var components=settled.connectedComponents(id); cells+=size;
                for(var component:components) {
                    var first=component.getFirst(); var key=new Key(id,first.y(),first.x());
                    var node=new Node(key,component,Math.multiplyExact(MassAccounting.cellMilligrams(rock.descriptor().substance()),component.size()),rock.descriptor().substance().id());
                    nodes.put(key,node);
                    for(var cell:component) {
                        long index=(long)cell.y()*world.width()+cell.x(); owners.put(index,key);
                        if(world.get(cell.x(),cell.y())!=Material.STONE) return new Result(SettledSupportGraph.Status.INVALID,List.of(),budget.work);
                        for(int dy:new int[]{-1,1}) { int y=cell.y()+dy; if(y>=0 && y<world.height()) { long other=settled.ownerAt(cell.x(),y); if(other>0 && !visited.contains(other)) pending.add(other); } }
                    }
                }
            }
            for(var node:nodes.values()) for(var cell:node.cells) {
                budget.add(1); int x=cell.x(),y=cell.y();
                for(int[] d:new int[][]{{1,0},{0,1}}) {
                    int nx=x+d[0],ny=y+d[1]; if(!world.contains(nx,ny)) continue;
                    if(node.key.equals(owners.get((long)ny*world.width()+nx)) && topology.bondState(x,y,nx,ny)==RockConnectivity.BondState.BROKEN) node.broken=true;
                }
                if(y+1<world.height()) {
                    var below=world.get(x,y+1); var other=owners.get((long)(y+1)*world.width()+x);
                    if(below==Material.BEDROCK || below==Material.STONE && other==null && settled.ownerAt(x,y+1)==0 && y+2<world.height() && world.get(x,y+2)==Material.BEDROCK)
                        node.contacts.add(new Contact(null,x,x+1.0));
                    else if(other!=null && !other.equals(node.key)) { node.contacts.add(new Contact(other,x,x+1.0)); nodes.get(other).uppers.add(node.key); }
                }
                if(y>0 && world.get(x,y-1)==Material.STONE && settled.ownerAt(x,y-1)==0) {
                    node.unknownLeft=Math.min(node.unknownLeft,x); node.unknownRight=Math.max(node.unknownRight,x+1.0);
                }
            }
            boolean changed;
            do { changed=false; for(var n:nodes.values()) { budget.add(1); if(!n.grounded) for(var c:n.contacts) {
                budget.add(1); if(c.below()==null || nodes.get(c.below()).grounded) { n.grounded=true; changed=true; break; }
            } } } while(changed);
            var ready=new TreeSet<Key>(); for(var n:nodes.values()) { budget.add(1); n.incoming=n.uppers.size(); if(n.incoming==0) ready.add(n.key); }
            while(!ready.isEmpty()) {
                var n=nodes.get(ready.pollFirst()); budget.add(1); Contact first=null,last=null;
                for(var c:n.contacts) { budget.add(1); if(c.below()!=null && !nodes.get(c.below()).grounded) continue;
                    if(first==null || c.left()<first.left() || c.left()==first.left() && compare(c.below(),first.below())<0) first=c;
                    if(last==null || c.right()>last.right() || c.right()==last.right() && compare(c.below(),last.below())<0) last=c;
                }
                double resultant=n.moment/n.load;
                n.forceFailed=first==null || resultant<=first.left()+1e-9 || resultant>=last.right()-1e-9
                        || n.unknownLeft<first.left()-1e-9 || n.unknownRight>last.right()+1e-9;
                if(!n.forceFailed) {
                    double share=(resultant-first.left())/(last.right()-first.left()); n.chosenA=first.below(); n.chosenB=last.below();
                    if(first.below()!=null) addLoad(nodes.get(first.below()),n.load*(1-share),first.left());
                    if(last.below()!=null) addLoad(nodes.get(last.below()),n.load*share,last.right());
                }
                n.forceDone=true; var targets=new HashSet<Key>();
                for(var c:n.contacts) if(c.below()!=null) { budget.add(1); var below=nodes.get(c.below());
                    if(Double.isFinite(n.unknownLeft)) { below.unknownLeft=Math.min(below.unknownLeft,c.left()); below.unknownRight=Math.max(below.unknownRight,c.right()); }
                    if(targets.add(c.below()) && --below.incoming==0) ready.add(below.key);
                }
            }
            do { changed=false; for(var n:nodes.values()) if(!n.stable && n.forceDone && !n.forceFailed && !n.broken) {
                budget.add(1); double left=Double.POSITIVE_INFINITY,right=Double.NEGATIVE_INFINITY;
                for(var c:n.contacts) { budget.add(1); if(c.below()==null || nodes.get(c.below()).stable) { left=Math.min(left,c.left()); right=Math.max(right,c.right()); } }
                double center=n.moment/n.load;
                if((n.chosenA==null || nodes.get(n.chosenA).stable) && (n.chosenB==null || nodes.get(n.chosenB).stable)
                        && center>left+1e-9 && center<right-1e-9 && n.unknownLeft>=left-1e-9 && n.unknownRight<=right+1e-9) { n.stable=true; changed=true; }
            } } while(changed);
            if(world.rigidTerrainRevision()!=terrain || topology.revision()!=revision || settled.revision()!=ownership)
                return new Result(SettledSupportGraph.Status.STALE,List.of(),budget.work);
            var result=new ArrayList<Component>();
            for(var n:nodes.values()) { budget.add(1); String reason=n.stable?"":n.broken?"broken internal bond":!n.grounded?"no vertical foundation path":!n.forceDone?"cyclic load graph":n.forceFailed?"vertical force or moment imbalance":"support not certified";
                result.add(new Component(n.key,n.cells,n.mass,n.substance,n.stable,reason)); }
            return new Result(SettledSupportGraph.Status.COMPLETE,result,budget.work);
        } catch(Exhausted ex) { return new Result(SettledSupportGraph.Status.LIMIT,List.of(),budget.work); }
    }
    private static int compare(Key a,Key b) { if(a==null) return b==null?0:-1; return b==null?1:a.compareTo(b); }
    private static void addLoad(Node node,double load,double x) { node.load+=load; node.moment+=load*x; }
}
