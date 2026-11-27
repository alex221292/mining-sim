package miningsim.generation;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import miningsim.persistence.FixedSessionIO;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NaturalWaterGeneratorTest {
    private byte[] save(miningsim.block.FixedBlockSession session) throws Exception {
        var out=new ByteArrayOutputStream(); FixedSessionIO.write(session,out); return out.toByteArray();
    }

    @ParameterizedTest @ValueSource(ints={4,8}) void deterministicAndOnlyAirChanges(int size) throws Exception {
        var config=WorldGenerationConfig.preset(12345,size);
        var a=WorldGenerator.generate(config); var b=WorldGenerator.generate(config);
        assertArrayEquals(save(a.session()),save(b.session()));
        var dry=dryReference(config);
        assertEquals(dry.blocks().saveState(),a.session().blocks().saveState());
        for(int y=0;y<config.height();y++) for(int x=0;x<config.width();x++) {
            var before=dry.world().get(x,y); var after=a.session().world().get(x,y);
            if(before!=after) { assertEquals(Material.AIR,before); assertEquals(Material.WATER,after); }
        }
        var world=b.session().world(); var blocks=b.session().blocks().saveState();
        int count=0;
        for(int y=0;y<world.height();y++) for(int x=0;x<world.width();x++) if(world.get(x,y)==Material.WATER) {
            assertNull(b.session().blocks().blockAt(x,y)); world.set(x,y,Material.AIR); count++;
        }
        assertTrue(count>0);
        var stats=NaturalWaterGenerator.generate(config,world);
        assertEquals(count,stats.undergroundCells()+stats.surfaceCells());
        assertEquals(blocks,b.session().blocks().saveState());
        assertArrayEquals(save(a.session()),save(b.session()));
        assertEquals(0,b.session().cellularTicks());
    }

    private miningsim.block.FixedBlockSession dryReference(WorldGenerationConfig c) {
        var w=new World(c.width(),c.height(),World.Mode.BEDROCK_ENABLED);
        var s=new miningsim.block.FixedBlockSession(w,12345,c.blockSize());
        int size=c.blockSize();
        var geology=new RockGeology(c.seed());
        var ores=new OreDeposits(c.seed(),c.width(),c.height());
        for(int y=0;y<c.height()-1;y+=size) for(int x=0;x<c.width();x+=size)
            if(y>=WorldGenerator.layers(c,x).stoneTop()) assertTrue(s.blocks().place(x,y,false,geology.sample(x+size*.5,y+size*.5),ores.sample(x+size*.5,y+size*.5)));
        for(int x=0;x<c.width();x++) {
            var l=WorldGenerator.layers(c,x);
            for(int y=l.surface();y<l.stoneTop();y++) w.set(x,y,y<l.surface()+l.soil()?Material.SOIL:y<l.surface()+l.soil()+l.sand()?Material.SAND:Material.GRAVEL);
        }
        CaveGenerator.carve(c,s.blocks());
        return s;
    }

    @ParameterizedTest @ValueSource(ints={4,8}) void waterSurvivesTicksAndPersistence(int size) throws Exception {
        var config=WorldGenerationConfig.preset(8,size);
        var a=WorldGenerator.generate(config).session(); var b=WorldGenerator.generate(config).session();
        int initial=water(a); assertTrue(initial>0);
        byte[] before=save(a); assertArrayEquals(before,save(FixedSessionIO.read(new ByteArrayInputStream(before)).fixed()));
        for(int i=0;i<100;i++) { a.tick(); b.tick(); }
        assertEquals(initial,water(a)); assertArrayEquals(save(a),save(b));
        byte[] after=save(a); assertArrayEquals(after,save(FixedSessionIO.read(new ByteArrayInputStream(after)).fixed()));
    }
    private int water(miningsim.block.FixedBlockSession session) {
        int n=0; var w=session.world();
        for(int y=0;y<w.height();y++) for(int x=0;x<w.width();x++) if(w.get(x,y)==Material.WATER) n++;
        return n;
    }

    @Test void isolatedChambersHaveFlatInitialLevelsAndDoNotAlterSolids() {
        boolean dry=false,wet=false;
        for(int seed=0;seed<32;seed++) {
            var c=new WorldGenerationConfig(seed,64,65,4,24,0,24,4);
            var w=new World(64,65,World.Mode.BEDROCK_ENABLED);
            for(int y=24;y<64;y++) for(int x=0;x<64;x++) w.set(x,y,Material.STONE);
            for(int y=45;y<55;y++) for(int x=5;x<19;x++) w.set(x,y,Material.AIR);
            for(int y=44;y<56;y++) for(int x=35;x<53;x++) w.set(x,y,Material.AIR);
            NaturalWaterGenerator.generate(c,w);
            int count=0;
            for(int y=45;y<55;y++) for(int x=5;x<19;x++) if(w.get(x,y)==Material.WATER) count++;
            dry|=count==0; wet|=count>0;
            for(int y=45;y<55;y++) for(int x=5;x<19;x++) assertEquals(w.get(5,y),w.get(x,y));
            for(int y=44;y<56;y++) for(int x=35;x<53;x++) assertEquals(w.get(35,y),w.get(x,y));
            for(int y=24;y<64;y++) for(int x=0;x<64;x++) {
                boolean chamber=(x>=5 && x<19 && y>=45 && y<55)||(x>=35 && x<53 && y>=44 && y<56);
                if(!chamber) assertEquals(Material.STONE,w.get(x,y));
            }
        }
        assertTrue(dry); assertTrue(wet);
    }

    @Test void surfaceBasinsUseRealSpillAndNotWorldEdges() {
        int[] profile={5,5,5,8,10,10,8,5,5,5,5,5}; boolean found=false;
        for(int seed=0;seed<32;seed++) {
            var w=new World(profile.length,16);
            for(int x=0;x<profile.length;x++) for(int y=profile[x];y<16;y++) w.set(x,y,Material.STONE);
            int[] result=NaturalWaterGenerator.surfaceLakes(w,profile,seed);
            if(result[0]>0) {
                found=true; int level=16;
                for(int y=0;y<16;y++) if(w.get(4,y)==Material.WATER) { level=y; break; }
                assertTrue(level>=5 && level<10);
                assertEquals(level,firstWater(w,5));
                assertEquals(Material.AIR,w.get(4,level-1));
            }
            for(int x=0;x<profile.length;x++) for(int y=0;y<profile[x];y++) if(w.get(x,y)==Material.WATER) assertTrue(x>=3 && x<=6);
        }
        assertTrue(found);
        int[] slope={5,6,7,8,9,10,11,12};
        for(int seed=0;seed<16;seed++) assertEquals(0,NaturalWaterGenerator.surfaceLakes(new World(8,16),slope,seed)[0]);
    }
    private int firstWater(World w,int x) {
        for(int y=0;y<w.height();y++) if(w.get(x,y)==Material.WATER) return y;
        return w.height();
    }

    @Test void elevatedPassageOnlyConnectsReservoirsWhenLevelReachesIt() {
        int width=7,height=7; boolean[] cave=new boolean[width*height];
        for(int y=3;y<=5;y++) for(int x:new int[]{1,2,4,5}) cave[y*width+x]=true;
        for(int x=1;x<=5;x++) cave[2*width+x]=true;
        int[] queue=new int[cave.length]; boolean[] seen=new boolean[cave.length];
        assertEquals(6,NaturalWaterGenerator.collect(3*width+1,cave,seen,queue,width,height,3));
        assertFalse(seen[3*width+4]);
        assertEquals(6,NaturalWaterGenerator.collect(3*width+4,cave,seen,queue,width,height,3));
        assertEquals(17,NaturalWaterGenerator.collect(3*width+1,cave,new boolean[cave.length],queue,width,height,2));
    }

    @Test void highPassageCanLeaveAConnectedChamberDry() {
        boolean found=false;
        for(int seed=0;seed<96 && !found;seed++) {
            var c=new WorldGenerationConfig(seed,64,65,4,24,0,24,4);
            var w=new World(64,65,World.Mode.BEDROCK_ENABLED);
            for(int y=24;y<64;y++) for(int x=0;x<64;x++) w.set(x,y,Material.WOOD);
            for(int y=45;y<55;y++) for(int x=5;x<19;x++) w.set(x,y,Material.AIR);
            for(int y=45;y<55;y++) for(int x=35;x<53;x++) w.set(x,y,Material.AIR);
            for(int x=10;x<=40;x++) w.set(x,40,Material.AIR);
            for(int y=40;y<46;y++) { w.set(10,y,Material.AIR); w.set(40,y,Material.AIR); }
            NaturalWaterGenerator.generate(c,w);
            int left=0,right=0;
            for(int y=45;y<55;y++) {
                for(int x=5;x<19;x++) if(w.get(x,y)==Material.WATER) left++;
                for(int x=35;x<53;x++) if(w.get(x,y)==Material.WATER) right++;
            }
            found=(left>0 && right==0)||(left==0 && right>0);
        }
        assertTrue(found,"an elevated passage must not force both pockets wet");
    }

    @Test void openingGeneratedReservoirFloodsLowerChamber() {
        boolean exercised=false;
        for(int seed=0;seed<96 && !exercised;seed++) {
            var c=new WorldGenerationConfig(seed,64,65,4,24,0,24,4);
            var w=new World(64,65,World.Mode.BEDROCK_ENABLED);
            for(int y=24;y<64;y++) for(int x=0;x<64;x++) w.set(x,y,Material.WOOD);
            for(int x=8;x<24;x++) for(int y=32;y<40;y++) w.set(x,y,Material.AIR);
            for(int x=8;x<24;x++) for(int y=48;y<56;y++) w.set(x,y,Material.AIR);
            NaturalWaterGenerator.generate(c,w);
            int upper=0,lower=0;
            for(int x=8;x<24;x++) for(int y=32;y<40;y++) if(w.get(x,y)==Material.WATER) upper++;
            for(int x=8;x<24;x++) for(int y=48;y<56;y++) if(w.get(x,y)==Material.WATER) lower++;
            if(upper==0 || lower!=0) continue;
            var s=new miningsim.block.FixedBlockSession(w,12345,4); int before=water(s);
            for(int y=40;y<48;y++) w.set(15,y,Material.AIR);
            for(int tick=0;tick<120;tick++) s.tick();
            for(int x=8;x<24;x++) for(int y=48;y<56;y++) if(w.get(x,y)==Material.WATER) lower++;
            assertTrue(lower>0); assertEquals(before,water(s)); exercised=true;
        }
        assertTrue(exercised,"seed sample must include a wet upper and dry lower chamber");
    }

    @ParameterizedTest @ValueSource(ints={4,8}) void fallingBlockDisplacesGeneratedWater(int size) {
        boolean exercised=false;
        for(int seed=0;seed<32 && !exercised;seed++) {
            var c=new WorldGenerationConfig(seed,64,65,size,24,0,24,4);
            var w=new World(64,65,World.Mode.BEDROCK_ENABLED);
            for(int y=24;y<64;y++) for(int x=0;x<64;x++) w.set(x,y,Material.WOOD);
            for(int y=24;y<64;y++) for(int x=8;x<40;x++) w.set(x,y,Material.AIR);
            var s=new miningsim.block.FixedBlockSession(w,12345,size);
            assertTrue(s.blocks().place(16,24,false));
            NaturalWaterGenerator.generate(c,w);
            int before=water(s); if(before==0) continue;
            assertEquals(Material.WATER,w.get(16,63));
            for(int i=0;i<50;i++) s.tick();
            assertEquals(64-size,s.blocks().blocks().getFirst().y(),"block must enter the initial water-filled bottom row");
            assertEquals(before,water(s));
            assertEquals(size*size,s.blocks().size()*size*size);
            new miningsim.block.FixedBlockWorld(w,size).restoreState(s.blocks().saveState());
            exercised=true;
        }
        assertTrue(exercised);
    }
}
