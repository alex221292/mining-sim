package miningsim.block;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.util.Base64;
import miningsim.persistence.FixedSessionIO;
import miningsim.resource.OreComposition;
import miningsim.resource.OreId;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.api.Test;
import miningsim.generation.WorldGenerator;
import miningsim.generation.WorldGenerationConfig;

class OreContentTest {
    private static OreComposition.Component c(OreId id,int ppm) { return new OreComposition.Component(id,ppm); }
    private static final OreComposition ORES=OreComposition.of(c(OreId.GOLD,1),c(OreId.IRON,120000),c(OreId.COPPER,25000),c(OreId.SILVER,80));
    @Test void validationAndCanonicalForm() {
        assertSame(OreComposition.EMPTY,OreComposition.of());
        assertEquals(0,OreComposition.EMPTY.ppm(OreId.IRON));
        assertEquals(1,ORES.ppm(OreId.GOLD));
        var input=new OreComposition.Component[]{c(OreId.GOLD,1),c(OreId.IRON,999999)};
        var composition=OreComposition.of(input); input[0]=c(OreId.COPPER,5);
        assertEquals(OreComposition.of(c(OreId.IRON,999999),c(OreId.GOLD,1)),composition);
        assertThrows(UnsupportedOperationException.class,()->composition.components().clear());
        for(int invalid:new int[]{-1,0,1000001}) assertThrows(IllegalArgumentException.class,()->c(OreId.IRON,invalid));
        assertThrows(IllegalArgumentException.class,()->OreComposition.of(c(OreId.IRON,1),c(OreId.IRON,2)));
        assertThrows(IllegalArgumentException.class,()->OreComposition.of(c(OreId.IRON,1000000),c(OreId.GOLD,1)));
        assertThrows(IllegalArgumentException.class,()->OreId.fromId(0));
    }
    private byte[] save(FixedBlockSession s) throws IOException { var out=new ByteArrayOutputStream(); FixedSessionIO.write(s,out); return out.toByteArray(); }
    private byte[] withoutOre(FixedBlockSession s) throws IOException {
        byte[] v3=java.util.Arrays.copyOf(save(s),36+s.world().width()*s.world().height()+20*s.blocks().size()+4);
        v3[7]=3;
        return checksum(v3);
    }
    @Test void generatedOreDoesNotChangeSixHundredTicks() throws Exception {
        for(int size:new int[]{4,8}) for(int seed:new int[]{8,12345}) {
            var withOre=WorldGenerator.generate(WorldGenerationConfig.preset(seed,size)).session();
            var initial=withOre.blocks().blocks().stream().collect(java.util.stream.Collectors.toMap(FixedBlockWorld.Block::id,FixedBlockWorld.Block::ores));
            var without=FixedSessionIO.read(new ByteArrayInputStream(withoutOre(withOre))).fixed();
            assertArrayEquals(withoutOre(withOre),withoutOre(without));
            for(int tick=0;tick<600;tick++) { withOre.tick(); without.tick(); }
            assertArrayEquals(withoutOre(withOre),withoutOre(without));
            for(var block:withOre.blocks().blocks()) assertEquals(initial.get(block.id()),block.ores());
            new FixedBlockWorld(withOre.world(),size).restoreState(withOre.blocks().saveState());
        }
    }
    @Test void generatedOreRoundTripAndOwnership() throws Exception {
        for(int size:new int[]{4,8}) {
            var session=WorldGenerator.generate(WorldGenerationConfig.preset(357355,size)).session();
            assertTrue(session.blocks().blocks().stream().anyMatch(b->!b.ores().components().isEmpty()));
            var loaded=FixedSessionIO.read(new ByteArrayInputStream(save(session))).fixed();
            assertEquals(session.blocks().saveState(),loaded.blocks().saveState());
            new FixedBlockWorld(loaded.world(),size).restoreState(loaded.blocks().saveState());
            for(int y=0;y<loaded.world().height();y++) for(int x=0;x<loaded.world().width();x++) {
                var material=loaded.world().get(x,y);
                assertEquals(material.isFixedRock(),loaded.blocks().blockAt(x,y)!=null);
            }
        }
    }
    @Test void v3FixtureReadAndV4RoundTrip() throws Exception {
        byte[] v3=Base64.getDecoder().decode("TVNJTQAAAAMAAAAIAAAACQAAAAEAAAAEAAAAAQAAAAAAAAACCAgICAAAAAAICAgIAAAAAAgICAgAAAAACAgICAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAcHBwcHBwcHAAAAAAAAAAEAAAAAAAAAAAAAAAGHJQ6s");
        var old=FixedSessionIO.read(new ByteArrayInputStream(v3)).fixed();
        assertEquals(Material.GRANITE,old.world().get(0,0)); assertEquals(OreComposition.EMPTY,old.blocks().blockAt(0,0).ores());
        for(int z:new int[]{4,8}) {
            var s=new FixedBlockSession(new World(64,65,World.Mode.BEDROCK_ENABLED),1,z);
            assertTrue(s.blocks().place(z,0,false,Material.MARBLE,ORES));
            assertTrue(s.blocks().place(2*z,0,false,Material.STONE));
            for(int i=0;i<3;i++) s.tick();
            var data=save(s); assertEquals(4,data[7]);
            var loaded=FixedSessionIO.read(new ByteArrayInputStream(data)).fixed();
            assertEquals(s.blocks().saveState(),loaded.blocks().saveState());
            assertEquals(ORES,loaded.blocks().blockAt(z,0).ores());
        }
    }
    @Test void physicalParityAndOwnership() {
        for(int z:new int[]{4,8}) {
            var a=new FixedBlockSession(new World(64,65,World.Mode.BEDROCK_ENABLED),42,z);
            var b=new FixedBlockSession(new World(64,65,World.Mode.BEDROCK_ENABLED),42,z);
            assertTrue(a.blocks().place(z,0,false,Material.GRANITE));
            assertTrue(b.blocks().place(z,0,false,Material.GRANITE,ORES));
            for(var s:new FixedBlockSession[]{a,b}) for(int y=z;y<3*z;y++) for(int x=0;x<4*z;x++) s.world().set(x,y,Material.WATER);
            for(int t=0;t<30;t++) {
                a.tick(); b.tick();
                var x=a.blocks().saveState().blocks().getFirst(); var y=b.blocks().saveState().blocks().getFirst();
                assertEquals(x.id(),y.id()); assertEquals(x.x(),y.x()); assertEquals(x.y(),y.y()); assertEquals(x.state(),y.state()); assertEquals(x.unsupportedTicks(),y.unsupportedTicks()); assertEquals(ORES,y.ores());
                for(int row=0;row<65;row++) for(int col=0;col<64;col++) assertEquals(a.world().get(col,row),b.world().get(col,row));
            }
            new FixedBlockWorld(b.world(),z).restoreState(b.blocks().saveState());
            var block=b.blocks().blocks().getFirst(); assertTrue(b.blocks().removeAt(block.x(),block.y())); assertEquals(0,b.blocks().size());
        }
    }
    @Test void generatedWorldsMatchM55cIgnoringOre() throws Exception {
        var digest=java.security.MessageDigest.getInstance("SHA-256");
        for(int size:new int[]{4,8}) for(int i=0;i<19;i++) {
            int seed=i<16?i:i==16?431039:i==17?357355:12345;
            var s=WorldGenerator.generate(WorldGenerationConfig.preset(seed,size)).session();
            byte[] v4=save(s),v3=java.util.Arrays.copyOf(v4,36+s.world().width()*s.world().height()+20*s.blocks().size()+4); v3[7]=3;
            var crc=new java.util.zip.CRC32(); crc.update(v3,0,v3.length-4);
            int value=(int)crc.getValue(); for(int j=0;j<4;j++) v3[v3.length-4+j]=(byte)(value >>> (24-8*j));
            digest.update(v3);
        }
        assertEquals("0aeb66ecdea9b33445d989fc7311bcbd957d3e464c1417a6d21a2edbf547cda5",java.util.HexFormat.of().formatHex(digest.digest()));
    }
    private byte[] checksum(byte[] bytes) {
        var crc=new java.util.zip.CRC32(); crc.update(bytes,0,bytes.length-4); int value=(int)crc.getValue();
        for(int j=0;j<4;j++) bytes[bytes.length-4+j]=(byte)(value >>> (24-8*j)); return bytes;
    }
    @Test void malformedOreRecordsAreRejected() throws Exception {
        var s=new FixedBlockSession(new World(16,17,World.Mode.BEDROCK_ENABLED),1,4);
        assertTrue(s.blocks().place(0,0,false,Material.STONE,ORES));
        byte[] good=save(s); int section=36+16*17+20,record=section+4;
        for(int kind=0;kind<10;kind++) {
            byte[] bad=good.clone(); var buf=java.nio.ByteBuffer.wrap(bad);
            switch(kind) {
                case 0 -> buf.putInt(section,2);
                case 1 -> buf.putLong(record,99);
                case 2 -> buf.put(record+8,(byte)0);
                case 3 -> buf.put(record+8,(byte)5);
                case 4 -> buf.put(record+9,(byte)0);
                case 5 -> buf.put(record+14,bad[record+9]);
                case 6 -> buf.putInt(record+10,0);
                case 7 -> buf.putInt(record+10,1_000_000);
                case 8 -> buf.putInt(record+10,-1);
                case 9 -> buf.putInt(record+10,1_000_001);
            }
            assertThrows(IOException.class,()->FixedSessionIO.read(new ByteArrayInputStream(checksum(bad))),"case "+kind);
        }
        assertThrows(IOException.class,()->FixedSessionIO.read(new ByteArrayInputStream(java.util.Arrays.copyOf(good,good.length-6))));
    }
    @Test void referenceSceneAndNewSessionsHaveIsolatedContent() {
        for(int size:new int[]{4,8}) {
            var scene=OreReferenceScene.create(size);
            assertTrue(scene.blocks().blocks().stream().anyMatch(b->b.ores().components().size()==4));
            assertTrue(scene.blocks().blocks().stream().anyMatch(b->b.ores().components().isEmpty()));
            assertEquals(0,scene.cellularTicks());
            var clean=new FixedBlockSession(new World(160,97,World.Mode.BEDROCK_ENABLED),1,size==4?8:4);
            assertEquals(0,clean.blocks().size());
            assertTrue(clean.blocks().place(0,0,false,Material.GRANITE));
            assertSame(OreComposition.EMPTY,clean.blocks().blockAt(0,0).ores());
        }
    }
    @Test void mixedSupportLossAndRestorationPreserveOreAndPhysics() {
        for(int z:new int[]{4,8}) {
            var a=new FixedBlockSession(new World(128,97,World.Mode.BEDROCK_ENABLED),17,z);
            var b=new FixedBlockSession(new World(128,97,World.Mode.BEDROCK_ENABLED),17,z);
            int ground=96-z,y=ground-z;
            for(var s:new FixedBlockSession[]{a,b}) {
                boolean ore=s==b;
                assertTrue(s.blocks().place(16,ground,false,Material.GRANITE));
                assertTrue(s.blocks().place(16,y,false,Material.MARBLE,ore?ORES:OreComposition.EMPTY));
                assertTrue(s.blocks().place(16+z,y,false,Material.LIMESTONE));
                assertTrue(s.blocks().place(16+2*z,y,false,Material.GRANITE,ore?ORES:OreComposition.EMPTY));
            }
            for(int tick=0;tick<20;tick++) {
                for(var s:new FixedBlockSession[]{a,b}) {
                    if(tick==2 || tick==10) assertTrue(s.blocks().removeAt(16,ground));
                    if(tick==6) assertTrue(s.blocks().place(16,ground,false,Material.GRANITE));
                    s.tick();
                }
                var left=a.blocks().saveState().blocks(); var right=b.blocks().saveState().blocks();
                assertEquals(left.size(),right.size());
                for(int i=0;i<left.size();i++) {
                    var x=left.get(i); var yb=right.get(i);
                    assertEquals(x.id(),yb.id()); assertEquals(x.x(),yb.x()); assertEquals(x.y(),yb.y());
                    assertEquals(x.state(),yb.state()); assertEquals(x.unsupportedTicks(),yb.unsupportedTicks());
                    assertEquals(a.blocks().blockSupportAt(x.x(),x.y()).supportCost(),b.blocks().blockSupportAt(yb.x(),yb.y()).supportCost());
                }
                for(int row=0;row<97;row++) for(int col=0;col<128;col++) assertEquals(a.world().get(col,row),b.world().get(col,row));
            }
            assertEquals(2,b.blocks().blocks().stream().filter(v->v.ores().equals(ORES)).count());
            new FixedBlockWorld(b.world(),z).restoreState(b.blocks().saveState());
        }
    }
}
