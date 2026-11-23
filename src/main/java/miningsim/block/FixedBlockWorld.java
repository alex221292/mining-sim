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
    public static final int BLOCK_SIZE=8;
    public static final int WATER_SEARCH_RADIUS=32;
    public static final int WATER_MAX_VISITS=8192;
    public static final int WATER_MAX_CHECKS=32768;
    public enum State { STATIC, FALLING }
    public enum MoveResult { MOVED, SUPPORTED, OBSTRUCTED }
    public record Block(long id,int x,int y,State state,SubstanceId substance) { }
    private static final class Entry {
        final long id; final SubstanceId substance; int x,y; State state=State.STATIC; boolean pinned;
        Entry(long id,int x,int y,boolean pinned) { this.id=id; this.x=x; this.y=y; this.pinned=pinned; substance=SubstanceRegistry.STONE; }
        Block snapshot() { return new Block(id,x,y,state,substance); }
    }
    private final World world;
    private final int yOffset;
    private final Map<Long,Entry> byCell=new HashMap<>();
    private long nextId=1,rejected,updateNanos,ticks,obstructed;
    public FixedBlockWorld(World world) {
        this.world=java.util.Objects.requireNonNull(world);
        if(world.mode()!=World.Mode.BEDROCK_ENABLED || world.hasChangeSink()) throw new IllegalArgumentException("Fixed blocks require an independent bedrock world");
        yOffset=Math.floorMod(world.height()-1,BLOCK_SIZE);
    }
    public int yOffset() { return yOffset; }
    private long key(int x,int y) { return (long)y*world.width()+x; }
    public int snapX(int x) { return Math.floorDiv(x,BLOCK_SIZE)*BLOCK_SIZE; }
    public int snapY(int y) { return Math.floorDiv(y-yOffset,BLOCK_SIZE)*BLOCK_SIZE+yOffset; }
    public int size() { return byCell.size(); }
    public long rejected() { return rejected; }
    public long updateNanos() { return updateNanos; }
    public long ticks() { return ticks; }
    public long obstructed() { return obstructed; }
    public List<Block> blocks() { return byCell.values().stream().sorted(Comparator.comparingLong(e->e.id)).map(Entry::snapshot).toList(); }
    public Block blockAt(int x,int y) { if(!world.contains(x,y)) return null; var e=byCell.get(key(snapX(x),snapY(y))); return e==null?null:e.snapshot(); }
    private boolean validArea(int x,int y) { return x>=0 && y>=0 && x<=world.width()-BLOCK_SIZE && y<=world.height()-BLOCK_SIZE; }
    private boolean empty(int x,int y) {
        if(!validArea(x,y)) return false;
        for(int dy=0;dy<BLOCK_SIZE;dy++) for(int dx=0;dx<BLOCK_SIZE;dx++) if(world.get(x+dx,y+dy)!=Material.AIR) return false;
        return true;
    }
    private void append(List<World.CellChange> changes,int x,int y,Material from,Material to) {
        for(int dy=0;dy<BLOCK_SIZE;dy++) for(int dx=0;dx<BLOCK_SIZE;dx++) changes.add(new World.CellChange(x+dx,y+dy,from,to));
    }
    public boolean place(int x,int y,boolean pinned) {
        if(!world.contains(x,y)) { rejected++; return false; }
        x=snapX(x); y=snapY(y); if(byCell.containsKey(key(x,y))) return false;
        if(!empty(x,y)) { rejected++; return false; }
        long id=nextId,next=Math.incrementExact(nextId);
        var changes=new ArrayList<World.CellChange>(64); append(changes,x,y,Material.AIR,Material.STONE);
        if(world.replaceAllIfMatches(changes)!=World.BatchResult.CHANGED) { rejected++; return false; }
        byCell.put(key(x,y),new Entry(id,x,y,pinned)); nextId=next; return true;
    }
    public boolean removeAt(int x,int y) {
        if(!world.contains(x,y)) return false;
        var e=byCell.get(key(snapX(x),snapY(y))); if(e==null) return false;
        var changes=new ArrayList<World.CellChange>(64); append(changes,e.x,e.y,Material.STONE,Material.AIR);
        if(world.replaceAllIfMatches(changes)!=World.BatchResult.CHANGED) throw new IllegalStateException("Block ownership mismatch");
        byCell.remove(key(e.x,e.y)); return true;
    }
    public void releaseStroke() { for(var e:byCell.values()) e.pinned=false; }
    private record SearchCell(int x,int y,int distance) { }
    private static final class Work { int visits,checks; }
    private boolean within(int x,int y,int left,int top) { return x>=left && x<left+BLOCK_SIZE && y>=top && y<top+BLOCK_SIZE; }
    private List<SearchCell> findWaterDestinations(List<SearchCell> sources,Entry block,Work work) {
        var queue=new ArrayDeque<SearchCell>(); var seen=new HashSet<Long>();
        for(var source:sources) { queue.add(source); seen.add(key(source.x(),source.y())); }
        var destinations=new ArrayList<SearchCell>();
        int[][] directions={{-1,0},{1,0},{0,-1},{0,1}};
        while(!queue.isEmpty()) {
            var current=queue.removeFirst(); if(++work.visits>WATER_MAX_VISITS) return null;
            if(current.distance()>=WATER_SEARCH_RADIUS) continue;
            for(var d:directions) {
                if(++work.checks>WATER_MAX_CHECKS) return null;
                int nx=current.x()+d[0],ny=current.y()+d[1];
                if(!world.contains(nx,ny) || !seen.add(key(nx,ny))) continue;
                boolean destination=within(nx,ny,block.x,block.y+BLOCK_SIZE);
                var material=world.get(nx,ny);
                if(material!=Material.AIR && material!=Material.WATER) continue;
                var next=new SearchCell(nx,ny,current.distance()+1);
                if(!destination && material==Material.AIR) {
                    destinations.add(next);
                    if(destinations.size()==sources.size()) return destinations;
                }
                queue.addLast(next);
            }
        }
        return null;
    }
    private MoveResult move(Entry e,int nextY) {
        if(!validArea(e.x,nextY)) return MoveResult.SUPPORTED;
        if(world.hasChangeSink()) return MoveResult.OBSTRUCTED;
        var water=new ArrayList<SearchCell>();
        for(int dy=0;dy<BLOCK_SIZE;dy++) for(int dx=0;dx<BLOCK_SIZE;dx++)
            if(world.get(e.x+dx,e.y+dy)!=Material.STONE) throw new IllegalStateException("Block ownership mismatch for "+e.id);
        for(int dy=0;dy<BLOCK_SIZE;dy++) for(int dx=0;dx<BLOCK_SIZE;dx++) {
            var material=world.get(e.x+dx,nextY+dy);
            if(material==Material.WATER) water.add(new SearchCell(e.x+dx,nextY+dy,0));
            else if(material!=Material.AIR) return MoveResult.SUPPORTED;
        }
        List<SearchCell> destinations=water.isEmpty()?List.of():findWaterDestinations(water,e,new Work());
        if(destinations==null) return MoveResult.OBSTRUCTED;
        var changes=new ArrayList<World.CellChange>(128+destinations.size());
        for(int dy=0;dy<BLOCK_SIZE;dy++) for(int dx=0;dx<BLOCK_SIZE;dx++) {
            int x=e.x+dx,y=e.y+dy;
            changes.add(new World.CellChange(x,y,Material.STONE,Material.AIR));
            changes.add(new World.CellChange(x,nextY+dy,world.get(x,nextY+dy),Material.STONE));
        }
        for(var target:destinations) if(!within(target.x(),target.y(),e.x,e.y)) changes.add(new World.CellChange(target.x(),target.y(),Material.AIR,Material.WATER));
        if(world.replaceAllIfMatches(changes)!=World.BatchResult.CHANGED) return MoveResult.OBSTRUCTED;
        byCell.remove(key(e.x,e.y)); e.y=nextY; e.state=State.FALLING; byCell.put(key(e.x,e.y),e);
        return MoveResult.MOVED;
    }
    /** One block-cell down per fixed tick, with bounded atomic water displacement. */
    public void step() {
        long start=System.nanoTime();
        var ordered=new ArrayList<>(byCell.values()); ordered.sort(Comparator.<Entry>comparingInt(e->e.y).reversed().thenComparingLong(e->e.id));
        for(var e:ordered) {
            if(e.pinned) { e.state=State.STATIC; continue; }
            int nextY=e.y+BLOCK_SIZE;
            var result=move(e,nextY);
            if(result==MoveResult.OBSTRUCTED) obstructed++;
            else if(result==MoveResult.SUPPORTED) e.state=State.STATIC;
        }
        ticks++; updateNanos=System.nanoTime()-start;
    }
}
