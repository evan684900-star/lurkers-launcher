package fr.evan.lurkers.core;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Array;

import java.lang.reflect.Field;

/**
 * Branche les mods sur le rendu du jeu sans modifier son code.
 *
 * Quand libGDX a démarré, on remplace (par réflexion) l'écouteur de la fenêtre par
 * une enveloppe qui appelle d'abord le jeu, puis dessine les mods par-dessus.
 */
final class RenderHook {
    private RenderHook() {}

    static void install(ModManager mods) {
        Thread t = new Thread(() -> {
            try {
                long deadline = System.currentTimeMillis() + 120_000;
                while (Gdx.app == null || Gdx.graphics == null) {
                    if (System.currentTimeMillis() > deadline) {
                        CoreLog.info("libGDX n'a pas démarré après 2 minutes : HUD non installé");
                        return;
                    }
                    Thread.sleep(100);
                }
                CoreLog.info("libGDX démarré (" + Gdx.app.getClass().getSimpleName() + "), branchement du HUD...");
                Gdx.app.postRunnable(() -> wrapWindows(mods));
            } catch (Throwable e) {
                CoreLog.error("attente de libGDX", e);
            }
        }, "Modloader-RenderHook");
        t.setDaemon(true);
        t.start();
    }

    @SuppressWarnings("unchecked")
    private static void wrapWindows(ModManager mods) {
        try {
            Object app = Gdx.app;
            Field windowsField = findField(app.getClass(), "windows");
            Array<Object> windows = (Array<Object>) windowsField.get(app);
            int n = 0;
            for (Object window : windows) {
                Field lf = findField(window.getClass(), "listener");
                ApplicationListener original = (ApplicationListener) lf.get(window);
                if (original instanceof ModdedListener) continue;
                lf.set(window, new ModdedListener(original, mods));
                n++;
            }
            CoreLog.info("HUD branché sur " + n + " fenêtre(s)");
        } catch (Throwable e) {
            CoreLog.error("branchement du HUD impossible (le jeu continue sans HUD)", e);
        }
    }

    private static Field findField(Class<?> c, String name) throws NoSuchFieldException {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                Field f = k.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name + " dans " + c.getName());
    }

    /** Enveloppe : le jeu d'abord, les mods ensuite. */
    static final class ModdedListener implements ApplicationListener {
        private final ApplicationListener game;
        private final ModManager mods;
        private SpriteBatch batch;
        private final Matrix4 projection = new Matrix4();
        private final java.nio.IntBuffer savedViewport = com.badlogic.gdx.utils.BufferUtils.newIntBuffer(16);
        private boolean broken;

        ModdedListener(ApplicationListener game, ModManager mods) {
            this.game = game;
            this.mods = mods;
        }

        @Override public void create() { game.create(); }
        @Override public void resize(int w, int h) { game.resize(w, h); }
        @Override public void pause() { game.pause(); }
        @Override public void resume() { game.resume(); }

        @Override
        public void render() {
            game.render();
            if (broken) return;
            try {
                if (batch == null) {
                    batch = new SpriteBatch();
                    mods.gameReady();
                }
                int w = Gdx.graphics.getBackBufferWidth();
                int h = Gdx.graphics.getBackBufferHeight();
                // On garde la zone d'affichage du jeu pour la remettre après : il ne la
                // redéfinit peut-être pas à chaque image.
                savedViewport.clear();
                Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, savedViewport);
                Gdx.gl.glViewport(0, 0, w, h);
                projection.setToOrtho2D(0, 0, w, h);
                batch.setProjectionMatrix(projection);
                batch.begin();
                batch.enableBlending();
                batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
                mods.render(batch, w, h);
                batch.end();
                Gdx.gl.glViewport(savedViewport.get(0), savedViewport.get(1), savedViewport.get(2), savedViewport.get(3));
            } catch (Throwable t) {
                broken = true;
                CoreLog.error("rendu du HUD (HUD coupé, le jeu continue)", t);
            }
        }

        @Override
        public void dispose() {
            try { mods.dispose(); } catch (Throwable ignored) {}
            if (batch != null) batch.dispose();
            game.dispose();
        }
    }
}
