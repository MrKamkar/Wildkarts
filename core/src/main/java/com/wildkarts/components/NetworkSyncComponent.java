package com.wildkarts.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector2;

/**
 * Component used to smoothly interpolate remote players' positions.
 */
public class NetworkSyncComponent implements Component {
    
    public static class Snapshot {
        public long timestamp;
        public Vector2 position = new Vector2();
        public float angle;
        public float angularVelocity; // Added for better extrapolation
        public Vector2 velocity = new Vector2();
    }
    
    public final java.util.List<Snapshot> snapshots = new java.util.ArrayList<>();
}
