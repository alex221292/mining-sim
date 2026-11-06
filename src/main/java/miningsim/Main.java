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
import miningsim.render.Java2DRenderer;
import miningsim.render.Renderer;
import miningsim.persistence.WorldIO;
import miningsim.simulation.Simulation;
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
        private World world = new World(400, 300);
        private SimulationControl control = new SimulationControl(new Simulation(world, 12345));
        private Renderer renderer = new Java2DRenderer(world.width(), world.height());
        private final Camera2D camera = new Camera2D();
        private final Timer timer;
        private Material brush = Material.SAND;
        private long previous = System.nanoTime(), accumulator, statsStart = previous;
        private int frames, ticks, fps, tps;
        private boolean left, right;
        private boolean panLeft, panRight, panUp, panDown;

        Sandbox() {
            setPreferredSize(new Dimension(800, 600));
            setFocusable(true);
            MouseAdapter mouse = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();
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
                        case KeyEvent.VK_C -> world.clear();
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
                    SimulationControl nextControl = new SimulationControl(new Simulation(loaded, 12345));
                    if (control.paused()) nextControl.togglePause();
                    Renderer nextRenderer = new Java2DRenderer(loaded.width(), loaded.height());
                    world = loaded; control = nextControl; renderer = nextRenderer;
                    camera.reset();
                }
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, e.getMessage(), save ? "Save failed" : "Load failed", JOptionPane.ERROR_MESSAGE);
            } finally {
                previous = System.nanoTime(); accumulator = 0;
                timer.start(); requestFocusInWindow(); repaint();
            }
        }

        private void paintAt(MouseEvent e) {
            if (getWidth() <= 0 || getHeight() <= 0 || (!left && !right)) return;
            Brush.paint(world, camera, e.getX(), e.getY(), 3, right ? Material.AIR : brush);
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
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(1));
            g.fillRect(0, 0, Math.min(getWidth(), 720), 94);
            g.setColor(Color.WHITE);
            g.drawString("1 Sand  2 Water  3 Stone  4 Wood  5 Gravel  6 Soil | Space Pause  N Step  C Clear", 8, 16);
            g.drawString("Brush: " + brush + (control.paused() ? " | PAUSED" : "") + " | FPS: " + fps + " | TPS: " + tps + " | Cells: " + world.occupiedCount(), 8, 33);
            var metrics = control.metrics();
            g.drawString(String.format(Locale.ROOT, "Tick ms: last %.3f | avg %.3f | max %.3f (120 ticks)",
                    metrics.lastMillis(), metrics.averageMillis(), metrics.maxMillis()), 8, 50);
            g.drawString("Arrows: pan | Wheel: zoom | R: reset camera", 8, 67);
            g.drawString("Ctrl+S: save | Ctrl+O: load", 8, 84);
            g.dispose();
            frames++;
        }
    }
}
