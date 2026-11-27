package miningsim.visual;

import java.util.List;

/** Backend-independent cumulative crack segments, in normalized 0..100 coordinates. */
public final class BlockDamage {
    public record Segment(int stage, int x1, int y1, int x2, int y2) {}
    private static final List<Segment> SEGMENTS = List.of(
        new Segment(1,43,42,51,48), new Segment(1,51,48,55,56),
        new Segment(2,43,42,37,34), new Segment(2,55,56,63,61),
        new Segment(3,37,34,31,23), new Segment(3,51,48,61,41),
        new Segment(4,63,61,67,73), new Segment(4,61,41,73,37),
        new Segment(5,31,23,21,18), new Segment(5,37,34,25,38), new Segment(5,67,73,60,84),
        new Segment(6,73,37,81,26), new Segment(6,25,38,17,48), new Segment(6,55,56,43,65),
        new Segment(7,17,48,21,61), new Segment(7,43,65,35,76), new Segment(7,67,73,79,78),
        new Segment(8,81,26,91,21), new Segment(8,21,61,12,71), new Segment(8,60,84,54,94), new Segment(8,73,37,84,48),
        new Segment(9,21,18,14,8), new Segment(9,31,23,40,14), new Segment(9,79,78,89,87), new Segment(9,84,48,93,56), new Segment(9,35,76,23,87),
        new Segment(10,40,14,49,8), new Segment(10,84,48,78,60), new Segment(10,78,60,89,67), new Segment(10,35,76,39,91), new Segment(10,12,71,7,82), new Segment(10,60,84,70,91));
    private BlockDamage() {}
    public static List<Segment> segments() { return SEGMENTS; }
    public static int stage(int work, int required) {
        if (required <= 0 || work < 0 || work > required) throw new IllegalArgumentException("Invalid progress");
        if (work == 0) return 0;
        return Math.max(1, (int)((long)work * 10 / required));
    }
}
