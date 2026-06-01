package com.wildkarts.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector2;
import com.wildkarts.systems.PacejkaTireModel;

/**
 * Parametry tuningu auta simcade — boczny model Pacejki z napędem na tylną oś (RWD).
 * Wszystkie pola publiczne umożliwiają szybką edycję w inspektorze / debuggerze.
 */
public class CarComponent implements Component {

    // --- Physics scaling ---

    public float physicsForceScale = 1f;

    /**
     * Master speed scale — multiply with physicsSpeedScale to fit map size.
     * Affects top speed limits, HUD speed, and soft speed cap via drag.
     */
    public float globalSpeedMultiplier = 1.10f;

    /** Fine-tune readout / limits (1 = raw Box2D m/s). */
    public float physicsSpeedScale = 1f;

    public float physicsTimeStep = 1f / 60f;

    // --- Mass & inertia ---

    public float mass = 28f;
    public float inertia = 0f;

    // --- Chassis geometry (meters from CG, body-local +Y forward) ---

    public float frontAxleDistance = 0.94f;
    public float rearAxleDistance = 0.96f;

    /** Half-track for rear wheels (meters from centerline); 0 = use body width. */
    public float rearWheelHalfTrack = 0f;

    // --- Engine & speed limits (base values, scaled by globalSpeedMultiplier) ---

    public float maxForwardSpeed = 68f;
    public float maxBackwardSpeed = 22f;

    /** Base engine force (N); also scaled by engineForceMultiplier. */
    public float engineForce = 580f;

    /** 0.75 = 25% less acceleration. */
    public float engineForceMultiplier = 0.92f;

    public float brakeForce = 220f;

    // --- Aerodynamic & rolling resistance ---

    public float aerodynamicDragCoeff = 0.0038f;

    /** Extra quadratic drag when speed exceeds this (m/s, after speed scaling). */
    public float highSpeedDragStart = 27f;

    public float highSpeedDragCoeff = 0.016f;

    public float rollingResistance = 12f;

    // --- Steering (degrees) — simcade tuned: limited max + slow build so WSAD doesn't snap into oversteer ---

    /** Hard cap on steer angle at low speed (deg). */
    public float maxSteeringAngle = 36f;
    /** Steer angle the cap fades down to at top speed (deg). */
    public float minSteeringAngle = 11f;
    /** How fast the wheel can turn TOWARD a held key (deg/s). */
    public float steeringSpeed = 90f;
    /** How fast the wheel returns to centre when keys released (deg/s). */
    public float steeringReturnSpeed = 140f;

    // --- Pacejka — front axle ---

    public float pacejkaFrontB = 8.56f;
    public float pacejkaFrontC = 1.3f;
    /** Front peak grip — slightly higher than rear for mild oversteer bias. */
    public float pacejkaFrontD = 244f;
    public float pacejkaFrontE = -1.09f;

    // --- Pacejka — rear axle ---

    public float pacejkaRearB = 7.9f;
    public float pacejkaRearC = 1.3f;
    /** Rear peak grip — below front = mild oversteer on throttle + steer. */
    public float pacejkaRearD = 222f;
    public float pacejkaRearE = -1.31f;

    /** Rear grip scale while the handbrake is held (drift trigger). */
    public float handbrakeRearGripMultiplier = 0.36f;
    public float pacejkaMinSpeedForGrip = 2.0f;

    /** Min |vLong| (m/s) in wheel frame for slip denominator. */
    public float minLongitudinalSpeedForSlip = 1.2f;

    /** Below this chassis speed (m/s), slip angle is zero. */
    public float minChassisSpeedForSlip = 0.8f;

    /** Chassis speed (m/s) at which slip/grip reach full strength. */
    public float lowSpeedGripFadeSpeed = 3.0f;

    /** Yaw torque per rad steer (N·m) — only supplements Pacejka at low speed. */
    public float steeringYawTorqueGain = 16f;

    /** Max yaw assist below this speed (m/s); above = Pacejka only. */
    public float steeringYawAssistMaxSpeed = 5f;

    /** Min speed before yaw-stability assist applies (m/s). */
    public float stabilityMinChassisSpeed = 4f;

    /** Reference normal load per axle (N) for load-sensitive grip; 0 = use static split. */
    public float referenceNormalLoadN = 0f;

    // --- Surface friction (mu) — written each frame by TerrainSystem ---

    /** Current surface mu (multiplier on Pacejka peak). Updated by TerrainSystem. */
    public float surfaceMu = 1f;
    /** Tarmac / road mu. 1.0 = full grip baseline. */
    public float surfaceMuRoad = 1f;
    /** Off-road / grass mu. Not too low — grass should slow you, not turn into ice. */
    public float surfaceMuGrass = 0.55f;

    // --- Off-road drag tuning — applied by TerrainSystem when not on road ---

    /** Engine force scale on grass (1 = road). Drops drive torque so wheels don't just spin. */
    public float grassEngineMultiplier = 0.72f;
    /** Top speed scale on grass (1 = road). */
    public float grassMaxSpeedMultiplier = 0.72f;
    /** Rolling resistance scale on grass. >1 = car bogs down smoothly off-road. */
    public float grassRollingResistanceMultiplier = 2.5f;
    /** Extra aero-drag added on grass (N·s²/m²). Speed-squared term, helps cap top end naturally. */
    public float grassAeroDragBonus = 0.04f;
    /** Box2D linear damping scale on grass. Higher = car decelerates harder when off track. */
    public float grassLinearDampingMultiplier = 3.5f;
    /** Box2D angular damping scale on grass. Higher = grass kills yaw fast (no endless donuts). */
    public float grassAngularDampingMultiplier = 5f;

    /** Slip angle (rad) sent to Pacejka. Wide enough to keep the curve smooth deep into the drift. */
    public float pacejkaMaxSlipAngle = 0.45f;

    /** Below this chassis speed (m/s), lateral Pacejka forces scale down. */
    public float pacejkaChassisSpeedRef = 3f;

    /**
     * Min falloff multiplier past peak — rear. Lower = more drift.
     */
    public float slideForceFalloffMinRear = 0.69f;

    /** Min falloff past peak — front. */
    public float slideForceFalloffMinFront = 0.848f;

    /** Load transfer to front when turning. Higher = rear unloads = oversteer. */
    public float turnLoadTransfer = 0.104f;

    /** Lateral damping while turning — keep low; high values scrub corner speed. */
    public float turningLateralDamping = 8f;

    /** Extra torque opposing yaw when omega is very large (smooths out runaway spin only). */
    public float spinAngularDamping = 13f;
    /** Omega (rad/s) at which spinAngularDamping reaches full strength. */
    public float spinDampingOmegaStart = 2.2f;

    /** Straight-line: cancels sideslip velocity (N per m/s lateral). */
    public float stabilityLateralDamping = 18f;

    /** Engine scale when rear is sliding past peak slip (straight-line wheelspin only). */
    public float tractionControlStrength = 0.73f;

    /** Extra rear grip cut when throttle + steer (classic RWD power oversteer). */
    public float powerOversteerGripMultiplier = 0.932f;

    // --- Simcade drift / counter-steer assists ---

    /**
     * When the player flicks steer opposite to the current yaw rate the wheel rate is multiplied
     * by this — fast kontra for responsive drift control.
     */
    public float counterSteerSpeedMultiplier = 2.5f;
    /** |omega| (rad/s) above which the car is considered "rotating" for counter-steer detection. */
    public float counterSteerYawThreshold = 0.4f;
    /** Min chassis speed (m/s) before any alignment / counter-steer assist kicks in. */
    public float alignmentAssistMinSpeed = 2.5f;
    /** Base torque aligning chassis with velocity vector (N·m per rad sideslip). */
    public float alignmentAssistStrength = 19.5f;
    /** Boost on alignmentAssistStrength while the driver is countering the current yaw. */
    public float counterSteerAlignmentBoost = 1.74f;
    /** Anti-yaw torque (N·m·s/rad) added when player is fully easing off or counter-steering. */
    public float idleYawDamping = 16.5f;

    // --- Box2D body damping (read by TerrainSystem and written to body each frame) ---

    public float linearDamping = 0.7f;
    public float angularDamping = 3.0f;

    // --- Skidmarks (rear wheels, past Pacejka peak grip) ---

    /** Slip (rad) below this = straight line, stop marks immediately. */
    public float rearSkidSlipStraightThreshold = 0.045f;

    /** Multiplier on estimated peak slip to enter skid (1.05 = just past peak). */
    public float rearSkidPastPeakMultiplier = 1.02f;

    /** Min speed (m/s) to lay marks. */
    public float rearSkidMinSpeed = 3f;

    public float skidmarkSpacing = 0.22f;

    /** Width of each single-wheel stripe (meters). */
    public float skidmarkWheelWidth = 0.18f;

    public int skidmarkMaxSegmentsPerWheel = 500;
    public float skidmarkFadeSeconds = 12f;

    // --- Debug ---

    public boolean debugOverlayEnabled = true;
    public boolean debugDrawPacejkaCurve = true;
    public float debugPacejkaCurveAlphaMax = 0.45f;
    public float debugHudX = 12f;
    public float debugHudY = 12f;

    // --- Runtime telemetry ---

    public float currentSteeringAngle = 0f;
    /** Render hysteresis: -1 left turn sprite, 0 straight, +1 right turn sprite. */
    public int steerSpriteState = 0;
    public float frontSlipAngle = 0f;
    public float rearSlipAngle = 0f;
    /** Slip used for Pacejka (clamped / stabilized). */
    public float frontSlipAngleEffective = 0f;
    public float rearSlipAngleEffective = 0f;
    public float rearPeakSlipAngle = 0f;
    public float frontPeakSlipAngle = 0f;
    public float frontNormalLoadN = 0f;
    public float rearNormalLoadN = 0f;
    public float frontLateralForce = 0f;
    public float rearLateralForce = 0f;

    public final PacejkaTireModel.AxleRuntime frontTireRuntime =
            new PacejkaTireModel.AxleRuntime();
    public final PacejkaTireModel.AxleRuntime rearTireRuntime =
            new PacejkaTireModel.AxleRuntime();
    public float frontGripPercent = 0f;
    public float rearGripPercent = 0f;
    public float displaySpeed = 0f;
    public float displayForwardSpeed = 0f;

    public final Vector2 frontAxleWorld = new Vector2();
    public final Vector2 rearAxleWorld = new Vector2();
    public final Vector2 rearLeftWheelWorld = new Vector2();
    public final Vector2 rearRightWheelWorld = new Vector2();
    public final Vector2 forwardDirWorld = new Vector2();
    public final Vector2 lateralDirWorld = new Vector2();

    public boolean rearSkidActive = false;
    public boolean rearLeftSkidActive = false;
    public boolean rearRightSkidActive = false;
}
