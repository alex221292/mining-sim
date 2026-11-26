package miningsim.generation;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import miningsim.block.FixedBlockSession;
import miningsim.persistence.FixedSessionIO;
import miningsim.world.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WorldGeneratorTest {
    @Test void matchesM54BaselineCellFingerprints() throws Exception {
        String[][] expected={
            {"63718550959a99d60c3afde53594a91d0f7c0f487ebac21f60cd3b5cafd6f1be","a01f63ca1da77ba231ee1f128f42268eda9e033e5b95fcc1865e6fd2415e4726","66a4e49ec823396b9e1b2ed9f99b01c0bb789cf49c85cda3f417b382aaa51d9c"},
            {"f4c9985e8f31226bf3f2fde70684263d4ebe88a582bc0e6d692e6a21630e0db0","b4f62df31933e3ce936ea801bb28c2c2f688d6da8225dabb54fca13cfa8eb50d","3bd5e429cde0fc29b02fed0ed32b3b426afea1d32d02c6988c71e222b3d5d759"}
        };
        int[] sizes={4,8}; long[] seeds={1,71,12345};
        for(int i=0;i<sizes.length;i++) for(int j=0;j<seeds.length;j++) {
            var c=new WorldGenerationConfig(seeds[j],128,129,sizes[i],48,16,32,8);
            var w=WorldGenerator.generate(c).session().world(); var digest=java.security.MessageDigest.getInstance("SHA-256");
            for(int y=0;y<w.height();y++) for(int x=0;x<w.width();x++) {
                var m=w.get(x,y); digest.update(m.isFixedRock()?Material.STONE.id():m.id());
            }
            assertEquals(expected[i][j],java.util.HexFormat.of().formatHex(digest.digest()));
        }
    }
    private WorldGenerationConfig config(long seed,int size) { return new WorldGenerationConfig(seed,64,65,size,24,8,24,4); }
    private byte[] bytes(FixedBlockSession s) throws Exception { var out=new ByteArrayOutputStream(); FixedSessionIO.write(s,out); return out.toByteArray(); }
    private FixedBlockSession load(byte[] data) throws Exception { return FixedSessionIO.read(new ByteArrayInputStream(data)).fixed(); }
    @Test void rejectsInvalidConfig() {
        assertThrows(IllegalArgumentException.class,()->new WorldGenerationConfig(1,99999,65,8,24,8,24,4));
        assertThrows(IllegalArgumentException.class,()->new WorldGenerationConfig(1,65,65,8,24,8,24,4));
        assertThrows(IllegalArgumentException.class,()->new WorldGenerationConfig(1,64,65,2,24,8,24,4));
        assertThrows(IllegalArgumentException.class,()->new WorldGenerationConfig(1,64,65,8,4,8,24,4));
    }
    @ParameterizedTest @ValueSource(ints={4,8}) void canceledAndInvalidPreparationKeepActiveSessionAndItsQueue(int size) throws Exception {
        var active=WorldGenerator.generate(config(12,size)).session(); byte[] before=bytes(active);
        active.enqueue(()->active.paintCells(50,1,0,Material.WATER));
        assertNull(WorldGenerator.prepare(null));
        assertThrows(IllegalArgumentException.class,()->WorldGenerator.prepare(new WorldGenerationConfig(1,65,65,size,24,8,24,4)));
        assertEquals(load(before).blocks().saveState(),active.blocks().saveState()); assertEquals(Material.AIR,active.world().get(50,1));
        assertTrue(active.hasPendingCommands()); active.applyCommandsAtSaveBarrier(); assertNotEquals(Material.AIR,active.world().get(50,1));
        var replacement=WorldGenerator.prepare(config(13,size)).session(); assertFalse(replacement.hasPendingCommands());
    }
    @ParameterizedTest @ValueSource(ints={4,8}) void deterministicOwnershipAndPersistence(int size) throws Exception {
        var c=config(123,size); var a=WorldGenerator.generate(c).session(); var b=WorldGenerator.generate(c).session();
        assertArrayEquals(bytes(a),bytes(b));
        b.tick(); b=WorldGenerator.generate(c).session(); assertArrayEquals(bytes(a),bytes(b));
        var different=WorldGenerator.generate(config(987,size)).session(); assertFalse(java.util.Arrays.equals(bytes(a),bytes(different)));
        int stone=0,soil=0,air=0; var ids=new java.util.HashSet<Long>();
        for(var block:a.blocks().blocks()) { assertTrue(ids.add(block.id())); assertEquals(0,block.x()%size); assertEquals(0,block.y()%size); }
        for(int y=0;y<65;y++) for(int x=0;x<64;x++) {
            var m=a.world().get(x,y); if(y==64) assertEquals(Material.BEDROCK,m);
            int surface=WorldGenerator.surface(c,x); assertTrue(surface>=c.baseSurfaceHeight()-c.terrainAmplitude()-size && surface<=c.baseSurfaceHeight()+c.terrainAmplitude()+size);
            var layers=WorldGenerator.layers(c,x);
            if(y<64) { var expected=y<surface?Material.AIR:y<surface+layers.soil()?Material.SOIL:y<surface+layers.soil()+layers.sand()?Material.SAND:y<layers.stoneTop()?Material.GRAVEL:Material.STONE; if(expected==Material.AIR && m==Material.WATER) assertNull(a.blocks().blockAt(x,y)); else assertEquals(expected,m.isFixedRock()?Material.STONE:m); }
            if(m.isFixedRock()) { stone++; assertNotNull(a.blocks().blockAt(x,y)); } if(m==Material.SOIL) soil++; if(m==Material.AIR) air++;
        }
        assertEquals(a.blocks().size()*size*size,stone); assertTrue(soil>0); assertTrue(air>0);
        var state=a.blocks().saveState(); for(int i=0;i<600;i++) a.tick(); assertEquals(state,a.blocks().saveState());
        var loaded=load(bytes(a)); assertArrayEquals(bytes(a),bytes(loaded));
        loaded.world().set(60,1,Material.SOIL); loaded.tick(); assertEquals(Material.AIR,loaded.world().get(60,1));
        assertTrue(loaded.blocks().removeAt(0,64-size)); loaded.tick();
        loaded.enqueue(()->loaded.paintCells(50,1,0,Material.WATER)); loaded.enqueue(()->loaded.paintCells(55,1,0,Material.SAND)); loaded.tick();
        assertTrue(loaded.world().occupiedCount()>0);
    }
    @ParameterizedTest @ValueSource(ints={4,8}) void replayTenThousandTicks(int size) throws Exception {
        var replayConfig=new WorldGenerationConfig(12345,128,65,size,24,8,24,4);
        var a=WorldGenerator.generate(replayConfig).session(); var b=WorldGenerator.generate(replayConfig).session();
        int initialBlocks=a.blocks().size(); int[] initial=new int[Material.values().length];
        for(int y=0;y<65;y++) for(int x=0;x<a.world().width();x++) initial[Byte.toUnsignedInt(a.world().get(x,y).id())]++;
        assertTrue(initial[Byte.toUnsignedInt(Material.SAND.id())]>0); assertTrue(initial[Byte.toUnsignedInt(Material.GRAVEL.id())]>0);
        for(int t=0;t<10000;t++) {
            for(var s:java.util.List.of(a,b)) {
                if(t==1) { s.enqueue(()->s.paintCells(50,1,0,Material.WATER)); s.enqueue(()->s.paintCells(55,1,0,Material.SAND)); }
                if(t==50) s.enqueue(()->s.blocks().removeAt(0,64-size));
                if(t==100) s.enqueue(()->s.blockStroke(24,0,32,0,false));
                if(t==101) s.enqueue(()->s.blocks().releaseStroke());
                s.tick();
            }
            if(t%500==0) {
                byte[] snapshot=bytes(a); assertArrayEquals(snapshot,bytes(b)); a=load(snapshot); b=load(snapshot);
                int stone=0; int[] counts=new int[Material.values().length];
                for(int y=0;y<65;y++) for(int x=0;x<a.world().width();x++) { var m=a.world().get(x,y); counts[Byte.toUnsignedInt(m.id())]++; if(m.isFixedRock()) { stone++; assertNotNull(a.blocks().blockAt(x,y)); } }
                assertEquals(a.blocks().size()*size*size,stone); assertEquals(initial[Byte.toUnsignedInt(Material.SOIL.id())],counts[Byte.toUnsignedInt(Material.SOIL.id())]);
                assertEquals(initial[Byte.toUnsignedInt(Material.GRAVEL.id())],counts[Byte.toUnsignedInt(Material.GRAVEL.id())]);
                assertEquals(initial[Byte.toUnsignedInt(Material.SAND.id())]+(t>=1?1:0),counts[Byte.toUnsignedInt(Material.SAND.id())]); assertEquals(initial[Byte.toUnsignedInt(Material.WATER.id())]+(t>=1?1:0),counts[Byte.toUnsignedInt(Material.WATER.id())]);
                assertEquals(initialBlocks-(t>=50?1:0)+(t>=100?8/size+1:0),a.blocks().size());
            }
        }
        assertArrayEquals(bytes(a),bytes(b));
    }
}
