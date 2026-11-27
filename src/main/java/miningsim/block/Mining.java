package miningsim.block;

import java.util.HashMap;
import java.util.Map;
import miningsim.world.Material;

/** Session-local, ID-keyed work; no physical ownership or persistence changes. */
public final class Mining {
    public static final int WORK_PER_TICK=2;
    public record Progress(long blockId, int requiredWork, int accumulatedWork) {
        public boolean ready() { return accumulatedWork>=requiredWork; }
    }
    private final FixedBlockWorld blocks;
    private final miningsim.world.World world;
    private final Map<Long,Integer> work=new HashMap<>();
    private boolean active;
    private int x=-1,y=-1;
    Mining(FixedBlockWorld blocks,miningsim.world.World world) {
        this.blocks=blocks; this.world=world; blocks.onRemoval(work::remove);
    }
    public static int requiredWork(Material material,int size) {
        int base=switch(material) {
            case LIMESTONE -> 60; case STONE -> 120; case MARBLE -> 180; case GRANITE -> 240;
            default -> throw new IllegalArgumentException("Not mineable rock");
        };
        if(size!=4 && size!=8) throw new IllegalArgumentException("Invalid block size");
        return base*size*size/16;
    }
    public void select(boolean active,int x,int y) { this.active=active; this.x=x; this.y=y; }
    public void stop() { active=false; }
    /** Read on the session owner thread; absent IDs have no accumulated work. */
    public int accumulatedWork(long blockId) { return work.getOrDefault(blockId,0); }
    public Progress progressAt(int x,int y) {
        var b=blocks.blockAt(x,y); if(b==null) return null;
        return new Progress(b.id(),requiredWork(world.get(x,y),blocks.rockBlockSizeCells()),work.getOrDefault(b.id(),0));
    }
    void tick() {
        if(!active || !blocks.miningEligibleAt(x,y)) return;
        var p=progressAt(x,y);
        if(p!=null && !p.ready()) work.put(p.blockId(),Math.min(p.requiredWork(),p.accumulatedWork()+WORK_PER_TICK));
    }
}
