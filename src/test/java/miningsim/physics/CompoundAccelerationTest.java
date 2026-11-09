package miningsim.physics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import miningsim.fragment.FragmentRegistry;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.FragmentDescriptor.Cell;
import miningsim.resource.SubstanceRegistry;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.api.Test;

class CompoundAccelerationTest {
    private static FragmentDescriptor shape(Random random,int count) {
        var set=new HashSet<Cell>(); int x=12,y=12; set.add(new Cell(x,y));
        while(set.size()<count) {
            switch(random.nextInt(4)) { case 0 -> x++; case 1 -> x--; case 2 -> y++; default -> y--; }
            x=Math.max(1,Math.min(23,x)); y=Math.max(1,Math.min(23,y)); set.add(new Cell(x,y));
        }
        int minX=set.stream().mapToInt(Cell::x).min().orElseThrow(),minY=set.stream().mapToInt(Cell::y).min().orElseThrow();
        var cells=new ArrayList<Cell>(); for(var c:set) cells.add(new Cell(c.x()-minX,c.y()-minY));
        return new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),cells);
    }
    @Test void acceleratedContactsMatchBruteForceInRotatedConcaveAndBoundaryCases() {
        var random=new Random(40961);
        for(int iteration=0;iteration<200;iteration++) {
            var world=new World(24,24,World.Mode.BEDROCK_ENABLED);
            for(int i=0;i<28;i++) world.set(random.nextInt(24),random.nextInt(23),i%2==0?Material.STONE:Material.WOOD);
            var registry=new FragmentRegistry(2); registry.insert(shape(random,4+random.nextInt(24)),3,3,0); registry.insert(shape(random,4+random.nextInt(24)),3,3,0);
            var physics=new RigidBodySystem(registry); physics.synchronize(); var a=physics.body(1); var b=physics.body(2);
            double ax=iteration%5==0?.05:3+random.nextDouble()*15,ay=iteration%7==0?23.7:3+random.nextDouble()*15;
            double bx=ax+(random.nextDouble()-.5)*4,by=ay+(random.nextDouble()-.5)*4;
            a.restore(new RigidBody.State(ax,ay,random.nextDouble()*6.28-3.14,0,0,0,false,0));
            b.restore(new RigidBody.State(bx,by,random.nextDouble()*6.28-3.14,0,0,0,false,0));
            var query=new BodyCollisionQuery(new BodyCollisionQuery.Limits(10,1000000,4096,4096));
            var accelerated=query.query(List.of(a,b)); var reference=query.queryBruteForce(List.of(a,b));
            assertEquals(reference.contacts(),accelerated.contacts(),"body iteration "+iteration);
            assertEquals(reference.rawContacts(),accelerated.rawContacts());
            var staticQuery=new StaticCollisionQuery(world,100000,1000000,StaticCollisionQuery.MaterialPolicy.DISPLACE_GRANULAR);
            assertEquals(staticQuery.queryBruteForce(a).contacts(),staticQuery.query(a).contacts(),"static iteration "+iteration);
        }
    }
    @Test void grazingAndExactTouchMatchBruteForce() {
        var random=new Random(9); var registry=new FragmentRegistry(2);
        registry.insert(shape(random,16),1,1,0); registry.insert(shape(random,16),1,1,0);
        var system=new RigidBodySystem(registry); system.synchronize(); var a=system.body(1); var b=system.body(2);
        var query=new BodyCollisionQuery(new BodyCollisionQuery.Limits(10,100000,4096,4096));
        for(double angle:new double[]{0,1e-12,Math.PI/4,Math.PI/2}) for(double delta:new double[]{-1e-10,0,1e-10}) {
            a.restore(new RigidBody.State(5,5,angle,0,0,0,false,0));
            b.restore(new RigidBody.State(6+delta,5,-angle,0,0,0,false,0));
            assertEquals(query.queryBruteForce(List.of(a,b)).contacts(),query.query(List.of(a,b)).contacts());
        }
    }
}
