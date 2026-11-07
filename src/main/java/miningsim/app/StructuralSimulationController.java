package miningsim.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import miningsim.structural.RockConnectivity;
import miningsim.structural.RockDamageSystem;
import miningsim.structural.RockReferenceScenes;
import miningsim.world.Material;
import miningsim.world.World;

/** Explicit one-tick coordinator for small reference worlds, independent of painting. */
public final class StructuralSimulationController {
    public static final int CONNECTIVITY_BUDGET = 4096;
    public static final int MAX_WORLD_CELLS = 4096;
    public record Mark(int x1, int y1, int x2, int y2, double damage, boolean broken) { }
    public record View(String status, int componentId, int cells, double utilization, double damage,
                       long ticks, List<Mark> marks) {
        public View { marks = List.copyOf(marks); }
    }
    private final World world;
    private final RockConnectivity connectivity;
    private final RockDamageSystem damage;
    private int selectedX, selectedY;
    private long ticks, analyzedRevision = -1;
    private int analyzedComponent = -1;
    private View view = new View("PENDING", -1, 0, 0, 0, 0, List.of());

    public StructuralSimulationController(RockReferenceScenes.Scene scene) {
        Objects.requireNonNull(scene);
        world = scene.world(); connectivity = scene.connectivity(); damage = scene.damage();
        if ((long)world.width() * world.height() > MAX_WORLD_CELLS) throw new IllegalArgumentException("Reference world too large");
        selectedX = scene.x(); selectedY = scene.y();
    }
    public World world() { return world; }
    public void select(int x, int y) {
        if (world.contains(x,y) && world.get(x,y) == Material.STONE) {
            selectedX = x; selectedY = y; analyzedComponent = -1;
            view = new View("PENDING", -1, 0, 0, 0, ticks, view.marks().stream().filter(Mark::broken).toList());
        }
    }
    /** Live invalidation is checked on reads; no solve or damage work is performed here. */
    public View view() {
        if (connectivity.pending() || analyzedRevision != connectivity.revision()) {
            List<Mark> surviving = view.marks().stream().filter(Mark::broken)
                    .filter(m -> connectivity.bondState(m.x1(),m.y1(),m.x2(),m.y2()) == RockConnectivity.BondState.BROKEN).toList();
            return new View("PENDING", -1, 0, 0, 0, ticks, surviving);
        }
        return view;
    }
    /** Called once after each ordinary simulation step, including a paused single-step. */
    public void tick() {
        long tick = ticks++;
        if (connectivity.pending()) connectivity.advance(CONNECTIVITY_BUDGET);
        if (connectivity.pending()) { view = new View("PENDING",-1,0,0,0,ticks,List.of()); return; }
        if (!world.contains(selectedX,selectedY) || world.get(selectedX,selectedY) != Material.STONE) {
            view = new View("NO SELECTION",-1,0,0,0,ticks,brokenMarks()); analyzedRevision=connectivity.revision(); return;
        }
        int component = connectivity.componentAt(selectedX,selectedY);
        if (analyzedRevision != connectivity.revision() || analyzedComponent != component) {
            damage.analyze(selectedX,selectedY); analyzedRevision=connectivity.revision(); analyzedComponent=component;
        }
        if (damage.status() == RockDamageSystem.Status.CONVERGED) damage.advanceTick(tick);
        if (connectivity.pending()) {
            view = new View("PENDING",-1,0,0,0,ticks,brokenMarks()); return;
        }
        var status = damage.status();
        ArrayList<Mark> marks = new ArrayList<>(brokenMarks());
        double maxUtil = 0, maxDamage = 0; int count = 0;
        if (status == RockDamageSystem.Status.CONVERGED) {
            var snap = damage.snapshot(); count = snap.nodeCount();
            for (var bond : snap.bonds()) {
                maxUtil = Math.max(maxUtil,bond.utilization()); maxDamage = Math.max(maxDamage,bond.damage());
                if (bond.damage() > 0) marks.add(new Mark(bond.x1(),bond.y1(),bond.x2(),bond.y2(),bond.damage(),false));
            }
        } else {
            for(int i=0;i<connectivity.componentCount();i++) if(connectivity.componentId(i)==component) { count=connectivity.componentSize(i); break; }
        }
        view = new View(status.name(),component,count,maxUtil,maxDamage,ticks,marks);
    }
    private List<Mark> brokenMarks() {
        ArrayList<Mark> marks = new ArrayList<>();
        connectivity.forEachBond(0,0,world.width(),world.height(),(x,y,nx,ny,state)-> {
            if(state == RockConnectivity.BondState.BROKEN) marks.add(new Mark(x,y,nx,ny,1,true));
        });
        return marks;
    }
}
