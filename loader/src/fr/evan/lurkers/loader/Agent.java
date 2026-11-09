package fr.evan.lurkers.loader;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.instrument.Instrumentation;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Point d'entrée du modloader, chargé par Java avant le jeu grâce à
 * -javaagent:modloader/lurkers-modloader.jar dans lurkers.json.
 *
 * Rien ici ne doit empêcher le jeu de démarrer : chaque étape attrape ses erreurs.
 */
public final class Agent {
    public static final String VERSION = "0.1.1";

    private Agent() {}

    public static void premain(String args, Instrumentation inst) {
        File base = findModloaderDir();
        Log.init(new File(base, "modloader.log"));
        Log.info("Lurkers Modloader " + VERSION + " | Java " + System.getProperty("java.version")
                + " | dossier " + base.getAbsolutePath());

        File configDir = new File(base, "config");
        File modsDir = new File(base, "mods");
        configDir.mkdirs();
        modsDir.mkdirs();

        // 1. Traduction des textes écrits dans le code
        try {
            Map<String, String> map = readTranslations(new File(configDir, "traduction.properties"));
            if (!map.isEmpty()) {
                String[] prefixes = {"io/lurkers/game/rendering/ui/"};
                inst.addTransformer(new StringPatcher(map, prefixes));
                Log.info("Traduction : " + map.size() + " texte(s) à remplacer");
            } else {
                Log.info("Traduction : aucun texte (config/traduction.properties vide ou absent)");
            }
        } catch (Throwable t) {
            Log.error("préparation de la traduction", t);
        }

        // 2. Mods : le jeu est chargé par son propre ClassLoader (lanceur packr). On attend de le voir
        //    charger libGDX, puis on démarre le cœur du modloader DANS ce ClassLoader.
        try {
            File core = new File(base, "lib/modloader-core.jar");
            if (core.isFile()) {
                inst.addTransformer(new GameLoaderSpy(core, base));
            } else {
                Log.info("lib/modloader-core.jar absent : mods désactivés");
            }
        } catch (Throwable t) {
            Log.error("préparation des mods", t);
        }
        Log.info("Modloader prêt, démarrage du jeu");
    }

    /** Le dossier qui contient le .jar du modloader (modloader/), sinon ./modloader. */
    private static File findModloaderDir() {
        try {
            File jar = new File(Agent.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (jar.isFile()) return jar.getParentFile();
        } catch (Throwable ignored) {
        }
        return new File("modloader").getAbsoluteFile();
    }

    /**
     * Format : une ligne par texte, « Texte anglais = Texte français ».
     * Un texte qui commence par une espace s'écrit avec « \ » devant (ex. « \ players online »).
     */
    private static Map<String, String> readTranslations(File f) throws Exception {
        Map<String, String> map = new LinkedHashMap<>();
        if (!f.isFile()) return map;
        Properties p = new Properties();
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            p.load(r);
        }
        for (String k : p.stringPropertyNames()) {
            String v = p.getProperty(k);
            if (!k.isEmpty() && v != null && !v.isEmpty()) map.put(k, v);
        }
        return map;
    }
}
