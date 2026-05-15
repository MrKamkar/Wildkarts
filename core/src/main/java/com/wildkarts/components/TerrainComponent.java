package com.wildkarts.components;

import com.badlogic.ashley.core.Component;
import com.wildkarts.track.TrackGenerator;

/**
 * Marks an entity as terrain-aware.
 * TerrainSystem reads the entity's Box2D position, looks up the tile type
 * from the TrackGenerator grid, and modifies CarComponent speed limits
 * accordingly (road = full speed, grass = heavily limited).
 *
 * Default speed values are captured at initialization so different car types
 * can have different base speeds.
 */
public class TerrainComponent implements Component {

    /** Reference to the track generator for tile lookups. */
    public TrackGenerator trackGenerator;

    /** Current tile type under this entity (updated by TerrainSystem). */
    public int currentTile = TrackGenerator.TILE_ROAD;

    /** Original max forward speed (captured from CarComponent at init). */
    public float defaultMaxForwardSpeed = 80f;

    /** Original drive force (captured from CarComponent at init). */
    public float defaultDriveForce = 60f;
}
