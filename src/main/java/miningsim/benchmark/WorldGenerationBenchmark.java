package miningsim.benchmark;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Locale;
import miningsim.generation.WorldGenerationConfig;
import miningsim.generation.WorldGenerator;
import miningsim.persistence.FixedSessionIO;
import miningsim.world.Material;

/** Headless exploratory timings; serialization uses memory streams, not disk. */
public final class WorldGenerationBenchmark {
    private static String stats(long[] values) {
        Arrays.sort(values); return String.format(Locale.ROOT,"%.3f/%.3f/%.3f",Arrays.stream(values).average().orElseThrow()/1e6,values[(int)Math.ceil(values.length*.95)-1]/1e6,values[values.length-1]/1e6);
    }
    public static void main(String[] args) throws Exception {
        System.out.println(System.getProperty("java.runtime.version")+" world=800x401 seed=12345; generation warmup=2 runs=10, tick warmup=20 runs=100; ms mean/p95/max");
        for(int size:new int[]{8,4}) {
            var config=WorldGenerationConfig.preset(12345,size); long[] generation=new long[10];
            for(int i=-2;i<10;i++) { long start=System.nanoTime(); var g=WorldGenerator.generate(config); if(i>=0) generation[i]=System.nanoTime()-start; }
            var s=WorldGenerator.generate(config).session(); long[] counts=new long[Material.values().length];
            for(int y=0;y<s.world().height();y++) for(int x=0;x<s.world().width();x++) counts[Byte.toUnsignedInt(s.world().get(x,y).id())]++;
            long start=System.nanoTime(); s.tick(); long first=System.nanoTime()-start;
            long[] early=new long[120]; for(int i=0;i<early.length;i++) { start=System.nanoTime(); s.tick(); early[i]=System.nanoTime()-start; }
            long[] stable=new long[100],edited=new long[100],write=new long[10],read=new long[10];
            for(int i=-20;i<100;i++) { start=System.nanoTime(); s.tick(); if(i>=0) stable[i]=System.nanoTime()-start; }
            int count=s.blocks().size(); s.blocks().removeAt(0,400-size);
            for(int i=0;i<100;i++) { start=System.nanoTime(); s.tick(); edited[i]=System.nanoTime()-start; }
            int bytes=0; for(int i=-2;i<10;i++) {
                var out=new ByteArrayOutputStream(); start=System.nanoTime(); FixedSessionIO.write(s,out); long w=System.nanoTime()-start; byte[] data=out.toByteArray();
                start=System.nanoTime(); FixedSessionIO.read(new ByteArrayInputStream(data)); long r=System.nanoTime()-start;
                if(i>=0) { write[i]=w; read[i]=r; bytes=data.length; }
            }
            System.out.printf(Locale.ROOT,"size=%d blocks=%d bytes=%d materials=%s generate=%s first=%.3f early120=%s steady=%s edit=%s write=%s read=%s%n",size,count,bytes,Arrays.toString(counts),stats(generation),first/1e6,stats(early),stats(stable),stats(edited),stats(write),stats(read));
        }
    }
}
