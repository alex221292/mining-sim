package miningsim.physics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import miningsim.world.Material;
import org.junit.jupiter.api.Test;

class LargeCompoundRegressionTest {
    private static List<RigidBody.State> run(String kind) {
        var session=LargeCompoundBenchmark.fixture(kind).session();
        for(int tick=0;tick<200;tick++) {
            session.tick();
            assertNull(session.failure(), "tick " + tick);
        }
        assertEquals(3,session.physics().interactionDiagnostics().sleepingBodies());
        var states=session.physics().bodies().stream().map(RigidBody::state).toList();
        for(int tick=0;tick<10;tick++) {
            session.tick();
            assertNull(session.failure());
            assertEquals(0,session.physics().interactionDiagnostics().wokenBodies());
        }
        assertEquals(states,session.physics().bodies().stream().map(RigidBody::state).toList());
        return states;
    }
    @Test void largeRestingStackReplays() { assertEquals(run("rest"),run("rest")); }
    @Test void largeImpactSettles() { run("impact"); }
    @Test void sustainedSlowDriftCannotAccumulateSleepTime() {
        var body=LargeCompoundBenchmark.fixture("large").session().physics().body(3);
        var config=RigidBodySystem.SleepConfig.defaults();
        for(int i=0;i<120;i++) {
            body.translate(0.002,0);
            boolean eligible=body.sleepEligible(true,config);
            body.updateIslandSleep(eligible,body.sleepTicks(),config);
            assertFalse(body.sleeping());
        }
    }
    @Test void intermittentSupportNeverAccumulatesSleepTime() {
        var body=LargeCompoundBenchmark.fixture("large").session().physics().body(3);
        var config=RigidBodySystem.SleepConfig.defaults();
        for(int i=0;i<180;i++) {
            boolean eligible=body.sleepEligible(i%10!=0,config);
            body.updateIslandSleep(eligible,body.sleepTicks(),config);
            assertFalse(body.sleeping());
        }
    }
    @Test void microscopicSupportedSlidingCanSleep() {
        var body=LargeCompoundBenchmark.fixture("large").session().physics().body(3);
        var config=RigidBodySystem.SleepConfig.defaults();
        body.setVelocity(0.0005,0,0);
        for(int i=0;i<120;i++) {
            double speed=i%2==0?0.00049:0.00051;
            body.applyImpulse((speed-body.velocityXMetresPerSecond())*body.geometry().massKg(),0,0,0);
            body.translate(speed/6,0); // 60 Hz, 0.1 m per cell
            boolean eligible=body.sleepEligible(true,config);
            body.updateIslandSleep(eligible,body.sleepTicks(),config);
            if(body.sleeping()) { assertEquals(0,body.velocityXMetresPerSecond()); return; }
        }
        fail("microscopic supported motion should eventually sleep");
    }
    @Test void largeBodyInFreeFallDoesNotSleep() {
        var session=LargeCompoundBenchmark.fixture("large").session();
        for(int i=0;i<15;i++) {
            session.tick(); assertNull(session.failure());
            assertFalse(session.physics().body(3).sleeping());
            assertTrue(session.physics().body(3).velocityYMetresPerSecond()>0);
        }
    }
    @Test void largestStackSleepsAndWakesAfterSupportRemoval() {
        var session=LargeCompoundBenchmark.fixture("largeWood").session();
        int ticks=0;
        while(ticks++<800 && session.physics().interactionDiagnostics().sleepingBodies()!=3) {
            session.tick(); assertNull(session.failure(),"tick "+ticks);
        }
        assertEquals(3,session.physics().interactionDiagnostics().sleepingBodies(),"did not settle");
        var states=session.physics().bodies().stream().map(RigidBody::state).toList();
        for(int i=0;i<30;i++) {
            session.tick(); assertNull(session.failure());
            assertEquals(0,session.physics().interactionDiagnostics().wokenBodies());
            assertEquals(states,session.physics().bodies().stream().map(RigidBody::state).toList());
        }
        for(int x=0;x<64;x++) session.world().set(x,62,Material.AIR);
        session.tick(); assertNull(session.failure());
        assertTrue(session.physics().interactionDiagnostics().wokenBodies()>0);
        for(int i=0;i<15;i++) { session.tick(); assertNull(session.failure()); }
        assertTrue(session.physics().body(3).yCells()>states.get(2).y());
    }
    @Test void largeFailureRestoresPhysicsAndOccupancy() {
        var session=LargeCompoundBenchmark.fixture("rest").session();
        session.tick();
        var states=session.physics().bodies().stream().map(RigidBody::state).toList();
        var occupancy=session.occupancy().snapshot();
        session.physics().setTestCheckpoint(point->{ if(point==RigidBodySystem.Checkpoint.AFTER_PAIR_QUERY) throw new IllegalStateException("injected"); });
        assertThrows(IllegalStateException.class,session::tick);
        assertEquals(states,session.physics().bodies().stream().map(RigidBody::state).toList());
        assertSame(occupancy,session.occupancy().snapshot());
    }
}
