package com.wildkarts.track;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.CatmullRomSpline;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;

/**
 * Track generator using manual control points and CatmullRomSpline.
 *
 * Workflow:
 * 1. Add control points via addPoint() (editor clicks)
 * 2. When >= 4 points exist, a closed CatmullRomSpline is built
 * 3. The spline is rasterized onto a 2D tile grid (ROAD vs GRASS)
 * 4. Grid can be queried at runtime for terrain type under any world position
 *
 * Maps are saved/loaded as JSON arrays of control point coordinates.
 */
public class TrackGenerator {

    public static final int TILE_GRASS = 0;
    public static final int TILE_ROAD = 1;

    /** Grid dimensions in tiles. */
    public static final int GRID_WIDTH = 200;
    public static final int GRID_HEIGHT = 200;

    /** Size of one tile in Box2D meters. */
    public static final float TILE_SIZE = 0.5f;

    /** Half-width of the road in meters (total road width = 2 * this). */
    private static final float TRACK_HALF_WIDTH = 3f;

    /** Spline sampling step — smaller = more accurate rasterization. */
    private static final float SPLINE_STEP = 0.001f;

    // Grid origin: bottom-left corner in world coordinates
    private final float originX = -(GRID_WIDTH * TILE_SIZE) / 2f;  // -50
    private final float originY = -(GRID_HEIGHT * TILE_SIZE) / 2f; // -50

    private int[][] grid;
    private final Array<Vector2> manualPoints = new Array<>();
    private CatmullRomSpline<Vector2> spline;

    public TrackGenerator() {
        grid = new int[GRID_WIDTH][GRID_HEIGHT];
    }

    // ─── Point Management ──────────────────────────────────────────────

    /**
     * Adds a control point. When 4+ points exist, rebuilds spline and grid.
     */
    public void addPoint(float x, float y) {
        manualPoints.add(new Vector2(x, y));
        if (manualPoints.size >= 4) {
            rebuildSplineAndGrid();
        }
    }

    /**
     * Removes the last added control point and rebuilds if enough remain.
     */
    public void removeLastPoint() {
        if (manualPoints.size > 0) {
            manualPoints.removeIndex(manualPoints.size - 1);
            if (manualPoints.size >= 4) {
                rebuildSplineAndGrid();
            } else {
                spline = null;
                clearGrid();
            }
        }
    }

    // ─── Spline & Grid Rebuild ─────────────────────────────────────────

    private void rebuildSplineAndGrid() {
        clearGrid();

        // Build closed CatmullRom spline from control points
        Vector2[] points = manualPoints.toArray(Vector2.class);
        spline = new CatmullRomSpline<>(points, true);

        // Walk along the spline, marking nearby tiles as ROAD
        Vector2 pos = new Vector2();
        for (float t = 0; t <= 1f; t += SPLINE_STEP) {
            spline.valueAt(pos, t);
            markTilesAround(pos.x, pos.y);
        }
    }

    private void clearGrid() {
        for (int col = 0; col < GRID_WIDTH; col++) {
            for (int row = 0; row < GRID_HEIGHT; row++) {
                grid[col][row] = TILE_GRASS;
            }
        }
    }

    /**
     * Marks all tiles within TRACK_HALF_WIDTH of the given world position as ROAD.
     */
    private void markTilesAround(float worldX, float worldY) {
        int centerCol = worldToGridCol(worldX);
        int centerRow = worldToGridRow(worldY);
        int radiusTiles = (int) Math.ceil(TRACK_HALF_WIDTH / TILE_SIZE);

        for (int dx = -radiusTiles; dx <= radiusTiles; dx++) {
            for (int dy = -radiusTiles; dy <= radiusTiles; dy++) {
                int col = centerCol + dx;
                int row = centerRow + dy;
                if (col < 0 || col >= GRID_WIDTH || row < 0 || row >= GRID_HEIGHT) continue;

                float tileCenterX = gridToWorldX(col);
                float tileCenterY = gridToWorldY(row);
                float dist = Vector2.dst(worldX, worldY, tileCenterX, tileCenterY);

                if (dist <= TRACK_HALF_WIDTH) {
                    grid[col][row] = TILE_ROAD;
                }
            }
        }
    }

    // ─── World ↔ Grid Coordinate Conversion ────────────────────────────

    /**
     * Returns the tile type (TILE_ROAD or TILE_GRASS) at the given world position.
     * Positions outside the grid are treated as GRASS.
     */
    public int getTileAt(float worldX, float worldY) {
        int col = worldToGridCol(worldX);
        int row = worldToGridRow(worldY);
        if (col < 0 || col >= GRID_WIDTH || row < 0 || row >= GRID_HEIGHT) {
            return TILE_GRASS;
        }
        return grid[col][row];
    }

    private int worldToGridCol(float worldX) {
        return (int) ((worldX - originX) / TILE_SIZE);
    }

    private int worldToGridRow(float worldY) {
        return (int) ((worldY - originY) / TILE_SIZE);
    }

    private float gridToWorldX(int col) {
        return originX + col * TILE_SIZE + TILE_SIZE / 2f;
    }

    private float gridToWorldY(int row) {
        return originY + row * TILE_SIZE + TILE_SIZE / 2f;
    }

    // ─── Save / Load (LibGDX Json) ─────────────────────────────────────

    /**
     * Saves control points to a JSON file in the local storage directory.
     */
    public void saveMap(String fileName) {
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        FileHandle file = Gdx.files.local(fileName);
        file.writeString(json.prettyPrint(manualPoints), false);
        Gdx.app.log("TrackGenerator", "Map saved to: " + file.path() + " (" + manualPoints.size + " points)");
    }

    /**
     * Loads control points from a JSON file and rebuilds the track.
     *
     * @return true if load succeeded, false if file not found
     */
    @SuppressWarnings("unchecked")
    public boolean loadMap(String fileName) {
        FileHandle file = Gdx.files.local(fileName);
        if (!file.exists()) {
            Gdx.app.log("TrackGenerator", "No saved map found: " + fileName);
            return false;
        }

        Json json = new Json();
        Array<Vector2> loaded = json.fromJson(Array.class, Vector2.class, file);

        manualPoints.clear();
        manualPoints.addAll(loaded);

        if (manualPoints.size >= 4) {
            rebuildSplineAndGrid();
        }

        Gdx.app.log("TrackGenerator", "Map loaded: " + manualPoints.size + " points from " + fileName);
        return true;
    }

    // ─── Start Position ────────────────────────────────────────────────

    /**
     * Returns the spawn position (first control point, or origin if none).
     */
    public Vector2 getStartPosition() {
        if (manualPoints.size > 0) {
            return new Vector2(manualPoints.get(0));
        }
        return new Vector2(0, 0);
    }

    /**
     * Returns the spawn angle (direction toward second point, or 0).
     */
    public float getStartAngle() {
        if (manualPoints.size >= 2) {
            Vector2 dir = new Vector2(manualPoints.get(1)).sub(manualPoints.get(0));
            return dir.angleRad() - MathUtils.HALF_PI;
        }
        return 0;
    }

    // ─── Getters ───────────────────────────────────────────────────────

    public int[][] getGrid() { return grid; }
    public int getGridWidth() { return GRID_WIDTH; }
    public int getGridHeight() { return GRID_HEIGHT; }
    public float getTileSize() { return TILE_SIZE; }
    public float getOriginX() { return originX; }
    public float getOriginY() { return originY; }
    public CatmullRomSpline<Vector2> getSpline() { return spline; }
    public Array<Vector2> getManualPoints() { return manualPoints; }
}
