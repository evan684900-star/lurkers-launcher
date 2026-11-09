package fr.evan.lurkers.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import fr.evan.lurkers.api.LurkersMod;
import fr.evan.lurkers.api.ModContext;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

/** Petit encadré bleu et blanc : FPS, heure et temps de session. Réglages dans config/hud.properties. */
public final class HudMod implements LurkersMod {
    private static final Color BLUE = new Color(0x1E5EFFDD);   // fond bleu, légèrement transparent
    private static final Color WHITE = new Color(0xFFFFFFFF);

    private String position = "haut-droite";
    private boolean showFps = true, showClock = true, showSession = false;
    private float scale = 1f;
    private int margin = 12;

    private BitmapFont font;
    private Texture pixel;
    private final GlyphLayout layout = new GlyphLayout();
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm");
    private final long startedAt = System.currentTimeMillis();
    private String text = "";
    private long lastUpdate;

    @Override public String getName() { return "HUD (FPS / heure)"; }

    @Override
    public void onLoad(ModContext ctx) {
        Properties d = new Properties();
        d.setProperty("position", "haut-droite");
        d.setProperty("afficherFps", "true");
        d.setProperty("afficherHeure", "true");
        d.setProperty("afficherSession", "false");
        d.setProperty("taille", "1.0");
        d.setProperty("marge", "12");
        Properties p = ctx.loadConfig("hud", d);
        position = p.getProperty("position", position).trim().toLowerCase();
        showFps = bool(p, "afficherFps", true);
        showClock = bool(p, "afficherHeure", true);
        showSession = bool(p, "afficherSession", false);
        try { scale = Float.parseFloat(p.getProperty("taille", "1.0").trim()); } catch (Exception ignored) {}
        try { margin = Integer.parseInt(p.getProperty("marge", "12").trim()); } catch (Exception ignored) {}
        ctx.log("HUD : position " + position + ", taille " + scale);
    }

    @Override
    public void onGameReady() {
        font = new BitmapFont();          // police par défaut de libGDX (incluse dans le jeu)
        font.getData().setScale(scale);
        font.setUseIntegerPositions(true);
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        pixel = new Texture(pm);
        pm.dispose();
    }

    @Override
    public void onRender(SpriteBatch batch, int sw, int sh) {
        long now = System.currentTimeMillis();
        if (now - lastUpdate > 250) {     // le texte change 4 fois par seconde, pas à chaque image
            lastUpdate = now;
            StringBuilder sb = new StringBuilder();
            if (showFps) sb.append(Gdx.graphics.getFramesPerSecond()).append(" FPS");
            if (showClock) { if (sb.length() > 0) sb.append("   "); sb.append(clock.format(new Date())); }
            if (showSession) {
                long m = (now - startedAt) / 60000;
                if (sb.length() > 0) sb.append("   ");
                sb.append("Session ").append(m / 60).append("h").append(String.format("%02d", m % 60));
            }
            text = sb.toString();
        }
        if (text.isEmpty()) return;

        layout.setText(font, text);
        float padX = 10 * scale, padY = 7 * scale;
        float bw = layout.width + padX * 2, bh = layout.height + padY * 2;
        float x = position.endsWith("gauche") ? margin : sw - margin - bw;
        float y = position.startsWith("bas") ? margin : sh - margin - bh;

        // fond bleu + fine ligne blanche en bas
        batch.setColor(BLUE);
        batch.draw(pixel, x, y, bw, bh);
        batch.setColor(WHITE);
        batch.draw(pixel, x, y, bw, Math.max(1, 2 * scale));
        batch.setColor(Color.WHITE);

        font.setColor(WHITE);
        font.draw(batch, text, x + padX, y + bh - padY);
    }

    @Override
    public void onDispose() {
        if (font != null) font.dispose();
        if (pixel != null) pixel.dispose();
    }

    private static boolean bool(Properties p, String k, boolean def) {
        String v = p.getProperty(k);
        return v == null ? def : v.trim().equalsIgnoreCase("true") || v.trim().equalsIgnoreCase("oui");
    }
}
