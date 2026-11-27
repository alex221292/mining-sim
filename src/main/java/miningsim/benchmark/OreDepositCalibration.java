package miningsim.benchmark;

import java.util.Locale;
import miningsim.generation.WorldGenerationConfig;
import miningsim.generation.WorldGenerator;
import miningsim.resource.OreId;

/** Reports connected components of actual surviving ore-bearing blocks. */
public final class OreDepositCalibration {
    public static void main(String[] args) {
        int[] seeds={0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,431039,357355,12345};
        for(int size:new int[]{4,8}) for(int seed:seeds) {
            var c=WorldGenerationConfig.preset(seed,size);
            var blocks=WorldGenerator.generate(c).session().blocks().saveState().blocks();
            int cols=c.width()/size,rows=(c.height()-1)/size,n=cols*rows;
            int[] masks=new int[n],queue=new int[n],count=new int[4],min={Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE},max=new int[4];
            long[] sum=new long[4]; int empty=0,mixed=0;
            int[][] rocks=new int[4][4];
            for(var b:blocks) {
                int index=b.y()/size*cols+b.x()/size;
                int components=b.ores().components().size();
                if(components==0) empty++; if(components>1) mixed++;
                for(var ore:b.ores().components()) {
                    int i=ore.ore().id()-1; masks[index]|=1<<i; count[i]++; sum[i]+=ore.ppm();
                    min[i]=Math.min(min[i],ore.ppm()); max[i]=Math.max(max[i],ore.ppm());
                    int rock=switch(b.material()) { case STONE -> 0; case GRANITE -> 1; case LIMESTONE -> 2; case MARBLE -> 3; default -> throw new IllegalStateException(); };
                    rocks[i][rock]++;
                }
            }
            for(var ore:OreId.values()) {
                int i=ore.id()-1,components=0,largest=0; boolean[] seen=new boolean[n];
                for(int p=0;p<n;p++) if((masks[p]&(1<<i))!=0 && !seen[p]) {
                    int head=0,tail=0; queue[tail++]=p; seen[p]=true;
                    while(head<tail) {
                        int q=queue[head++],x=q%cols,y=q/cols;
                        if(x>0) tail=visit(q-1,i,masks,seen,queue,tail);
                        if(x+1<cols) tail=visit(q+1,i,masks,seen,queue,tail);
                        if(y>0) tail=visit(q-cols,i,masks,seen,queue,tail);
                        if(y+1<rows) tail=visit(q+cols,i,masks,seen,queue,tail);
                    }
                    components++; largest=Math.max(largest,tail);
                }
                System.out.printf(Locale.ROOT,"size=%d seed=%d ore=%s blocks=%d total=%d share=%.3f%% ppm=%d/%.2f/%d components=%d largest=%d rocks=%s empty=%d mixed=%d%n",size,seed,ore,count[i],blocks.size(),100.0*count[i]/blocks.size(),count[i]==0?0:min[i],count[i]==0?0:sum[i]/(double)count[i],max[i],components,largest,java.util.Arrays.toString(rocks[i]),empty,mixed);
            }
        }
    }
    private static int visit(int p,int i,int[] masks,boolean[] seen,int[] queue,int tail) {
        if(!seen[p] && (masks[p]&(1<<i))!=0) { seen[p]=true; queue[tail++]=p; }
        return tail;
    }
}
