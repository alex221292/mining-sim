package miningsim;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import javax.swing.JFrame;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import miningsim.app.SimulationControl;
import miningsim.app.Camera2D;
import miningsim.app.Brush;
import miningsim.app.StructuralSimulationController;
import miningsim.app.InteractiveSession;
import miningsim.app.SandboxSessionFactory;
import miningsim.render.CrackOverlay;
import miningsim.render.FragmentOverlay;
import miningsim.render.CoupledDiagnosticsOverlay;
import miningsim.render.LifecycleDiagnosticsOverlay;
import miningsim.structural.RockReferenceScenes;
import miningsim.structural.RestingStackFixture;
import miningsim.render.Java2DRenderer;
import miningsim.render.Renderer;
import miningsim.persistence.WorldIO;
import miningsim.persistence.FixedSessionIO;
import miningsim.world.Material;
import miningsim.world.World;
import miningsim.block.FixedBlockSession;
import miningsim.generation.WorldGenerationConfig;
import miningsim.generation.WorldGenerator;

public final class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Mining Sim — Falling Sand Sandbox");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.add(new Sandbox());
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    private static final class Sandbox extends JPanel {
        private static final long TICK = 1_000_000_000L / 60;
        private World world = new World(400, 300, World.Mode.BEDROCK_ENABLED);
        private InteractiveSession session;
        private FixedBlockSession fixed = new FixedBlockSession(world,12345);
        private int rockBlockSizeCells = miningsim.block.FixedBlockWorld.DEFAULT_BLOCK_SIZE;
        private SimulationControl control = new SimulationControl(fixed::tick);
        private Renderer renderer = new Java2DRenderer(world.width(), world.height());
        private final Camera2D camera = new Camera2D();
        private final Timer timer;
        private Material brush = Material.SAND;
        private long previous = System.nanoTime(), accumulator, statsStart = previous;
        private int frames, ticks, fps, tps;
        private boolean left, right;
        private int strokeX,strokeY; private boolean strokeStarted;
        private int hoverX=-1,hoverY=-1;
        private boolean panLeft, panRight, panUp, panDown;
        private StructuralSimulationController structural;
        private boolean diagnostics;
        private int sceneIndex = -1;
        private String sceneName = "";
        private Long generationSeed;

        Sandbox() {
            setPreferredSize(new Dimension(800, 600));
            setFocusable(true);
            MouseAdapter mouse = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();
                    if (SwingUtilities.isMiddleMouseButton(e) && session!=null && session.isCoupled()) {
                        var target=session; int x=(int)Math.floor(camera.screenToWorldX(e.getX())),y=(int)Math.floor(camera.screenToWorldY(e.getY()));
                        session.enqueue(()->target.selectRock(x,y));
                        repaint(); return;
                    }
                    if (SwingUtilities.isLeftMouseButton(e)) left = true;
                    if (SwingUtilities.isRightMouseButton(e)) right = true;
                    strokeStarted=false;
                    paintAt(e);
                }
                @Override public void mouseReleased(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) left = false;
                    if (SwingUtilities.isRightMouseButton(e)) right = false;
                    if(fixed!=null) { var target=fixed; target.enqueue(()->target.blocks().releaseStroke()); strokeStarted=false; }
                }
                @Override public void mouseDragged(MouseEvent e) { paintAt(e); }
                @Override public void mouseMoved(MouseEvent e) { hoverX=(int)Math.floor(camera.screenToWorldX(e.getX())); hoverY=(int)Math.floor(camera.screenToWorldY(e.getY())); }
                @Override public void mouseWheelMoved(MouseWheelEvent e) {
                    requestFocusInWindow();
                    camera.zoomAt(camera.zoom() * Math.pow(1.2, -e.getPreciseWheelRotation()), e.getX(), e.getY());
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
            addMouseWheelListener(mouse);
            addFocusListener(new FocusAdapter() {
                @Override public void focusLost(FocusEvent e) {
                    panLeft = panRight = panUp = panDown = left = right = false;
                    if(fixed!=null) { var target=fixed; target.enqueue(()->target.blocks().releaseStroke()); strokeStarted=false; }
                }
            });
            addKeyListener(new KeyAdapter() {
                @Override public void keyPressed(KeyEvent e) {
                    if ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0) {
                        if (e.getKeyCode() == KeyEvent.VK_S) chooseFile(true);
                        else if (e.getKeyCode() == KeyEvent.VK_O) chooseFile(false);
                        return;
                    }
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_1 -> brush = Material.SAND;
                        case KeyEvent.VK_2 -> brush = Material.WATER;
                        case KeyEvent.VK_3 -> brush = Material.STONE;
                        case KeyEvent.VK_4 -> brush = Material.WOOD;
                        case KeyEvent.VK_5 -> brush = Material.GRAVEL;
                        case KeyEvent.VK_6 -> brush = Material.SOIL;
                        case KeyEvent.VK_7 -> { if(fixed!=null) brush = Material.GRANITE; }
                        case KeyEvent.VK_8 -> { if(fixed!=null) brush = Material.LIMESTONE; }
                        case KeyEvent.VK_9 -> { if(fixed!=null) brush = Material.MARBLE; }
                        case KeyEvent.VK_SPACE -> { control.togglePause(); accumulator = 0; }
                        case KeyEvent.VK_N -> { if (control.singleStep()) ticks++; }
                        case KeyEvent.VK_F3 -> { diagnostics = !diagnostics; var target=session; boolean enabled=diagnostics; if(target!=null) target.enqueue(()->target.enableProfiling(enabled)); }
                        case KeyEvent.VK_F4 -> chooseReferenceScene();
                        case KeyEvent.VK_F5 -> { if (session!=null && session.isCoupled()) { var target=session; session.enqueue(()-> { if(target.extractSelected()!=miningsim.fragment.ExtractionCommit.Status.COMMITTED) java.awt.Toolkit.getDefaultToolkit().beep(); }); } }
                        case KeyEvent.VK_C -> resetWorld();
                        case KeyEvent.VK_F6 -> { if(session!=null) session.resume(); }
                        case KeyEvent.VK_F7 -> switchRockBlockSize();
                        case KeyEvent.VK_F8 -> generateWorld();
                        case KeyEvent.VK_LEFT -> panLeft = true;
                        case KeyEvent.VK_RIGHT -> panRight = true;
                        case KeyEvent.VK_UP -> panUp = true;
                        case KeyEvent.VK_DOWN -> panDown = true;
                        case KeyEvent.VK_R -> camera.reset();
                        default -> { }
                    }
                }
                @Override public void keyReleased(KeyEvent e) {
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_LEFT -> panLeft = false;
                        case KeyEvent.VK_RIGHT -> panRight = false;
                        case KeyEvent.VK_UP -> panUp = false;
                        case KeyEvent.VK_DOWN -> panDown = false;
                        default -> { }
                    }
                }
            });
            // Timer, input, simulation and painting all run on the Swing event thread.
            timer = new Timer(8, e -> update());
            timer.start();
        }

        private void chooseFile(boolean save) {
            // Modal dialogs run a nested event loop; stopping the timer freezes the world.
            timer.stop();
            try {
                var snapshot=save && fixed!=null ? FixedSessionIO.capture(fixed) : null;
                JFileChooser chooser = new JFileChooser();
                chooser.setFileFilter(new FileNameExtensionFilter("Mining Sim world (*.msim)", "msim"));
                int result = save ? chooser.showSaveDialog(this) : chooser.showOpenDialog(this);
                if (result != JFileChooser.APPROVE_OPTION) return;
                Path path = chooser.getSelectedFile().toPath();
                if (save) {
                    if (session!=null && SandboxSessionFactory.losesLifecycleState(session) && JOptionPane.showConfirmDialog(this,
                            "World-only save omits rigid bodies and settled-rock provenance. Continue?",
                            "Incomplete world save",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE)!=JOptionPane.YES_OPTION) return;
                    if (!path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".msim"))
                        path = path.resolveSibling(path.getFileName() + ".msim");
                    if (Files.exists(path) && JOptionPane.showConfirmDialog(this, "Replace existing file?", "Save world",
                            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
                    if(snapshot!=null) snapshot.write(path);
                    else WorldIO.write(world, path);
                } else {
                    if ((fixed!=null && fixed.blocks().size()>0 || session!=null && SandboxSessionFactory.losesLifecycleState(session)) && JOptionPane.showConfirmDialog(this,
                            "Loading replaces the current rigid bodies and settled-rock state. Continue?",
                            "Replace current session",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE)!=JOptionPane.YES_OPTION) return;
                    var prepared=FixedSessionIO.read(path); World loaded=prepared.world(); var nextFixed=prepared.fixed();
                    InteractiveSession nextSession=prepared.legacy()?new InteractiveSession(loaded,null,diagnostics,false):null;
                    SimulationControl nextControl = new SimulationControl(nextFixed!=null?nextFixed::tick:nextSession::tick);
                    if (control.paused()) nextControl.togglePause();
                    Renderer nextRenderer = new Java2DRenderer(loaded.width(), loaded.height());
                    if(session!=null) session.retire(); world = loaded; session=nextSession; fixed=nextFixed; control = nextControl; renderer = nextRenderer;
                    if(nextFixed!=null) rockBlockSizeCells=nextFixed.blocks().rockBlockSizeCells();
                    structural = null; sceneIndex = -1; sceneName = ""; generationSeed=null; left=right=strokeStarted=false;
                    camera.reset();
                }
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, e.getMessage(), save ? "Save failed" : "Load failed", JOptionPane.ERROR_MESSAGE);
            } finally {
                previous = System.nanoTime(); accumulator = 0;
                timer.start(); requestFocusInWindow(); repaint();
            }
        }

        private void resetWorld() {
            boolean paused=control.paused();
            var nextWorld=new World(world.width(),world.height(),World.Mode.BEDROCK_ENABLED);
            var nextFixed=new FixedBlockSession(nextWorld,12345,rockBlockSizeCells); var nextControl=new SimulationControl(nextFixed::tick);
            var nextRenderer=new Java2DRenderer(nextWorld.width(),nextWorld.height());
            if(paused) nextControl.togglePause();
            if(session!=null) session.retire(); world=nextWorld; structural=null; session=null; fixed=nextFixed; control=nextControl; renderer=nextRenderer;
            sceneIndex=-1; sceneName=""; generationSeed=null; left=right=strokeStarted=false;
        }

        private void generateWorld() {
            timer.stop();
            try {
                if(JOptionPane.showConfirmDialog(this,"Replace the current scene with a generated world?", "Generate world",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION) return;
                String text=JOptionPane.showInputDialog(this,"Seed (leave blank for random):", "Generate world",JOptionPane.QUESTION_MESSAGE);
                Long seed=text==null?null:text.isBlank()?java.util.concurrent.ThreadLocalRandom.current().nextLong():Long.parseLong(text.trim());
                var generated=WorldGenerator.prepare(seed==null?null:WorldGenerationConfig.preset(seed,rockBlockSizeCells));
                if(generated==null) return;
                var nextFixed=generated.session(); var nextWorld=nextFixed.world();
                var nextControl=new SimulationControl(nextFixed::tick); nextControl.togglePause();
                var nextRenderer=new Java2DRenderer(nextWorld.width(),nextWorld.height());
                if(session!=null) session.retire();
                world=nextWorld; fixed=nextFixed; session=null; structural=null; control=nextControl; renderer=nextRenderer;
                generationSeed=seed; sceneIndex=-1; sceneName=""; left=right=strokeStarted=false;
                camera.reset(); camera.pan(0,Math.max(0,generated.config().baseSurfaceHeight()-getHeight()/(4.0*camera.zoom())));
            } catch(IllegalArgumentException | IllegalStateException e) {
                JOptionPane.showMessageDialog(this,e.getMessage(),"Generation failed",JOptionPane.ERROR_MESSAGE);
            } finally { previous=System.nanoTime(); accumulator=0; timer.start(); requestFocusInWindow(); repaint(); }
        }

        private void switchRockBlockSize() {
            timer.stop();
            try {
                int next=rockBlockSizeCells==8?4:8;
                if(JOptionPane.showConfirmDialog(this,"Switch to "+next+"x"+next+" blocks? The current scene will be erased.",
                        "Replace scene",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE)!=JOptionPane.YES_OPTION) return;
                rockBlockSizeCells=next; resetWorld();
            } finally { previous=System.nanoTime(); accumulator=0; timer.start(); requestFocusInWindow(); repaint(); }
        }

        private void chooseReferenceScene() {
            timer.stop();
            try {
                if (JOptionPane.showConfirmDialog(this, "Replace current world with a reference scene? Unsaved work will be lost.",
                        "Reference scene", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
                int next = (sceneIndex + 1) % 11;
                var scene = switch (next) {
                    case 0 -> RockReferenceScenes.roof("short",4,1);
                    case 1 -> RockReferenceScenes.roof("long",12,1);
                    case 2 -> RockReferenceScenes.roof("thick",12,2);
                    case 3 -> RockReferenceScenes.cantilever(8);
                    case 4 -> RockReferenceScenes.island();
                    case 5 -> RockReferenceScenes.interactive("falling rock",Material.AIR);
                    case 6 -> RockReferenceScenes.interactive("rock and sand",Material.SAND);
                    case 7 -> RockReferenceScenes.interactive("rock and water",Material.WATER);
                    case 8 -> RockReferenceScenes.cantilever(12);
                    case 9 -> RockReferenceScenes.oversizedDetachment();
                    default -> RestingStackFixture.scene();
                };
                boolean paused = control.paused();
                var nextWorld=scene.world(); var nextStructural=new StructuralSimulationController(scene);
                if(next==10) RestingStackFixture.populate(nextStructural.registry());
                var nextSession=new InteractiveSession(nextWorld,nextStructural,diagnostics,next==8 || next==9);
                if(next==10) { nextSession.physics().synchronize(); nextSession.occupancy().rebuild(nextSession.physics(),0); }
                if(next>=5 && next<=7) { nextStructural.tick(); nextSession.extractSelected(); }
                var nextControl=new SimulationControl(nextSession::tick); if(paused) nextControl.togglePause();
                var nextRenderer=new Java2DRenderer(nextWorld.width(),nextWorld.height());
                if(session!=null) session.retire(); world=nextWorld; structural=nextStructural; session=nextSession; fixed=null; control=nextControl; renderer=nextRenderer;
                sceneIndex = next; sceneName = scene.name(); generationSeed=null;
                camera.reset(); camera.zoomAt(Camera2D.MAX_ZOOM,0,0);
                camera.pan(-Math.max(0,(getWidth()/camera.zoom()-world.width())/2), -Math.max(0,(getHeight()/camera.zoom()-world.height())/2));
                if(next==10) camera.pan(0,Math.max(0,world.height()-getHeight()/camera.zoom()));
            } finally { previous=System.nanoTime(); accumulator=0; timer.start(); requestFocusInWindow(); repaint(); }
        }

        private void paintAt(MouseEvent e) {
            if (getWidth() <= 0 || getHeight() <= 0 || (!left && !right)) return;
            double x=camera.screenToWorldX(e.getX()),y=camera.screenToWorldY(e.getY());
            hoverX=(int)Math.floor(x); hoverY=(int)Math.floor(y);
            if(fixed!=null) {
                int ix=(int)Math.floor(x),iy=(int)Math.floor(y); if(!world.contains(ix,iy)) { strokeStarted=false; return; }
                var target=fixed; var material=right?Material.AIR:brush;
                int previousX=strokeStarted?strokeX:ix,previousY=strokeStarted?strokeY:iy; strokeX=ix; strokeY=iy; strokeStarted=true;
                if(right || material.isFixedRock()) target.enqueue(()->target.blockStroke(previousX,previousY,ix,iy,material==Material.AIR,material));
                if(!material.isFixedRock()) target.enqueue(()->target.paintCells(ix,iy,3,material));
                return;
            }
            var target=session; var material=right?Material.AIR:brush;
            if(material.isFixedRock() && material!=Material.STONE) return;
            target.enqueue(()->Brush.paintWorld(target.world(),x,y,3,material,
                    target.isCoupled()?target.occupancy().snapshot():null));
        }

        private void update() {
            long now = System.nanoTime();
            double secondsElapsed = Math.min((now - previous) / 1_000_000_000.0, 0.1);
            double panDistance = 400 * secondsElapsed / camera.zoom();
            camera.pan(((panRight ? 1 : 0) - (panLeft ? 1 : 0)) * panDistance,
                    ((panDown ? 1 : 0) - (panUp ? 1 : 0)) * panDistance);
            accumulator = Math.min(accumulator + now - previous, TICK * 5);
            previous = now;
            if (control.paused()) accumulator = 0;
            while (accumulator >= TICK) {
                if (control.tick()) ticks++;
                accumulator -= TICK;
            }
            if (now - statsStart >= 1_000_000_000L) {
                double seconds = (now - statsStart) / 1_000_000_000.0;
                fps = (int) (frames / seconds); tps = (int) (ticks / seconds);
                frames = 0; ticks = 0; statsStart = now;
            }
            repaint();
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            renderer.render(g, world, camera, getWidth(), getHeight());
            if(fixed!=null) {
                g.setColor(new Color(0x45515d));
                for(var block:fixed.blocks().blocks()) {
                    int x=(int)Math.round(camera.worldToScreenX(block.x())),y=(int)Math.round(camera.worldToScreenY(block.y()));
                    int size=(int)Math.round(fixed.blocks().rockBlockSizeCells()*camera.zoom());
                    if(x<getWidth() && y<getHeight() && x+size>0 && y+size>0) g.drawRect(x,y,size,size);
                }
            }
            if (session!=null && session.isCoupled()) FragmentOverlay.renderBodies(g,camera,session.physics().bodies());
            if (diagnostics && structural != null) CrackOverlay.render(g,camera,structural.view().marks());
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(1));
            g.fillRect(0, 0, Math.min(getWidth(), 790), diagnostics ? 445 : 110);
            g.setColor(Color.WHITE);
            g.drawString("1 Sand 2 Water 3 Stone 4 Wood 5 Gravel 6 Soil 7 Granite 8 Limestone 9 Marble", 8, 16);
            String runState=session!=null && session.blocked()?"BLOCKED":control.paused()?"PAUSED":session!=null && session.failure()!=null?"RETRYING":"RUNNING";
            g.drawString("Brush: " + brush + (fixed!=null?" | Rock block: "+rockBlockSizeCells+"x"+rockBlockSizeCells+(rockBlockSizeCells==4?" EXPERIMENTAL":""):world.mode()==World.Mode.LEGACY?" | LEGACY v1":"") + " | " + runState + " | FPS: " + fps + " | TPS attempts: " + tps + " | Cells: " + world.occupiedCount(), 8, 33);
            var metrics = control.metrics();
            g.drawString(String.format(Locale.ROOT, "Tick ms: last %.3f | avg %.3f | max %.3f (120 ticks)",
                    metrics.lastMillis(), metrics.averageMillis(), metrics.maxMillis()), 8, 50);
            if(session!=null && session.failure()!=null) {
                String reason=session.failure(); if(reason.length()>85) reason=reason.substring(0,82)+"...";
                g.drawString(reason+" | F6 retry",8,67);
            } else g.drawString("Space: pause | N: step | C: clear | Arrows: pan | Wheel: zoom | R: reset camera", 8, 67);
            g.drawString("Ctrl+S: save | Ctrl+O: load", 8, 84);
            g.drawString(fixed!=null?"F3: diagnostics | F4: scene | F7: block size | F8: generate":"F3: diagnostics | F4: scene | F5: extract | F6: retry | F7: size | F8: generate",8,101);
            if(generationSeed!=null) g.drawString("Generated seed: "+generationSeed, 8, getHeight()-8);
            if (diagnostics) {
                if(fixed!=null) {
                    var b=fixed.blocks(); var list=b.blocks(); long falling=list.stream().filter(v->v.state()==miningsim.block.FixedBlockWorld.State.FALLING).count();
                    var support=b.supportDiagnostics();
                    g.drawString("Blocks "+list.size()+" | supported "+support.supported()+" | unsupported "+support.unsupported()+" | waiting "+support.waiting()+" | falling "+falling,8,118);
                    g.drawString(String.format(Locale.ROOT,"Support %.3f ms | processed %d | contacts %d | block %.3f ms",support.analysisNanos()/1e6,support.processed(),support.contacts(),b.updateNanos()/1e6),8,135);
                    var blocked=b.diagnostics().stream().filter(d->d.reason()==miningsim.block.FixedBlockWorld.Reason.WATER_DISPLACEMENT_BLOCKED || d.reason()==miningsim.block.FixedBlockWorld.Reason.WATER_SEARCH_BUDGET_EXCEEDED || d.reason()==miningsim.block.FixedBlockWorld.Reason.BLOCKED_BY_STONE || d.reason()==miningsim.block.FixedBlockWorld.Reason.INVALID_OR_STALE_PLAN).findFirst();
                    if(blocked.isPresent()) { var d=blocked.get(); g.drawString("Block "+d.id()+": "+d.reason()+" | visits "+d.visits()+" checks "+d.checks(),8,152); }
                    var hovered=b.blockAt(hoverX,hoverY);
                    if(hovered!=null) { var d=b.blockSupportAt(hoverX,hoverY); g.drawString("Cursor block "+d.id()+" "+hovered.substance()+" | support "+d.supported()+" distance "+d.distanceCells()+" | "+d.state()+" wait "+d.remainingDelayTicks(),8,169); }
                } else {
                if (structural == null) g.drawString("STRUCTURE DISABLED (reference scenes only)",8,118);
                else {
                    var v = structural.view(); long broken = v.marks().stream().filter(StructuralSimulationController.Mark::broken).count();
                    g.drawString(String.format(Locale.ROOT,"%s: %s | component %d (%d cells) | U %.2f damage %.2f | broken %d | tick %d | selected only",
                            sceneName,v.status(),v.componentId(),v.cells(),v.utilization(),v.damage(),broken,v.ticks()),8,118);
                }
                CoupledDiagnosticsOverlay.render(g,session);
                LifecycleDiagnosticsOverlay.render(g,session);
                if(session.automatic()==null) g.drawString("AUTO DETACH: disabled (reference or legacy)",8,186);
                else {
                    var d=session.automatic().diagnostics();
                    g.drawString(String.format(Locale.ROOT,"AUTO DETACH: %s | topology rev %d | work %d | unsupported %d eligible %d",
                            d.pending()?"pending":"complete",d.revision(),d.work(),d.unsupported(),d.eligible()),8,186);
                    g.drawString(String.format(Locale.ROOT,"Detached %d (%d cells), total %d | settling %d | too large %d | rejected %d",
                            d.detached(),d.detachedCells(),d.total(),d.deferred(),d.tooLarge(),d.rejected()),8,203);
                    var message=d.failure(); if(message.length()>100) message=message.substring(0,97)+"...";
                    g.drawString(message,8,220);
                }
                }
            }
            g.dispose();
            frames++;
        }
    }
}
