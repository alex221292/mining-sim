package miningsim.block;

import java.util.Arrays;
import java.util.Locale;
import miningsim.world.Material;
import miningsim.world.World;

/** Headless equal-object/equal-area comparison; fresh deterministic scenes. */
public final class BlockSizeBenchmark {
    private BlockSizeBenchmark() { }
    private static FixedBlockSession session(World w,int size) {
        if(size==8) return new FixedBlockSession(w,12345);
        try { return FixedBlockSession.class.getConstructor(World.class,long.class,int.class).newInstance(w,12345L,size); }
        catch(ReflectiveOperationException ex) { throw new IllegalStateException(ex); }
    }
    private static FixedBlockSession scene(int size,int count,String type) {
        var w=new World(800,401,World.Mode.BEDROCK_ENABLED); var s=session(w,size);
        if(type.equals("static")) {
            int cols=w.width()/size;
            for(int i=0;i<count;i++) if(!s.blocks().place((i%cols)*size,400-size-(i/cols)*size,false)) throw new IllegalStateException("static fixture");
        } else {
            for(int i=0;i<count;i++) {
                int x=i*size*2; if(!s.blocks().place(x,8*size,false)) throw new IllegalStateException("moving fixture");
                if(type.equals("water")) for(int y=9*size;y<10*size;y++) for(int dx=0;dx<size;dx++) w.set(x+dx,y,Material.WATER);
            }
            for(int i=0;i<FixedBlockWorld.FALL_DELAY_TICKS;i++) s.blocks().step();
        }
        return s;
    }
    public static void main(String[] args) {
        int size=args.length>0?Integer.parseInt(args[0]):8,runs=args.length>1?Integer.parseInt(args[1]):10;
        System.out.println(System.getProperty("java.runtime.version")+" "+System.getProperty("java.vm.name")+"; size="+size+"; 800x401; seed=12345; warmup=2; runs="+runs);
        for(int count:new int[]{100,1000,4000,5000}) measure(size,count,"static",runs);
        measure(size,10,"fall",runs); measure(size,10,"water",runs);
    }
    private static void measure(int size,int count,String type,int runs) {
        long[] block=new long[runs],full=new long[runs];
        for(int i=-2;i<runs;i++) {
            var s=scene(size,count,type); long start=System.nanoTime(); s.tick(); long elapsed=System.nanoTime()-start;
            if(!type.equals("static") && s.blocks().tickDiagnostics().moved()!=count) throw new IllegalStateException("Not all blocks moved: "+type);
            if(i>=0) { block[i]=s.blocks().updateNanos(); full[i]=elapsed; }
        }
        Arrays.sort(block); Arrays.sort(full); int p=(int)Math.ceil(runs*.95)-1;
        System.out.printf(Locale.ROOT,"%s size=%d count=%d cells=%d block=%.3f/%.3fms full=%.3f/%.3fms%n",type,size,count,count*size*size,
                Arrays.stream(block).average().orElseThrow()/1e6,block[p]/1e6,Arrays.stream(full).average().orElseThrow()/1e6,full[p]/1e6);
    }
}
