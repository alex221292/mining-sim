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

/** Reproducible headless reactivation timings; fixture construction is excluded. */
public final class ReactivationBenchmark {
    private ReactivationBenchmark() { }
    public static void main(String[] args) {
        int count=args.length>0?Integer.parseInt(args[0]):3, width=args.length>1?Integer.parseInt(args[1]):8,
                height=args.length>2?Integer.parseInt(args[2]):4, runs=args.length>3?Integer.parseInt(args[3]):5;
        if(count<1 || count>10 || width*height>2048 || width<1 || height<1 || runs<1 || count*(width+3)>300)
            throw new IllegalArgumentException("Invalid benchmark size");
        var planning=new ArrayList<Long>(); var commit=new ArrayList<Long>(); var geometry=new ArrayList<Long>();
        var occupancy=new ArrayList<Long>(); var ticks=new ArrayList<Long>(); var physicsTicks=new ArrayList<Long>();
        for(int run=0;run<runs;run++) {
            var world=new World(300,80,World.Mode.BEDROCK_ENABLED); var topology=new RockConnectivity(world);
            var settled=new SettledRockRegistry(world,topology,count,2048);
            var cells=new ArrayList<FragmentDescriptor.Cell>();
            for(int y=0;y<height;y++) for(int x=0;x<width;x++) cells.add(new FragmentDescriptor.Cell(x,y));
            var descriptor=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),cells);
            for(int i=0;i<count;i++) {
                int x=2+i*(width+3),y=10; for(var cell:cells) world.set(x+cell.x(),y+cell.y(),Material.STONE);
                settled.registerExisting(i+1,descriptor,x,y);
            }
            var registry=new FragmentRegistry(count); var solver=new StaticContactSolver(new StaticCollisionQuery(world,100_000,1_000_000,StaticCollisionQuery.MaterialPolicy.DISPLACE_GRANULAR),StaticContactSolver.Config.defaults());
            var physics=new RigidBodySystem(registry,4,solver,RigidBodySystem.MotionConfig.defaults(),new BodyCollisionQuery(new BodyCollisionQuery.Limits(1_000_000,10_000_000,4096,4096)),new BodyContactSolver(BodyContactSolver.Config.defaults()));
            physics.synchronize(); var index=new RigidBodyOccupancyIndex(world,RigidBodyOccupancyIndex.Limits.defaults()); index.rebuild(physics,0);
            var coupled=new CoupledSimulationStep(world,physics,index,CellularDisplacementPlanner.Limits.defaults(),true);
            for(long id=1;id<=count;id++) {
                long start=System.nanoTime(); var result=SettledRockReactivation.prepareReactivation(id,world,topology,settled,physics,index,coupled.diagnostics().ticks(),true);
                planning.add(System.nanoTime()-start); if(result.status()!=SettledRockReactivation.Status.READY) throw new IllegalStateException(result.toString());
                geometry.add(result.plan().geometryNanos()); occupancy.add(result.plan().occupancyNanos());
                start=System.nanoTime(); var status=world.commitReactivation(result.plan()); commit.add(System.nanoTime()-start);
                if(status!=World.BatchResult.CHANGED) throw new IllegalStateException(status.toString());
                start=System.nanoTime(); coupled.step(); ticks.add(System.nanoTime()-start); physicsTicks.add(coupled.phaseTimes().physicsNanos());
                if(coupled.lastFailure()!=null) throw new IllegalStateException(coupled.lastFailure());
            }
        }
        System.out.printf(Locale.ROOT,"%s world=300x80 bodies=%d cells/body=%d runs=%d prepare=%s commit=%s geometry=%s occupancy=%s first-coupled-tick=%s%n",
                System.getProperty("java.runtime.version"),count,width*height,runs,stats(planning),stats(commit),stats(geometry),stats(occupancy),stats(ticks));
        System.out.printf(Locale.ROOT,"first-physics-tick=%s%n",stats(physicsTicks));
    }
    private static String stats(List<Long> input) {
        long[] values=input.stream().mapToLong(Long::longValue).sorted().toArray();
        return String.format(Locale.ROOT,"%.3f/%.3fms",Arrays.stream(values).average().orElseThrow()/1e6,values[(int)Math.ceil(values.length*.95)-1]/1e6);
    }
}
