package com.wildkarts.screen;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.wildkarts.components.CarComponent;
import com.wildkarts.components.LapComponent;
import com.wildkarts.components.PhysicsComponent;
import com.wildkarts.components.RaceComponent;
import com.wildkarts.components.RaceState;
import com.wildkarts.components.TerrainComponent;

/**
 * Zarządza przejściami stanów ekranu gry, spawnem samochodu i encją wyścigu.
 */
public class GameSessionController {

    private final GameScreenContext ctx;
    private final EditorUiController editorUi;

    /**
     * Tworzy kontroler sesji powiązany z kontekstem i UI edytora.
     *
     * @param ctx      współdzielony kontekst ekranu gry
     * @param editorUi kontroler UI edytora (kamera, licznik punktów)
     */
    public GameSessionController(GameScreenContext ctx, EditorUiController editorUi) {
        this.ctx = ctx;
        this.editorUi = editorUi;
    }

    /**
     * Włącza lub wyłącza systemy odpowiedzialne za jazdę i fizykę samochodu.
     *
     * @param enabled true aby włączyć systemy, false aby je zatrzymać
     */
    public void setDrivingSystemsEnabled(boolean enabled) {
        ctx.raceStateSystem.setProcessing(enabled);
        ctx.lapSectorSystem.setProcessing(enabled);
        ctx.inputSystem.setProcessing(enabled);
        ctx.terrainSystem.setProcessing(enabled);
        ctx.movementSystem.setProcessing(enabled);
        ctx.physicsSystem.setProcessing(enabled);
    }

    /**
     * Przechodzi w tryb edytora — usuwa samochód i encję wyścigu, włącza UI edytora.
     */
    public void transitionToEditing() {
        ctx.currentState = GameState.EDITING;

        setDrivingSystemsEnabled(false);

        if (ctx.raceEntity != null) {
            ctx.engine.removeEntity(ctx.raceEntity);
            ctx.raceEntity = null;
        }

        if (ctx.playerCar != null) {
            PhysicsComponent phys = ctx.playerCar.getComponent(PhysicsComponent.class);
            if (phys != null && phys.body != null)
                ctx.world.destroyBody(phys.body);
            ctx.engine.removeEntity(ctx.playerCar);
            ctx.playerCar = null;
        }

        editorUi.setupEditorCamera();

        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(ctx.editorStage);
        multiplexer.addProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                if (button == Input.Buttons.LEFT && ctx.currentState == GameState.EDITING) {
                    Vector3 worldCoords = ctx.camera.unproject(new Vector3(screenX, screenY, 0));
                    ctx.trackGenerator.addPoint(worldCoords.x, worldCoords.y);
                    editorUi.updatePointCountLabel();
                    return true;
                }
                return false;
            }
        });
        Gdx.input.setInputProcessor(multiplexer);
    }

    /**
     * Przechodzi w stan ładowania mapy — wyłącza jazdę i pokazuje ekran oczekiwania.
     */
    public void transitionToLoading() {
        ctx.currentState = GameState.LOADING;

        setDrivingSystemsEnabled(false);

        ctx.loadingLabel.setText("Connecting & Downloading Map...");
        Gdx.input.setInputProcessor(ctx.loadingStage);
    }

    /**
     * Rozpoczyna rozgrywkę — spawnuje samochód gracza i uruchamia systemy jazdy.
     */
    public void transitionToPlaying() {
        if (ctx.trackGenerator.getManualPoints().size < 4 && !ctx.isMultiplayerMode) {
            Gdx.app.log("Game", "Cannot play: Need at least 4 points to form a track.");
            return;
        }

        ctx.currentState = GameState.PLAYING;

        if (ctx.isMultiplayerMode)
            createBoundaryWalls();

        setDrivingSystemsEnabled(true);

        float aspectRatio = (float) Gdx.graphics.getWidth() / Gdx.graphics.getHeight();
        ctx.camera.viewportWidth = GameScreenContext.VIEWPORT_WIDTH_METERS;
        ctx.camera.viewportHeight = GameScreenContext.VIEWPORT_WIDTH_METERS / aspectRatio;
        ctx.camera.update();

        Vector2 startPos = ctx.trackGenerator.getStartPosition();
        float startAngle = ctx.trackGenerator.getStartAngle();
        ctx.playerCar = ctx.carFactory.createCar(startPos.x, startPos.y, startAngle);

        CarComponent carComp = ctx.playerCar.getComponent(CarComponent.class);
        TerrainComponent terrain = new TerrainComponent();
        terrain.trackGenerator = ctx.trackGenerator;
        terrain.defaultMaxForwardSpeed = carComp.maxForwardSpeed;
        terrain.defaultEngineForce = carComp.engineForce;
        terrain.defaultRollingResistance = carComp.rollingResistance;
        terrain.defaultAeroDragCoeff = carComp.aerodynamicDragCoeff;
        terrain.defaultLinearDamping = carComp.linearDamping;
        terrain.defaultAngularDamping = carComp.angularDamping;
        ctx.playerCar.add(terrain);
        ctx.playerCar.add(new LapComponent());

        startRaceLobby();

        if (ctx.readyButton != null) {
            ctx.readyButton.setDisabled(false);
            ctx.readyButton.setText("READY");
        }

        Gdx.input.setInputProcessor(ctx.playStage);
    }

    /**
     * Tworzy encję menedżera wyścigu w stanie treningu (PRACTICE).
     */
    public void startRaceLobby() {
        if (ctx.raceEntity != null)
            ctx.engine.removeEntity(ctx.raceEntity);

        ctx.raceEntity = new Entity();
        RaceComponent race = new RaceComponent();
        race.currentState = RaceState.PRACTICE;
        race.countdownTimer = 3.0f;
        race.raceTimer = 0.0f;
        race.maxLaps = 3;
        race.totalSectors = 3;
        race.totalTrackPoints = ctx.trackGenerator.getManualPoints().size;
        race.readyPlayers = 0;
        race.requiredPlayers = 1;
        race.serverAuthoritative = ctx.isMultiplayerMode;
        ctx.raceEntity.add(race);
        ctx.engine.addEntity(ctx.raceEntity);
    }

    /**
     * Oznacza lokalnego gracza jako gotowego do startu wyścigu.
     */
    public void markLocalPlayerReady() {
        if (ctx.raceEntity == null || ctx.playerCar == null) return;
        RaceComponent race = ctx.raceEntity.getComponent(RaceComponent.class);
        LapComponent lap = ctx.playerCar.getComponent(LapComponent.class);
        if (race == null || lap == null) return;
        if (lap.ready) return;
        lap.ready = true;
        race.readyPlayers++;
        Gdx.app.log("Race", "Local player READY (" + race.readyPlayers
                + "/" + race.requiredPlayers + ")");
    }

    /**
     * Zwraca komponent wyścigu z aktywnej encji menedżera wyścigu.
     *
     * @return komponent wyścigu lub null gdy wyścig nie jest aktywny
     */
    public RaceComponent getRaceComponent() {
        if (ctx.raceEntity == null) return null;
        return ctx.raceEntity.getComponent(RaceComponent.class);
    }

    /**
     * Płynnie śledzi samochód gracza kamerą metodą interpolacji liniowej.
     */
    public void updateCamera() {
        if (ctx.playerCar == null) return;
        PhysicsComponent physics = ctx.playerCar.getComponent(PhysicsComponent.class);
        if (physics == null || physics.body == null) return;
        Vector2 carPos = physics.body.getPosition();
        ctx.camera.position.x += (carPos.x - ctx.camera.position.x) * 0.1f;
        ctx.camera.position.y += (carPos.y - ctx.camera.position.y) * 0.1f;
        ctx.camera.update();
    }

    /**
     * Tworzy statyczne ściany graniczne wokół toru (tryb wieloosobowy).
     * Rozmiar dopasowany do bounding box mapy — wywoływać po załadowaniu toru z serwera.
     */
    public void createBoundaryWalls() {
        float halfSize = ctx.trackGenerator.getBoundaryHalfExtent();
        float wallThickness = 1f;

        createWall(0, halfSize, halfSize, wallThickness);
        createWall(0, -halfSize, halfSize, wallThickness);
        createWall(-halfSize, 0, wallThickness, halfSize);
        createWall(halfSize, 0, wallThickness, halfSize);
    }

    /**
     * Tworzy pojedynczą statyczną ścianę Box2D o podanym położeniu i rozmiarze.
     *
     * @param x          pozycja X środka ściany
     * @param y          pozycja Y środka ściany
     * @param halfWidth  połowa szerokości ściany
     * @param halfHeight połowa wysokości ściany
     */
    private void createWall(float x, float y, float halfWidth, float halfHeight) {
        BodyDef wallDef = new BodyDef();
        wallDef.type = BodyDef.BodyType.StaticBody;
        wallDef.position.set(x, y);

        Body wallBody = ctx.world.createBody(wallDef);

        PolygonShape wallShape = new PolygonShape();
        wallShape.setAsBox(halfWidth, halfHeight);

        wallBody.createFixture(wallShape, 0f);
        wallShape.dispose();
    }
}
