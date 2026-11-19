package miningsim.physics;

import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import miningsim.fragment.FragmentRegistry;

/** Headless fixed-step motion, optionally with discrete static collisions. */
public final class RigidBodySystem {
    private static final double SETTLING_DISTURBANCE_LINEAR_MULTIPLIER=5.0;
    private static final double SETTLING_DISTURBANCE_ANGULAR_MULTIPLIER=10.0;
    enum Checkpoint { AFTER_SYNC, AFTER_WAKE, DURING_WAKE_PROPAGATION, AFTER_STATIC_PREPARE, AFTER_PAIR_QUERY,
                      AFTER_VELOCITY, AFTER_POSITION, BEFORE_SUPPORT, DURING_SUPPORT_EVALUATION, AFTER_PUBLICATION }
    private java.util.function.Consumer<Checkpoint> testCheckpoint;
    private java.util.function.Consumer<RigidBody> coupledTrace;
    void setTestCheckpoint(java.util.function.Consumer<Checkpoint> checkpoint) { testCheckpoint = checkpoint; }
    private void checkpoint(Checkpoint point) { if (testCheckpoint != null) testCheckpoint.accept(point); }
    void setCoupledTrace(java.util.function.Consumer<RigidBody> trace) { coupledTrace=trace; }
    private void trace(RigidBody body) { if(coupledTrace!=null) coupledTrace.accept(body); }
    public record MotionConfig(double maxTravelCells, int maxMicrostepsPerBody, int maxTotalMicrosteps) {
        public MotionConfig {
            if (!Double.isFinite(maxTravelCells) || maxTravelCells <= 0 || maxTravelCells > 0.25
                    || maxMicrostepsPerBody < 1 || maxTotalMicrosteps < 1)
                throw new IllegalArgumentException("Invalid bounded motion configuration");
        }
        public static MotionConfig defaults() { return new MotionConfig(0.25, 128, 4096); }
    }
    public static final class MotionBudgetException extends IllegalStateException {
        public MotionBudgetException(String message) { super(message); }
    }
    public record MotionDiagnostics(int baseSubsteps, int executedMicrosteps, int maxMicrostepsPerBody,
                                    double maxPredictedPointTravelCells, double maxCenterDisplacementCells,
                                    double maxAngularChangeRadians) { }
    public record InteractionConfig(long maxPairs, long maxChecks, long maxRawContacts, long maxReducedContacts,
                                    long maxWakeWork, int positionIterations) {
        public InteractionConfig {
            if (maxPairs < 1 || maxChecks < 1 || maxRawContacts < 1 || maxReducedContacts < 1 || maxWakeWork < 1
                    || positionIterations < 0 || positionIterations > 32) throw new IllegalArgumentException("Invalid interaction limits");
        }
        public static InteractionConfig defaults() { return new InteractionConfig(1_000_000, 10_000_000, 1_000_000, 1_000_000, 1_000_000, 3); }
    }
    public record SleepConfig(double linearSpeed, double angularSpeed, int ticks) {
        public static final double DEFAULT_LINEAR_SPEED_METRES_PER_SECOND = 0.02;
        public static final double DEFAULT_ANGULAR_SPEED_RADIANS_PER_SECOND = 0.02;
        public static final int DEFAULT_QUIET_TICKS = 60;
        public SleepConfig {
            if (!Double.isFinite(linearSpeed) || linearSpeed <= 0 || !Double.isFinite(angularSpeed) || angularSpeed <= 0 || ticks < 1)
                throw new IllegalArgumentException("Invalid sleep configuration");
        }
        public static SleepConfig defaults() { return new SleepConfig(DEFAULT_LINEAR_SPEED_METRES_PER_SECOND, DEFAULT_ANGULAR_SPEED_RADIANS_PER_SECOND, DEFAULT_QUIET_TICKS); }
    }
    public record InteractionDiagnostics(int sharedMicrosteps, long pairs, long overlappingPairs, long checks,
                                         long rawContacts, long reducedContacts, long solverIterations,
                                         int sleepingBodies, int wokenBodies, double maxPenetrationCells) { }
    private static final class InteractionWork {
        long pairs, overlapping, checks, raw, reduced, iterations, wakeWork;
        int woken;
        double penetration;
    }
    public static final double TICK_SECONDS = 1.0 / 60.0;
    public static final double GRAVITY_METRES_PER_SECOND_SQUARED = 9.81;
    /** Maximum discrete contact penetration accepted for any HELD participant, in cells. */
    static final double MAX_HELD_PENETRATION_CELLS = 0.1;
    private final FragmentRegistry registry;
    private final int substeps;
    private final StaticContactSolver solver;
    private final BodyCollisionQuery bodyQuery;
    private final BodyContactSolver bodySolver;
    private final InteractionConfig interactionConfig;
    private final SleepConfig sleepConfig;
    private InteractionDiagnostics interactionDiagnostics = new InteractionDiagnostics(0,0,0,0,0,0,0,0,0,0);
    private InteractionWork interactionWork;
    private final MotionConfig motionConfig;
    private MotionDiagnostics motionDiagnostics = new MotionDiagnostics(0, 0, 0, 0, 0, 0);
    private String lastFailure;
    private long observedWorldRevision = -1;
    private java.util.Set<Long> lastSuccessfulBodyIds = java.util.Set.of();
    private StaticContactSolver.Diagnostics diagnostics = new StaticContactSolver.Diagnostics(0, 0, 0, 0, 0, 0);
    private TreeMap<Long, RigidBody> bodies = new TreeMap<>();
    private long extractionCommitNanos;
    public record Profile(long staticQueryNanos,long staticSolverNanos,long bodyQueryNanos,long bodySolverNanos,
                          double maxPositionCorrectionCells,double maxStaticPenetrationCells) { }
    private boolean profiling;
    private long bodyQueryNanos,bodySolverNanos;
    private double maxPositionCorrection;
    private Profile profile=new Profile(0,0,0,0,0,0);
    public void enableProfiling(boolean enabled) { profiling=enabled; if(solver!=null) solver.profiling(enabled); }
    public Profile profile() { return profile; }

    /** Preallocated interactive membership, published only inside the World extraction batch. */
    public final class PreparedMembership {
        private final TreeMap<Long,RigidBody> expected,next;
        private PreparedMembership(TreeMap<Long,RigidBody> expected,TreeMap<Long,RigidBody> next) { this.expected=expected; this.next=next; }
        public boolean valid() { return bodies==expected; }
        public void publish() { if(!valid()) throw new IllegalStateException("Physics membership changed"); bodies=next; }
        private List<RigidBody> bodies() { return List.copyOf(next.values()); }
    }
    /** Prepared rigid membership and occupancy removal for a settling commit. */
    public final class PreparedSettlingRemoval {
        private final TreeMap<Long,RigidBody> expected,next;
        private final java.util.ArrayList<RigidBody.State> states=new java.util.ArrayList<>();
        private final FragmentRegistry.PreparedRemoval fragment;
        private final RigidBodyOccupancyIndex.Prepared occupancy;
        private PreparedSettlingRemoval(TreeMap<Long,RigidBody> expected,TreeMap<Long,RigidBody> next,
                                        FragmentRegistry.PreparedRemoval fragment,RigidBodyOccupancyIndex.Prepared occupancy) {
            this.expected=expected; this.next=next; this.fragment=fragment; this.occupancy=occupancy;
            for(var body:expected.values()) states.add(body.state());
        }
        public long bodyId() { return fragment.fragment().id(); }
        public miningsim.fragment.RockFragment fragment() { return fragment.fragment(); }
        public boolean owns(miningsim.world.World world) { return occupancy.owns(world) && coupledTo(world); }
        public boolean valid() {
            if(!bodies.equals(expected) || !fragment.valid() || !occupancy.valid()) return false;
            int i=0; for(var body:expected.values()) if(!body.state().equals(states.get(i++))) return false;
            return true;
        }
        public void publish(Object token) {
            if(!miningsim.world.World.isSettlingPublicationToken(token)) throw new IllegalStateException("Not a settling publication");
            fragment.publishSettling(token); bodies=next; occupancy.publish();
        }
    }
    public PreparedSettlingRemoval prepareSettlingRemoval(long bodyId,RigidBodyOccupancyIndex occupancy,long generation) {
        Objects.requireNonNull(occupancy);
        var body=bodies.get(bodyId); if(body==null) throw new IllegalArgumentException("Unknown body ID");
        var old=occupancy.snapshot(); var current=occupancy.prepare(this,old.diagnostics().generation()).snapshot();
        if(!current.sameMembership(old)) throw new IllegalStateException("Published occupancy does not match current bodies");
        var fragment=registry.prepareRemoval(bodyId);
        if(fragment.fragment()!=body.fragment()) throw new IllegalStateException("Fragment/body ownership mismatch");
        var expected=new TreeMap<>(bodies); var next=new TreeMap<>(bodies); next.remove(bodyId);
        var preparedOccupancy=occupancy.prepare(List.copyOf(next.values()),generation);
        return new PreparedSettlingRemoval(expected,next,fragment,preparedOccupancy);
    }

    /** Preallocated group membership, fragment removal and occupancy publication. */
    public final class PreparedGroupSettlingRemoval {
        private final TreeMap<Long,RigidBody> expected,next;
        private final java.util.List<RigidBody.State> states;
        private final FragmentRegistry.PreparedGroupRemoval fragments;
        private final RigidBodyOccupancyIndex.Prepared occupancy;
        private PreparedGroupSettlingRemoval(TreeMap<Long,RigidBody> expected,TreeMap<Long,RigidBody> next,
                FragmentRegistry.PreparedGroupRemoval fragments,RigidBodyOccupancyIndex.Prepared occupancy) {
            this.expected=expected; this.next=next; this.fragments=fragments; this.occupancy=occupancy;
            states=expected.values().stream().map(RigidBody::state).toList();
        }
        public boolean owns(miningsim.world.World world) { return occupancy.owns(world) && coupledTo(world); }
        public boolean valid() {
            if(!bodies.equals(expected) || !fragments.valid() || !occupancy.valid()) return false;
            int i=0; for(var body:expected.values()) if(!body.state().equals(states.get(i++))) return false;
            return true;
        }
        public void publish(Object token) {
            if(!miningsim.world.World.isSettlingPublicationToken(token)) throw new IllegalStateException("Not a settling publication");
            fragments.publishSettling(token); bodies=next; occupancy.publish();
        }
    }
    public PreparedGroupSettlingRemoval prepareGroupSettlingRemoval(java.util.Set<Long> ids,RigidBodyOccupancyIndex occupancy,long generation) {
        Objects.requireNonNull(ids); Objects.requireNonNull(occupancy);
        if(ids.isEmpty()) throw new IllegalArgumentException("Empty group");
        var old=occupancy.snapshot(); var current=occupancy.prepare(this,old.diagnostics().generation()).snapshot();
        if(!current.sameMembership(old)) throw new IllegalStateException("Published occupancy does not match current bodies");
        var fragments=registry.prepareGroupRemoval(ids);
        var expected=new TreeMap<>(bodies); var next=new TreeMap<>(bodies);
        for(var fragment:fragments.fragments()) {
            var body=next.remove(fragment.id());
            if(body==null || body.fragment()!=fragment) throw new IllegalStateException("Fragment/body ownership mismatch");
        }
        return new PreparedGroupSettlingRemoval(expected,next,fragments,occupancy.prepare(List.copyOf(next.values()),generation));
    }

    /** Private-to-package checkpoint for the single-owner coupled commit. */
    final class CoupledCheckpoint {
        private final TreeMap<Long,RigidBody> savedBodies = new TreeMap<>(bodies);
        private final java.util.ArrayList<RigidBody.State> states = new java.util.ArrayList<>(savedBodies.size());
        private final StaticContactSolver.Diagnostics savedDiagnostics = diagnostics;
        private final MotionDiagnostics savedMotion = motionDiagnostics;
        private final InteractionDiagnostics savedInteraction = interactionDiagnostics;
        private final Profile savedProfile = profile;
        private final String savedFailure = lastFailure;
        private final long savedRevision = observedWorldRevision;
        private final java.util.Set<Long> savedIds = lastSuccessfulBodyIds;
        private final StaticContactSolver.CounterState savedCounters = solver == null ? null : solver.counterState();
        private CoupledCheckpoint() { for(var b:savedBodies.values()) states.add(b.state()); }
        void restore() {
            bodies.clear(); bodies.putAll(savedBodies); int i=0;
            for(var b:savedBodies.values()) b.restore(states.get(i++));
            diagnostics=savedDiagnostics; motionDiagnostics=savedMotion; interactionDiagnostics=savedInteraction; profile=savedProfile;
            lastFailure=savedFailure; observedWorldRevision=savedRevision; lastSuccessfulBodyIds=savedIds;
            if(solver!=null) solver.restoreCounters(savedCounters);
            interactionWork=null;
        }
    }
    CoupledCheckpoint coupledCheckpoint() { return new CoupledCheckpoint(); }
    boolean coupledTo(miningsim.world.World world) { return solver != null && solver.coupledTo(world); }

    /** Single-owner interactive extraction: prepare body and occupancy before World ownership transfer. */
    public miningsim.fragment.ExtractionCommit.Result extract(miningsim.fragment.ExtractionPlanner.Plan plan,
                                                               RigidBodyOccupancyIndex occupancy,long generation) {
        return extract(plan,occupancy,generation,false);
    }
    public long extractionCommitNanos() { return extractionCommitNanos; }
    public miningsim.fragment.ExtractionCommit.Result extract(miningsim.fragment.ExtractionPlanner.Plan plan,
                                                               RigidBodyOccupancyIndex occupancy,long generation,boolean instrument) {
        extractionCommitNanos=0;
        Objects.requireNonNull(plan); Objects.requireNonNull(occupancy);
        if(!coupledTo(plan.sourceWorld()) || !occupancy.owns(plan.sourceWorld())) throw new IllegalArgumentException("Extraction world mismatch");
        if(registry.size()>=registry.capacity()) return new miningsim.fragment.ExtractionCommit.Result(miningsim.fragment.ExtractionCommit.Status.CAPACITY,null);
        FragmentRegistry.Prepared prepared;
        try { prepared=registry.prepare(plan.descriptor(),plan.originX(),plan.originY(),plan.componentId()); }
        catch(ArithmeticException ex) { return new miningsim.fragment.ExtractionCommit.Result(miningsim.fragment.ExtractionCommit.Status.OVERFLOW,null); }
        var next=new TreeMap<Long,RigidBody>();
        for(var fragment:registry.snapshot()) next.put(fragment.id(),bodies.containsKey(fragment.id())?bodies.get(fragment.id()):new RigidBody(fragment));
        var body=new RigidBody(prepared.fragment());
        if(next.putIfAbsent(body.id(),body)!=null) throw new IllegalStateException("Duplicate extraction body ID");
        var membership=new PreparedMembership(bodies,next);
        var snapshot=occupancy.prepare(membership.bodies(),generation);
        long start=instrument?System.nanoTime():0;
        var result=miningsim.fragment.ExtractionCommit.commit(plan,registry,prepared,snapshot,membership);
        if(instrument) extractionCommitNanos=System.nanoTime()-start;
        return result;
    }

    public RigidBodySystem(FragmentRegistry registry) { this(registry, 4); }
    public RigidBodySystem(FragmentRegistry registry, int substeps) {
        this(registry, substeps, null);
    }
    public RigidBodySystem(FragmentRegistry registry, int substeps, StaticContactSolver solver) {
        this(registry, substeps, solver, MotionConfig.defaults());
    }
    public RigidBodySystem(FragmentRegistry registry, int substeps, StaticContactSolver solver, MotionConfig motionConfig) {
        this(registry, substeps, solver, motionConfig, null, null);
    }
    public RigidBodySystem(FragmentRegistry registry, int substeps, StaticContactSolver solver, MotionConfig motionConfig,
                           BodyCollisionQuery bodyQuery, BodyContactSolver bodySolver) {
        this(registry, substeps, solver, motionConfig, bodyQuery, bodySolver, InteractionConfig.defaults());
    }
    public RigidBodySystem(FragmentRegistry registry, int substeps, StaticContactSolver solver, MotionConfig motionConfig,
                           BodyCollisionQuery bodyQuery, BodyContactSolver bodySolver, InteractionConfig interactionConfig) {
        this(registry, substeps, solver, motionConfig, bodyQuery, bodySolver, interactionConfig, SleepConfig.defaults());
    }
    public RigidBodySystem(FragmentRegistry registry, int substeps, StaticContactSolver solver, MotionConfig motionConfig,
                           BodyCollisionQuery bodyQuery, BodyContactSolver bodySolver, InteractionConfig interactionConfig, SleepConfig sleepConfig) {
        this.registry = Objects.requireNonNull(registry);
        if (substeps < 1 || substeps > 1024) throw new IllegalArgumentException("Invalid substep count");
        this.substeps = substeps;
        this.solver = solver;
        this.motionConfig = Objects.requireNonNull(motionConfig);
        if ((bodyQuery == null) != (bodySolver == null)) throw new IllegalArgumentException("Body query and solver must be configured together");
        this.bodyQuery = bodyQuery; this.bodySolver = bodySolver;
        this.interactionConfig = Objects.requireNonNull(interactionConfig);
        this.sleepConfig = Objects.requireNonNull(sleepConfig);
    }

    /** Call at the simulation boundary; IDs belong to this system's fixed registry. */
    public void synchronize() {
        var fragments = registry.snapshot();
        var ids = new java.util.HashSet<Long>();
        for (var fragment : fragments) {
            ids.add(fragment.id());
            bodies.computeIfAbsent(fragment.id(), id -> new RigidBody(fragment));
        }
        bodies.keySet().removeIf(id -> !ids.contains(id));
    }

    public List<RigidBody> bodies() { return List.copyOf(bodies.values()); }
    public RigidBody body(long id) { return bodies.get(id); }
    public StaticContactSolver.Diagnostics diagnostics() { return diagnostics; }
    public MotionDiagnostics motionDiagnostics() { return motionDiagnostics; }
    public String lastFailure() { return lastFailure; }
    public InteractionDiagnostics interactionDiagnostics() { return interactionDiagnostics; }

    /** Conservative for the semi-implicit position update, which uses the end-of-interval velocity. */
    private static double travelBound(RigidBody body, double dt) {
        double linear = (Math.hypot(body.velocityXMetresPerSecond(), body.velocityYMetresPerSecond())
                + GRAVITY_METRES_PER_SECOND_SQUARED * dt) * dt / FragmentGeometry.METRES_PER_CELL;
        double angular = body.geometry().maxRadiusCells() * Math.abs(body.angularVelocityRadiansPerSecond()) * dt;
        double bound = linear + angular;
        if (!Double.isFinite(bound) || bound < 0) throw new IllegalStateException("Non-finite motion bound for body " + body.id());
        return bound;
    }

    /** Explicit tick, independent of wall time and rendering. */
    public void step() { step(java.util.Set.of()); }
    /** Single trial only; the caller owns the immutable constraint set and any outer transaction. */
    void step(java.util.Set<Long> heldIds) {
        var held=java.util.Set.copyOf(heldIds);
        var original = new TreeMap<>(bodies);
        var states = new java.util.ArrayList<RigidBody.State>(original.size());
        for (var body : original.values()) states.add(body.state());
        var oldDiagnostics = diagnostics;
        var oldMotionDiagnostics = motionDiagnostics;
        var oldInteractionDiagnostics = interactionDiagnostics;
        long oldWorldRevision = observedWorldRevision;
        var oldSuccessfulBodyIds = lastSuccessfulBodyIds;
        var oldSolverCounters = solver == null ? null : solver.counterState();
        var oldProfile=profile;
        try {
            bodyQueryNanos=bodySolverNanos=0; maxPositionCorrection=0;
            synchronize();
            if(!held.isEmpty()) {
                if(bodyQuery==null || solver==null) throw new IllegalStateException("HELD requires shared coupled solver");
                if(!bodies.keySet().containsAll(held)) throw new IllegalArgumentException("Unknown HELD body ID");
                for(var body:bodies.values()) if(held.contains(body.id())) {
                    body.wake(); body.setVelocity(0,0,0);
                    solver.validateHeld(body,MAX_HELD_PENETRATION_CELLS);
                }
            }
            checkpoint(Checkpoint.AFTER_SYNC);
            if(coupledTrace!=null) for(var body:bodies.values()) trace(body);
            if (solver != null) solver.resetDiagnostics();
            if (bodyQuery != null) {
                interactionWork = new InteractionWork();
                if (!bodies.keySet().containsAll(lastSuccessfulBodyIds) || solver != null && observedWorldRevision >= 0 && observedWorldRevision != solver.worldRevision()) wakeAll();
                checkpoint(Checkpoint.AFTER_WAKE);
                sharedStep(held);
                profile=new Profile(solver==null?0:solver.queryNanos(),solver==null?0:solver.solverNanos(),bodyQueryNanos,bodySolverNanos,maxPositionCorrection,solver==null?0:solver.maxPenetration());
                if (solver != null) observedWorldRevision = solver.worldRevision();
                lastSuccessfulBodyIds = java.util.Set.copyOf(bodies.keySet());
                checkpoint(Checkpoint.AFTER_PUBLICATION);
                lastFailure = null;
                return;
            }
            double dt = TICK_SECONDS / substeps, maxDisplacement = 0, maxAngle = 0, maxPointTravel = 0;
            int executed = 0, maxPerBody = 0;
            var perBody = new TreeMap<Long, Integer>();
            for (int i = 0; i < substeps; i++) for (var body : bodies.values()) {
                double remaining = dt;
                do {
                    int used = perBody.getOrDefault(body.id(), 0);
                    double required = solver == null ? 1 : Math.max(1, Math.ceil(travelBound(body, remaining) / motionConfig.maxTravelCells()));
                    if (solver != null && (required > motionConfig.maxMicrostepsPerBody() - used
                            || required > motionConfig.maxTotalMicrosteps() - executed)) {
                        throw new MotionBudgetException("Body " + body.id() + " schedule requests " + required
                                + " scheduled microsteps for remaining substep; body remaining "
                                + (motionConfig.maxMicrostepsPerBody() - used) + ", total remaining " + (motionConfig.maxTotalMicrosteps() - executed));
                    }
                    double interval = remaining / required;
                    double predicted = solver == null ? 0 : travelBound(body, interval);
                    if (solver != null && predicted > motionConfig.maxTravelCells() * (1 + 1e-12))
                        throw new IllegalStateException("Motion bound exceeded for body " + body.id());
                    double x = body.xCells(), y = body.yCells(), angle = body.angleRadians();
                    body.integrate(interval, GRAVITY_METRES_PER_SECOND_SQUARED);
                    trace(body);
                    maxDisplacement = Math.max(maxDisplacement, Math.hypot(body.xCells() - x, body.yCells() - y));
                    maxAngle = Math.max(maxAngle, Math.abs(body.angleRadians() - angle));
                    if (solver != null) solver.solve(body);
                    trace(body);
                    maxPointTravel = Math.max(maxPointTravel, predicted);
                    executed = Math.incrementExact(executed); used = Math.incrementExact(used); perBody.put(body.id(), used); maxPerBody = Math.max(maxPerBody, used);
                    remaining = required == 1 ? 0 : remaining - interval;
                } while (remaining > 0);
            }
            if (solver != null) diagnostics = solver.diagnostics(maxDisplacement, maxAngle);
            motionDiagnostics = new MotionDiagnostics(substeps, executed, maxPerBody, maxPointTravel, maxDisplacement, maxAngle);
            lastFailure = null;
        } catch (RuntimeException ex) {
            bodies.clear(); bodies.putAll(original);
            int index = 0;
            for (var body : original.values()) body.restore(states.get(index++));
            diagnostics = oldDiagnostics;
            motionDiagnostics = oldMotionDiagnostics;
            interactionDiagnostics = oldInteractionDiagnostics;
            profile=oldProfile;
            observedWorldRevision = oldWorldRevision;
            lastSuccessfulBodyIds = oldSuccessfulBodyIds;
            if (solver != null) solver.restoreCounters(oldSolverCounters);
            lastFailure = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            throw ex;
        } finally {
            interactionWork = null;
        }
    }

    /** All bodies are integrated to the same time before pair detection. */
    private void sharedStep(java.util.Set<Long> held) {
        // A fully sleeping set has no integration or constraint work. World edits and
        // removed bodies have already woken it; a newly inserted body starts awake.
        // Still perform the bounded support/contact observation once per fixed tick.
        if (held.isEmpty() && !bodies.isEmpty() && bodies.values().stream().allMatch(RigidBody::sleeping)) {
            checkpoint(Checkpoint.BEFORE_SUPPORT);
            if (solver != null) for (var body : bodies.values()) {
                solver.supported(body);
                checkpoint(Checkpoint.DURING_SUPPORT_EVALUATION);
            }
            queryBodies();
            interactionDiagnostics = new InteractionDiagnostics(0,interactionWork.pairs,interactionWork.overlapping,interactionWork.checks,
                    interactionWork.raw,interactionWork.reduced,interactionWork.iterations,bodies.size(),interactionWork.woken,interactionWork.penetration);
            if (solver != null) diagnostics=solver.diagnostics(0,0);
            motionDiagnostics=new MotionDiagnostics(substeps,0,0,0,0,0);
            return;
        }
        if(!held.isEmpty()) queryBodies(held);
        double dt = TICK_SECONDS / substeps, maxTravel = 0, maxDisplacement = 0, maxAngle = 0;
        var initialStates=new TreeMap<Long,RigidBody.State>();
        for(var body:bodies.values()) initialStates.put(body.id(),body.state());
        // Per-tick transient only: a large impact must reset the whole island's
        // quiet window even if the velocity solver removes it before tick end.
        var disturbed=new java.util.TreeSet<Long>();
        for(var body:bodies.values()) noteSettlingDisturbance(body,disturbed);
        int shared = 0, work = 0, maxPerBody = 0;
        var perBody = new TreeMap<Long, Integer>();
        for (int sub = 0; sub < substeps; sub++) {
            double remaining = dt;
            do {
                // Wake potentially intersecting sleep islands before advancing either participant.
                wakeNearby(remaining,held);
                double required = 1;
                int awake = 0;
                for (var body : bodies.values()) if (!body.sleeping() && !held.contains(body.id())) { awake++; required = Math.max(required, Math.ceil(travelBound(body, remaining) / motionConfig.maxTravelCells())); }
                if (required > motionConfig.maxMicrostepsPerBody() - shared
                        || required * awake > motionConfig.maxTotalMicrosteps() - work)
                    throw new MotionBudgetException("Shared schedule requests " + required + " microsteps with " + bodies.size() + " bodies; remaining body="
                            + (motionConfig.maxMicrostepsPerBody() - shared) + ", total=" + (motionConfig.maxTotalMicrosteps() - work));
                double interval = remaining / required;
                for (var body : bodies.values()) if (!body.sleeping() && !held.contains(body.id())) {
                    int count = perBody.merge(body.id(), 1, Integer::sum);
                    maxPerBody = Math.max(maxPerBody, count);
                    maxTravel = Math.max(maxTravel, travelBound(body, interval));
                    double x = body.xCells(), y = body.yCells(), angle = body.angleRadians();
                    body.integrate(interval, GRAVITY_METRES_PER_SECOND_SQUARED);
                    noteSettlingDisturbance(body,disturbed);
                    trace(body);
                    maxDisplacement = Math.max(maxDisplacement, Math.hypot(body.xCells() - x, body.yCells() - y));
                    maxAngle = Math.max(maxAngle, Math.abs(body.angleRadians() - angle));
                }
                var staticConstraints = new java.util.ArrayList<StaticContactSolver.Prepared>();
                if (solver != null) for (var body : bodies.values()) if (!body.sleeping() && !held.contains(body.id())) staticConstraints.add(solver.prepare(body));
                checkpoint(Checkpoint.AFTER_STATIC_PREPARE);
                var dynamicContacts = queryBodies();
                for(var contact:dynamicContacts) noteRelativeSettlingDisturbance(contact,disturbed);
                checkpoint(Checkpoint.AFTER_PAIR_QUERY);
                if (dynamicContacts.stream().anyMatch(c -> !held.contains(c.bodyA()) && !held.contains(c.bodyB()) && bodies.get(c.bodyA()).sleeping() != bodies.get(c.bodyB()).sleeping()))
                    throw new IllegalStateException("Sleeping body reached without pre-integration wake");
                var activeContacts = dynamicContacts.stream().filter(c -> (!held.contains(c.bodyA()) && !bodies.get(c.bodyA()).sleeping()) || (!held.contains(c.bodyB()) && !bodies.get(c.bodyB()).sleeping())).toList();
                var dynamicConstraints = bodySolver.prepare(activeContacts, bodies,held);
                int iterations = Math.max(bodySolver.iterations(), solver == null ? 0 : solver.velocityIterations());
                for (int pass = 0; pass < iterations; pass++) {
                    if (solver != null && pass < solver.velocityIterations()) for (var constraint : staticConstraints) constraint.iterate();
                    if (pass < bodySolver.iterations()) {
                        long start=profiling?System.nanoTime():0;
                        dynamicConstraints.iterate();
                        if(profiling) bodySolverNanos+=System.nanoTime()-start;
                    }
                    if (pass < bodySolver.iterations() && !activeContacts.isEmpty()) interactionWork.iterations = Math.incrementExact(interactionWork.iterations);
                }
                for(var body:bodies.values()) noteSettlingDisturbance(body,disturbed);
                checkpoint(Checkpoint.AFTER_VELOCITY);
                for (int pass = 0; pass < interactionConfig.positionIterations(); pass++) {
                    if (solver != null && pass < solver.positionIterations()) for (var body : bodies.values()) if (!body.sleeping() && !held.contains(body.id())) { maxPositionCorrection=Math.max(maxPositionCorrection,solver.correctPosition(body)); trace(body); }
                    var contacts = queryBodies();
                    for (var contact : contacts) {
                        var a = bodies.get(contact.bodyA()); var b = bodies.get(contact.bodyB());
                        boolean ha=held.contains(a.id()),hb=held.contains(b.id());
                        if ((ha || a.sleeping()) && (hb || b.sleeping())) continue;
                        if ((!ha && a.sleeping()) || (!hb && b.sleeping())) throw new IllegalStateException("Position correction reached a sleeping body after integration");
                        double invA = ha?0:1 / a.geometry().massKg(), invB = hb?0:1 / b.geometry().massKg();
                        double correction = Math.min(0.2, Math.max(0, contact.penetrationCells() - 0.01) * 0.2);
                        maxPositionCorrection=Math.max(maxPositionCorrection,correction);
                        if(!ha) a.translate(-contact.normalX() * correction * invA / (invA + invB), -contact.normalY() * correction * invA / (invA + invB));
                        if(!hb) b.translate(contact.normalX() * correction * invB / (invA + invB), contact.normalY() * correction * invB / (invA + invB));
                        trace(a); trace(b);
                    }
                }
                if(!held.isEmpty()) queryBodies(held);
                checkpoint(Checkpoint.AFTER_POSITION);
                if(coupledTrace!=null) for(var body:bodies.values()) trace(body);
                shared = Math.incrementExact(shared); work = Math.addExact(work, awake); remaining = required == 1 ? 0 : remaining - interval;
            } while (remaining > 0);
        }
        checkpoint(Checkpoint.BEFORE_SUPPORT);
        var supported = new java.util.HashSet<Long>();
        supported.addAll(held);
        if (solver != null) for (var body : bodies.values()) {
            if (solver.supported(body)) supported.add(body.id());
            checkpoint(Checkpoint.DURING_SUPPORT_EVALUATION);
        }
        var contacts = queryBodies();
        for (int pass = 0; pass < bodies.size(); pass++) for (var c : contacts) {
            interactionWork.wakeWork = Math.incrementExact(interactionWork.wakeWork);
            if (interactionWork.wakeWork > interactionConfig.maxWakeWork()) throw new MotionBudgetException("Support propagation limit exceeded: " + interactionWork.wakeWork);
            if (c.normalY() > 0.5 && supported.contains(c.bodyB())) supported.add(c.bodyA());
            if (c.normalY() < -0.5 && supported.contains(c.bodyA())) supported.add(c.bodyB());
        }
        updateIslandSleep(contacts,supported,initialStates,disturbed,held);
        int sleeping = 0; for (var body : bodies.values()) if (body.sleeping()) sleeping++;
        interactionDiagnostics = new InteractionDiagnostics(shared,interactionWork.pairs,interactionWork.overlapping,interactionWork.checks,
                interactionWork.raw,interactionWork.reduced,interactionWork.iterations,sleeping,interactionWork.woken,interactionWork.penetration);
        if (solver != null) diagnostics = solver.diagnostics(maxDisplacement, maxAngle);
        motionDiagnostics = new MotionDiagnostics(substeps, work, maxPerBody, maxTravel, maxDisplacement, maxAngle);
    }
    /** Contact-connected bodies may sleep only together after the whole supported island is quiet. */
    private void updateIslandSleep(List<BodyCollisionQuery.Contact> contacts,java.util.Set<Long> supported,TreeMap<Long,RigidBody.State> initialStates,java.util.Set<Long> disturbed,java.util.Set<Long> held) {
        var ordered=bodies(); int n=ordered.size(); int[] parent=new int[n];
        var indices=new TreeMap<Long,Integer>();
        for(int i=0;i<n;i++) { parent[i]=i; indices.put(ordered.get(i).id(),i); }
        for(var c:contacts) {
            consumeIslandWork();
            if(held.contains(c.bodyA()) || held.contains(c.bodyB())) continue;
            int a=islandRoot(parent,indices.get(c.bodyA())),b=islandRoot(parent,indices.get(c.bodyB()));
            if(a!=b) parent[Math.max(a,b)]=Math.min(a,b);
        }
        boolean[] eligible=new boolean[n]; java.util.Arrays.fill(eligible,true);
        int[] minimum=new int[n]; java.util.Arrays.fill(minimum,Integer.MAX_VALUE);
        for(int i=0;i<n;i++) {
            consumeIslandWork();
            int root=islandRoot(parent,i); var body=ordered.get(i);
            var initial=initialStates.get(body.id());
            double displacement=Math.hypot(body.xCells()-initial.x(),body.yCells()-initial.y());
            double rotation=Math.abs(body.angleRadians()-initial.angle());
            eligible[root]&=!disturbed.contains(body.id()) && body.sleepEligible(supported.contains(body.id()),sleepConfig)
                    && displacement<sleepConfig.linearSpeed()*TICK_SECONDS/FragmentGeometry.METRES_PER_CELL
                    && rotation<sleepConfig.angularSpeed()*TICK_SECONDS;
            minimum[root]=Math.min(minimum[root],body.sleepTicks());
        }
        for(int i=0;i<n;i++) { int root=islandRoot(parent,i); if(!held.contains(ordered.get(i).id())) ordered.get(i).updateIslandSleep(eligible[root],minimum[root],sleepConfig); }
    }
    private void noteSettlingDisturbance(RigidBody body,java.util.Set<Long> disturbed) {
        // Five times the configured quiet linear speed or ten times the quiet
        // angular speed is a significant transient, not a sleep tolerance.
        if(Math.hypot(body.velocityXMetresPerSecond(),body.velocityYMetresPerSecond())>=SETTLING_DISTURBANCE_LINEAR_MULTIPLIER*sleepConfig.linearSpeed()
                || Math.abs(body.angularVelocityRadiansPerSecond())>=SETTLING_DISTURBANCE_ANGULAR_MULTIPLIER*sleepConfig.angularSpeed()) disturbed.add(body.id());
    }
    private void noteRelativeSettlingDisturbance(BodyCollisionQuery.Contact contact,java.util.Set<Long> disturbed) {
        var a=bodies.get(contact.bodyA()); var b=bodies.get(contact.bodyB());
        double arx=(contact.pointX()-a.xCells())*FragmentGeometry.METRES_PER_CELL;
        double ary=(contact.pointY()-a.yCells())*FragmentGeometry.METRES_PER_CELL;
        double brx=(contact.pointX()-b.xCells())*FragmentGeometry.METRES_PER_CELL;
        double bry=(contact.pointY()-b.yCells())*FragmentGeometry.METRES_PER_CELL;
        double vx=b.velocityXMetresPerSecond()-b.angularVelocityRadiansPerSecond()*bry
                -a.velocityXMetresPerSecond()+a.angularVelocityRadiansPerSecond()*ary;
        double vy=b.velocityYMetresPerSecond()+b.angularVelocityRadiansPerSecond()*brx
                -a.velocityYMetresPerSecond()-a.angularVelocityRadiansPerSecond()*arx;
        if(Math.hypot(vx,vy)>=SETTLING_DISTURBANCE_LINEAR_MULTIPLIER*sleepConfig.linearSpeed()
                || Math.abs(a.angularVelocityRadiansPerSecond()-b.angularVelocityRadiansPerSecond())>=SETTLING_DISTURBANCE_ANGULAR_MULTIPLIER*sleepConfig.angularSpeed()) {
            disturbed.add(a.id()); disturbed.add(b.id());
        }
    }
    private void consumeIslandWork() {
        interactionWork.wakeWork=Math.incrementExact(interactionWork.wakeWork);
        if(interactionWork.wakeWork>interactionConfig.maxWakeWork()) throw new MotionBudgetException("Sleep island work limit exceeded: "+interactionWork.wakeWork);
    }
    private static int islandRoot(int[] parent,int i) {
        int root=i; while(parent[root]!=root) root=parent[root];
        while(parent[i]!=i) { int next=parent[i]; parent[i]=root; i=next; }
        return root;
    }
    private void wakeAll() {
        for (var body : bodies.values()) {
            interactionWork.wakeWork = Math.incrementExact(interactionWork.wakeWork);
            if (interactionWork.wakeWork > interactionConfig.maxWakeWork()) throw new MotionBudgetException("Wake limit exceeded: " + interactionWork.wakeWork);
            if (body.sleeping()) { body.wake(); interactionWork.woken++; }
        }
    }
    private void wakeNearby(double remaining,java.util.Set<Long> held) {
        var queue = new java.util.ArrayDeque<RigidBody>();
        for (var body : bodies.values()) if (!body.sleeping() && !held.contains(body.id())) queue.add(body);
        while (!queue.isEmpty()) {
            var source = queue.removeFirst();
            var a = source.geometry().bounds(source.xCells(),source.yCells(),source.angleRadians());
            double margin = travelBound(source, remaining) + 0.1;
            for (var other : bodies.values()) {
                interactionWork.wakeWork = Math.incrementExact(interactionWork.wakeWork);
                if (interactionWork.wakeWork > interactionConfig.maxWakeWork()) throw new MotionBudgetException("Wake propagation limit exceeded: " + interactionWork.wakeWork);
                checkpoint(Checkpoint.DURING_WAKE_PROPAGATION);
                if (!other.sleeping() || held.contains(other.id())) continue;
                var b = other.geometry().bounds(other.xCells(),other.yCells(),other.angleRadians());
                if (a.minX() - margin <= b.maxX() && a.maxX() + margin >= b.minX()
                        && a.minY() - margin <= b.maxY() && a.maxY() + margin >= b.minY()) {
                    other.wake(); interactionWork.woken++; queue.addLast(other);
                }
            }
        }
    }
    private List<BodyCollisionQuery.Contact> queryBodies() {
        return queryBodies(java.util.Set.of());
    }
    private List<BodyCollisionQuery.Contact> queryBodies(java.util.Set<Long> held) {
        long start=profiling?System.nanoTime():0;
        var r = held.isEmpty()?bodyQuery.query(bodies()):bodyQuery.queryHeldValidation(bodies(),held,MAX_HELD_PENETRATION_CELLS);
        if(profiling) bodyQueryNanos+=System.nanoTime()-start;
        interactionWork.pairs = Math.addExact(interactionWork.pairs, r.candidatePairs());
        interactionWork.overlapping = Math.addExact(interactionWork.overlapping, r.overlappingPairs());
        interactionWork.checks = Math.addExact(interactionWork.checks, r.narrowChecks());
        interactionWork.raw = Math.addExact(interactionWork.raw, r.rawContacts());
        interactionWork.reduced = Math.addExact(interactionWork.reduced, r.contacts().size());
        if (interactionWork.pairs > interactionConfig.maxPairs() || interactionWork.checks > interactionConfig.maxChecks()
                || interactionWork.raw > interactionConfig.maxRawContacts() || interactionWork.reduced > interactionConfig.maxReducedContacts())
            throw new MotionBudgetException("Body contact tick budget exceeded: pairs=" + interactionWork.pairs + ", checks=" + interactionWork.checks
                    + ", raw=" + interactionWork.raw + ", reduced=" + interactionWork.reduced);
        for (var contact : r.contacts()) interactionWork.penetration = Math.max(interactionWork.penetration, contact.penetrationCells());
        return r.contacts();
    }
}
