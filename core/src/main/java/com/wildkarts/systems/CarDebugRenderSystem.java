package com.wildkarts.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.wildkarts.components.CarComponent;
import com.wildkarts.components.InputComponent;

/**
 * Debug Pacejki — krzywa i żółta kropka używają tego samego pipeline'u co {@link PacejkaTireModel}.
 */
public class CarDebugRenderSystem extends IteratingSystem {

    private final ComponentMapper<CarComponent> carMapper = ComponentMapper.getFor(CarComponent.class);
    private final Viewport hudViewport;
    private final SpriteBatch batch;
    private final BitmapFont font;
    private final ShapeRenderer shapeRenderer;
    private final StringBuilder hudText = new StringBuilder(320);

    private Entity trackedEntity;

    /**
     * Tworzy system overlay debug powiązany z kamerą świata.
     *
     * @param worldCamera kamera ortograficzna świata (używana pośrednio przez viewport HUD)
     */
    public CarDebugRenderSystem(OrthographicCamera worldCamera) {
        super(Family.all(InputComponent.class, CarComponent.class).get());
        this.hudViewport = new ScreenViewport();
        this.batch = new SpriteBatch();
        this.font = new BitmapFont();
        this.shapeRenderer = new ShapeRenderer();
        font.setColor(Color.WHITE);
        font.getData().setScale(1.1f);
    }

    /** Rysuje HUD tekstowy i opcjonalnie krzywe Pacejki dla śledzonego auta. */
    @Override
    public void update(float deltaTime) {
        trackedEntity = null;
        super.update(deltaTime);

        if (trackedEntity == null) return;
        CarComponent car = carMapper.get(trackedEntity);
        if (car == null || !car.debugOverlayEnabled) return;

        hudViewport.apply();
        drawHud(car);
        if (car.debugDrawPacejkaCurve)
            drawPacejkaCurves(car);
    }

    /** Zapamiętuje ostatnio przetworzoną encję (lokalne auto gracza). */
    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        trackedEntity = entity;
    }

    /** Rysuje panel tekstowy z parametrami poślizgu i sił bocznych. */
    private void drawHud(CarComponent car) {
        PacejkaTireModel.AxleRuntime frontRt = car.frontTireRuntime;
        PacejkaTireModel.AxleRuntime rearRt = car.rearTireRuntime;

        hudText.setLength(0);
        hudText.append("--- Pacejka / Drift Debug ---\n");
        hudText.append(String.format("Speed: %.1f m/s  (fwd %.1f)\n", car.displaySpeed, car.displayForwardSpeed));
        hudText.append(String.format("Slip raw  F:%.1f  R:%.1f deg\n",
                car.frontSlipAngle * MathUtils.radiansToDegrees,
                car.rearSlipAngle * MathUtils.radiansToDegrees));
        hudText.append(String.format("Slip eff  F:%.1f  R:%.1f deg\n",
                car.frontSlipAngleEffective * MathUtils.radiansToDegrees,
                car.rearSlipAngleEffective * MathUtils.radiansToDegrees));
        hudText.append(String.format("Fy front: %.0f N  |  rear: %.0f N\n", car.frontLateralForce, car.rearLateralForce));
        hudText.append(String.format("Grip front: %.0f%%  |  rear: %.0f%%\n", car.frontGripPercent, car.rearGripPercent));
        hudText.append(String.format("Normal load  F:%.0f N  R:%.0f N\n", car.frontNormalLoadN, car.rearNormalLoadN));
        hudText.append(String.format("Mu: %.2f  |  loadScale F:%.2f R:%.2f\n",
                car.surfaceMu, frontRt.loadScale, rearRt.loadScale));
        hudText.append(String.format("Fade lowSpd:%.2f grip:%.2f slide F:%.2f R:%.2f\n",
                frontRt.lowSpeedFade, frontRt.gripScale, frontRt.slideMult, rearRt.slideMult));
        hudText.append(String.format("Steer: %.1f deg  |  forceScale: %.2f\n",
                car.currentSteeringAngle, car.physicsForceScale));
        hudText.append(String.format("Peak slip R: %.2f deg  past peak: %s  skid: %s\n",
                car.rearPeakSlipAngle * MathUtils.radiansToDegrees,
                Math.abs(car.rearSlipAngleEffective) > car.rearPeakSlipAngle * car.rearSkidPastPeakMultiplier
                        ? "YES" : "no",
                car.rearSkidActive ? "YES" : "no"));

        batch.setProjectionMatrix(hudViewport.getCamera().combined);
        batch.begin();
        font.draw(batch, hudText, car.debugHudX, Gdx.graphics.getHeight() - car.debugHudY);
        batch.end();
    }

    private float[] dotPositions = new float[4];

    /** Rysuje dwie krzywe Pacejki (przód/tył) i pozycje kropek bieżącego poślizgu. */
    private void drawPacejkaCurves(CarComponent car) {
        float x = car.debugHudX;
        float y = Gdx.graphics.getHeight() - car.debugHudY - 290f;
        float w = 130f;
        float h = 70f;
        float alphaMax = car.debugPacejkaCurveAlphaMax;

        PacejkaTireModel.AxleConfig frontCfg = buildConfig(car, true);
        PacejkaTireModel.AxleConfig rearCfg = buildConfig(car, false);

        shapeRenderer.setProjectionMatrix(hudViewport.getCamera().combined);

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(0.4f, 0.8f, 1f, 0.9f);
        drawPacejkaCurve(x, y, w, h, alphaMax, frontCfg, car, car.frontTireRuntime, car.frontSlipAngleEffective, 0);
        shapeRenderer.setColor(1f, 0.45f, 0.3f, 0.9f);
        drawPacejkaCurve(x + w + 16f, y, w, h, alphaMax, rearCfg, car, car.rearTireRuntime, car.rearSlipAngleEffective, 1);
        shapeRenderer.end();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(1f, 1f, 0.2f, 1f);
        shapeRenderer.circle(dotPositions[0], dotPositions[1], 5f);
        shapeRenderer.circle(dotPositions[2], dotPositions[3], 5f);
        shapeRenderer.end();
    }

    /** Buduje konfigurację osi z parametrów {@link CarComponent}. */
    private static PacejkaTireModel.AxleConfig buildConfig(CarComponent car, boolean front) {
        PacejkaTireModel.AxleConfig cfg = new PacejkaTireModel.AxleConfig();
        if (front) {
            cfg.B = car.pacejkaFrontB;
            cfg.C = car.pacejkaFrontC;
            cfg.D = car.pacejkaFrontD;
            cfg.E = car.pacejkaFrontE;
            cfg.slideFalloffMin = car.slideForceFalloffMinFront;
            cfg.peakSlipAngle = car.frontPeakSlipAngle;
        } else {
            cfg.B = car.pacejkaRearB;
            cfg.C = car.pacejkaRearC;
            cfg.D = car.pacejkaRearD;
            cfg.E = car.pacejkaRearE;
            cfg.slideFalloffMin = car.slideForceFalloffMinRear;
            cfg.peakSlipAngle = car.rearPeakSlipAngle;
        }
        return cfg;
    }

    /**
     * Rysuje krzywą siły bocznej i zapisuje pozycję kropki bieżącego poślizgu.
     * Krzywa i kropka używają tych samych mnożników co fizyka.
     */
    private void drawPacejkaCurve(float ox, float oy, float w, float h, float alphaMax,
                                  PacejkaTireModel.AxleConfig cfg, CarComponent car,
                                  PacejkaTireModel.AxleRuntime baseRt, float dotAlphaEff, int dotIndex) {
        float forceScale = car.physicsForceScale;
        float maxF = Math.max(baseRt.peakForceScaledN, 1f);

        PacejkaTireModel.AxleRuntime curveRt = copyRuntimeForCurve(baseRt);

        int steps = 28;
        float prevPx = 0f, prevPy = 0f;
        boolean first = true;

        for (int i = 0; i <= steps; i++) {
            float alpha = -alphaMax + (2f * alphaMax * i / steps);
            curveRt.slideMult = PacejkaTireModel.slideForceFalloff(Math.abs(alpha), cfg.peakSlipAngle,
                    car.pacejkaMaxSlipAngle, cfg.slideFalloffMin);

            float fy = PacejkaTireModel.curveFyAtSlip(alpha, cfg, curveRt, forceScale);
            float px = ox + (alpha / alphaMax + 1f) * 0.5f * w;
            float py = oy + (fy / maxF + 1f) * 0.5f * h;

            if (!first)
                shapeRenderer.line(prevPx, prevPy, px, py);
            prevPx = px;
            prevPy = py;
            first = false;
        }

        curveRt.slideMult = PacejkaTireModel.slideForceFalloff(Math.abs(dotAlphaEff), cfg.peakSlipAngle,
                car.pacejkaMaxSlipAngle, cfg.slideFalloffMin);
        float dotFyRecomputed = PacejkaTireModel.curveFyAtSlip(dotAlphaEff, cfg, curveRt, forceScale);
        dotPositions[dotIndex * 2] = ox + (dotAlphaEff / alphaMax + 1f) * 0.5f * w;
        dotPositions[dotIndex * 2 + 1] = oy + (dotFyRecomputed / maxF + 1f) * 0.5f * h;
    }

    /**
     * Dopasowuje viewport HUD po zmianie rozmiaru okna.
     *
     * @param width  nowa szerokość
     * @param height nowa wysokość
     */
    public void resize(int width, int height) {
        hudViewport.update(width, height, true);
    }

    /** Kopiuje stan runtime osi do rysowania krzywej (slideMult nadpisywany per próbka). */
    private static PacejkaTireModel.AxleRuntime copyRuntimeForCurve(PacejkaTireModel.AxleRuntime src) {
        PacejkaTireModel.AxleRuntime rt = new PacejkaTireModel.AxleRuntime();
        rt.mu = src.mu;
        rt.loadScale = src.loadScale;
        rt.gripScale = src.gripScale;
        rt.extraGripMult = src.extraGripMult;
        rt.lowSpeedFade = src.lowSpeedFade;
        rt.slideMult = src.slideMult;
        return rt;
    }

    /** Zwalnia zasoby batch, czcionki i shape renderera. */
    public void dispose() {
        batch.dispose();
        font.dispose();
        shapeRenderer.dispose();
    }
}
