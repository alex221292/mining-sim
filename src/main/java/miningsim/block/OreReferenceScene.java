package miningsim.block;

import miningsim.resource.OreComposition;
import miningsim.resource.OreId;
import miningsim.world.Material;
import miningsim.world.World;

/** Diagnostic fixture; no ticks are advanced during construction. */
public final class OreReferenceScene {
    private OreReferenceScene() { }
    public static FixedBlockSession create(int size) {
        var s=new FixedBlockSession(new World(160,97,World.Mode.BEDROCK_ENABLED),12345,size);
        var one=OreComposition.of(new OreComposition.Component(OreId.IRON,120000));
        var mixed=OreComposition.of(new OreComposition.Component(OreId.IRON,120000),new OreComposition.Component(OreId.COPPER,25000),new OreComposition.Component(OreId.SILVER,80),new OreComposition.Component(OreId.GOLD,5));
        int ground=96-size;
        Material[] rocks={Material.STONE,Material.GRANITE,Material.MARBLE,Material.LIMESTONE};
        for(int i=0;i<4;i++) {
            s.blocks().place((i+1)*2*size,ground,false,rocks[i],i==0?OreComposition.EMPTY:i==1?one:mixed);
            s.blocks().place((i+1)*2*size,ground-4*size,false,rocks[i],i==0?OreComposition.EMPTY:i==1?one:mixed);
        }
        for(int y=ground-2*size;y<ground;y++) for(int x=0;x<10*size && x<160;x++) if(s.world().get(x,y)==Material.AIR) s.world().set(x,y,Material.WATER);
        return s;
    }
}
