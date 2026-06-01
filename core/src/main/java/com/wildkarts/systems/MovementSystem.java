package com.wildkarts.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.wildkarts.components.CarComponent;
import com.wildkarts.components.InputComponent;
import com.wildkarts.components.PhysicsComponent;

/**
 * Fizyka pojazdu — model Pacejki z kątami poślizgu w układzie rowerowym (bicycle model).
 */
public class MovementSystem extends IteratingSystem {

    private static final float MIN_SPEED_FOR_STEER = 0.3f;

    private static final Vector2 LOCAL_FORWARD = new Vector2(0f, 1f);
    private static final Vector2 LOCAL_RIGHT = new Vector2(1f, 0f);
    private static final Vector2 LOCAL_FRONT_AXLE = new Vector2();
    private static final Vector2 LOCAL_REAR_AXLE = new Vector2();

    private final ComponentMapper<InputComponent> inputMapper =
            ComponentMapper.getFor(InputComponent.class);
    private final ComponentMapper<CarComponent> carMapper =
            ComponentMapper.getFor(CarComponent.class);
    private final ComponentMapper<PhysicsComponent> physicsMapper =
            ComponentMapper.getFor(PhysicsComponent.class);

    private static final Vector2 forwardDir = new Vector2();
    private static final Vector2 lateralDir = new Vector2();
    private static final Vector2 velocity = new Vector2();
    private static final Vector2 frontAxlePos = new Vector2();
    private static final Vector2 rearAxlePos = new Vector2();
    private static final Vector2 force = new Vector2();
    private static final Vector2 wheelForward = new Vector2();
    private static final Vector2 wheelLateral = new Vector2();

    /**
     * Tworzy system przetwarzający encje z wejściem, parametrami auta i ciałem Box2D.
     */
    public MovementSystem() {
        super(Family.all(InputComponent.class, CarComponent.class, PhysicsComponent.class).get());
    }

    /**
     * Aktualizuje kąt skrętu kół na podstawie wejścia gracza (wywoływane co klatkę).
     *
     * @param entity    encja samochodu
     * @param deltaTime czas od ostatniej klatki w sekundach
     */
    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        InputComponent input = inputMapper.get(entity);
        CarComponent car = carMapper.get(entity);
        PhysicsComponent physics = physicsMapper.get(entity);
        Body body = physics.body;
        if (body == null) return;

        forwardDir.set(body.getWorldVector(LOCAL_FORWARD));
        velocity.set(body.getLinearVelocity());
        float speedMult = car.globalSpeedMultiplier * car.physicsSpeedScale;
        float forwardSpeed = velocity.dot(forwardDir);
        float speedAbs = Math.abs(forwardSpeed);
        float omega = body.getAngularVelocity();

        updateSteering(car, input, forwardSpeed, speedAbs, speedMult, omega, deltaTime);
    }

    /**
     * Wykonuje pełny krok fizyki samochodu — siły boczne Pacejki, napęd, opory i asysty simcade.
     * Wywoływane z {@code PhysicsSystem} z ustalonym krokiem czasowym.
     *
     * @param body     ciało Box2D samochodu
     * @param car      parametry tuningu auta
     * @param input    wejście gracza (gaz, hamulec, skręt)
     * @param physics  komponent fizyki z wymiarami nadwozia
     */
    public static void simulatePhysicsStep(Body body, CarComponent car, InputComponent input,
                                            PhysicsComponent physics) {
        float forceScale = car.physicsForceScale;
        float speedMult = car.globalSpeedMultiplier * car.physicsSpeedScale;

        forwardDir.set(body.getWorldVector(LOCAL_FORWARD));
        lateralDir.set(body.getWorldVector(LOCAL_RIGHT));

        car.forwardDirWorld.set(forwardDir);
        car.lateralDirWorld.set(lateralDir);

        velocity.set(body.getLinearVelocity());
        float forwardSpeed = velocity.dot(forwardDir);
        float lateralSpeed = velocity.dot(lateralDir);
        float speedAbs = Math.abs(forwardSpeed);
        float chassisSpeed = velocity.len();
        float omega = body.getAngularVelocity();

        car.displaySpeed = speedAbs * speedMult;
        car.displayForwardSpeed = forwardSpeed * speedMult;

        float steerRad = car.currentSteeringAngle * MathUtils.degreesToRadians;
        boolean reversing = forwardSpeed < -0.5f;
        float rearGripMult = input.braking ? car.handbrakeRearGripMultiplier : 1f;
        float reverseGripMult = reversing ? 0.9f : 1f;
        float rearExtraGrip = reverseGripMult * rearGripMult;

        LOCAL_FRONT_AXLE.set(0f, car.frontAxleDistance);
        LOCAL_REAR_AXLE.set(0f, -car.rearAxleDistance);
        frontAxlePos.set(body.getWorldPoint(LOCAL_FRONT_AXLE));
        rearAxlePos.set(body.getWorldPoint(LOCAL_REAR_AXLE));
        car.frontAxleWorld.set(frontAxlePos);
        car.rearAxleWorld.set(rearAxlePos);

        float halfTrack = car.rearWheelHalfTrack > 0f
                ? car.rearWheelHalfTrack
                : physics.widthMeters * 0.42f;
        car.rearLeftWheelWorld.set(rearAxlePos).add(lateralDir.x * halfTrack, lateralDir.y * halfTrack);
        car.rearRightWheelWorld.set(rearAxlePos).sub(lateralDir.x * halfTrack, lateralDir.y * halfTrack);

        float gripScale = computeGripScale(chassisSpeed, car);

        float steerInputAbs = Math.abs(input.steering);
        if (input.throttle > 0.15f && steerInputAbs > 0.08f) {
            rearExtraGrip *= car.powerOversteerGripMultiplier;
        }
        boolean isTurning = steerInputAbs > 0.01f;
        boolean isHandbrake = input.braking;
        boolean straightMode = !isTurning && !isHandbrake;

        float[] normalLoads = new float[2];
        PacejkaTireModel.computeNormalLoads(car, normalLoads);
        applyTurnLoadTransfer(normalLoads, input.steering, car);
        car.frontNormalLoadN = normalLoads[0];
        car.rearNormalLoadN = normalLoads[1];
        float refLoad = car.referenceNormalLoadN > 0f
                ? car.referenceNormalLoadN
                : (car.mass * PacejkaTireModel.GRAVITY * 0.5f);

        float alphaFrontRaw = computeBicycleSlipAngle(forwardSpeed, lateralSpeed, omega,
                car.frontAxleDistance, steerRad, car);
        float alphaRearRaw = computeBicycleSlipAngle(forwardSpeed, lateralSpeed, omega,
                -car.rearAxleDistance, 0f, car);

        car.frontSlipAngle = alphaFrontRaw;
        car.rearSlipAngle = alphaRearRaw;

        PacejkaTireModel.AxleConfig frontCfg = buildFrontConfig(car);
        PacejkaTireModel.AxleConfig rearCfg = buildRearConfig(car);

        float alphaFront = PacejkaTireModel.clampSlip(alphaFrontRaw, car.pacejkaMaxSlipAngle);
        float alphaRear = PacejkaTireModel.clampSlip(alphaRearRaw, car.pacejkaMaxSlipAngle);

        car.frontSlipAngleEffective = alphaFront;
        car.rearSlipAngleEffective = alphaRear;

        float fyFront = evaluateAxleForce(car, frontCfg, car.frontTireRuntime, alphaFrontRaw, alphaFront,
                car.frontNormalLoadN, refLoad, gripScale, forceScale, car.surfaceMu, reverseGripMult, 1f);
        float fyRear = evaluateAxleForce(car, rearCfg, car.rearTireRuntime, alphaRearRaw, alphaRear,
                car.rearNormalLoadN, refLoad, gripScale, forceScale, car.surfaceMu, rearExtraGrip, 1f);

        car.frontLateralForce = fyFront;
        car.rearLateralForce = fyRear;
        car.frontGripPercent = PacejkaTireModel.gripUsagePercent(fyFront, car.frontTireRuntime.peakForceScaledN);
        car.rearGripPercent = PacejkaTireModel.gripUsagePercent(fyRear, car.rearTireRuntime.peakForceScaledN);

        float fxRear = computeLongitudinalForce(car, input.throttle, forwardSpeed, speedAbs, speedMult) * forceScale;
        // Kontrola trakcji: ogranicza napęd przy nadmiernym poślizgu tylnej osi (prosto lub na trawie).
        boolean rearOverslipping = Math.abs(alphaRear) > car.rearPeakSlipAngle * 1.35f;
        boolean lowMuSurface = car.surfaceMu < (car.surfaceMuRoad - 0.15f);
        if (input.throttle > 0f && rearOverslipping && (straightMode || lowMuSurface)) {
            fxRear *= car.tractionControlStrength;
        }
        fxRear += computeDragAndRolling(car, forwardSpeed, speedAbs, speedMult) * forceScale;

        applyLateralForceAtAxle(body, frontAxlePos, steerRad, fyFront, forwardDir, force, wheelForward, wheelLateral);
        applyLateralForceAtAxle(body, rearAxlePos, 0f, fyRear, forwardDir, force, wheelForward, wheelLateral);

        force.set(forwardDir).scl(fxRear);
        body.applyForce(force, rearAxlePos, true);

        if (speedAbs < car.steeringYawAssistMaxSpeed) {
            applySteeringYawAssist(body, car, input.steering, forwardSpeed, speedAbs, speedMult, forceScale);
        }

        float latDamp = straightMode ? car.stabilityLateralDamping : car.turningLateralDamping;
        // W zakręcie z gazem — lekkie tłumienie boczne, żeby nie zabijać prędkości w łuku.
        if (isTurning && input.throttle > 0.12f) {
            latDamp *= 0.35f;
        }
        if (reversing) latDamp *= 1.5f;
        force.set(lateralDir).scl(-lateralSpeed * latDamp * forceScale);
        body.applyForceToCenter(force, true);

        applyAlignmentAssist(body, car, input, forwardSpeed, lateralSpeed, omega, chassisSpeed, forceScale);

        applySpinDamping(body, car, omega, chassisSpeed, forceScale, isTurning);

        updateRearSkidState(car, chassisSpeed, input);
    }

    /**
     * Przenosi obciążenie między osiami przy skręcie — symuluje przesunięcie masy na przód.
     */
    private static void applyTurnLoadTransfer(float[] frontRearLoad, float steerInput, CarComponent car) {
        if (Math.abs(steerInput) < 0.01f) return;
        float transfer = car.mass * PacejkaTireModel.GRAVITY * car.turnLoadTransfer * Math.abs(steerInput);
        frontRearLoad[0] += transfer;
        frontRearLoad[1] = Math.max(frontRearLoad[1] - transfer, frontRearLoad[1] * 0.5f);
    }

    /**
     * Oblicza kąt poślizgu bocznego na osi w modelu rowerowym (w radianach).
     */
    static float computeBicycleSlipAngle(float vLong, float vLat, float omega, float axleOffset,
                                         float steerRad, CarComponent car) {
        // Kinetyka sztywnego nadwozia: prędkość osi = prędkość CG + omega × r.
        float vLongWheel = vLong;
        float vLatWheel = vLat - omega * axleOffset;

        if (steerRad != 0f) {
            // Przekształcenie prędkości do układu obróconego kołem o kąt skrętu.
            float cos = MathUtils.cos(steerRad);
            float sin = MathUtils.sin(steerRad);
            float vLw = vLongWheel * cos - vLatWheel * sin;
            float vTw = vLongWheel * sin + vLatWheel * cos;
            vLongWheel = vLw;
            vLatWheel = vTw;
        }

        float chassisSpeed = (float) Math.sqrt(vLong * vLong + vLat * vLat);
        if (chassisSpeed < car.minChassisSpeedForSlip) {
            return 0f;
        }

        float denom = Math.max(Math.abs(vLongWheel), car.minLongitudinalSpeedForSlip);
        if (Math.abs(vLongWheel) < car.minLongitudinalSpeedForSlip) {
            denom = car.minLongitudinalSpeedForSlip;
        }

        // Dodatnia prędkość wzdłużna — cofanie nie generuje fałszywego poślizgu ~180°.
        float alpha = MathUtils.atan2(vLatWheel, denom);

        float fade = MathUtils.clamp(chassisSpeed / Math.max(car.lowSpeedGripFadeSpeed, 0.1f), 0f, 1f);
        return alpha * fade;
    }

    private static float computeGripScale(float chassisSpeed, CarComponent car) {
        float speedGrip = MathUtils.clamp(chassisSpeed / Math.max(car.pacejkaMinSpeedForGrip, 0.1f), 0f, 1f);
        float chassisGrip = MathUtils.clamp(chassisSpeed / Math.max(car.pacejkaChassisSpeedRef, 0.1f), 0f, 1f);
        return speedGrip * chassisGrip;
    }

    /** Lekki moment yaw — Pacejka sama nie skręca na miejscu przy niskiej prędkości. */
    private static void applySteeringYawAssist(Body body, CarComponent car, float steeringInput,
                                               float forwardSpeed, float speedAbs, float speedMult,
                                               float forceScale) {
        if (Math.abs(steeringInput) < 0.01f) return;

        float speedFrac = MathUtils.clamp(speedAbs / Math.max(car.steeringYawAssistMaxSpeed, 0.5f), 0f, 1f);
        float steerRad = car.currentSteeringAngle * MathUtils.degreesToRadians;

        float torque = steerRad * car.steeringYawTorqueGain * speedFrac * forceScale;
        body.applyTorque(torque, true);
    }

    private static void applySpinDamping(Body body, CarComponent car, float omega, float chassisSpeed,
                                         float forceScale, boolean isTurning) {
        float absOmega = Math.abs(omega);
        if (absOmega < 0.1f) return;

        float omegaFactor = MathUtils.clamp((absOmega - car.spinDampingOmegaStart * 0.4f)
                / Math.max(car.spinDampingOmegaStart, 0.1f), 0f, 1f);
        float speedFactor = MathUtils.clamp(chassisSpeed / Math.max(car.pacejkaChassisSpeedRef, 0.1f), 0.3f, 1f);
        // Słabsze tłumienie w skręcie; pełne tylko przy niekontrolowanym spinie bez sterowania.
        float damp = car.spinAngularDamping * (isTurning ? 0.6f : 1f);

        body.applyTorque(-omega * damp * omegaFactor * speedFactor * forceScale, true);
    }

    /**
     * Asysta wyrównania simcade — ciągnie nadwozie w kierunku wektora prędkości przy kontrze czy puszczeniu gazu.
     */
    private static void applyAlignmentAssist(Body body, CarComponent car, InputComponent input,
                                              float forwardSpeed, float lateralSpeed, float omega,
                                              float chassisSpeed, float forceScale) {
        if (chassisSpeed < car.alignmentAssistMinSpeed) return;

        boolean noSteer = Math.abs(input.steering) < 0.01f;
        boolean counterSteer = Math.abs(forwardSpeed) > 0.5f && isCounterSteering(input.steering, omega, car);

        // Aktywny skręt (poza kontrą) — nie wyrównuj kursu, bo zabija prędkość w łuku.
        if (!noSteer && !counterSteer) return;

        float beta = MathUtils.atan2(lateralSpeed, Math.max(Math.abs(forwardSpeed), 1.5f));
        float speedWeight = MathUtils.clamp(
                chassisSpeed / Math.max(car.alignmentAssistMinSpeed * 2f, 0.5f), 0f, 1f);

        float strength = car.alignmentAssistStrength;
        if (counterSteer) {
            strength *= car.counterSteerAlignmentBoost;
        }

        body.applyTorque(-beta * strength * speedWeight * forceScale, true);

        if (noSteer || counterSteer) {
            body.applyTorque(-omega * car.idleYawDamping * speedWeight * forceScale, true);
        }
    }

    private static PacejkaTireModel.AxleConfig buildFrontConfig(CarComponent car) {
        PacejkaTireModel.AxleConfig cfg = new PacejkaTireModel.AxleConfig();
        cfg.B = car.pacejkaFrontB;
        cfg.C = car.pacejkaFrontC;
        cfg.D = car.pacejkaFrontD;
        cfg.E = car.pacejkaFrontE;
        cfg.isFront = true;
        cfg.slideFalloffMin = car.slideForceFalloffMinFront;
        cfg.peakSlipAngle = car.frontPeakSlipAngle = PacejkaTireModel.estimatePeakSlipAngle(
                cfg.B, cfg.C, cfg.D, cfg.E);
        return cfg;
    }

    private static PacejkaTireModel.AxleConfig buildRearConfig(CarComponent car) {
        PacejkaTireModel.AxleConfig cfg = new PacejkaTireModel.AxleConfig();
        cfg.B = car.pacejkaRearB;
        cfg.C = car.pacejkaRearC;
        cfg.D = car.pacejkaRearD;
        cfg.E = car.pacejkaRearE;
        cfg.isFront = false;
        cfg.slideFalloffMin = car.slideForceFalloffMinRear;
        cfg.peakSlipAngle = car.rearPeakSlipAngle = PacejkaTireModel.estimatePeakSlipAngle(
                cfg.B, cfg.C, cfg.D, cfg.E);
        return cfg;
    }

    private static float evaluateAxleForce(CarComponent car, PacejkaTireModel.AxleConfig cfg,
                                           PacejkaTireModel.AxleRuntime rt, float alphaRaw, float alphaEff,
                                           float normalLoad, float refLoad, float gripScale, float forceScale,
                                           float mu, float extraGrip, float handbrakeMult) {
        rt.alphaRaw = alphaRaw;
        rt.alphaEffective = alphaEff;
        rt.normalLoadN = normalLoad;
        rt.mu = mu;
        rt.loadScale = PacejkaTireModel.computeLoadScale(normalLoad, refLoad);
        rt.gripScale = gripScale;
        rt.extraGripMult = extraGrip * handbrakeMult;
        rt.lowSpeedFade = 1f;
        rt.slideMult = PacejkaTireModel.slideForceFalloff(Math.abs(alphaEff), cfg.peakSlipAngle,
                car.pacejkaMaxSlipAngle, cfg.slideFalloffMin);

        float fy = PacejkaTireModel.computeAppliedFy(alphaEff, cfg, rt, forceScale);

        rt.peakForceScaledN = computePeakForceN(cfg, rt, forceScale);

        return fy;
    }

    private static float computePeakForceN(PacejkaTireModel.AxleConfig cfg,
                                            PacejkaTireModel.AxleRuntime rt, float forceScale) {
        PacejkaTireModel.AxleRuntime peakRt = new PacejkaTireModel.AxleRuntime();
        peakRt.mu = rt.mu;
        peakRt.loadScale = rt.loadScale;
        peakRt.gripScale = rt.gripScale;
        peakRt.extraGripMult = rt.extraGripMult;
        peakRt.lowSpeedFade = rt.lowSpeedFade;
        peakRt.slideMult = 1f;
        return Math.abs(PacejkaTireModel.curveFyAtSlip(cfg.peakSlipAngle, cfg, peakRt, forceScale));
    }

    private static void updateRearSkidState(CarComponent car, float chassisSpeed, InputComponent input) {
        float absSlip = Math.abs(car.rearSlipAngleEffective);
        float peak = car.rearPeakSlipAngle * car.rearSkidPastPeakMultiplier;
        boolean pastPeak = absSlip > peak;
        boolean straight = absSlip < car.rearSkidSlipStraightThreshold;
        boolean fastEnough = chassisSpeed >= car.rearSkidMinSpeed;

        if (straight || !fastEnough) {
            car.rearSkidActive = false;
            car.rearLeftSkidActive = false;
            car.rearRightSkidActive = false;
            return;
        }

        car.rearSkidActive = pastPeak;
        car.rearLeftSkidActive = pastPeak;
        car.rearRightSkidActive = pastPeak;
    }

    static float pacejkaLateralForce(float alpha, float B, float C, float D, float E) {
        return PacejkaTireModel.magicFormulaForce(alpha, B, C, D, E);
    }

    private void updateSteering(CarComponent car, InputComponent input, float forwardSpeed,
                                 float speedAbs, float speedMult, float omega, float deltaTime) {
        // Zeruj kierownicę przy praktycznym postoju — brak resztkowego skrętu.
        if (speedAbs < MIN_SPEED_FOR_STEER * 0.3f && Math.abs(input.steering) < 0.01f) {
            car.currentSteeringAngle = 0f;
            return;
        }

        float maxSpeed = car.maxForwardSpeed * speedMult;
        float speedFraction = MathUtils.clamp(speedAbs / Math.max(maxSpeed, 0.1f), 0f, 1f);
        float maxAngle = MathUtils.lerp(car.maxSteeringAngle, car.minSteeringAngle, speedFraction);

        float targetAngle = input.steering * maxAngle;
        if (speedAbs < MIN_SPEED_FOR_STEER) {
            targetAngle *= MathUtils.clamp(speedAbs / MIN_SPEED_FOR_STEER, 0f, 1f);
        }

        boolean holdingSteer = Math.abs(input.steering) > 0.01f;
        float rate = holdingSteer ? car.steeringSpeed : car.steeringReturnSpeed;

        // Asysta kontry — szybkie przełożenie kierownicy przeciw kierunkowi obrotu nadwozia.
        if (holdingSteer && Math.abs(forwardSpeed) > 0.5f && isCounterSteering(input.steering, omega, car)) {
            rate *= car.counterSteerSpeedMultiplier;
        }

        car.currentSteeringAngle = moveToward(car.currentSteeringAngle, targetAngle, rate * deltaTime);
    }

    private static boolean isCounterSteering(float steerInput, float omega, CarComponent car) {
        return Math.abs(omega) > car.counterSteerYawThreshold && steerInput * omega < 0f;
    }

    private static float moveToward(float current, float target, float maxDelta) {
        if (current < target) {
            return Math.min(current + maxDelta, target);
        }
        return Math.max(current - maxDelta, target);
    }

    private static void applyLateralForceAtAxle(Body body, Vector2 axlePos, float steerRad, float fy,
                                                Vector2 bodyForward, Vector2 forceOut,
                                                Vector2 wheelFwd, Vector2 wheelLat) {
        wheelFwd.set(bodyForward);
        if (steerRad != 0f) {
            float cos = MathUtils.cos(steerRad);
            float sin = MathUtils.sin(steerRad);
            float fx = wheelFwd.x * cos - wheelFwd.y * sin;
            float fyDir = wheelFwd.x * sin + wheelFwd.y * cos;
            wheelFwd.set(fx, fyDir);
        }
        wheelLat.set(-wheelFwd.y, wheelFwd.x);
        forceOut.set(wheelLat).scl(fy);
        body.applyForce(forceOut, axlePos, true);
    }

    private static float computeLongitudinalForce(CarComponent car, float throttle, float forwardSpeed,
                                                  float speedAbs, float speedMult) {
        if (throttle == 0f) return 0f;

        float maxForward = car.maxForwardSpeed * speedMult;
        float maxBackward = car.maxBackwardSpeed * speedMult;
        float engine = car.engineForce * car.engineForceMultiplier;

        if (throttle > 0f) {
            if (forwardSpeed >= maxForward) return 0f;
            return engine * throttle;
        }

        if (forwardSpeed > 0.5f) {
            return -car.brakeForce;
        }
        if (forwardSpeed > -maxBackward) {
            return engine * throttle;
        }
        return 0f;
    }

    private static float computeDragAndRolling(CarComponent car, float forwardSpeed, float speedAbs, float speedMult) {
        float drag = -car.aerodynamicDragCoeff * speedAbs * speedAbs;

        float highStart = car.highSpeedDragStart * speedMult;
        if (speedAbs > highStart) {
            float excess = speedAbs - highStart;
            drag -= car.highSpeedDragCoeff * excess * excess;
        }

        if (forwardSpeed < 0f) {
            drag = -drag;
        }

        float rolling = 0f;
        if (speedAbs > 0.1f) {
            rolling = -car.rollingResistance * Math.signum(forwardSpeed);
        }
        return drag + rolling;
    }
}
