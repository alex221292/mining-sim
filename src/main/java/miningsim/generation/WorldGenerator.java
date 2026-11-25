package miningsim.generation;

import miningsim.block.FixedBlockSession;
import miningsim.block.FixedBlockWorld;
import miningsim.world.Material;
import miningsim.world.World;

/** Constructs an isolated fixed-block session; no UI, clock or ambient RNG is involved. */
public final class WorldGenerator {
    private WorldGenerator() { }
    public record Generated(FixedBlockSession session,WorldGenerationConfig config) { }
    /** A canceled seed dialog prepares no scene; publication remains the caller's responsibility. */
    public static Generated prepare(WorldGenerationConfig config) { return config==null?null:generate(config); }
    private static double anchor(long seed,int index) {
        long z=seed+0x9e3779b97f4a7c15L*index;
        z=(z^(z>>>30))*0xbf58476d1ce4e5b9L; z=(z^(z>>>27))*0x94d049bb133111ebL; z^=z>>>31;
        return ((z>>>11)*0x1.0p-53)*2-1;
    }
    /** First stone row at the aligned block column x, derived from smooth seeded value noise. */
    public static int surface(WorldGenerationConfig c,int x) {
        if(x<0 || x>=c.width()) throw new IllegalArgumentException("Surface coordinate out of bounds");
        int column=(x/c.blockSize())*c.blockSize(); double pos=(column+c.blockSize()*0.5)/c.terrainScale();
        int i=(int)Math.floor(pos); double t=pos-i,blend=t*t*(3-2*t);
        double height=c.baseSurfaceHeight()+c.terrainAmplitude()*(anchor(c.seed(),i)*(1-blend)+anchor(c.seed(),i+1)*blend);
        int aligned=(int)Math.round(height/c.blockSize())*c.blockSize();
        int min=((c.soilDepth()+c.blockSize()+c.blockSize()-1)/c.blockSize())*c.blockSize();
        int max=c.height()-1-2*c.blockSize(); return Math.max(min,Math.min(max,aligned));
    }
    public static Generated generate(WorldGenerationConfig c) {
        java.util.Objects.requireNonNull(c);
        var world=new World(c.width(),c.height(),World.Mode.BEDROCK_ENABLED);
        var session=new FixedBlockSession(world,12345,c.blockSize());
        int size=c.blockSize();
        // Stable row-major block IDs, with an unbroken column down to BEDROCK.
        int[] surface=new int[c.width()/size];
        for(int col=0;col<surface.length;col++) surface[col]=surface(c,col*size);
        for(int y=0;y<c.height()-1;y+=size) for(int col=0;col<surface.length;col++)
            if(y>=surface[col] && !session.blocks().place(col*size,y,false)) throw new IllegalStateException("Terrain block placement failed");
        for(int x=0;x<c.width();x++) for(int y=surface[x/size]-c.soilDepth();y<surface[x/size];y++)
            if(world.set(x,y,Material.SOIL)!=World.Mutation.CHANGED) throw new IllegalStateException("Terrain soil placement failed");
        // Validate all ownership records before exposing the newly constructed session.
        new FixedBlockWorld(world,size).restoreState(session.blocks().saveState());
        return new Generated(session,c);
    }
}
