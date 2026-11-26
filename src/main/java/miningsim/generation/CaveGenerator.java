package miningsim.generation;

import java.util.ArrayList;
import java.util.Random;
import miningsim.block.FixedBlockWorld;

/** Bounded seeded branching walks; cave cells transfer from the fixed-block registry to AIR. */
public final class CaveGenerator {
    public static final int MAX_CANDIDATES=8, MARGIN_CELLS=16;
    private static final int MAX_BRANCHES=3, MAX_STEPS=48;
    private static final long CAVE_CHANNEL=0x62a9d4b6e3815f07L;
    public record Stats(int candidates,int accepted,int geometryRejected,int supportRejected,int blocksRemoved,long candidateNanos,long supportNanos,int branchedSystems) { }
    private CaveGenerator() { }
    private static long mix(long value) {
        value=(value^(value>>>30))*0xbf58476d1ce4e5b9L;
        value=(value^(value>>>27))*0x94d049bb133111ebL;
        return value^(value>>>31);
    }
    private static double[][] walk(WorldGenerationConfig c,boolean[] mask,Random rng,double startX,double startY,double angle,int steps) {
        int size=c.blockSize(),cols=c.width()/size,rows=(c.height()-1)/size;
        double x=startX,y=startY,heading=angle,radius=10+rng.nextDouble()*5; double[][] path=new double[steps][3];
        for(int i=0;i<steps;i++) {
            heading+=(rng.nextDouble()-.5)*.5; radius=Math.max(8,Math.min(17,radius+(rng.nextDouble()-.5)*4));
            path[i][0]=x; path[i][1]=y; path[i][2]=heading;
            if(i==steps/2 && rng.nextInt(3)==0) radius+=7;
            int reach=(int)Math.ceil(radius/size)+1, bx=(int)Math.round(x/size),by=(int)Math.round(y/size);
            for(int dy=-reach;dy<=reach;dy++) for(int dx=-reach;dx<=reach;dx++) {
                int col=bx+dx,row=by+dy; if(col<0 || col>=cols || row<0 || row>=rows) continue;
                int wx=col*size,wy=row*size;
                if(wx<MARGIN_CELLS || wx+size>c.width()-MARGIN_CELLS || wy+size>c.height()-1-MARGIN_CELLS || wy<WorldGenerator.layers(c,wx).stoneTop()+MARGIN_CELLS) continue;
                double xx=(wx-x)/radius,yy=(wy-y)/radius;
                if(xx*xx+yy*yy<=1) mask[row*cols+col]=true;
            }
            x+=Math.cos(heading)*size*.7; y+=Math.sin(heading)*size*.7;
        }
        return path;
    }
    public static Stats carve(WorldGenerationConfig c,FixedBlockWorld blocks) {
        int size=c.blockSize(),cols=c.width()/size,rows=(c.height()-1)/size;
        if(cols*size<80 || rows*size<80) return new Stats(0,0,0,0,0,0,0,0);
        long start=System.nanoTime(); var rng=new Random(mix(c.seed()^CAVE_CHANNEL));
        int systems=3+rng.nextInt(4),accepted=0,rejected=0,removed=0,branched=0;
        boolean[] selected=new boolean[cols*rows];
        for(int system=0;system<systems;system++) {
            boolean[] candidate=new boolean[selected.length];
            double x=16+rng.nextInt(Math.max(1,c.width()-32));
            int rock=WorldGenerator.layers(c,(int)x).stoneTop(); int floor=c.height()-1-24;
            if(rock+24>=floor) { rejected++; continue; }
            double y=rock+24+rng.nextInt(floor-rock-23), angle=rng.nextDouble()*Math.PI*2;
            int steps=Math.min(MAX_STEPS,Math.max(8,(60+rng.nextInt(121))/size));
            var path=walk(c,candidate,rng,x,y,angle,steps);
            int branches=rng.nextInt(MAX_BRANCHES+1); boolean expanded=false;
            for(int branch=0;branch<branches;branch++) {
                int index=(branch+1)*steps/(branches+1); var anchor=path[index];
                double turn=(branch%2==0?1:-1)*(.7+rng.nextDouble());
                int before=0; for(boolean value:candidate) if(value) before++;
                walk(c,candidate,rng,anchor[0],anchor[1],anchor[2]+turn,Math.max(5,steps/2));
                int after=0; for(boolean value:candidate) if(value) after++; if(after>before) expanded=true;
            }
            int count=0; for(boolean value:candidate) if(value) count++;
            if(count<4) { rejected++; continue; }
            accepted++; if(expanded) branched++; for(int i=0;i<selected.length;i++) selected[i]|=candidate[i];
        }
        // Clipping near terrain can detach tiny remnants; discard components under four blocks.
        boolean[] seen=new boolean[selected.length]; int[] queue=new int[selected.length];
        for(int i=0;i<selected.length;i++) if(selected[i] && !seen[i]) {
            int head=0,tail=0; queue[tail++]=i; seen[i]=true;
            while(head<tail) { int p=queue[head++],x=p%cols,y=p/cols;
                if(x>0 && selected[p-1] && !seen[p-1]) { seen[p-1]=true; queue[tail++]=p-1; }
                if(x+1<cols && selected[p+1] && !seen[p+1]) { seen[p+1]=true; queue[tail++]=p+1; }
                if(y>0 && selected[p-cols] && !seen[p-cols]) { seen[p-cols]=true; queue[tail++]=p-cols; }
                if(y+1<rows && selected[p+cols] && !seen[p+cols]) { seen[p+cols]=true; queue[tail++]=p+cols; }
            }
            if(tail<4) for(int k=0;k<tail;k++) selected[queue[k]]=false;
        }
        var positions=new ArrayList<FixedBlockWorld.BlockPosition>();
        for(int i=0;i<selected.length;i++) if(selected[i]) {
            int x=(i%cols)*size,y=(i/cols)*size;
            if(blocks.blockAt(x,y)!=null) positions.add(new FixedBlockWorld.BlockPosition(x,y));
        }
        long candidateNanos=System.nanoTime()-start;
        for(var p:positions) { if(!blocks.removeAt(p.x(),p.y())) throw new IllegalStateException("Cave ownership changed"); removed++; }
        return new Stats(systems,accepted,rejected,0,removed,candidateNanos,0,branched);
    }
}
