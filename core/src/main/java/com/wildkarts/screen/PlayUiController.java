package com.wildkarts.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

/**
 * Obsługuje interfejs rozgrywki: lobby, ekran ładowania, menu ESC i wyniki wyścigu.
 */
public class PlayUiController {

    private final GameScreenContext ctx;
    private final GameScreenActions actions;

    /**
     * Tworzy kontroler UI rozgrywki powiązany z kontekstem ekranu gry.
     *
     * @param ctx     współdzielony kontekst zasobów i referencji
     * @param actions callbacki nawigacji ekranu gry
     */
    public PlayUiController(GameScreenContext ctx, GameScreenActions actions) {
        this.ctx = ctx;
        this.actions = actions;
    }

    /**
     * Buduje scenę gry z panelem lobby, przyciskiem powrotu do edytora (solo) i menu ESC.
     */
    public void setupPlayUI() {
        ctx.playStage = new Stage(new ScreenViewport());

        Table topLeft = new Table();
        topLeft.setFillParent(true);
        topLeft.top().left();
        topLeft.pad(10f);

        if (!ctx.isMultiplayerMode) {
            TextButton editButton = new TextButton("BACK TO EDITOR", ctx.uiSkin);
            editButton.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    actions.transitionToEditing();
                }
            });
            topLeft.add(editButton).width(150f).height(40f);
        }
        ctx.playStage.addActor(topLeft);

        ctx.lobbyPanel = new Table();
        ctx.lobbyPanel.setFillParent(true);
        ctx.lobbyPanel.bottom().pad(40f);

        ctx.lobbyStatusLabel = new Label("Waiting for players...", ctx.uiSkin);
        ctx.lobbyStatusLabel.setAlignment(Align.center);

        ctx.readyButton = new TextButton("READY", ctx.uiSkin);
        ctx.readyButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                actions.onReadyButtonClicked();
            }
        });

        ctx.lobbyPanel.add(ctx.lobbyStatusLabel).padBottom(10f).row();
        ctx.lobbyPanel.add(ctx.readyButton).width(180f).height(50f);

        ctx.lobbyPanel.setVisible(false);
        ctx.playStage.addActor(ctx.lobbyPanel);

        setupEscapeMenuUI();
        setupResultsUI();
    }

    /**
     * Buduje ekran ładowania mapy w trybie wieloosobowym.
     */
    public void setupLoadingUI() {
        ctx.loadingStage = new Stage(new ScreenViewport());

        ctx.loadingLabel = new Label("Loading...", ctx.uiSkin);
        ctx.loadingLabel.setAlignment(Align.center);

        Table table = new Table();
        table.setFillParent(true);
        table.center();
        table.add(ctx.loadingLabel).pad(20).row();

        TextButton loadingExitButton = new TextButton("EXIT TO MENU", ctx.uiSkin);
        loadingExitButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                actions.exitToMainMenu();
            }
        });
        table.add(loadingExitButton).width(220f).height(50f).padTop(30f);

        ctx.loadingStage.addActor(table);
    }

    /**
     * Przygotowuje pustą scenę wyników wyścigu z półprzezroczystym tłem.
     */
    private void setupResultsUI() {
        ctx.resultsStage = new Stage(new ScreenViewport());
        ctx.overlayRenderer = new ShapeRenderer();
        ctx.resultsPanel = new Table();
        ctx.resultsPanel.setFillParent(true);
        ctx.resultsPanel.center();
        ctx.resultsStage.addActor(ctx.resultsPanel);
    }

    /**
     * Wyświetla tabelę wyników wyścigu i blokuje sterowanie samochodem.
     *
     * @param playerIds   identyfikatory graczy w kolejności miejsc
     * @param playerNames nazwy graczy
     * @param finishTimes czasy mety w sekundach (0 = DNF)
     */
    public void showRaceResults(int[] playerIds, String[] playerNames, float[] finishTimes) {
        ctx.resultsPanel.clear();

        Label title = new Label("RACE RESULTS", ctx.uiSkin);
        title.setFontScale(2f);
        title.setAlignment(Align.center);
        ctx.resultsPanel.add(title).padBottom(35f).expandX().center().colspan(3).row();

        for (int i = 0; i < playerIds.length; i++) {
            String place = (i + 1) + ". Place";
            String name = playerNames[i] != null ? playerNames[i] : "Player";
            String time = finishTimes[i] > 0f ? formatRaceTime(finishTimes[i]) : "DNF";

            Label placeLabel = new Label(place, ctx.uiSkin);
            placeLabel.setFontScale(1.4f);
            placeLabel.setAlignment(Align.right);
            Label nameLabel = new Label(name, ctx.uiSkin);
            nameLabel.setFontScale(1.4f);
            nameLabel.setAlignment(Align.center);
            Label timeLabel = new Label(time, ctx.uiSkin);
            timeLabel.setFontScale(1.4f);
            timeLabel.setAlignment(Align.left);

            ctx.resultsPanel.add(placeLabel).width(140f).padRight(25f).padBottom(14f);
            ctx.resultsPanel.add(nameLabel).width(160f).padRight(25f).padBottom(14f);
            ctx.resultsPanel.add(timeLabel).width(120f).padBottom(14f);
            ctx.resultsPanel.row();
        }

        TextButton returnButton = new TextButton("Exit to Menu", ctx.uiSkin);
        returnButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                actions.exitToMainMenu();
            }
        });
        ctx.resultsPanel.add(returnButton).width(220f).height(50f).padTop(35f).colspan(3).center();

        ctx.isResultsScreenOpen = true;
        ctx.inputSystem.externalInputBlocked = true;
        ctx.hudRenderSystem.suppressFinishedBanner = true;
        Gdx.input.setInputProcessor(ctx.resultsStage);
        Gdx.input.setCursorCatched(false);
    }

    /**
     * Formatuje czas wyścigu jako MM:SS.ss.
     *
     * @param totalSeconds czas w sekundach
     * @return sformatowany łańcuch czasu
     */
    public String formatRaceTime(float totalSeconds) {
        int minutes = (int) (totalSeconds / 60f);
        float secs = totalSeconds - minutes * 60f;
        return String.format("%02d:%05.2f", minutes, secs);
    }

    /**
     * Buduje overlay menu pauzy wywoływanego klawiszem ESC w trybie wieloosobowym.
     */
    private void setupEscapeMenuUI() {
        ctx.escapeMenuStage = new Stage(new ScreenViewport());

        Table escapeMenuPanel = new Table();
        escapeMenuPanel.setFillParent(true);
        escapeMenuPanel.center();

        Label title = new Label("MENU", ctx.uiSkin);
        title.setFontScale(1.8f);
        title.setAlignment(Align.center);

        TextButton resumeButton = new TextButton("Resume", ctx.uiSkin);
        resumeButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                closeEscapeMenu();
            }
        });

        TextButton exitButton = new TextButton("Exit to Menu", ctx.uiSkin);
        exitButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                actions.exitToMainMenu();
            }
        });

        escapeMenuPanel.add(title).padBottom(30f).row();
        escapeMenuPanel.add(resumeButton).width(220f).height(50f).padBottom(15f).row();
        escapeMenuPanel.add(exitButton).width(220f).height(50f);

        ctx.escapeMenuStage.addActor(escapeMenuPanel);
    }

    /**
     * Przełącza widoczność menu pauzy (ESC).
     */
    public void toggleEscapeMenu() {
        if (ctx.isEscapeMenuOpen)
            closeEscapeMenu();
        else
            openEscapeMenu();
    }

    /**
     * Otwiera menu pauzy i blokuje sterowanie samochodem.
     */
    public void openEscapeMenu() {
        ctx.isEscapeMenuOpen = true;
        ctx.inputSystem.externalInputBlocked = true;
        Gdx.input.setInputProcessor(ctx.escapeMenuStage);
        Gdx.input.setCursorCatched(false);
    }

    /**
     * Zamyka menu pauzy i przywraca sterowanie rozgrywką.
     */
    public void closeEscapeMenu() {
        ctx.isEscapeMenuOpen = false;
        ctx.inputSystem.externalInputBlocked = false;
        Gdx.input.setInputProcessor(ctx.playStage);
    }

    /**
     * Rysuje overlay wyników lub menu pauzy w zależności od stanu UI.
     *
     * @param delta czas od ostatniej klatki w sekundach
     */
    public void renderOverlays(float delta) {
        if (ctx.isResultsScreenOpen) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            ctx.overlayRenderer.begin(ShapeRenderer.ShapeType.Filled);
            ctx.overlayRenderer.setColor(0f, 0f, 0f, 0.7f);
            ctx.overlayRenderer.rect(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            ctx.overlayRenderer.end();
            Gdx.gl.glDisable(GL20.GL_BLEND);
            ctx.resultsStage.act(delta);
            ctx.resultsStage.draw();
        } else if (ctx.isEscapeMenuOpen) {
            ctx.escapeMenuStage.act(delta);
            ctx.escapeMenuStage.draw();
        }
    }
}
