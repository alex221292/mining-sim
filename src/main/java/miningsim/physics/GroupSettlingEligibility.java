package miningsim.physics;

import java.util.List;
import java.util.Objects;
import miningsim.structural.RockConnectivity;
import miningsim.structural.SettledRockRegistry;
import miningsim.world.World;

/** Owner-thread observer: call after every attempted coupled tick. Support is checked at preparation. */
public final class GroupSettlingEligibility {
    private final List<Long> ids;
    private List<RigidBody.State> anchors=List.of(),states=List.of();
    private long tick=-1,attempt=-1,terrain=-1,topologyRevision=-1,ownershipRevision=-1;
    private int quiet;
    public GroupSettlingEligibility(List<Long> bodyIds) {
        Objects.requireNonNull(bodyIds);
        if(bodyIds.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("Null group ID");
        ids=bodyIds.stream().sorted().toList();
        if(ids.isEmpty() || ids.size()>3 || ids.getFirst()<=0 || ids.stream().distinct().count()!=ids.size())
            throw new IllegalArgumentException("Invalid group IDs");
    }
    public int quietTicks() { return quiet; }
    public boolean observe(RigidBodySystem physics,CoupledSimulationStep coupled,World world,RockConnectivity topology,SettledRockRegistry settled) {
        if(!coupled.owns(world,physics) || !settled.owns(world,topology)) throw new IllegalArgumentException("Mismatched owners");
        long t=coupled.diagnostics().ticks(),a=coupled.attempts();
        var current=new java.util.ArrayList<RigidBody.State>();
        for(long id:ids) {
            var body=physics.body(id);
            if(body==null || !body.sleeping() || Math.hypot(body.velocityXMetresPerSecond(),body.velocityYMetresPerSecond())>=0.02
                    || Math.abs(body.angularVelocityRadiansPerSecond())>=0.02) { quiet=0; tick=t; attempt=a; return false; }
            current.add(body.state());
        }
        if(t==0 || coupled.lastFailure()!=null || !coupled.diagnostics().physicsSucceeded()) { quiet=0; tick=t; attempt=a; return false; }
        long tr=world.rigidTerrainRevision(),top=topology.revision(),own=settled.revision();
        boolean continuous=quiet>0 && t==tick+1 && a==attempt+1 && tr==terrain && top==topologyRevision && own==ownershipRevision;
        if(continuous) for(int i=0;i<current.size();i++) {
            var p=current.get(i); var anchor=anchors.get(i);
            if(Math.hypot(p.x()-anchor.x(),p.y()-anchor.y())>=0.05 || Math.abs(p.angle()-anchor.angle())>=0.002) { continuous=false; break; }
        }
        if(!continuous) { quiet=0; anchors=List.copyOf(current); }
        quiet=Math.min(120,quiet+1); states=List.copyOf(current); tick=t; attempt=a; terrain=tr; topologyRevision=top; ownershipRevision=own;
        return quiet==120;
    }
    boolean valid(List<Long> requested,RigidBodySystem physics,CoupledSimulationStep coupled,World world,RockConnectivity topology,SettledRockRegistry settled) {
        if(!ids.equals(requested) || quiet<120 || !coupled.owns(world,physics) || !settled.owns(world,topology)
                || coupled.lastFailure()!=null || coupled.diagnostics().ticks()!=tick || coupled.attempts()!=attempt
                || world.rigidTerrainRevision()!=terrain || topology.revision()!=topologyRevision || settled.revision()!=ownershipRevision) return false;
        for(int i=0;i<ids.size();i++) { var body=physics.body(ids.get(i)); if(body==null || !body.state().equals(states.get(i))) return false; }
        return true;
    }
}
