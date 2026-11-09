package fr.evan.lurkers.api;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/**
 * Interface à implémenter par chaque mod.
 *
 * Un mod est un .jar placé dans modloader/mods/ qui contient le fichier
 * META-INF/services/fr.evan.lurkers.api.LurkersMod avec le nom complet de sa classe.
 */
public interface LurkersMod {

    /** Nom affiché dans le journal. */
    String getName();

    /** Appelé au démarrage, avant que le jeu ne crée sa fenêtre. Pas d'OpenGL ici. */
    default void onLoad(ModContext context) {}

    /** Appelé une fois sur le fil graphique, quand la fenêtre existe : créer textures et polices ici. */
    default void onGameReady() {}

    /**
     * Appelé à chaque image, après le rendu du jeu. Le batch est déjà ouvert (begin),
     * en coordonnées écran : (0,0) en bas à gauche, en pixels.
     */
    default void onRender(SpriteBatch batch, int screenWidth, int screenHeight) {}

    /** Appelé à la fermeture du jeu. */
    default void onDispose() {}
}
