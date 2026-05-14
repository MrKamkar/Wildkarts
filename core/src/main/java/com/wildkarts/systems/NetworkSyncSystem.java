package com.wildkarts.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Vector2;
import com.wildkarts.components.NetworkSyncComponent;
import com.wildkarts.components.PhysicsComponent;

/**
 * Interpolates physics bodies of remote players towards the latest positions 
 * received from the server to create smooth movement instead of snapping.
 */
public class NetworkSyncSystem extends IteratingSystem {

    // 100ms delay for snapshot interpolation
    private static final long RENDER_DELAY_MS = 100;

    public NetworkSyncSystem() {
        super(Family.all(NetworkSyncComponent.class, PhysicsComponent.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        NetworkSyncComponent sync = entity.getComponent(NetworkSyncComponent.class);
        PhysicsComponent physics = entity.getComponent(PhysicsComponent.class);

        if (physics.body == null || sync.snapshots.isEmpty()) return;

        long renderTime = System.currentTimeMillis() - RENDER_DELAY_MS;

        // Clean up old snapshots (older than 1000ms) to prevent memory leaks
        sync.snapshots.removeIf(s -> s.timestamp < renderTime - 1000);

        if (sync.snapshots.isEmpty()) return;

        // Find bounding snapshots
        NetworkSyncComponent.Snapshot s1 = null;
        NetworkSyncComponent.Snapshot s2 = null;

        // Ensure snapshots are sorted by timestamp (UDP can arrive out of order)
        sync.snapshots.sort((a, b) -> Long.compare(a.timestamp, b.timestamp));

        for (int i = 0; i < sync.snapshots.size(); i++) {
            NetworkSyncComponent.Snapshot s = sync.snapshots.get(i);
            if (s.timestamp <= renderTime) {
                s1 = s; // This will find the newest snapshot <= renderTime
            } else if (s.timestamp > renderTime) {
                s2 = s;
                break; // Found the immediate next snapshot
            }
        }

        Vector2 pos = physics.body.getPosition();
        float angle = physics.body.getAngle();
        Vector2 vel = physics.body.getLinearVelocity();

        if (s1 != null && s2 != null) {
            // Interpolate between s1 and s2
            float t = (float) (renderTime - s1.timestamp) / (s2.timestamp - s1.timestamp);
            
            pos.set(s1.position).lerp(s2.position, t);
            
            // Shortest path angle interpolation
            float diff = (s2.angle - s1.angle) % ((float) Math.PI * 2);
            if (diff != diff) diff = 0f;
            if (diff > Math.PI) diff -= Math.PI * 2;
            else if (diff < -Math.PI) diff += Math.PI * 2;
            
            angle = s1.angle + diff * t;
            
            vel.set(s1.velocity).lerp(s2.velocity, t);
            
        } else if (s1 != null) {
            // Extrapolate past s1 if we don't have a future packet yet
            float t = (renderTime - s1.timestamp) / 1000f; // time in seconds
            pos.set(s1.position).mulAdd(s1.velocity, t);
            angle = s1.angle + s1.angularVelocity * t;
            vel.set(s1.velocity);
        }

        physics.body.setTransform(pos, angle);
        physics.body.setLinearVelocity(vel);
    }
}
