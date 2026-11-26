package miningsim.benchmark;

import java.util.Locale;
import miningsim.generation.WorldGenerationConfig;
import miningsim.generation.WorldGenerator;
import miningsim.world.Material;

/** Bounded orthogonal block-component statistics for the prescribed sample. */
public final class RockGeologyCalibration {
    public static void main(String[] args) {
        int[] seeds={0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,12345,357355,431039};
        Material[] rocks={Material.STONE,Material.GRANITE,Material.LIMESTONE,Material.MARBLE};
        for(int size:new int[]{8,4}) {
            int[] present=new int[4],surfaceWorlds=new int[4]; double[] sum=new double[4];
            for(int seed:seeds) {
                var c=WorldGenerationConfig.preset(seed,size); var s=WorldGenerator.generate(c).session();
                int cols=c.width()/size,rows=(c.height()-1)/size,n=cols*rows; int[] grid=new int[n],queue=new int[n];
                java.util.Arrays.fill(grid,-1); int[] count=new int[4],components=new int[4],largest=new int[4],small=new int[4],surface=new int[4];
                for(var b:s.blocks().saveState().blocks()) {
                    int kind=java.util.Arrays.asList(rocks).indexOf(b.material()); int col=b.x()/size,row=b.y()/size;
                    grid[row*cols+col]=kind; count[kind]++;
                    if(b.y()==WorldGenerator.layers(c,b.x()).stoneTop()) surface[kind]++;
                }
                boolean[] seen=new boolean[n];
                for(int p=0;p<n;p++) if(grid[p]>=0 && !seen[p]) {
                    int kind=grid[p],head=0,tail=0; queue[tail++]=p; seen[p]=true;
                    while(head<tail) { int q=queue[head++],x=q%cols,y=q/cols;
                        if(x>0) tail=visit(q-1,kind,grid,seen,queue,tail);
                        if(x+1<cols) tail=visit(q+1,kind,grid,seen,queue,tail);
                        if(y>0) tail=visit(q-cols,kind,grid,seen,queue,tail);
                        if(y+1<rows) tail=visit(q+cols,kind,grid,seen,queue,tail);
                    }
                    components[kind]++; largest[kind]=Math.max(largest[kind],tail); if(tail<=4) small[kind]++;
                }
                int total=java.util.Arrays.stream(count).sum();
                for(int i=0;i<4;i++) {
                    double share=100.0*count[i]/total; sum[i]+=share;
                    if(count[i]>0) present[i]++; if(surface[i]>0) surfaceWorlds[i]++;
                    System.out.printf(Locale.ROOT,"size=%d seed=%d rock=%s share=%.2f%% components=%d largest=%d largestShare=%.1f%% small<=4=%d surfaceBlocks=%d%n",size,seed,rocks[i],share,components[i],largest[i],count[i]==0?0:100.0*largest[i]/count[i],small[i],surface[i]);
                }
            }
            for(int i=0;i<4;i++) System.out.printf(Locale.ROOT,"SUMMARY size=%d rock=%s meanShare=%.2f%% present=%d/19 surface=%d/19%n",size,rocks[i],sum[i]/seeds.length,present[i],surfaceWorlds[i]);
        }
    }
    private static int visit(int p,int kind,int[] grid,boolean[] seen,int[] queue,int tail) {
        if(!seen[p] && grid[p]==kind) { seen[p]=true; queue[tail++]=p; } return tail;
    }
}
