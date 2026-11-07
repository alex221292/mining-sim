package miningsim.benchmark;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import miningsim.fragment.FragmentRegistry;
import miningsim.physics.*;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.FragmentDescriptor.Cell;
import miningsim.resource.SubstanceRegistry;
import miningsim.world.*;

/** Exploratory deterministic headless scenarios; reported failures are never omitted. */
public final class BodyCollisionBenchmark {
    private record Scene(String name,int count,int spacing,boolean moving,boolean irregular) { }
    private static void run(Scene scene) {
        var w=new World(128,80); for(int x=0;x<128;x++) w.set(x,70,Material.STONE);
        var shape=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),scene.irregular()
                ? List.of(new Cell(0,0),new Cell(1,0),new Cell(0,1)) : List.of(new Cell(0,0)));
        var registry=new FragmentRegistry(scene.count());
        for(int i=0;i<scene.count();i++) registry.insert(shape,scene.name().equals("stack") ? 3 : 3+(i%8)*scene.spacing(),
                scene.name().equals("stack") ? 69-i : 68-(i/8)*scene.spacing(),0);
        var system=new RigidBodySystem(registry,4,new StaticContactSolver(new StaticCollisionQuery(w,10000,100000),StaticContactSolver.Config.defaults()),
                RigidBodySystem.MotionConfig.defaults(),new BodyCollisionQuery(new BodyCollisionQuery.Limits(100000,1000000,4096,4096)),
                new BodyContactSolver(BodyContactSolver.Config.defaults()));
        system.synchronize();
        if(scene.moving()) for(var body:system.bodies()) body.setVelocity((body.id()%2==0?-0.5:0.5),0,scene.irregular()?1:0);
        long[] times=new long[120]; long shared=0,pairs=0,checks=0,contacts=0,iters=0,sleep=0;
        try {
            for(int i=-60;i<times.length;i++) {
                long start=System.nanoTime(); system.step(); long elapsed=System.nanoTime()-start;
                if(i>=0) { times[i]=elapsed; var d=system.interactionDiagnostics(); shared+=d.sharedMicrosteps(); pairs+=d.pairs(); checks+=d.checks(); contacts+=d.reducedContacts(); iters+=d.solverIterations(); sleep+=d.sleepingBodies(); }
            }
            double mean=Arrays.stream(times).average().orElseThrow()/1e6; Arrays.sort(times);
            System.out.printf(Locale.ROOT,"%s mean=%.3fms p95=%.3fms shared=%.1f pairs=%.1f checks=%.1f contacts=%.1f iterations=%.1f sleep=%.1f%n",
                    scene.name(),mean,times[113]/1e6,shared/120.0,pairs/120.0,checks/120.0,contacts/120.0,iters/120.0,sleep/120.0);
        } catch (RuntimeException e) { System.out.println(scene.name()+" FAILED: "+e); }
    }
    public static void main(String[] args) {
        System.out.println(System.getProperty("java.runtime.version")+" "+System.getProperty("java.vm.name"));
        for(var s:List.of(new Scene("one",1,2,false,false),new Scene("head-on",2,2,true,false),
                new Scene("stack",2,2,false,false),new Scene("ten-rest",10,2,false,false),
                new Scene("ten-moving",10,2,true,false),new Scene("sparse32",32,3,false,false),
                new Scene("dense32",32,1,false,false),new Scene("irregular",8,4,true,true))) run(s);
    }
}
