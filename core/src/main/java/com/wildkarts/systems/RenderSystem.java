package com.wildkarts.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer;
import com.badlogic.gdx.physics.box2d.World;
import com.wildkarts.components.CarComponent;
import com.wildkarts.components.PhysicsComponent;

/**
 * Renders car entities using ShapeRenderer + optional Box2D debug overlay.
 * 
 * This is a placeholder renderer — in production, this would be replaced
 * by a sprite-based system. The car is drawn as a colored rectangle with
 * a direction indicator (triangle at the front).
 */
public class RenderSystem extends IteratingSystem {

    private final ShapeRenderer shapeRenderer;
    private final Box2DDebugRenderer debugRenderer;
    private final OrthographicCamera camera;
    private final World world;
    private final PhysicsSystem physicsSystem;
    private boolean debugDraw = false;

    // Car color scheme
    private static final Color CAR_BODY_COLOR = new Color(0.2f, 0.6f, 1.0f, 1.0f);
    private static final Color CAR_FRONT_COLOR = new Color(1.0f, 0.9f, 0.2f, 1.0f);
    private static final Color CAR_DRIFT_COLOR = new Color(1.0f, 0.3f, 0.2f, 1.0f);

    private final ComponentMapper<PhysicsComponent> physicsMapper =
            ComponentMapper.getFor(PhysicsComponent.class);
    private final ComponentMapper<CarComponent> carMapper =
            ComponentMapper.getFor(CarComponent.class);

    // Reusable vectors for corner calculations
    private final Vector2 corner = new Vector2();
    private final Vector2 frontTip = new Vector2();

    public RenderSystem(OrthographicCamera camera, World world, PhysicsSystem physicsSystem) {
        super(Family.all(PhysicsComponent.class).get());
        this.camera = camera;
        this.world = world;
        this.physicsSystem = physicsSystem;
        this.shapeRenderer = new ShapeRenderer();
        this.debugRenderer = new Box2DDebugRenderer();
    }

    @Override
    public void update(float deltaTime) {
        shapeRenderer.setProjectionMatrix(camera.combined);

        // Draw filled car bodies
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        super.update(deltaTime);
        shapeRenderer.end();

        // Box2D debug overlay
        if (debugDraw) {
            debugRenderer.render(world, camera.combined);
        }
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        PhysicsComponent physics = physicsMapper.get(entity);
        CarComponent car = carMapper.get(entity);
        Body body = physics.body;

        if (body == null) return;

        float alpha = physicsSystem != null ? physicsSystem.getInterpolationAlpha() : 1f;

        // Visual interpolation between previous and current physics state (needed for multiplayer)
        Vector2 pos = new Vector2(physics.prevPosition).lerp(body.getPosition(), alpha);
        
        // Shortest path angle interpolation
        float currentAngle = body.getAngle();
        float prevAngle = physics.prevAngle;
        float diff = (currentAngle - prevAngle) % ((float)Math.PI * 2);
        if (diff != diff) diff = 0f;
        if (diff > Math.PI) diff -= Math.PI * 2;
        else if (diff < -Math.PI) diff += Math.PI * 2;
        float angle = prevAngle + diff * alpha;
        float hw = physics.widthMeters / 2f;
        float hh = physics.heightMeters / 2f;

        boolean isDrifting = car != null
                ? Math.abs(car.rearSlipAngle) > 0.12f || Math.abs(car.frontSlipAngle) > 0.12f
                : Math.abs(getLateralSpeed(body, angle)) > 1.5f;

        // Draw car body as a rotated rectangle
        Color bodyColor = isDrifting ? CAR_DRIFT_COLOR : CAR_BODY_COLOR;
        drawRotatedRect(pos.x, pos.y, physics.widthMeters, physics.heightMeters, angle, bodyColor);

        // Draw front indicator (small triangle)
        float frontX = pos.x + MathUtils.cos(angle + MathUtils.HALF_PI) * hh * 0.7f;
        float frontY = pos.y + MathUtils.sin(angle + MathUtils.HALF_PI) * hh * 0.7f;
        drawDirectionIndicator(frontX, frontY, angle, hw * 0.5f, CAR_FRONT_COLOR);
    }

    private float getLateralSpeed(Body body, float angle) {
        Vector2 vel = body.getLinearVelocity();
        float latDirX = MathUtils.cos(angle);
        float latDirY = MathUtils.sin(angle);
        return vel.x * latDirX + vel.y * latDirY;
    }

    private void drawRotatedRect(float cx, float cy, float w, float h, float angle, Color color) {
        shapeRenderer.setColor(color);
        float hw = w / 2f;
        float hh = h / 2f;
        float cos = MathUtils.cos(angle);
        float sin = MathUtils.sin(angle);

        // 4 corners of the rotated rectangle
        float x1 = cx + (-hw * cos - (-hh) * sin);
        float y1 = cy + (-hw * sin + (-hh) * cos);
        float x2 = cx + (hw * cos - (-hh) * sin);
        float y2 = cy + (hw * sin + (-hh) * cos);
        float x3 = cx + (hw * cos - hh * sin);
        float y3 = cy + (hw * sin + hh * cos);
        float x4 = cx + (-hw * cos - hh * sin);
        float y4 = cy + (-hw * sin + hh * cos);

        // Two triangles to form the rectangle
        shapeRenderer.triangle(x1, y1, x2, y2, x3, y3);
        shapeRenderer.triangle(x1, y1, x3, y3, x4, y4);
    }

    private void drawDirectionIndicator(float cx, float cy, float angle, float size, Color color) {
        shapeRenderer.setColor(color);
        float cos = MathUtils.cos(angle + MathUtils.HALF_PI);
        float sin = MathUtils.sin(angle + MathUtils.HALF_PI);
        float cosL = MathUtils.cos(angle + MathUtils.PI);
        float sinL = MathUtils.sin(angle + MathUtils.PI);

        float tipX = cx + cos * size;
        float tipY = cy + sin * size;
        float leftX = cx + cosL * size * 0.5f - cos * size * 0.3f;
        float leftY = cy + sinL * size * 0.5f - sin * size * 0.3f;
        float rightX = cx - cosL * size * 0.5f - cos * size * 0.3f;
        float rightY = cy - sinL * size * 0.5f - sin * size * 0.3f;

        shapeRenderer.triangle(tipX, tipY, leftX, leftY, rightX, rightY);
    }

    public void toggleDebugDraw() {
        debugDraw = !debugDraw;
    }

    public void dispose() {
        shapeRenderer.dispose();
        debugRenderer.dispose();
    }
}
