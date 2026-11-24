package miningsim.block;

import java.util.ArrayDeque;
import miningsim.simulation.Simulation;
import miningsim.world.Material;
import miningsim.world.World;

/** Commands, block motion, then cellular simulation, all on one owner thread. */
public final class FixedBlockSession {
    private final World world;
    private final FixedBlockWorld blocks;
    private final Simulation simulation;
    private final ArrayDeque<Runnable> commands=new ArrayDeque<>();
    private long cellularTicks;
    public FixedBlockSession(World world,long seed) { this(world,seed,FixedBlockWorld.DEFAULT_BLOCK_SIZE); }
    public FixedBlockSession(World world,long seed,int rockBlockSizeCells) { this.world=world; blocks=new FixedBlockWorld(world,rockBlockSizeCells); simulation=new Simulation(world,seed); }
    public World world() { return world; }
    public FixedBlockWorld blocks() { return blocks; }
    public long cellularTicks() { return cellularTicks; }
    public void enqueue(Runnable command) { commands.addLast(java.util.Objects.requireNonNull(command)); }
    public void applyCommands() { while(!commands.isEmpty()) commands.removeFirst().run(); }
    /** Apply exactly the commands preceding this owner-thread barrier, without a physics tick. */
    public void applyCommandsAtSaveBarrier() {
        int count=commands.size();
        for(int i=0;i<count;i++) commands.removeFirst().run();
    }
    public boolean hasPendingCommands() { return !commands.isEmpty(); }
    public void tick() { applyCommands(); blocks.step(); simulation.step(); cellularTicks++; }
    /** Rasterize a stroke segment in grid coordinates; points outside the world are ignored. */
    public void blockStroke(int x0,int y0,int x1,int y1,boolean erase) {
        if(!world.contains(x0,y0) || !world.contains(x1,y1)) return;
        int x=blocks.snapX(x0),y=blocks.snapY(y0),endX=blocks.snapX(x1),endY=blocks.snapY(y1);
        int size=blocks.rockBlockSizeCells();
        int dx=Math.abs(endX-x)/size,dy=Math.abs(endY-y)/size,sx=x<endX?size:-size,sy=y<endY?size:-size,error=dx-dy;
        while(true) {
            if(erase) blocks.removeAt(x,y); else blocks.place(x,y,true);
            if(x==endX && y==endY) break;
            int twice=2*error; if(twice>-dy) { error-=dy; x+=sx; } if(twice<dx) { error+=dx; y+=sy; }
        }
    }
    public void paintCells(int cx,int cy,int radius,Material material) {
        if(!world.contains(cx,cy)) return;
        for(int dy=-radius;dy<=radius;dy++) for(int dx=-radius;dx<=radius;dx++) {
            int x=cx+dx,y=cy+dy;
            if(dx*dx+dy*dy<=radius*radius && world.contains(x,y) && blocks.blockAt(x,y)==null) world.set(x,y,material);
        }
    }
}
