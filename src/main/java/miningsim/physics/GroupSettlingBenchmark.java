package miningsim.physics;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import miningsim.structural.SettledRockRegistry;

/** Repeated fresh stack fixtures; excludes rendering and warmup from timed samples. */
public final class GroupSettlingBenchmark {
    private GroupSettlingBenchmark() { }
    public static void main(String[] args) {
        int runs=args.length==0?5:Integer.parseInt(args[0]);
        long[] plans=new long[runs],commits=new long[runs],before=new long[runs*60],after=new long[runs*60];
        long[] physicsBefore=new long[runs*60],physicsAfter=new long[runs*60];
        long checksBefore=0,checksAfter=0,pairsBefore=0,pairsAfter=0;
        for(int run=0;run<runs;run++) {
            var s=LargeCompoundBenchmark.fixture("large").session(); var topology=s.structural().connectivity();
            var settled=new SettledRockRegistry(s.world(),topology,10,1024); var ids=List.of(1L,2L,3L);
            var eligibility=new GroupSettlingEligibility(ids);
            for(int i=0;i<500;i++) {
                long t=System.nanoTime(); s.tick(); long elapsed=System.nanoTime()-t;
                if(s.failure()!=null) throw new IllegalStateException(s.failure());
                eligibility.observe(s.physics(),s.coupled(),s.world(),topology,settled);
                if(i>=440) { before[run*60+i-440]=elapsed; physicsBefore[run*60+i-440]=s.coupled().phaseTimes().physicsNanos(); checksBefore+=s.physics().interactionDiagnostics().checks(); pairsBefore+=s.physics().interactionDiagnostics().pairs(); }
            }
            long t=System.nanoTime(); var plan=GroupSettlingCommit.prepareGroupSettling(ids,s.physics(),s.coupled(),s.world(),s.occupancy(),topology,settled,eligibility,2_000_000); plans[run]=System.nanoTime()-t;
            if(plan.status()!=GroupSettlingCommit.Status.READY) throw new IllegalStateException(plan.toString());
            t=System.nanoTime(); var result=s.world().commitGroupSettling(plan.prepared()); commits[run]=System.nanoTime()-t;
            if(result!=miningsim.world.World.BatchResult.CHANGED) throw new IllegalStateException(result.toString());
            for(int i=0;i<60;i++) { t=System.nanoTime(); s.tick(); after[run*60+i]=System.nanoTime()-t;
                if(s.failure()!=null) throw new IllegalStateException(s.failure()); physicsAfter[run*60+i]=s.coupled().phaseTimes().physicsNanos(); checksAfter+=s.physics().interactionDiagnostics().checks(); pairsAfter+=s.physics().interactionDiagnostics().pairs(); }
        }
        System.out.printf(Locale.ROOT,"%s 64x64 stack 134/134/526 runs=%d planning mean/p95=%s commit=%s tick before=%s after=%s bodies=3/0 pairs before/after=%.2f/%.2f SAT checks before/after=%.2f/%.2f%n",
                System.getProperty("java.runtime.version"),runs,stats(plans),stats(commits),stats(before),stats(after),
                pairsBefore/(runs*60.0),pairsAfter/(runs*60.0),checksBefore/(runs*60.0),checksAfter/(runs*60.0));
        System.out.printf(Locale.ROOT,"physics before=%s after=%s%n",stats(physicsBefore),stats(physicsAfter));
    }
    private static String stats(long[] values) {
        double mean=Arrays.stream(values).average().orElseThrow()/1e6; Arrays.sort(values);
        return String.format(Locale.ROOT,"%.3f/%.3fms",mean,values[(int)Math.ceil(values.length*0.95)-1]/1e6);
    }
}
