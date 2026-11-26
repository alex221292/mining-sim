package miningsim.benchmark;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import miningsim.generation.WorldGenerationConfig;
import miningsim.generation.WorldGenerator;
import miningsim.world.Material;

/** Initial and physical cave-space retention; seeds 0..31,431039,357355,12345. */
public final class CaveSettlingBenchmark {
    private static String retained(FixedBlockSessionView view,boolean[] initial,int cols,int size) {
        int width=view.session.world().width(),height=view.session.world().height(),count=0,components=0,largest=0;
        boolean[] cells=new boolean[width*height];
        for(int p=0;p<initial.length;p++) if(initial[p]) for(int dy=0;dy<size;dy++) for(int dx=0;dx<size;dx++) {
            int x=(p%cols)*size+dx,y=(p/cols)*size+dy; if(view.session.world().get(x,y)==Material.AIR) { cells[y*width+x]=true; count++; }
        }
        int[] queue=new int[cells.length];
        for(int p=0;p<cells.length;p++) if(cells[p]) {
            components++; int head=0,tail=0; queue[tail++]=p; cells[p]=false;
            while(head<tail) { int cell=queue[head++],x=cell%width,y=cell/width;
                if(x>0 && cells[cell-1]) { cells[cell-1]=false; queue[tail++]=cell-1; }
                if(x+1<width && cells[cell+1]) { cells[cell+1]=false; queue[tail++]=cell+1; }
                if(y>0 && cells[cell-width]) { cells[cell-width]=false; queue[tail++]=cell-width; }
                if(y+1<height && cells[cell+width]) { cells[cell+width]=false; queue[tail++]=cell+width; }
            }
            largest=Math.max(largest,tail);
        }
        return String.format(Locale.ROOT,"cells=%d components=%d largest=%d",count,components,largest);
    }
    private record FixedBlockSessionView(miningsim.block.FixedBlockSession session) { }
    public static void main(String[] args) {
        int ticks=args.length==0?600:Integer.parseInt(args[0]);
        for(int size:new int[]{8,4}) for(int index=0;index<35;index++) {
            int seed=index<32?index:index==32?431039:index==33?357355:12345;
            var c=WorldGenerationConfig.preset(seed,size); var g=WorldGenerator.generate(c); var s=g.session();
            int cols=c.width()/size,rows=(c.height()-1)/size; boolean[] cave=new boolean[cols*rows]; int area=0;
            for(int y=0;y<rows;y++) for(int x=0;x<cols;x++) if(y*size>=WorldGenerator.layers(c,x*size).stoneTop() && s.world().get(x*size,y*size)==Material.AIR) { cave[y*cols+x]=true; area++; }
            var original=new HashMap<Long,Integer>(); for(var b:s.blocks().blocks()) original.put(b.id(),b.y());
            var falling=new HashSet<Long>(); long totalNanos=0,maxNanos=0; int at=0;
            System.out.printf(Locale.ROOT,"size=%d seed=%d rockBlocks=%d areaBlocks=%d initial[%s] branchCandidates=%d initialUnsupported=%s",size,seed,original.size(),area,retained(new FixedBlockSessionView(s),cave,cols,size),g.caves().branchedSystems(),!s.blocks().supportedAfterRemoving(java.util.List.of()));
            int limit=Math.max(ticks,1200);
            for(int tick=1;tick<=limit;tick++) {
                long start=System.nanoTime(); s.tick(); long elapsed=System.nanoTime()-start; totalNanos+=elapsed; maxNanos=Math.max(maxNanos,elapsed);
                for(var b:s.blocks().blocks()) if(b.state()==miningsim.block.FixedBlockWorld.State.FALLING) falling.add(b.id());
                if(tick==1) System.out.printf(" initialUnsupportedCount=%d",s.blocks().supportDiagnostics().unsupported());
                if(tick==60 || tick==300 || tick==600 || tick==limit) {
                    int retained=0,moved=0,active=0,maxDisplacement=0; long displacement=0; for(int p=0;p<cave.length;p++) if(cave[p] && s.world().get((p%cols)*size,(p/cols)*size)==Material.AIR) retained++;
                    for(var b:s.blocks().blocks()) { int delta=Math.abs(original.get(b.id())-b.y()); if(delta!=0) moved++; displacement+=delta; maxDisplacement=Math.max(maxDisplacement,delta); if(b.state()==miningsim.block.FixedBlockWorld.State.FALLING) active++; }
                    System.out.printf(Locale.ROOT," t%d[retainedBlocks=%d %s moved=%d displacementCells=%d maxDisplacement=%d everFalling=%d active=%d unsupported=%d]",tick,retained,retained(new FixedBlockSessionView(s),cave,cols,size),moved,displacement,maxDisplacement,falling.size(),active,s.blocks().supportDiagnostics().unsupported()); at=tick;
                    if(tick>=ticks && active==0 && s.blocks().supportDiagnostics().waiting()==0 && s.blocks().supportDiagnostics().unsupported()==0) break;
                }
            }
            System.out.printf(Locale.ROOT," ticks=%d meanMs=%.3f maxMs=%.3f%n",at,totalNanos/1e6/Math.max(1,at),maxNanos/1e6);
        }
    }
}
