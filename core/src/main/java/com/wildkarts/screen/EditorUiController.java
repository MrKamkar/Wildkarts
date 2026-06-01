package com.wildkarts.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.List;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

/**
 * Buduje i obsługuje interfejs edytora toru — siatka, zapis/odczyt map, licznik punktów.
 */
public class EditorUiController {

    private final GameScreenContext ctx;
    private final GameScreenActions actions;

    /**
     * Tworzy kontroler UI edytora powiązany z kontekstem ekranu gry.
     *
     * @param ctx     współdzielony kontekst zasobów i referencji
     * @param actions callbacki nawigacji ekranu gry
     */
    public EditorUiController(GameScreenContext ctx, GameScreenActions actions) {
        this.ctx = ctx;
        this.actions = actions;
    }

    /**
     * Buduje scenę edytora z polami rozmiaru siatki, zapisem/odczytem map i przyciskami akcji.
     */
    public void setupEditorUI() {
        ctx.editorStage = new Stage(new ScreenViewport());

        TextField widthField = new TextField(String.valueOf(ctx.trackGenerator.getGridWidth()), ctx.uiSkin);
        TextField heightField = new TextField(String.valueOf(ctx.trackGenerator.getGridHeight()), ctx.uiSkin);
        TextButton setSizeBtn = new TextButton("Set Grid Size", ctx.uiSkin);

        setSizeBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                try {
                    int w = Integer.parseInt(widthField.getText());
                    int h = Integer.parseInt(heightField.getText());
                    ctx.trackGenerator.setGridSize(w, h);
                    setupEditorCamera();
                    Gdx.app.log("Editor", "Grid resized to " + w + "x" + h);
                } catch (NumberFormatException e) {
                    Gdx.app.log("Editor", "Invalid grid size");
                }
            }
        });

        TextField nameField = new TextField("custom_map.json", ctx.uiSkin);
        TextButton saveButton = new TextButton("SAVE", ctx.uiSkin);
        TextButton loadButton = new TextButton("LOAD", ctx.uiSkin);
        TextButton playButton = new TextButton("PLAY", ctx.uiSkin);

        List<String> mapList = new List<>(ctx.uiSkin);
        ScrollPane scrollPane = new ScrollPane(mapList, ctx.uiSkin);

        Runnable refreshList = () -> {
            FileHandle mapsDir = Gdx.files.local("Maps");
            if (!mapsDir.exists()) mapsDir.mkdirs();
            FileHandle[] files = mapsDir.list(".json");
            Array<String> names = new Array<>();
            for (FileHandle f : files)
                names.add(f.name());
            mapList.setItems(names);
        };
        refreshList.run();

        saveButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                String name = nameField.getText();
                if (!name.endsWith(".json")) name += ".json";
                ctx.trackGenerator.saveMap("Maps/" + name);
                refreshList.run();
            }
        });

        loadButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                String name = mapList.getSelected();
                if (name != null) {
                    ctx.trackGenerator.loadMap("Maps/" + name);
                    nameField.setText(name);
                    updatePointCountLabel();
                    setupEditorCamera();
                }
            }
        });

        playButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                actions.transitionToPlaying();
            }
        });

        TextButton undoButton = new TextButton("UNDO POINT", ctx.uiSkin);
        undoButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                ctx.trackGenerator.removeLastPoint();
                updatePointCountLabel();
            }
        });

        ctx.pointCountLabel = new Label("Points: 0", ctx.uiSkin);
        updatePointCountLabel();

        Table table = new Table();
        table.setFillParent(true);
        table.top().right();
        table.pad(10f);

        table.add(new Label("Width:", ctx.uiSkin)).padRight(5);
        table.add(widthField).width(50).padBottom(5);
        table.add(new Label("Height:", ctx.uiSkin)).padLeft(10).padRight(5);
        table.add(heightField).width(50).padBottom(5).row();
        table.add(setSizeBtn).colspan(4).fillX().padBottom(20).row();

        table.add(ctx.pointCountLabel).colspan(4).padBottom(5).row();
        table.add(undoButton).colspan(4).fillX().height(35f).padBottom(5).row();
        table.add(playButton).colspan(4).fillX().height(35f).padBottom(20).row();

        table.add(new Label("File Name:", ctx.uiSkin)).colspan(4).row();
        table.add(nameField).colspan(4).fillX().padBottom(5).row();
        table.add(saveButton).colspan(2).fillX().padRight(5).height(35f);
        table.add(loadButton).colspan(2).fillX().height(35f).row();
        table.add(scrollPane).colspan(4).fillX().height(150f).padTop(10).padBottom(10).row();

        TextButton editorExitButton = new TextButton("EXIT TO MENU", ctx.uiSkin);
        editorExitButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                actions.exitToMainMenu();
            }
        });
        table.add(editorExitButton).colspan(4).fillX().height(35f);

        ctx.editorStage.addActor(table);
    }

    /**
     * Dopasowuje kamerę tak, aby cała siatka edytora była widoczna z lekkim marginesem.
     */
    public void setupEditorCamera() {
        float worldWidth = ctx.trackGenerator.getGridWidth() * ctx.trackGenerator.getTileSize();
        float worldHeight = ctx.trackGenerator.getGridHeight() * ctx.trackGenerator.getTileSize();
        float aspectRatio = (float) Gdx.graphics.getWidth() / Gdx.graphics.getHeight();

        // Większy wymiar siatki decyduje o zoomie kamery (5% marginesu).
        float neededWidth = worldWidth * 1.05f;
        float neededHeight = worldHeight * 1.05f;

        if (neededWidth / aspectRatio > neededHeight) {
            ctx.camera.viewportWidth = neededWidth;
            ctx.camera.viewportHeight = neededWidth / aspectRatio;
        } else {
            ctx.camera.viewportHeight = neededHeight;
            ctx.camera.viewportWidth = neededHeight * aspectRatio;
        }

        ctx.camera.position.set(0, 0, 0);
        ctx.camera.update();
    }

    /**
     * Aktualizuje etykietę z liczbą punktów kontrolnych toru.
     */
    public void updatePointCountLabel() {
        if (ctx.pointCountLabel != null) {
            int count = ctx.trackGenerator.getManualPoints().size;
            ctx.pointCountLabel.setText("Points: " + count + (count < 4 ? " (need 4+)" : ""));
        }
    }
}
