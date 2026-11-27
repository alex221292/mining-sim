package miningsim.generation;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import miningsim.block.FixedBlockSession;
import miningsim.block.FixedBlockWorld;
import miningsim.persistence.FixedSessionIO;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RockGeologyTest {
    private byte[] save(FixedBlockSession s) throws Exception {
        var out=new ByteArrayOutputStream(); FixedSessionIO.write(s,out); return out.toByteArray();
    }
    private long[] counts(FixedBlockSession s) {
        long[] result=new long[Material.values().length];
        for(int y=0;y<s.world().height();y++) for(int x=0;x<s.world().width();x++) result[Byte.toUnsignedInt(s.world().get(x,y).id())]++;
        return result;
    }
    private FixedBlockSession normalized(FixedBlockSession source,int size) {
        var original=source.world(); var world=new World(original.width(),original.height(),World.Mode.BEDROCK_ENABLED);
        for(int y=0;y<world.height()-1;y++) for(int x=0;x<world.width();x++) {
            var m=original.get(x,y); if(m!=Material.AIR) world.set(x,y,m.isFixedRock()?Material.STONE:m);
        }
        var result=new FixedBlockSession(world,12345,size); var state=source.blocks().saveState();
        var blocks=new ArrayList<FixedBlockWorld.SavedBlock>();
        for(var b:state.blocks()) blocks.add(new FixedBlockWorld.SavedBlock(b.id(),b.x(),b.y(),b.state(),b.unsupportedTicks(),b.pinned(),Material.STONE));
        result.blocks().restoreState(new FixedBlockWorld.SavedState(state.nextId(),blocks));
        return result;
    }
    @ParameterizedTest @ValueSource(ints={4,8}) void generatedMixedWorldRoundTripAndPhysics(int size) throws Exception {
        for(int seed:new int[]{8,12345}) {
            var config=WorldGenerationConfig.preset(seed,size); var a=WorldGenerator.generate(config).session();
            assertArrayEquals(save(a),save(WorldGenerator.generate(config).session()));
            var initial=counts(a); assertTrue(initial[Material.WATER.id()]>0);
            assertTrue(Arrays.stream(new Material[]{Material.STONE,Material.GRANITE,Material.LIMESTONE,Material.MARBLE}).filter(m->initial[m.id()]>0).count()>1);
            assertTrue(WorldGenerator.generate(config).caves().blocksRemoved()>0);
            var ids=a.blocks().blocks().stream().map(FixedBlockWorld.Block::id).toList();
            assertArrayEquals(save(a),save(FixedSessionIO.read(new ByteArrayInputStream(save(a))).fixed()));
            var normalized=normalized(a,size);
            assertEquals(a.blocks().size(),normalized.blocks().size());
            var b=WorldGenerator.generate(config).session();
            for(int i=0;i<600;i++) { a.tick(); b.tick(); }
            assertArrayEquals(initial,counts(a));
            assertEquals(ids,a.blocks().blocks().stream().map(FixedBlockWorld.Block::id).toList());
            new FixedBlockWorld(a.world(),size).restoreState(a.blocks().saveState());
            var as=a.blocks().saveState(); var bs=b.blocks().saveState();
            assertEquals(as.nextId(),bs.nextId()); assertEquals(as.blocks().size(),bs.blocks().size());
            for(int i=0;i<as.blocks().size();i++) {
                var x=as.blocks().get(i); var y=bs.blocks().get(i);
                assertEquals(x,y);
            }
            for(int y=0;y<config.height();y++) for(int x=0;x<config.width();x++) {
                assertEquals(a.world().get(x,y),b.world().get(x,y));
            }
            assertArrayEquals(save(a),save(FixedSessionIO.read(new ByteArrayInputStream(save(a))).fixed()));
        }
    }
    @ParameterizedTest @ValueSource(ints={4,8}) void fieldIsDeterministicAndVariesBySeed(int size) {
        var a=new RockGeology(42); var b=new RockGeology(42); var c=new RockGeology(43); boolean different=false;
        for(int y=0;y<400;y+=size) for(int x=0;x<800;x+=size) {
            assertEquals(a.sample(x+size*.5,y+size*.5),b.sample(x+size*.5,y+size*.5));
            different|=a.sample(x+size*.5,y+size*.5)!=c.sample(x+size*.5,y+size*.5);
        }
        assertTrue(different);
    }
}
