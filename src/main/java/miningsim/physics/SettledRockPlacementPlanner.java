package miningsim.physics;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import miningsim.fragment.RockFragment;
import miningsim.resource.FragmentDescriptor;
import miningsim.structural.SettledRockRegistry;
import miningsim.world.Material;
import miningsim.world.World;

/** Bounded conservative lattice placement; does not change either owner. */
public final class SettledRockPlacementPlanner {
    public enum Status { READY, INVALID_INPUT, NOT_SETTLEABLE, BUDGET_EXCEEDED, STALE }
    public record Limits(int maxCells,long maxChecks,double maxCenterShift,double minCoverage) {
        public Limits {
            if(maxCells<1 || maxChecks<1 || !Double.isFinite(maxCenterShift) || maxCenterShift<0 || maxCenterShift>0.3
                    || !Double.isFinite(minCoverage) || minCoverage<0.90 || minCoverage>1) throw new IllegalArgumentException("Invalid placement limits");
        }
    }
    public record Pose(long bodyId,FragmentDescriptor descriptor,double centerX,double centerY,double angle) {
        public Pose { Objects.requireNonNull(descriptor); }
    }
    public record Cell(int x,int y) { }
    public record Plan(long bodyId,FragmentDescriptor descriptor,int originX,int originY,List<Cell> cells,
                       double coverage,double centerShift,long worldRevision,long ownershipRevision,long occupancyRevision) {
        public Plan { cells=List.copyOf(cells); }
    }
    public record Result(Status status,Plan plan,long checks) { }
    private record Local(int x,int y) { }
    private record CellState(Material material,long owner,boolean otherBody) { }
    private SettledRockPlacementPlanner() { }
    public static Result prepare(Pose pose,World world,RigidBodyOccupancyIndex.Snapshot occupancy,SettledRockRegistry settled,Limits limits) {
        Objects.requireNonNull(pose); Objects.requireNonNull(world); Objects.requireNonNull(occupancy); Objects.requireNonNull(settled); Objects.requireNonNull(limits);
        if(pose.bodyId()<=0 || !Double.isFinite(pose.centerX()) || !Double.isFinite(pose.centerY()) || !Double.isFinite(pose.angle())
                || Math.abs(pose.centerX())>1_000_000_000 || Math.abs(pose.centerY())>1_000_000_000
                || pose.descriptor().cellCount()>limits.maxCells() || !settled.owns(world) || occupancy.width()!=world.width() || occupancy.height()!=world.height())
            return new Result(Status.INVALID_INPUT,null,0);
        long worldRevision=world.mutationRevision(),ownerRevision=settled.revision(),occRevision=occupancy.diagnostics().revision();
        FragmentGeometry geometry;
        try { geometry=new FragmentGeometry(new RockFragment(pose.bodyId(),pose.descriptor(),0,0,0)); }
        catch(IllegalArgumentException | ArithmeticException ex) { return new Result(Status.INVALID_INPUT,null,0); }
        // Snapshot only candidate cells; do not scan or copy the full World.
        var captured=new java.util.HashMap<Long,CellState>(); long checks=0;
        for(int turn=0;turn<4;turn++) {
            var local=new ArrayList<Local>(); int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE;
            for(var c:pose.descriptor().cells()) {
                int x=switch(turn) { case 0->c.x(); case 1->-c.y()-1; case 2->-c.x()-1; default->c.y(); };
                int y=switch(turn) { case 0->c.y(); case 1->c.x(); case 2->-c.y()-1; default->-c.x()-1; };
                local.add(new Local(x,y)); minX=Math.min(minX,x); minY=Math.min(minY,y);
            }
            double lx=0,ly=0; for(var c:local) { lx+=c.x()-minX+0.5; ly+=c.y()-minY+0.5; }
            lx/=local.size(); ly/=local.size();
            int baseX=(int)Math.rint(pose.centerX()-lx),baseY=(int)Math.rint(pose.centerY()-ly);
            for(int oy=-1;oy<=1;oy++) for(int ox=-1;ox<=1;ox++) {
                int x0=baseX+ox,y0=baseY+oy;
                if(Math.hypot(x0+lx-pose.centerX(),y0+ly-pose.centerY())>limits.maxCenterShift()) continue;
                for(var cell:local) {
                    if(++checks>limits.maxChecks()) return new Result(Status.BUDGET_EXCEEDED,null,checks);
                    long x=(long)x0+cell.x()-minX,y=(long)y0+cell.y()-minY;
                    if(x<0 || y<0 || x>=world.width() || y>=world.height()) continue;
                    long key=y*world.width()+x;
                    if(!captured.containsKey(key)) {
                        boolean other=false; for(long id:occupancy.bodyIdsAt((int)x,(int)y)) if(id!=pose.bodyId()) { other=true; break; }
                        captured.put(key,new CellState(world.get((int)x,(int)y),settled.ownerAt((int)x,(int)y),other));
                    }
                }
            }
        }
        if(world.mutationRevision()!=worldRevision || settled.revision()!=ownerRevision || occupancy.diagnostics().revision()!=occRevision)
            return new Result(Status.STALE,null,checks);
        var snapshot=java.util.Map.copyOf(captured);
        Plan best=null;
        for(int turn=0;turn<4;turn++) {
            var local=new ArrayList<Local>(); int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE;
            for(var c:pose.descriptor().cells()) {
                int x=switch(turn) { case 0->c.x(); case 1->-c.y()-1; case 2->-c.x()-1; default->c.y(); };
                int y=switch(turn) { case 0->c.y(); case 1->c.x(); case 2->-c.y()-1; default->-c.x()-1; };
                local.add(new Local(x,y)); minX=Math.min(minX,x); minY=Math.min(minY,y);
            }
            var normalized=new ArrayList<FragmentDescriptor.Cell>(); double lx=0,ly=0;
            for(var c:local) { int x=c.x()-minX,y=c.y()-minY; normalized.add(new FragmentDescriptor.Cell(x,y)); lx+=x+0.5; ly+=y+0.5; }
            lx/=local.size(); ly/=local.size();
            int baseX=(int)Math.rint(pose.centerX()-lx),baseY=(int)Math.rint(pose.centerY()-ly);
            for(int oy=-1;oy<=1;oy++) for(int ox=-1;ox<=1;ox++) {
                int x0=baseX+ox,y0=baseY+oy; double shift=Math.hypot(x0+lx-pose.centerX(),y0+ly-pose.centerY());
                if(shift>limits.maxCenterShift()) continue;
                var placed=new ArrayList<Cell>(); var keys=new HashSet<Long>(); boolean valid=true;
                for(var c:normalized) {
                    if(++checks>limits.maxChecks()) return new Result(Status.BUDGET_EXCEEDED,null,checks);
                    long x=(long)x0+c.x(),y=(long)y0+c.y();
                    if(x<0 || y<0 || x>=world.width() || y>=world.height()) { valid=false; break; }
                    int xx=(int)x,yy=(int)y;
                    var state=snapshot.get(y*world.width()+x);
                    if(state==null || state.material()!=Material.AIR || state.owner()!=0 || state.otherBody()) { valid=false; break; }
                    if(!valid) break;
                    placed.add(new Cell(xx,yy)); keys.add(y*world.width()+x);
                }
                if(!valid) continue;
                double area=0;
                for(var cell:pose.descriptor().cells()) {
                    double[] px=new double[4],py=new double[4]; int[] dx={0,1,1,0},dy={0,0,1,1};
                    double a=Double.POSITIVE_INFINITY,b=a,c=Double.NEGATIVE_INFINITY,d=c;
                    for(int k=0;k<4;k++) { var p=geometry.transform(cell.x()+dx[k],cell.y()+dy[k],pose.centerX(),pose.centerY(),pose.angle());
                        px[k]=p.x(); py[k]=p.y(); a=Math.min(a,p.x()); b=Math.min(b,p.y()); c=Math.max(c,p.x()); d=Math.max(d,p.y()); }
                    int left=(int)Math.max(0,Math.floor(a)),right=(int)Math.min(world.width(),Math.ceil(c));
                    int top=(int)Math.max(0,Math.floor(b)),bottom=(int)Math.min(world.height(),Math.ceil(d));
                    for(int y=top;y<bottom;y++) for(int x=left;x<right;x++) {
                        if(++checks>limits.maxChecks()) return new Result(Status.BUDGET_EXCEEDED,null,checks);
                        if(keys.contains((long)y*world.width()+x)) area+=RigidBodyOccupancyIndex.intersectionArea(px,py,x,y);
                    }
                }
                double coverage=area/pose.descriptor().cellCount();
                if(coverage+1e-12<limits.minCoverage()) continue;
                placed.sort(java.util.Comparator.comparingInt(Cell::y).thenComparingInt(Cell::x));
                if(best==null || coverage>best.coverage()+1e-12 || Math.abs(coverage-best.coverage())<=1e-12 &&
                        (y0<best.originY() || y0==best.originY() && x0<best.originX())) {
                    var descriptor=new FragmentDescriptor(pose.descriptor().substance(),normalized);
                    best=new Plan(pose.bodyId(),descriptor,x0,y0,placed,coverage,shift,worldRevision,ownerRevision,occRevision);
                }
            }
        }
        if(world.mutationRevision()!=worldRevision || settled.revision()!=ownerRevision || occupancy.diagnostics().revision()!=occRevision)
            return new Result(Status.STALE,null,checks);
        return new Result(best==null?Status.NOT_SETTLEABLE:Status.READY,best,checks);
    }
}
