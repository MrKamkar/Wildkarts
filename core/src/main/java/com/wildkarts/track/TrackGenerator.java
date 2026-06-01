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
 * Generator toru na podstawie ręcznych punktów kontrolnych i {@link CatmullRomSpline}.
 *
 * <p>Przebieg pracy:</p>
 * <ol>
 *   <li>Dodawanie punktów przez {@link #addPoint(float, float)} (kliknięcia w edytorze)</li>
 *   <li>Przy ≥4 punktach budowana jest zamknięta spline Catmull-Rom</li>
 *   <li>Spline rasteryzowana jest na siatkę 2D (DROGA vs TRAWA)</li>
 *   <li>Siatkę można odpytywać w runtime o typ terenu pod dowolną pozycją</li>
 * </ol>
 *
 * <p>Mapy zapisywane/ładowane są jako tablice JSON współrzędnych punktów kontrolnych.</p>
 */
public class TrackGenerator {

    /** Kafelek trawy (poza drogą). */
    public static final int TILE_GRASS = 0;

    /** Kafelek drogi. */
    public static final int TILE_ROAD = 1;

    /** Szerokość siatki w kafelkach. */
    private int gridWidth = 200;

    /** Wysokość siatki w kafelkach. */
    private int gridHeight = 200;

    /** Rozmiar jednego kafelka w metrach Box2D. */
    public static final float TILE_SIZE = 0.5f;

    /** Połowa szerokości jezdni w metrach (pełna szerokość = 2 × ta wartość). */
    private float trackHalfWidth = 6f;

    /** Dodatkowa tolerancja boczna bramki checkpointu poza połową szerokości drogi (m). */
    public static final float GATE_LATERAL_MARGIN = 0.75f;

    /** Głębokość wzdłuż drogi, w której auto uznawane jest za minięcie bramki (m). */
    public static final float GATE_DEPTH_ALONG_ROAD = 3.0f;

    /** Krok próbkowania spline — mniejszy = dokładniejsza rasteryzacja. */
    private static final float SPLINE_STEP = 0.001f;

    /** Szerokość pasa krawężnika w metrach. */
    private static final float CURB_WIDTH = 1.0f;

    /** Próg kąta zakrętu (stopnie) — krawężniki przy przekroczeniu. */
    private static final float CURB_ANGLE_THRESHOLD = 15f;

    /** Krok spline do wykrywania krawężników (rzadszy niż rasteryzacja drogi). */
    private static final float CURB_SPLINE_STEP = 0.003f;

    /** Początek siatki: lewy dolny róg we współrzędnych świata. */
    private float originX;
    private float originY;

    private int[][] grid;
    private final Array<Vector2> manualPoints = new Array<>();
    private CatmullRomSpline<Vector2> spline;

    /** Pojedynczy segment krawężnika (pozycja, obrót, kolor). */
    public static class CurbSegment {
        public float x, y;
        public float rotationRad;
        public boolean isRed;
    }

    private final Array<CurbSegment> curbs = new Array<>();

    /** Tworzy generator z domyślną siatką 200×200. */
    public TrackGenerator() {
        setGridSize(200, 200);
    }

    /**
     * Zmienia rozmiar siatki kafelków i przebudowuje tor, jeśli to możliwe.
     *
     * @param width  nowa szerokość siatki
     * @param height nowa wysokość siatki
     */
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

    /**
     * Dodaje punkt kontrolny. Przy ≥4 punktach przebudowuje spline i siatkę.
     *
     * @param x współrzędna X w metrach
     * @param y współrzędna Y w metrach
     */
    public void addPoint(float x, float y) {
        manualPoints.add(new Vector2(x, y));
        if (manualPoints.size >= 4) {
            rebuildSplineAndGrid();
        }
    }

    /**
     * Usuwa ostatni punkt kontrolny i przebudowuje tor, jeśli zostało wystarczająco punktów.
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

    /** Przebudowuje zamkniętą spline i rasteryzuje drogę oraz krawężniki. */
    private void rebuildSplineAndGrid() {
        clearGrid();

        Vector2[] points = manualPoints.toArray(Vector2.class);
        spline = new CatmullRomSpline<>(points, true);

        Vector2 pos = new Vector2();
        for (float t = 0; t <= 1f; t += SPLINE_STEP) {
            spline.valueAt(pos, t);
            markTilesAround(pos.x, pos.y);
        }

        generateCurbs();
    }

    /**
     * Próbkuje spline co stały dystans, wykrywa ostre zakręty
     * i rozkłada krawężniki wzdłuż krawędzi jezdni.
     */
    private void generateCurbs() {
        curbs.clear();
        if (spline == null) return;

        float stepSize = 0.5f;
        float distanceAccumulator = 0f;

        Vector2 currPos = new Vector2();
        Vector2 prevPos = new Vector2();

        spline.valueAt(prevPos, 0f);

        Array<Vector2> pathPoints = new Array<>();
        Array<Vector2> pathTangents = new Array<>();

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

        boolean[] rawCurb = new boolean[pathPoints.size];
        int lookahead = 6;
        float thresholdAngle = 5.0f;
        
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

        boolean[] hasCurb = new boolean[pathPoints.size];
        int dilation = 5;
        for (int i = 0; i < pathPoints.size; i++) {
            if (rawCurb[i]) {
                for (int j = -dilation; j <= dilation; j++) {
                    int idx = (i + j + pathPoints.size) % pathPoints.size;
                    hasCurb[idx] = true;
                }
            }
        }

        Array<Vector2> leftEdge = new Array<>();
        Array<Vector2> rightEdge = new Array<>();
        Array<Boolean> leftHasCurb = new Array<>();
        Array<Boolean> rightHasCurb = new Array<>();

        float curbDepth = 0.6f;
        float offset = trackHalfWidth + curbDepth / 2f;

        for (int i = 0; i < pathPoints.size; i++) {
            Vector2 p = pathPoints.get(i);
            Vector2 tDir = pathTangents.get(i);
            Vector2 normal = new Vector2(-tDir.y, tDir.x);

            leftEdge.add(new Vector2(p.x + normal.x * offset, p.y + normal.y * offset));
            rightEdge.add(new Vector2(p.x - normal.x * offset, p.y - normal.y * offset));
            leftHasCurb.add(hasCurb[i]);
            rightHasCurb.add(hasCurb[i]);
        }

        generateEdgeCurbs(leftEdge, leftHasCurb, pathPoints, pathTangents, curbs);
        generateEdgeCurbs(rightEdge, rightHasCurb, pathPoints, pathTangents, curbs);
    }

    /** Rozkłada segmenty krawężników wzdłuż jednej krawędzi jezdni. */
    private void generateEdgeCurbs(Array<Vector2> edgePoints, Array<Boolean> edgeHasCurb,
                                   Array<Vector2> centerPoints, Array<Vector2> centerTangents, 
                                   Array<CurbSegment> curbsList) {
        float curbLength = 1.0f;
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

            if (dir.dot(centerDir) < -0.2f)
                continue;

            float walkedOnSegment = 0f;
            while (walkedOnSegment + (curbLength - distAcc) <= segmentLen) {
                float move = curbLength - distAcc;
                walkedOnSegment += move;
                distAcc = 0f;
                
                Vector2 pos = new Vector2(prevP).add(new Vector2(dir).scl(walkedOnSegment));
                
                boolean valid = true;
                int checkRange = 15;
                for (int j = -checkRange; j <= checkRange; j++) {
                    int pIdx = (i + j + centerPoints.size) % centerPoints.size;
                    Vector2 cp = centerPoints.get(pIdx);
                    if (pos.dst(cp) < trackHalfWidth - 0.1f) {
                        valid = false;
                        break;
                    }
                }
                
                if (valid && edgeHasCurb.get(i)) {
                    CurbSegment seg = new CurbSegment();
                    seg.x = pos.x - dir.x * (curbLength / 2f);
                    seg.y = pos.y - dir.y * (curbLength / 2f);
                    seg.rotationRad = dir.angleRad();
                    seg.isRed = isRed;
                    curbsList.add(seg);
                    
                    colorCounter++;
                    if (colorCounter % 1 == 0)
                        isRed = !isRed;
                } else {
                    colorCounter = 0;
                }
            }
            distAcc += (segmentLen - walkedOnSegment);
        }
    }

    /** Ustawia wszystkie kafelki siatki na {@link #TILE_GRASS}. */
    private void clearGrid() {
        for (int col = 0; col < gridWidth; col++) {
            for (int row = 0; row < gridHeight; row++) {
                grid[col][row] = TILE_GRASS;
            }
        }
    }

    /**
     * Oznacza kafelki w promieniu {@code trackHalfWidth} od podanej pozycji jako {@link #TILE_ROAD}.
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

    /**
     * Zwraca typ kafelka ({@link #TILE_ROAD} lub {@link #TILE_GRASS}) w podanej pozycji świata.
     * Pozycje poza siatką traktowane są jako trawa.
     *
     * @param worldX pozycja X w metrach
     * @param worldY pozycja Y w metrach
     * @return {@link #TILE_ROAD} lub {@link #TILE_GRASS}
     */
    public int getTileAt(float worldX, float worldY) {
        int col = worldToGridCol(worldX);
        int row = worldToGridRow(worldY);
        if (col < 0 || col >= gridWidth || row < 0 || row >= gridHeight) {
            return TILE_GRASS;
        }
        return grid[col][row];
    }

    /** Konwertuje współrzędną X świata na kolumnę siatki. */
    private int worldToGridCol(float worldX) {
        return (int) ((worldX - originX) / TILE_SIZE);
    }

    /** Konwertuje współrzędną Y świata na wiersz siatki. */
    private int worldToGridRow(float worldY) {
        return (int) ((worldY - originY) / TILE_SIZE);
    }

    /** Zwraca środek kafelka (X) dla danej kolumny. */
    private float gridToWorldX(int col) {
        return originX + col * TILE_SIZE + TILE_SIZE / 2f;
    }

    /** Zwraca środek kafelka (Y) dla danego wiersza. */
    private float gridToWorldY(int row) {
        return originY + row * TILE_SIZE + TILE_SIZE / 2f;
    }

    /**
     * Zapisuje punkty kontrolne do pliku JSON w katalogu lokalnym.
     *
     * @param fileName nazwa pliku (ścieżka względem {@code Gdx.files.local})
     */
    public void saveMap(String fileName) {
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        FileHandle file = Gdx.files.local(fileName);
        file.writeString(json.prettyPrint(manualPoints), false);
        Gdx.app.log("TrackGenerator", "Map saved to: " + file.path() + " (" + manualPoints.size + " points)");
    }

    /**
     * Ładuje punkty kontrolne z pliku JSON i przebudowuje tor.
     *
     * @param fileName nazwa pliku
     * @return {@code true} gdy wczytanie się powiodło, {@code false} gdy brak pliku
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

    /**
     * Zwraca pozycję startową (pierwszy punkt kontrolny lub (0,0) gdy brak punktów).
     *
     * @return nowy {@link Vector2} z pozycją spawnu
     */
    public Vector2 getStartPosition() {
        if (manualPoints.size > 0) {
            return new Vector2(manualPoints.get(0));
        }
        return new Vector2(0, 0);
    }

    /**
     * Zwraca kąt startowy (kierunek do drugiego punktu lub 0).
     *
     * @return kąt w radianach
     */
    public float getStartAngle() {
        if (manualPoints.size >= 2) {
            Vector2 dir = new Vector2(manualPoints.get(1)).sub(manualPoints.get(0));
            return dir.angleRad() - MathUtils.HALF_PI;
        }
        return 0;
    }

    /** @return siatka kafelków (typy terenu) */
    public int[][] getGrid() { return grid; }

    /** @return szerokość siatki w kafelkach */
    public int getGridWidth() { return gridWidth; }

    /** @return wysokość siatki w kafelkach */
    public int getGridHeight() { return gridHeight; }

    /** @return rozmiar kafelka w metrach */
    public float getTileSize() { return TILE_SIZE; }

    /** @return współrzędna X początku siatki */
    public float getOriginX() { return originX; }

    /** @return współrzędna Y początku siatki */
    public float getOriginY() { return originY; }

    /** @return spline Catmull-Rom toru lub {@code null} gdy za mało punktów */
    public CatmullRomSpline<Vector2> getSpline() { return spline; }

    /** @return lista punktów kontrolnych */
    public Array<Vector2> getManualPoints() { return manualPoints; }

    /** @return połowa szerokości jezdni w metrach */
    public float getTrackHalfWidth() { return trackHalfWidth; }

    /** @return wygenerowane segmenty krawężników */
    public Array<CurbSegment> getCurbs() { return curbs; }

    /**
     * Sprawdza, czy punkt {@code (x, y)} leży w bramce checkpointu przy danym punkcie kontrolnym
     * (pasek prostopadły do drogi na pełną szerokość jezdni).
     *
     * @param pointIndex indeks punktu kontrolnego
     * @param x          pozycja X auta
     * @param y          pozycja Y auta
     * @return {@code true} gdy auto jest w strefie bramki
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

        float nx = -ty;
        float ny = tx;

        float relX = x - center.x;
        float relY = y - center.y;
        float lateral = relX * nx + relY * ny;
        float along = relX * tx + relY * ty;

        return Math.abs(lateral) <= trackHalfWidth + GATE_LATERAL_MARGIN
                && Math.abs(along) <= GATE_DEPTH_ALONG_ROAD;
    }

    /**
     * Zwraca środek bramki checkpointu i wektor normalny (prostopadły do drogi).
     *
     * @param pointIndex indeks punktu kontrolnego
     * @param centerOut  wyjście: środek bramki
     * @param normalOut  wyjście: znormalizowany wektor normalny
     */
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

    /**
     * Eksportuje dane toru do synchronizacji sieciowej.
     *
     * @return pakiet {@link TrackData}
     */
    public TrackData exportData() {
        return new TrackData(new Array<>(manualPoints), gridWidth, gridHeight, trackHalfWidth);
    }

    /**
     * Importuje dane toru z pakietu sieciowego i przebudowuje siatkę.
     *
     * @param data dane toru z serwera
     */
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
