package com.wildkarts.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector2;

import java.util.ArrayList;
import java.util.List;

/**
 * Komponent interpolacji pozycji zdalnych graczy w trybie wieloosobowym.
 * Przechowuje historię snapshotów używaną przez {@code NetworkSyncSystem}.
 */
public class NetworkSyncComponent implements Component {

    /**
     * Pojedynczy snapshot pozycji samochodu z timestampem i prędkością.
     */
    public static class Snapshot {
        public long timestamp;
        public Vector2 position = new Vector2();
        public float angle;
        /** Prędkość kątowa używana przy ekstrapolacji między snapshotami. */
        public float angularVelocity;
        public Vector2 velocity = new Vector2();
    }

    /** Bufor ostatnich snapshotów pozycji zdalnego gracza. */
    public final List<Snapshot> snapshots = new ArrayList<>();
}
