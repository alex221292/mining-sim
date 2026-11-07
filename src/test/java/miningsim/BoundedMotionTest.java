package miningsim;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.stream.IntStream;
import miningsim.fragment.FragmentRegistry;
import miningsim.physics.*;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.FragmentDescriptor.Cell;
import miningsim.resource.SubstanceRegistry;
import miningsim.world.*;
import org.junit.jupiter.api.Test;

class BoundedMotionTest {
    private static FragmentDescriptor shape(List<Cell> cells) {
        return new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE), cells);
    }
    private static RigidBodySystem system(World w, FragmentRegistry r) {
        return new RigidBodySystem(r, 4, new StaticContactSolver(new StaticCollisionQuery(w,100000,1000000),StaticContactSolver.Config.defaults()));
    }
    private static FragmentRegistry registry(int x, int y, List<Cell> cells) {
        var r=new FragmentRegistry(8); r.insert(shape(cells),x,y,0); return r;
    }
    private static final List<Cell> CELL=List.of(new Cell(0,0));
    @Test void highSpeedFloorCeilingAndWalls() {
        for(int direction=0;direction<4;direction++) {
            var w=new World(30,30);
            if(direction<2) for(int x=0;x<30;x++) w.set(x,15,Material.STONE);
            else for(int y=0;y<30;y++) w.set(15,y,Material.STONE);
            var r=registry(direction==2?10:direction==3?19:12,direction==0?10:direction==1?19:12,CELL);
            var s=system(w,r); s.synchronize(); var b=s.body(1);
            b.setVelocity(direction==2?60:direction==3?-60:0,direction==0?60:direction==1?-60:0,0);
            s.step();
            switch(direction) {
                case 0 -> assertTrue(b.yCells()<15.1);
                case 1 -> assertTrue(b.yCells()>15.9);
                case 2 -> assertTrue(b.xCells()<15.1);
                default -> assertTrue(b.xCells()>15.9);
            }
            assertTrue(s.motionDiagnostics().executedMicrosteps()>4);
            assertTrue(s.motionDiagnostics().maxPredictedPointTravelCells()<=0.250000000001);
            assertEquals(30,w.occupiedCount()); assertEquals(2_500_000,r.totalMassMilligrams());
        }
    }
    @Test void highSpeedBoundariesAndBedrock() {
        for(int dir=0;dir<4;dir++) {
            var w=new World(20,20); var s=system(w,registry(8,8,CELL)); s.synchronize();
            var b=s.body(1); b.setVelocity(dir==0?-80:dir==1?80:0,dir==2?-80:dir==3?80:0,0);
            s.step(); assertTrue(b.xCells()>-0.1 && b.xCells()<20.1 && b.yCells()>-0.1 && b.yCells()<20.1);
        }
        var w=new World(20,20,World.Mode.BEDROCK_ENABLED); var s=system(w,registry(8,10,CELL));
        s.synchronize(); s.body(1).setVelocity(0,80,0); s.step();
        assertTrue(s.body(1).yCells()<19); assertEquals(Material.BEDROCK,w.get(8,19));
    }
    @Test void rotationBudgetAndDeterministicReplay() {
        var line=IntStream.range(0,8).mapToObj(x->new Cell(x,0)).toList();
        var w=new World(40,40); var a=system(w,registry(10,10,line)); var b=system(w,registry(10,10,line));
        a.synchronize(); b.synchronize(); a.body(1).setVelocity(0,0,30); b.body(1).setVelocity(0,0,30);
        for(int i=0;i<5;i++){ a.step(); b.step(); assertEquals(a.motionDiagnostics(),b.motionDiagnostics()); }
        assertEquals(a.body(1).angleRadians(),b.body(1).angleRadians());
        assertTrue(a.motionDiagnostics().maxMicrostepsPerBody()>4);
        assertTrue(a.body(1).geometry().maxRadiusCells()>4);
    }
    @Test void budgetFailureRollsBackAndAllowsRetry() {
        var r=registry(2,2,CELL); r.insert(shape(CELL),5,5,0); var w=new World(30,30);
        var s=new RigidBodySystem(r,4,new StaticContactSolver(new StaticCollisionQuery(w,100,100),StaticContactSolver.Config.defaults()),
                new RigidBodySystem.MotionConfig(0.25,8,100));
        s.synchronize(); var first=s.body(1); var second=s.body(2); second.setVelocity(200,0,0);
        double y=first.yCells(), x=second.xCells();
        var e=assertThrows(RigidBodySystem.MotionBudgetException.class,s::step);
        assertTrue(e.getMessage().contains("remaining")); assertNotNull(s.lastFailure());
        assertEquals(y,first.yCells()); assertEquals(x,second.xCells()); assertEquals(0,first.velocityYMetresPerSecond());
        second.setVelocity(0,0,0); s.step(); assertNull(s.lastFailure()); assertTrue(first.yCells()>y);
    }
    @Test void totalBudgetIsExplicitAndAtomic() {
        var r=registry(2,2,CELL); r.insert(shape(CELL),5,5,0);
        var s=new RigidBodySystem(r,4,new StaticContactSolver(new StaticCollisionQuery(new World(30,30),100,100),StaticContactSolver.Config.defaults()),
                new RigidBodySystem.MotionConfig(0.25,128,7));
        s.synchronize(); var before=s.body(1).yCells();
        var ex=assertThrows(RigidBodySystem.MotionBudgetException.class,s::step);
        assertTrue(ex.getMessage().contains("total remaining")); assertEquals(before,s.body(1).yCells());
        assertEquals(0,s.motionDiagnostics().executedMicrosteps());
    }
    @Test void isolatedObstacleAndOpening() {
        var w=new World(30,30); w.set(15,12,Material.STONE);
        var s=system(w,registry(10,12,CELL)); s.synchronize(); s.body(1).setVelocity(60,0,0); s.step();
        assertTrue(s.body(1).xCells()<15.1); assertEquals(Material.STONE,w.get(15,12));
        var opening=new World(30,30); for(int y=0;y<30;y++) if(y!=12 && y!=13) opening.set(15,y,Material.STONE);
        var passage=system(opening,registry(10,12,CELL)); passage.synchronize(); passage.body(1).setVelocity(60,0,0);
        passage.step(); assertTrue(passage.body(1).xCells()>16);
    }
    @Test void thinPlatformAndConcaveRotatedBody() {
        var w=new World(40,40); for(int x=10;x<16;x++) w.set(x,20,Material.STONE);
        var r=registry(11,13,List.of(new Cell(0,0),new Cell(1,0),new Cell(0,1)));
        var s=system(w,r); s.synchronize(); s.body(1).setVelocity(0,60,3); s.step();
        assertTrue(s.body(1).yCells()<20); assertTrue(Double.isFinite(s.body(1).angleRadians()));
        assertEquals(7_500_000,r.totalMassMilligrams());
    }
    @Test void queryFailureDuringMicrosteppingRollsBackAndRestingIsCheap() {
        var r=registry(2,2,CELL); r.insert(shape(CELL),5,5,0); var w=new World(20,20); w.set(5,5,Material.STONE);
        var s=new RigidBodySystem(r,4,new StaticContactSolver(new StaticCollisionQuery(w,100,4),StaticContactSolver.Config.defaults()));
        s.synchronize(); s.body(1).setVelocity(30,0,0); double x=s.body(1).xCells(), y=s.body(2).yCells();
        assertThrows(IllegalStateException.class,s::step); assertEquals(x,s.body(1).xCells()); assertEquals(y,s.body(2).yCells());
        assertTrue(s.lastFailure().contains("limit"));
        var floor=new World(20,20); for(int i=0;i<20;i++) floor.set(i,15,Material.STONE);
        var rest=system(floor,registry(8,8,CELL)); for(int i=0;i<700;i++) rest.step();
        assertEquals(4,rest.motionDiagnostics().executedMicrosteps());
        assertTrue(rest.body(1).yCells()<14.6);
    }
    @Test void invalidConfigurationAndVelocityAreRejected() {
        assertThrows(IllegalArgumentException.class,()->new RigidBodySystem.MotionConfig(Double.NaN,1,1));
        assertThrows(IllegalArgumentException.class,()->new RigidBodySystem.MotionConfig(0.26,1,1));
        var s=system(new World(10,10),registry(2,2,CELL)); s.synchronize();
        assertThrows(IllegalArgumentException.class,()->s.body(1).setVelocity(0,Double.POSITIVE_INFINITY,0));
    }
    @Test void rotationSweepsLongFragmentIntoObstacle() {
        var line=IntStream.range(0,8).mapToObj(x->new Cell(x,0)).toList();
        var w=new World(40,40); w.set(17,12,Material.STONE);
        var s=system(w,registry(10,10,line)); s.synchronize(); s.body(1).setVelocity(0,0,30); s.step();
        assertTrue(s.motionDiagnostics().maxMicrostepsPerBody()>4);
        assertTrue(s.diagnostics().contacts()>0);
        assertEquals(Material.STONE,w.get(17,12));
    }
    @Test void diagonalWallAndWorldCorner() {
        var wall=new World(30,30); for(int y=0;y<30;y++) wall.set(15,y,Material.STONE);
        var s=system(wall,registry(10,8,CELL)); s.synchronize(); s.body(1).setVelocity(60,30,0); s.step();
        assertTrue(s.body(1).xCells()<15.1); assertTrue(s.diagnostics().contacts()>0);
        var corner=system(new World(20,20),registry(3,3,CELL)); corner.synchronize();
        corner.body(1).setVelocity(-60,-60,12); corner.step();
        assertTrue(corner.body(1).xCells()>-0.1 && corner.body(1).yCells()>-0.1);
        assertTrue(Double.isFinite(corner.body(1).angleRadians()));
    }
}
