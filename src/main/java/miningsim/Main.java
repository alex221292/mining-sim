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
import miningsim.render.CrackOverlay;
import miningsim.render.FragmentOverlay;
import miningsim.render.CoupledDiagnosticsOverlay;
import miningsim.structural.RockReferenceScenes;
import miningsim.structural.RestingStackFixture;
import miningsim.render.Java2DRenderer;
import miningsim.render.Renderer;
import miningsim.persistence.WorldIO;
import miningsim.world.Material;
import miningsim.world.World;

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
        private InteractiveSession session = new InteractiveSession(world,null);
        private SimulationControl control = new SimulationControl(session::tick);
        private Renderer renderer = new Java2DRenderer(world.width(), world.height());
        private final Camera2D camera = new Camera2D();
        private final Timer timer;
        private Material brush = Material.SAND;
        private long previous = System.nanoTime(), accumulator, statsStart = previous;
        private int frames, ticks, fps, tps;
        private boolean left, right;
        private boolean panLeft, panRight, panUp, panDown;
        private StructuralSimulationController structural;
        private boolean diagnostics;
        private int sceneIndex = -1;
        private String sceneName = "";

        Sandbox() {
            setPreferredSize(new Dimension(800, 600));
            setFocusable(true);
            MouseAdapter mouse = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();
                    if (SwingUtilities.isMiddleMouseButton(e) && session.isCoupled()) {
                        var target=session; int x=(int)Math.floor(camera.screenToWorldX(e.getX())),y=(int)Math.floor(camera.screenToWorldY(e.getY()));
                        session.enqueue(()->target.selectRock(x,y));
                        repaint(); return;
                    }
                    if (SwingUtilities.isLeftMouseButton(e)) left = true;
                    if (SwingUtilities.isRightMouseButton(e)) right = true;
                    paintAt(e);
                }
                @Override public void mouseReleased(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) left = false;
                    if (SwingUtilities.isRightMouseButton(e)) right = false;
                }
                @Override public void mouseDragged(MouseEvent e) { paintAt(e); }
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
                        case KeyEvent.VK_SPACE -> { control.togglePause(); accumulator = 0; }
                        case KeyEvent.VK_N -> { if (control.singleStep()) ticks++; }
                        case KeyEvent.VK_F3 -> { diagnostics = !diagnostics; var target=session; boolean enabled=diagnostics; target.enqueue(()->target.enableProfiling(enabled)); }
                        case KeyEvent.VK_F4 -> chooseReferenceScene();
                        case KeyEvent.VK_F5 -> { if (session.isCoupled()) { var target=session; session.enqueue(()-> { if(target.extractSelected()!=miningsim.fragment.ExtractionCommit.Status.COMMITTED) java.awt.Toolkit.getDefaultToolkit().beep(); }); } }
                        case KeyEvent.VK_C -> resetWorld();
                        case KeyEvent.VK_F6 -> session.resume();
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
                JFileChooser chooser = new JFileChooser();
                chooser.setFileFilter(new FileNameExtensionFilter("Mining Sim world (*.msim)", "msim"));
                int result = save ? chooser.showSaveDialog(this) : chooser.showOpenDialog(this);
                if (result != JFileChooser.APPROVE_OPTION) return;
                Path path = chooser.getSelectedFile().toPath();
                if (save) {
                    if (!path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".msim"))
                        path = path.resolveSibling(path.getFileName() + ".msim");
                    if (Files.exists(path) && JOptionPane.showConfirmDialog(this, "Replace existing file?", "Save world",
                            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
                    WorldIO.write(world, path);
                } else {
                    World loaded = WorldIO.read(path);
                    InteractiveSession nextSession = new InteractiveSession(loaded,null,diagnostics);
                    SimulationControl nextControl = new SimulationControl(nextSession::tick);
                    if (control.paused()) nextControl.togglePause();
                    Renderer nextRenderer = new Java2DRenderer(loaded.width(), loaded.height());
                    session.retire(); world = loaded; session=nextSession; control = nextControl; renderer = nextRenderer;
                    structural = null; sceneIndex = -1; sceneName = "";
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
            var nextWorld=new World(world.width(),world.height(),world.mode());
            var nextSession=new InteractiveSession(nextWorld,null,diagnostics); var nextControl=new SimulationControl(nextSession::tick);
            var nextRenderer=new Java2DRenderer(nextWorld.width(),nextWorld.height());
            if(paused) nextControl.togglePause();
            session.retire(); world=nextWorld; structural=null; session=nextSession; control=nextControl; renderer=nextRenderer;
            sceneIndex=-1; sceneName="";
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
                session.retire(); world=nextWorld; structural=nextStructural; session=nextSession; control=nextControl; renderer=nextRenderer;
                sceneIndex = next; sceneName = scene.name();
                camera.reset(); camera.zoomAt(Camera2D.MAX_ZOOM,0,0);
                camera.pan(-Math.max(0,(getWidth()/camera.zoom()-world.width())/2), -Math.max(0,(getHeight()/camera.zoom()-world.height())/2));
                if(next==10) camera.pan(0,Math.max(0,world.height()-getHeight()/camera.zoom()));
            } finally { previous=System.nanoTime(); accumulator=0; timer.start(); requestFocusInWindow(); repaint(); }
        }

        private void paintAt(MouseEvent e) {
            if (getWidth() <= 0 || getHeight() <= 0 || (!left && !right)) return;
            double x=camera.screenToWorldX(e.getX()),y=camera.screenToWorldY(e.getY());
            var target=session; var material=right?Material.AIR:brush;
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
            if (session.isCoupled()) FragmentOverlay.renderBodies(g,camera,session.physics().bodies());
            if (diagnostics && structural != null) CrackOverlay.render(g,camera,structural.view().marks());
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(1));
            g.fillRect(0, 0, Math.min(getWidth(), 790), diagnostics ? 275 : 110);
            g.setColor(Color.WHITE);
            g.drawString("1 Sand  2 Water  3 Stone  4 Wood  5 Gravel  6 Soil | Space Pause  N Step  C Clear", 8, 16);
            g.drawString("Brush: " + brush + (control.paused() ? " | PAUSED" : "") + " | FPS: " + fps + " | TPS: " + tps + " | Cells: " + world.occupiedCount(), 8, 33);
            var metrics = control.metrics();
            g.drawString(String.format(Locale.ROOT, "Tick ms: last %.3f | avg %.3f | max %.3f (120 ticks)",
                    metrics.lastMillis(), metrics.averageMillis(), metrics.maxMillis()), 8, 50);
            g.drawString("Arrows: pan | Wheel: zoom | R: reset camera", 8, 67);
            g.drawString("Ctrl+S: save | Ctrl+O: load", 8, 84);
            g.drawString("F3: diagnostics | F4: scene | F5: extract | F6: retry | Middle click: select",8,101);
            if (diagnostics) {
                if (structural == null) g.drawString("STRUCTURE DISABLED (reference scenes only)",8,118);
                else {
                    var v = structural.view(); long broken = v.marks().stream().filter(StructuralSimulationController.Mark::broken).count();
                    g.drawString(String.format(Locale.ROOT,"%s: %s | component %d (%d cells) | U %.2f damage %.2f | broken %d | tick %d | selected only",
                            sceneName,v.status(),v.componentId(),v.cells(),v.utilization(),v.damage(),broken,v.ticks()),8,118);
                }
                CoupledDiagnosticsOverlay.render(g,session);
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
            g.dispose();
            frames++;
        }
    }
}
