package miningsim.block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import miningsim.resource.SubstanceId;
import miningsim.resource.SubstanceRegistry;
import miningsim.world.Material;
import miningsim.world.World;

/** Single-owner grid blocks. World STONE is the block's cellular representation, not extra matter. */
public final class FixedBlockWorld {
    public static final int BLOCK_SIZE=8;
    public enum State { STATIC, FALLING }
    public record Block(long id,int x,int y,State state,SubstanceId substance) { }
    private static final class Entry {
        final long id; final SubstanceId substance; int x,y; State state=State.STATIC; boolean pinned;
        Entry(long id,int x,int y,boolean pinned) { this.id=id; this.x=x; this.y=y; this.pinned=pinned; substance=SubstanceRegistry.STONE; }
        Block snapshot() { return new Block(id,x,y,state,substance); }
    }
    private final World world;
    private final int yOffset;
    private final Map<Long,Entry> byCell=new HashMap<>();
    private long nextId=1,rejected,updateNanos,ticks;
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
    /** One block-cell down per fixed tick; all non-AIR material is a conservative obstacle. */
    public void step() {
        long start=System.nanoTime();
        var ordered=new ArrayList<>(byCell.values()); ordered.sort(Comparator.<Entry>comparingInt(e->e.y).reversed().thenComparingLong(e->e.id));
        for(var e:ordered) {
            if(e.pinned) { e.state=State.STATIC; continue; }
            int nextY=e.y+BLOCK_SIZE;
            if(!empty(e.x,nextY)) { e.state=State.STATIC; continue; }
            var changes=new ArrayList<World.CellChange>(128); append(changes,e.x,e.y,Material.STONE,Material.AIR); append(changes,e.x,nextY,Material.AIR,Material.STONE);
            if(world.replaceAllIfMatches(changes)!=World.BatchResult.CHANGED) throw new IllegalStateException("Block move rejected");
            byCell.remove(key(e.x,e.y)); e.y=nextY; e.state=State.FALLING; byCell.put(key(e.x,e.y),e);
        }
        ticks++; updateNanos=System.nanoTime()-start;
    }
}
