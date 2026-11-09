package fr.evan.lurkers.traducteur;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
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
import java.util.Locale;

/** Style bleu et blanc, le même que le launcher. */
final class Ui {
    private Ui() {}

    static final Color BLUE = new Color(0x1E5EFF);
    static final Color BLUE_DARK = new Color(0x1347D1);
    static final Color BLUE_LIGHT = new Color(0x4F8BFF);
    static final Color BG = new Color(0xF3F6FC);
    static final Color BORDER = new Color(0xE1E7F2);
    static final Color TEXT = new Color(0x14213D);
    static final Color MUTED = new Color(0x6B7A99);
    static final Color OFF = new Color(0xCBD3E1);
    static final Color OK = new Color(0x16A34A);
    static final Color WARN = new Color(0xD97706);
    static final Color ERROR = new Color(0xDC2626);

    static final Color ROW_TODO = new Color(0xFFF6E5);
    static final Color ROW_WARN = new Color(0xFDECEC);
    static final Color ROW_SECTION = new Color(0xEAF0FF);
    static final Color ROW_SELECTED = new Color(0xD4E1FF);

    /** Interface en français si Windows est en français, sinon en anglais. */
    static final boolean FR = "fr".equals(Locale.getDefault().getLanguage());

    static String t(String fr, String en) { return FR ? fr : en; }

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
            g.setColor(new Color(255, 255, 255, 18));
            g.fillOval(getWidth() - 260, -140, 320, 320);
            g.fillOval(getWidth() - 110, 30, 170, 170);
            g.dispose();
        }
    }

    /** Bouton arrondi : plein bleu (principal) ou blanc bordé (secondaire). */
    static final class Button extends JComponent {
        private String text;
        private final boolean primary;
        private boolean hover, pressed, enabled = true;
        private Runnable action;

        Button(String text, boolean primary) {
            this.text = text;
            this.primary = primary;
            setFont(font(primary ? Font.BOLD : Font.PLAIN, 14));
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

        void setText(String t) { text = t; revalidate(); repaint(); }

        @Override public void setEnabled(boolean e) {
            enabled = e;
            setCursor(Cursor.getPredefinedCursor(e ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            repaint();
        }

        @Override public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(fm.stringWidth(text) + 32, 36);
        }

        @Override public Dimension getMaximumSize() { return getPreferredSize(); }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            smooth(g);
            int w = getWidth(), h = getHeight();
            if (primary) {
                Color c = !enabled ? OFF : pressed ? BLUE_DARK : hover ? BLUE_LIGHT : BLUE;
                g.setColor(c);
                g.fillRoundRect(0, 0, w, h, 12, 12);
                g.setColor(Color.WHITE);
            } else {
                g.setColor(pressed ? ROW_SECTION : hover ? new Color(0xF5F8FF) : Color.WHITE);
                g.fillRoundRect(0, 0, w - 1, h - 1, 12, 12);
                g.setColor(hover && enabled ? BLUE_LIGHT : BORDER);
                g.drawRoundRect(0, 0, w - 1, h - 1, 12, 12);
                g.setColor(enabled ? TEXT : OFF);
            }
            g.setFont(getFont());
            FontMetrics fm = g.getFontMetrics();
            g.drawString(text, (w - fm.stringWidth(text)) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
            g.dispose();
        }
    }
}
