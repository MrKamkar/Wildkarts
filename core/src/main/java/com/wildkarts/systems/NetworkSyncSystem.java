package com.wildkarts.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.wildkarts.components.NetworkSyncComponent;
import com.wildkarts.components.PhysicsComponent;

/**
 * Interpoluje ciała fizyczne zdalnych graczy w kierunku ostatnich pozycji z serwera,
 * aby ruch był płynny zamiast skokowy.
 */
public class NetworkSyncSystem extends IteratingSystem {

    /** Opóźnienie renderowania dla interpolacji migawek (ms). */
    private static final long RENDER_DELAY_MS = 100;

    private static final ComponentMapper<NetworkSyncComponent> syncMapper =
            ComponentMapper.getFor(NetworkSyncComponent.class);
    private static final ComponentMapper<PhysicsComponent> physicsMapper =
            ComponentMapper.getFor(PhysicsComponent.class);

    /** Tworzy system dla encji z synchronizacją sieciową i fizyką. */
    public NetworkSyncSystem() {
        super(Family.all(NetworkSyncComponent.class, PhysicsComponent.class).get());
    }

    /**
     * Interpoluje lub ekstrapoluje pozycję ciała na podstawie bufora migawek.
     */
    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        NetworkSyncComponent sync = syncMapper.get(entity);
        PhysicsComponent physics = physicsMapper.get(entity);

        if (physics.body == null || sync.snapshots.isEmpty()) return;

        long renderTime = System.currentTimeMillis() - RENDER_DELAY_MS;

        sync.snapshots.removeIf(s -> s.timestamp < renderTime - 1000);

        if (sync.snapshots.isEmpty()) return;

        NetworkSyncComponent.Snapshot s1 = null;
        NetworkSyncComponent.Snapshot s2 = null;

        sync.snapshots.sort((a, b) -> Long.compare(a.timestamp, b.timestamp));

        for (int i = 0; i < sync.snapshots.size(); i++) {
            NetworkSyncComponent.Snapshot s = sync.snapshots.get(i);
            if (s.timestamp <= renderTime)
                s1 = s;
            else if (s.timestamp > renderTime) {
                s2 = s;
                break;
            }
        }

        Vector2 pos = physics.body.getPosition();
        float angle = physics.body.getAngle();
        Vector2 vel = physics.body.getLinearVelocity();

        if (s1 != null && s2 != null) {
            float t = (float) (renderTime - s1.timestamp) / (s2.timestamp - s1.timestamp);

            pos.set(s1.position).lerp(s2.position, t);

            float diff = (s2.angle - s1.angle) % MathUtils.PI2;
            if (Float.isNaN(diff)) diff = 0f;
            if (diff > MathUtils.PI) diff -= MathUtils.PI2;
            else if (diff < -MathUtils.PI) diff += MathUtils.PI2;

            angle = s1.angle + diff * t;
            vel.set(s1.velocity).lerp(s2.velocity, t);

        } else if (s1 != null) {
            float t = (renderTime - s1.timestamp) / 1000f;
            pos.set(s1.position).mulAdd(s1.velocity, t);
            angle = s1.angle + s1.angularVelocity * t;
            vel.set(s1.velocity);
        }

        physics.body.setTransform(pos, angle);
        physics.body.setLinearVelocity(vel);
    }
}
