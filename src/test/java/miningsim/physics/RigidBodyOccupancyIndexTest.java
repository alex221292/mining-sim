package miningsim.physics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import miningsim.fragment.FragmentRegistry;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.FragmentDescriptor.Cell;
import miningsim.resource.SubstanceRegistry;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.api.Test;

class RigidBodyOccupancyIndexTest {
    private static FragmentDescriptor shape(Cell... cells) {
        return new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(cells));
    }
    private static RigidBodySystem system(FragmentRegistry r) { var s=new RigidBodySystem(r); s.synchronize(); return s; }
    private static void pose(RigidBody b,double x,double y,double angle) { var s=b.state(); b.restore(new RigidBody.State(x,y,angle,s.vx(),s.vy(),s.omega(),s.sleeping(),s.sleepTicks())); }
    @Test void integerFractionalTouchingAndRotation() {
        var w=new World(12,12); var r=new FragmentRegistry(1); r.insert(shape(new Cell(0,0)),3,3,0);
        var s=system(r); var index=new RigidBodyOccupancyIndex(w,RigidBodyOccupancyIndex.Limits.defaults());
        var first=index.rebuild(s,1); assertTrue(first.occupied(3,3)); assertFalse(first.occupied(4,3)); assertFalse(first.occupied(4,4));
        assertArrayEquals(new long[]{1},first.bodyIdsAt(3,3));
        pose(s.body(1),5.5,6.5,0); var moved=index.rebuild(s,2); assertTrue(moved.occupied(5,6)); assertFalse(moved.occupied(3,3));
        assertTrue(first.occupied(3,3));
        pose(s.body(1),5.75,6.75,0); var fractional=index.rebuild(s,3);
        assertTrue(fractional.occupied(5,6)); assertTrue(fractional.occupied(6,6)); assertTrue(fractional.occupied(5,7)); assertTrue(fractional.occupied(6,7));
        pose(s.body(1),5.5,6.5,Math.PI/2); var rightAngle=index.rebuild(s,4); assertEquals(1,rightAngle.diagnostics().occupiedCells());
        pose(s.body(1),5.5,6.5,Math.PI/4); var rotated=index.rebuild(s,5);
        assertEquals(5,rotated.diagnostics().occupiedCells()); assertFalse(rotated.occupied(4,5));
        pose(s.body(1),5.5,6.5,0.31); assertTrue(index.rebuild(s,6).occupied(5,6));
        pose(s.body(1),3.5+1e-9,3.5,0); assertTrue(index.rebuild(s,7).occupied(4,3));
        pose(s.body(1),3.5+1e-11,3.5,0); assertFalse(index.rebuild(s,8).occupied(4,3));
    }
    @Test void concavityAndMultipleOccupantsArePreserved() {
        var w=new World(12,12); var r=new FragmentRegistry(2);
        r.insert(shape(new Cell(0,0),new Cell(1,0),new Cell(2,0),new Cell(0,1),new Cell(2,1),new Cell(0,2),new Cell(1,2),new Cell(2,2)),2,2,0);
        r.insert(shape(new Cell(0,0)),2,2,0); var s=system(r);
        var index=new RigidBodyOccupancyIndex(w,RigidBodyOccupancyIndex.Limits.defaults()); var snap=index.rebuild(s.bodies().reversed(),0);
        assertFalse(snap.occupied(3,3)); assertArrayEquals(new long[]{1,2},snap.bodyIdsAt(2,2));
        long[] ids=snap.bodyIdsAt(2,2); ids[0]=999; assertArrayEquals(new long[]{1,2},snap.bodyIdsAt(2,2));
        assertEquals(1,snap.diagnostics().multipleOccupants());
        var canonical=index.rebuild(s.bodies(),0);
        assertArrayEquals(snap.bodyIdsAt(2,2),canonical.bodyIdsAt(2,2));
        assertEquals(snap.diagnostics().candidates(),canonical.diagnostics().candidates());
        var order=new ArrayList<String>(); snap.forEachCell((x,y)->order.add(x+","+y));
        assertEquals(List.of("2,2","3,2","4,2","2,3","4,3","2,4","3,4","4,4"),order);
        assertThrows(IllegalArgumentException.class,()->shape(new Cell(0,0),new Cell(2,0)));
    }
    @Test void boundariesEmptyAndFailureAtomicity() {
        var w=new World(8,8); w.set(1,1,Material.WOOD); long revision=w.mutationRevision();
        var r=new FragmentRegistry(2); r.insert(shape(new Cell(0,0)),2,2,0); var s=system(r);
        var index=new RigidBodyOccupancyIndex(w,RigidBodyOccupancyIndex.Limits.defaults());
        for(double[] position:new double[][]{{0,3.5},{8,3.5},{3.5,0},{3.5,8}}) {
            pose(s.body(1),position[0],position[1],0); var snap=index.rebuild(s,0);
            assertEquals(1,snap.diagnostics().occupiedCells()); assertTrue(snap.diagnostics().clippedCandidates()>0);
        }
        pose(s.body(1),-5,3,0); assertEquals(0,index.rebuild(s,0).diagnostics().occupiedCells());
        var old=index.snapshot(); pose(s.body(1),1e20,1,0);
        assertThrows(IllegalStateException.class,()->index.rebuild(s,0)); assertSame(old,index.snapshot());
        pose(s.body(1),Double.NaN,1,0); assertThrows(IllegalArgumentException.class,()->index.rebuild(s,0)); assertSame(old,index.snapshot());
        assertEquals(revision,w.mutationRevision()); assertEquals(Material.WOOD,w.get(1,1)); assertEquals(2_500_000,r.totalMassMilligrams());
        r.remove(1); s.synchronize(); assertEquals(0,index.rebuild(s,0).diagnostics().indexedBodies());
    }
    @Test void everyBudgetFailsWithoutPublication() {
        var w=new World(8,8); var r=new FragmentRegistry(2); r.insert(shape(new Cell(0,0)),2,2,0); var s=system(r);
        for(var limits:List.of(new RigidBodyOccupancyIndex.Limits(0,10,10,10,10,10,10),
                new RigidBodyOccupancyIndex.Limits(10,0,10,10,10,10,10),new RigidBodyOccupancyIndex.Limits(10,10,0,10,10,10,10),
                new RigidBodyOccupancyIndex.Limits(10,10,10,0,10,10,10),new RigidBodyOccupancyIndex.Limits(10,10,10,10,0,10,10),
                new RigidBodyOccupancyIndex.Limits(10,10,10,10,10,0,10),new RigidBodyOccupancyIndex.Limits(10,10,10,10,10,10,0))) {
            var index=new RigidBodyOccupancyIndex(w,limits); var before=index.snapshot();
            assertThrows(IllegalStateException.class,()->index.rebuild(s,0)); assertSame(before,index.snapshot());
        }
    }
    @Test void physicsAndRegistryPublicationIsExplicit() {
        var w=new World(20,20); var r=new FragmentRegistry(2); r.insert(shape(new Cell(0,0)),2,2,0);
        var s=new RigidBodySystem(r,4,new StaticContactSolver(new StaticCollisionQuery(w,10000,10000),StaticContactSolver.Config.defaults())); s.synchronize();
        var index=new RigidBodyOccupancyIndex(w,RigidBodyOccupancyIndex.Limits.defaults()); var before=index.rebuild(s,4);
        s.body(1).setVelocity(12,0,0); assertSame(before,index.snapshot()); s.step(); assertSame(before,index.snapshot());
        var after=index.rebuild(s,5); assertTrue(after.occupied(4,2)); assertEquals(before.diagnostics().revision()+1,after.diagnostics().revision());
        r.insert(shape(new Cell(0,0)),8,8,0); s.synchronize(); assertEquals(2,index.rebuild(s,6).diagnostics().indexedBodies());
        s.body(1).setVelocity(Double.MAX_VALUE,0,0); var saved=index.snapshot(); assertThrows(IllegalStateException.class,s::step); assertSame(saved,index.snapshot());
    }
    @Test void failedRebuildPreservesNonemptySnapshotAndLongThinGeometry() {
        var w=new World(40,20); var r=new FragmentRegistry(2); var cells=new ArrayList<Cell>();
        for(int x=0;x<12;x++) cells.add(new Cell(x,0));
        r.insert(new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),cells),2,2,0);
        var s=system(r); var index=new RigidBodyOccupancyIndex(w,new RigidBodyOccupancyIndex.Limits(1,100,1000,1000,1000,1000,1000));
        var first=index.rebuild(s,17); assertEquals(12,first.diagnostics().occupiedCells()); assertFalse(first.occupied(8,3));
        r.insert(shape(new Cell(0,0)),25,2,0); s.synchronize();
        assertThrows(IllegalStateException.class,()->index.rebuild(s,18)); assertSame(first,index.snapshot());
        assertTrue(index.snapshot().occupied(2,2)); assertEquals(17,index.snapshot().diagnostics().generation());
    }
}
