package miningsim.benchmark;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import miningsim.block.FixedBlockSession;
import miningsim.persistence.FixedSessionIO;
import miningsim.world.Material;
import miningsim.world.World;
import miningsim.resource.OreId;
import miningsim.resource.OreComposition;
import java.util.Arrays;
import java.util.Locale;

public final class OreBenchmark {
    public static void main(String[] args) throws Exception {
        if(args.length>0 && args[0].equals("fixture")) {
            var s=new FixedBlockSession(new World(8,9,World.Mode.BEDROCK_ENABLED),1,4);
            s.blocks().place(0,0,false,Material.GRANITE);
            var out=new ByteArrayOutputStream(); FixedSessionIO.write(s,out);
            System.out.println(Base64.getEncoder().encodeToString(out.toByteArray()));
            return;
        }
        System.out.println(System.getProperty("java.runtime.version")+" world=800x401 seed=12345 warmup=20 samples=100 ms mean/p95/max");
        var one=OreComposition.of(new OreComposition.Component(OreId.IRON,120000));
        var four=OreComposition.of(new OreComposition.Component(OreId.IRON,120000),new OreComposition.Component(OreId.COPPER,25000),new OreComposition.Component(OreId.SILVER,80),new OreComposition.Component(OreId.GOLD,5));
        for(int size:new int[]{8,4}) for(String scene:new String[]{"static","water"}) for(var ores:new OreComposition[]{OreComposition.EMPTY,one,four}) {
            var s=new FixedBlockSession(new World(800,401,World.Mode.BEDROCK_ENABLED),12345,size);
            if(scene.equals("static")) {
                int columns=800/size; for(int i=0;i<1000;i++) if(!s.blocks().place(i%columns*size,400-size-i/columns*size,false,Material.STONE,ores)) throw new IllegalStateException("fixture");
            } else {
                for(int y=400-10*size;y<400;y++) for(int x=0;x<20*size;x++) s.world().set(x,y,Material.WATER);
                for(int i=0;i<10;i++) if(!s.blocks().place(i*2*size,400-13*size,false,Material.STONE,ores)) throw new IllegalStateException("fixture");
            }
            long[] ticks=new long[100];
            for(int i=-20;i<100;i++) { long start=System.nanoTime(); s.tick(); if(i>=0) ticks[i]=System.nanoTime()-start; }
            Arrays.sort(ticks);
            System.out.printf(Locale.ROOT,"size=%d scene=%s components=%d blocks=%d mean=%.3f p95=%.3f max=%.3f%n",size,scene,ores.components().size(),s.blocks().size(),Arrays.stream(ticks).average().orElseThrow()/1e6,ticks[94]/1e6,ticks[99]/1e6);
        }
    }
}
