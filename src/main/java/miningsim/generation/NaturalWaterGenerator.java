package miningsim.generation;

import java.util.Random;
import miningsim.world.Material;
import miningsim.world.World;

/** Initial water only. Coordinates are cells, with y increasing downward. */
public final class NaturalWaterGenerator {
    private static final long UNDERGROUND_CHANNEL=0x47d629e87c31b50fL;
    private static final long SURFACE_CHANNEL=0x1935a6b47f82dc0eL;
    private NaturalWaterGenerator() { }

    public record Stats(int caveSystems,int wetSystems,int undergroundCells,int lakes,int surfaceCells,long nanos) { }

    private static long mix(long v) {
        v=(v^(v>>>30))*0xbf58476d1ce4e5b9L;
        v=(v^(v>>>27))*0x94d049bb133111ebL;
        return v^(v>>>31);
    }

    /** Returns a generation diagnostic, not a persistent water model. */
    public static Stats generate(WorldGenerationConfig config,World world) {
        if(world.width()!=config.width() || world.height()!=config.height()) throw new IllegalArgumentException("World dimensions differ");
        long start=System.nanoTime();
        int width=world.width(),height=world.height(),cells=Math.multiplyExact(width,height);
        int[] surface=new int[width];
        for(int x=0;x<width;x++) surface[x]=WorldGenerator.layers(config,x).surface();
        boolean[] cave=new boolean[cells],seen=new boolean[cells];
        for(int y=0;y<height-1;y++) for(int x=0;x<width;x++)
            cave[y*width+x]=y>=surface[x] && world.get(x,y)==Material.AIR;
        int[] queue=new int[cells],component=new int[cells];
        var rng=new Random(mix(config.seed()^UNDERGROUND_CHANNEL));
        int systems=0,wet=0,underground=0;
        for(int p=0;p<cells;p++) if(cave[p] && !seen[p]) {
            int count=collect(p,cave,seen,queue,width,height,0);
            if(count<8) continue;
            System.arraycopy(queue,0,component,0,count);
            systems++;
            int top=height,bottom=0; long sx=0,depth=0;
            for(int i=0;i<count;i++) { int q=component[i],x=q%width,y=q/width; top=Math.min(top,y); bottom=Math.max(bottom,y); sx+=x; depth+=y-surface[x]; }
            int center=(int)(sx/count);
            double profile=config.baseSurfaceHeight()+(height-1-config.baseSurfaceHeight())*.43
                    +Math.sin((center/(double)Math.max(1,width))*Math.PI*2+(mix(config.seed()^UNDERGROUND_CHANNEL)>>>11)*0x1.0p-53*Math.PI*2)*Math.min(24,height*.06);
            double probability=Math.max(.18,Math.min(.78,.34+(bottom-profile)/Math.max(1.0,height)*.7+(depth/(double)count)/Math.max(1.0,height)*.25));
            if(rng.nextDouble()>=probability) continue;
            double fill=rng.nextDouble();
            double fraction=Math.max(.08,Math.min(.95,.12+.78*rng.nextDouble()+(bottom-profile)/Math.max(1.0,height)*.25));
            int level=fill<.12?top:bottom-(int)Math.round((bottom-top)*fraction);
            // Partition below the horizontal level: elevated passages cannot connect reservoirs.
            boolean[] eligible=new boolean[count]; // marker for the component's candidate cells
            for(int i=0;i<count;i++) if(component[i]/width>=level) { seen[component[i]]=false; eligible[i]=true; }
            int added=0;
            for(int i=0;i<count;i++) if(eligible[i] && !seen[component[i]]) {
                int part=collect(component[i],cave,seen,queue,width,height,level);
                if(part<8) continue;
                // A high passage does not share a reservoir with the first pocket.
                if(added>0 && rng.nextDouble()>=.65) continue;
                for(int j=0;j<part;j++) { int q=queue[j]; putWater(world,q%width,q/width); }
                added+=part;
            }
            if(added>0) { wet++; underground+=added; }
        }
        int[] surfaceResult=surfaceLakes(world,surface,config.seed());
        return new Stats(systems,wet,underground,surfaceResult[0],surfaceResult[1],System.nanoTime()-start);
    }

    /** Profiles contain the first solid row; the two sides must both provide terrain barriers. */
    static int[] surfaceLakes(World world,int[] surface,long seed) {
        int width=world.width(),height=world.height();
        if(surface.length!=width) throw new IllegalArgumentException("Surface width differs");
        var surfaceRng=new Random(mix(seed^SURFACE_CHANNEL));
        int[] left=new int[width],right=new int[width];
        int best=height;
        for(int x=0;x<width;x++) { left[x]=best; best=Math.min(best,surface[x]); }
        best=height;
        for(int x=width-1;x>=0;x--) { right[x]=best; best=Math.min(best,surface[x]); }
        int lakes=0,surfaceCells=0;
        for(int x=1;x<width-1;) {
            int spill=Math.max(left[x],right[x]);
            if(spill>=surface[x]) { x++; continue; }
            int startX=x,end=x+1;
            while(end<width-1 && Math.max(left[end],right[end])==spill && surface[end]>spill) end++;
            if(end-startX>=3 && surfaceRng.nextDouble()<.55) {
                int deepest=spill;
                for(int k=startX;k<end;k++) deepest=Math.max(deepest,surface[k]);
                int level=spill+surfaceRng.nextInt(Math.max(1,(deepest-spill)/2+1));
                int added=0;
                for(int k=startX;k<end;k++) for(int y=level;y<surface[k];y++) {
                    if(world.get(k,y)==Material.AIR) { putWater(world,k,y); added++; }
                }
                if(added>0) { lakes++; surfaceCells+=added; }
            }
            x=end;
        }
        return new int[]{lakes,surfaceCells};
    }

    private static void putWater(World world,int x,int y) {
        if(world.get(x,y)!=Material.AIR || world.set(x,y,Material.WATER)!=World.Mutation.CHANGED)
            throw new IllegalStateException("Water must replace AIR");
    }

    static int collect(int start,boolean[] cave,boolean[] seen,int[] queue,int width,int height,int level) {
        int head=0,tail=0; queue[tail++]=start; seen[start]=true;
        while(head<tail) {
            int p=queue[head++],x=p%width,y=p/width;
            if(x>0) tail=visit(p-1,cave,seen,queue,tail,width,level);
            if(x+1<width) tail=visit(p+1,cave,seen,queue,tail,width,level);
            if(y>level) tail=visit(p-width,cave,seen,queue,tail,width,level);
            if(y+1<height) tail=visit(p+width,cave,seen,queue,tail,width,level);
        }
        return tail;
    }
    private static int visit(int p,boolean[] cave,boolean[] seen,int[] queue,int tail,int width,int level) {
        if(p/width>=level && cave[p] && !seen[p]) { seen[p]=true; queue[tail++]=p; }
        return tail;
    }
}
