package miningsim.generation;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import miningsim.block.FixedBlockSession;
import miningsim.persistence.FixedSessionIO;
import miningsim.world.Material;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GeologicalLayersTest {
    @ParameterizedTest @ValueSource(ints={4,8}) void calibratedDiversityAcrossFixedSeeds(int size) {
        int soilSeeds=0,sandSeeds=0,gravelSeeds=0,outcropSeeds=0; boolean cellResolution=false;
        for(int seed=0;seed<32;seed++) {
            var c=WorldGenerationConfig.preset(seed,size); int soil=0,sand=0,gravel=0,outcropRun=0,longest=0;
            for(int x=0;x<c.width();x+=size) {
                var l=WorldGenerator.layers(c,x); soil=Math.max(soil,l.soil()); sand=Math.max(sand,l.sand()); gravel=Math.max(gravel,l.gravel());
                cellResolution|=l.soil()%size!=0 || l.sand()%size!=0 || l.gravel()%size!=0;
                if(l.surface()==l.stoneTop()) { outcropRun+=size; longest=Math.max(longest,outcropRun); } else outcropRun=0;
            }
            if(soil>=16) soilSeeds++; if(sand>=32) sandSeeds++; if(gravel>=24) gravelSeeds++; if(longest>=32) outcropSeeds++;
        }
        assertTrue(soilSeeds>=8); assertTrue(sandSeeds>=8); assertTrue(gravelSeeds>=8); assertTrue(outcropSeeds>=4); assertTrue(cellResolution);
    }
    private byte[] save(FixedBlockSession s) throws Exception { var out=new ByteArrayOutputStream(); FixedSessionIO.write(s,out); return out.toByteArray(); }
    private int[] counts(FixedBlockSession s) {
        int[] counts=new int[Material.values().length];
        for(int y=0;y<s.world().height();y++) for(int x=0;x<s.world().width();x++) counts[Byte.toUnsignedInt(s.world().get(x,y).id())]++;
        return counts;
    }
    @ParameterizedTest @ValueSource(ints={4,8}) void layerProfilesAreDeterministicBoundedAndContiguous(int size) throws Exception {
        boolean soil=false,sand=false,gravel=false,outcrop=false; byte[] previous=null;
        for(long seed:new long[]{1,71,12345,987654321}) {
            var c=WorldGenerationConfig.preset(seed,size); var a=WorldGenerator.generate(c).session(); var b=WorldGenerator.generate(c).session();
            byte[] data=save(a); assertArrayEquals(data,save(b)); if(previous!=null) assertFalse(Arrays.equals(previous,data)); previous=data;
            var ids=new java.util.HashSet<Long>(); for(var block:a.blocks().blocks()) assertTrue(ids.add(block.id()));
            for(int x=0;x<c.width();x++) {
                var l=WorldGenerator.layers(c,x); assertEquals(0,l.surface()%size); assertEquals(0,l.stoneTop()%size);
                assertTrue(l.soil()>=0 && l.soil()<=24); assertTrue(l.sand()>=0 && l.sand()<=64); assertTrue(l.gravel()>=0 && l.gravel()<=48);
                soil|=l.soil()>0; sand|=l.sand()>0; gravel|=l.gravel()>0; outcrop|=l.surface()==l.stoneTop();
                if(x%size!=0) assertEquals(WorldGenerator.layers(c,x-1),l);
                for(int y=0;y<c.height();y++) {
                    var expected=y==c.height()-1?Material.BEDROCK:y<l.surface()?Material.AIR:y<l.surface()+l.soil()?Material.SOIL:y<l.surface()+l.soil()+l.sand()?Material.SAND:y<l.stoneTop()?Material.GRAVEL:Material.STONE;
                    assertEquals(expected,a.world().get(x,y)); if(expected==Material.STONE) assertNotNull(a.blocks().blockAt(x,y));
                }
            }
        }
        assertTrue(soil && sand && gravel && outcrop);
        var first=WorldGenerationConfig.preset(1,size); var second=WorldGenerationConfig.preset(71,size); boolean layerDifference=false;
        for(int x=0;x<first.width();x+=size) {
            var a=WorldGenerator.layers(first,x); var b=WorldGenerator.layers(second,x);
            layerDifference|=a.soil()!=b.soil() || a.sand()!=b.sand() || a.gravel()!=b.gravel();
            if(x>0) { var p=WorldGenerator.layers(first,x-size); assertTrue(Math.abs(a.soil()-p.soil())<=2*size); assertTrue(Math.abs(a.sand()-p.sand())<=2*size); assertTrue(Math.abs(a.gravel()-p.gravel())<=2*size); }
        }
        assertTrue(layerDifference,"different seeds must affect geological layers");
    }
    @ParameterizedTest @ValueSource(ints={4,8}) void compactWorldsRemainStableAndPersist(int size) throws Exception {
        for(long seed:new long[]{1,71,12345}) {
            var c=new WorldGenerationConfig(seed,128,129,size,48,16,32,8); var s=WorldGenerator.generate(c).session();
            var blocks=s.blocks().saveState(); var total=counts(s); byte[] first=save(s);
            assertArrayEquals(first,save(FixedSessionIO.read(new ByteArrayInputStream(first)).fixed()));
            for(int tick=0;tick<600;tick++) s.tick();
            assertEquals(blocks,s.blocks().saveState()); assertArrayEquals(total,counts(s));
            byte[] settled=save(s); s=FixedSessionIO.read(new ByteArrayInputStream(settled)).fixed(); assertArrayEquals(settled,save(s));
            assertTrue(s.blocks().removeAt(0,128-size)); s.tick();
            s.world().set(120,1,Material.WATER); for(int tick=0;tick<30;tick++) s.tick();
            byte[] edited=save(s); assertArrayEquals(edited,save(FixedSessionIO.read(new ByteArrayInputStream(edited)).fixed()));
        }
    }
    @ParameterizedTest @ValueSource(ints={4,8}) void excavatedSedimentFallsAndWaterIsConserved(int size) throws Exception {
        var c=new WorldGenerationConfig(12345,128,129,size,48,16,32,8); var s=WorldGenerator.generate(c).session();
        int column=-1;
        for(int x=size;x<c.width()-size;x+=size) if(WorldGenerator.layers(c,x).sand()+WorldGenerator.layers(c,x).gravel()>=size) { column=x; break; }
        assertTrue(column>=0,"fixed seed must have a sediment layer");
        var layer=WorldGenerator.layers(c,column); int[] before=counts(s);
        assertTrue(s.blocks().removeAt(column,layer.stoneTop()));
        assertEquals(miningsim.world.World.Mutation.CHANGED,s.world().set(column+size/2,1,Material.WATER));
        for(int i=0;i<40;i++) s.tick(); int[] after=counts(s);
        for(var material:new Material[]{Material.SOIL,Material.SAND,Material.GRAVEL}) assertEquals(before[Byte.toUnsignedInt(material.id())],after[Byte.toUnsignedInt(material.id())]);
        assertEquals(before[Byte.toUnsignedInt(Material.STONE.id())]-size*size,after[Byte.toUnsignedInt(Material.STONE.id())]);
        assertEquals(1,after[Byte.toUnsignedInt(Material.WATER.id())]);
        boolean filled=false; for(int y=layer.stoneTop();y<layer.stoneTop()+size;y++) for(int x=column;x<column+size;x++) if(s.world().get(x,y)==Material.SAND || s.world().get(x,y)==Material.GRAVEL) filled=true;
        assertTrue(filled,"sediment must enter the excavated cavity");
        byte[] edited=save(s); assertArrayEquals(edited,save(FixedSessionIO.read(new ByteArrayInputStream(edited)).fixed()));
    }
}
