package miningsim.benchmark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.TreeMap;
import miningsim.app.InteractiveSession;
import miningsim.physics.LargeCompoundBenchmark;
import miningsim.structural.SettledSupportCoordinator;
import miningsim.world.Material;
import miningsim.world.World;

/** Reproducible wall-clock samples, not a correctness test or an FPS benchmark. */
public final class HeadlessLifecycleBenchmark {
    private static final TreeMap<String,ArrayList<Long>> samples=new TreeMap<>();
    private static void add(boolean measured,String name,long nanos) {
        if(measured && nanos>0) samples.computeIfAbsent(name,k->new ArrayList<>()).add(nanos);
    }
    private static void small(boolean measured) {
        var world=new World(20,20,World.Mode.BEDROCK_ENABLED);
        world.set(5,3,Material.STONE); world.set(5,8,Material.STONE);
        var s=InteractiveSession.headlessLifecycle(world,true,SettledSupportCoordinator.Limits.measuredDefaults());
        boolean removed=false,done=false; long begin=System.nanoTime();
        for(int i=0;i<2000;i++) {
            long oldSettled=s.lifecycle().diagnostics().settled(),oldReactivated=s.lifecycle().diagnostics().reactivated();
            long start=System.nanoTime(); s.tick(); long elapsed=System.nanoTime()-start;
            if(s.failure()!=null || s.blocked()) throw new IllegalStateException("tick "+i+": "+s.failure());
            var d=s.lifecycle().diagnostics();
            add(measured,"small.support",d.supportNanos());
            if(removed && oldReactivated==0) add(measured,"small.support-after-removal",d.supportNanos());
            add(measured,"small.settling.prepare",d.planningNanos());
            add(measured,"small.settling.commit",d.commitNanos());
            add(measured,"small.reactivation.prepare",d.reactivationPlanningNanos());
            add(measured,"small.reactivation.commit",d.reactivationCommitNanos());
            if(d.reactivated()>oldReactivated) add(measured,"small.reactivation.first-physics-tick",elapsed);
            else if(d.settled()==oldSettled && d.planningNanos()==0) add(measured,"small.ordinary-tick",elapsed);
            if(!removed && d.settledRocks()==2 && s.physics().bodies().isEmpty()) {
                s.enqueue(()->world.set(5,18,Material.AIR)); removed=true;
            }
            if(removed && d.reactivated()>0 && d.settled()>=3 && s.physics().bodies().isEmpty()) { done=true; break; }
        }
        if(!done) throw new IllegalStateException("Incomplete small lifecycle: "+s.lifecycle().diagnostics());
        add(measured,"small.full-lifecycle",System.nanoTime()-begin);
    }
    private static void large(boolean measured) {
        var fixture=LargeCompoundBenchmark.fixture("large"); var original=fixture.session();
        var s=InteractiveSession.headlessLifecycle(original.world(),original.structural(),true,false,SettledSupportCoordinator.Limits.measuredDefaults());
        original.retire(); s.physics().synchronize(); s.occupancy().rebuild(s.physics(),0);
        long begin=System.nanoTime(); boolean done=false;
        for(int i=0;i<1200;i++) {
            long start=System.nanoTime(); s.tick(); long elapsed=System.nanoTime()-start;
            if(s.failure()!=null || s.blocked()) throw new IllegalStateException("large tick "+i+": "+s.failure());
            var d=s.lifecycle().diagnostics();
            add(measured,"large.support",d.supportNanos()); add(measured,"large.settling.prepare",d.planningNanos());
            add(measured,"large.settling.commit",d.commitNanos());
            if(d.planningNanos()==0) add(measured,"large.ordinary-tick",elapsed);
            else add(measured,"large.settling-transition-tick",elapsed);
            if(d.settled()==3) { done=true; break; }
        }
        if(!done) throw new IllegalStateException("Incomplete large settling: "+s.lifecycle().diagnostics());
        add(measured,"large.until-settled",System.nanoTime()-begin);
    }
    public static void main(String[] args) {
        int runs=args.length==0?10:Integer.parseInt(args[0]);
        if(runs<1) throw new IllegalArgumentException("runs must be positive");
        System.out.println(System.getProperty("java.runtime.version")+" "+System.getProperty("java.vm.name")+"; warmup=2; runs="+runs+"; 20x20 full lifecycle, 64x64 134/134/526 settling");
        for(int i=0;i<runs+2;i++) { small(i>=2); large(i>=2); }
        samples.forEach((name,values)->{
            Collections.sort(values); double mean=values.stream().mapToDouble(Long::doubleValue).average().orElseThrow()/1e6;
            System.out.printf(Locale.ROOT,"%s n=%d mean=%.3f ms p95=%.3f ms max=%.3f ms%n",name,values.size(),mean,values.get((int)Math.ceil(values.size()*0.95)-1)/1e6,values.getLast()/1e6);
        });
    }
}
