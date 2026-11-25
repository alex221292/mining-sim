package miningsim.generation;

import miningsim.persistence.WorldIO;

/** Cell coordinates: y increases downward; surface is the first potential deposit row. */
public record WorldGenerationConfig(long seed,int width,int height,int blockSize,int baseSurfaceHeight,
                                    int terrainAmplitude,int terrainScale,int soilDepth) {
    public static final int MAX_SAND_DEPTH=32, MAX_GRAVEL_DEPTH=16;
    public static final int LAYER_SCALE_MULTIPLIER=2;
    public WorldGenerationConfig {
        if((blockSize!=4 && blockSize!=8) || width<blockSize || width>WorldIO.MAX_DIMENSION || height>WorldIO.MAX_DIMENSION
                || height<3*blockSize+1 || (long)width*height>WorldIO.MAX_CELLS || width%blockSize!=0
                || (height-1)%blockSize!=0 || terrainScale<blockSize || terrainAmplitude<0 || soilDepth<1
                || (long)baseSurfaceHeight-terrainAmplitude<(long)soilDepth+blockSize
                || (long)baseSurfaceHeight+terrainAmplitude>height-1-2*blockSize)
            throw new IllegalArgumentException("Invalid terrain configuration or incompatible block grid");
    }
    public static WorldGenerationConfig preset(long seed,int blockSize) {
        return new WorldGenerationConfig(seed,800,401,blockSize,160,32,96,8);
    }
}
