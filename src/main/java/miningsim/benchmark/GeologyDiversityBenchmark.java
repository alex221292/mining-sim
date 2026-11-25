package miningsim.benchmark;

import java.util.Locale;
import miningsim.generation.WorldGenerationConfig;
import miningsim.generation.WorldGenerator;

/** Geometric statistics before simulation; seeds 0..31 and the user-provided seed. */
public final class GeologyDiversityBenchmark {
    private static void measure(int size,int first,int count) {
        long[] cells=new long[7],columns=new long[7],runs=new long[7]; int[] max=new int[7],longest=new int[7],qualifying=new int[4];
        for(int n=0;n<count;n++) {
            var c=WorldGenerationConfig.preset(first+n,size); int[] run=new int[7],seedMax=new int[4];
            for(int x=0;x<c.width();x++) {
                var l=WorldGenerator.layers(c,x); int[] d={l.soil(),l.sand(),l.gravel(),l.stoneTop()==l.surface()?1:0,l.surface(),c.height()-1-l.stoneTop(),1};
                seedMax[0]=Math.max(seedMax[0],l.soil()); seedMax[1]=Math.max(seedMax[1],l.sand()); seedMax[2]=Math.max(seedMax[2],l.gravel());
                for(int i=0;i<7;i++) { cells[i]+=d[i]; max[i]=Math.max(max[i],d[i]); if(d[i]>0) { columns[i]++; if(run[i]++==0) runs[i]++; longest[i]=Math.max(longest[i],run[i]); } else run[i]=0; }
            }
            if(seedMax[0]>=16) qualifying[0]++; if(seedMax[1]>=32) qualifying[1]++; if(seedMax[2]>=24) qualifying[2]++;
        }
        System.out.printf(Locale.ROOT,"size=%d seeds=%d..%d",size,first,first+count-1);
        for(int i=0;i<7;i++) System.out.printf(Locale.ROOT," %s[total=%d mean=%.2f max=%d coverage=%.2f%% runMean=%.2f longest=%d]",new String[]{"soil","sand","gravel","outcrop","air","stone","bedrock"}[i],cells[i],columns[i]==0?0:(double)cells[i]/columns[i],max[i],100.0*columns[i]/(800L*count),runs[i]==0?0:(double)columns[i]/runs[i],longest[i]);
        System.out.printf(" thresholds[soil16=%d sand32=%d gravel24=%d]%n",qualifying[0],qualifying[1],qualifying[2]);
    }
    public static void main(String[] args) { for(int s:new int[]{8,4}) { measure(s,0,32); measure(s,357355,1); } }
}
