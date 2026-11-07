package miningsim.benchmark;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import miningsim.fragment.FragmentRegistry;
import miningsim.physics.*;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.FragmentDescriptor.Cell;
import miningsim.resource.SubstanceRegistry;
import miningsim.world.World;

/** Measures complete successful ticks, not an isolated transaction snapshot. */
public final class PhysicsRollbackBenchmark {
    private static void run(int count) {
        var registry=new FragmentRegistry(count);
        var shape=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new Cell(0,0)));
        for(int i=0;i<count;i++) registry.insert(shape,4+(i%10)*4,4+(i/10)*4,0);
        var world=new World(256,256);
        var system=new RigidBodySystem(registry,4,new StaticContactSolver(new StaticCollisionQuery(world,10000,100000),StaticContactSolver.Config.defaults()),
                RigidBodySystem.MotionConfig.defaults(),new BodyCollisionQuery(new BodyCollisionQuery.Limits(100000,1000000,4096,4096)),new BodyContactSolver(BodyContactSolver.Config.defaults()));
        try {
            for(int i=0;i<30;i++) system.step();
            long[] samples=new long[60];
            for(int i=0;i<samples.length;i++) { long start=System.nanoTime(); system.step(); samples[i]=System.nanoTime()-start; }
            double mean=Arrays.stream(samples).average().orElseThrow()/1e6; Arrays.sort(samples);
            System.out.printf(Locale.ROOT,"bodies=%d mean=%.4fms p95=%.4fms pairs=%d%n",count,mean,samples[56]/1e6,system.interactionDiagnostics().pairs());
        } catch(RuntimeException ex) { System.out.println("bodies="+count+" FAILED: "+ex); }
    }
    public static void main(String[] args) {
        System.out.println(System.getProperty("java.runtime.version")+" "+System.getProperty("java.vm.name"));
        for(int n:new int[]{1,10,32,100}) run(n);
    }
}
