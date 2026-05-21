package com.wildkarts.systems;

import com.badlogic.gdx.math.MathUtils;
import com.wildkarts.components.CarComponent;

/**
 * Shared Pacejka lateral force — used by physics and debug HUD so the chart dot matches Fy.
 */
public final class PacejkaTireModel {

    private PacejkaTireModel() {}

    public static final float GRAVITY = 9.81f;

    public static class AxleConfig {
        public float B, C, D, E;
        public float peakSlipAngle;
        public float slideFalloffMin;
        public boolean isFront;
    }

    /** Per-axle state written each physics step (and read by debug). */
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

    public static float magicFormulaForce(float alpha, float B, float C, float D, float E) {
        float bAlpha = B * alpha;
        float atanB = MathUtils.atan(bAlpha);
        float inner = bAlpha - E * (bAlpha - atanB);
        return D * MathUtils.sin(C * MathUtils.atan(inner));
    }

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

    /** Static weight distribution (front / rear normal load in N). */
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

    public static float computeLoadScale(float normalLoadN, float referenceLoadN) {
        if (referenceLoadN <= 0f) return 1f;
        return MathUtils.clamp(normalLoadN / referenceLoadN, 0.35f, 1.4f);
    }

    public static float computeLowSpeedFade(float chassisSpeedMps, CarComponent car) {
        return MathUtils.clamp(chassisSpeedMps / Math.max(car.lowSpeedGripFadeSpeed, 0.1f), 0f, 1f);
    }

    public static float slideForceFalloff(float absSlipRaw, float peakSlip, float maxSlip, float minFalloff) {
        if (absSlipRaw <= peakSlip * 1.15f) return 1f;
        float t = MathUtils.clamp((absSlipRaw - peakSlip) / Math.max(maxSlip - peakSlip, 0.01f), 0f, 1f);
        return MathUtils.lerp(1f, minFalloff, t);
    }

    public static float clampSlip(float alpha, float maxSlip) {
        return MathUtils.clamp(alpha, -maxSlip, maxSlip);
    }

    /**
     * Pacejka pipeline shared by physics and debug. Convention:
     *  - Positive slip alpha = wheel velocity to the right of wheel forward (body +X side at zero steer).
     *  - magicFormulaForce(positive alpha) > 0 = restoring lateral force, magnitude in newtons.
     *  - Caller multiplies this by wheelLat (which points to the LEFT of wheel forward) so that
     *    positive force * leftDir = leftward push that opposes the rightward slip.
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

    /** Fy at arbitrary slip for curve drawing (includes all runtime multipliers). */
    public static float curveFyAtSlip(float alpha, AxleConfig cfg, AxleRuntime rt, float forceScale) {
        float base = magicFormulaForce(alpha, cfg.B, cfg.C, cfg.D, cfg.E);
        return base * rt.mu * rt.loadScale * rt.gripScale * rt.slideMult
                * rt.extraGripMult * rt.lowSpeedFade * forceScale;
    }

    public static float gripUsagePercent(float fyApplied, float peakForceScaled) {
        if (peakForceScaled <= 0f) return 0f;
        return MathUtils.clamp(Math.abs(fyApplied) / peakForceScaled * 100f, 0f, 150f);
    }
}
