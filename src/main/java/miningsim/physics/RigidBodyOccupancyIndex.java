package miningsim.physics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import miningsim.world.World;

/** Explicit single-owner build-then-publish occupancy cache; never owns or mutates bodies. */
public final class RigidBodyOccupancyIndex {
    /** Positive intersection polygon area in square cells must exceed this tolerance. */
    public static final double AREA_EPSILON = 1e-10;
    public record Limits(int bodies, long fragmentCells, long candidates, long areaChecks,
                         long positiveIntersections, int occupiedCells, int memberships) {
        public Limits {
            if (bodies < 0 || fragmentCells < 0 || candidates < 0 || areaChecks < 0 || positiveIntersections < 0
                    || occupiedCells < 0 || memberships < 0) throw new IllegalArgumentException("Negative occupancy limit");
        }
        public static Limits defaults() { return new Limits(4096, 1_000_000, 10_000_000, 10_000_000, 10_000_000, 2_000_000, 4_000_000); }
    }
    public record Diagnostics(long revision, long generation, int indexedBodies, long fragmentCells, long candidates,
                              long areaChecks, long positiveIntersections, int occupiedCells, int memberships,
                              int multipleOccupants, long clippedCandidates) { }
    public static final class Snapshot {
        private final int width, height;
        private final long[] keys, ids;
        private final int[] offsets;
        private final Diagnostics diagnostics;
        private Snapshot(int width, int height, long[] keys, int[] offsets, long[] ids, Diagnostics diagnostics) {
            this.width=width; this.height=height; this.keys=keys; this.offsets=offsets; this.ids=ids; this.diagnostics=diagnostics;
        }
        public int width() { return width; }
        public int height() { return height; }
        public Diagnostics diagnostics() { return diagnostics; }
        public boolean occupied(int x, int y) { return index(x,y) >= 0; }
        public long[] bodyIdsAt(int x, int y) {
            int i=index(x,y); return i < 0 ? new long[0] : Arrays.copyOfRange(ids,offsets[i],offsets[i+1]);
        }
        /** Row-major occupied coordinates, without exposing internal arrays. */
        public void forEachCell(CellVisitor visitor) {
            Objects.requireNonNull(visitor);
            for(long key:keys) visitor.visit((int)(key%width),(int)(key/width));
        }
        private int index(int x,int y) {
            if(x<0 || y<0 || x>=width || y>=height) return -1;
            return Arrays.binarySearch(keys,(long)y*width+x);
        }
    }
    @FunctionalInterface public interface CellVisitor { void visit(int x,int y); }
    private static final class Ids {
        long[] values = new long[1]; int size;
        void add(long id) {
            if(size>0 && values[size-1]==id) return;
            if(size==values.length) values=Arrays.copyOf(values,Math.multiplyExact(size,2));
            values[size++]=id;
        }
    }
    private final int width,height;
    private final Limits limits;
    private Snapshot snapshot;
    /** Prepared against one published snapshot; no allocation is needed for publication. */
    public final class Prepared {
        private final Snapshot expected, next;
        private Prepared(Snapshot expected, Snapshot next) { this.expected=expected; this.next=next; }
        public Snapshot snapshot() { return next; }
        public boolean valid() { return snapshot == expected; }
        public void publish() {
            if (!valid()) throw new IllegalStateException("Occupancy publication is stale");
            snapshot = next;
        }
    }
    public RigidBodyOccupancyIndex(World world, Limits limits) {
        Objects.requireNonNull(world); this.width=world.width(); this.height=world.height(); this.limits=Objects.requireNonNull(limits);
        snapshot=new Snapshot(width,height,new long[0],new int[]{0},new long[0],new Diagnostics(0,0,0,0,0,0,0,0,0,0,0));
    }
    public Snapshot snapshot() { return snapshot; }
    public Snapshot rebuild(RigidBodySystem system,long generation) { return rebuild(Objects.requireNonNull(system).bodies(),generation); }
    /** The body list and transforms must not change concurrently with this single-owner call. */
    public Snapshot rebuild(List<RigidBody> input,long generation) {
        var prepared=prepare(input,generation); prepared.publish(); return prepared.snapshot();
    }
    public Prepared prepare(RigidBodySystem system,long generation) { return prepare(Objects.requireNonNull(system).bodies(),generation); }
    public Prepared prepare(List<RigidBody> input,long generation) {
        Objects.requireNonNull(input);
        if(input.size()>limits.bodies()) throw failure("bodies",input.size(),limits.bodies());
        var bodies=new ArrayList<>(input); bodies.sort(Comparator.comparingLong(RigidBody::id));
        for(int i=1;i<bodies.size();i++) if(bodies.get(i-1).id()==bodies.get(i).id()) throw new IllegalArgumentException("Duplicate body ID");
        long cells=0,candidates=0,checks=0,positive=0,clipped=0; int memberships=0;
        var map=new TreeMap<Long,Ids>();
        for(var body:bodies) for(var cell:body.fragment().descriptor().cells()) {
            cells=Math.incrementExact(cells); if(cells>limits.fragmentCells()) throw failure("fragment cells",cells,limits.fragmentCells());
            double[] px=new double[4],py=new double[4];
            int[] dx={0,1,1,0},dy={0,0,1,1};
            double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX;
            for(int i=0;i<4;i++) {
                var p=body.geometry().transform(cell.x()+(double)dx[i],cell.y()+(double)dy[i],body.xCells(),body.yCells(),body.angleRadians());
                px[i]=p.x(); py[i]=p.y();
                if(!Double.isFinite(px[i]) || !Double.isFinite(py[i]) || Math.abs(px[i])>1_000_000_000 || Math.abs(py[i])>1_000_000_000)
                    throw new IllegalStateException("Non-finite or extreme occupancy geometry for body "+body.id());
                minX=Math.min(minX,px[i]); maxX=Math.max(maxX,px[i]); minY=Math.min(minY,py[i]); maxY=Math.max(maxY,py[i]);
            }
            long x0=(long)Math.floor(minX),x1=(long)Math.ceil(maxX),y0=(long)Math.floor(minY),y1=(long)Math.ceil(maxY);
            long total=Math.multiplyExact(x1-x0,y1-y0);
            candidates=Math.addExact(candidates,total); if(candidates>limits.candidates()) throw failure("candidates",candidates,limits.candidates());
            long left=Math.max(0,x0),right=Math.min(width,x1),top=Math.max(0,y0),bottom=Math.min(height,y1);
            long inWidth=Math.max(0,right-left),inHeight=Math.max(0,bottom-top);
            clipped=Math.addExact(clipped,total-Math.multiplyExact(inWidth,inHeight));
            for(long y=top;y<bottom;y++) for(long x=left;x<right;x++) {
                checks=Math.incrementExact(checks); if(checks>limits.areaChecks()) throw failure("area checks",checks,limits.areaChecks());
                if(intersectionArea(px,py,x,y)<=AREA_EPSILON) continue;
                positive=Math.incrementExact(positive); if(positive>limits.positiveIntersections()) throw failure("positive intersections",positive,limits.positiveIntersections());
                long key=y*width+x; var ids=map.get(key);
                if(ids==null) {
                    if(map.size()>=limits.occupiedCells()) throw failure("occupied cells",(long)map.size()+1,limits.occupiedCells());
                    ids=new Ids(); map.put(key,ids);
                }
                if(ids.size==0 || ids.values[ids.size-1]!=body.id()) {
                    if(memberships>=limits.memberships()) throw failure("memberships",(long)memberships+1,limits.memberships());
                    ids.add(body.id()); memberships++;
                }
            }
        }
        long revision=Math.incrementExact(snapshot.diagnostics().revision());
        long[] keys=new long[map.size()],ids=new long[memberships]; int[] offsets=new int[map.size()+1]; int i=0,pos=0,multi=0;
        for(var entry:map.entrySet()) {
            keys[i]=entry.getKey(); offsets[i]=pos; var list=entry.getValue();
            System.arraycopy(list.values,0,ids,pos,list.size); pos+=list.size; if(list.size>1) multi++; i++;
        }
        offsets[i]=pos;
        var stats=new Diagnostics(revision,generation,bodies.size(),cells,candidates,checks,positive,map.size(),memberships,multi,clipped);
        var next=new Snapshot(width,height,keys,offsets,ids,stats); return new Prepared(snapshot,next);
    }
    private static IllegalStateException failure(String name,long requested,long allowed) {
        return new IllegalStateException("Occupancy "+name+" limit exceeded: "+requested+" > "+allowed);
    }
    /** Clip a transformed square to the candidate cell in local coordinates, then use shoelace area. */
    static double intersectionArea(double[] px,double[] py,long cellX,long cellY) {
        double[] x=new double[12],y=new double[12],nx=new double[12],ny=new double[12];
        int n=4; for(int i=0;i<n;i++) { x[i]=px[i]-cellX; y[i]=py[i]-cellY; }
        for(int edge=0;edge<4 && n>0;edge++) {
            int count=0; int previous=n-1; double ps=side(x[previous],y[previous],edge);
            for(int i=0;i<n;i++) {
                double cs=side(x[i],y[i],edge);
                if((ps>=0)!=(cs>=0)) {
                    double t=ps/(ps-cs); nx[count]=x[previous]+t*(x[i]-x[previous]); ny[count++]=y[previous]+t*(y[i]-y[previous]);
                }
                if(cs>=0) { nx[count]=x[i]; ny[count++]=y[i]; }
                previous=i; ps=cs;
            }
            var swap=x; x=nx; nx=swap; swap=y; y=ny; ny=swap; n=count;
        }
        double twice=0; for(int i=0;i<n;i++) { int j=(i+1)%n; twice+=x[i]*y[j]-y[i]*x[j]; }
        double area=Math.abs(twice)*0.5;
        if(!Double.isFinite(area)) throw new IllegalStateException("Non-finite occupancy intersection area");
        return area;
    }
    private static double side(double x,double y,int edge) {
        return switch(edge) { case 0 -> x; case 1 -> 1-x; case 2 -> y; default -> 1-y; };
    }
}
