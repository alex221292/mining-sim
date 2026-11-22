package miningsim.structural;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import miningsim.app.InteractiveSession;
import miningsim.app.StructuralSimulationController;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.SubstanceRegistry;
import miningsim.world.Material;
import miningsim.world.World;

/** Benchmark for incremental component analysis and its separate atomic commit. */
public final class ComponentLifecycleBenchmark {
    private static final ArrayList<Long> analysis=new ArrayList<>(),commit=new ArrayList<>(),ticks=new ArrayList<>();
    private static void run(boolean measured) {
        var w=new World(20,20,World.Mode.BEDROCK_ENABLED); var topology=new RockConnectivity(w);
        var damage=new RockDamageSystem(w,topology,RockReferenceScenes.MATERIALS,RockReferenceScenes.GRAVITY);
        var structural=new StructuralSimulationController(new RockReferenceScenes.Scene("component",w,topology,damage,0,0));
        var s=InteractiveSession.headlessLifecycle(w,structural,true,false,SettledSupportCoordinator.Limits.measuredDefaults());
        var shape=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new FragmentDescriptor.Cell(1,0),new FragmentDescriptor.Cell(2,0),new FragmentDescriptor.Cell(0,1),new FragmentDescriptor.Cell(1,1)));
        for(var c:shape.cells()) w.set(3+c.x(),17+c.y(),Material.STONE);
        s.lifecycle().settled().registerExisting(101,shape,3,17);
        for(int i=0;i<300 && !s.lifecycle().settled().supportTransitionsAllowed();i++) { s.tick(); if(s.failure()!=null) throw new IllegalStateException(s.failure()); }
        if(!s.lifecycle().settled().supportTransitionsAllowed()) throw new IllegalStateException("initial analysis incomplete");
        s.enqueue(()->{ w.set(4,17,Material.AIR); w.set(4,18,Material.AIR); }); int count=0; boolean done=false; long prior=0;
        for(int i=0;i<1000;i++) {
            s.tick(); count++; if(s.failure()!=null || s.blocked()) throw new IllegalStateException("tick "+i+": "+s.failure());
            var d=s.lifecycle().diagnostics();
            long cumulative=s.lifecycle().assemblyAnalysisNanos();
            long delta=cumulative>=prior?cumulative-prior:cumulative; prior=cumulative;
            if(measured && delta>0) analysis.add(delta);
            if(measured && d.reactivationCommitNanos()>0) commit.add(d.reactivationCommitNanos());
            if(d.reactivated()>0) { done=true; break; }
        }
        if(!done) throw new IllegalStateException("reactivation incomplete: "+s.lifecycle().diagnostics());
        if(measured) ticks.add((long)count);
    }
    private static void print(String name,ArrayList<Long> samples,boolean time) {
        Collections.sort(samples); double scale=time?1e6:1;
        System.out.printf(Locale.ROOT,"%s n=%d mean=%.3f p95=%.3f max=%.3f %s%n",name,samples.size(),samples.stream().mapToDouble(Long::doubleValue).average().orElseThrow()/scale,
                samples.get((int)Math.ceil(samples.size()*.95)-1)/scale,samples.getLast()/scale,time?"ms":"ticks");
    }
    public static void main(String[] args) {
        int runs=args.length==0?10:Integer.parseInt(args[0]); if(runs<1) throw new IllegalArgumentException();
        System.out.println(System.getProperty("java.runtime.version")+" "+System.getProperty("java.vm.name")+" 20x20 component, graphWorkPerTick=256, warmup=2, runs="+runs);
        for(int i=0;i<runs+2;i++) run(i>=2);
        print("analysis-tick",analysis,true); print("atomic-commit",commit,true); print("ticks-to-reactivation",ticks,false);
    }
}
