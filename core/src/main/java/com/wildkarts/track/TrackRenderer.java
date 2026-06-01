package com.wildkarts.track;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.CatmullRomSpline;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

/**
 * Renderuje siatkę kafelków toru, linię mety i nakładkę edytora (punkty kontrolne + spline).
 *
 * <p>Render kafelków używa culling'u opartego na kamerze — rysowane są tylko widoczne obszary.
 * Trawa to powtarzalna tekstura PNG; droga rysowana jest jako polygon wzdłuż spline.</p>
 */
public class TrackRenderer {

    private final ShapeRenderer shapeRenderer;
    private final SpriteBatch batch;
    private final Texture grassTexture;

    /** Rozmiar jednego powtórzenia tekstury trawy w metrach świata. */
    private static final float GRASS_TILE_WORLD_SIZE = 4.0f;

    private static final Color ROAD_COLOR  = new Color(0.35f, 0.35f, 0.38f, 1f);

    private static final Color CURB_RED   = new Color(0.9f, 0.15f, 0.1f, 1f);
    private static final Color CURB_WHITE = new Color(0.95f, 0.95f, 0.95f, 1f);

    private static final Color CONTROL_POINT_COLOR = new Color(1f, 0.4f, 0.2f, 1f);
    private static final Color SPLINE_COLOR = new Color(1f, 1f, 0.3f, 0.8f);
    private static final Color POINT_INDEX_COLOR = new Color(1f, 1f, 1f, 0.6f);
    private static final Color CHECKPOINT_GATE_COLOR = new Color(1f, 0.95f, 0.2f, 0.55f);

    private static final Color FINISH_BLACK = new Color(0.1f, 0.1f, 0.1f, 1f);
    private static final Color FINISH_WHITE = new Color(1f, 1f, 1f, 1f);

    /** Liczba pól szachownicy w poprzek linii mety. */
    private static final int FINISH_CHECKER_COUNT = 12;

    /** Grubość pasa linii mety w metrach (wzdłuż drogi). */
    private static final float FINISH_LINE_THICKNESS = 1.2f;

    /**
     * Tworzy renderer toru z teksturą trawy.
     *
     * @param grassTexture powtarzalna tekstura tła trawy
     */
    public TrackRenderer(Texture grassTexture) {
        shapeRenderer = new ShapeRenderer();
        batch = new SpriteBatch();
        this.grassTexture = grassTexture;
    }

    /**
     * Renderuje tło toru (trawa + droga + krawężniki) i linię mety.
     * Wywołać PRZED renderowaniem encji, aby kafelki były w tle.
     *
     * @param camera kamera świata
     * @param track  generator toru
     */
    public void render(OrthographicCamera camera, TrackGenerator track) {
        shapeRenderer.setProjectionMatrix(camera.combined);
        batch.setProjectionMatrix(camera.combined);
        renderTiles(camera, track);
        renderCurbs(track);
        renderFinishLine(track);
    }

    /**
     * Renderuje nakładkę edytora: punkty kontrolne, spline i bramki checkpointów.
     * Wywołać PO renderowaniu encji, aby nakładka była na wierzchu.
     *
     * @param camera kamera świata
     * @param track  generator toru
     */
    public void renderEditorOverlay(OrthographicCamera camera, TrackGenerator track) {
        shapeRenderer.setProjectionMatrix(camera.combined);
        renderGridBoundary(track);
        renderControlPoints(track);
        renderSpline(track);
        renderCheckpointGates(track);
    }

    /** Rysuje teksturę trawy w widocznym obszarze kamery, potem polygon drogi. */
    private void renderTiles(OrthographicCamera camera, TrackGenerator track) {
        float halfW = camera.viewportWidth * camera.zoom / 2f;
        float halfH = camera.viewportHeight * camera.zoom / 2f;
        float camLeft   = camera.position.x - halfW;
        float camRight  = camera.position.x + halfW;
        float camBottom = camera.position.y - halfH;
        float camTop    = camera.position.y + halfH;

        float visWidth  = camRight - camLeft;
        float visHeight = camTop - camBottom;

        float u  = camLeft   / GRASS_TILE_WORLD_SIZE;
        float v  = camBottom / GRASS_TILE_WORLD_SIZE;
        float u2 = camRight  / GRASS_TILE_WORLD_SIZE;
        float v2 = camTop    / GRASS_TILE_WORLD_SIZE;

        batch.begin();
        batch.draw(grassTexture, camLeft, camBottom, visWidth, visHeight, u, v, u2, v2);
        batch.end();

        renderRoadSpline(track);
    }

    /** Rysuje drogę jako płaski pasek polygonów wzdłuż spline Catmull-Rom. */
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

    /** Rysuje krawężniki wzdłuż krawędzi jezdni. */
    private void renderCurbs(TrackGenerator track) {
        Array<TrackGenerator.CurbSegment> curbs = track.getCurbs();
        if (curbs == null || curbs.size == 0) return;

        float curbWidth = 1.0f;
        float curbDepth = 0.6f;

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (TrackGenerator.CurbSegment seg : curbs) {
            shapeRenderer.setColor(seg.isRed ? CURB_RED : CURB_WHITE);
            shapeRenderer.rect(seg.x - curbWidth / 2f, seg.y - curbDepth / 2f,
                    curbWidth / 2f, curbDepth / 2f,
                    curbWidth, curbDepth, 1f, 1f, seg.rotationRad * MathUtils.radiansToDegrees);
        }
        shapeRenderer.end();
    }

    /** Rysuje szachownicę linii mety prostopadle do drogi przy pierwszym punkcie kontrolnym. */
    private void renderFinishLine(TrackGenerator track) {
        Array<Vector2> points = track.getManualPoints();
        if (points.size < 2) return;

        Vector2 start = points.get(0);
        Vector2 next = points.get(1);
        float halfWidth = track.getTrackHalfWidth();

        Vector2 dir = new Vector2(next).sub(start).nor();
        Vector2 normal = new Vector2(-dir.y, dir.x);

        float halfThick = FINISH_LINE_THICKNESS / 2f;

        float totalWidth = halfWidth * 2f;
        float checkerSize = totalWidth / FINISH_CHECKER_COUNT;

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (int row = 0; row < 2; row++) {
            for (int i = 0; i < FINISH_CHECKER_COUNT; i++) {
                boolean isWhite = (i + row) % 2 == 0;
                shapeRenderer.setColor(isWhite ? FINISH_WHITE : FINISH_BLACK);

                float t = -halfWidth + i * checkerSize;
                float rowOffset = -halfThick + row * halfThick;

                float cx = start.x + normal.x * (t + checkerSize / 2f) + dir.x * (rowOffset + halfThick / 2f);
                float cy = start.y + normal.y * (t + checkerSize / 2f) + dir.y * (rowOffset + halfThick / 2f);

                drawRotatedRect(cx, cy, checkerSize, halfThick, dir, normal);
            }
        }
        shapeRenderer.end();
    }

    /**
     * Rysuje wypełniony obrócony prostokąt o podanym środku i wektorach orientacji.
     */
    private void drawRotatedRect(float cx, float cy, float w, float h,
                                  Vector2 dirAlong, Vector2 dirPerp) {
        float hw = w / 2f;
        float hh = h / 2f;

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

    /** Rysuje punkty kontrolne i linie łączące je w edytorze. */
    private void renderControlPoints(TrackGenerator track) {
        Array<Vector2> points = track.getManualPoints();
        if (points.size == 0) return;

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < points.size; i++) {
            Vector2 p = points.get(i);

            if (i == 0)
                shapeRenderer.setColor(0.2f, 1f, 0.3f, 1f);
            else
                shapeRenderer.setColor(CONTROL_POINT_COLOR);
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

    /** Rysuje bramki checkpointów na pełną szerokość drogi w edytorze. */
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

    /** Rysuje zamkniętą krzywą spline toru. */
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
        spline.valueAt(curr, 0);
        shapeRenderer.line(prev.x, prev.y, curr.x, curr.y);

        shapeRenderer.end();
    }

    /** Rysuje czerwoną ramkę granic mapy. */
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

    /** Zwalnia zasoby {@link ShapeRenderer} i {@link SpriteBatch}. */
    public void dispose() {
        shapeRenderer.dispose();
        batch.dispose();
    }
}
