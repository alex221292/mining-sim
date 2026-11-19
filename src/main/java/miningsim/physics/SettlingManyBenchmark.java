package miningsim.physics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import miningsim.fragment.FragmentRegistry;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.SubstanceRegistry;
import miningsim.simulation.CellularDisplacementPlanner;
import miningsim.structural.RockConnectivity;
import miningsim.structural.SettledRockRegistry;
import miningsim.world.World;

/** Headless sequential settling diagnostic; timings exclude rendering and cellular motion. */
public final class SettlingManyBenchmark {
    private SettlingManyBenchmark() { }
    public static void main(String[] args) {
        int count=args.length==0?10:Integer.parseInt(args[0]);
        if(count<1 || count>50) throw new IllegalArgumentException("count 1..50");
        var world=new World(400,40,World.Mode.BEDROCK_ENABLED); var topology=new RockConnectivity(world);
        var settled=new SettledRockRegistry(world,topology,count,2); while(topology.pending()) topology.advance(8192);
        var fragments=new FragmentRegistry(count); var descriptor=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new FragmentDescriptor.Cell(0,0)));
        for(int i=0;i<count;i++) fragments.insert(descriptor,2+7*i,38,0);
        var solver=new StaticContactSolver(new StaticCollisionQuery(world,10000,100000,StaticCollisionQuery.MaterialPolicy.DISPLACE_GRANULAR),StaticContactSolver.Config.defaults());
        var physics=new RigidBodySystem(fragments,4,solver,RigidBodySystem.MotionConfig.defaults(),new BodyCollisionQuery(new BodyCollisionQuery.Limits(1000000,10000000,4096,4096)),new BodyContactSolver(BodyContactSolver.Config.defaults()));
        physics.synchronize(); var occupancy=new RigidBodyOccupancyIndex(world,RigidBodyOccupancyIndex.Limits.defaults()); occupancy.rebuild(physics,0);
        var coupled=new CoupledSimulationStep(world,physics,occupancy,CellularDisplacementPlanner.Limits.defaults(),true);
        var trackers=new GroupSettlingEligibility[count]; for(int i=0;i<count;i++) trackers[i]=new GroupSettlingEligibility(List.of((long)i+1));
        var pre=new ArrayList<Long>(); var prePhysics=new ArrayList<Long>(); var planningTimes=new ArrayList<Long>(); var commitTimes=new ArrayList<Long>(); long prep=0,commit=0; int ticks=0,success=0; long maxPairs=0,maxChecks=0;
        while(success<count && ticks<Math.max(1000,count*300)) {
            while(topology.pending()) topology.advance(8192);
            long start=System.nanoTime(); coupled.step(); long elapsed=System.nanoTime()-start; ticks++;
            if(success==0 && ticks>60) { pre.add(elapsed); prePhysics.add(coupled.phaseTimes().physicsNanos()); }
            maxPairs=Math.max(maxPairs,physics.interactionDiagnostics().pairs()); maxChecks=Math.max(maxChecks,physics.interactionDiagnostics().checks());
            for(int i=0;i<count;i++) {
                var body=physics.body(i+1); if(body==null) continue;
                if(!trackers[i].observe(physics,coupled,world,topology,settled)) continue;
                start=System.nanoTime(); var result=GroupSettlingCommit.prepareGroupSettling(List.of(body.id()),physics,coupled,world,occupancy,topology,settled,trackers[i],2_000_000); long p=System.nanoTime()-start; prep+=p; planningTimes.add(p);
                if(result.status()!=GroupSettlingCommit.Status.READY) throw new IllegalStateException(result.toString());
                start=System.nanoTime(); var status=world.commitGroupSettling(result.prepared()); long c=System.nanoTime()-start; commit+=c; commitTimes.add(c);
                if(status!=World.BatchResult.CHANGED) throw new IllegalStateException("commit "+status); success++; break;
            }
        }
        long[] post=new long[60],postPhysics=new long[60]; for(int i=0;i<post.length;i++) { long start=System.nanoTime(); coupled.step(); post[i]=System.nanoTime()-start; postPhysics[i]=coupled.phaseTimes().physicsNanos(); }
        long[] before=pre.stream().mapToLong(Long::longValue).toArray(); Arrays.sort(before); Arrays.sort(post);
        System.out.printf(Locale.ROOT,"%s 400x40 cells=1 bodies=%d ticks=%d settled=%d remaining=%d prep-total=%.3fms commit-total=%.3fms pre mean/p95=%.3f/%.3fms post=%.3f/%.3fms max-pairs=%d max-checks=%d post-pairs=%d post-checks=%d%n",
                System.getProperty("java.runtime.version"),count,ticks,success,physics.bodies().size(),prep/1e6,commit/1e6,
                Arrays.stream(before).average().orElse(0)/1e6,before.length==0?0:before[(int)Math.ceil(before.length*.95)-1]/1e6,
                Arrays.stream(post).average().orElseThrow()/1e6,post[56]/1e6,maxPairs,maxChecks,physics.interactionDiagnostics().pairs(),physics.interactionDiagnostics().checks());
        long[] ps=planningTimes.stream().mapToLong(Long::longValue).sorted().toArray(),cs=commitTimes.stream().mapToLong(Long::longValue).sorted().toArray();
        if(ps.length>0) System.out.printf(Locale.ROOT,"planning mean/p95=%.3f/%.3fms commit mean/p95=%.3f/%.3fms%n",
                Arrays.stream(ps).average().orElseThrow()/1e6,ps[(int)Math.ceil(ps.length*.95)-1]/1e6,
                Arrays.stream(cs).average().orElseThrow()/1e6,cs[(int)Math.ceil(cs.length*.95)-1]/1e6);
        long[] bp=prePhysics.stream().mapToLong(Long::longValue).sorted().toArray(); Arrays.sort(postPhysics);
        if(bp.length>0) System.out.printf(Locale.ROOT,"physics before mean/p95=%.3f/%.3fms after=%.3f/%.3fms%n",
                Arrays.stream(bp).average().orElseThrow()/1e6,bp[(int)Math.ceil(bp.length*.95)-1]/1e6,
                Arrays.stream(postPhysics).average().orElseThrow()/1e6,postPhysics[(int)Math.ceil(postPhysics.length*.95)-1]/1e6);
    }
}
