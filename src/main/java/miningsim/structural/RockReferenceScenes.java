package miningsim.structural;

import miningsim.world.Material;
import miningsim.world.World;

/** Deterministic, small gameplay calibration fixtures; not geological data. */
public final class RockReferenceScenes {
    public static final double GRAVITY = 9.81;
    public static final RockProperties GAMEPLAY_STONE = new RockProperties(2500, 1e8, 40000, 1e6, 40000, 40000, 0.5);
    public static final RockMaterialRegistry MATERIALS = new RockMaterialRegistry.Builder().register(1, GAMEPLAY_STONE).build();
    private RockReferenceScenes() { }
    public record Scene(String name, World world, RockConnectivity connectivity, RockDamageSystem damage, int x, int y) { }
    private static Scene create(String name, World world, int x, int y) {
        RockConnectivity connectivity = new RockConnectivity(world);
        RockDamageSystem damage = new RockDamageSystem(world, connectivity, MATERIALS, GRAVITY);
        return new Scene(name, world, connectivity, damage, x, y);
    }
    /** Supports at x=2 and x=2+span; roof bottom y=2, bedrock y=5. */
    public static Scene roof(String name, int span, int thickness) {
        if (span < 2 || span > 20 || thickness < 1 || thickness > 2) throw new IllegalArgumentException();
        World w = new World(span + 5, 6, World.Mode.BEDROCK_ENABLED);
        for (int x = 2; x <= 2 + span; x++) for (int y = 3 - thickness; y <= 2; y++) w.set(x,y,Material.STONE);
        for (int y = 3; y <= 4; y++) { w.set(2,y,Material.STONE); w.set(2+span,y,Material.STONE); }
        return create(name,w,2,2);
    }
    public static Scene cantilever(int length) {
        World w = new World(length + 5,6,World.Mode.BEDROCK_ENABLED);
        for (int x = 2; x <= 2 + length; x++) w.set(x,2,Material.STONE);
        w.set(2,3,Material.STONE); w.set(2,4,Material.STONE);
        return create("cantilever-" + length,w,2,2);
    }
    public static Scene island() {
        World w = new World(7,6,World.Mode.BEDROCK_ENABLED); w.set(3,1,Material.STONE); w.set(4,1,Material.STONE);
        return create("island",w,3,1);
    }
    /** Small reproducible interactive fixtures; calibration scenes remain unchanged. */
    public static Scene interactive(String name, Material material) {
        if(material!=Material.AIR && material!=Material.SAND && material!=Material.WATER) throw new IllegalArgumentException();
        World w=new World(24,20,World.Mode.BEDROCK_ENABLED);
        w.set(12,3,Material.STONE);
        if(material!=Material.AIR) {
            for(int x=8;x<=16;x++) w.set(x,17,Material.WOOD);
            w.set(8,16,Material.WOOD); w.set(16,16,Material.WOOD);
            for(int x=10;x<=14;x++) w.set(x,16,material);
        }
        return create(name,w,12,3);
    }
    public static void finishConnectivity(RockConnectivity connectivity) { while (connectivity.pending()) connectivity.advance(10000); }
}
