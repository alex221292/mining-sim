package miningsim.physics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import miningsim.fragment.FragmentRegistry;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.FragmentDescriptor.Cell;
import miningsim.resource.SubstanceRegistry;
import miningsim.world.Material;
import miningsim.world.World;

/** One-tick identical-start comparison against the former four-substep algorithm. */
public final class BoundedMotionBenchmark {
    private static final int WARMUP=60, SAMPLES=200;
    private static FragmentDescriptor shape(int count) {
        var cells=new ArrayList<Cell>(); for(int i=0;i<count;i++) cells.add(new Cell(i%8,i/8));
        return new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),cells);
    }
    private record Case(String name, int count, int bodies, int y, double vy, double omega) { }
    private static void run(Case scenario, boolean adaptive) {
        var world=new World(128,48); for(int x=0;x<128;x++) world.set(x,40,Material.STONE);
        var descriptor=shape(scenario.count());
        long[] times=new long[SAMPLES]; long steps=0,candidates=0,checks=0,contacts=0,iterations=0;
        for(int sample=-WARMUP;sample<SAMPLES;sample++) {
            var registry=new FragmentRegistry(scenario.bodies());
            for(int i=0;i<scenario.bodies();i++) registry.insert(descriptor,2+12*i,scenario.y(),0);
            var solver=new StaticContactSolver(new StaticCollisionQuery(world,100000,1000000),StaticContactSolver.Config.defaults());
            var system=new RigidBodySystem(registry,4,solver); system.synchronize();
            for(var b:system.bodies()) b.setVelocity(0,scenario.vy(),scenario.omega());
            long start=System.nanoTime();
            if(adaptive) system.step();
            else {
                // Historical fixed stepping is reproduced here only for measurement; it is not a safe public mode.
                var snapshot = new java.util.TreeMap<Long, RigidBody.State>();
                for (var body : system.bodies()) snapshot.put(body.id(), body.state());
                system.synchronize();
                solver.resetDiagnostics();
                for(int sub=0;sub<4;sub++) for(var b:system.bodies()) {
                    b.integrate(RigidBodySystem.TICK_SECONDS/4,RigidBodySystem.GRAVITY_METRES_PER_SECOND_SQUARED); solver.solve(b);
                }
            }
            long elapsed=System.nanoTime()-start;
            if(sample>=0) {
                times[sample]=elapsed;
                var d=adaptive?system.diagnostics():solver.diagnostics(0,0);
                steps+=adaptive?system.motionDiagnostics().executedMicrosteps():4L*scenario.bodies();
                candidates+=d.candidates(); checks+=d.checks(); contacts+=d.contacts(); iterations+=d.velocityIterations();
            }
        }
        double mean=Arrays.stream(times).average().orElseThrow()/1e6; Arrays.sort(times);
        System.out.printf(Locale.ROOT,"%s %s mean=%.4fms p95=%.4fms steps=%.1f candidates=%.1f checks=%.1f contacts=%.1f iterations=%.1f%n",
                scenario.name(),adaptive?"adaptive":"baseline",mean,times[(int)(SAMPLES*0.95)-1]/1e6,
                steps/(double)SAMPLES,candidates/(double)SAMPLES,checks/(double)SAMPLES,contacts/(double)SAMPLES,iterations/(double)SAMPLES);
    }
    public static void main(String[] args) {
        System.out.println(System.getProperty("java.runtime.version")+" "+System.getProperty("java.vm.name"));
        for(var c:List.of(new Case("rest",1,1,39,0,0),new Case("fall",1,1,36,1,0),
                new Case("fast",1,1,35,60,0),new Case("spin16",16,1,25,0,30),
                new Case("spin64",64,1,25,0,12),new Case("multiple",1,6,39,0,0))) {
            run(c,false); run(c,true);
        }
    }
}
