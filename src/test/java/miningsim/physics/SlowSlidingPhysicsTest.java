package miningsim.physics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import miningsim.fragment.FragmentRegistry;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.SubstanceRegistry;
import miningsim.world.World;
import org.junit.jupiter.api.Test;

class SlowSlidingPhysicsTest {
    @Test void traceLargeStackReactions() {
        var session=LargeCompoundBenchmark.fixture("large").session();
        var staticQuery=new StaticCollisionQuery(session.world(),100000,1000000,StaticCollisionQuery.MaterialPolicy.DISPLACE_GRANULAR);
        var bodyQuery=new BodyCollisionQuery(new BodyCollisionQuery.Limits(1000000,10000000,4096,4096));
        for(int tick=0;tick<230;tick++) {
            var bodies=session.physics().bodies();
            var before=bodies.stream().map(RigidBody::state).toList();
            session.tick(); assertNull(session.failure());
            if(tick==59 || tick==119 || tick==179 || tick==228) {
                double reactionX=0,reactionY=0;
                for(int i=0;i<bodies.size();i++) {
                    var b=bodies.get(i); var previous=before.get(i); double mass=b.geometry().massKg();
                    reactionX+=mass*(b.velocityXMetresPerSecond()-previous.vx());
                    reactionY+=mass*(b.velocityYMetresPerSecond()-previous.vy()+RigidBodySystem.GRAVITY_METRES_PER_SECOND_SQUARED*RigidBodySystem.TICK_SECONDS);
                    var contacts=staticQuery.query(b).contacts();
                    System.out.printf(java.util.Locale.ROOT,"stack tick=%d id=%d x=%.8f y=%.8f vx=%.6f vy=%.6f omega=%.6f dx=%.8f static=%d first=%s sleep=%d%n",tick,b.id(),b.xCells(),b.yCells(),b.velocityXMetresPerSecond(),b.velocityYMetresPerSecond(),b.angularVelocityRadiansPerSecond(),b.xCells()-previous.x(),contacts.size(),contacts.isEmpty()?"none":contacts.getFirst(),b.sleepTicks());
                }
                var contacts=bodyQuery.query(bodies).contacts();
                System.out.printf(java.util.Locale.ROOT,"stack tick=%d netReaction=(%.6f,%.6f) contacts=%s correction=%.8f sleeping=%d%n",tick,reactionX,reactionY,contacts,session.physics().profile().maxPositionCorrectionCells(),session.physics().interactionDiagnostics().sleepingBodies());
            }
        }
    }
    @Test void microscopicFrictionlessSlidingMaySleep() { checkSliding(0.0005,true); }
    @Test void significantFrictionlessSlidingRemainsAwake() { checkSliding(0.03,false); }
    private void checkSliding(double speed,boolean maySleep) {
        var world=new World(20,20,World.Mode.BEDROCK_ENABLED);
        var registry=new FragmentRegistry(1);
        var shape=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new FragmentDescriptor.Cell(0,0)));
        registry.insert(shape,8,18,0);
        var query=new StaticCollisionQuery(world,10000,100000);
        var solver=new StaticContactSolver(query,new StaticContactSolver.Config(0,0,0,8,3,0.01,0.2,0.5));
        var system=new RigidBodySystem(registry,4,solver,RigidBodySystem.MotionConfig.defaults(),
                new BodyCollisionQuery(new BodyCollisionQuery.Limits(100,10000,100,100)),
                new BodyContactSolver(BodyContactSolver.Config.defaults()));
        system.synchronize(); system.enableProfiling(true); var body=system.body(1); body.setVelocity(speed,0,0);
        for(int tick=0;tick<120;tick++) {
            double beforeY=body.velocityYMetresPerSecond();
            system.step();
            if(tick==0 || tick==30 || tick==59 || body.sleeping()) {
                var contacts=query.query(body).contacts();
                double reaction=body.geometry().massKg()*(body.velocityYMetresPerSecond()-beforeY+RigidBodySystem.GRAVITY_METRES_PER_SECOND_SQUARED*RigidBodySystem.TICK_SECONDS);
                System.out.printf(java.util.Locale.ROOT,"tick=%d x=%.9f y=%.9f vx=%.9f vy=%.9f reactionY=%.9f contacts=%s correction=%.9f sleepTicks=%d sleeping=%s%n",tick,body.xCells(),body.yCells(),body.velocityXMetresPerSecond(),body.velocityYMetresPerSecond(),reaction,contacts,system.profile().maxPositionCorrectionCells(),body.sleepTicks(),body.sleeping());
            }
            if(body.sleeping()) { assertTrue(maySleep); assertEquals(0,body.velocityXMetresPerSecond()); return; }
            assertEquals(speed,body.velocityXMetresPerSecond(),1e-10);
        }
        assertFalse(maySleep,"microscopic supported sliding did not sleep");
    }
}
