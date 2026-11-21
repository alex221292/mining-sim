package miningsim.physics;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import miningsim.structural.SettledRockRegistry;
import miningsim.structural.SettledSupportGraph;
import miningsim.world.Material;
import miningsim.world.World;

/** Deterministic vertical compression/moment accounting; no World mutation. */
final class ProspectiveAssemblyBalance {
    private record Contact(long below,double x) { }
    private static final class Node {
        final long id,mass; final double center,unknownLeft,unknownRight;
        final ArrayList<Contact> contacts=new ArrayList<>();
        int incoming; double load,moment;
        Node(long id,long mass,double center,double unknownLeft,double unknownRight) {
            this.id=id; this.mass=mass; this.center=center; this.unknownLeft=unknownLeft; this.unknownRight=unknownRight;
            load=mass; moment=mass*center;
        }
    }
    record Result(boolean success,String reason,long work) { }
    private ProspectiveAssemblyBalance() { }
    static Result check(List<GroupSettlingPlanner.BodyPlan> plans,Map<Long,SettledSupportGraph.SupportNode> existing,
                        World world,SettledRockRegistry settled,long budget) {
        long work=0; var nodes=new TreeMap<Long,Node>(); var owners=new java.util.HashMap<Long,Long>();
        for(var value:existing.values()) {
            if(value.integrity()!=SettledRockRegistry.Integrity.INTACT) return new Result(false,"settled "+value.id()+" damaged",work);
            var node=new Node(value.id(),value.massMilligrams(),value.centerX(),value.unknownUpperLeft(),value.unknownUpperRight());
            for(var c:value.contacts()) { node.contacts.add(new Contact(c.below(),c.left())); node.contacts.add(new Contact(c.below(),c.right())); }
            nodes.put(node.id,node);
        }
        for(var p:plans) {
            double sum=0; for(var c:p.cells()) { if(++work>budget) return new Result(false,"prospective work budget exceeded",work-1); sum+=c.x()+0.5; owners.put((long)c.y()*world.width()+c.x(),-p.id()); }
            nodes.put(-p.id(),new Node(-p.id(),p.mass(),sum/p.cells().size(),Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY));
        }
        for(var p:plans) for(var c:p.cells()) {
            if(++work>budget) return new Result(false,"prospective work budget exceeded",work-1);
            int y=c.y()+1; if(y>=world.height()) continue;
            long below=settled.ownerAt(c.x(),y); Long candidate=owners.get((long)y*world.width()+c.x());
            if(world.get(c.x(),y)==Material.BEDROCK) below=0;
            else if(below==0 && candidate!=null) below=candidate;
            else if(below==0) continue;
            if(below!=-p.id()) { var node=nodes.get(-p.id()); node.contacts.add(new Contact(below,c.x())); node.contacts.add(new Contact(below,c.x()+1.0)); }
        }
        for(var node:nodes.values()) {
            var targets=new HashSet<Long>();
            for(var c:node.contacts) {
                if(++work>budget) return new Result(false,"prospective work budget exceeded",work-1);
                if(c.below()!=0 && targets.add(c.below())) {
                    var target=nodes.get(c.below()); if(target==null) return new Result(false,"missing support node "+c.below(),work);
                    target.incoming++;
                }
            }
        }
        var ready=new TreeSet<Long>(); for(var n:nodes.values()) if(n.incoming==0) ready.add(n.id);
        int processed=0;
        while(!ready.isEmpty()) {
            var node=nodes.get(ready.pollFirst()); processed++;
            if(node.contacts.isEmpty()) return new Result(false,"node "+node.id+" has no vertical support",work);
            node.contacts.sort(java.util.Comparator.comparingDouble(Contact::x).thenComparingLong(Contact::below));
            var a=node.contacts.getFirst(); var b=node.contacts.getLast(); double center=node.moment/node.load;
            if(center<=a.x()+1e-9 || center>=b.x()-1e-9 || node.unknownLeft<a.x()-1e-9 || node.unknownRight>b.x()+1e-9)
                return new Result(false,String.format(java.util.Locale.ROOT,"node %d resultant=%.4f outside support=[%.4f,%.4f] or unknown=[%.4f,%.4f]; mass=%d center=%.4f load=%.0f moment=%.4f",node.id,center,a.x(),b.x(),node.unknownLeft,node.unknownRight,node.mass,node.center,node.load,node.moment),work);
            double share=(center-a.x())/(b.x()-a.x());
            if(a.below()!=0) { var n=nodes.get(a.below()); double force=node.load*(1-share); n.load+=force; n.moment+=force*a.x(); }
            if(b.below()!=0) { var n=nodes.get(b.below()); double force=node.load*share; n.load+=force; n.moment+=force*b.x(); }
            var targets=new HashSet<Long>(); for(var c:node.contacts) if(c.below()!=0 && targets.add(c.below())) {
                if(++work>budget) return new Result(false,"prospective work budget exceeded",work-1);
                var n=nodes.get(c.below()); if(--n.incoming==0) ready.add(n.id);
            }
        }
        return processed==nodes.size()?new Result(true,"",work):new Result(false,"cyclic vertical support",work);
    }
}
