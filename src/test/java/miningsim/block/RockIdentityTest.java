package miningsim.block;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;
import miningsim.persistence.FixedSessionIO;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RockIdentityTest {
    private static final Material[] ROCKS={Material.STONE,Material.GRANITE,Material.LIMESTONE,Material.MARBLE};

    @Test void identityMovementAndRoundTrip() throws Exception {
        for(int size:new int[]{4,8}) for(var rock:ROCKS) {
            var world=new World(64,65,World.Mode.BEDROCK_ENABLED);
            var session=new FixedBlockSession(world,12345,size);
            assertTrue(session.blocks().place(0,0,false,rock));
            long id=session.blocks().blockAt(0,0).id();
            for(int i=0;i<10;i++) session.tick();
            var block=session.blocks().blocks().getFirst();
            assertEquals(id,block.id()); assertTrue(block.y()>0);
            assertEquals(rock.rockSubstance(),block.substance());
            for(int y=block.y();y<block.y()+size;y++) for(int x=0;x<size;x++) assertEquals(rock,world.get(x,y));
            var bytes=new ByteArrayOutputStream(); FixedSessionIO.write(session,bytes);
            var loaded=FixedSessionIO.read(new ByteArrayInputStream(bytes.toByteArray())).fixed();
            assertEquals(block,loaded.blocks().blocks().getFirst());
            assertTrue(loaded.blocks().removeAt(0,block.y()));
            assertEquals(Material.AIR,loaded.world().get(0,block.y()));
        }
    }

    @Test void rejectsMixedOwnedCellsAndPaintingRockAsCells() {
        for(int size:new int[]{4,8}) {
            var world=new World(32,33,World.Mode.BEDROCK_ENABLED);
            var s=new FixedBlockSession(world,1,size);
            s.blockStroke(0,0,2*size,0,false,Material.MARBLE);
            assertEquals(3,s.blocks().size());
            s.blockStroke(0,0,2*size,0,false,Material.GRANITE);
            assertTrue(s.blocks().saveState().blocks().stream().allMatch(b->b.pinned() && b.material()==Material.MARBLE));
            s.blocks().releaseStroke();
            assertTrue(s.blocks().saveState().blocks().stream().noneMatch(FixedBlockWorld.SavedBlock::pinned));
            assertThrows(IllegalArgumentException.class,()->s.paintCells(0,0,1,Material.GRANITE));
            world.set(0,0,Material.GRANITE);
            assertThrows(IllegalArgumentException.class,()->new FixedBlockWorld(world,size).restoreState(s.blocks().saveState()));
        }
    }

    private static byte[] save(FixedBlockSession s) throws IOException {
        var out=new ByteArrayOutputStream(); FixedSessionIO.write(s,out); return out.toByteArray();
    }
    private static void checksum(byte[] bytes) {
        var crc=new CRC32(); crc.update(bytes,0,bytes.length-4); int c=(int)crc.getValue();
        for(int i=0;i<4;i++) bytes[bytes.length-4+i]=(byte)(c >>> (24-8*i));
    }
    @Test void persistenceRejectsCorruptionAndReadsOldV2() throws Exception {
        for(int size:new int[]{4,8}) {
            var s=new FixedBlockSession(new World(32,33,World.Mode.BEDROCK_ENABLED),1,size);
            assertTrue(s.blocks().place(0,0,false));
            // Remove the empty v4 block-ore and v5 palette/cell sections to construct v2.
            byte[] v5=save(s),old=java.util.Arrays.copyOf(v5,v5.length-12); old[7]=2; checksum(old);
            assertEquals(Material.STONE,FixedSessionIO.read(new ByteArrayInputStream(old)).world().get(0,0));
            byte[] bad=save(s); bad[36]=(byte)255; checksum(bad);
            byte[] unknown=bad;
            assertThrows(IOException.class,()->FixedSessionIO.read(new ByteArrayInputStream(unknown)));
            bad=save(s); bad[36]=(byte)Material.GRANITE.id(); checksum(bad);
            byte[] mismatch=bad;
            assertThrows(IOException.class,()->FixedSessionIO.read(new ByteArrayInputStream(mismatch)));
            bad=save(s); bad[36]^=1; byte[] crc=bad;
            assertThrows(IOException.class,()->FixedSessionIO.read(new ByteArrayInputStream(crc)));
        }
    }

    @Test void readsActualM54V2Fixture() throws Exception {
        // Produced by FixedSessionIO and FixedBlockWorld from de08c4d, not a rewritten v3 file.
        var bytes=java.util.Base64.getDecoder().decode("TVNJTQAAAAIAAAAIAAAACQAAAAEAAAAEAAAAAQAAAAAAAAACAQEBAQAAAAABAQEBAAAAAAEBAQEAAAAAAQEBAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAcHBwcHBwcHAAAAAAAAAAEAAAAAAAAAAAAAAAD/cLao");
        var loaded=FixedSessionIO.read(new ByteArrayInputStream(bytes)).fixed();
        assertNotNull(loaded); assertEquals(4,loaded.blocks().rockBlockSizeCells());
        assertEquals(1,loaded.blocks().blockAt(0,0).id());
        assertEquals(Material.STONE,loaded.world().get(3,3));
        assertEquals(Material.BEDROCK,loaded.world().get(0,8));
    }

    @Test void mixedSupportReplayIsDeterministic() {
        for(int size:new int[]{4,8}) {
            var stone=new FixedBlockSession(new World(96,97,World.Mode.BEDROCK_ENABLED),12345,size);
            var mixed=new FixedBlockSession(new World(96,97,World.Mode.BEDROCK_ENABLED),12345,size);
            int ground=96-size;
            for(int i=0;i<6;i++) {
                assertTrue(stone.blocks().place(i*size,ground-size,false,ROCKS[i%4]));
                assertTrue(mixed.blocks().place(i*size,ground-size,false,ROCKS[i%4]));
            }
            assertTrue(stone.blocks().place(0,ground,false,Material.MARBLE));
            assertTrue(mixed.blocks().place(0,ground,false,Material.MARBLE));
            for(int t=0;t<25;t++) {
                if(t==2) { stone.blocks().removeAt(0,ground); mixed.blocks().removeAt(0,ground); }
                if(t==5) { stone.blocks().place(0,ground,false,Material.GRANITE); mixed.blocks().place(0,ground,false,Material.GRANITE); }
                stone.tick(); mixed.tick();
                var a=stone.blocks().saveState().blocks(); var b=mixed.blocks().saveState().blocks();
                assertEquals(a.size(),b.size());
                for(int i=0;i<a.size();i++) {
                    var x=a.get(i); var y=b.get(i);
                    assertEquals(x.id(),y.id()); assertEquals(x.x(),y.x()); assertEquals(x.y(),y.y());
                    assertEquals(x.state(),y.state()); assertEquals(x.unsupportedTicks(),y.unsupportedTicks());
                    assertEquals(stone.blocks().blockSupportAt(x.x(),x.y()).supportCost(),mixed.blocks().blockSupportAt(y.x(),y.y()).supportCost());
                }
            }
        }
    }

    @Test void rocksDisplaceWaterAndPreserveCounts() {
        for(int size:new int[]{4,8}) for(var rock:ROCKS) {
            var s=new FixedBlockSession(new World(64,65,World.Mode.BEDROCK_ENABLED),42,size);
            assertTrue(s.blocks().place(size,0,false,rock));
            for(int y=size;y<3*size;y++) for(int x=0;x<4*size;x++) s.world().set(x,y,Material.WATER);
            int water=8*size*size;
            boolean displaced=false;
            for(int t=0;t<20;t++) {
                s.blocks().step(); displaced |= s.blocks().tickDiagnostics().waterAttempts()>0 && s.blocks().tickDiagnostics().moved()>0;
                int rocks=0,waters=0;
                for(int y=0;y<65;y++) for(int x=0;x<64;x++) {
                    if(s.world().get(x,y)==rock) rocks++;
                    if(s.world().get(x,y)==Material.WATER) waters++;
                }
                assertEquals(size*size,rocks); assertEquals(water,waters);
            }
            assertTrue(displaced);
        }
    }

    @Test void mixedColumnAndWaterMatchStoneDynamics() {
        for(int size:new int[]{4,8}) {
            var a=new FixedBlockSession(new World(64,65,World.Mode.BEDROCK_ENABLED),123,size);
            var b=new FixedBlockSession(new World(64,65,World.Mode.BEDROCK_ENABLED),123,size);
            for(int i=0;i<4;i++) {
                assertTrue(a.blocks().place(0,i*size,false));
                assertTrue(b.blocks().place(0,i*size,false,ROCKS[i]));
            }
            for(var s:new FixedBlockSession[]{a,b}) for(int y=5*size;y<7*size;y++) for(int x=0;x<4*size;x++) s.world().set(x,y,Material.WATER);
            for(int tick=0;tick<25;tick++) {
                a.tick(); b.tick();
                var x=a.blocks().saveState().blocks(); var y=b.blocks().saveState().blocks();
                for(int i=0;i<x.size();i++) {
                    assertEquals(x.get(i).id(),y.get(i).id()); assertEquals(x.get(i).y(),y.get(i).y());
                    assertEquals(x.get(i).state(),y.get(i).state()); assertEquals(x.get(i).unsupportedTicks(),y.get(i).unsupportedTicks());
                }
                for(int row=0;row<65;row++) for(int col=0;col<64;col++) {
                    var expected=a.world().get(col,row); var actual=b.world().get(col,row);
                    assertEquals(expected,actual.isFixedRock()?Material.STONE:actual);
                }
            }
        }
    }

    @Test void mixedStatesAndTimersRoundTrip() throws Exception {
        for(int size:new int[]{4,8}) {
            var s=new FixedBlockSession(new World(64,65,World.Mode.BEDROCK_ENABLED),1,size);
            assertTrue(s.blocks().place(0,64-size,false,Material.MARBLE));
            assertTrue(s.blocks().place(size,0,false,Material.GRANITE));
            for(int i=0;i<3;i++) s.blocks().step();
            assertTrue(s.blocks().place(2*size,0,false,Material.LIMESTONE));
            for(int i=0;i<6;i++) s.blocks().step();
            var expected=s.blocks().saveState();
            assertTrue(expected.blocks().stream().anyMatch(b->b.state()==FixedBlockWorld.State.FALLING));
            assertTrue(expected.blocks().stream().anyMatch(b->b.unsupportedTicks()>0));
            var restored=FixedSessionIO.read(new ByteArrayInputStream(save(s))).fixed();
            assertEquals(expected,restored.blocks().saveState());
        }
    }

    @Test void deterministicMixedReplayAndGranularObstacle() {
        for(int size:new int[]{4,8}) {
            var a=new FixedBlockSession(new World(64,65,World.Mode.BEDROCK_ENABLED),123,size);
            var b=new FixedBlockSession(new World(64,65,World.Mode.BEDROCK_ENABLED),123,size);
            for(var s:new FixedBlockSession[]{a,b}) {
                for(int i=0;i<4;i++) assertTrue(s.blocks().place(i*size,0,false,ROCKS[i]));
                for(int x=0;x<4*size;x++) s.world().set(x,2*size,Material.SOIL);
                for(int x=4*size;x<6*size;x++) s.world().set(x,2*size,Material.WATER);
            }
            for(int t=0;t<30;t++) {
                a.tick(); b.tick(); assertEquals(a.blocks().saveState(),b.blocks().saveState());
                int[] counts=new int[ROCKS.length];
                for(int y=0;y<65;y++) for(int x=0;x<64;x++) {
                    var material=a.world().get(x,y); assertEquals(material,b.world().get(x,y));
                    for(int i=0;i<ROCKS.length;i++) if(material==ROCKS[i]) counts[i]++;
                }
                for(int count:counts) assertEquals(size*size,count);
            }
        }
    }
}
