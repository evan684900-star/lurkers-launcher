package fr.evan.lurkers.core;

import fr.evan.lurkers.api.LurkersMod;
import fr.evan.lurkers.api.ModContext;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.ServiceLoader;

/** Charge les .jar de modloader/mods et appelle chaque mod en isolant ses erreurs. */
final class ModManager implements ModContext {
    private final File configDir;
    private final List<LurkersMod> mods = new ArrayList<>();

    ModManager(File configDir) {
        this.configDir = configDir;
    }

    void loadFrom(File modsDir) {
        File[] jars = modsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
        if (jars == null || jars.length == 0) {
            CoreLog.info("Aucun mod dans " + modsDir.getName() + "/");
            return;
        }
        Arrays.sort(jars);
        for (File jar : jars) {
            try {
                URLClassLoader cl = new URLClassLoader(new URL[]{jar.toURI().toURL()}, ModManager.class.getClassLoader());
                int found = 0;
                for (LurkersMod mod : ServiceLoader.load(LurkersMod.class, cl)) {
                    mods.add(mod);
                    found++;
                    CoreLog.info("Mod chargé : " + mod.getName() + " (" + jar.getName() + ")");
                    try {
                        mod.onLoad(this);
                    } catch (Throwable t) {
                        CoreLog.error("onLoad de " + mod.getName(), t);
                    }
                }
                if (found == 0) CoreLog.info("Aucun mod déclaré dans " + jar.getName() + " (fichier META-INF/services manquant ?)");
            } catch (Throwable t) {
                CoreLog.error("chargement de " + jar.getName(), t);
            }
        }
    }

    boolean isEmpty() { return mods.isEmpty(); }

    void gameReady() {
        for (LurkersMod m : new ArrayList<>(mods)) {
            try { m.onGameReady(); } catch (Throwable t) { disable(m, "onGameReady", t); }
        }
    }

    void render(com.badlogic.gdx.graphics.g2d.SpriteBatch batch, int w, int h) {
        for (LurkersMod m : new ArrayList<>(mods)) {
            try { m.onRender(batch, w, h); } catch (Throwable t) { disable(m, "onRender", t); }
        }
    }

    void dispose() {
        for (LurkersMod m : mods) {
            try { m.onDispose(); } catch (Throwable t) { CoreLog.error("onDispose de " + m.getName(), t); }
        }
    }

    /** Un mod qui plante est désactivé pour la partie, le jeu continue. */
    private void disable(LurkersMod m, String where, Throwable t) {
        CoreLog.error(where + " de " + m.getName() + " : mod désactivé jusqu'au prochain lancement", t);
        mods.remove(m);
    }

    // ---- ModContext

    @Override public File getConfigDir() { return configDir; }

    @Override public void log(String message) { CoreLog.info(message); }

    @Override
    public Properties loadConfig(String name, Properties defaults) {
        Properties p = new Properties();
        if (defaults != null) p.putAll(defaults);
        File f = new File(configDir, name + ".properties");
        if (f.isFile()) {
            try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
                p.load(r);
            } catch (Exception e) {
                CoreLog.error("lecture de " + f.getName(), e);
            }
        } else if (defaults != null) {
            try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
                defaults.store(w, "Réglages du mod " + name);
            } catch (Exception e) {
                CoreLog.error("écriture de " + f.getName(), e);
            }
        }
        return p;
    }
}
