package miningsim.physics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import miningsim.structural.RockConnectivity;
import miningsim.structural.SettledRockRegistry;
import miningsim.world.Material;
import miningsim.world.World;

/** Narrow single-owner preparation of one rigid-to-static transfer. */
public final class SettlingCommit {
    public enum Status { READY, NOT_ELIGIBLE, NOT_SETTLEABLE, BUDGET_EXCEEDED, PREPARATION_FAILED }
    public record Result(Status status,Prepared prepared,String reason) { }
    public static final class Prepared {
        private final World world; private final RigidBodySystem physics; private final RigidBody body;
        private final CoupledSimulationStep coupled; private final RockConnectivity topology; private final SettledRockRegistry settled;
        private final SettlingEligibility eligibility; private final RigidBodySystem.PreparedSettlingRemoval removal;
        private final SettledRockRegistry.PreparedInsertion insertion; private final List<World.CellChange> changes;
        private final List<SettledRockPlacementPlanner.Cell> cells;
        private final long worldRevision,terrainRevision;
        private Prepared(World world,RigidBodySystem physics,RigidBody body,CoupledSimulationStep coupled,RockConnectivity topology,
                         SettledRockRegistry settled,SettlingEligibility eligibility,RigidBodySystem.PreparedSettlingRemoval removal,
                         SettledRockRegistry.PreparedInsertion insertion,List<World.CellChange> changes,List<SettledRockPlacementPlanner.Cell> cells) {
            this.world=world; this.physics=physics; this.body=body; this.coupled=coupled; this.topology=topology; this.settled=settled;
            this.eligibility=eligibility; this.removal=removal; this.insertion=insertion; this.changes=List.copyOf(changes);
            this.cells=List.copyOf(cells);
            worldRevision=world.mutationRevision(); terrainRevision=world.rigidTerrainRevision();
        }
        public List<World.CellChange> changes() { return changes; }
        public long settledId() { return insertion.id(); }
        public boolean owns(World candidate) { return world==candidate && insertion.owns(candidate) && removal.owns(candidate); }
        public boolean valid() {
            return settled.supportTransitionsAllowed() && world.mutationRevision()==worldRevision && world.rigidTerrainRevision()==terrainRevision
                    && removal.valid() && insertion.valid() && eligibility.validFor(body,physics,coupled,world,topology,settled)
                    && eligibility.placementSupported(cells,world,topology,settled);
        }
        /** Only World may call this after complete preflight, without intervening callbacks. */
        public void publish(Object token) {
            if(!World.isSettlingPublicationToken(token)) throw new IllegalStateException("Not a settling publication");
            removal.publish(token); insertion.publish(token);
        }
    }
    private SettlingCommit() { }
    public static Result prepare(long bodyId,RigidBodySystem physics,CoupledSimulationStep coupled,World world,
                                 RigidBodyOccupancyIndex occupancy,RockConnectivity topology,SettledRockRegistry settled,
                                 SettlingEligibility eligibility,SettledRockPlacementPlanner.Limits limits) {
        Objects.requireNonNull(physics); Objects.requireNonNull(coupled); Objects.requireNonNull(world); Objects.requireNonNull(occupancy);
        Objects.requireNonNull(topology); Objects.requireNonNull(settled); Objects.requireNonNull(eligibility); Objects.requireNonNull(limits);
        var body=physics.body(bodyId);
        if(body==null || !occupancy.owns(world) || !eligibility.validFor(body,physics,coupled,world,topology,settled))
            return new Result(Status.NOT_ELIGIBLE,null,"Body lacks a current, supported quiet window");
        try {
            boolean[] materialOverlap={false};
            occupancy.snapshot().forEachCell((x,y)->{
                if(miningsim.simulation.CellularDisplacementPlanner.classify(world.get(x,y))!=miningsim.simulation.CellularDisplacementPlanner.Kind.DISPLACEABLE) return;
                for(long id:occupancy.snapshot().bodyIdsAt(x,y)) if(id==bodyId) { materialOverlap[0]=true; break; }
            });
            if(materialOverlap[0]) return new Result(Status.NOT_SETTLEABLE,null,"Source body overlaps cellular material");
            var query=new BodyCollisionQuery(new BodyCollisionQuery.Limits(1_000_000,10_000_000,4096,4096));
            query.queryHeldValidation(physics.bodies(),Set.of(bodyId),RigidBodySystem.MAX_HELD_PENETRATION_CELLS);
            var pose=new SettledRockPlacementPlanner.Pose(body.id(),body.fragment().descriptor(),body.xCells(),body.yCells(),body.angleRadians());
            var placement=SettledRockPlacementPlanner.prepare(pose,world,occupancy.snapshot(),settled,limits);
            if(placement.status()!=SettledRockPlacementPlanner.Status.READY)
                return new Result(placement.status()==SettledRockPlacementPlanner.Status.BUDGET_EXCEEDED?Status.BUDGET_EXCEEDED:Status.NOT_SETTLEABLE,null,placement.status().name());
            var plan=placement.plan();
            if(!eligibility.placementSupported(plan.cells(),world,topology,settled))
                return new Result(Status.NOT_SETTLEABLE,null,"Placed cells lack verified static support");
            if(plan.cells().size()!=body.fragment().descriptor().cellCount()
                    || plan.descriptor().massMilligrams()!=body.fragment().massMilligrams()
                    || !plan.descriptor().substance().equals(body.fragment().descriptor().substance()))
                return new Result(Status.PREPARATION_FAILED,null,"Placement does not preserve substance and mass");
            var removal=physics.prepareSettlingRemoval(bodyId,occupancy,coupled.diagnostics().ticks());
            var insertion=settled.prepareInsertion(bodyId,plan.descriptor(),plan.originX(),plan.originY());
            var changes=new ArrayList<World.CellChange>(plan.cells().size());
            for(var cell:plan.cells()) changes.add(new World.CellChange(cell.x(),cell.y(),Material.AIR,Material.STONE));
            var prepared=new Prepared(world,physics,body,coupled,topology,settled,eligibility,removal,insertion,changes,plan.cells());
            return prepared.valid()?new Result(Status.READY,prepared,""):new Result(Status.PREPARATION_FAILED,null,"Prepared state became stale");
        } catch(IllegalStateException | IllegalArgumentException | ArithmeticException ex) {
            return new Result(Status.PREPARATION_FAILED,null,ex.getClass().getSimpleName()+": "+ex.getMessage());
        }
    }
}
