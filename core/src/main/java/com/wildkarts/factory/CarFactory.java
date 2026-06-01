package com.wildkarts.factory;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.MassData;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import com.wildkarts.components.CarComponent;
import com.wildkarts.components.InputComponent;
import com.wildkarts.components.NetworkSyncComponent;
import com.wildkarts.components.PhysicsComponent;

/**
 * Fabryka encji aut z wymaganymi komponentami i ciałem Box2D.
 *
 * <p>Centralizuje tworzenie encji — łatwo rozszerzyć o różne typy aut,
 * przeciwników AI lub zdalnych graczy w multiplayerze.</p>
 */
public class CarFactory {

    private final World world;
    private final Engine engine;

    /**
     * Tworzy fabrykę powiązaną ze światem fizyki i silnikiem Ashley.
     *
     * @param world  świat Box2D
     * @param engine silnik encji Ashley
     */
    public CarFactory(World world, Engine engine) {
        this.world = world;
        this.engine = engine;
    }

    /**
     * Tworzy kompletne auto w podanej pozycji z domyślnymi parametrami.
     *
     * @param x     pozycja startowa X w metrach świata
     * @param y     pozycja startowa Y w metrach świata
     * @param angle kąt startowy w radianach
     * @return utworzona encja (już dodana do silnika)
     */
    public Entity createCar(float x, float y, float angle) {
        Entity entity = new Entity();

        InputComponent input = new InputComponent();
        CarComponent car = new CarComponent();
        PhysicsComponent physics = new PhysicsComponent();

        physics.body = createCarBody(x, y, angle, physics.widthMeters, physics.heightMeters, car);
        physics.body.setUserData(entity);

        physics.prevPosition.set(x, y);
        physics.prevAngle = angle;
        entity.add(input);
        entity.add(car);
        entity.add(physics);

        engine.addEntity(entity);
        return entity;
    }

    /**
     * Tworzy auto z niestandardowymi parametrami {@link CarComponent}.
     * Przydatne dla różnych klas pojazdów (lekkie, ciężkie, driftowe itd.).
     *
     * @param x          pozycja startowa X
     * @param y          pozycja startowa Y
     * @param angle      kąt startowy w radianach
     * @param customCar  wstępnie skonfigurowany komponent auta
     * @return utworzona encja (już dodana do silnika)
     */
    public Entity createCar(float x, float y, float angle, CarComponent customCar) {
        Entity entity = new Entity();

        InputComponent input = new InputComponent();
        PhysicsComponent physics = new PhysicsComponent();

        physics.body = createCarBody(x, y, angle, physics.widthMeters, physics.heightMeters, customCar);
        physics.body.setUserData(entity);

        entity.add(input);
        entity.add(customCar);
        entity.add(physics);

        engine.addEntity(entity);
        return entity;
    }

    /**
     * Tworzy dynamiczne ciało Box2D dla auta.
     *
     * <p>Konfiguracja: typ dynamiczny, kształt prostokątny, tłumienie z {@link CarComponent},
     * umiarkowana gęstość, niska restytucja (auta mało odbijają).</p>
     */
    private Body createCarBody(float x, float y, float angle,
                               float width, float height, CarComponent car) {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.DynamicBody;
        bodyDef.position.set(x, y);
        bodyDef.angle = angle;
        bodyDef.linearDamping = car.linearDamping;
        bodyDef.angularDamping = car.angularDamping;
        bodyDef.bullet = true;

        Body body = world.createBody(bodyDef);

        PolygonShape shape = new PolygonShape();
        shape.setAsBox(width / 2f, height / 2f);

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.density = 1.0f;
        fixtureDef.friction = 0.3f;
        fixtureDef.restitution = 0.1f;

        body.createFixture(fixtureDef);
        shape.dispose();

        float inertia = car.inertia;
        if (inertia <= 0f)
            inertia = (1f / 12f) * car.mass * (width * width + height * height);
        MassData massData = new MassData();
        massData.mass = car.mass;
        massData.I = inertia;
        massData.center.setZero();
        body.setMassData(massData);

        return body;
    }

    /**
     * Tworzy auto zdalnego gracza sterowane aktualizacjami sieciowymi.
     * Nie ma {@link InputComponent}, ale ma {@link NetworkSyncComponent}.
     *
     * @param x     pozycja startowa X
     * @param y     pozycja startowa Y
     * @param angle kąt startowy w radianach
     * @return encja zdalnego gracza
     */
    public Entity createRemoteCar(float x, float y, float angle) {
        Entity entity = createCar(x, y, angle);
        entity.remove(InputComponent.class);
        entity.add(new NetworkSyncComponent());
        return entity;
    }
}
