package miningsim.benchmark;

import java.util.Locale;
import miningsim.fragment.*;
import miningsim.structural.RockConnectivity;
import miningsim.world.*;

public final class FragmentExtractionBenchmark {
    private static void finish(RockConnectivity c) { while(c.pending()) c.advance(10000); }
    public static void main(String[] args) {
        for (int n : new int[]{1,6,32,64}) {
            World w = new World(70,6); for(int i=0;i<n;i++) w.set(i,2,Material.STONE);
            if(n==6) { w.set(5,2,Material.AIR); w.set(4,3,Material.STONE); }
            RockConnectivity c=new RockConnectivity(w); finish(c); FragmentRegistry registry=new FragmentRegistry(100);
            long start=System.nanoTime(); var p=ExtractionPlanner.prepare(w,c,0,2,64); long plan=System.nanoTime()-start;
            start=System.nanoTime(); var result=ExtractionCommit.commit(p.plan(),registry); long commit=System.nanoTime()-start;
            start=System.nanoTime(); finish(c); long rebuild=System.nanoTime()-start;
            start=System.nanoTime(); for(int i=0;i<10000;i++) registry.snapshot(); long snapshot=System.nanoTime()-start;
            System.out.printf(Locale.ROOT,"cells=%d status=%s plan=%.3fms validation+commit=%.3fms rebuild=%.3fms snapshot=%.6fms%n",
                    n,result.status(),plan/1e6,commit/1e6,rebuild/1e6,snapshot/10000e6);
        }
        World w=new World(32,3); for(int x=0;x<32;x+=2) w.set(x,1,Material.STONE);
        RockConnectivity c=new RockConnectivity(w); finish(c); FragmentRegistry registry=new FragmentRegistry(16);
        long start=System.nanoTime();
        for(int x=0;x<32;x+=2) { var p=ExtractionPlanner.prepare(w,c,x,1,64); ExtractionCommit.commit(p.plan(),registry); finish(c); }
        System.out.printf(Locale.ROOT,"16 isolated cells sequential: %.3fms fragments=%d%n",(System.nanoTime()-start)/1e6,registry.size());
    }
}
