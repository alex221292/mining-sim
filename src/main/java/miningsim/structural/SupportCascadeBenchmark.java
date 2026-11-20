package miningsim.structural;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import miningsim.fragment.FragmentRegistry;
import miningsim.physics.*;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.SubstanceRegistry;
import miningsim.simulation.CellularDisplacementPlanner;
import miningsim.world.Material;
import miningsim.world.World;

/** Headless fixed-scene diagnostic; setup is excluded from measured work. */
public final class SupportCascadeBenchmark {
    private SupportCascadeBenchmark() { }
    public static void main(String[] args) {
        int count=args.length>0?Integer.parseInt(args[0]):3,runs=args.length>1?Integer.parseInt(args[1]):5;
        if(count!=1 && count!=3 && count!=10 && count!=50 || runs<1) throw new IllegalArgumentException("Use 1, 3, 10 or 50");
        int affected=count==50?3:count;
        var initial=new ArrayList<Long>(); var incremental=new ArrayList<Long>(); var build=new ArrayList<Long>();
        var stability=new ArrayList<Long>(); var cascade=new ArrayList<Long>(); var prep=new ArrayList<Long>();
        var commit=new ArrayList<Long>(); var firstPhysics=new ArrayList<Long>();
        long beforeChecks=0,afterChecks=0; int inspected=0,cells=0; long work=0;
        for(int run=0;run<runs;run++) {
            var world=new World(256,128,World.Mode.BEDROCK_ENABLED); var topology=new RockConnectivity(world);
            var settled=new SettledRockRegistry(world,topology,100,10);
            var one=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new FragmentDescriptor.Cell(0,0)));
            world.set(2,126,Material.STONE);
            for(int i=0;i<affected;i++) { world.set(2,125-i,Material.STONE); settled.registerExisting(i+1,one,2,125-i); }
            for(int i=affected;i<count;i++) { int x=8+4*(i-affected); world.set(x,126,Material.STONE); settled.registerExisting(i+1,one,x,126); }
            var registry=new FragmentRegistry(100);
            var solver=new StaticContactSolver(new StaticCollisionQuery(world,100_000,1_000_000,StaticCollisionQuery.MaterialPolicy.DISPLACE_GRANULAR),StaticContactSolver.Config.defaults());
            var physics=new RigidBodySystem(registry,4,solver,RigidBodySystem.MotionConfig.defaults(),new BodyCollisionQuery(new BodyCollisionQuery.Limits(1_000_000,10_000_000,4096,4096)),new BodyContactSolver(BodyContactSolver.Config.defaults()));
            physics.synchronize(); var occupancy=new RigidBodyOccupancyIndex(world,RigidBodyOccupancyIndex.Limits.defaults()); occupancy.rebuild(physics,0);
            var coupled=new CoupledSimulationStep(world,physics,occupancy,CellularDisplacementPlanner.Limits.defaults(),true);
            var coordinator=new SettledSupportCoordinator(world,topology,settled,physics,occupancy,new SettledSupportCoordinator.Limits(100,100,10000,256,50,10000,1)); coordinator.enableProfiling(true);
            long start=System.nanoTime(); boolean idle=false;
            for(int i=0;i<10_000;i++) { var result=coordinator.step(0); if(result.status()==SettledSupportCoordinator.Status.IDLE) { idle=true; break; }
                if(result.status()==SettledSupportCoordinator.Status.BLOCKED || result.status()==SettledSupportCoordinator.Status.LIMIT || result.status()==SettledSupportCoordinator.Status.COMMITTED) throw new IllegalStateException(result.toString()); }
            initial.add(System.nanoTime()-start); if(!idle) throw new IllegalStateException("Initial scan did not finish");
            coupled.step(); if(coupled.lastFailure()!=null) throw new IllegalStateException(coupled.lastFailure()); beforeChecks+=physics.interactionDiagnostics().checks();
            world.set(2,126,Material.AIR); start=System.nanoTime(); boolean done=false;
            for(int i=0;i<10_000;i++) { var result=coordinator.step(coupled.diagnostics().ticks());
                if(result.status()==SettledSupportCoordinator.Status.COMMITTED) {
                    var expected=java.util.stream.LongStream.rangeClosed(1,affected).boxed().toList();
                    if(!result.reactivated().equals(expected)) throw new IllegalStateException(result.toString()); done=true; break;
                }
                if(result.status()==SettledSupportCoordinator.Status.BLOCKED || result.status()==SettledSupportCoordinator.Status.LIMIT) throw new IllegalStateException(result.toString()); }
            incremental.add(System.nanoTime()-start); if(!done) throw new IllegalStateException("Cascade did not finish");
            if(physics.bodies().size()!=affected || settled.snapshot().stream().filter(r->r.remainingCells()>0).count()!=count-affected)
                throw new IllegalStateException("Unrelated rocks changed");
            var p=coordinator.profile(); build.add(p.buildNanos()); stability.add(p.stabilityNanos()); cascade.add(p.cascadeNanos()); prep.add(p.preparationNanos()); commit.add(p.commitNanos());
            inspected=p.rocks(); cells=p.cells(); work=p.graphWork();
            coupled.step(); if(coupled.lastFailure()!=null) throw new IllegalStateException(coupled.lastFailure());
            firstPhysics.add(coupled.phaseTimes().physicsNanos()); afterChecks+=physics.interactionDiagnostics().checks();
            for(int i=0;i<10;i++) if(coordinator.step(coupled.diagnostics().ticks()).status()!=SettledSupportCoordinator.Status.IDLE) throw new IllegalStateException("Unexpected repeated analysis");
        }
        System.out.printf(Locale.ROOT,"%s 256x128 rocks=%d affected=%d runs=%d initial=%s incremental=%s graph-build=%s stability=%s cascade=%s preparation=%s commit=%s first-physics=%s SAT-before/after=%.1f/%.1f inspected=%d cells=%d work=%d%n",
                System.getProperty("java.runtime.version"),count,affected,runs,stats(initial),stats(incremental),stats(build),stats(stability),stats(cascade),stats(prep),stats(commit),stats(firstPhysics),beforeChecks/(double)runs,afterChecks/(double)runs,inspected,cells,work);
    }
    private static String stats(List<Long> values) {
        long[] a=values.stream().mapToLong(Long::longValue).sorted().toArray();
        return String.format(Locale.ROOT,"%.3f/%.3fms",Arrays.stream(a).average().orElseThrow()/1e6,a[(int)Math.ceil(a.length*.95)-1]/1e6);
    }
}
