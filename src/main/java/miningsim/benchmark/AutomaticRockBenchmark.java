package miningsim.benchmark;

import java.util.Arrays;
import java.util.Locale;
import miningsim.app.InteractiveSession;
import miningsim.world.Material;
import miningsim.world.World;

/** Bounded exploratory full-world topology, extraction and subsequent physics measurements. */
public final class AutomaticRockBenchmark {
    private static void run(String name,int components,int size,boolean dense,boolean edits,int maxTicks) {
        var w=new World(400,300,World.Mode.BEDROCK_ENABLED);
        if(dense) for(int y=0;y<299;y++) for(int x=0;x<400;x++) w.set(x,y,Material.STONE);
        for(int c=0;c<components;c++) {
            int ox=4+(c%20)*19,oy=4+(c/20)*12;
            for(int i=0;i<size;i++) w.set(ox+i%32,oy+i/32,Material.STONE);
        }
        var s=new InteractiveSession(w,null,true); long[] samples=new long[maxTicks];
        int n=0,first=-1,firstComplete=-1,lastDetach=-1; long lastTotal=0,work=0,prep=0,transaction=0,commit=0,physics=0,occupancy=0; String failure="";
        for(int i=0;i<maxTicks;i++) {
            if(edits && i<20) { int x=200+i; s.enqueue(()->w.set(x,100,Material.STONE)); }
            long start=System.nanoTime(); s.tick(); long elapsed=System.nanoTime()-start;
            var d=s.automatic().diagnostics(); work+=d.work(); prep+=s.automatic().preparationNanos(); transaction+=s.automatic().transactionNanos(); commit+=s.automatic().commitNanos();
            if(d.total()>0 && first<0) first=i+1;
            if(d.total()!=lastTotal) { lastTotal=d.total(); lastDetach=i+1; }
            if((!d.pending() || d.total()>0) && firstComplete<0) firstComplete=i+1;
            if(s.failure()!=null) { failure=s.failure(); break; }
            samples[n++]=elapsed; var p=s.coupled().phaseTimes(); physics+=p.physicsNanos(); occupancy+=p.occupancyNanos();
            if(!edits && !dense && components>0 && d.total()>=components && i+1>=lastDetach+30) break;
        }
        if(n==0) { System.out.println(name+" no successful ticks failure="+failure); return; }
        samples=Arrays.copyOf(samples,n); double mean=Arrays.stream(samples).average().orElseThrow()/1e6; Arrays.sort(samples);
        var d=s.automatic().diagnostics();
        System.out.printf(Locale.ROOT,"%s ticks=%d mean=%.3fms p95=%.3fms work=%d firstComplete=%d firstDetach=%d totalDetached=%d pending=%s tooLarge=%d prepTotal=%.3fms transactionTotal=%.3fms commitTotal=%.3fms physicsMean=%.3fms occupancyMean=%.3fms failure=%s%n",
                name,n,mean,samples[(int)Math.ceil(n*.95)-1]/1e6,work,firstComplete,first,d.total(),d.pending(),d.tooLarge(),prep/1e6,transaction/1e6,commit/1e6,
                physics/(double)n/1e6,occupancy/(double)n/1e6,failure.isEmpty()?"none":failure);
    }
    public static void main(String[] args) {
        System.out.println(System.getProperty("java.runtime.version")+" "+System.getProperty("java.vm.name")+" world=400x300, default budgets, cold sequential ticks");
        run("empty",0,0,false,false,30); run("sparse",10,1,false,false,220); run("dense-supported",0,0,true,false,40);
        run("brush-edits",0,0,false,true,70); run("components-1",1,1,false,false,80);
        run("components-10",10,1,false,false,220); run("components-100",100,1,false,false,1800);
        for(int size:new int[]{64,128,256,512,1024}) run("cells-"+size,1,size,false,false,80);
    }
}
