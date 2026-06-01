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
    private int gridWidth = 200;
    private int gridHeight = 200;

    /** Size of one tile in Box2D meters. */
    public static final float TILE_SIZE = 0.5f;

    /** Half-width of the road in meters (total road width = 2 * this). */
    private float trackHalfWidth = 6f;

    /** Extra lateral tolerance beyond road half-width for checkpoint gates (meters). */
    public static final float GATE_LATERAL_MARGIN = 0.75f;

    /** Depth along the road direction within which a car counts as crossing a gate (meters). */
    public static final float GATE_DEPTH_ALONG_ROAD = 3.0f;

    /** Spline sampling step — smaller = more accurate rasterization. */
    private static final float SPLINE_STEP = 0.001f;

    /** Width of curb strip in meters. */
    private static final float CURB_WIDTH = 1.0f;

    /** Curvature angle threshold (degrees) — curbs placed when exceeded. */
    private static final float CURB_ANGLE_THRESHOLD = 15f;

    /** Spline step for curb detection (coarser than road rasterization). */
    private static final float CURB_SPLINE_STEP = 0.003f;

    // Grid origin: bottom-left corner in world coordinates
    private float originX;
    private float originY;

    private int[][] grid;
    private final Array<Vector2> manualPoints = new Array<>();
    private CatmullRomSpline<Vector2> spline;

    public static class CurbSegment {
        public float x, y;
        public float rotationRad;
        public boolean isRed;
    }

    private final Array<CurbSegment> curbs = new Array<>();

    public TrackGenerator() {
        setGridSize(200, 200);
    }

    public void setGridSize(int width, int height) {
        this.gridWidth = width;
        this.gridHeight = height;
        this.originX = -(gridWidth * TILE_SIZE) / 2f;
        this.originY = -(gridHeight * TILE_SIZE) / 2f;
        this.grid = new int[gridWidth][gridHeight];
        if (manualPoints.size >= 4) {
            rebuildSplineAndGrid();
        } else {
            clearGrid();
        }
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
                curbs.clear();
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

        // Second pass: detect sharp turns and place curbs on road edges
        generateCurbs();
    }

    /**
     * Walks the spline evenly by distance, evaluates curvature, and generates
     * independent edge paths. It then places uniform curbs exactly on the edges.
     */
    private void generateCurbs() {
        curbs.clear();
        if (spline == null) return;

        float stepSize = 0.5f; // Sample the center spline every 0.5 meters
        float distanceAccumulator = 0f;

        Vector2 currPos = new Vector2();
        Vector2 prevPos = new Vector2();

        spline.valueAt(prevPos, 0f);

        Array<Vector2> pathPoints = new Array<>();
        Array<Vector2> pathTangents = new Array<>();

        // 1. Sample center spline evenly by distance
        for (float t = 0.001f; t <= 1f; t += 0.001f) {
            spline.valueAt(currPos, t);
            float dist = prevPos.dst(currPos);
            distanceAccumulator += dist;
            prevPos.set(currPos);

            if (distanceAccumulator >= stepSize) {
                distanceAccumulator -= stepSize;
                
                Vector2 p = new Vector2(currPos);
                Vector2 tg = new Vector2();
                spline.derivativeAt(tg, t);
                tg.nor();
                
                pathPoints.add(p);
                pathTangents.add(tg);
            }
        }

        if (pathPoints.size == 0) return;

        // 2. Identify sharp curves with a lookahead window
        boolean[] rawCurb = new boolean[pathPoints.size];
        int lookahead = 6; // Compare with tangent 3.0m ahead (stepSize=0.5 * 6)
        float thresholdAngle = 5.0f; // Must bend at least 5 degrees over 3 meters
        
        for (int i = 0; i < pathPoints.size; i++) {
            int nextIdx = (i + lookahead) % pathPoints.size;
            Vector2 tg1 = pathTangents.get(i);
            Vector2 tg2 = pathTangents.get(nextIdx);
            
            float dot = MathUtils.clamp(tg1.dot(tg2), -1f, 1f);
            float angleDeg = (float) Math.toDegrees(Math.acos(dot));
            
            if (angleDeg > thresholdAngle) {
                rawCurb[i] = true;
            }
        }

        // Dilate curb zones to bridge small gaps and extend them slightly before/after turns
        boolean[] hasCurb = new boolean[pathPoints.size];
        int dilation = 5; // Expand by 2.5 meters in both directions
        for (int i = 0; i < pathPoints.size; i++) {
            if (rawCurb[i]) {
                for (int j = -dilation; j <= dilation; j++) {
                    int idx = (i + j + pathPoints.size) % pathPoints.size;
                    hasCurb[idx] = true;
                }
            }
        }

        // 3. Generate independent Left and Right Edge paths
        Array<Vector2> leftEdge = new Array<>();
        Array<Vector2> rightEdge = new Array<>();
        Array<Boolean> leftHasCurb = new Array<>();
        Array<Boolean> rightHasCurb = new Array<>();

        float curbDepth = 0.6f; // Depth of curb away from road edge
        float offset = trackHalfWidth + curbDepth / 2f; // Offset to center of the curb

        for (int i = 0; i < pathPoints.size; i++) {
            Vector2 p = pathPoints.get(i);
            Vector2 tDir = pathTangents.get(i);
            Vector2 normal = new Vector2(-tDir.y, tDir.x);

            leftEdge.add(new Vector2(p.x + normal.x * offset, p.y + normal.y * offset));
            rightEdge.add(new Vector2(p.x - normal.x * offset, p.y - normal.y * offset));
            leftHasCurb.add(hasCurb[i]);
            rightHasCurb.add(hasCurb[i]);
        }

        // 4. Generate evenly spaced curbs along each edge path independently
        generateEdgeCurbs(leftEdge, leftHasCurb, pathPoints, pathTangents, curbs);
        generateEdgeCurbs(rightEdge, rightHasCurb, pathPoints, pathTangents, curbs);
    }

    private void generateEdgeCurbs(Array<Vector2> edgePoints, Array<Boolean> edgeHasCurb, 
                                   Array<Vector2> centerPoints, Array<Vector2> centerTangents, 
                                   Array<CurbSegment> curbsList) {
        float curbLength = 1.0f; // Fixed length of curb segment
        float distAcc = 0f;
        boolean isRed = true;
        int colorCounter = 0;

        for (int i = 1; i < edgePoints.size; i++) {
            Vector2 prevP = edgePoints.get(i - 1);
            Vector2 currP = edgePoints.get(i);
            float segmentLen = prevP.dst(currP);
            
            if (segmentLen == 0) continue;
            
            Vector2 dir = new Vector2(currP).sub(prevP).nor();
            Vector2 centerDir = centerTangents.get(i);

            // 1. Prevent tangled loops: If the edge path goes backward relative to the road, skip it.
            if (dir.dot(centerDir) < -0.2f) {
                continue;
            }

            float walkedOnSegment = 0f;
            while (walkedOnSegment + (curbLength - distAcc) <= segmentLen) {
                float move = curbLength - distAcc;
                walkedOnSegment += move;
                distAcc = 0f; // We completed a curb length
                
                Vector2 pos = new Vector2(prevP).add(new Vector2(dir).scl(walkedOnSegment));
                
                // 2. Distance check: Ensure the curb doesn't intersect the asphalt.
                // We check nearby center points. If any is closer than the track width, the curb is biting into the road.
                boolean valid = true;
                int checkRange = 15; // Local window to check for intersections
                for (int j = -checkRange; j <= checkRange; j++) {
                    int pIdx = (i + j + centerPoints.size) % centerPoints.size;
                    Vector2 cp = centerPoints.get(pIdx);
                    // 0.1f epsilon for floating point inaccuracies
                    if (pos.dst(cp) < trackHalfWidth - 0.1f) {
                        valid = false;
                        break;
                    }
                }
                
                // If the end of the curb falls within a curb zone and is valid, place it
                if (valid && edgeHasCurb.get(i)) {
                    CurbSegment seg = new CurbSegment();
                    // Move position to the center of this 1.0m segment
                    seg.x = pos.x - dir.x * (curbLength / 2f);
                    seg.y = pos.y - dir.y * (curbLength / 2f);
                    seg.rotationRad = dir.angleRad();
                    seg.isRed = isRed;
                    curbsList.add(seg);
                    
                    colorCounter++;
                    if (colorCounter % 1 == 0) { // Toggle every 1 segment
                        isRed = !isRed;
                    }
                } else {
                    colorCounter = 0;
                }
            }
            distAcc += (segmentLen - walkedOnSegment);
        }
    }

    private void clearGrid() {
        for (int col = 0; col < gridWidth; col++) {
            for (int row = 0; row < gridHeight; row++) {
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
        int radiusTiles = (int) Math.ceil(trackHalfWidth / TILE_SIZE);

        for (int dx = -radiusTiles; dx <= radiusTiles; dx++) {
            for (int dy = -radiusTiles; dy <= radiusTiles; dy++) {
                int col = centerCol + dx;
                int row = centerRow + dy;
                if (col < 0 || col >= gridWidth || row < 0 || row >= gridHeight) continue;

                float tileCenterX = gridToWorldX(col);
                float tileCenterY = gridToWorldY(row);
                float dist = Vector2.dst(worldX, worldY, tileCenterX, tileCenterY);

                if (dist <= trackHalfWidth) {
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
        if (col < 0 || col >= gridWidth || row < 0 || row >= gridHeight) {
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
    public int getGridWidth() { return gridWidth; }
    public int getGridHeight() { return gridHeight; }
    public float getTileSize() { return TILE_SIZE; }
    public float getOriginX() { return originX; }
    public float getOriginY() { return originY; }
    public CatmullRomSpline<Vector2> getSpline() { return spline; }
    public Array<Vector2> getManualPoints() { return manualPoints; }
    public float getTrackHalfWidth() { return trackHalfWidth; }
    public Array<CurbSegment> getCurbs() { return curbs; }

    /**
     * Returns true when {@code (x, y)} is within the checkpoint gate at the given
     * control point — a strip perpendicular to the track spanning the full road width.
     */
    public boolean isWithinCheckpointGate(int pointIndex, float x, float y) {
        int n = manualPoints.size;
        if (n < 3 || pointIndex < 0 || pointIndex >= n) return false;

        Vector2 center = manualPoints.get(pointIndex);
        Vector2 prev = manualPoints.get((pointIndex - 1 + n) % n);
        Vector2 next = manualPoints.get((pointIndex + 1) % n);

        float tx = next.x - prev.x;
        float ty = next.y - prev.y;
        float tLen = (float) Math.sqrt(tx * tx + ty * ty);
        if (tLen < 0.001f) return false;
        tx /= tLen;
        ty /= tLen;

        // Gate normal (perpendicular to road direction through this point)
        float nx = -ty;
        float ny = tx;

        float relX = x - center.x;
        float relY = y - center.y;
        float lateral = relX * nx + relY * ny;
        float along = relX * tx + relY * ty;

        return Math.abs(lateral) <= trackHalfWidth + GATE_LATERAL_MARGIN
                && Math.abs(along) <= GATE_DEPTH_ALONG_ROAD;
    }

    /** Gate center and unit normal for rendering or debug (normal points across track). */
    public void getCheckpointGateFrame(int pointIndex, Vector2 centerOut, Vector2 normalOut) {
        int n = manualPoints.size;
        if (n < 3 || pointIndex < 0 || pointIndex >= n) return;

        Vector2 center = manualPoints.get(pointIndex);
        Vector2 prev = manualPoints.get((pointIndex - 1 + n) % n);
        Vector2 next = manualPoints.get((pointIndex + 1) % n);

        float tx = next.x - prev.x;
        float ty = next.y - prev.y;
        float tLen = (float) Math.sqrt(tx * tx + ty * ty);
        if (tLen < 0.001f) return;
        tx /= tLen;
        ty /= tLen;

        centerOut.set(center);
        normalOut.set(-ty, tx);
    }

    // ─── Data Sync ─────────────────────────────────────────────────────

    public TrackData exportData() {
        return new TrackData(new Array<>(manualPoints), gridWidth, gridHeight, trackHalfWidth);
    }

    public void importData(TrackData data) {
        this.gridWidth = data.gridWidth;
        this.gridHeight = data.gridHeight;
        this.trackHalfWidth = data.trackHalfWidth;
        this.originX = -(gridWidth * TILE_SIZE) / 2f;
        this.originY = -(gridHeight * TILE_SIZE) / 2f;
        this.grid = new int[gridWidth][gridHeight];
        
        manualPoints.clear();
        manualPoints.addAll(data.points);
        
        if (manualPoints.size >= 4) {
            rebuildSplineAndGrid();
        } else {
            clearGrid();
            curbs.clear();
        }
    }
}
