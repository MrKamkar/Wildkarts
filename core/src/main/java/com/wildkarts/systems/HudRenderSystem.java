package com.wildkarts.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.wildkarts.components.InputComponent;
import com.wildkarts.components.LapComponent;
import com.wildkarts.components.PhysicsComponent;
import com.wildkarts.components.RaceComponent;
import com.wildkarts.components.RaceState;

/**
 * Renders the race HUD overlay in screen-space (pixel coordinates).
 * Uses its own {@link ScreenViewport}, {@link SpriteBatch} and
 * {@link BitmapFont} — completely independent of the world camera
 * that follows the player car.
 *
 * <p>Displayed elements (top-right cluster):</p>
 * <ul>
 *   <li>Position in race (P1/N)</li>
 *   <li>Current lap / max laps</li>
 *   <li>Race timer (MM:SS.cc)</li>
 *   <li>Current sector number + sector time</li>
 *   <li>Last sector delta (colored green/red, fades after 3.5 s)</li>
 * </ul>
 *
 * <p>Center-screen countdown (3… 2… 1… GO!).</p>
 */
public class HudRenderSystem extends EntitySystem {

    private static final float DELTA_DISPLAY_DURATION = 3.5f;
    private static final float GO_DISPLAY_DURATION = 1.5f;

    private final Viewport hudViewport;
    private final SpriteBatch batch;
    private final BitmapFont font;
    private final BitmapFont countdownFont;
    private final GlyphLayout layout;

    private final ComponentMapper<LapComponent> lapMapper =
            ComponentMapper.getFor(LapComponent.class);
    private final ComponentMapper<RaceComponent> raceMapper =
            ComponentMapper.getFor(RaceComponent.class);

    private ImmutableArray<Entity> racers;
    private ImmutableArray<Entity> localPlayers;
    private ImmutableArray<Entity> raceEntities;

    private float deltaDisplayTimer = 0f;
    private float lastKnownDelta = 0f;

    private float goDisplayTimer = 0f;
    private RaceState previousState = RaceState.WAITING_FOR_PLAYERS;

    /** Set by GameScreen to suppress the FINISHED banner when results overlay is shown. */
    public boolean suppressFinishedBanner = false;

    public HudRenderSystem() {
        super();
        hudViewport = new ScreenViewport();
        batch = new SpriteBatch();
        font = new BitmapFont();
        countdownFont = new BitmapFont();
        layout = new GlyphLayout();

        font.setColor(Color.WHITE);
        font.getData().setScale(1.4f);

        countdownFont.setColor(Color.WHITE);
        countdownFont.getData().setScale(4f);
    }

    @Override
    public void addedToEngine(Engine engine) {
        racers = engine.getEntitiesFor(
                Family.all(LapComponent.class, PhysicsComponent.class).get());
        localPlayers = engine.getEntitiesFor(
                Family.all(LapComponent.class, InputComponent.class).get());
        raceEntities = engine.getEntitiesFor(Family.all(RaceComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        RaceComponent race = getRace();
        LapComponent lap = getLocalPlayerLap();
        if (race == null) return;

        // Track delta display timer
        if (lap != null && lap.lastSectorDelta != lastKnownDelta) {
            lastKnownDelta = lap.lastSectorDelta;
            if (lastKnownDelta != 0f) {
                deltaDisplayTimer = DELTA_DISPLAY_DURATION;
            }
        }
        if (deltaDisplayTimer > 0f) {
            deltaDisplayTimer -= deltaTime;
        }

        // Trigger "GO!" on COUNTDOWN → RACING transition
        if (previousState == RaceState.COUNTDOWN && race.currentState == RaceState.RACING) {
            goDisplayTimer = GO_DISPLAY_DURATION;
        }
        previousState = race.currentState;

        if (goDisplayTimer > 0f) {
            goDisplayTimer -= deltaTime;
        }

        hudViewport.apply();
        batch.setProjectionMatrix(hudViewport.getCamera().combined);
        batch.begin();

        if (race.currentState == RaceState.COUNTDOWN) {
            drawCountdown(race);
        } else if (race.currentState == RaceState.RACING
                || race.currentState == RaceState.FINISHED
                || race.currentState == RaceState.PRACTICE) {
            if (goDisplayTimer > 0f) {
                drawGoText();
            }
            if (lap != null) {
                drawRaceHud(race, lap);
            }
        }

        batch.end();
    }

    // ─── Countdown ────────────────────────────────────────────────────

    private void drawCountdown(RaceComponent race) {
        int seconds = (int) Math.ceil(race.countdownTimer);
        if (seconds <= 0) return;

        String text = String.valueOf(seconds);
        countdownFont.setColor(Color.YELLOW);
        layout.setText(countdownFont, text);
        float x = (Gdx.graphics.getWidth() - layout.width) / 2f;
        float y = (Gdx.graphics.getHeight() + layout.height) / 2f;
        countdownFont.draw(batch, text, x, y);
    }

    private void drawGoText() {
        String text = "GO!";
        countdownFont.setColor(Color.GREEN);
        layout.setText(countdownFont, text);
        float x = (Gdx.graphics.getWidth() - layout.width) / 2f;
        float y = (Gdx.graphics.getHeight() + layout.height) / 2f;
        countdownFont.draw(batch, text, x, y);
    }

    // ─── Race HUD (top-right) ─────────────────────────────────────────

    private void drawRaceHud(RaceComponent race, LapComponent lap) {
        float screenH = Gdx.graphics.getHeight();
        float screenW = Gdx.graphics.getWidth();
        float pad = 16f;
        float lineHeight = 28f;
        float x = screenW - 260f;
        float y = screenH - pad;

        int totalRacers = racers != null ? racers.size() : 1;

        // Position
        font.setColor(Color.WHITE);
        String posText = "P" + lap.racePosition + " / " + totalRacers;
        font.draw(batch, posText, x, y);
        y -= lineHeight;

        // Lap / practice header
        if (race.currentState == RaceState.PRACTICE) {
            font.draw(batch, "Practice", x, y);
            y -= lineHeight;
            if (lap.bestPracticeLapTime > 0f) {
                font.draw(batch, "Best " + formatTime(lap.bestPracticeLapTime), x, y);
                y -= lineHeight;
            }
        } else {
            int displayLap = Math.min(lap.currentLap, race.maxLaps);
            String lapText = "Lap " + displayLap + " / " + race.maxLaps;
            font.draw(batch, lapText, x, y);
            y -= lineHeight;
        }

        // Lap / race timer
        float timerSeconds = race.currentState == RaceState.PRACTICE
                ? currentLapElapsed(lap)
                : race.raceTimer;
        String timerText = formatTime(timerSeconds);
        font.draw(batch, timerText, x, y);
        y -= lineHeight;

        // Sector info
        String sectorText = "Sector " + (lap.currentSector + 1) + "  " + formatTime(lap.currentSectorElapsed);
        font.draw(batch, sectorText, x, y);
        y -= lineHeight;

        // Delta (fading)
        if (deltaDisplayTimer > 0f && lastKnownDelta != 0f) {
            float alpha = Math.min(1f, deltaDisplayTimer / 1f);
            if (lastKnownDelta < 0f) {
                font.setColor(0f, 1f, 0f, alpha);
            } else {
                font.setColor(1f, 0.2f, 0.2f, alpha);
            }
            String deltaText = String.format("%+.2f", lastKnownDelta);
            font.draw(batch, deltaText, x, y);
            font.setColor(Color.WHITE);
        }

        // Finished banner (hidden when results overlay is displayed)
        if (race.currentState == RaceState.FINISHED && !suppressFinishedBanner) {
            String finText = "RACE FINISHED!";
            countdownFont.setColor(Color.GOLD);
            layout.setText(countdownFont, finText);
            float cx = (screenW - layout.width) / 2f;
            float cy = (screenH + layout.height) / 2f;
            countdownFont.draw(batch, finText, cx, cy);
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    private float currentLapElapsed(LapComponent lap) {
        float total = lap.currentSectorElapsed;
        for (float sectorTime : lap.currentLapSectorTimes) {
            total += sectorTime;
        }
        return total;
    }

    private String formatTime(float totalSeconds) {
        if (totalSeconds < 0f) totalSeconds = 0f;
        int minutes = (int) (totalSeconds / 60f);
        float secs = totalSeconds - minutes * 60f;
        return String.format("%02d:%05.2f", minutes, secs);
    }

    private RaceComponent getRace() {
        if (raceEntities == null || raceEntities.size() == 0) return null;
        return raceMapper.get(raceEntities.first());
    }

    /**
     * Returns the LapComponent of the local player, identified as the entity
     * with both {@code InputComponent} and {@code LapComponent} (remote cars
     * never have {@code InputComponent}).
     */
    private LapComponent getLocalPlayerLap() {
        if (localPlayers == null || localPlayers.size() == 0) return null;
        return lapMapper.get(localPlayers.first());
    }

    // ─── Lifecycle ────────────────────────────────────────────────────

    public void resize(int width, int height) {
        hudViewport.update(width, height, true);
    }

    public void dispose() {
        batch.dispose();
        font.dispose();
        countdownFont.dispose();
    }
}
