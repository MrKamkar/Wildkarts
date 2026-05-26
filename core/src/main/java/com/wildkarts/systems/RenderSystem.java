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
import com.wildkarts.components.InputComponent;
import com.wildkarts.components.PhysicsComponent;

/**
 * Renders car entities as sprites (SpriteBatch) with optional Box2D debug overlay.
 *
 * Sprite selection based on steering input:
 *   - No steering  → car_straight sprite
 *   - Steering right (D) → car_turn sprite (default orientation)
 *   - Steering left  (A) → car_turn sprite, horizontally flipped
 */
public class RenderSystem extends IteratingSystem {

    /**
     * Visual scale multiplier for the car sprite. The PNG has padding around
     * the car body, so the sprite must be drawn larger than the physics
     * hitbox for the visible car to match the collision shape.
     */
    private static final float SPRITE_SCALE = 2.0f;

    private final SpriteBatch batch;
    private final Box2DDebugRenderer debugRenderer;
    private final OrthographicCamera camera;
    private final World world;
    private final PhysicsSystem physicsSystem;
    private boolean debugDraw = false;

    private final TextureRegion carStraightRegion;
    private final TextureRegion carTurnRegion;
    private final TextureRegion carTurnFlippedRegion;

    private final ComponentMapper<PhysicsComponent> physicsMapper =
            ComponentMapper.getFor(PhysicsComponent.class);
    private final ComponentMapper<CarComponent> carMapper =
            ComponentMapper.getFor(CarComponent.class);
    private final ComponentMapper<InputComponent> inputMapper =
            ComponentMapper.getFor(InputComponent.class);

    public RenderSystem(OrthographicCamera camera, World world, PhysicsSystem physicsSystem,
                        Texture carStraight, Texture carTurn) {
        super(Family.all(PhysicsComponent.class).get());
        this.camera = camera;
        this.world = world;
        this.physicsSystem = physicsSystem;
        this.batch = new SpriteBatch();
        this.debugRenderer = new Box2DDebugRenderer();
        this.carStraightRegion = new TextureRegion(carStraight);
        this.carTurnRegion = new TextureRegion(carTurn);
        this.carTurnFlippedRegion = new TextureRegion(carTurn);
        this.carTurnFlippedRegion.flip(false, true);
    }

    @Override
    public void update(float deltaTime) {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        super.update(deltaTime);
        batch.end();

        if (debugDraw) {
            debugRenderer.render(world, camera.combined);
        }
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        PhysicsComponent physics = physicsMapper.get(entity);
        Body body = physics.body;

        if (body == null) return;

        float alpha = physicsSystem != null ? physicsSystem.getInterpolationAlpha() : 1f;

        Vector2 pos = new Vector2(physics.prevPosition).lerp(body.getPosition(), alpha);

        float currentAngle = body.getAngle();
        float prevAngle = physics.prevAngle;
        float diff = (currentAngle - prevAngle) % MathUtils.PI2;
        if (Float.isNaN(diff)) diff = 0f;
        if (diff > MathUtils.PI) diff -= MathUtils.PI2;
        else if (diff < -MathUtils.PI) diff += MathUtils.PI2;
        float angle = prevAngle + diff * alpha;

        InputComponent input = inputMapper.has(entity) ? inputMapper.get(entity) : null;
        float steer = input != null ? input.steering : 0f;

        TextureRegion region;

        if (steer < -0.01f) {
            region = carTurnRegion;
        } else if (steer > 0.01f) {
            region = carTurnFlippedRegion;
        } else {
            region = carStraightRegion;
        }

        float spriteW = physics.heightMeters * SPRITE_SCALE;
        float spriteH = physics.widthMeters * SPRITE_SCALE;

        float drawX = pos.x - spriteW / 2f;
        float drawY = pos.y - spriteH / 2f;
        float originX = spriteW / 2f;
        float originY = spriteH / 2f;
        float rotation = angle * MathUtils.radiansToDegrees + 90f;

        batch.draw(region, drawX, drawY, originX, originY, spriteW, spriteH, 1f, 1f, rotation);
    }

    public void toggleDebugDraw() {
        debugDraw = !debugDraw;
    }

    public void dispose() {
        batch.dispose();
        debugRenderer.dispose();
    }
}
