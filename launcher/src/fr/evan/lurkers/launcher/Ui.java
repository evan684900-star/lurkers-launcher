package fr.evan.lurkers.launcher;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.function.Consumer;

/** Petits composants Swing dessinés à la main pour un style bleu et blanc moderne. */
final class Ui {
    private Ui() {}

    static final Color BLUE = new Color(0x1E5EFF);
    static final Color BLUE_DARK = new Color(0x1347D1);
    static final Color BLUE_LIGHT = new Color(0x4F8BFF);
    static final Color BG = new Color(0xF3F6FC);
    static final Color CARD = Color.WHITE;
    static final Color BORDER = new Color(0xE1E7F2);
    static final Color TEXT = new Color(0x14213D);
    static final Color MUTED = new Color(0x6B7A99);
    static final Color OFF = new Color(0xCBD3E1);
    static final Color OK = new Color(0x16A34A);
    static final Color WARN = new Color(0xD97706);

    private static final String FAMILY = pickFamily();

    private static String pickFamily() {
        String[] have = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        for (String f : new String[]{"Segoe UI Variable Text", "Segoe UI", "Inter", "Helvetica Neue", "Arial"}) {
            if (Arrays.asList(have).contains(f)) return f;
        }
        return Font.SANS_SERIF;
    }

    static Font font(int style, float size) { return new Font(FAMILY, style, Math.round(size)); }

    static void smooth(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    static JLabel label(String text, int style, float size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font(style, size));
        l.setForeground(color);
        return l;
    }

    /** Carte blanche à coins arrondis avec une fine bordure. */
    static final class Card extends JPanel {
        Card(LayoutManager lm) {
            super(lm);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            smooth(g);
            g.setColor(new Color(20, 40, 90, 10));
            g.fillRoundRect(1, 3, getWidth() - 2, getHeight() - 3, 18, 18);
            g.setColor(CARD);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 3, 18, 18);
            g.setColor(BORDER);
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 3, 18, 18);
            g.dispose();
        }
    }

    /** Bandeau du haut : dégradé bleu. */
    static final class Header extends JPanel {
        Header(LayoutManager lm) {
            super(lm);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            smooth(g);
            g.setPaint(new GradientPaint(0, 0, BLUE_DARK, getWidth(), getHeight(), BLUE_LIGHT));
            g.fillRect(0, 0, getWidth(), getHeight());
            // quelques cercles décoratifs très légers
            g.setColor(new Color(255, 255, 255, 18));
            g.fillOval(getWidth() - 220, -120, 300, 300);
            g.fillOval(getWidth() - 90, 40, 160, 160);
            g.dispose();
        }
    }

    /** Interrupteur on/off. */
    static final class Toggle extends JComponent {
        private boolean on;
        private boolean enabled = true;
        private Consumer<Boolean> onChange;

        Toggle(boolean on) {
            this.on = on;
            setPreferredSize(new Dimension(46, 26));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (!enabled) return;
                    Toggle.this.on = !Toggle.this.on;
                    repaint();
                    if (onChange != null) onChange.accept(Toggle.this.on);
                }
            });
        }

        void onChange(Consumer<Boolean> c) { onChange = c; }
        void setOn(boolean v) { on = v; repaint(); }
        boolean isOn() { return on; }

        @Override public void setEnabled(boolean e) {
            enabled = e;
            setCursor(Cursor.getPredefinedCursor(e ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            smooth(g);
            int w = 44, h = 24, y = (getHeight() - h) / 2;
            Color track = on ? BLUE : OFF;
            if (!enabled) track = new Color(track.getRed(), track.getGreen(), track.getBlue(), 90);
            g.setColor(track);
            g.fillRoundRect(0, y, w, h, h, h);
            g.setColor(Color.WHITE);
            int k = h - 6;
            g.fillOval(on ? w - k - 3 : 3, y + 3, k, k);
            g.dispose();
        }
    }

    /** Gros bouton bleu arrondi. */
    static final class PrimaryButton extends JComponent {
        private final String text;
        private boolean hover, pressed, enabled = true;
        private Runnable action;

        PrimaryButton(String text) {
            this.text = text;
            setPreferredSize(new Dimension(240, 56));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; pressed = false; repaint(); }
                @Override public void mousePressed(MouseEvent e) { pressed = true; repaint(); }
                @Override public void mouseReleased(MouseEvent e) {
                    boolean fire = pressed && hover && enabled;
                    pressed = false;
                    repaint();
                    if (fire && action != null) action.run();
                }
            });
        }

        void onClick(Runnable r) { action = r; }

        @Override public void setEnabled(boolean e) { enabled = e; repaint(); }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            smooth(g);
            Color c = !enabled ? OFF : pressed ? BLUE_DARK : hover ? BLUE_LIGHT : BLUE;
            g.setColor(new Color(30, 94, 255, enabled ? 50 : 0));
            g.fillRoundRect(2, 6, getWidth() - 4, getHeight() - 6, 16, 16);
            g.setColor(c);
            g.fillRoundRect(0, 0, getWidth(), getHeight() - 4, 16, 16);
            g.setColor(Color.WHITE);
            g.setFont(font(Font.BOLD, 20));
            FontMetrics fm = g.getFontMetrics();
            int tw = fm.stringWidth(text);
            int tx = (getWidth() - tw) / 2 + 10;
            int ty = (getHeight() - 4 - fm.getHeight()) / 2 + fm.getAscent();
            // petit triangle « play »
            int s = 12, px = tx - s - 10, py = (getHeight() - 4) / 2;
            g.fillPolygon(new int[]{px, px, px + s}, new int[]{py - s / 2 - 1, py + s / 2 + 1, py}, 3);
            g.drawString(text, tx, ty);
            g.dispose();
        }
    }

    /** Lien texte bleu. */
    static JLabel link(String text, Runnable action) {
        JLabel l = label(text, Font.PLAIN, 13, BLUE);
        l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        l.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { action.run(); }
            @Override public void mouseEntered(MouseEvent e) { l.setForeground(BLUE_DARK); }
            @Override public void mouseExited(MouseEvent e) { l.setForeground(BLUE); }
        });
        return l;
    }

    /** Pastille de statut (rond coloré). */
    static final class Dot extends JComponent {
        private Color color;
        Dot(Color c) { color = c; setPreferredSize(new Dimension(10, 10)); }
        void setColor(Color c) { color = c; repaint(); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            smooth(g);
            g.setColor(color);
            g.fillOval(0, (getHeight() - 10) / 2, 10, 10);
            g.dispose();
        }
    }

    static void stroke(Graphics2D g, float w) { g.setStroke(new BasicStroke(w)); }
}
