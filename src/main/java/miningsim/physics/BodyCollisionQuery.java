package miningsim.physics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Read-only SAT query. The normal points from the lower-ID body toward the higher-ID body. */
public final class BodyCollisionQuery {
    public record Limits(long pairs, long checks, int rawContacts, int reducedContacts) {
        public Limits {
            if (pairs < 1 || checks < 1 || rawContacts < 1 || reducedContacts < 1 || rawContacts > 4096)
                throw new IllegalArgumentException("Invalid body collision limits");
        }
    }
    public record Contact(long bodyA, long bodyB, double normalX, double normalY,
                          double penetrationCells, double pointX, double pointY) { }
    public record Result(List<Contact> contacts, long candidatePairs, long overlappingPairs,
                         long narrowChecks, long rawContacts) {
        public Result { contacts = List.copyOf(contacts); }
    }
    private final Limits limits;
    public BodyCollisionQuery(Limits limits) { this.limits = Objects.requireNonNull(limits); }

    /** The caller must provide bodies at the same simulation time. */
    public Result query(List<RigidBody> input) {
        var bodies = new ArrayList<>(input); bodies.sort(Comparator.comparingLong(RigidBody::id));
        for (int i = 1; i < bodies.size(); i++)
            if (bodies.get(i - 1).id() == bodies.get(i).id()) throw new IllegalArgumentException("Duplicate body ID");
        var result = new ArrayList<Contact>(); long pairs = 0, overlapping = 0, checks = 0, rawCount = 0;
        for (int i = 0; i < bodies.size(); i++) for (int j = i + 1; j < bodies.size(); j++) {
            if (++pairs > limits.pairs()) throw new IllegalStateException("Body candidate-pair limit exceeded: " + pairs + " > " + limits.pairs());
            var a = bodies.get(i); var b = bodies.get(j);
            var ab = a.geometry().bounds(a.xCells(), a.yCells(), a.angleRadians());
            var bb = b.geometry().bounds(b.xCells(), b.yCells(), b.angleRadians());
            if (ab.maxX() <= bb.minX() || bb.maxX() <= ab.minX() || ab.maxY() <= bb.minY() || bb.maxY() <= ab.minY()) continue;
            overlapping++;
            var pairContacts = new ArrayList<Contact>();
            for (var ca : a.fragment().descriptor().cells()) for (var cb : b.fragment().descriptor().cells()) {
                if (++checks > limits.checks()) throw new IllegalStateException("Body narrow-phase limit exceeded: " + checks + " > " + limits.checks());
                var pa = a.geometry().transform(ca.x() + 0.5, ca.y() + 0.5, a.xCells(), a.yCells(), a.angleRadians());
                var pb = b.geometry().transform(cb.x() + 0.5, cb.y() + 0.5, b.xCells(), b.yCells(), b.angleRadians());
                var hit = SquareCollision.intersect(new SquareCollision.Square(pa.x(), pa.y(), a.angleRadians()),
                        new SquareCollision.Square(pb.x(), pb.y(), b.angleRadians()));
                if (hit == null) continue;
                if (++rawCount > limits.rawContacts()) throw new IllegalStateException("Body raw-contact limit exceeded: " + rawCount + " > " + limits.rawContacts());
                pairContacts.add(new Contact(a.id(), b.id(), hit.normalX(), hit.normalY(), hit.penetrationCells(), hit.pointX(), hit.pointY()));
            }
            // Deterministically group similar normals and keep tangent extremes, not AABB geometry.
            var remaining = new ArrayList<>(pairContacts);
            while (!remaining.isEmpty()) {
                var reference = remaining.removeFirst(); var min = reference; var max = reference;
                double low = tangent(reference, reference), high = low;
                for (int k = 0; k < remaining.size();) {
                    var c = remaining.get(k);
                    if (reference.normalX() * c.normalX() + reference.normalY() * c.normalY() < 0.999) { k++; continue; }
                    remaining.remove(k); double t = tangent(reference, c);
                    if (t < low) { low = t; min = c; } if (t > high) { high = t; max = c; }
                }
                result.add(min); if (high - low > 1e-9) result.add(max);
                if (result.size() > limits.reducedContacts()) throw new IllegalStateException("Body reduced-contact limit exceeded: " + result.size() + " > " + limits.reducedContacts());
            }
        }
        return new Result(result, pairs, overlapping, checks, rawCount);
    }
    private static double tangent(Contact normal, Contact point) {
        return -normal.normalY() * point.pointX() + normal.normalX() * point.pointY();
    }
}
