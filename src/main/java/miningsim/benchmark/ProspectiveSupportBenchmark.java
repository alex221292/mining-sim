package miningsim.benchmark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.TreeMap;
import miningsim.app.InteractiveSession;
import miningsim.structural.SettledSupportCoordinator;
import miningsim.world.Material;
import miningsim.world.World;

/** Single-threaded reproducible lifecycle measurements; values are not FPS. */
public final class ProspectiveSupportBenchmark {
    private static final TreeMap<String,ArrayList<Long>> samples=new TreeMap<>();
    private static void add(boolean measure,String key,long time) { if(measure && time>0) samples.computeIfAbsent(key,k->new ArrayList<>()).add(time); }
    private static void run(boolean measure) {
        var world=new World(20,20,World.Mode.BEDROCK_ENABLED); world.set(5,3,Material.STONE);
        var s=InteractiveSession.headlessLifecycle(world,true,SettledSupportCoordinator.Limits.measuredDefaults());
        int added=1; boolean removed=false,finished=false;
        for(int i=0;i<3000;i++) {
            long before=s.lifecycle().diagnostics().reactivated(); long start=System.nanoTime(); s.tick(); long elapsed=System.nanoTime()-start;
            if(s.failure()!=null || s.blocked()) throw new IllegalStateException("tick "+i+": "+s.failure());
            var d=s.lifecycle().diagnostics();
            add(measure,"settle-"+added+"-prepare",d.planningNanos());
            if(d.reactivated()>before) add(measure,"cascade-tick",elapsed);
            if(!removed && d.settledRocks()==added && s.physics().bodies().isEmpty()) {
                if(added<3) { added++; s.enqueue(()->world.set(5,3,Material.STONE)); }
                else {
                    for(int k=0;k<120;k++) { start=System.nanoTime(); s.tick(); add(measure,"unchanged-three-tick",System.nanoTime()-start); }
                    s.enqueue(()->world.set(5,18,Material.AIR)); removed=true;
                }
            }
            if(removed && d.reactivated()>=2) { finished=true; break; }
        }
        if(!finished) throw new IllegalStateException("incomplete: "+s.lifecycle().diagnostics());
    }
    private static void dense() {
        var world=new World(128,64,World.Mode.BEDROCK_ENABLED);
        for(int i=0;i<20;i++) world.set(3+i*6,53,Material.STONE);
        var s=InteractiveSession.headlessLifecycle(world,true,SettledSupportCoordinator.Limits.measuredDefaults());
        for(int i=0;i<6000 && s.lifecycle().settled().liveRocks()<20;i++) {
            s.tick(); if(s.failure()!=null || s.blocked()) throw new IllegalStateException("dense: "+s.failure());
        }
        if(s.lifecycle().settled().liveRocks()!=20) throw new IllegalStateException("dense incomplete: "+s.lifecycle().diagnostics());
        for(int i=0;i<120;i++) { long start=System.nanoTime(); s.tick(); add(true,"unchanged-20-tick",System.nanoTime()-start); }
        for(int i=0;i<10;i++) {
            final int x=2+i*3; s.enqueue(()->world.set(x,4,Material.STONE));
            long start=System.nanoTime(); s.tick(); add(true,"stone-edit-tick",System.nanoTime()-start);
        }
        for(int batch=0;batch<3;batch++) {
            for(int x=75;x<85;x++) world.set(x,2,Material.SAND);
            for(int i=0;i<60;i++) { long start=System.nanoTime(); s.tick(); add(true,"sand-tick",System.nanoTime()-start); }
        }
        if(s.failure()!=null || s.blocked()) throw new IllegalStateException("dense blocked: "+s.failure());
    }
    public static void main(String[] args) {
        int count=args.length==0?10:Integer.parseInt(args[0]); if(count<1) throw new IllegalArgumentException();
        System.out.println(System.getProperty("java.runtime.version")+" "+System.getProperty("java.vm.name")+" 20x20, 2 warmups, runs="+count);
        for(int i=0;i<count+2;i++) run(i>=2);
        dense();
        samples.forEach((key,v)->{ Collections.sort(v); System.out.printf(Locale.ROOT,"%s n=%d mean=%.3f p95=%.3f max=%.3f ms%n",key,v.size(),v.stream().mapToDouble(Long::doubleValue).average().orElseThrow()/1e6,v.get((int)Math.ceil(v.size()*.95)-1)/1e6,v.getLast()/1e6); });
    }
}
