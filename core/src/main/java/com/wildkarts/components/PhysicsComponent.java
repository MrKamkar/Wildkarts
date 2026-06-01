package com.wildkarts.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;

/**
 * Przechowuje referencję do ciała Box2D i jego wymiary.
 * Komponent łączy encje Ashley ECS ze światem fizyki Box2D.
 *
 * <p>W multiplayerze: pozycję, prędkość i kąt można odczytać bezpośrednio z {@link Body}.</p>
 */
public class PhysicsComponent implements Component {

    /** Dynamiczne ciało Box2D reprezentujące encję w świecie fizyki. */
    public Body body;

    /** Poprzednia pozycja do interpolacji wizualnej. */
    public final Vector2 prevPosition = new Vector2();

    /** Poprzedni kąt do interpolacji wizualnej. */
    public float prevAngle = 0f;

    /** Szerokość kształtu fizycznego w metrach (jednostki Box2D). */
    public float widthMeters = 1.2f;

    /** Wysokość (długość) kształtu fizycznego w metrach (jednostki Box2D). */
    public float heightMeters = 2.4f;
}
