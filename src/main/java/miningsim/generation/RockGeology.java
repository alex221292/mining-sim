package miningsim.generation;

import miningsim.world.Material;

/** Seeded, coordinate-based geology. Coordinates and wavelengths are in world cells. */
public final class RockGeology {
    private static final long CHANNEL=0x5cb46a19e2d783f1L;
    private final long seed;
    private final double slope, offset;

    public RockGeology(long seed) {
        this.seed=seed^CHANNEL;
        slope=hash(71,23)*0.45;
        offset=hash(19,7)*120;
    }
    private double hash(int x,int y) {
        long z=seed+0x9e3779b97f4a7c15L*x+0x632be59bd9b4e019L*y;
        z=(z^(z>>>30))*0xbf58476d1ce4e5b9L;
        z=(z^(z>>>27))*0x94d049bb133111ebL;
        return ((z^(z>>>31))>>>11)*0x1.0p-52-1;
    }
    private double noise(double x,double y,double scale) {
        double px=x/scale,py=y/scale; int ix=(int)Math.floor(px),iy=(int)Math.floor(py);
        double a=px-ix,b=py-iy; a=a*a*(3-2*a); b=b*b*(3-2*b);
        double top=hash(ix,iy)*(1-a)+hash(ix+1,iy)*a;
        double bottom=hash(ix,iy+1)*(1-a)+hash(ix+1,iy+1)*a;
        return top*(1-b)+bottom*b;
    }
    /** Sample at a block centre; independent of block grid and other generation stages. */
    public Material sample(double x,double y) {
        double warp=noise(x+1900,y-400,150)*35;
        double granite=noise(x,y,180)*0.75+noise(x+900,y+500,85)*0.25+(y-250)*0.00045;
        double marble=noise(x-1700,y+2200,80)*0.7+noise(x+4000,y-1000,42)*0.3;
        if(marble>0.59) return Material.MARBLE;
        if(granite>0.18) return Material.GRANITE;
        double layer=Math.sin((y+slope*x+offset+warp)*Math.PI/110);
        double breakage=noise(x+3200,y+3100,160);
        if(layer+breakage*0.45>0.62) return Material.LIMESTONE;
        return Material.STONE;
    }
}
