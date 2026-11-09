package fr.evan.lurkers.launcher;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public final class Main {
    static final String VERSION = "0.1.1";

    private final Game game;
    private final Properties settings = new Properties();
    private final File settingsFile;
    private JFrame frame;

    private Ui.Toggle frToggle, mlToggle, closeToggle;
    private JPanel modsList;
    private JLabel status;
    private Ui.Dot gameDot, mlDot;
    private JLabel mlState;

    private Main(Game game) {
        this.game = game;
        this.settingsFile = new File(game.launcherDir(), "launcher.properties");
        boolean firstRun = !settingsFile.isFile();
        loadSettings();
        if (firstRun) {
            // Premier lancement : on part de l'état actuel du jeu, sans rien changer tout seul.
            settings.setProperty("fr", "" + game.frEnabled());
            boolean ml = false;
            try { ml = game.modloaderEnabled(); } catch (Exception ignored) {}
            settings.setProperty("modloader", "" + ml);
            saveSettings();
        }
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> {
            File dir = findGameDir();
            if (dir == null) {
                JOptionPane.showMessageDialog(null,
                        "Je ne trouve pas Lurkers.\nChoisis le dossier du jeu (celui qui contient lurkers.exe).",
                        "Lurkers Launcher", JOptionPane.INFORMATION_MESSAGE);
                JFileChooser fc = new JFileChooser(new File("C:\\Program Files (x86)\\Steam\\steamapps\\common"));
                fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                if (fc.showOpenDialog(null) != JFileChooser.APPROVE_OPTION || !Game.looksLikeGame(fc.getSelectedFile())) {
                    System.exit(0);
                }
                dir = fc.getSelectedFile();
            }
            Main m = new Main(new Game(dir));
            m.repairAfterUpdate();
            m.show();
        });
    }

    /** Le launcher est installé dans <jeu>/launcher/ : le jeu est le dossier parent. */
    private static File findGameDir() {
        try {
            File jar = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            for (File d = jar.getParentFile(); d != null; d = d.getParentFile()) {
                if (Game.looksLikeGame(d)) return d;
            }
        } catch (Exception ignored) {
        }
        File cwd = new File(".").getAbsoluteFile();
        if (Game.looksLikeGame(cwd)) return cwd;
        File steam = new File("C:\\Program Files (x86)\\Steam\\steamapps\\common\\Lurkers Demo");
        return Game.looksLikeGame(steam) ? steam : null;
    }

    // ------------------------------------------------------------ réglages

    private void loadSettings() {
        settings.setProperty("fr", "true");
        settings.setProperty("modloader", "true");
        settings.setProperty("fermerAuLancement", "true");
        if (settingsFile.isFile()) {
            try (Reader r = new InputStreamReader(new FileInputStream(settingsFile), StandardCharsets.UTF_8)) {
                settings.load(r);
            } catch (Exception ignored) {
            }
        }
    }

    private void saveSettings() {
        try {
            settingsFile.getParentFile().mkdirs();
            try (Writer w = new OutputStreamWriter(new FileOutputStream(settingsFile), StandardCharsets.UTF_8)) {
                settings.store(w, "Lurkers Launcher");
            }
        } catch (Exception ignored) {
        }
    }

    private boolean want(String k) { return Boolean.parseBoolean(settings.getProperty(k, "false")); }

    /** Après une mise à jour Steam, remet ce qui était activé (fichiers effacés, lurkers.json remplacé…). */
    private void repairAfterUpdate() {
        StringBuilder done = new StringBuilder();
        try {
            if (want("fr") && game.frAvailable() && !game.frEnabled()) { game.setFr(true); done.append("traduction "); }
        } catch (Exception ignored) {}
        try {
            if (want("modloader") && game.modloaderInstalled() && !game.modloaderEnabled()) { game.setModloader(true); done.append("modloader "); }
        } catch (Exception ignored) {}
        if (done.length() > 0) pendingStatus = "Réappliqué après mise à jour : " + done.toString().trim();
    }

    private String pendingStatus;

    // ------------------------------------------------------------ interface

    private void show() {
        frame = new JFrame("Lurkers Launcher");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setContentPane(buildRoot());
        frame.setMinimumSize(new Dimension(860, 560));
        frame.setSize(900, 580);
        frame.setLocationRelativeTo(null);
        File icon = new File(game.launcherDir(), "icon.png");
        if (icon.isFile()) frame.setIconImage(new ImageIcon(icon.getAbsolutePath()).getImage());
        refresh();
        if (pendingStatus != null) setStatus(pendingStatus, Ui.OK);
        frame.setVisible(true);
    }

    JPanel buildRoot() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Ui.BG);

        // --- bandeau
        Ui.Header header = new Ui.Header(new BorderLayout());
        header.setPreferredSize(new Dimension(10, 118));
        header.setBorder(BorderFactory.createEmptyBorder(26, 32, 22, 32));
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.add(Ui.label("Lurkers Launcher", Font.BOLD, 28, Color.WHITE));
        titles.add(Box.createVerticalStrut(4));
        String build = game.buildVersion();
        titles.add(Ui.label(game.dir.getName() + (build != null ? "  ·  build " + build : ""), Font.PLAIN, 14,
                new Color(255, 255, 255, 200)));
        header.add(titles, BorderLayout.WEST);
        JLabel ver = Ui.label("v" + VERSION, Font.PLAIN, 12, new Color(255, 255, 255, 170));
        ver.setVerticalAlignment(JLabel.TOP);
        header.add(ver, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        // --- contenu
        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(22, 24, 8, 24));
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(0, 0, 16, 16);

        c.gridx = 0; c.gridy = 0; c.weightx = 0.62; c.weighty = 0;
        body.add(optionsCard(), c);
        c.gridy = 1; c.weighty = 1;
        body.add(modsCard(), c);

        c.gridx = 1; c.gridy = 0; c.gridheight = 2; c.weightx = 0.38; c.insets = new Insets(0, 0, 16, 0);
        body.add(playCard(), c);
        root.add(body, BorderLayout.CENTER);

        // --- barre d'état
        status = Ui.label(" ", Font.PLAIN, 12, Ui.MUTED);
        status.setBorder(BorderFactory.createEmptyBorder(0, 28, 12, 28));
        root.add(status, BorderLayout.SOUTH);
        return root;
    }

    private JComponent optionsCard() {
        Ui.Card card = new Ui.Card(null);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.add(sectionTitle("Options"));
        card.add(Box.createVerticalStrut(12));

        frToggle = new Ui.Toggle(false);
        frToggle.onChange(on -> act(() -> { game.setFr(on); settings.setProperty("fr", "" + on); saveSettings(); },
                on ? "Traduction française activée" : "Traduction française désactivée"));
        card.add(optionRow("Traduction française", "Textes du jeu en français, sans toucher au jeu", frToggle));
        card.add(Box.createVerticalStrut(12));

        mlToggle = new Ui.Toggle(false);
        mlToggle.onChange(on -> act(() -> { game.setModloader(on); settings.setProperty("modloader", "" + on); saveSettings(); },
                on ? "Modloader activé : il se chargera au prochain lancement" : "Modloader désactivé"));
        card.add(optionRow("Modloader", "Boutons du menu en français et mods (HUD…)", mlToggle));
        return card;
    }

    private JComponent modsCard() {
        Ui.Card card = new Ui.Card(new BorderLayout());
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(sectionTitle("Mods"), BorderLayout.WEST);
        top.add(Ui.link("Ouvrir le dossier", () -> open(game.modsDir())), BorderLayout.EAST);
        top.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        card.add(top, BorderLayout.NORTH);
        modsList = new JPanel();
        modsList.setOpaque(false);
        modsList.setLayout(new BoxLayout(modsList, BoxLayout.Y_AXIS));
        card.add(modsList, BorderLayout.CENTER);
        return card;
    }

    private JComponent playCard() {
        Ui.Card card = new Ui.Card(new BorderLayout());
        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.add(sectionTitle("Jeu"));
        info.add(Box.createVerticalStrut(12));

        gameDot = new Ui.Dot(Ui.OK);
        info.add(statusRow(gameDot, Ui.label("Lurkers trouvé", Font.PLAIN, 14, Ui.TEXT)));
        JLabel path = Ui.label("<html><div style='width:210px'>" + game.dir.getAbsolutePath() + "</div></html>", Font.PLAIN, 11, Ui.MUTED);
        path.setBorder(BorderFactory.createEmptyBorder(2, 18, 10, 0));
        path.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.add(path);

        mlDot = new Ui.Dot(Ui.OFF);
        mlState = Ui.label("", Font.PLAIN, 14, Ui.TEXT);
        info.add(statusRow(mlDot, mlState));
        info.add(Box.createVerticalStrut(14));

        JPanel links = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
        links.setOpaque(false);
        links.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.add(Ui.link("Ouvrir le dossier du jeu", () -> open(game.dir)));
        col.add(Box.createVerticalStrut(6));
        col.add(Ui.link("Réglages du HUD", () -> open(new File(game.modloaderDir(), "config/hud.properties"))));
        col.add(Box.createVerticalStrut(6));
        col.add(Ui.link("Journal du modloader", () -> open(new File(game.modloaderDir(), "modloader.log"))));
        col.add(Box.createVerticalStrut(6));
        col.add(Ui.link("Journal du jeu", () -> open(game.gameLog())));
        links.add(col);
        info.add(links);
        card.add(info, BorderLayout.NORTH);

        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        closeToggle = new Ui.Toggle(want("fermerAuLancement"));
        closeToggle.onChange(on -> { settings.setProperty("fermerAuLancement", "" + on); saveSettings(); });
        JPanel closeRow = new JPanel(new BorderLayout(10, 0));
        closeRow.setOpaque(false);
        closeRow.add(Ui.label("Fermer le launcher au lancement", Font.PLAIN, 13, Ui.MUTED), BorderLayout.CENTER);
        closeRow.add(closeToggle, BorderLayout.EAST);
        closeRow.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
        bottom.add(closeRow);
        Ui.PrimaryButton play = new Ui.PrimaryButton("JOUER");
        play.setPreferredSize(new Dimension(10, 58));
        play.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
        play.onClick(this::play);
        bottom.add(play);
        card.add(bottom, BorderLayout.SOUTH);
        return card;
    }

    private static JComponent sectionTitle(String t) {
        JLabel l = Ui.label(t, Font.BOLD, 16, Ui.TEXT);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static JComponent optionRow(String title, String desc, Ui.Toggle toggle) {
        JPanel row = new JPanel(new BorderLayout(16, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel txt = new JPanel();
        txt.setOpaque(false);
        txt.setLayout(new BoxLayout(txt, BoxLayout.Y_AXIS));
        txt.add(Ui.label(title, Font.BOLD, 14, Ui.TEXT));
        txt.add(Box.createVerticalStrut(2));
        txt.add(Ui.label(desc, Font.PLAIN, 12, Ui.MUTED));
        row.add(txt, BorderLayout.CENTER);
        row.add(toggle, BorderLayout.EAST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        return row;
    }

    private static JComponent statusRow(Ui.Dot dot, JLabel text) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(dot);
        row.add(text);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        return row;
    }

    // ------------------------------------------------------------ état

    void refresh() {
        frToggle.setOn(game.frEnabled());
        frToggle.setEnabled(game.frAvailable());
        boolean mlOn = false;
        try { mlOn = game.modloaderEnabled(); } catch (Exception ignored) {}
        mlToggle.setOn(mlOn);
        mlToggle.setEnabled(game.modloaderInstalled());
        mlDot.setColor(mlOn ? Ui.OK : Ui.OFF);
        mlState.setText(!game.modloaderInstalled() ? "Modloader absent" : mlOn ? "Modloader actif" : "Modloader inactif");

        modsList.removeAll();
        java.util.List<Game.Mod> mods = game.mods();
        if (mods.isEmpty()) {
            modsList.add(Ui.label("Aucun mod. Dépose des .jar dans modloader/mods.", Font.PLAIN, 13, Ui.MUTED));
        }
        for (Game.Mod m : mods) {
            Ui.Toggle t = new Ui.Toggle(m.enabled());
            t.setEnabled(mlOn);
            t.onChange(on -> act(() -> game.setModEnabled(m, on), m.name() + (on ? " activé" : " désactivé")));
            String desc = mlOn ? m.file.getName() : "Active le modloader pour utiliser ce mod";
            modsList.add(optionRow(m.name(), desc, t));
            modsList.add(Box.createVerticalStrut(10));
        }
        modsList.revalidate();
        modsList.repaint();
    }

    private interface IoAction { void run() throws Exception; }

    private void act(IoAction a, String okMessage) {
        try {
            a.run();
            setStatus(okMessage, Ui.OK);
        } catch (Exception e) {
            setStatus("Erreur : " + e.getMessage(), Color.RED.darker());
        }
        refresh();
    }

    private void setStatus(String s, Color c) {
        status.setText(s);
        status.setForeground(c);
    }

    private void play() {
        try {
            game.launch();
            setStatus("Lancement de Lurkers…", Ui.OK);
            if (closeToggle.isOn()) {
                new javax.swing.Timer(1500, e -> System.exit(0)).start();
            }
        } catch (Exception e) {
            setStatus("Impossible de lancer le jeu : " + e.getMessage(), Color.RED.darker());
        }
    }

    private void open(File f) {
        try {
            if (!f.exists()) { setStatus(f.getName() + " n'existe pas encore (lance le jeu une fois)", Ui.WARN); return; }
            String n = f.getName().toLowerCase();
            if (f.isFile() && (n.endsWith(".properties") || n.endsWith(".log") || n.endsWith(".txt"))) {
                new ProcessBuilder("notepad.exe", f.getAbsolutePath()).start();
            } else {
                Desktop.getDesktop().open(f);
            }
        } catch (Exception e) {
            setStatus("Impossible d'ouvrir " + f.getName(), Ui.WARN);
        }
    }

    // ------------------------------------------------------------ aperçu (tests)

    /** Dessine l'interface dans une image, sans fenêtre (utilisé pour vérifier le rendu). */
    public static void preview(File gameDir, File png, int w, int h) throws Exception {
        Main m = new Main(new Game(gameDir));
        JPanel root = m.buildRoot();
        m.refresh();
        root.setSize(w, h);
        layoutAll(root);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        Ui.smooth(g);
        root.printAll(g);
        g.dispose();
        javax.imageio.ImageIO.write(img, "png", png);
    }

    private static void layoutAll(Component c) {
        c.doLayout();
        if (c instanceof java.awt.Container) for (Component k : ((java.awt.Container) c).getComponents()) layoutAll(k);
    }
}
