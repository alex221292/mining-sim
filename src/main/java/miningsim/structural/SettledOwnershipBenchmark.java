package miningsim.structural;

import java.util.List;
import java.util.Locale;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.SubstanceRegistry;
import miningsim.world.Material;
import miningsim.world.World;

/** Standalone ownership-index lookup diagnostic, separate from correctness tests. */
public final class SettledOwnershipBenchmark {
    private SettledOwnershipBenchmark() { }
    public static void main(String[] args) {
        var world=new World(400,300,World.Mode.BEDROCK_ENABLED); var connectivity=new RockConnectivity(world);
        var registry=new SettledRockRegistry(world,connectivity,100,1);
        var descriptor=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new FragmentDescriptor.Cell(0,0)));
        for(int i=0;i<100;i++) { int x=4*i%400,y=1+(i/100)*3; world.set(x,y,Material.STONE); registry.registerExisting(i+1,descriptor,x,y); }
        long checksum=0;
        for(int run=0;run<3;run++) {
            long start=System.nanoTime();
            for(int i=0;i<5_000_000;i++) checksum+=registry.ownerAt(i%400,(i/400)%300);
            System.out.printf(Locale.ROOT,"run=%d ns/lookup=%.3f%n",run,(System.nanoTime()-start)/5_000_000.0);
        }
        System.out.println("world=400x300 records=100 dense-payload-bytes="+(400L*300*Long.BYTES)+" checksum="+checksum+" jvm="+System.getProperty("java.runtime.version"));
    }
}
