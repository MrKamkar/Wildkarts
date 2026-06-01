package com.wildkarts.track;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.CatmullRomSpline;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

/**
 * Renders the track tile grid, finish line, and editor overlay (control points + spline curve).
 *
 * Tile rendering uses camera-based culling to only draw visible tiles,
 * avoiding the cost of rendering the full 200x200 grid every frame.
 *
 * Grass tiles are rendered as a tiled PNG texture (256x256, repeating pattern).
 * Road tiles are drawn on top using ShapeRenderer.
 */
public class TrackRenderer {

    private final ShapeRenderer shapeRenderer;
    private final SpriteBatch batch;
    private final Texture grassTexture;

    /** World-space size (meters) of one grass texture repetition. */
    private static final float GRASS_TILE_WORLD_SIZE = 4.0f;

    // Terrain colors
    private static final Color ROAD_COLOR  = new Color(0.35f, 0.35f, 0.38f, 1f);

    // Curb colors (alternating red/white racing kerb pattern)
    private static final Color CURB_RED   = new Color(0.9f, 0.15f, 0.1f, 1f);
    private static final Color CURB_WHITE = new Color(0.95f, 0.95f, 0.95f, 1f);

    // Editor overlay colors
    private static final Color CONTROL_POINT_COLOR = new Color(1f, 0.4f, 0.2f, 1f);
    private static final Color SPLINE_COLOR = new Color(1f, 1f, 0.3f, 0.8f);
    private static final Color POINT_INDEX_COLOR = new Color(1f, 1f, 1f, 0.6f);
    private static final Color CHECKPOINT_GATE_COLOR = new Color(1f, 0.95f, 0.2f, 0.55f);

    // Finish line colors
    private static final Color FINISH_BLACK = new Color(0.1f, 0.1f, 0.1f, 1f);
    private static final Color FINISH_WHITE = new Color(1f, 1f, 1f, 1f);

    /** Number of checkered squares across the finish line. */
    private static final int FINISH_CHECKER_COUNT = 12;

    /** Thickness of the finish line strip in meters (along road direction). */
    private static final float FINISH_LINE_THICKNESS = 1.2f;

    public TrackRenderer(Texture grassTexture) {
        shapeRenderer = new ShapeRenderer();
        batch = new SpriteBatch();
        this.grassTexture = grassTexture;
    }

    /**
     * Renders the tile grid (grass texture + road + curbs) and finish line.
     * Call BEFORE entity rendering so that tiles appear as background.
     */
    public void render(OrthographicCamera camera, TrackGenerator track) {
        shapeRenderer.setProjectionMatrix(camera.combined);
        batch.setProjectionMatrix(camera.combined);
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
        renderCheckpointGates(track);
    }

    // ─── Tile Grid ─────────────────────────────────────────────────────

    private void renderTiles(OrthographicCamera camera, TrackGenerator track) {
        float halfW = camera.viewportWidth * camera.zoom / 2f;
        float halfH = camera.viewportHeight * camera.zoom / 2f;
        float camLeft   = camera.position.x - halfW;
        float camRight  = camera.position.x + halfW;
        float camBottom = camera.position.y - halfH;
        float camTop    = camera.position.y + halfH;

        // --- Phase 1: tiled grass texture covering the entire visible area ---
        float visWidth  = camRight - camLeft;
        float visHeight = camTop - camBottom;

        float u  = camLeft   / GRASS_TILE_WORLD_SIZE;
        float v  = camBottom / GRASS_TILE_WORLD_SIZE;
        float u2 = camRight  / GRASS_TILE_WORLD_SIZE;
        float v2 = camTop    / GRASS_TILE_WORLD_SIZE;

        batch.begin();
        batch.draw(grassTexture, camLeft, camBottom, visWidth, visHeight, u, v, u2, v2);
        batch.end();

        // --- Phase 2: smooth spline-based road polygon ---
        renderRoadSpline(track);
    }

    /**
     * Draws the road as a smooth polygon strip along the Catmull-Rom spline.
     * Replaces per-tile rectangles for silky-smooth curved edges.
     */
    private void renderRoadSpline(TrackGenerator track) {
        CatmullRomSpline<Vector2> spline = track.getSpline();
        if (spline == null) return;

        float halfWidth = track.getTrackHalfWidth();
        if (halfWidth <= 0f) return;

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(ROAD_COLOR);

        Vector2 pos = new Vector2();
        Vector2 nextPos = new Vector2();
        Vector2 tangent = new Vector2();
        Vector2 nextTangent = new Vector2();

        float step = 0.002f;

        spline.valueAt(pos, 0f);
        spline.derivativeAt(tangent, 0f);

        for (float t = step; t <= 1f + step * 0.5f; t += step) {
            float tc = Math.min(t, 1f);
            spline.valueAt(nextPos, tc);
            spline.derivativeAt(nextTangent, tc);

            float nx = -tangent.y;
            float ny = tangent.x;
            float len = (float) Math.sqrt(nx * nx + ny * ny);
            if (len > 0.001f) { nx /= len; ny /= len; }

            float nnx = -nextTangent.y;
            float nny = nextTangent.x;
            float nlen = (float) Math.sqrt(nnx * nnx + nny * nny);
            if (nlen > 0.001f) { nnx /= nlen; nny /= nlen; }

            float l1x = pos.x + nx * halfWidth, l1y = pos.y + ny * halfWidth;
            float r1x = pos.x - nx * halfWidth, r1y = pos.y - ny * halfWidth;
            float l2x = nextPos.x + nnx * halfWidth, l2y = nextPos.y + nny * halfWidth;
            float r2x = nextPos.x - nnx * halfWidth, r2y = nextPos.y - nny * halfWidth;

            shapeRenderer.triangle(l1x, l1y, r1x, r1y, l2x, l2y);
            shapeRenderer.triangle(r1x, r1y, r2x, r2y, l2x, l2y);

            pos.set(nextPos);
            tangent.set(nextTangent);
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

    /**
     * Draws full-width checkpoint gate lines at every control point so the
     * editor shows the same detection geometry used during racing.
     */
    private void renderCheckpointGates(TrackGenerator track) {
        Array<Vector2> points = track.getManualPoints();
        if (points.size < 3) return;

        float halfWidth = track.getTrackHalfWidth();
        Vector2 center = new Vector2();
        Vector2 normal = new Vector2();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(CHECKPOINT_GATE_COLOR);
        for (int i = 0; i < points.size; i++) {
            track.getCheckpointGateFrame(i, center, normal);
            float x1 = center.x - normal.x * halfWidth;
            float y1 = center.y - normal.y * halfWidth;
            float x2 = center.x + normal.x * halfWidth;
            float y2 = center.y + normal.y * halfWidth;
            shapeRenderer.line(x1, y1, x2, y2);
        }
        shapeRenderer.end();
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
        batch.dispose();
    }
}
