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
import java.util.Locale;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import miningsim.app.SimulationControl;
import miningsim.render.Java2DRenderer;
import miningsim.render.Renderer;
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
        private final World world = new World(400, 300);
        private final SimulationControl control = new SimulationControl(new Simulation(world, 12345));
        private final Renderer renderer = new Java2DRenderer(world.width(), world.height());
        private Material brush = Material.SAND;
        private long previous = System.nanoTime(), accumulator, statsStart = previous;
        private int frames, ticks, fps, tps;
        private boolean left, right;

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
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
            addKeyListener(new KeyAdapter() {
                @Override public void keyPressed(KeyEvent e) {
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_1 -> brush = Material.SAND;
                        case KeyEvent.VK_2 -> brush = Material.WATER;
                        case KeyEvent.VK_3 -> brush = Material.STONE;
                        case KeyEvent.VK_4 -> brush = Material.WOOD;
                        case KeyEvent.VK_SPACE -> { control.togglePause(); accumulator = 0; }
                        case KeyEvent.VK_N -> { if (control.singleStep()) ticks++; }
                        case KeyEvent.VK_C -> world.clear();
                        default -> { }
                    }
                }
            });
            // Timer, input, simulation and painting all run on the Swing event thread.
            new Timer(8, e -> update()).start();
        }

        private void paintAt(MouseEvent e) {
            if (getWidth() <= 0 || getHeight() <= 0 || (!left && !right)) return;
            int cx = e.getX() * world.width() / getWidth();
            int cy = e.getY() * world.height() / getHeight();
            for (int dy = -3; dy <= 3; dy++) for (int dx = -3; dx <= 3; dx++) {
                int x = cx + dx, y = cy + dy;
                if (dx * dx + dy * dy <= 9 && world.contains(x, y))
                    world.set(x, y, right ? Material.AIR : brush);
            }
        }

        private void update() {
            long now = System.nanoTime();
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
            renderer.render(g, world, getWidth(), getHeight());
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(1));
            g.fillRect(0, 0, Math.min(getWidth(), 610), 60);
            g.setColor(Color.WHITE);
            g.drawString("1 Sand  2 Water  3 Stone  4 Wood | Space Pause  N Step  C Clear", 8, 16);
            g.drawString("Brush: " + brush + (control.paused() ? " | PAUSED" : "") + " | FPS: " + fps + " | TPS: " + tps + " | Cells: " + world.occupiedCount(), 8, 33);
            var metrics = control.metrics();
            g.drawString(String.format(Locale.ROOT, "Tick ms: last %.3f | avg %.3f | max %.3f (120 ticks)",
                    metrics.lastMillis(), metrics.averageMillis(), metrics.maxMillis()), 8, 50);
            g.dispose();
            frames++;
        }
    }
}
