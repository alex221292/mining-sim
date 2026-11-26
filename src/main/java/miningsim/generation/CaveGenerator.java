package miningsim.generation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import miningsim.block.FixedBlockWorld;

/** Bounded independent-seed candidate generator; no persistent cave objects. */
public final class CaveGenerator {
    public static final int MAX_CANDIDATES=20, MARGIN_CELLS=16;
    private static final long CAVE_CHANNEL=0x62a9d4b6e3815f07L;
    public record Stats(int candidates,int accepted,int geometryRejected,int supportRejected,int blocksRemoved,long candidateNanos,long supportNanos) { }
    private CaveGenerator() { }
    public static Stats carve(WorldGenerationConfig config,FixedBlockWorld blocks) {
        int size=config.blockSize(), cols=config.width()/size, rows=(config.height()-1)/size;
        int margin=(MARGIN_CELLS+size-1)/size;
        if(cols<=2*margin+4 || rows<=2*margin+4) return new Stats(0,0,0,0,0,0,0);
        var random=new Random(config.seed()^CAVE_CHANNEL); int accepted=0,geometry=0,unstable=0,removed=0; long candidateTime=0,supportTime=0;
        for(int i=0;i<MAX_CANDIDATES;i++) {
            long start=System.nanoTime();
            int cx=margin+random.nextInt(cols-2*margin),cy=margin+random.nextInt(rows-2*margin);
            int rx=Math.max(1,(12+random.nextInt(37))/size),ry=Math.max(1,(8+random.nextInt(17))/size);
            var positions=new ArrayList<FixedBlockWorld.BlockPosition>(); boolean invalid=false;
            for(int y=cy-ry;y<=cy+ry;y++) for(int x=cx-rx;x<=cx+rx;x++) {
                double dx=(double)(x-cx)/rx,dy=(double)(y-cy)/ry; if(dx*dx+dy*dy>1) continue;
                int wx=x*size,wy=y*size;
                if(x<margin || x>=cols-margin || y>=rows-margin || y<0 || wy<WorldGenerator.layers(config,Math.max(0,Math.min(config.width()-1,wx))).stoneTop()+MARGIN_CELLS || blocks.blockAt(wx,wy)==null) { invalid=true; continue; }
                positions.add(new FixedBlockWorld.BlockPosition(wx,wy));
            }
            // Require at least two orthogonally connected block positions; avoid touching prior cavities.
            if(positions.size()<2) invalid=true;
            for(var p:positions) for(int[] d:new int[][]{{-size,0},{size,0},{0,-size},{0,size}}) {
                int x=p.x()+d[0],y=p.y()+d[1];
                if(x>=0 && x<config.width() && y>=0 && y<config.height()-1 && y>=WorldGenerator.layers(config,x).stoneTop()+MARGIN_CELLS && blocks.blockAt(x,y)==null) invalid=true;
            }
            candidateTime+=System.nanoTime()-start;
            if(invalid) { geometry++; continue; }
            start=System.nanoTime(); boolean supported=blocks.supportedAfterRemoving(positions); supportTime+=System.nanoTime()-start;
            if(!supported) { unstable++; continue; }
            for(var p:positions) if(!blocks.removeAt(p.x(),p.y())) throw new IllegalStateException("Cave ownership changed");
            removed+=positions.size(); accepted++;
        }
        long start=System.nanoTime(); boolean finalSupported=blocks.supportedAfterRemoving(List.of()); supportTime+=System.nanoTime()-start;
        if(!finalSupported) throw new IllegalStateException("Generated caves lack structural support");
        return new Stats(MAX_CANDIDATES,accepted,geometry,unstable,removed,candidateTime,supportTime);
    }
}
