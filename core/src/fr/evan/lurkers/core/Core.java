package fr.evan.lurkers.core;

import java.io.File;
import java.util.function.Consumer;

/**
 * Cœur du modloader. Il est chargé DANS le ClassLoader du jeu (celui qui contient libGDX),
 * pour que les mods et le HUD utilisent exactement les mêmes classes que le jeu.
 */
public final class Core {
    private Core() {}

    /** Appelé par réflexion depuis l'agent, sur un fil à part. */
    public static void start(File modloaderDir, Consumer<String> log) {
        CoreLog.sink = log;
        try {
            ModManager mods = new ModManager(new File(modloaderDir, "config"));
            mods.loadFrom(new File(modloaderDir, "mods"));
            if (!mods.isEmpty()) RenderHook.install(mods);
        } catch (Throwable t) {
            CoreLog.error("démarrage du cœur", t);
        }
    }
}
