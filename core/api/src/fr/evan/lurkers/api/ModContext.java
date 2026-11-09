package fr.evan.lurkers.api;

import java.io.File;
import java.util.Properties;

/** Ce que le modloader donne à chaque mod. */
public interface ModContext {

    /** Dossier modloader/config, où chaque mod peut lire ou écrire ses réglages. */
    File getConfigDir();

    /**
     * Lit modloader/config/&lt;nom&gt;.properties (en UTF-8). Si le fichier n'existe pas,
     * il est créé avec les valeurs par défaut données.
     */
    Properties loadConfig(String name, Properties defaults);

    /** Écrit une ligne dans modloader/modloader.log. */
    void log(String message);
}
