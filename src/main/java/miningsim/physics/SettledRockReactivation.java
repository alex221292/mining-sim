package miningsim.physics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import miningsim.resource.FragmentDescriptor;
import miningsim.structural.RockConnectivity;
import miningsim.structural.SettledRockRegistry;
import miningsim.world.Material;
import miningsim.world.World;

/** Explicit single-owner, headless transfer of one live settled rock back to rigid ownership. */
public final class SettledRockReactivation {
    public enum Status { READY, NOT_FOUND, INVALID_ROCK, CONFLICT, CAPACITY_OR_BUDGET, PREPARATION_FAILED }
    public record Result(Status status,Prepared plan,String reason) { }
    public static final class Prepared {
        private final World world;
        private final RigidBodySystem.PreparedReactivation rigid;
        private final SettledRockRegistry.PreparedReactivationRemoval removal;
        private final SettledRockRegistry settled;
        private final List<World.CellChange> changes;
        private final FragmentDescriptor descriptor;
        private final long settledId,bodyId,worldRevision;
        private Prepared(World world,RigidBodySystem.PreparedReactivation rigid,SettledRockRegistry.PreparedReactivationRemoval removal,SettledRockRegistry settled,
                         List<World.CellChange> changes,FragmentDescriptor descriptor,long settledId) {
            this.world=world; this.rigid=rigid; this.removal=removal; this.settled=settled; this.changes=List.copyOf(changes);
            this.descriptor=descriptor; this.settledId=settledId; bodyId=rigid.bodyId(); worldRevision=world.mutationRevision();
        }
        public long settledId() { return settledId; }
        public long bodyId() { return bodyId; }
        public FragmentDescriptor descriptor() { return descriptor; }
        public List<World.CellChange> changes() { return changes; }
        public long geometryNanos() { return rigid.geometryNanos(); }
        public long occupancyNanos() { return rigid.occupancyNanos(); }
        public boolean owns(World candidate) { return world==candidate && rigid.owns(candidate) && removal.owns(candidate); }
        public boolean valid() { return settled.directReactivationAllowed() && world.mutationRevision()==worldRevision && rigid.valid() && removal.valid(); }
        public void publish(Object token) {
            if(!World.isReactivationPublicationToken(token)) throw new IllegalStateException("Not a reactivation publication");
            removal.publish(token); rigid.publish(token);
        }
    }
    private SettledRockReactivation() { }
    public static Result prepareReactivation(long id,World world,RockConnectivity topology,SettledRockRegistry settled,
            RigidBodySystem physics,RigidBodyOccupancyIndex occupancy,long generation) {
        return prepareReactivation(id,world,topology,settled,physics,occupancy,generation,false);
    }
    /** Optional timings are diagnostic only and never affect physics or transaction decisions. */
    public static Result prepareReactivation(long id,World world,RockConnectivity topology,SettledRockRegistry settled,
            RigidBodySystem physics,RigidBodyOccupancyIndex occupancy,long generation,boolean instrument) {
        Objects.requireNonNull(world); Objects.requireNonNull(topology); Objects.requireNonNull(settled);
        Objects.requireNonNull(physics); Objects.requireNonNull(occupancy);
        if(!settled.directReactivationAllowed()) return new Result(Status.PREPARATION_FAILED,null,"Reactivation requires the support coordinator");
        if(generation<0 || !settled.owns(world,topology) || !occupancy.owns(world) || !physics.coupledTo(world))
            return new Result(Status.INVALID_ROCK,null,"Mismatched owners");
        try {
            SettledRockRegistry.Rock rock;
            try { rock=settled.rock(id); } catch(IllegalArgumentException ex) { return new Result(Status.NOT_FOUND,null,ex.getMessage()); }
            if(rock.remainingCells()<=0 || rock.reactivatedBodyId()!=0) return new Result(Status.NOT_FOUND,null,"Rock is not live");
            if(rock.descriptor().cellCount()>2048) return new Result(Status.INVALID_ROCK,null,"Source descriptor limit exceeded");
            var cells=settled.cells(id);
            if(cells.size()!=rock.remainingCells() || cells.size()>2048) return new Result(Status.INVALID_ROCK,null,"Cell count mismatch or limit exceeded");
            int x=cells.stream().mapToInt(SettledRockRegistry.WorldCell::x).min().orElseThrow();
            int y=cells.stream().mapToInt(SettledRockRegistry.WorldCell::y).min().orElseThrow();
            var shape=new ArrayList<FragmentDescriptor.Cell>(cells.size()); var changes=new ArrayList<World.CellChange>(cells.size());
            for(var c:cells) {
                if(world.get(c.x(),c.y())!=Material.STONE || settled.ownerAt(c.x(),c.y())!=id)
                    return new Result(Status.INVALID_ROCK,null,"Cell ownership or material changed");
                shape.add(new FragmentDescriptor.Cell(c.x()-x,c.y()-y));
                changes.add(new World.CellChange(c.x(),c.y(),Material.STONE,Material.AIR));
            }
            FragmentDescriptor descriptor;
            try { descriptor=new FragmentDescriptor(rock.descriptor().substance(),shape); }
            catch(IllegalArgumentException ex) { return new Result(Status.INVALID_ROCK,null,"Owned cells are not connected"); }
            if(descriptor.massMilligrams()!=rock.massMilligrams()) return new Result(Status.INVALID_ROCK,null,"Mass mismatch");
            var rigid=physics.prepareReactivation(descriptor,x,y,occupancy,generation,instrument);
            var removal=settled.prepareReactivationRemoval(id,rigid.bodyId());
            var plan=new Prepared(world,rigid,removal,settled,changes,descriptor,id);
            return plan.valid()?new Result(Status.READY,plan,""):new Result(Status.PREPARATION_FAILED,null,"Preparation became stale");
        } catch(RigidBodyOccupancyIndex.BudgetException ex) {
            return new Result(Status.CAPACITY_OR_BUDGET,null,ex.getMessage());
        } catch(IllegalStateException ex) {
            String message=ex.getMessage();
            if(message!=null && message.contains("capacity")) return new Result(Status.CAPACITY_OR_BUDGET,null,message);
            return new Result(message!=null && (message.contains("overlap") || message.contains("conflict"))?Status.CONFLICT:Status.PREPARATION_FAILED,null,ex.toString());
        } catch(IllegalArgumentException | ArithmeticException ex) {
            return new Result(Status.PREPARATION_FAILED,null,ex.toString());
        }
    }
}
