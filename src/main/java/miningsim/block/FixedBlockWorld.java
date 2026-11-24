package miningsim.block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayDeque;
import java.util.HashSet;
import miningsim.resource.SubstanceId;
import miningsim.resource.SubstanceRegistry;
import miningsim.world.Material;
import miningsim.world.World;

/** Single-owner grid blocks. World STONE is the block's cellular representation, not extra matter. */
public final class FixedBlockWorld {
    public static final int DEFAULT_BLOCK_SIZE=8;
    /** Number of unsupported fixed ticks spent stationary before a new fall. */
    public static final int FALL_DELAY_TICKS=8;
    public static final int MAX_HORIZONTAL_SUPPORT_DISTANCE_CELLS=32;
    public static final int WATER_SEARCH_RADIUS=32;
    public static final int WATER_MAX_VISITS=8192;
    public static final int WATER_MAX_CHECKS=32768;
    public static final int WATER_VENT_MAX_DISTANCE=128;
    public enum State { STATIC, FALLING }
    public enum MoveResult { MOVED, SUPPORTED, OBSTRUCTED }
    public enum Reason { NONE, PINNED, WAITING_FOR_FALL, SUPPORTED_BY_SOLID, BLOCKED_BY_STONE, WATER_DISPLACEMENT_BLOCKED, WATER_SEARCH_BUDGET_EXCEEDED, INVALID_OR_STALE_PLAN }
    public record BlockDiagnostic(long id,Reason reason,int visits,int checks) { }
    public record TickDiagnostics(int waterAttempts,int moved,int maxVisits,int maxChecks) { }
    public record SupportDiagnostics(int supported,int unsupported,int waiting,long analysisNanos,int processed,int contacts) { }
    public record BlockSupport(long id,boolean supported,int distanceCells,State state,int remainingDelayTicks) { }
    public record Block(long id,int x,int y,State state,SubstanceId substance) { }
    /** Authoritative block fields; diagnostics are intentionally omitted. */
    public record SavedBlock(long id,int x,int y,State state,int unsupportedTicks,boolean pinned) { }
    public record SavedState(long nextId,List<SavedBlock> blocks) { public SavedState { blocks=List.copyOf(blocks); } }
    private static final class Entry {
        final long id; final SubstanceId substance; int x,y; State state=State.STATIC; boolean pinned; Reason reason=Reason.NONE; int visits,checks,unsupportedTicks,supportDistance=Integer.MAX_VALUE;
        Entry(long id,int x,int y,boolean pinned) { this.id=id; this.x=x; this.y=y; this.pinned=pinned; substance=SubstanceRegistry.STONE; }
        Block snapshot() { return new Block(id,x,y,state,substance); }
    }
    private final World world;
    private final int rockBlockSizeCells;
    private final int yOffset;
    private final Map<Long,Entry> byCell=new HashMap<>();
    private long nextId=1,rejected,updateNanos,ticks,obstructed;
    private int waterAttempts,moved,maxVisits,maxChecks;
    private SupportDiagnostics supportDiagnostics=new SupportDiagnostics(0,0,0,0,0,0);
    public FixedBlockWorld(World world) { this(world,DEFAULT_BLOCK_SIZE); }
    public FixedBlockWorld(World world,int rockBlockSizeCells) {
        this.world=java.util.Objects.requireNonNull(world);
        if(rockBlockSizeCells!=4 && rockBlockSizeCells!=8) throw new IllegalArgumentException("Rock block size must be 4 or 8");
        this.rockBlockSizeCells=rockBlockSizeCells;
        if(world.mode()!=World.Mode.BEDROCK_ENABLED || world.hasChangeSink()) throw new IllegalArgumentException("Fixed blocks require an independent bedrock world");
        yOffset=Math.floorMod(world.height()-1,rockBlockSizeCells);
    }
    public int rockBlockSizeCells() { return rockBlockSizeCells; }
    public int yOffset() { return yOffset; }
    private long key(int x,int y) { return (long)y*world.width()+x; }
    public int snapX(int x) { return Math.floorDiv(x,rockBlockSizeCells)*rockBlockSizeCells; }
    public int snapY(int y) { return Math.floorDiv(y-yOffset,rockBlockSizeCells)*rockBlockSizeCells+yOffset; }
    public int size() { return byCell.size(); }
    public long rejected() { return rejected; }
    public long updateNanos() { return updateNanos; }
    public long ticks() { return ticks; }
    public long obstructed() { return obstructed; }
    public TickDiagnostics tickDiagnostics() { return new TickDiagnostics(waterAttempts,moved,maxVisits,maxChecks); }
    public SupportDiagnostics supportDiagnostics() { return supportDiagnostics; }
    public BlockSupport blockSupportAt(int x,int y) {
        if(!world.contains(x,y)) return null;
        var e=byCell.get(key(snapX(x),snapY(y))); if(e==null) return null;
        return new BlockSupport(e.id,e.supportDistance!=Integer.MAX_VALUE,e.supportDistance==Integer.MAX_VALUE?-1:e.supportDistance,e.state,Math.max(0,FALL_DELAY_TICKS-e.unsupportedTicks));
    }
    public List<BlockDiagnostic> diagnostics() { return byCell.values().stream().sorted(Comparator.comparingLong(e->e.id)).map(e->new BlockDiagnostic(e.id,e.reason,e.visits,e.checks)).toList(); }
    public List<Block> blocks() { return byCell.values().stream().sorted(Comparator.comparingLong(e->e.id)).map(Entry::snapshot).toList(); }
    public SavedState saveState() {
        return new SavedState(nextId,byCell.values().stream().sorted(Comparator.comparingLong(e->e.id))
                .map(e->new SavedBlock(e.id,e.x,e.y,e.state,e.unsupportedTicks,e.pinned)).toList());
    }
    /** Rebuild metadata only after verifying that every STONE cell is owned exactly once. */
    public void restoreState(SavedState saved) {
        if(!byCell.isEmpty() || nextId!=1) throw new IllegalStateException("Restore requires empty registry");
        java.util.Objects.requireNonNull(saved); var ids=new HashSet<Long>(); var entries=new HashMap<Long,Entry>();
        long max=0;
        for(var b:saved.blocks()) {
            if(b==null || b.id()<=0 || !ids.add(b.id()) || b.state()==null || b.unsupportedTicks()<0 || b.unsupportedTicks()>FALL_DELAY_TICKS
                    || b.state()==State.FALLING && (b.unsupportedTicks()!=0 || b.pinned()) || b.pinned() && b.unsupportedTicks()!=0
                    || !validArea(b.x(),b.y()) || snapX(b.x())!=b.x() || snapY(b.y())!=b.y())
                throw new IllegalArgumentException("Invalid block record");
            var e=new Entry(b.id(),b.x(),b.y(),b.pinned()); e.state=b.state(); e.unsupportedTicks=b.unsupportedTicks();
            if(entries.put(key(b.x(),b.y()),e)!=null) throw new IllegalArgumentException("Overlapping blocks");
            for(int y=b.y();y<b.y()+rockBlockSizeCells;y++) for(int x=b.x();x<b.x()+rockBlockSizeCells;x++)
                if(world.get(x,y)!=Material.STONE) throw new IllegalArgumentException("Block and World mismatch");
            max=Math.max(max,b.id());
        }
        if(saved.nextId()<=max || saved.nextId()<=0 || saved.nextId()==Long.MAX_VALUE) throw new IllegalArgumentException("Invalid next block ID");
        long stone=0; for(int y=0;y<world.height();y++) for(int x=0;x<world.width();x++) if(world.get(x,y)==Material.STONE) stone++;
        if(stone!=(long)entries.size()*rockBlockSizeCells*rockBlockSizeCells) throw new IllegalArgumentException("Unowned STONE cells");
        byCell.putAll(entries); nextId=saved.nextId();
    }
    public Block blockAt(int x,int y) { if(!world.contains(x,y)) return null; var e=byCell.get(key(snapX(x),snapY(y))); return e==null?null:e.snapshot(); }
    private boolean validArea(int x,int y) { return x>=0 && y>=0 && x<=world.width()-rockBlockSizeCells && y<=world.height()-rockBlockSizeCells; }
    private boolean empty(int x,int y) {
        if(!validArea(x,y)) return false;
        for(int dy=0;dy<rockBlockSizeCells;dy++) for(int dx=0;dx<rockBlockSizeCells;dx++) if(world.get(x+dx,y+dy)!=Material.AIR) return false;
        return true;
    }
    private void append(List<World.CellChange> changes,int x,int y,Material from,Material to) {
        for(int dy=0;dy<rockBlockSizeCells;dy++) for(int dx=0;dx<rockBlockSizeCells;dx++) changes.add(new World.CellChange(x+dx,y+dy,from,to));
    }
    public boolean place(int x,int y,boolean pinned) {
        if(!world.contains(x,y)) { rejected++; return false; }
        x=snapX(x); y=snapY(y); if(byCell.containsKey(key(x,y))) return false;
        if(!empty(x,y)) { rejected++; return false; }
        long id=nextId,next=Math.incrementExact(nextId);
        var changes=new ArrayList<World.CellChange>(rockBlockSizeCells*rockBlockSizeCells); append(changes,x,y,Material.AIR,Material.STONE);
        if(world.replaceAllIfMatches(changes)!=World.BatchResult.CHANGED) { rejected++; return false; }
        byCell.put(key(x,y),new Entry(id,x,y,pinned)); nextId=next; return true;
    }
    public boolean removeAt(int x,int y) {
        if(!world.contains(x,y)) return false;
        var e=byCell.get(key(snapX(x),snapY(y))); if(e==null) return false;
        var changes=new ArrayList<World.CellChange>(rockBlockSizeCells*rockBlockSizeCells); append(changes,e.x,e.y,Material.STONE,Material.AIR);
        if(world.replaceAllIfMatches(changes)!=World.BatchResult.CHANGED) throw new IllegalStateException("Block ownership mismatch");
        byCell.remove(key(e.x,e.y)); return true;
    }
    public void releaseStroke() { for(var e:byCell.values()) e.pinned=false; }
    private record SearchCell(int x,int y,int distance) { }
    private static final class Work { int visits,checks; boolean exceeded; }
    private boolean within(int x,int y,int left,int top) { return x>=left && x<left+rockBlockSizeCells && y>=top && y<top+rockBlockSizeCells; }
    /** Bounded vertical vent through water and aligned, movable blocks above this block. */
    private boolean hasVerticalVent(Entry block,Work work) {
        for(int dx=0;dx<rockBlockSizeCells;dx++) for(int distance=1;distance<=WATER_VENT_MAX_DISTANCE;distance++) {
            int x=block.x+dx,y=block.y-distance; if(y<0) break;
            if(work.checks>=WATER_MAX_CHECKS) { work.exceeded=true; return false; } work.checks++;
            var material=world.get(x,y);
            if(material==Material.AIR) return true;
            if(material==Material.WATER) continue;
            if(material==Material.STONE) {
                var upper=byCell.get(key(snapX(x),snapY(y)));
                if(upper!=null && upper.x==block.x && !upper.pinned) continue;
            }
            break;
        }
        return false;
    }
    private List<SearchCell> findWaterDestinations(List<SearchCell> sources,Entry block,Work work) {
        var queue=new ArrayDeque<SearchCell>(); var seen=new HashSet<Long>();
        for(var source:sources) { queue.add(source); seen.add(key(source.x(),source.y())); }
        var destinations=new ArrayList<SearchCell>();
        boolean airReached=hasVerticalVent(block,work);
        if(work.exceeded) return null;
        int[][] directions={{-1,0},{1,0},{0,-1},{0,1}};
        while(!queue.isEmpty()) {
            var current=queue.removeFirst(); if(work.visits>=WATER_MAX_VISITS) { work.exceeded=true; return null; } work.visits++;
            if(current.distance()>=WATER_SEARCH_RADIUS) continue;
            for(var d:directions) {
                if(work.checks>=WATER_MAX_CHECKS) { work.exceeded=true; return null; } work.checks++;
                int nx=current.x()+d[0],ny=current.y()+d[1];
                if(!world.contains(nx,ny) || !seen.add(key(nx,ny))) continue;
                boolean destination=within(nx,ny,block.x,block.y+rockBlockSizeCells);
                boolean vacated=within(nx,ny,block.x,block.y);
                var material=world.get(nx,ny);
                if(material!=Material.AIR && material!=Material.WATER && !(vacated && material==Material.STONE)) continue;
                var next=new SearchCell(nx,ny,current.distance()+1);
                if(!destination && (material==Material.AIR || vacated)) {
                    if(material==Material.AIR) airReached=true;
                    if(destinations.size()<sources.size()) destinations.add(next);
                    if(airReached && destinations.size()==sources.size()) return destinations;
                }
                queue.addLast(next);
            }
        }
        return null;
    }
    private MoveResult move(Entry e,int nextY) {
        e.reason=Reason.NONE; e.visits=e.checks=0;
        if(!validArea(e.x,nextY)) { e.reason=Reason.SUPPORTED_BY_SOLID; return MoveResult.SUPPORTED; }
        if(world.hasChangeSink()) { e.reason=Reason.INVALID_OR_STALE_PLAN; return MoveResult.OBSTRUCTED; }
        var water=new ArrayList<SearchCell>();
        for(int dy=0;dy<rockBlockSizeCells;dy++) for(int dx=0;dx<rockBlockSizeCells;dx++)
            if(world.get(e.x+dx,e.y+dy)!=Material.STONE) { e.reason=Reason.INVALID_OR_STALE_PLAN; throw new IllegalStateException("Block ownership mismatch for "+e.id); }
        for(int dy=0;dy<rockBlockSizeCells;dy++) for(int dx=0;dx<rockBlockSizeCells;dx++) {
            var material=world.get(e.x+dx,nextY+dy);
            if(material==Material.WATER) water.add(new SearchCell(e.x+dx,nextY+dy,0));
            else if(material!=Material.AIR) {
                e.reason=Reason.SUPPORTED_BY_SOLID;
                if(material==Material.STONE) {
                    var below=byCell.get(key(snapX(e.x+dx),snapY(nextY+dy)));
                    if(below!=null && !below.pinned && below.reason!=Reason.SUPPORTED_BY_SOLID) e.reason=Reason.BLOCKED_BY_STONE;
                }
                return MoveResult.SUPPORTED;
            }
        }
        var work=new Work();
        if(!water.isEmpty()) waterAttempts++;
        List<SearchCell> destinations=water.isEmpty()?List.of():findWaterDestinations(water,e,work);
        e.visits=work.visits; e.checks=work.checks;
        maxVisits=Math.max(maxVisits,work.visits); maxChecks=Math.max(maxChecks,work.checks);
        if(destinations==null) { e.reason=work.exceeded?Reason.WATER_SEARCH_BUDGET_EXCEEDED:Reason.WATER_DISPLACEMENT_BLOCKED; return MoveResult.OBSTRUCTED; }
        var reserved=new HashSet<Long>(); for(var target:destinations) reserved.add(key(target.x(),target.y()));
        var changes=new ArrayList<World.CellChange>(2*rockBlockSizeCells*rockBlockSizeCells+destinations.size());
        for(int dy=0;dy<rockBlockSizeCells;dy++) for(int dx=0;dx<rockBlockSizeCells;dx++) {
            int x=e.x+dx,y=e.y+dy;
            changes.add(new World.CellChange(x,y,Material.STONE,reserved.contains(key(x,y))?Material.WATER:Material.AIR));
            changes.add(new World.CellChange(x,nextY+dy,world.get(x,nextY+dy),Material.STONE));
        }
        for(var target:destinations) if(!within(target.x(),target.y(),e.x,e.y)) changes.add(new World.CellChange(target.x(),target.y(),Material.AIR,Material.WATER));
        if(world.replaceAllIfMatches(changes)!=World.BatchResult.CHANGED) { e.reason=Reason.INVALID_OR_STALE_PLAN; return MoveResult.OBSTRUCTED; }
        byCell.remove(key(e.x,e.y)); e.y=nextY; e.state=State.FALLING; byCell.put(key(e.x,e.y),e);
        return MoveResult.MOVED;
    }
    private boolean hasSolidObstacle(int x,int y) {
        if(!validArea(x,y)) return true;
        for(int dy=0;dy<rockBlockSizeCells;dy++) for(int dx=0;dx<rockBlockSizeCells;dx++) {
            var material=world.get(x+dx,y+dy);
            if(material!=Material.AIR && material!=Material.WATER) return true;
        }
        return false;
    }
    private boolean isSupportSource(Entry e) {
        int y=e.y+rockBlockSizeCells;
        if(!validArea(e.x,y)) return true;
        for(int dy=0;dy<rockBlockSizeCells;dy++) for(int dx=0;dx<rockBlockSizeCells;dx++) {
            var m=world.get(e.x+dx,y+dy);
            if(m!=Material.AIR && m!=Material.WATER && m!=Material.STONE) return true;
        }
        return false;
    }
    private Entry neighbour(int x,int y) {
        return validArea(x,y)?byCell.get(key(x,y)):null;
    }
    private void relax(ArrayDeque<Entry> queue,Entry e,int distance,boolean vertical) {
        if(e==null || e.pinned || e.state==State.FALLING || distance>MAX_HORIZONTAL_SUPPORT_DISTANCE_CELLS || distance>=e.supportDistance) return;
        e.supportDistance=distance;
        if(vertical) queue.addFirst(e); else queue.addLast(e);
    }
    /** Repeated relaxation: upward propagation resets cost; horizontal steps add block size. */
    private void analyzeSupport(List<Entry> ordered) {
        long start=System.nanoTime(); int processed=0,contacts=0;
        var queue=new ArrayDeque<Entry>();
        for(var e:ordered) { e.supportDistance=Integer.MAX_VALUE; if(!e.pinned && e.state==State.STATIC && isSupportSource(e)) { e.supportDistance=0; queue.addLast(e); } }
        while(!queue.isEmpty()) {
            var e=queue.removeFirst(); processed++; int d=e.supportDistance,s=rockBlockSizeCells;
            var above=neighbour(e.x,e.y-s); var left=neighbour(e.x-s,e.y); var right=neighbour(e.x+s,e.y);
            if(above!=null) contacts++; if(left!=null) contacts++; if(right!=null) contacts++;
            relax(queue,above,0,true); relax(queue,left,d+s,false); relax(queue,right,d+s,false);
        }
        int supported=0,waiting=0;
        for(var e:ordered) { if(e.supportDistance!=Integer.MAX_VALUE) supported++; if(e.unsupportedTicks>0 && e.state==State.STATIC) waiting++; }
        supportDiagnostics=new SupportDiagnostics(supported,ordered.size()-supported,waiting,System.nanoTime()-start,processed,contacts);
    }
    /** One block-cell down per fixed tick, with bounded atomic water displacement. */
    public void step() {
        long start=System.nanoTime(); waterAttempts=moved=maxVisits=maxChecks=0;
        var ordered=new ArrayList<>(byCell.values()); ordered.sort(Comparator.<Entry>comparingInt(e->e.y).reversed().thenComparingLong(e->e.id));
        analyzeSupport(ordered);
        for(var e:ordered) {
            if(e.pinned) { e.state=State.STATIC; e.reason=Reason.PINNED; e.visits=e.checks=e.unsupportedTicks=0; continue; }
            if(e.state==State.STATIC && e.supportDistance!=Integer.MAX_VALUE) { e.unsupportedTicks=0; e.reason=Reason.SUPPORTED_BY_SOLID; e.visits=e.checks=0; continue; }
            int nextY=e.y+rockBlockSizeCells;
            if(e.state==State.STATIC && !hasSolidObstacle(e.x,nextY) && e.unsupportedTicks<FALL_DELAY_TICKS) {
                e.unsupportedTicks++; e.reason=Reason.WAITING_FOR_FALL; e.visits=e.checks=0; continue;
            }
            var result=move(e,nextY);
            if(result==MoveResult.MOVED) { moved++; e.unsupportedTicks=0; }
            if(result==MoveResult.OBSTRUCTED) obstructed++;
            else if(result==MoveResult.SUPPORTED) { e.state=State.STATIC; e.unsupportedTicks=0; }
        }
        ticks++; updateNanos=System.nanoTime()-start;
    }
}
