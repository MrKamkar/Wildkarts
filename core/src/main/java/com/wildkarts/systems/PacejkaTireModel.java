package com.wildkarts.systems;

import com.badlogic.gdx.math.MathUtils;
import com.wildkarts.components.CarComponent;

/**
 * Wspólny model bocznej siły Pacejki — używany przez fizykę i HUD debug,
 * aby kropka na wykresie odpowiadała rzeczywistej sile Fy.
 */
public final class PacejkaTireModel {

    /** Przyspieszenie ziemskie (m/s²) do obliczeń obciążenia osi. */
    public static final float GRAVITY = 9.81f;

    private PacejkaTireModel() {
    }

    /** Parametry magicznej formuły Pacejki dla jednej osi. */
    public static class AxleConfig {
        public float B, C, D, E;
        public float peakSlipAngle;
        public float slideFalloffMin;
        public boolean isFront;
    }

    /** Stan osi zapisywany co krok fizyki (odczytywany też przez debug). */
    public static class AxleRuntime {
        public float alphaRaw;
        public float alphaEffective;
        public float normalLoadN;
        public float mu;
        public float gripScale;
        public float loadScale;
        public float slideMult;
        public float extraGripMult = 1f;
        public float lowSpeedFade = 1f;
        public float peakForceScaledN;
        public float fyAppliedN;
    }

    /**
     * Oblicza siłę boczną według magicznej formuły Pacejki.
     *
     * @param alpha kąt poślizgu (rad)
     * @param B,C,D,E współczynniki formuły
     * @return siła boczna (N)
     */
    public static float magicFormulaForce(float alpha, float B, float C, float D, float E) {
        float bAlpha = B * alpha;
        float atanB = MathUtils.atan(bAlpha);
        float inner = bAlpha - E * (bAlpha - atanB);
        return D * MathUtils.sin(C * MathUtils.atan(inner));
    }

    /**
     * Szacuje kąt poślizgu przy maksymalnej sile poprzez przeszukanie zakresu.
     *
     * @return kąt poślizgu (rad) przy szczytowej sile
     */
    public static float estimatePeakSlipAngle(float B, float C, float D, float E) {
        float bestAlpha = 0.08f;
        float bestForce = 0f;
        for (float a = 0.02f; a <= 0.35f; a += 0.005f) {
            float f = Math.abs(magicFormulaForce(a, B, C, D, E));
            if (f > bestForce) {
                bestForce = f;
                bestAlpha = a;
            }
        }
        return bestAlpha;
    }

    /**
     * Oblicza statyczny rozkład obciążenia na przednią i tylną oś (N).
     *
     * @param car          komponent auta z masą i rozstawem osi
     * @param outFrontRear tablica wyjściowa [przód, tył]
     */
    public static void computeNormalLoads(CarComponent car, float[] outFrontRear) {
        float wheelbase = car.frontAxleDistance + car.rearAxleDistance;
        float weight = car.mass * GRAVITY;
        if (wheelbase <= 0.01f) {
            outFrontRear[0] = weight * 0.5f;
            outFrontRear[1] = weight * 0.5f;
            return;
        }
        outFrontRear[0] = weight * (car.rearAxleDistance / wheelbase);
        outFrontRear[1] = weight * (car.frontAxleDistance / wheelbase);
    }

    /**
     * Skaluje siłę boczną w zależności od obciążenia osi względem referencyjnego.
     *
     * @return mnożnik obciążenia (0.35…1.4)
     */
    public static float computeLoadScale(float normalLoadN, float referenceLoadN) {
        if (referenceLoadN <= 0f) return 1f;
        return MathUtils.clamp(normalLoadN / referenceLoadN, 0.35f, 1.4f);
    }

    /**
     * Tłumi przyczepność przy bardzo niskiej prędkości (łatwiejsze ruszanie).
     *
     * @return mnożnik 0…1
     */
    public static float computeLowSpeedFade(float chassisSpeedMps, CarComponent car) {
        return MathUtils.clamp(chassisSpeedMps / Math.max(car.lowSpeedGripFadeSpeed, 0.1f), 0f, 1f);
    }

    /**
     * Obniża siłę boczną po przekroczeniu szczytu poślizgu (strefa poślizgu).
     *
     * @return mnożnik siły (1.0 przy małym poślizgu, {@code minFalloff} przy dużym)
     */
    public static float slideForceFalloff(float absSlipRaw, float peakSlip, float maxSlip, float minFalloff) {
        if (absSlipRaw <= peakSlip * 1.15f) return 1f;
        float t = MathUtils.clamp((absSlipRaw - peakSlip) / Math.max(maxSlip - peakSlip, 0.01f), 0f, 1f);
        return MathUtils.lerp(1f, minFalloff, t);
    }

    /**
     * Ogranicza kąt poślizgu do sensownego zakresu.
     *
     * @return sklamowany kąt poślizgu (rad)
     */
    public static float clampSlip(float alpha, float maxSlip) {
        return MathUtils.clamp(alpha, -maxSlip, maxSlip);
    }

    /**
     * Pełny pipeline Pacejki wspólny dla fizyki i debugu.
     *
     * <p>Konwencja: dodatni kąt poślizgu = prędkość koła w prawo względem osi koła;
     * dodatnia siła Fy = siła przywracająca; wywołujący mnoży przez wektor boczny koła.</p>
     *
     * @return zastosowana siła boczna Fy (N), zapisana też w {@code rt.fyAppliedN}
     */
    public static float computeAppliedFy(float alphaEffective, AxleConfig cfg, AxleRuntime rt,
                                         float forceScale) {
        float base = magicFormulaForce(alphaEffective, cfg.B, cfg.C, cfg.D, cfg.E);
        float scaled = base * rt.mu * rt.loadScale * rt.gripScale * rt.slideMult
                * rt.extraGripMult * rt.lowSpeedFade * forceScale;
        rt.peakForceScaledN = Math.abs(magicFormulaForce(cfg.peakSlipAngle, cfg.B, cfg.C, cfg.D, cfg.E))
                * rt.mu * rt.loadScale * rt.gripScale * rt.slideMult * rt.extraGripMult
                * rt.lowSpeedFade * forceScale;
        rt.fyAppliedN = scaled;
        return rt.fyAppliedN;
    }

    /**
     * Siła Fy przy dowolnym kącie poślizgu — do rysowania krzywej na HUD debug.
     *
     * @return siła boczna (N) z uwzględnieniem mnożników runtime
     */
    public static float curveFyAtSlip(float alpha, AxleConfig cfg, AxleRuntime rt, float forceScale) {
        float base = magicFormulaForce(alpha, cfg.B, cfg.C, cfg.D, cfg.E);
        return base * rt.mu * rt.loadScale * rt.gripScale * rt.slideMult
                * rt.extraGripMult * rt.lowSpeedFade * forceScale;
    }

    /**
     * Procent wykorzystania przyczepności względem szczytu siły.
     *
     * @return procent 0…150
     */
    public static float gripUsagePercent(float fyApplied, float peakForceScaled) {
        if (peakForceScaled <= 0f) return 0f;
        return MathUtils.clamp(Math.abs(fyApplied) / peakForceScaled * 100f, 0f, 150f);
    }
}
