package miningsim.benchmark;

import java.util.Arrays;
import miningsim.block.FixedBlockWorld;
import miningsim.generation.WorldGenerationConfig;
import miningsim.generation.WorldGenerator;
import miningsim.world.Material;

/** Fixed bounded sample; prints per-world results so totals can be independently checked. */
public final class NaturalWaterCalibration {
    public static void main(String[] args) {
        int[] seeds={0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,431039,357355,12345};
        for(int size:new int[]{8,4}) for(int seed:seeds) {
            var c=WorldGenerationConfig.preset(seed,size); var g=WorldGenerator.generate(c); var s=g.session(); var w=s.world();
            int width=w.width(),height=w.height(),n=width*height; boolean[] seen=new boolean[n]; int[] queue=new int[n];
            int dry=0,partial=0,full=0,largestWater=0; int[] depth=new int[3];
            for(int p=0;p<n;p++) {
                int x=p%width,y=p/width; var m=w.get(x,y);
                if(m==Material.WATER && y>=WorldGenerator.layers(c,x).surface()) depth[Math.min(2,y*3/height)]++;
                if(seen[p] || y<WorldGenerator.layers(c,x).stoneTop() || (m!=Material.AIR && m!=Material.WATER)) continue;
                int head=0,tail=0,water=0; queue[tail++]=p; seen[p]=true;
                while(head<tail) {
                    int q=queue[head++],qx=q%width,qy=q/width; if(w.get(qx,qy)==Material.WATER) water++;
                    if(qx>0) tail=visit(q-1,c,w,seen,queue,tail);
                    if(qx+1<width) tail=visit(q+1,c,w,seen,queue,tail);
                    if(qy>0) tail=visit(q-width,c,w,seen,queue,tail);
                    if(qy+1<height) tail=visit(q+width,c,w,seen,queue,tail);
                }
                if(water==0) dry++; else if(water==tail) full++; else partial++;
                largestWater=Math.max(largestWater,water);
            }
            int[] largest=largestWaterBodies(c,w);
            System.out.printf("size=%d seed=%d dry=%d partial=%d full=%d lakes=%d underground=%d surface=%d largestSystemWater=%d largestBodies=%s depthThirds=%s%n",size,seed,dry,partial,full,g.water().lakes(),g.water().undergroundCells(),g.water().surfaceCells(),largestWater,Arrays.toString(largest),Arrays.toString(depth));
            if(seed==5 || seed==8 || seed==12345 || seed==357355 || seed==431039) {
                long[] before=counts(s); var ids=s.blocks().blocks().stream().map(FixedBlockWorld.Block::id).sorted().toList();
                for(int tick=0;tick<600;tick++) s.tick();
                if(!Arrays.equals(before,counts(s))) throw new IllegalStateException("Material conservation failed");
                if(!ids.equals(s.blocks().blocks().stream().map(FixedBlockWorld.Block::id).sorted().toList())) throw new IllegalStateException("Block identity changed");
                new FixedBlockWorld(w,size).restoreState(s.blocks().saveState());
                System.out.printf("size=%d seed=%d ticks=600 conservation=ok ownership=ok%n",size,seed);
            }
        }
    }
    private static int[] largestWaterBodies(WorldGenerationConfig c,miningsim.world.World w) {
        int width=w.width(),height=w.height(); boolean[] seen=new boolean[width*height]; int[] q=new int[seen.length],largest=new int[2];
        for(int p=0;p<seen.length;p++) if(!seen[p] && w.get(p%width,p/width)==Material.WATER) {
            int head=0,tail=0; q[tail++]=p; seen[p]=true;
            while(head<tail) { int v=q[head++],x=v%width,y=v/width;
                if(x>0) tail=visitWater(v-1,w,seen,q,tail);
                if(x+1<width) tail=visitWater(v+1,w,seen,q,tail);
                if(y>0) tail=visitWater(v-width,w,seen,q,tail);
                if(y+1<height) tail=visitWater(v+width,w,seen,q,tail);
            }
            int kind=p/width>=WorldGenerator.layers(c,p%width).surface()?0:1; largest[kind]=Math.max(largest[kind],tail);
        }
        return largest;
    }
    private static int visitWater(int p,miningsim.world.World w,boolean[] seen,int[] q,int tail) {
        if(!seen[p] && w.get(p%w.width(),p/w.width())==Material.WATER) { seen[p]=true; q[tail++]=p; }
        return tail;
    }
    private static int visit(int p,WorldGenerationConfig c,miningsim.world.World w,boolean[] seen,int[] q,int tail) {
        int x=p%w.width(),y=p/w.width(); var m=w.get(x,y);
        if(!seen[p] && y>=WorldGenerator.layers(c,x).stoneTop() && (m==Material.AIR || m==Material.WATER)) { seen[p]=true; q[tail++]=p; }
        return tail;
    }
    private static long[] counts(miningsim.block.FixedBlockSession s) {
        var counts=new long[Material.values().length]; var w=s.world();
        for(int y=0;y<w.height();y++) for(int x=0;x<w.width();x++) counts[Byte.toUnsignedInt(w.get(x,y).id())]++;
        return counts;
    }
}
