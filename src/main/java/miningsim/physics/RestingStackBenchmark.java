package miningsim.physics;

import java.util.Arrays;
import java.util.Locale;
import miningsim.fragment.FragmentRegistry;
import miningsim.world.World;
import miningsim.structural.RestingStackFixture;
import miningsim.app.InteractiveSession;
import miningsim.app.StructuralSimulationController;

/** Fixed 1,000-tick coupled stack; phase ranges are fixed to permit before/after comparison. */
public final class RestingStackBenchmark {
    public record Scene(World world,FragmentRegistry registry,RigidBodySystem physics,RigidBodyOccupancyIndex occupancy,CoupledSimulationStep coupling,InteractiveSession session) { }
    private record TickSample(long elapsed,CoupledSimulationStep.PhaseTimes phases,RigidBodySystem.Profile profile,
                              RigidBodySystem.MotionDiagnostics motion,RigidBodySystem.InteractionDiagnostics contacts,
                              StaticContactSolver.Diagnostics staticContacts,java.util.List<RigidBody.State> before,
                              java.util.List<RigidBody.State> after) { }
    public static Scene scene() {
        var fixture=RestingStackFixture.scene(); var w=fixture.world(); var structural=new StructuralSimulationController(fixture); var registry=structural.registry();
        RestingStackFixture.populate(registry);
        var session=new InteractiveSession(w,structural,true); var physics=session.physics();
        physics.synchronize(); physics.enableProfiling(true); var occupancy=session.occupancy(); occupancy.rebuild(physics,0);
        return new Scene(w,registry,physics,occupancy,session.coupled(),session);
    }
    public static void main(String[] args) {
        var s=scene();
        if(args.length>0 && args[0].equals("wood")) for(int x=18;x<=34;x++) s.world().set(x,38,miningsim.world.Material.WOOD);
        long[][] elapsed={new long[16],new long[10],new long[374],new long[600]};
        var timeline=new TickSample[1000];
        int[] counts=new int[4]; long[] wakes=new long[4],checks=new long[4],contacts=new long[4],reduced=new long[4],pairs=new long[4],micro=new long[4],iterations=new long[4],physics=new long[4],occupancy=new long[4],planning=new long[4],staticQuery=new long[4],bodyQuery=new long[4],staticSolve=new long[4],bodySolve=new long[4]; double[] maxMotion=new double[4],maxSpeed=new double[4],maxPenetration=new double[4],staticPenetration=new double[4],correction=new double[4];
        for(int tick=0;tick<1000;tick++) {
            var before=s.physics().bodies().stream().map(RigidBody::state).toList();
            long start=System.nanoTime();
            try { s.session().tick(); if(s.session().failure()!=null) throw new IllegalStateException(s.session().failure()); }
            catch(RuntimeException ex) { System.out.println("FAILED tick="+tick+" reason="+ex); return; }
            int phase=tick<16?0:tick<26?1:tick<400?2:3; elapsed[phase][counts[phase]++]=System.nanoTime()-start; var d=s.physics().interactionDiagnostics(); var p=s.coupling().phaseTimes(); var profile=s.physics().profile();
            physics[phase]+=p.physicsNanos(); occupancy[phase]+=p.occupancyNanos(); planning[phase]+=p.planningNanos(); wakes[phase]+=d.wokenBodies(); checks[phase]+=d.checks(); contacts[phase]+=d.rawContacts(); maxPenetration[phase]=Math.max(maxPenetration[phase],d.maxPenetrationCells());
            staticQuery[phase]+=profile.staticQueryNanos(); bodyQuery[phase]+=profile.bodyQueryNanos(); staticSolve[phase]+=profile.staticSolverNanos(); bodySolve[phase]+=profile.bodySolverNanos(); correction[phase]=Math.max(correction[phase],profile.maxPositionCorrectionCells());
            staticPenetration[phase]=Math.max(staticPenetration[phase],profile.maxStaticPenetrationCells());
            reduced[phase]+=d.reducedContacts(); pairs[phase]+=d.pairs(); iterations[phase]+=d.solverIterations(); micro[phase]+=s.physics().motionDiagnostics().executedMicrosteps();
            var after=s.physics().bodies().stream().map(RigidBody::state).toList();
            timeline[tick]=new TickSample(elapsed[phase][counts[phase]-1],p,profile,s.physics().motionDiagnostics(),d,s.physics().diagnostics(),before,after);
            for(int i=0;i<3;i++) { var b=s.physics().body(i+1); var previous=before.get(i); maxMotion[phase]=Math.max(maxMotion[phase],Math.max(Math.hypot(b.xCells()-previous.x(),b.yCells()-previous.y()),Math.abs(b.angleRadians()-previous.angle()))); maxSpeed[phase]=Math.max(maxSpeed[phase],Math.hypot(b.velocityXMetresPerSecond(),b.velocityYMetresPerSecond())); }
            if(tick>=14 && tick<=32 || tick==40 || tick==49 || tick==99 || tick==249 || tick==499 || tick==749 || tick==999)
                System.out.printf(Locale.ROOT,"tick=%d total=%.3fms physics=%.3fms staticQuery=%.3fms bodyQuery=%.3fms staticSolve=%.3fms bodySolve=%.3fms occupancy=%.3fms planning=%.3fms micro=%d pairs=%d checks=%d raw=%d reduced=%d iterations=%d correction=%.6f sleeping=%d wakes=%d penetration=%.5f states=%s%n",tick,timeline[tick].elapsed()/1e6,p.physicsNanos()/1e6,profile.staticQueryNanos()/1e6,profile.bodyQueryNanos()/1e6,profile.staticSolverNanos()/1e6,profile.bodySolverNanos()/1e6,p.occupancyNanos()/1e6,p.planningNanos()/1e6,s.physics().motionDiagnostics().executedMicrosteps(),d.pairs(),d.checks(),d.rawContacts(),d.reducedContacts(),d.solverIterations(),profile.maxPositionCorrectionCells(),d.sleepingBodies(),d.wokenBodies(),d.maxPenetrationCells(),s.physics().bodies().stream().map(b->String.format(Locale.ROOT,"%d:(%.4f,%.4f,%.4f;%.4f,%.4f,%.4f;%s)",b.id(),b.xCells(),b.yCells(),b.angleRadians(),b.velocityXMetresPerSecond(),b.velocityYMetresPerSecond(),b.angularVelocityRadiansPerSecond(),b.sleeping())).toList());
        }
        String[] names={"free-fall 0-15","first-impact 16-25","stacking 26-399","rest 400-999"};
        for(int i=0;i<4;i++) { Arrays.sort(elapsed[i]); double mean=Arrays.stream(elapsed[i]).average().orElseThrow()/1e6; int n=counts[i]; System.out.printf(Locale.ROOT,"%s mean=%.3fms p95=%.3fms worst=%.3fms physics=%.3f staticQuery=%.3f bodyQuery=%.3f staticSolve=%.3f bodySolve=%.3f occupancy=%.3f planning=%.3f wakes=%d pairs/tick=%.1f checks/tick=%.1f raw/tick=%.1f reduced/tick=%.1f iterations/tick=%.1f micro/tick=%.1f maxCorrection=%.6f maxMotion=%.6f maxSpeed=%.5f maxPenetration=%.5f maxStaticPenetration=%.5f%n",names[i],mean,elapsed[i][(int)Math.ceil(n*.95)-1]/1e6,elapsed[i][n-1]/1e6,physics[i]/(double)n/1e6,staticQuery[i]/(double)n/1e6,bodyQuery[i]/(double)n/1e6,staticSolve[i]/(double)n/1e6,bodySolve[i]/(double)n/1e6,occupancy[i]/(double)n/1e6,planning[i]/(double)n/1e6,wakes[i],pairs[i]/(double)n,checks[i]/(double)n,contacts[i]/(double)n,reduced[i]/(double)n,iterations[i]/(double)n,micro[i]/(double)n,correction[i],maxMotion[i],maxSpeed[i],maxPenetration[i],staticPenetration[i]); }
    }
}
