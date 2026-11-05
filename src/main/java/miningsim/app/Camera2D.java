package miningsim.app;

/** Origin is the world coordinate at screen (0,0); zoom is pixels per cell. */
public final class Camera2D {
    public static final double MIN_ZOOM = 0.5, MAX_ZOOM = 16.0, DEFAULT_ZOOM = 2.0;
    private double x, y, zoom = DEFAULT_ZOOM;

    public record CellRect(int left, int top, int right, int bottom) { }

    public double x() { return x; }
    public double y() { return y; }
    public double zoom() { return zoom; }
    public double screenToWorldX(double screenX) { return x + screenX / zoom; }
    public double screenToWorldY(double screenY) { return y + screenY / zoom; }
    public double worldToScreenX(double worldX) { return (worldX - x) * zoom; }
    public double worldToScreenY(double worldY) { return (worldY - y) * zoom; }
    public void pan(double dx, double dy) { x += dx; y += dy; }
    public void reset() { x = 0; y = 0; zoom = DEFAULT_ZOOM; }

    public void zoomAt(double requestedZoom, double screenX, double screenY) {
        if (!Double.isFinite(requestedZoom) || requestedZoom <= 0) throw new IllegalArgumentException("zoom must be positive and finite");
        double anchorX = screenToWorldX(screenX), anchorY = screenToWorldY(screenY);
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, requestedZoom));
        x = anchorX - screenX / zoom;
        y = anchorY - screenY / zoom;
    }

    /** Half-open rectangle of potentially visible cells, clipped to the world. */
    public CellRect visibleCells(int screenWidth, int screenHeight, int worldWidth, int worldHeight) {
        if (screenWidth <= 0 || screenHeight <= 0) return new CellRect(0, 0, 0, 0);
        int left = clamp(Math.floor(x), worldWidth), top = clamp(Math.floor(y), worldHeight);
        int right = clamp(Math.ceil(screenToWorldX(screenWidth)), worldWidth);
        int bottom = clamp(Math.ceil(screenToWorldY(screenHeight)), worldHeight);
        return new CellRect(left, top, Math.max(left, right), Math.max(top, bottom));
    }
    private static int clamp(double coordinate, int limit) {
        return (int) Math.max(0, Math.min(limit, coordinate));
    }
}
