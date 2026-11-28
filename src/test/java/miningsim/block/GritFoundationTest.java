package miningsim.block;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import miningsim.persistence.FixedSessionIO;
import miningsim.resource.OreComposition;
import miningsim.resource.OreId;
import miningsim.simulation.Simulation;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.zip.CRC32;
import static org.junit.jupiter.api.Assertions.*;

class GritFoundationTest {
    private static final OreComposition ORE=OreComposition.of(new OreComposition.Component(OreId.IRON,120000),new OreComposition.Component(OreId.GOLD,5));

    @Test void gritBrushAtBedrockDoesNotThrowOrModifyBedrock() {
        var s=new FixedBlockSession(new World(8,9,World.Mode.BEDROCK_ENABLED),1,4);
        assertDoesNotThrow(()->s.paintCells(3,8,2,Material.STONE_GRIT));
        assertEquals(Material.BEDROCK,s.world().get(3,8));
        assertDoesNotThrow(()->miningsim.app.Brush.paintWorld(s.world(),3,8,2,Material.MARBLE_GRIT,null));
        assertEquals(Material.BEDROCK,s.world().get(3,8));
    }

    @Test void identityAndSceneForBothBlockSizes() {
        var fixed=new Material[]{Material.STONE,Material.GRANITE,Material.LIMESTONE,Material.MARBLE};
        var grit=new Material[]{Material.STONE_GRIT,Material.GRANITE_GRIT,Material.LIMESTONE_GRIT,Material.MARBLE_GRIT};
        for(int i=0;i<4;i++) {
            assertEquals(11+i,Byte.toUnsignedInt(grit[i].id()));
            assertEquals(fixed[i].rockSubstance(),grit[i].rockSubstance());
            assertEquals(miningsim.resource.MaterialForm.GRIT,grit[i].rockForm());
            assertTrue(fixed[i].isFixedRock()); assertFalse(grit[i].isFixedRock());
        }
        for(int size:new int[]{4,8}) {
            var session=GritReferenceScene.create(size);
            for(int i=0;i<4;i++) {
                assertEquals(grit[i],session.world().get(12+i*30,8));
                assertNull(session.blocks().blockAt(12+i*30,8));
                assertNotEquals(session.world().gritOre(12+i*30,8),session.world().gritOre(13+i*30,8));
            }
            session.tick();
        }
    }

    @Test void movementAcrossChunkAndRemoval() {
        var w=new World(80,100,World.Mode.BEDROCK_ENABLED);
        w.set(20,63,Material.MARBLE_GRIT); w.setGritOre(20,63,ORE);
        new Simulation(w,123).step();
        assertEquals(Material.MARBLE_GRIT,w.get(20,64));
        assertEquals(ORE,w.gritOre(20,64));
        assertEquals(OreComposition.EMPTY,w.gritOre(20,63));
        w.set(20,64,Material.WATER);
        assertEquals(OreComposition.EMPTY,w.gritOre(20,64));
    }

    @Test void waterSwapRetainsOre() {
        var w=new World(4,4);
        w.set(1,1,Material.STONE_GRIT); w.setGritOre(1,1,ORE); w.set(1,2,Material.WATER);
        w.swapCells(1,1,1,2);
        assertEquals(Material.WATER,w.get(1,1)); assertEquals(OreComposition.EMPTY,w.gritOre(1,1));
        assertEquals(ORE,w.gritOre(1,2));
        w.clear(); assertEquals(OreComposition.EMPTY,w.gritOre(1,2));
    }

    @Test void physicsSwapsWithBlockedWaterAndRespectsOtherGranularCells() {
        var w=new World(3,3,World.Mode.BEDROCK_ENABLED);
        w.set(1,0,Material.LIMESTONE_GRIT); w.setGritOre(1,0,ORE);
        w.set(1,1,Material.WATER); w.set(0,1,Material.WOOD); w.set(2,1,Material.WOOD);
        new Simulation(w,1).step();
        assertEquals(Material.LIMESTONE_GRIT,w.get(1,1)); assertEquals(ORE,w.gritOre(1,1));
        assertEquals(Material.WATER,w.get(1,0)); assertEquals(OreComposition.EMPTY,w.gritOre(1,0));
        for(var obstacle:new Material[]{Material.SAND,Material.GRAVEL,Material.SOIL}) {
            var world=new World(3,3,World.Mode.BEDROCK_ENABLED);
            world.set(1,0,Material.MARBLE_GRIT); world.setGritOre(1,0,ORE);
            world.set(1,1,obstacle); world.set(0,1,Material.WOOD); world.set(2,1,Material.WOOD);
            new Simulation(world,1).step();
            assertEquals(Material.MARBLE_GRIT,world.get(1,0)); assertEquals(ORE,world.gritOre(1,0));
        }
    }

    @Test void roundTripAllGritAndBlockOre() throws Exception {
        for(int size:new int[]{4,8}) {
            var s=new FixedBlockSession(new World(80,65,World.Mode.BEDROCK_ENABLED),123,size);
            assertTrue(s.blocks().place(0,64-size,false,Material.STONE,ORE));
            Material[] grit={Material.STONE_GRIT,Material.GRANITE_GRIT,Material.LIMESTONE_GRIT,Material.MARBLE_GRIT};
            for(int i=0;i<4;i++) { s.world().set(20+i,10,grit[i]); s.world().setGritOre(20+i,10,ORE); }
            var out=new ByteArrayOutputStream(); FixedSessionIO.write(s,out);
            var loaded=FixedSessionIO.read(new ByteArrayInputStream(out.toByteArray()));
            for(int i=0;i<4;i++) { assertEquals(grit[i],loaded.world().get(20+i,10)); assertEquals(ORE,loaded.world().gritOre(20+i,10)); assertNull(loaded.fixed().blocks().blockAt(20+i,10)); }
            assertEquals(ORE,loaded.fixed().blocks().blockAt(0,64-size).ores());
            var again=new ByteArrayOutputStream(); FixedSessionIO.write(loaded.fixed(),again); assertArrayEquals(out.toByteArray(),again.toByteArray());
        }
    }

    @Test void actualBaselineV4Fixture() throws Exception {
        var data=Base64.getDecoder().decode("TVNJTQAAAAQAAAAIAAAACQAAAAEAAAAEAAAAAQAAAAAAAAACCAgICAAAAAAICAgIAAAAAAgICAgAAAAACAgICAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAcHBwcHBwcHAAAAAAAAAAEAAAAAAAAAAAAAAAEAAAAAgdy52g==");
        var loaded=FixedSessionIO.read(new ByteArrayInputStream(data));
        assertEquals(Material.GRANITE,loaded.world().get(0,0));
        assertEquals(OreComposition.EMPTY,loaded.fixed().blocks().blockAt(0,0).ores());
    }

    @Test void diagonalAndBoundaries() {
        var w=new World(5,5);
        w.set(2,1,Material.GRANITE_GRIT); w.setGritOre(2,1,ORE); w.set(2,2,Material.WOOD);
        new Simulation(w,2).step();
        assertTrue(w.get(1,2)==Material.GRANITE_GRIT || w.get(3,2)==Material.GRANITE_GRIT);
        assertEquals(ORE,w.gritOre(w.get(1,2)==Material.GRANITE_GRIT?1:3,2));
        w.clear(); w.set(0,4,Material.STONE_GRIT); new Simulation(w,3).step();
        assertEquals(Material.STONE_GRIT,w.get(0,4));
    }

    @Test void deterministicConservationAndNoMixing() {
        var a=new World(70,80,World.Mode.BEDROCK_ENABLED); var b=new World(70,80,World.Mode.BEDROCK_ENABLED);
        var other=OreComposition.of(new OreComposition.Component(OreId.COPPER,45000));
        for(var w:new World[]{a,b}) {
            for(int x=0;x<60;x++) for(int y=2;y<6;y++) {
                w.set(x,y,x%2==0?Material.STONE_GRIT:Material.GRANITE_GRIT);
                w.setGritOre(x,y,x%3==0?ORE:other);
            }
            for(int x=0;x<70;x++) for(int y=50;y<60;y++) w.set(x,y,Material.WATER);
        }
        var sa=new Simulation(a,987); var sb=new Simulation(b,987);
        for(int i=0;i<100;i++) { sa.step(); sb.step(); }
        long stone=0,granite=0,iron=0,copper=0,gold=0;
        for(int y=0;y<a.height();y++) for(int x=0;x<a.width();x++) {
            assertEquals(a.get(x,y),b.get(x,y)); assertEquals(a.gritOre(x,y),b.gritOre(x,y));
            if(a.get(x,y)==Material.STONE_GRIT) stone++;
            if(a.get(x,y)==Material.GRANITE_GRIT) granite++;
            var ore=a.gritOre(x,y); iron+=ore.ppm(OreId.IRON); copper+=ore.ppm(OreId.COPPER); gold+=ore.ppm(OreId.GOLD);
            if(!a.get(x,y).isGrit()) assertEquals(OreComposition.EMPTY,ore);
        }
        assertEquals(120,stone); assertEquals(120,granite);
        assertEquals(80L*120000,iron); assertEquals(160L*45000,copper); assertEquals(80L*5,gold);
    }

    @Test void invalidV5ReferencesAreRejected() throws Exception {
        var s=new FixedBlockSession(new World(8,9,World.Mode.BEDROCK_ENABLED),1,4);
        s.world().set(1,1,Material.STONE_GRIT); s.world().setGritOre(1,1,ORE);
        var out=new ByteArrayOutputStream(); FixedSessionIO.write(s,out); byte[] original=out.toByteArray();
        // Header + cells + block ore count, then palette: count, component count, two pairs, cell count, record.
        int palette=36+8*9+4, record=palette+4+1+10+4;
        for(int kind=0;kind<5;kind++) {
            byte[] bad=original.clone(); var buf=ByteBuffer.wrap(bad);
            switch(kind) {
                case 0 -> buf.put(record+7,(byte)2); // invalid reference
                case 1 -> buf.putInt(record,0); // ore on AIR
                case 2 -> buf.put(palette+5,(byte)99); // unknown ore
                case 3 -> buf.putInt(palette+6,0); // invalid ppm
                case 4 -> buf.put(36+9,(byte)99); // unknown material
            }
            var crc=new CRC32(); crc.update(bad,0,bad.length-4); buf.putInt(bad.length-4,(int)crc.getValue());
            assertThrows(java.io.IOException.class,()->FixedSessionIO.read(new ByteArrayInputStream(bad)));
        }
    }
}
