package miningsim.block;

import miningsim.resource.OreComposition;
import miningsim.resource.OreId;
import miningsim.world.Material;
import miningsim.world.World;

/** Reproducible paused diagnostic fixture for either supported block size. */
public final class GritReferenceScene {
    private GritReferenceScene() { }
    public static FixedBlockSession create(int size) {
        var s = new FixedBlockSession(new World(160,97,World.Mode.BEDROCK_ENABLED),12345,size);
        var w = s.world();
        var one = OreComposition.of(new OreComposition.Component(OreId.IRON,120000));
        var mixed = OreComposition.of(new OreComposition.Component(OreId.IRON,120000),
                new OreComposition.Component(OreId.COPPER,25000),new OreComposition.Component(OreId.SILVER,80),
                new OreComposition.Component(OreId.GOLD,5));
        Material[] grit={Material.STONE_GRIT,Material.GRANITE_GRIT,Material.LIMESTONE_GRIT,Material.MARBLE_GRIT};
        for(int i=0;i<4;i++) for(int y=8;y<16;y++) for(int x=12+i*30;x<24+i*30;x++) {
            w.set(x,y,grit[i]);
            if(x%3==1) w.setGritOre(x,y,one);
            if(x%3==2) w.setGritOre(x,y,mixed);
        }
        for(int i=0;i<5;i++) s.blocks().place(16+i*size,55+i*size/2,false,Material.STONE);
        for(int y=78;y<96;y++) for(int x=95;x<155;x++) if(w.get(x,y)==Material.AIR) w.set(x,y,Material.WATER);
        for(int y=35;y<40;y++) for(int x=75;x<95;x++) w.set(x,y,x<82?Material.SAND:x<89?Material.GRAVEL:Material.SOIL);
        return s;
    }
}
