package miningsim.generation;

import miningsim.block.FixedBlockSession;
import miningsim.block.FixedBlockWorld;
import miningsim.world.Material;
import miningsim.world.World;

/** Constructs an isolated fixed-block session; no UI, clock or ambient RNG is involved. */
public final class WorldGenerator {
    private WorldGenerator() { }
    public record Generated(FixedBlockSession session,WorldGenerationConfig config,CaveGenerator.Stats caves) { }
    /** A canceled seed dialog prepares no scene; publication remains the caller's responsibility. */
    public static Generated prepare(WorldGenerationConfig config) { return config==null?null:generate(config); }
    private static double anchor(long seed,int index) {
        long z=seed+0x9e3779b97f4a7c15L*index;
        z=(z^(z>>>30))*0xbf58476d1ce4e5b9L; z=(z^(z>>>27))*0x94d049bb133111ebL; z^=z>>>31;
        return ((z>>>11)*0x1.0p-53)*2-1;
    }
    /** Upper terrain boundary at the aligned block column x, derived from seeded value noise. */
    public static int surface(WorldGenerationConfig c,int x) {
        if(x<0 || x>=c.width()) throw new IllegalArgumentException("Surface coordinate out of bounds");
        int column=(x/c.blockSize())*c.blockSize(); double pos=(column+c.blockSize()*0.5)/c.terrainScale();
        int i=(int)Math.floor(pos); double t=pos-i,blend=t*t*(3-2*t);
        double height=c.baseSurfaceHeight()+c.terrainAmplitude()*(anchor(c.seed(),i)*(1-blend)+anchor(c.seed(),i+1)*blend);
        int aligned=(int)Math.round(height/c.blockSize())*c.blockSize();
        int min=((c.soilDepth()+c.blockSize()+c.blockSize()-1)/c.blockSize())*c.blockSize();
        int max=c.height()-1-2*c.blockSize(); return Math.max(min,Math.min(max,aligned));
    }
    public record Layers(int surface,int soil,int sand,int gravel,int stoneTop) { }
    private static int layer(WorldGenerationConfig c,int x,long channel,int max,double threshold) {
        double position=(x+c.blockSize()*0.5)/(c.terrainScale()*(double)WorldGenerationConfig.LAYER_SCALE_MULTIPLIER);
        int i=(int)Math.floor(position); double t=position-i,b=t*t*(3-2*t);
        double v=anchor(c.seed()^channel,i)*(1-b)+anchor(c.seed()^channel,i+1)*b;
        double fraction=Math.max(0,(v-threshold)/(1-threshold));
        return (int)Math.round(max*fraction);
    }
    /** Upper and rock boundaries are aligned; loose-layer thicknesses are in cells. */
    public static Layers layers(WorldGenerationConfig c,int x) {
        int top=surface(c,x),column=(x/c.blockSize())*c.blockSize(),size=c.blockSize();
        int available=c.height()-1-2*size-top;
        int soil=Math.min(available,layer(c,column,0x612a9f17L,Math.min(WorldGenerationConfig.MAX_SOIL_DEPTH,c.soilDepth()*3),-0.2)); available-=soil;
        int sand=Math.min(available,layer(c,column,0x7a2f4b35L,WorldGenerationConfig.MAX_SAND_DEPTH,-0.2)); available-=sand;
        int gravel=Math.min(available,layer(c,column,0x4eacb905L,WorldGenerationConfig.MAX_GRAVEL_DEPTH,-0.2));
        int total=soil+sand+gravel,aligned=(total/size)*size,excess=total-aligned;
        int cut=Math.min(excess,gravel); gravel-=cut; excess-=cut;
        cut=Math.min(excess,sand); sand-=cut; excess-=cut; soil-=excess;
        return new Layers(top,soil,sand,gravel,top+aligned);
    }
    public static Generated generate(WorldGenerationConfig c) {
        java.util.Objects.requireNonNull(c);
        var world=new World(c.width(),c.height(),World.Mode.BEDROCK_ENABLED);
        var session=new FixedBlockSession(world,12345,c.blockSize());
        int size=c.blockSize();
        // Stable row-major block IDs, with an unbroken column down to BEDROCK.
        Layers[] layers=new Layers[c.width()/size];
        for(int col=0;col<layers.length;col++) layers[col]=layers(c,col*size);
        for(int y=0;y<c.height()-1;y+=size) for(int col=0;col<layers.length;col++)
            if(y>=layers[col].stoneTop() && !session.blocks().place(col*size,y,false)) throw new IllegalStateException("Terrain block placement failed");
        for(int x=0;x<c.width();x++) {
            var l=layers[x/size];
            for(int y=l.surface();y<l.stoneTop();y++) {
                Material material=y<l.surface()+l.soil()?Material.SOIL:y<l.surface()+l.soil()+l.sand()?Material.SAND:Material.GRAVEL;
                if(world.set(x,y,material)!=World.Mutation.CHANGED) throw new IllegalStateException("Terrain layer placement failed");
            }
        }
        var caves=CaveGenerator.carve(c,session.blocks());
        // Validate all ownership records before exposing the newly constructed session.
        new FixedBlockWorld(world,size).restoreState(session.blocks().saveState());
        return new Generated(session,c,caves);
    }
}
