package miningsim;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import miningsim.app.Brush;
import miningsim.app.InteractiveSession;
import miningsim.app.StructuralSimulationController;
import miningsim.fragment.ExtractionCommit;
import miningsim.structural.RockReferenceScenes;
import miningsim.world.Material;
import org.junit.jupiter.api.Test;

class IntegrationReplaySoakTest {
    private static List<String> replay() {
        var result=new ArrayList<String>();
        for(int sceneNumber=0;sceneNumber<3;sceneNumber++) {
            var scene=RockReferenceScenes.interactive("soak",Material.AIR);
            var structural=new StructuralSimulationController(scene); var session=new InteractiveSession(scene.world(),structural);
            session.enqueue(()->Brush.paintWorld(scene.world(),2.5,2.5,0,Material.SAND,session.occupancy().snapshot()));
            session.enqueue(()->Brush.paintWorld(scene.world(),20.5,2.5,0,Material.WATER,session.occupancy().snapshot()));
            for(int i=0;i<4;i++) session.tick();
            session.enqueue(()->structural.select(12,3)); session.tick();
            assertEquals(ExtractionCommit.Status.COMMITTED,session.extractSelected());
            assertEquals(ExtractionCommit.Status.INVALID,session.extractSelected());
            for(int i=0;i<400;i++) {
                if(i==250) session.enqueue(()->scene.world().set(1,1,Material.WOOD));
                session.tick(); assertFalse(session.blocked(),session.failure());
            }
            assertEquals(1,structural.registry().size()); assertEquals(1,session.physics().bodies().size());
            assertEquals(2_500_000,structural.registry().totalMassMilligrams());
            int sand=0,water=0;
            for(int y=0;y<scene.world().height();y++) for(int x=0;x<scene.world().width();x++) {
                var m=scene.world().get(x,y); result.add(m.name());
                if(m==Material.SAND) sand++; if(m==Material.WATER) water++;
            }
            assertEquals(1,sand); assertEquals(1,water);
            var body=session.physics().body(1); var fragment=body.fragment();
            result.add(fragment.id()+":"+fragment.descriptor().cells()+":"+fragment.substance()+":"+fragment.massMilligrams());
            result.add(body.xCells()+":"+body.yCells()+":"+body.angleRadians()+":"+body.velocityXMetresPerSecond()+":"+body.velocityYMetresPerSecond()+":"+body.angularVelocityRadiansPerSecond()+":"+body.sleeping()+":"+body.sleepTicks());
            session.occupancy().snapshot().forEachCell((x,y)->result.add(x+","+y+":"+java.util.Arrays.toString(session.occupancy().snapshot().bodyIdsAt(x,y))));
            scene.connectivity().forEachBond(0,0,scene.world().width(),scene.world().height(),(x,y,nx,ny,state)->result.add(x+","+y+","+nx+","+ny+":"+state));
            result.add(session.coupled().diagnostics().toString()); result.add(structural.view().toString());
            session.retire(); session.tick();
        }
        return result;
    }
    @Test void deterministicReplayAndRepeatedResetSoak() { assertEquals(replay(),replay()); }
}
