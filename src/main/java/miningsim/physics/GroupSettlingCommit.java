package miningsim.physics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import miningsim.resource.FragmentDescriptor;
import miningsim.structural.RockConnectivity;
import miningsim.structural.SettledRockRegistry;
import miningsim.world.Material;
import miningsim.world.World;

/** Explicit owner-thread group transfer. No automatic sandbox integration. */
public final class GroupSettlingCommit {
    public enum Status { READY, NOT_ELIGIBLE, NOT_SETTLEABLE, PREPARATION_FAILED }
    public record Result(Status status,Prepared prepared,String reason) { }
    public record Outcome(Status status,World.BatchResult publication,String reason) { }
    public record BodyDiagnostic(long bodyId,double coverage,double centerShiftCells) { }
    public static final class Prepared {
        private final World world;
        private final RigidBodySystem physics;
        private final CoupledSimulationStep coupled;
        private final RockConnectivity topology;
        private final SettledRockRegistry settled;
        private final GroupSettlingEligibility eligibility;
        private final List<Long> ids;
        private final RigidBodySystem.PreparedGroupSettlingRemoval removal;
        private final SettledRockRegistry.PreparedGroupInsertion insertion;
        private final List<World.CellChange> changes;
        private final List<BodyDiagnostic> diagnostics;
        private final long work;
        private final long revision;
        private Prepared(World world,RigidBodySystem physics,CoupledSimulationStep coupled,RockConnectivity topology,
                         SettledRockRegistry settled,GroupSettlingEligibility eligibility,List<Long> ids,
                         RigidBodySystem.PreparedGroupSettlingRemoval removal,SettledRockRegistry.PreparedGroupInsertion insertion,
                         List<World.CellChange> changes,List<BodyDiagnostic> diagnostics,long work) {
            this.world=world; this.physics=physics; this.coupled=coupled; this.topology=topology; this.settled=settled;
            this.eligibility=eligibility; this.ids=ids; this.removal=removal; this.insertion=insertion;
            this.changes=List.copyOf(changes); this.diagnostics=List.copyOf(diagnostics); this.work=work; revision=world.mutationRevision();
        }
        public List<World.CellChange> changes() { return changes; }
        public List<Long> bodyIds() { return ids; }
        public List<BodyDiagnostic> diagnostics() { return diagnostics; }
        public long workUnits() { return work; }
        public List<Long> settledIds() { return insertion.ids(); }
        public boolean owns(World candidate) { return world==candidate && removal.owns(candidate) && insertion.owns(candidate); }
        public boolean valid() {
            return settled.supportTransitionsAllowed() && revision==world.mutationRevision() && removal.valid() && insertion.valid()
                    && eligibility.valid(ids,physics,coupled,world,topology,settled);
        }
        public void publish(Object token) {
            if(!World.isSettlingPublicationToken(token)) throw new IllegalStateException("Not a settling publication");
            removal.publish(token); insertion.publish(token);
        }
    }
    private GroupSettlingCommit() { }
    /** Synchronous owner-thread convenience operation; explicit preparation remains available. */
    public static Outcome settleGroup(List<Long> bodyIds,RigidBodySystem physics,CoupledSimulationStep coupled,World world,
            RigidBodyOccupancyIndex occupancy,RockConnectivity topology,SettledRockRegistry settled,GroupSettlingEligibility eligibility,long budget) {
        var result=prepareGroupSettling(bodyIds,physics,coupled,world,occupancy,topology,settled,eligibility,budget);
        if(result.status()!=Status.READY) return new Outcome(result.status(),World.BatchResult.REJECTED,result.reason());
        var publication=world.commitGroupSettling(result.prepared());
        return new Outcome(result.status(),publication,publication==World.BatchResult.CHANGED?"":"Publication rejected");
    }
    private static void consume(long[] work,long amount,long budget) {
        if(amount<0 || amount>budget-work[0]) throw new IllegalStateException("Group work budget exceeded");
        work[0]+=amount;
    }
    private static boolean near(RigidBody a,RigidBody b,double tolerance,long[] work,long budget,boolean upward) {
        double ac=Math.cos(a.angleRadians()),as=Math.sin(a.angleRadians()),bc=Math.cos(b.angleRadians()),bs=Math.sin(b.angleRadians());
        double[] axes={ac,as,-as,ac,bc,bs,-bs,bc};
        for(var ca:a.fragment().descriptor().cells()) {
            var pa=a.geometry().transform(ca.x()+0.5,ca.y()+0.5,a.xCells(),a.yCells(),a.angleRadians());
            for(var cb:b.fragment().descriptor().cells()) {
                if(work[0]>=budget) throw new IllegalStateException("Group work budget exceeded"); work[0]++;
                var pb=b.geometry().transform(cb.x()+0.5,cb.y()+0.5,b.xCells(),b.yCells(),b.angleRadians());
                if(upward && pa.y()-pb.y()<=Math.abs(pa.x()-pb.x())) continue;
                boolean close=true;
                for(int k=0;k<8;k+=2) {
                    double x=axes[k],y=axes[k+1];
                    double radius=0.5*(Math.abs(x*ac+y*as)+Math.abs(-x*as+y*ac)+Math.abs(x*bc+y*bs)+Math.abs(-x*bs+y*bc));
                    if(Math.abs((pb.x()-pa.x())*x+(pb.y()-pa.y())*y)>radius+tolerance) { close=false; break; }
                }
                if(close) return true;
            }
        }
        return false;
    }
    public static Result prepareGroupSettling(List<Long> bodyIds,RigidBodySystem physics,CoupledSimulationStep coupled,World world,
            RigidBodyOccupancyIndex occupancy,RockConnectivity topology,SettledRockRegistry settled,GroupSettlingEligibility eligibility,long budget) {
        Objects.requireNonNull(bodyIds); Objects.requireNonNull(eligibility);
        try {
            if(budget<1 || budget>2_000_000 || bodyIds.stream().anyMatch(Objects::isNull)) return new Result(Status.NOT_SETTLEABLE,null,"Invalid group or budget");
            var ids=bodyIds.stream().sorted().toList();
            if(ids.isEmpty() || ids.size()>3 || ids.stream().distinct().count()!=ids.size() || !occupancy.owns(world)
                    || !eligibility.valid(ids,physics,coupled,world,topology,settled))
                return new Result(Status.NOT_ELIGIBLE,null,"Group lacks a current 120-tick quiet window");
            var bodies=ids.stream().map(physics::body).toList(); var selected=new HashSet<>(ids);
            boolean[] overlap={false}; long[] graphWork={0};
            occupancy.snapshot().forEachCell((x,y)->{
                consume(graphWork,1,budget);
                if(miningsim.simulation.CellularDisplacementPlanner.classify(world.get(x,y))!=miningsim.simulation.CellularDisplacementPlanner.Kind.DISPLACEABLE) return;
                for(long id:occupancy.snapshot().bodyIdsAt(x,y)) { consume(graphWork,1,budget); if(selected.contains(id)) overlap[0]=true; }
            });
            if(overlap[0]) return new Result(Status.NOT_SETTLEABLE,null,"Body overlaps world material");
            boolean[] foundation=new boolean[bodies.size()];
            for(int i=0;i<bodies.size();i++) {
                long left=budget-graphWork[0]; if(left<1) throw new IllegalStateException("Group work budget exceeded");
                var staticQuery=new StaticCollisionQuery(world,left,left,StaticCollisionQuery.MaterialPolicy.DISPLACE_GRANULAR);
                var result=staticQuery.queryHeldValidation(bodies.get(i),RigidBodySystem.MAX_HELD_PENETRATION_CELLS);
                consume(graphWork,result.candidates()+result.narrowPhaseChecks(),budget);
                for(var contact:result.contacts()) if(contact.boundary()==StaticCollisionQuery.Boundary.NONE && contact.normalY()>0.5
                        && world.get(contact.worldX(),contact.worldY())==Material.BEDROCK) foundation[i]=true;
            }
            long left=budget-graphWork[0]; if(left<1) throw new IllegalStateException("Group work budget exceeded");
            var collisions=new BodyCollisionQuery(new BodyCollisionQuery.Limits(left,left,4096,4096))
                    .queryHeldValidation(physics.bodies(),selected,RigidBodySystem.MAX_HELD_PENETRATION_CELLS);
            consume(graphWork,collisions.candidatePairs()+collisions.narrowChecks(),budget);
            boolean[][] edges=new boolean[bodies.size()][bodies.size()],supportEdges=new boolean[bodies.size()][bodies.size()];
            for(int i=0;i<bodies.size();i++) for(int j=i+1;j<bodies.size();j++)
                edges[i][j]=edges[j][i]=near(bodies.get(i),bodies.get(j),0.05,graphWork,budget,false);
            for(int i=0;i<bodies.size();i++) for(int j=0;j<bodies.size();j++)
                if(i!=j && edges[i][j] && bodies.get(i).yCells()>bodies.get(j).yCells())
                    supportEdges[i][j]=near(bodies.get(i),bodies.get(j),0.05,graphWork,budget,true);
            boolean[] sourceReached=new boolean[ids.size()]; sourceReached[0]=true; boolean grown;
            do { grown=false; for(int i=0;i<ids.size();i++) if(sourceReached[i]) for(int j=0;j<ids.size();j++)
                if(edges[i][j] && !sourceReached[j]) { sourceReached[j]=true; grown=true; } } while(grown);
            for(boolean r:sourceReached) if(!r) return new Result(Status.NOT_SETTLEABLE,null,"Source contact graph disconnected");
            boolean[] supported=foundation.clone();
            do { grown=false; for(int i=0;i<ids.size();i++) if(supported[i]) for(int j=0;j<ids.size();j++)
                if(supportEdges[i][j] && !supported[j]) { supported[j]=true; grown=true; } } while(grown);
            for(boolean r:supported) if(!r) return new Result(Status.NOT_SETTLEABLE,null,"Source lacks group support path to BEDROCK");
            if(graphWork[0]>=budget) return new Result(Status.NOT_SETTLEABLE,null,"Group work budget exceeded");
            var plan=GroupSettlingPlanner.plan(bodies,world,occupancy.snapshot(),settled,budget-graphWork[0],0.3,0.9);
            if(!plan.success()) return new Result(Status.NOT_SETTLEABLE,null,plan.reason());
            // The placed contact graph must be connected; independent BEDROCK-supported groups are separate operations.
            var owners=new java.util.HashMap<GroupSettlingPlanner.Cell,Integer>();
            for(int i=0;i<plan.bodies().size();i++) for(var c:plan.bodies().get(i).cells()) owners.put(c,i);
            boolean[] reached=new boolean[ids.size()]; reached[0]=true;
            boolean changed;
            do { changed=false; for(var e:owners.entrySet()) if(reached[e.getValue()]) {
                var c=e.getKey(); for(int[] d:new int[][]{{-1,0},{1,0},{0,-1},{0,1}}) {
                    var n=owners.get(new GroupSettlingPlanner.Cell(c.x()+d[0],c.y()+d[1]));
                    if(n!=null && !reached[n]) { reached[n]=true; changed=true; }
                }
            } } while(changed);
            for(boolean r:reached) if(!r) return new Result(Status.NOT_SETTLEABLE,null,"Group contact graph disconnected");
            var changes=new ArrayList<World.CellChange>(); var insertions=new ArrayList<SettledRockRegistry.Insertion>();
            for(var bodyPlan:plan.bodies()) {
                var body=physics.body(bodyPlan.id()); int x=bodyPlan.cells().stream().mapToInt(GroupSettlingPlanner.Cell::x).min().orElseThrow();
                int y=bodyPlan.cells().stream().mapToInt(GroupSettlingPlanner.Cell::y).min().orElseThrow();
                var cells=new ArrayList<FragmentDescriptor.Cell>();
                for(var c:bodyPlan.cells()) { cells.add(new FragmentDescriptor.Cell(c.x()-x,c.y()-y)); changes.add(new World.CellChange(c.x(),c.y(),Material.AIR,Material.STONE)); }
                var descriptor=new FragmentDescriptor(body.fragment().descriptor().substance(),cells);
                if(descriptor.massMilligrams()!=body.fragment().massMilligrams()) throw new IllegalStateException("Mass mismatch");
                insertions.add(new SettledRockRegistry.Insertion(body.id(),descriptor,x,y));
            }
            changes.sort(Comparator.comparingInt(World.CellChange::y).thenComparingInt(World.CellChange::x));
            var removal=physics.prepareGroupSettlingRemoval(selected,occupancy,coupled.diagnostics().ticks());
            var insertion=settled.prepareGroupInsertion(insertions);
            var diagnostics=plan.bodies().stream().map(p->new BodyDiagnostic(p.id(),p.coverage(),p.centerShift())).toList();
            var prepared=new Prepared(world,physics,coupled,topology,settled,eligibility,ids,removal,insertion,changes,diagnostics,graphWork[0]+plan.work());
            return prepared.valid()?new Result(Status.READY,prepared,""):new Result(Status.PREPARATION_FAILED,null,"State changed");
        } catch(IllegalStateException ex) {
            if("Group work budget exceeded".equals(ex.getMessage()) || ex.getMessage()!=null &&
                    (ex.getMessage().startsWith("Static collision") || ex.getMessage().startsWith("Body ")) && ex.getMessage().contains("limit exceeded"))
                return new Result(Status.NOT_SETTLEABLE,null,ex.getMessage());
            return new Result(Status.PREPARATION_FAILED,null,ex.toString());
        } catch(IllegalArgumentException | ArithmeticException ex) {
            return new Result(Status.PREPARATION_FAILED,null,ex.toString());
        }
    }
}
