package miningsim.block;

import java.util.Arrays;
import java.util.Locale;
import miningsim.world.Material;
import miningsim.world.World;

/** Identical fixed-seed scenes; full tick and block phase are recorded separately. */
public final class StabilizationBenchmark {
    private StabilizationBenchmark() { }
    private static FixedBlockSession fixture(String kind) {
        int width=kind.equals("static")?800:160; var w=new World(width,161,World.Mode.BEDROCK_ENABLED);
        var s=new FixedBlockSession(w,12345);
        if(kind.equals("static")) {
            for(int i=0;i<1000;i++) if(!s.blocks().place((i%100)*8,152-(i/100)*8,false)) throw new IllegalStateException();
        } else {
            for(int i=0;i<10;i++) if(!s.blocks().place(i*16,24,false)) throw new IllegalStateException();
            if(kind.equals("water")) for(int i=0;i<10;i++) for(int y=32;y<40;y++) for(int x=i*16;x<i*16+8;x++) w.set(x,y,Material.WATER);
        }
        return s;
    }
    public static void main(String[] args) {
        int runs=args.length==0?20:Integer.parseInt(args[0]);
        int delay=0;
        try { delay=FixedBlockWorld.class.getField("FALL_DELAY_TICKS").getInt(null); }
        catch(NoSuchFieldException ignored) { /* M4.6b.1 baseline has no delay. */ }
        catch(ReflectiveOperationException ex) { throw new IllegalStateException(ex); }
        System.out.println(System.getProperty("java.runtime.version")+" "+System.getProperty("java.vm.name")+"; seed=12345 warmup=3 runs="+runs+"; 800x161 static, 160x161 fall/water");
        for(var kind:new String[]{"static","fall","water"}) {
            long[] block=new long[runs],full=new long[runs];
            for(int run=-3;run<runs;run++) {
                var s=fixture(kind);
                if(!kind.equals("static")) for(int i=0;i<delay;i++) s.blocks().step();
                long start=System.nanoTime(); s.tick(); long elapsed=System.nanoTime()-start;
                if(!kind.equals("static") && s.blocks().tickDiagnostics().moved()==0) throw new IllegalStateException("No movement: "+kind);
                if(run>=0) { block[run]=s.blocks().updateNanos(); full[run]=elapsed; }
            }
            report(kind+".block",block); report(kind+".full",full);
        }
    }
    private static void report(String name,long[] samples) {
        Arrays.sort(samples); System.out.printf(Locale.ROOT,"%s n=%d mean=%.3fms p95=%.3fms%n",name,samples.length,Arrays.stream(samples).average().orElseThrow()/1e6,samples[(int)Math.ceil(samples.length*.95)-1]/1e6);
    }
}
