package com.wildkarts.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer;
import com.badlogic.gdx.physics.box2d.World;
import com.wildkarts.components.CarComponent;
import com.wildkarts.components.PhysicsComponent;
import com.wildkarts.util.SpriteAnchorUtil;

/**
 * Renderuje encje aut jako sprite'y ({@link SpriteBatch}) z opcjonalną nakładką debug Box2D.
 *
 * <p>Trzy sprite'y: {@code car_straight}, {@code car_turn_left}, {@code car_turn_right}
 * (osobne pliki PNG — bez lustrzanego odbicia). Środki wizualne wyrównane do referencji
 * prostej, aby zamiana grafiki nie przesuwała auta na ekranie.</p>
 */
public class RenderSystem extends IteratingSystem {

    private static final float SPRITE_SCALE = 2.0f;
    private static final float TURN_SPRITE_ANGLE_ON = 6f;
    private static final float TURN_SPRITE_ANGLE_OFF = 2.5f;

    private final SpriteBatch batch;
    private final Box2DDebugRenderer debugRenderer;
    private final OrthographicCamera camera;
    private final World world;
    private final PhysicsSystem physicsSystem;
    private boolean debugDraw = false;

    private final TextureRegion carStraightRegion;
    private final TextureRegion carTurnLeftRegion;
    private final TextureRegion carTurnRightRegion;

    private final SpriteAnchorUtil.Anchor straightAnchor;
    private final SpriteAnchorUtil.Anchor turnLeftAnchor;
    private final SpriteAnchorUtil.Anchor turnRightAnchor;

    private final Vector2 anchorCorrection = new Vector2();
    private final Vector2 drawPos = new Vector2();

    private final ComponentMapper<PhysicsComponent> physicsMapper =
            ComponentMapper.getFor(PhysicsComponent.class);
    private final ComponentMapper<CarComponent> carMapper =
            ComponentMapper.getFor(CarComponent.class);

    /**
     * Tworzy system renderowania aut z trzema wariantami sprite'a i kotwicami wyrównania.
     *
     * @param camera          kamera świata
     * @param world           świat Box2D (do debug draw)
     * @param physicsSystem   system fizyki (do interpolacji alpha)
     * @param carStraight     tekstura jazdy prosto
     * @param straightAnchor  kotwica sprite'a prostej
     * @param carTurnLeft     tekstura skrętu w lewo
     * @param turnLeftAnchor  kotwica skrętu w lewo
     * @param carTurnRight    tekstura skrętu w prawo
     * @param turnRightAnchor kotwica skrętu w prawo
     */
    public RenderSystem(OrthographicCamera camera, World world, PhysicsSystem physicsSystem,
                        Texture carStraight, SpriteAnchorUtil.Anchor straightAnchor,
                        Texture carTurnLeft, SpriteAnchorUtil.Anchor turnLeftAnchor,
                        Texture carTurnRight, SpriteAnchorUtil.Anchor turnRightAnchor) {
        super(Family.all(PhysicsComponent.class).get());
        this.camera = camera;
        this.world = world;
        this.physicsSystem = physicsSystem;
        this.batch = new SpriteBatch();
        this.debugRenderer = new Box2DDebugRenderer();

        this.straightAnchor = straightAnchor != null ? straightAnchor : new SpriteAnchorUtil.Anchor(0f, 0f);
        this.turnLeftAnchor = turnLeftAnchor != null ? turnLeftAnchor : this.straightAnchor;
        this.turnRightAnchor = turnRightAnchor != null ? turnRightAnchor : this.straightAnchor;

        this.carStraightRegion = new TextureRegion(carStraight);
        this.carTurnLeftRegion = new TextureRegion(carTurnLeft);
        this.carTurnRightRegion = new TextureRegion(carTurnRight);
    }

    /** Rysuje wszystkie auta, potem opcjonalnie debug Box2D. */
    @Override
    public void update(float deltaTime) {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        super.update(deltaTime);
        batch.end();

        if (debugDraw)
            debugRenderer.render(world, camera.combined);
    }

    /** Rysuje jedno auto z interpolacją pozycji i wyborem sprite'a skrętu. */
    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        PhysicsComponent physics = physicsMapper.get(entity);
        Body body = physics.body;
        if (body == null) return;

        float alpha = physicsSystem != null ? physicsSystem.getInterpolationAlpha() : 1f;

        drawPos.set(physics.prevPosition).lerp(body.getPosition(), alpha);

        float currentAngle = body.getAngle();
        float prevAngle = physics.prevAngle;
        float diff = (currentAngle - prevAngle) % MathUtils.PI2;
        if (Float.isNaN(diff)) diff = 0f;
        if (diff > MathUtils.PI) diff -= MathUtils.PI2;
        else if (diff < -MathUtils.PI) diff += MathUtils.PI2;
        float angle = prevAngle + diff * alpha;

        CarComponent car = carMapper.has(entity) ? carMapper.get(entity) : null;
        int steerSprite = updateSteerSpriteState(car);

        TextureRegion region;
        SpriteAnchorUtil.Anchor spriteAnchor = straightAnchor;

        if (steerSprite < 0) {
            region = carTurnRightRegion;
            spriteAnchor = turnRightAnchor;
        } else if (steerSprite > 0) {
            region = carTurnLeftRegion;
            spriteAnchor = turnLeftAnchor;
        } else {
            region = carStraightRegion;
        }

        float spriteW = physics.heightMeters * SPRITE_SCALE;
        float spriteH = physics.widthMeters * SPRITE_SCALE;
        float rotation = angle * MathUtils.radiansToDegrees + 90f;

        applyAnchorCorrection(straightAnchor, spriteAnchor, spriteW, spriteH, rotation, anchorCorrection);

        float originX = spriteW / 2f;
        float originY = spriteH / 2f;
        float drawX = drawPos.x - originX + anchorCorrection.x;
        float drawY = drawPos.y - originY + anchorCorrection.y;

        batch.draw(region, drawX, drawY, originX, originY, spriteW, spriteH, 1f, 1f, rotation);
    }

    /**
     * Aktualizuje stan histerezy sprite'a skrętu (prosto / lewo / prawo).
     *
     * @return -1 prawo, 0 prosto, 1 lewo
     */
    private static int updateSteerSpriteState(CarComponent car) {
        if (car == null) return 0;

        float steerAngle = car.currentSteeringAngle;
        if (car.steerSpriteState == 0) {
            if (steerAngle <= -TURN_SPRITE_ANGLE_ON)
                car.steerSpriteState = -1;
            else if (steerAngle >= TURN_SPRITE_ANGLE_ON)
                car.steerSpriteState = 1;
        } else if (car.steerSpriteState < 0) {
            if (steerAngle > -TURN_SPRITE_ANGLE_OFF)
                car.steerSpriteState = 0;
        } else if (car.steerSpriteState > 0 && steerAngle < TURN_SPRITE_ANGLE_OFF) {
            car.steerSpriteState = 0;
        }

        return car.steerSpriteState;
    }

    /**
     * Przesuwa pozycję rysowania, aby {@code spriteAnchor} pokrywał się z {@code referenceAnchor}
     * w miejscu ciała fizycznego.
     */
    private static void applyAnchorCorrection(SpriteAnchorUtil.Anchor referenceAnchor,
                                              SpriteAnchorUtil.Anchor spriteAnchor,
                                              float spriteW, float spriteH, float rotationDeg,
                                              Vector2 out) {
        float du = referenceAnchor.offsetU - spriteAnchor.offsetU;
        float dv = referenceAnchor.offsetV - spriteAnchor.offsetV;
        if (Math.abs(du) < 0.00001f && Math.abs(dv) < 0.00001f) {
            out.setZero();
            return;
        }
        float localX = du * spriteW;
        float localY = dv * spriteH;
        float rad = rotationDeg * MathUtils.degreesToRadians;
        float cos = MathUtils.cos(rad);
        float sin = MathUtils.sin(rad);
        out.x = localX * cos - localY * sin;
        out.y = localX * sin + localY * cos;
    }

    /** Przełącza rysowanie debug Box2D. */
    public void toggleDebugDraw() {
        debugDraw = !debugDraw;
    }

    /** Zwalnia zasoby renderowania. */
    public void dispose() {
        batch.dispose();
        debugRenderer.dispose();
    }
}
