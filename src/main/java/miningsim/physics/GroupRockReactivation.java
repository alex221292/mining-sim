package miningsim.physics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import miningsim.fragment.FragmentRegistry;
import miningsim.resource.FragmentDescriptor;
import miningsim.structural.RockConnectivity;
import miningsim.structural.SettledRockRegistry;
import miningsim.world.Material;
import miningsim.world.World;

/** Bounded owner-thread preparation of a group transfer, without simulation mutation. */
public final class GroupRockReactivation {
    public enum Status { READY, INVALID, CONFLICT, BUDGET, STALE }
    public record Limits(int maxBodies,int maxCells) {
        public Limits { if(maxBodies<1 || maxCells<1) throw new IllegalArgumentException("Invalid limits"); }
    }
    public record Result(Status status,Prepared plan,String reason) { }
    public static final class Prepared {
        private final World world;
        private final RigidBodySystem.PreparedGroupReactivation rigid;
        private final SettledRockRegistry.PreparedGroupReactivationRemoval ownership;
        private final SettledRockRegistry settled;
        private final Object supportPermit;
        private final List<World.CellChange> changes;
        private final List<SettledRockRegistry.ReactivationMapping> mappings;
        private final long worldRevision;
        private Prepared(World world,RigidBodySystem.PreparedGroupReactivation rigid,SettledRockRegistry.PreparedGroupReactivationRemoval ownership,SettledRockRegistry settled,Object supportPermit,
                List<World.CellChange> changes,List<SettledRockRegistry.ReactivationMapping> mappings) {
            this.world=world; this.rigid=rigid; this.ownership=ownership; this.settled=settled; this.supportPermit=supportPermit; this.changes=List.copyOf(changes);
            this.mappings=List.copyOf(mappings); worldRevision=world.mutationRevision();
        }
        public List<World.CellChange> changes() { return changes; }
        public List<SettledRockRegistry.ReactivationMapping> mappings() { return mappings; }
        public boolean owns(World candidate) { return world==candidate && rigid.owns(candidate) && ownership.owns(candidate); }
        public boolean valid() { return settled.supportTransitionsAllowed(supportPermit) && world.mutationRevision()==worldRevision && rigid.valid() && ownership.valid(); }
        public void publish(Object token) {
            if(!World.isReactivationPublicationToken(token)) throw new IllegalStateException("Not a reactivation publication");
            ownership.publish(token); rigid.publish(token);
        }
    }
    private GroupRockReactivation() { }
    public static Result prepareGroupReactivation(List<Long> bodyIds,World world,RockConnectivity topology,SettledRockRegistry settled,
            RigidBodySystem physics,RigidBodyOccupancyIndex occupancy,long generation,Limits limits) {
        return prepareGroupReactivation(bodyIds,world,topology,settled,physics,occupancy,generation,limits,null);
    }
    /** The optional opaque permit is held only by the attached support coordinator. */
    public static Result prepareGroupReactivation(List<Long> bodyIds,World world,RockConnectivity topology,SettledRockRegistry settled,
            RigidBodySystem physics,RigidBodyOccupancyIndex occupancy,long generation,Limits limits,Object supportPermit) {
        Objects.requireNonNull(bodyIds); Objects.requireNonNull(world); Objects.requireNonNull(topology); Objects.requireNonNull(settled);
        Objects.requireNonNull(physics); Objects.requireNonNull(occupancy); Objects.requireNonNull(limits);
        if(!settled.supportTransitionsAllowed(supportPermit)) return new Result(Status.STALE,null,"Support analysis pending");
        if(generation<0 || bodyIds.isEmpty() || bodyIds.size()>limits.maxBodies() || bodyIds.stream().anyMatch(Objects::isNull)
                || !settled.owns(world,topology) || !occupancy.owns(world) || !physics.coupledTo(world)) return new Result(Status.INVALID,null,"Invalid group or owners");
        try {
            var ids=bodyIds.stream().sorted().toList(); if(ids.stream().distinct().count()!=ids.size()) return new Result(Status.INVALID,null,"Duplicate settled ID");
            var changes=new ArrayList<World.CellChange>(); var inputs=new ArrayList<FragmentRegistry.GroupInput>(); int total=0;
            for(long id:ids) {
                var rock=settled.rock(id);
                if(rock.remainingCells()<1 || rock.reactivatedBodyId()!=0 || rock.descriptor().cellCount()>2048)
                    return new Result(Status.INVALID,null,"Rock is not live or exceeds cell limit");
                total=Math.addExact(total,rock.remainingCells()); if(total>limits.maxCells()) return new Result(Status.BUDGET,null,"Group cell limit exceeded");
                var cells=settled.cells(id); if(cells.size()!=rock.remainingCells()) return new Result(Status.INVALID,null,"Ownership count mismatch");
                int x=cells.stream().mapToInt(SettledRockRegistry.WorldCell::x).min().orElseThrow(),y=cells.stream().mapToInt(SettledRockRegistry.WorldCell::y).min().orElseThrow();
                var local=new ArrayList<FragmentDescriptor.Cell>(cells.size());
                for(var cell:cells) {
                    if(world.get(cell.x(),cell.y())!=Material.STONE || settled.ownerAt(cell.x(),cell.y())!=id) return new Result(Status.INVALID,null,"Invalid owned cell");
                    local.add(new FragmentDescriptor.Cell(cell.x()-x,cell.y()-y)); changes.add(new World.CellChange(cell.x(),cell.y(),Material.STONE,Material.AIR));
                }
                var descriptor=new FragmentDescriptor(rock.descriptor().substance(),local);
                if(descriptor.massMilligrams()!=rock.massMilligrams()) return new Result(Status.INVALID,null,"Mass mismatch");
                inputs.add(new FragmentRegistry.GroupInput(descriptor,x,y,0));
            }
            var rigid=physics.prepareGroupReactivation(inputs,occupancy,generation); var mappings=new ArrayList<SettledRockRegistry.ReactivationMapping>();
            var newIds=rigid.bodyIds(); for(int i=0;i<ids.size();i++) mappings.add(new SettledRockRegistry.ReactivationMapping(ids.get(i),newIds.get(i)));
            var ownership=settled.prepareGroupReactivationRemoval(mappings);
            changes.sort(Comparator.comparingInt(World.CellChange::y).thenComparingInt(World.CellChange::x));
            var plan=new Prepared(world,rigid,ownership,settled,supportPermit,changes,mappings);
            return plan.valid()?new Result(Status.READY,plan,""):new Result(Status.STALE,null,"Preparation became stale");
        } catch(RigidBodyOccupancyIndex.BudgetException ex) { return new Result(Status.BUDGET,null,ex.getMessage()); }
        catch(IllegalArgumentException | ArithmeticException ex) { return new Result(Status.INVALID,null,ex.toString()); }
        catch(IllegalStateException ex) { return new Result(Status.CONFLICT,null,ex.toString()); }
    }
}
