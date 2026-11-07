package miningsim;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import miningsim.fragment.FragmentRegistry;
import miningsim.physics.*;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.FragmentDescriptor.Cell;
import miningsim.resource.SubstanceRegistry;
import miningsim.world.*;
import org.junit.jupiter.api.Test;

class StaticContactSolverTest {
    private static final FragmentDescriptor CELL = new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE), List.of(new Cell(0,0)));
    private RigidBodySystem system(World world, FragmentRegistry registry, StaticContactSolver.Config config) {
        return new RigidBodySystem(registry, 4, new StaticContactSolver(new StaticCollisionQuery(world, 10000, 100000), config));
    }
    private FragmentRegistry registry(int x, int y) {
        var registry = new FragmentRegistry(8); registry.insert(CELL, x, y, 0); return registry;
    }
    @Test void fallRestAndConserve() {
        var world = new World(16, 16); for (int x=0;x<16;x++) world.set(x, 12, Material.STONE);
        var r=registry(6,3); var system=system(world,r,StaticContactSolver.Config.defaults());
        for (int i=0;i<900;i++) system.step();
        var b=system.body(1);
        assertEquals(11.5,b.yCells(),0.04); assertEquals(0,b.velocityYMetresPerSecond(),0.03);
        assertEquals(0,b.velocityXMetresPerSecond(),0.03); assertEquals(0,b.angularVelocityRadiansPerSecond(),0.1);
        assertEquals(16,world.occupiedCount()); assertEquals(2_500_000,r.totalMassMilligrams());
    }
    @Test void cardinalBoundaryImpulsesOpposeApproach() {
        double[][] velocities={{-2,0},{2,0},{0,-2},{0,2}};
        int[][] origins={{0,4},{9,4},{4,0},{4,9}};
        for (int i=0;i<4;i++) {
            var s=system(new World(10,10),registry(origins[i][0],origins[i][1]),StaticContactSolver.Config.defaults());
            s.synchronize(); var b=s.body(1); b.setVelocity(velocities[i][0],velocities[i][1],0); s.step();
            double dot=b.velocityXMetresPerSecond()*velocities[i][0]+b.velocityYMetresPerSecond()*velocities[i][1];
            assertTrue(dot<0.1,"direction "+i+" dot="+dot);
        }
    }
    @Test void queryFailureRollsBackAllBodies() {
        var r=registry(1,1); r.insert(CELL, 4, 4, 0);
        var world=new World(10,10); world.set(4,4,Material.STONE);
        var solver=new StaticContactSolver(new StaticCollisionQuery(world,100,4),StaticContactSolver.Config.defaults());
        var s=new RigidBodySystem(r,4,solver); s.synchronize(); var a=s.body(1); double y=a.yCells();
        assertThrows(IllegalStateException.class,s::step); assertEquals(y,a.yCells()); assertEquals(0,a.velocityYMetresPerSecond());
    }
    @Test void deterministicAndFriction() {
        var world=new World(20,10); for(int x=0;x<20;x++) world.set(x,7,Material.STONE);
        var a=system(world,registry(5,5),StaticContactSolver.Config.defaults());
        var b=system(world,registry(5,5),StaticContactSolver.Config.defaults());
        a.synchronize(); b.synchronize(); a.body(1).setVelocity(0.2,0,0); b.body(1).setVelocity(0.2,0,0);
        for(int i=0;i<300;i++){a.step();b.step();}
        assertEquals(a.body(1).xCells(),b.body(1).xCells()); assertEquals(a.body(1).yCells(),b.body(1).yCells());
        assertTrue(Math.abs(a.body(1).velocityXMetresPerSecond()) < 0.2);
    }
    @Test void offCenterImpactGeneratesRotation() {
        var r=new FragmentRegistry(1);
        var shape=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),
                List.of(new Cell(0,0),new Cell(1,0),new Cell(2,0)));
        r.insert(shape,3,3,0); var world=new World(12,12); world.set(3,4,Material.STONE);
        var s=system(world,r,StaticContactSolver.Config.defaults()); s.synchronize();
        s.body(1).setVelocity(0,1,0); s.step();
        assertTrue(Math.abs(s.body(1).angularVelocityRadiansPerSecond()) > 0.1);
        assertTrue(Double.isFinite(s.body(1).angularVelocityRadiansPerSecond()));
    }
    @Test void restitutionAndFrictionlessSliding() {
        var world=new World(30,10); for(int x=0;x<30;x++) world.set(x,7,Material.STONE);
        var config=new StaticContactSolver.Config(0.5,0,0,8,3,0.01,0.2,0.5);
        var s=system(world,registry(5,5),config); s.synchronize(); s.body(1).setVelocity(0.2,1,0);
        boolean bounced=false;
        for(int i=0;i<60;i++){ s.step(); if(s.body(1).velocityYMetresPerSecond() < -0.1) bounced=true; }
        assertTrue(bounced); assertEquals(0.2,s.body(1).velocityXMetresPerSecond(),1e-8);
    }
    @Test void bedrockLandingAndInvalidConfiguration() {
        var world=new World(10,10,World.Mode.BEDROCK_ENABLED); var r=registry(4,3);
        var s=system(world,r,StaticContactSolver.Config.defaults());
        for(int i=0;i<700;i++) s.step();
        assertEquals(8.5,s.body(1).yCells(),0.05); assertEquals(Material.BEDROCK,world.get(4,9));
        assertThrows(IllegalArgumentException.class, () -> new StaticContactSolver.Config(Double.NaN,0,0,8,3,0.01,0.2,0.5));
    }
    @Test void lowSpeedContactDoesNotBounceAndCornerRemainsFinite() {
        var w=new World(10,10); var s=system(w,registry(0,8),StaticContactSolver.Config.defaults());
        s.synchronize(); s.body(1).setVelocity(-0.2,0.1,0);
        for(int i=0;i<700;i++) s.step();
        var body=s.body(1);
        assertTrue(Double.isFinite(body.xCells()) && Double.isFinite(body.yCells()));
        assertTrue(Double.isFinite(body.angularVelocityRadiansPerSecond()));
        assertTrue(body.xCells() > 0.45 && body.yCells() < 9.56);
        assertTrue(Math.abs(body.velocityYMetresPerSecond()) < 0.05);
    }
    @Test void rotatedIrregularLandingStaysFinite() {
        var r=new FragmentRegistry(1);
        r.insert(new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),
                List.of(new Cell(0,0),new Cell(1,0),new Cell(0,1))),4,2,0);
        var w=new World(12,12); for(int x=0;x<12;x++) w.set(x,10,Material.STONE);
        var s=system(w,r,StaticContactSolver.Config.defaults()); s.synchronize(); s.body(1).setVelocity(0,0,1);
        for(int i=0;i<500;i++) s.step();
        assertTrue(Double.isFinite(s.body(1).yCells())); assertTrue(s.body(1).yCells() < 10);
        assertEquals(7_500_000,r.totalMassMilligrams());
    }
    @Test void stoneWallsOpposeBothDirections() {
        for (int sign : new int[]{-1, 1}) {
            var w=new World(12,12); for(int y=0;y<12;y++) w.set(sign > 0 ? 6 : 4,y,Material.STONE);
            var s=system(w,registry(5,5),StaticContactSolver.Config.defaults());
            s.synchronize(); s.body(1).setVelocity(sign,0,0); s.step();
            assertTrue(sign*s.body(1).velocityXMetresPerSecond() < 0.1);
            assertEquals(12,w.occupiedCount());
        }
    }
    @Test void symmetricMultiCellFloorDoesNotMultiplyImpactEnergy() {
        var shape=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),
                List.of(new Cell(0,0),new Cell(1,0),new Cell(2,0)));
        var r=new FragmentRegistry(1); r.insert(shape,4,4,0);
        var w=new World(16,12); for(int x=0;x<16;x++) w.set(x,5,Material.STONE);
        var config=new StaticContactSolver.Config(0.05,0.7,0.5,8,3,0.01,0.2,0.5);
        var s=system(w,r,config); s.synchronize(); s.body(1).setVelocity(0,1,0); s.step();
        assertTrue(s.body(1).velocityYMetresPerSecond() > -0.15);
        assertTrue(Math.abs(s.body(1).angularVelocityRadiansPerSecond()) < 0.2);
    }
}
