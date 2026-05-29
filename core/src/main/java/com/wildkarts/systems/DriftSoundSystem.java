package com.wildkarts.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.math.MathUtils;
import com.wildkarts.components.CarComponent;
import com.wildkarts.components.InputComponent;

/**
 * Plays a looping tire screech SFX when the local player's car is drifting.
 * Volume scales with the rear slip angle magnitude for a dynamic effect.
 */
public class DriftSoundSystem extends EntitySystem {

    private static final float SLIP_VOLUME_MIN = 0.15f;
    private static final float SLIP_VOLUME_MAX = 0.8f;

    private final ComponentMapper<CarComponent> carMapper =
            ComponentMapper.getFor(CarComponent.class);

    private Sound driftSound;
    private long loopId = -1;
    private boolean playing = false;

    private ImmutableArray<Entity> localCars;

    @Override
    public void addedToEngine(Engine engine) {
        localCars = engine.getEntitiesFor(
                Family.all(CarComponent.class, InputComponent.class).get());
        driftSound = Gdx.audio.newSound(Gdx.files.internal("sounds/Drift_SFX.ogg"));
    }

    @Override
    public void update(float deltaTime) {
        if (localCars.size() == 0) return;

        CarComponent car = carMapper.get(localCars.first());
        if (car == null) return;

        if (car.rearSkidActive) {
            float volume = computeVolume(car);
            if (!playing) {
                loopId = driftSound.loop(volume);
                playing = true;
            } else {
                driftSound.setVolume(loopId, volume);
            }
        } else {
            if (playing) {
                driftSound.stop(loopId);
                playing = false;
                loopId = -1;
            }
        }
    }

    private float computeVolume(CarComponent car) {
        float absSlip = Math.abs(car.rearSlipAngleEffective);
        float peak = car.rearPeakSlipAngle;
        if (peak <= 0f) peak = 0.1f;

        float ratio = absSlip / (peak * 2f);
        return MathUtils.clamp(
                MathUtils.lerp(SLIP_VOLUME_MIN, SLIP_VOLUME_MAX, ratio),
                SLIP_VOLUME_MIN, SLIP_VOLUME_MAX);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        dispose();
    }

    public void dispose() {
        if (playing && loopId != -1) {
            driftSound.stop(loopId);
            playing = false;
        }
        if (driftSound != null) {
            driftSound.dispose();
            driftSound = null;
        }
    }
}
