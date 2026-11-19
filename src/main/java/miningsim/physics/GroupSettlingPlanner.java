package miningsim.physics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import miningsim.resource.SubstanceId;
import miningsim.structural.SettledRockRegistry;
import miningsim.world.Material;
import miningsim.world.World;

/** Experimental bounded greedy area assignment; it never publishes simulation state. */
final class GroupSettlingPlanner {
    record Cell(int x,int y) { }
    record BodyPlan(long id,SubstanceId substance,long mass,List<Cell> cells,double coverage,double centerShift) {
        BodyPlan { cells=List.copyOf(cells); }
    }
    record Result(boolean success,String reason,List<BodyPlan> bodies,long work,int competingCells) {
        Result { bodies=List.copyOf(bodies); }
    }
    private record Offer(int body,long cell,double area) { }
    private record Support(int body,double x) { } // -1 is bedrock
    private static final class Budget extends RuntimeException { }
    private static final class Work {
        final long limit; long value;
        Work(long limit) { this.limit=limit; }
        void add(long n) { if(n>limit-value) throw new Budget(); value+=n; }
    }
    private GroupSettlingPlanner() { }
    static Result plan(List<RigidBody> input,World world,RigidBodyOccupancyIndex.Snapshot occupancy,
                       SettledRockRegistry settled,long budget,double maxShift,double minCoverage) {
        if(input.isEmpty() || input.size()>3 || budget<1 || !Double.isFinite(maxShift) || maxShift<0 || maxShift>0.3
                || !Double.isFinite(minCoverage) || minCoverage<0.9 || minCoverage>1)
            throw new IllegalArgumentException("Invalid group planning limits");
        var work=new Work(budget); int competing=0;
        try {
            var bodies=new ArrayList<>(input); bodies.sort(Comparator.comparingLong(RigidBody::id));
            var ids=new HashSet<Long>(); int total=0;
            for(var body:bodies) { if(!ids.add(body.id())) throw new IllegalArgumentException("Duplicate ID"); total=Math.addExact(total,body.fragment().descriptor().cellCount()); }
            if(total>2048 || !settled.owns(world)) throw new IllegalArgumentException("Group size or ownership limit");
            long revision=world.mutationRevision(),ownershipRevision=settled.revision();
            var offers=new ArrayList<Offer>(); var owners=new HashMap<Long,Integer>();
            for(int i=0;i<bodies.size();i++) {
                var body=bodies.get(i); var areas=new TreeMap<Long,Double>();
                for(var c:body.fragment().descriptor().cells()) {
                    work.add(1);
                    double[] px=new double[4],py=new double[4]; int[] dx={0,1,1,0},dy={0,0,1,1};
                    double left=Double.POSITIVE_INFINITY,top=left,right=Double.NEGATIVE_INFINITY,bottom=right;
                    for(int k=0;k<4;k++) {
                        var p=body.geometry().transform(c.x()+dx[k],c.y()+dy[k],body.xCells(),body.yCells(),body.angleRadians());
                        px[k]=p.x(); py[k]=p.y(); left=Math.min(left,p.x()); right=Math.max(right,p.x()); top=Math.min(top,p.y()); bottom=Math.max(bottom,p.y());
                    }
                    if(left<0 || top<0 || right>world.width() || bottom>world.height()) return new Result(false,"source outside world",List.of(),work.value,competing);
                    for(int y=(int)Math.floor(top);y<(int)Math.ceil(bottom);y++) for(int x=(int)Math.floor(left);x<(int)Math.ceil(right);x++) {
                        work.add(1);
                        if(!world.contains(x,y) || world.get(x,y)!=Material.AIR || settled.ownerAt(x,y)!=0) continue;
                        boolean foreign=false; for(long id:occupancy.bodyIdsAt(x,y)) if(!ids.contains(id)) { foreign=true; break; }
                        if(foreign) continue;
                        double area=RigidBodyOccupancyIndex.intersectionArea(px,py,x,y);
                        if(area>RigidBodyOccupancyIndex.AREA_EPSILON) areas.merge((long)y*world.width()+x,area,Double::sum);
                    }
                }
                for(var entry:areas.entrySet()) {
                    work.add(1); offers.add(new Offer(i,entry.getKey(),entry.getValue()));
                    int prior=owners.getOrDefault(entry.getKey(),0); if(prior==1) competing++; owners.put(entry.getKey(),prior+1);
                }
            }
            offers.sort((a,b)->{ work.add(1); int c=Double.compare(b.area(),a.area()); if(c!=0) return c;
                c=Long.compare(bodies.get(a.body()).id(),bodies.get(b.body()).id()); return c!=0?c:Long.compare(a.cell(),b.cell()); });
            var assigned=new HashSet<Long>(); var selected=new ArrayList<List<Offer>>(); for(int i=0;i<bodies.size();i++) selected.add(new ArrayList<>());
            for(var offer:offers) {
                work.add(1); var list=selected.get(offer.body());
                if(list.size()<bodies.get(offer.body()).fragment().descriptor().cellCount() && assigned.add(offer.cell())) list.add(offer);
            }
            var result=new ArrayList<BodyPlan>(); var cellOwner=new HashMap<Long,Integer>();
            for(int i=0;i<bodies.size();i++) {
                var body=bodies.get(i); var list=selected.get(i); int n=body.fragment().descriptor().cellCount();
                if(list.size()!=n) return new Result(false,"greedy assignment lacks cells for body "+body.id()+": "+list.size()+"/"+n,List.of(),work.value,competing);
                list.sort(Comparator.comparingLong(Offer::cell)); var cells=new ArrayList<Cell>(); double area=0,cx=0,cy=0;
                for(var offer:list) { work.add(1); int x=(int)(offer.cell()%world.width()),y=(int)(offer.cell()/world.width());
                    cells.add(new Cell(x,y)); cellOwner.put(offer.cell(),i); area+=offer.area(); cx+=x+0.5; cy+=y+0.5; }
                double coverage=area/n,shift=Math.hypot(cx/n-body.xCells(),cy/n-body.yCells());
                if(coverage+1e-12<minCoverage || shift>maxShift)
                    return new Result(false,String.format(java.util.Locale.ROOT,"body %d coverage %.9f shift %.9f",body.id(),coverage,shift),List.of(),work.value,competing);
                if(!connected(cells,work)) return new Result(false,"disconnected body "+body.id(),List.of(),work.value,competing);
                result.add(new BodyPlan(body.id(),body.fragment().substance(),body.fragment().massMilligrams(),cells,coverage,shift));
            }
            if(!supported(bodies,result,cellOwner,world,work)) return new Result(false,"no conservative vertical equilibrium to BEDROCK",List.of(),work.value,competing);
            if(world.mutationRevision()!=revision || settled.revision()!=ownershipRevision) return new Result(false,"stale input",List.of(),work.value,competing);
            return new Result(true,"",result,work.value,competing);
        } catch(Budget ex) { return new Result(false,"work budget exceeded",List.of(),work.value,competing); }
    }
    private static boolean connected(List<Cell> cells,Work work) {
        var pending=new HashSet<>(cells); var queue=new java.util.ArrayDeque<Cell>(); queue.add(cells.getFirst()); pending.remove(cells.getFirst());
        while(!queue.isEmpty()) { var c=queue.removeFirst(); for(int[] d:new int[][]{{-1,0},{1,0},{0,-1},{0,1}}) {
            work.add(1); var n=new Cell(c.x()+d[0],c.y()+d[1]); if(pending.remove(n)) queue.add(n);
        } }
        return pending.isEmpty();
    }
    /** Vertical-only, frictionless static force/moment balance; not a rock-strength analysis. */
    private static boolean supported(List<RigidBody> bodies,List<BodyPlan> plans,Map<Long,Integer> owners,World world,Work work) {
        var order=new ArrayList<Integer>(); for(int i=0;i<bodies.size();i++) order.add(i);
        order.sort(Comparator.<Integer>comparingDouble(i->bodies.get(i).yCells()).thenComparingLong(i->bodies.get(i).id()));
        double[] weights=new double[bodies.size()],moments=new double[bodies.size()];
        for(int i=0;i<bodies.size();i++) {
            weights[i]=bodies.get(i).geometry().massKg(); double x=0;
            for(var cell:plans.get(i).cells()) x+=cell.x()+0.5;
            moments[i]=weights[i]*x/plans.get(i).cells().size();
        }
        for(int i:order) {
            var contacts=new ArrayList<Support>();
            for(var c:plans.get(i).cells()) {
                work.add(1); int y=c.y()+1; if(!world.contains(c.x(),y)) continue;
                if(world.get(c.x(),y)==Material.BEDROCK) { contacts.add(new Support(-1,c.x())); contacts.add(new Support(-1,c.x()+1.0)); }
                else { Integer below=owners.get((long)y*world.width()+c.x());
                    if(below!=null && below!=i && bodies.get(below).yCells()>bodies.get(i).yCells()) {
                        contacts.add(new Support(below,c.x())); contacts.add(new Support(below,c.x()+1.0));
                    } }
            }
            if(contacts.isEmpty()) return false;
            contacts.sort(Comparator.comparingDouble(Support::x).thenComparingInt(Support::body));
            var a=contacts.getFirst(); var b=contacts.getLast(); double x=moments[i]/weights[i];
            if(x<=a.x()+1e-9 || x>=b.x()-1e-9) return false;
            double share=b.x()==a.x()?0:(x-a.x())/(b.x()-a.x());
            if(a.body()>=0) { weights[a.body()]+=weights[i]*(1-share); moments[a.body()]+=weights[i]*(1-share)*a.x(); }
            if(b.body()>=0) { weights[b.body()]+=weights[i]*share; moments[b.body()]+=weights[i]*share*b.x(); }
        }
        return true;
    }
}
