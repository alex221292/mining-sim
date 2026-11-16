package miningsim.physics;

import java.util.Arrays;
import java.util.Set;

/** Standalone physics-only diagnostic; not a correctness or frame-time benchmark. */
public final class HeldSolverBenchmark {
    private HeldSolverBenchmark() { }
    public static void main(String[] args) {
        System.out.println(System.getProperty("java.runtime.version")+" "+System.getProperty("java.vm.name"));
        for(var held:java.util.List.of(Set.<Long>of(),Set.of(1L),Set.of(1L,2L))) {
            var fixture=LargeCompoundBenchmark.fixture("large"); var physics=fixture.session().physics();
            long[] times=new long[80]; int count=0; String failure="none";
            for(int i=0;i<110;i++) {
                long start=System.nanoTime();
                try { physics.step(held); } catch(RuntimeException ex) { failure=ex.toString(); break; }
                long elapsed=System.nanoTime()-start;
                if(i>=30) times[count++]=elapsed;
            }
            if(count>0) {
                var values=Arrays.copyOf(times,count); Arrays.sort(values);
                System.out.printf(java.util.Locale.ROOT,"64x64 sizes=%s held=%s n=%d physics mean/p95=%.3f/%.3f ms failure=%s%n",fixture.sizes(),held,count,Arrays.stream(values).average().orElseThrow()/1e6,values[(int)Math.ceil(count*.95)-1]/1e6,failure);
            } else System.out.println("held="+held+" failure="+failure);
        }
    }
}
