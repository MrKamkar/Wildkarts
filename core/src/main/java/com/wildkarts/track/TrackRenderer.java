package com.wildkarts.track;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.CatmullRomSpline;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

/**
 * Renders the track tile grid, finish line, and editor overlay (control points + spline curve).
 *
 * Tile rendering uses camera-based culling to only draw visible tiles,
 * avoiding the cost of rendering the full 200×200 grid every frame.
 */
public class TrackRenderer {

    private final ShapeRenderer shapeRenderer;

    // Terrain colors
    private static final Color GRASS_COLOR = new Color(0.18f, 0.45f, 0.15f, 1f);
    private static final Color ROAD_COLOR  = new Color(0.35f, 0.35f, 0.38f, 1f);

    // Curb colors (alternating red/white racing kerb pattern)
    private static final Color CURB_RED   = new Color(0.9f, 0.15f, 0.1f, 1f);
    private static final Color CURB_WHITE = new Color(0.95f, 0.95f, 0.95f, 1f);

    // Editor overlay colors
    private static final Color CONTROL_POINT_COLOR = new Color(1f, 0.4f, 0.2f, 1f);
    private static final Color SPLINE_COLOR = new Color(1f, 1f, 0.3f, 0.8f);
    private static final Color POINT_INDEX_COLOR = new Color(1f, 1f, 1f, 0.6f);

    // Finish line colors
    private static final Color FINISH_BLACK = new Color(0.1f, 0.1f, 0.1f, 1f);
    private static final Color FINISH_WHITE = new Color(1f, 1f, 1f, 1f);

    /** Number of checkered squares across the finish line. */
    private static final int FINISH_CHECKER_COUNT = 12;

    /** Thickness of the finish line strip in meters (along road direction). */
    private static final float FINISH_LINE_THICKNESS = 1.2f;

    public TrackRenderer() {
        shapeRenderer = new ShapeRenderer();
    }

    /**
     * Renders the tile grid (grass + road + curbs) and finish line.
     * Call BEFORE entity rendering so that tiles appear as background.
     */
    public void render(OrthographicCamera camera, TrackGenerator track) {
        shapeRenderer.setProjectionMatrix(camera.combined);
        renderTiles(camera, track);
        renderCurbs(track);
        renderFinishLine(track);
    }

    /**
     * Renders editor overlay: control points and spline curve.
     * Call AFTER entity rendering so overlay appears on top.
     */
    public void renderEditorOverlay(OrthographicCamera camera, TrackGenerator track) {
        shapeRenderer.setProjectionMatrix(camera.combined);
        renderGridBoundary(track);
        renderControlPoints(track);
        renderSpline(track);
    }

    // ─── Tile Grid ─────────────────────────────────────────────────────

    private void renderTiles(OrthographicCamera camera, TrackGenerator track) {
        int[][] grid = track.getGrid();
        if (grid == null) return;

        float tileSize = track.getTileSize();
        float originX = track.getOriginX();
        float originY = track.getOriginY();

        // Camera-based culling: only render visible tile range
        float halfW = camera.viewportWidth * camera.zoom / 2f;
        float halfH = camera.viewportHeight * camera.zoom / 2f;
        float camLeft   = camera.position.x - halfW;
        float camRight  = camera.position.x + halfW;
        float camBottom = camera.position.y - halfH;
        float camTop    = camera.position.y + halfH;

        int minCol = Math.max(0, (int) ((camLeft - originX) / tileSize) - 1);
        int maxCol = Math.min(track.getGridWidth() - 1, (int) ((camRight - originX) / tileSize) + 1);
        int minRow = Math.max(0, (int) ((camBottom - originY) / tileSize) - 1);
        int maxRow = Math.min(track.getGridHeight() - 1, (int) ((camTop - originY) / tileSize) + 1);

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (int col = minCol; col <= maxCol; col++) {
            for (int row = minRow; row <= maxRow; row++) {
                int tile = grid[col][row];
                if (tile == TrackGenerator.TILE_ROAD) {
                    shapeRenderer.setColor(ROAD_COLOR);
                } else {
                    shapeRenderer.setColor(GRASS_COLOR);
                }
                float x = originX + col * tileSize;
                float y = originY + row * tileSize;
                shapeRenderer.rect(x, y, tileSize, tileSize);
            }
        }
        shapeRenderer.end();
    }

    // ─── Curbs ─────────────────────────────────────────────────────────

    private void renderCurbs(TrackGenerator track) {
        Array<TrackGenerator.CurbSegment> curbs = track.getCurbs();
        if (curbs == null || curbs.size == 0) return;

        float curbWidth = 1.0f; // Width along the road
        float curbDepth = 0.6f; // Depth away from the road

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (TrackGenerator.CurbSegment seg : curbs) {
            shapeRenderer.setColor(seg.isRed ? CURB_RED : CURB_WHITE);
            // Draw rotated rect around its center
            shapeRenderer.rect(seg.x - curbWidth / 2f, seg.y - curbDepth / 2f,
                    curbWidth / 2f, curbDepth / 2f,
                    curbWidth, curbDepth, 1f, 1f, seg.rotationRad * com.badlogic.gdx.math.MathUtils.radiansToDegrees);
        }
        shapeRenderer.end();
    }

    // ─── Finish Line ──────────────────────────────────────────────────

    /**
     * Draws a checkered black & white finish line perpendicular to the road
     * at the first control point (start / finish position).
     */
    private void renderFinishLine(TrackGenerator track) {
        Array<Vector2> points = track.getManualPoints();
        if (points.size < 2) return;

        Vector2 start = points.get(0);
        Vector2 next = points.get(1);
        float halfWidth = track.getTrackHalfWidth();

        // Road direction at start and its perpendicular
        Vector2 dir = new Vector2(next).sub(start).nor();
        Vector2 normal = new Vector2(-dir.y, dir.x);

        // Along-road offset vectors (half thickness)
        float halfThick = FINISH_LINE_THICKNESS / 2f;
        Vector2 along = new Vector2(dir).scl(halfThick);

        // Size of each checker square
        float totalWidth = halfWidth * 2f;
        float checkerSize = totalWidth / FINISH_CHECKER_COUNT;

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        // Draw two rows of checkers (along road direction)
        for (int row = 0; row < 2; row++) {
            for (int i = 0; i < FINISH_CHECKER_COUNT; i++) {
                boolean isWhite = (i + row) % 2 == 0;
                shapeRenderer.setColor(isWhite ? FINISH_WHITE : FINISH_BLACK);

                // Position along the perpendicular (from -halfWidth to +halfWidth)
                float t = -halfWidth + i * checkerSize;
                float rowOffset = -halfThick + row * halfThick;

                // Corner of this checker square in world space
                float cx = start.x + normal.x * (t + checkerSize / 2f) + dir.x * (rowOffset + halfThick / 2f);
                float cy = start.y + normal.y * (t + checkerSize / 2f) + dir.y * (rowOffset + halfThick / 2f);

                // Draw rotated rectangle for this checker cell
                drawRotatedRect(cx, cy, checkerSize, halfThick, dir, normal);
            }
        }
        shapeRenderer.end();
    }

    /**
     * Draws a filled rotated rectangle given center, size, and orientation vectors.
     */
    private void drawRotatedRect(float cx, float cy, float w, float h,
                                  Vector2 dirAlong, Vector2 dirPerp) {
        float hw = w / 2f;
        float hh = h / 2f;

        // 4 corners
        float x1 = cx - dirPerp.x * hw - dirAlong.x * hh;
        float y1 = cy - dirPerp.y * hw - dirAlong.y * hh;
        float x2 = cx + dirPerp.x * hw - dirAlong.x * hh;
        float y2 = cy + dirPerp.y * hw - dirAlong.y * hh;
        float x3 = cx + dirPerp.x * hw + dirAlong.x * hh;
        float y3 = cy + dirPerp.y * hw + dirAlong.y * hh;
        float x4 = cx - dirPerp.x * hw + dirAlong.x * hh;
        float y4 = cy - dirPerp.y * hw + dirAlong.y * hh;

        shapeRenderer.triangle(x1, y1, x2, y2, x3, y3);
        shapeRenderer.triangle(x1, y1, x3, y3, x4, y4);
    }

    // ─── Control Points ────────────────────────────────────────────────

    private void renderControlPoints(TrackGenerator track) {
        Array<Vector2> points = track.getManualPoints();
        if (points.size == 0) return;

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < points.size; i++) {
            Vector2 p = points.get(i);

            // First point is green (start), rest are orange
            if (i == 0) {
                shapeRenderer.setColor(0.2f, 1f, 0.3f, 1f);
            } else {
                shapeRenderer.setColor(CONTROL_POINT_COLOR);
            }
            shapeRenderer.circle(p.x, p.y, 0.5f, 16);
        }
        shapeRenderer.end();

        // Draw lines connecting points in order
        if (points.size >= 2) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            shapeRenderer.setColor(POINT_INDEX_COLOR);
            for (int i = 0; i < points.size - 1; i++) {
                Vector2 a = points.get(i);
                Vector2 b = points.get(i + 1);
                shapeRenderer.line(a.x, a.y, b.x, b.y);
            }
            // Close the loop visually
            if (points.size >= 3) {
                Vector2 last = points.get(points.size - 1);
                Vector2 first = points.get(0);
                shapeRenderer.line(last.x, last.y, first.x, first.y);
            }
            shapeRenderer.end();
        }
    }

    // ─── Spline Curve ──────────────────────────────────────────────────

    private void renderSpline(TrackGenerator track) {
        CatmullRomSpline<Vector2> spline = track.getSpline();
        if (spline == null) return;

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(SPLINE_COLOR);

        Vector2 prev = new Vector2();
        Vector2 curr = new Vector2();
        spline.valueAt(prev, 0);

        float step = 0.005f;
        for (float t = step; t <= 1f; t += step) {
            spline.valueAt(curr, t);
            shapeRenderer.line(prev.x, prev.y, curr.x, curr.y);
            prev.set(curr);
        }
        // Close the loop
        spline.valueAt(curr, 0);
        shapeRenderer.line(prev.x, prev.y, curr.x, curr.y);

        shapeRenderer.end();
    }

    // ─── Map Boundaries ───────────────────────────────────────────────

    private void renderGridBoundary(TrackGenerator track) {
        float x = track.getOriginX();
        float y = track.getOriginY();
        float width = track.getGridWidth() * track.getTileSize();
        float height = track.getGridHeight() * track.getTileSize();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(Color.RED);
        shapeRenderer.rect(x, y, width, height);
        shapeRenderer.end();
    }

    public void dispose() {
        shapeRenderer.dispose();
    }
}
