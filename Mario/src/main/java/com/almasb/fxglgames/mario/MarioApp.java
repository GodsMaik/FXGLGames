package com.almasb.fxglgames.mario;

import com.almasb.fxgl.animation.Interpolators;
import com.almasb.fxgl.app.ApplicationMode;
import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.app.GameView;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.SpawnData;
import com.almasb.fxgl.entity.components.CollidableComponent;
import com.almasb.fxgl.input.UserAction;
import com.almasb.fxgl.physics.CollisionHandler;
import com.almasb.fxgl.physics.PhysicsComponent;
import com.almasb.fxgl.scene.Viewport;
import com.almasb.fxgl.ui.FontType;
import javafx.geometry.Point2D;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.Map;

import static com.almasb.fxgl.dsl.FXGL.*;
import static com.almasb.fxglgames.mario.MarioType.*;

/**
 * @author Almas Baimagambetov (almaslvl@gmail.com)
 */
public class MarioApp extends GameApplication {

    private static final int MAX_LEVEL = 16;

    @Override
    protected void initSettings(GameSettings settings) {
        settings.setWidth(1280);
        settings.setHeight(720);
        settings.setApplicationMode(ApplicationMode.DEVELOPER);
    }

    private Entity player;

    @Override
    protected void initInput() {
        getInput().addAction(new UserAction("Left") {
            @Override
            protected void onAction() {
                player.getComponent(PlayerComponent.class).left();
            }

            @Override
            protected void onActionEnd() {
                player.getComponent(PhysicsComponent.class).setVelocityX(0);
            }
        }, KeyCode.A);

        getInput().addAction(new UserAction("Right") {
            @Override
            protected void onAction() {
                player.getComponent(PlayerComponent.class).right();
            }

            @Override
            protected void onActionEnd() {
                player.getComponent(PhysicsComponent.class).setVelocityX(0);
            }
        }, KeyCode.D);

        getInput().addAction(new UserAction("Jump") {
            @Override
            protected void onActionBegin() {
                player.getComponent(PlayerComponent.class).jump();
            }
        }, KeyCode.W);

        getInput().addAction(new UserAction("Use") {
            @Override
            protected void onActionBegin() {
                getGameWorld().getEntitiesByType(BUTTON)
                        .stream()
                        .filter(btn -> btn.hasComponent(CollidableComponent.class) && player.isColliding(btn))
                        .forEach(btn -> {
                            btn.removeComponent(CollidableComponent.class);

                            makeExitDoor();
                        });
            }
        }, KeyCode.E);

        getInput().addAction(new UserAction("Test") {
            @Override
            protected void onActionBegin() {
                levelEndScene.onLevelFinish();
            }
        }, KeyCode.G);
    }

    @Override
    protected void initGameVars(Map<String, Object> vars) {
        vars.put("level", getSettings().getApplicationMode() == ApplicationMode.RELEASE ? 0 : -1);
        vars.put("levelTime", 0.0);
    }

    private boolean firstTime = true;
    private LevelEndScene levelEndScene;

    @Override
    protected void initGame() {
        if (firstTime) {
            if (getSettings().getApplicationMode() == ApplicationMode.RELEASE) {
                getSettings().setGlobalMusicVolume(0.25);
                loopBGM("BGM_dash_runner.wav");
            }

            levelEndScene = new LevelEndScene();
            firstTime = false;
        }

        getGameWorld().addEntityFactory(new MarioFactory());

        player = null;
        nextLevel();

        // player must be spawned after call to nextLevel, otherwise player gets removed
        // before the update tick _actually_ adds the player to game world
        player = getGameWorld().spawn("player", 50, 50);

        spawn("background");

        Viewport viewport = getGameScene().getViewport();

        viewport.setBounds(-1500, 0, 5000, getAppHeight());
        viewport.bindToEntity(player, getAppWidth() / 2, getAppHeight() / 2);

        viewport.setLazy(true);

        getGameScene().setBackgroundRepeat(image("bg_0.png", getAppWidth(), getAppHeight()));
    }

    @Override
    protected void initPhysics() {
        // this seems to fix a rare javafx with delayed audio playback - https://bugs.openjdk.java.net/browse/JDK-8125515
//        var mediaPlayer = new MediaPlayer(new Media(getClass().getResource("/assets/sounds/jump.wav").toExternalForm()));
//        mediaPlayer.setVolume(0);
//        mediaPlayer.seek(Duration.ZERO);
//        mediaPlayer.play();

        getPhysicsWorld().setGravity(0, 760);

        onCollisionBegin(PLAYER, DOOR_BOT, (player, door) -> {
            door.removeComponent(CollidableComponent.class);

            levelEndScene.onLevelFinish();

            // the above runs in its own scene, so fade will wait until
            // the user exits that scene
            getGameScene().getViewport().fade(() -> {
                nextLevel();
            });
        });

        onCollisionBegin(PLAYER, EXIT_SIGN, (player, sign) -> {
            sign.removeComponent(CollidableComponent.class);

            var texture = texture("exit_sign.png").brighter();
            texture.setTranslateX(sign.getX() + 9);
            texture.setTranslateY(sign.getY() + 13);

            var gameView = new GameView(texture, 150);

            getGameScene().addGameView(gameView);

            runOnce(() -> getGameScene().removeGameView(gameView), Duration.seconds(1.6));
        });

        onCollisionBegin(PLAYER, COIN, (player, coin) -> {
            coin.removeComponent(CollidableComponent.class);

            animationBuilder()
                    .duration(Duration.seconds(0.25))
                    .interpolator(Interpolators.EXPONENTIAL.EASE_OUT())
                    .onFinished(coin::removeFromWorld)
                    .scale(coin)
                    .from(new Point2D(1, 1))
                    .to(new Point2D(0, 0))
                    .buildAndPlay();

            var text = getUIFactory().newText("+100");
            text.setFont(getUIFactory().newFont(FontType.GAME, 26.0));
            text.setStroke(Color.RED);
            text.setStrokeWidth(2.75);

            var textEntity = entityBuilder()
                    .at(coin.getPosition())
                    .view(text)
                    .buildAndAttach();

            animationBuilder()
                    .interpolator(Interpolators.EXPONENTIAL.EASE_OUT())
                    .onFinished(textEntity::removeFromWorld)
                    .translate(textEntity)
                    .from(textEntity.getPosition())
                    .to(textEntity.getPosition().subtract(0, 100))
                    .buildAndPlay();
        });

        onCollisionBegin(PLAYER, KEY_PROMPT, (player, prompt) -> {
            String key = prompt.getString("key");

            var entity = getGameWorld().create("keyCode", new SpawnData(prompt.getX(), prompt.getY()).put("key", key));
            entity.getTransformComponent().setScaleOrigin(new Point2D(15, 15));

            animationBuilder()
                    .interpolator(Interpolators.ELASTIC.EASE_OUT())
                    .scale(entity)
                    .from(new Point2D(0, 0))
                    .to(new Point2D(1, 1))
                    .buildAndPlay();

            getGameWorld().addEntity(entity);

            runOnce(() -> {
                animationBuilder()
                        .interpolator(Interpolators.ELASTIC.EASE_IN())
                        .onFinished(() -> getGameWorld().removeEntity(entity))
                        .scale(entity)
                        .from(new Point2D(1, 1))
                        .to(new Point2D(0, 0))
                        .buildAndPlay();

            }, Duration.seconds(2.5));
        });

        getPhysicsWorld().addCollisionHandler(new CollisionHandler(PLAYER, BUTTON) {
            @Override
            protected void onCollisionBegin(Entity player, Entity btn) {
                Entity keyEntity = btn.getObject("keyEntity");

                if (!keyEntity.isActive()) {
                    getGameWorld().addEntity(keyEntity);
                }

                keyEntity.getViewComponent().opacityProperty().setValue(1);
            }

            @Override
            protected void onCollisionEnd(Entity player, Entity btn) {
                Entity keyEntity = btn.getObject("keyEntity");

                keyEntity.getViewComponent().opacityProperty().setValue(0);
            }
        });

        onCollisionBegin(PLAYER, EXIT_TRIGGER, (player, trigger) -> {
            trigger.removeComponent(CollidableComponent.class);

            makeExitDoor();
        });

        onCollisionBegin(PLAYER, ENEMY, (player, enemy) -> {
            System.out.println("Collision");
        });

        onCollisionBegin(PLAYER, MESSAGE_PROMPT, (player, prompt) -> {
            // TODO: can we add something like SingleCollidable?
            prompt.removeComponent(CollidableComponent.class);
            prompt.getViewComponent().setOpacity(1);

            runOnce(() -> prompt.removeFromWorld(), Duration.seconds(4.5));
        });

        onCollisionBegin(PLAYER, PORTAL, (player, portal) -> {
            var portal1 = portal.getComponent(PortalComponent.class);

            if (!portal1.isActive()) {
                return;
            }

            var portal2 = getGameWorld().getEntitiesByType(PORTAL)
                    .stream()
                    .filter(e -> e != portal)
                    .findAny()
                    .get()
                    .getComponent(PortalComponent.class);

            portal2.activate();

            animationBuilder()
                    .duration(Duration.seconds(0.5))
                    .onFinished(() -> {
                        player.getComponent(PhysicsComponent.class).overwritePosition(portal2.getEntity().getPosition());

                        animationBuilder()
                                .scale(player)
                                .from(new Point2D(0, 0))
                                .to(new Point2D(1, 1))
                                .buildAndPlay();
                    })
                    .scale(player)
                    .from(new Point2D(1, 1))
                    .to(new Point2D(0, 0))
                    .buildAndPlay();
        });

        onCollisionBegin(PLAYER, QUESTION, (player, question) -> {
            var q = question.getString("question");
            var a = question.getString("answer");

            getDisplay().showInputBox("Question: " + q + "?", answer -> {
                if (a.equals(answer)) {
                    question.removeFromWorld();
                }
            });
        });
    }

    private void makeExitDoor() {
        var doorTop = getGameWorld().getSingleton(DOOR_TOP);
        var doorBot = getGameWorld().getSingleton(DOOR_BOT);

        doorBot.getComponent(CollidableComponent.class).setValue(true);

        doorTop.getViewComponent().opacityProperty().setValue(1);
        doorBot.getViewComponent().opacityProperty().setValue(1);
    }

    private void nextLevel() {
        if (geti("level") == MAX_LEVEL) {
            getDisplay().showMessageBox("You finished the game!");
            return;
        }

        if (player != null) {
            player.getComponent(PhysicsComponent.class).overwritePosition(new Point2D(50, 50));
            player.setZ(Integer.MAX_VALUE);
        }

        inc("level", +1);
        set("levelTime", 0.0);

        var level = setLevelFromMap("tmx/level" + geti("level")  + ".tmx");

        var shortestTime = level.getProperties().getDouble("star1time");

        var levelTimeData = new LevelEndScene.LevelTimeData(shortestTime * 2.4, shortestTime*1.3, shortestTime);

        set("levelTimeData", levelTimeData);
    }

    @Override
    protected void onUpdate(double tpf) {
        inc("levelTime", tpf);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
