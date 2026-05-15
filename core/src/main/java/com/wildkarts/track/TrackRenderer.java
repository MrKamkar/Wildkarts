package com.wildkarts.track;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.CatmullRomSpline;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

/**
 * Renders the track tile grid and editor overlay (control points + spline curve).
 *
 * Tile rendering uses camera-based culling to only draw visible tiles,
 * avoiding the cost of rendering the full 200×200 grid every frame.
 */
public class TrackRenderer {

    private final ShapeRenderer shapeRenderer;

    // Terrain colors
    private static final Color GRASS_COLOR = new Color(0.18f, 0.45f, 0.15f, 1f);
    private static final Color ROAD_COLOR  = new Color(0.35f, 0.35f, 0.38f, 1f);

    // Editor overlay colors
    private static final Color CONTROL_POINT_COLOR = new Color(1f, 0.4f, 0.2f, 1f);
    private static final Color SPLINE_COLOR = new Color(1f, 1f, 0.3f, 0.8f);
    private static final Color POINT_INDEX_COLOR = new Color(1f, 1f, 1f, 0.6f);

    public TrackRenderer() {
        shapeRenderer = new ShapeRenderer();
    }

    /**
     * Renders the tile grid (grass + road). Call BEFORE entity rendering
     * so that tiles appear as background.
     */
    public void render(OrthographicCamera camera, TrackGenerator track) {
        shapeRenderer.setProjectionMatrix(camera.combined);
        renderTiles(camera, track);
    }

    /**
     * Renders editor overlay: control points and spline curve.
     * Call AFTER entity rendering so overlay appears on top.
     */
    public void renderEditorOverlay(OrthographicCamera camera, TrackGenerator track) {
        shapeRenderer.setProjectionMatrix(camera.combined);
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
        float camLeft   = camera.position.x - camera.viewportWidth / 2f;
        float camRight  = camera.position.x + camera.viewportWidth / 2f;
        float camBottom = camera.position.y - camera.viewportHeight / 2f;
        float camTop    = camera.position.y + camera.viewportHeight / 2f;

        int minCol = Math.max(0, (int) ((camLeft - originX) / tileSize) - 1);
        int maxCol = Math.min(track.getGridWidth() - 1, (int) ((camRight - originX) / tileSize) + 1);
        int minRow = Math.max(0, (int) ((camBottom - originY) / tileSize) - 1);
        int maxRow = Math.min(track.getGridHeight() - 1, (int) ((camTop - originY) / tileSize) + 1);

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (int col = minCol; col <= maxCol; col++) {
            for (int row = minRow; row <= maxRow; row++) {
                if (grid[col][row] == TrackGenerator.TILE_ROAD) {
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

    public void dispose() {
        shapeRenderer.dispose();
    }
}
