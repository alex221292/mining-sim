package miningsim.generation;

import java.util.ArrayList;
import java.util.SplittableRandom;
import miningsim.resource.OreComposition;
import miningsim.resource.OreId;

/** Seeded deposit geometry, in world cells, sampled once during world generation. */
public final class OreDeposits {
    private record Deposit(OreId ore,double x,double y,double angle,double length,double width,int peak,double phase) { }
    private final ArrayList<Deposit> deposits=new ArrayList<>();

    public OreDeposits(long seed,int width,int height) {
        OreId[] ores={OreId.IRON,OreId.COPPER,OreId.SILVER,OreId.GOLD};
        int[] counts={6,8,5,2};
        int[] peaks={400000,80000,500,50};
        for(int i=0;i<ores.length;i++) {
            var random=new SplittableRandom(seed ^ (0x73a89d25e4c61b0fL * (i+1)));
            int count=(int)Math.round(counts[i]*(width*(double)height)/(800*401));
            for(int j=0;j<count;j++) {
                double length=(i==0?70:45)+random.nextDouble()*(i==0?100:100);
                double thickness=(i==0?35:5)+random.nextDouble()*(i==0?35:12);
                deposits.add(new Deposit(ores[i],random.nextDouble(width),random.nextDouble(height),random.nextDouble(Math.PI),length,thickness,peaks[i],random.nextDouble(Math.PI*2)));
            }
        }
    }

    public OreComposition sample(double x,double y) {
        int[] ppm=new int[4];
        for(var d:deposits) {
            double dx=x-d.x(),dy=y-d.y(),cos=Math.cos(d.angle()),sin=Math.sin(d.angle());
            double along=(dx*cos+dy*sin)/d.length();
            double across=(-dx*sin+dy*cos)/d.width();
            across-=0.35*Math.sin(along*4+d.phase());
            double radius=along*along+across*across;
            if(radius>=1) continue;
            double variation=0.65+0.35*Math.cos(along*8+d.phase())*Math.cos(across*3+d.phase());
            int value=(int)Math.round(d.peak()*(1-radius)*variation);
            int index=d.ore().id()-1;
            ppm[index]=Math.max(ppm[index],value);
        }
        var components=new ArrayList<OreComposition.Component>();
        OreId[] ores={OreId.IRON,OreId.COPPER,OreId.SILVER,OreId.GOLD};
        for(int i=0;i<ppm.length;i++) if(ppm[i]>0) components.add(new OreComposition.Component(ores[i],ppm[i]));
        return OreComposition.of(components.toArray(OreComposition.Component[]::new));
    }
}
