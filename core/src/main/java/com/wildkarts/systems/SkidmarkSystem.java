package com.wildkarts.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.wildkarts.components.CarComponent;
import com.wildkarts.components.InputComponent;
import com.wildkarts.components.PhysicsComponent;

/**
 * Niezależne smugi poślizgu tylnych kół (Pacejka po przekroczeniu szczytu przyczepności).
 */
public class SkidmarkSystem extends IteratingSystem {

    private static final Color SKID_COLOR = new Color(0.1f, 0.1f, 0.1f, 0.82f);

    private final ComponentMapper<CarComponent> carMapper = ComponentMapper.getFor(CarComponent.class);

    private final OrthographicCamera camera;
    private final ShapeRenderer shapeRenderer;
    private final Array<WheelStripe> leftStripes = new Array<>();
    private final Array<WheelStripe> rightStripes = new Array<>();

    private final WheelTracker leftTracker = new WheelTracker();
    private final WheelTracker rightTracker = new WheelTracker();
    private final Vector2 perpScratch = new Vector2();

    private int maxSegmentsPerWheel = 500;

    /**
     * Tworzy system smug poślizgu powiązany z kamerą świata.
     *
     * @param camera kamera ortograficzna
     */
    public SkidmarkSystem(OrthographicCamera camera) {
        super(Family.all(InputComponent.class, CarComponent.class, PhysicsComponent.class).get());
        this.camera = camera;
        this.shapeRenderer = new ShapeRenderer();
    }

    /** Aktualizuje smugi, wygasza stare segmenty i rysuje je na ekranie. */
    @Override
    public void update(float deltaTime) {
        super.update(deltaTime);
        fadeStripes(leftStripes, deltaTime);
        fadeStripes(rightStripes, deltaTime);
        drawStripes();
    }

    /** Dodaje nowe segmenty smug dla obu tylnych kół auta. */
    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        CarComponent car = carMapper.get(entity);
        if (car == null) return;

        maxSegmentsPerWheel = car.skidmarkMaxSegmentsPerWheel;

        updateWheel(leftTracker, leftStripes, car.rearLeftWheelWorld, car.rearLeftSkidActive, car);
        updateWheel(rightTracker, rightStripes, car.rearRightWheelWorld, car.rearRightSkidActive, car);
    }

    /** Śledzi odległość między pozycjami koła i dodaje segment smugi co określony odstęp. */
    private void updateWheel(WheelTracker tracker, Array<WheelStripe> stripes, Vector2 wheelPos,
                             boolean active, CarComponent car) {
        if (!active) {
            tracker.reset();
            return;
        }

        if (tracker.hasLast)
            tracker.distanceSinceLast += wheelPos.dst(tracker.lastPos);

        if (!tracker.hasLast || tracker.distanceSinceLast >= car.skidmarkSpacing) {
            if (tracker.hasLast)
                addWheelStripe(stripes, tracker.lastPos, wheelPos, car);
            tracker.lastPos.set(wheelPos);
            tracker.hasLast = true;
            tracker.distanceSinceLast = 0f;
        }
    }

    /** Tworzy trójkątny segment smugi między dwoma pozycjami koła. */
    private void addWheelStripe(Array<WheelStripe> stripes, Vector2 from, Vector2 to, CarComponent car) {
        while (stripes.size >= maxSegmentsPerWheel)
            stripes.removeIndex(0);

        float halfW = car.skidmarkWheelWidth * 0.5f;
        float dx = to.x - from.x;
        float dy = to.y - from.y;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) return;

        perpScratch.set(-dy / len * halfW, dx / len * halfW);

        WheelStripe stripe = new WheelStripe();
        stripe.lx1 = from.x + perpScratch.x;
        stripe.ly1 = from.y + perpScratch.y;
        stripe.rx1 = from.x - perpScratch.x;
        stripe.ry1 = from.y - perpScratch.y;
        stripe.lx2 = to.x + perpScratch.x;
        stripe.ly2 = to.y + perpScratch.y;
        stripe.rx2 = to.x - perpScratch.x;
        stripe.ry2 = to.y - perpScratch.y;
        stripe.age = 0f;
        stripe.maxAge = car.skidmarkFadeSeconds;
        stripes.add(stripe);
    }

    /** Usuwa segmenty, które przekroczyły czas wygaszania. */
    private void fadeStripes(Array<WheelStripe> stripes, float deltaTime) {
        for (int i = stripes.size - 1; i >= 0; i--) {
            WheelStripe s = stripes.get(i);
            s.age += deltaTime;
            if (s.age >= s.maxAge)
                stripes.removeIndex(i);
        }
    }

    /** Rysuje wszystkie aktywne smugi obu kół. */
    private void drawStripes() {
        if (leftStripes.size == 0 && rightStripes.size == 0) return;

        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        drawStripeArray(leftStripes);
        drawStripeArray(rightStripes);

        shapeRenderer.end();
    }

    /** Rysuje jedną listę segmentów z malejącą przezroczystością. */
    private void drawStripeArray(Array<WheelStripe> stripes) {
        for (WheelStripe s : stripes) {
            float t = 1f - s.age / s.maxAge;
            shapeRenderer.setColor(SKID_COLOR.r, SKID_COLOR.g, SKID_COLOR.b, SKID_COLOR.a * t);
            shapeRenderer.triangle(s.lx1, s.ly1, s.rx1, s.ry1, s.lx2, s.ly2);
            shapeRenderer.triangle(s.lx1, s.ly1, s.lx2, s.ly2, s.rx2, s.ry2);
        }
    }

    /** Zwalnia {@link ShapeRenderer}. */
    public void dispose() {
        shapeRenderer.dispose();
    }

    /** Śledzi ostatnią znaną pozycję koła między klatkami. */
    private static class WheelTracker {
        final Vector2 lastPos = new Vector2();
        boolean hasLast = false;
        float distanceSinceLast = 0f;

        void reset() {
            hasLast = false;
            distanceSinceLast = 0f;
        }
    }

    /** Pojedynczy segment smugi (cztery rogi trójkątów). */
    private static class WheelStripe {
        float lx1, ly1, rx1, ry1, lx2, ly2, rx2, ry2;
        float age;
        float maxAge;
    }
}
