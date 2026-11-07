package miningsim.benchmark;

import miningsim.structural.*;
import miningsim.world.*;

/** Single-run exploratory timings, not a microbenchmark. */
public final class RockDamageBenchmark {
    private static void finish(RockConnectivity c) { while (c.pending()) c.advance(100000); }
    public static void main(String[] args) {
        for (int n : new int[]{8, 24, 48, 64}) {
            World w = new World(n,4,World.Mode.BEDROCK_ENABLED);
            w.set(0,2,Material.STONE); for (int x=0;x<n-1;x++) w.set(x,1,Material.STONE);
            RockConnectivity c = new RockConnectivity(w); finish(c);
            var registry = new RockMaterialRegistry.Builder().register(1,new RockProperties(2500,1e8,1e12,1e12,1e12,1e12,0.1)).build();
            RockDamageSystem s = new RockDamageSystem(w,c,registry,9.81);
            long start = System.nanoTime(); var status = s.analyze(0,1); long analysis = System.nanoTime()-start;
            if (status != RockDamageSystem.Status.CONVERGED) { System.out.println("nodes="+n+" status="+status); continue; }
            int elements = s.snapshot().bonds().size();
            double assembly = s.assemblyNanos()/1e6, solve = s.solveNanos()/1e6;
            start = System.nanoTime(); for(int i=0;i<20;i++) s.analyze(0,1); long repeated = System.nanoTime()-start;
            start = System.nanoTime(); for(int i=0;i<20;i++) s.advanceTick(i); long ticks = System.nanoTime()-start;
            c.breakBond(n-3,1,n-2,1); start = System.nanoTime(); finish(c); var split = s.analyze(0,1); long fracture = System.nanoTime()-start;
            w.set(n-2,1,Material.AIR); start = System.nanoTime(); finish(c); var edited = s.analyze(0,1); long rebuild = System.nanoTime()-start;
            System.out.printf("nodes=%d elements=%d status=%s analysis=%.3fms assembly=%.3fms solve=%.3fms repeated=%.3fms/call damage=%.3fms/tick break-rebuild=%.3fms status=%s edit-rebuild=%.3fms status=%s%n",
                    n,elements,status,analysis/1e6,assembly,solve,repeated/20e6,ticks/20e6,fracture/1e6,split,rebuild/1e6,edited);
        }
    }
}
