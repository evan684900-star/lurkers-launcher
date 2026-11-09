package fr.evan.lurkers.traducteur;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lurkers Translator : le texte anglais du jeu à gauche, la traduction à droite.
 * À l'enregistrement, écrit un fichier propre dans <jeu>/i18n/language_xx.properties,
 * que le jeu lit tout seul. Le jeu lui-même n'est jamais modifié.
 */
public final class Main {
    static final String VERSION = "1.0.0";

    // ------------------------------------------------------------ données

    enum State { EMPTY, DONE, TODO, WARN }

    static final class Item {
        final String section;
        final String key;
        final String en;
        String tr;

        Item(String section, String key, String en, String tr) {
            this.section = section;
            this.key = key;
            this.en = en;
            this.tr = tr;
        }

        State state() {
            if (en.isEmpty()) return tr.isEmpty() ? State.EMPTY : State.WARN;
            if (tr.isEmpty()) return State.TODO;
            if (!PropFile.placeholders(en).equals(PropFile.placeholders(tr))) return State.WARN;
            return State.DONE;
        }

        String problem() {
            if (en.isEmpty()) {
                return tr.isEmpty() ? null : Ui.t("Cette ligne sert de séparateur : laisse-la vide.",
                        "This line is a separator: leave it empty.");
            }
            if (tr.isEmpty()) return null;
            List<String> a = PropFile.placeholders(en), b = PropFile.placeholders(tr);
            if (!a.equals(b)) {
                return Ui.t("Les codes entre accolades doivent être les mêmes : anglais " + a + ", traduction " + b,
                        "Codes in braces must match: English " + a + ", translation " + b);
            }
            return null;
        }

        String note() {
            int x = PropFile.lineBreaks(en), y = PropFile.lineBreaks(tr);
            if (!tr.isEmpty() && x != y) {
                return Ui.t("Retours à la ligne : " + x + " en anglais, " + y + " ici (vérifie que ça rentre à l'écran).",
                        "Line breaks: " + x + " in English, " + y + " here (check it fits on screen).");
            }
            return null;
        }
    }

    static final class Lang {
        final String code, name;

        Lang(String code, String name) {
            this.code = code;
            this.name = name;
        }

        @Override public String toString() { return name + "  (" + code + ")"; }
    }

    private static final String[][] KNOWN = {
            {"fr", "Français"}, {"es", "Español"}, {"de", "Deutsch"}, {"it", "Italiano"},
            {"pt", "Português"}, {"pt_BR", "Português (Brasil)"}, {"nl", "Nederlands"}, {"pl", "Polski"},
            {"ru", "Русский"}, {"uk", "Українська"}, {"tr", "Türkçe"}, {"cs", "Čeština"},
            {"sv", "Svenska"}, {"da", "Dansk"}, {"fi", "Suomi"}, {"no", "Norsk"}, {"hu", "Magyar"},
            {"ro", "Română"}, {"el", "Ελληνικά"}, {"id", "Bahasa Indonesia"}, {"vi", "Tiếng Việt"},
            {"ja", "日本語"}, {"ko", "한국어"}, {"zh_CN", "简体中文"}, {"zh_TW", "繁體中文"}, {"ar", "العربية"},
    };

    private static final Lang OTHER = new Lang("", Ui.t("Autre langue…", "Other language…"));

    private File gameDir;
    private File englishJar;
    private File englishFile; // fichier anglais choisi à la main (prioritaire)
    private String englishLabel = "";
    private PropFile template;
    private final List<Item> items = new ArrayList<>();
    private final List<Object> rows = new ArrayList<>(); // Item ou String (section)
    private Map<String, String> commentedTr = new LinkedHashMap<>();
    private Lang lang;
    private boolean dirty;
    private boolean loading;
    private Item current;

    // ------------------------------------------------------------ interface

    JFrame frame;
    private JTable table;
    private final RowModel model = new RowModel();
    private JComboBox<Lang> langBox;
    private SearchField search;
    private JComboBox<String> filter;
    private JLabel sourceLabel, progressLabel, statusLabel, keyLabel, problemLabel, trTitle;
    private Bar bar;
    private JTextArea enArea, trArea;
    private Ui.Button saveButton;

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        SwingUtilities.invokeLater(() -> open(args, true));
    }

    static Main open(String[] args, boolean interactive) {
        Main m = new Main();
        m.gameDir = Sources.findGameDir(args);
        m.build();
        if (m.gameDir == null && interactive) m.askGameDir();
        m.reloadEnglish();
        m.frame.setVisible(true);
        return m;
    }

    private void build() {
        frame = new JFrame("Lurkers Translator");
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                if (confirmDiscard()) {
                    frame.dispose();
                    System.exit(0);
                }
            }
        });
        frame.setIconImage(icon());

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Ui.BG);
        root.add(buildHeader(), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);
        center.setBorder(BorderFactory.createEmptyBorder(12, 16, 0, 16));
        center.add(buildToolbar(), BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, buildTable(), buildEditor());
        split.setResizeWeight(0.6);
        split.setBorder(null);
        split.setOpaque(false);
        split.setDividerSize(10);
        split.setContinuousLayout(true);
        center.add(split, BorderLayout.CENTER);
        root.add(center, BorderLayout.CENTER);

        statusLabel = Ui.label(" ", Font.PLAIN, 13, Ui.MUTED);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(8, 18, 10, 18));
        root.add(statusLabel, BorderLayout.SOUTH);

        bindKeys(root);
        frame.setContentPane(root);
        frame.setMinimumSize(new Dimension(900, 600));
        frame.setSize(1180, 780);
        frame.setLocationRelativeTo(null);
    }

    private JComponent buildHeader() {
        Ui.Header h = new Ui.Header(new BorderLayout());
        h.setBorder(BorderFactory.createEmptyBorder(16, 22, 16, 22));

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.add(Ui.label("Lurkers Translator", Font.BOLD, 24, Color.WHITE));
        left.add(Box.createVerticalStrut(2));
        sourceLabel = Ui.label(" ", Font.PLAIN, 12, new Color(255, 255, 255, 210));
        left.add(sourceLabel);
        h.add(left, BorderLayout.WEST);

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        JPanel langRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        langRow.setOpaque(false);
        langRow.add(Ui.label(Ui.t("Langue", "Language"), Font.BOLD, 13, Color.WHITE));
        langBox = new JComboBox<>();
        langBox.setFont(Ui.font(Font.PLAIN, 13));
        langBox.setPreferredSize(new Dimension(230, 30));
        langBox.addActionListener(e -> onLanguagePicked());
        langRow.add(langBox);
        right.add(langRow);
        right.add(Box.createVerticalStrut(8));
        JPanel progRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        progRow.setOpaque(false);
        progressLabel = Ui.label(" ", Font.BOLD, 13, Color.WHITE);
        bar = new Bar();
        progRow.add(progressLabel);
        progRow.add(bar);
        right.add(progRow);
        h.add(right, BorderLayout.EAST);
        return h;
    }

    private JComponent buildToolbar() {
        JPanel p = new JPanel(new BorderLayout(10, 0));
        p.setOpaque(false);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        search = new SearchField(Ui.t("Rechercher un texte ou une clé…  (Ctrl+F)", "Search text or key…  (Ctrl+F)"));
        search.setPreferredSize(new Dimension(330, 36));
        search.getDocument().addDocumentListener(onChange(this::refilter));
        left.add(search);
        left.add(Box.createHorizontalStrut(8));
        filter = new JComboBox<>(new String[]{
                Ui.t("Tous les textes", "All texts"),
                Ui.t("À traduire", "To translate"),
                Ui.t("Problèmes", "Problems")});
        filter.setFont(Ui.font(Font.PLAIN, 13));
        filter.setPreferredSize(new Dimension(160, 36));
        filter.addActionListener(e -> refilter());
        left.add(filter);
        p.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        Ui.Button openEn = new Ui.Button(Ui.t("Ouvrir un fichier anglais…", "Open English file…"), false);
        openEn.setToolTipText(Ui.t("Par exemple le language.properties envoyé par le dev",
                "For example the language.properties sent by the developer"));
        openEn.onClick(this::chooseEnglishFile);
        Ui.Button export = new Ui.Button(Ui.t("Exporter…", "Export…"), false);
        export.setToolTipText(Ui.t("Enregistrer une copie ailleurs, pour l'envoyer au dev",
                "Save a copy elsewhere, to send to the developer"));
        export.onClick(this::export);
        saveButton = new Ui.Button(Ui.t("Enregistrer dans le jeu", "Save to game"), true);
        saveButton.setToolTipText("Ctrl+S");
        saveButton.onClick(this::saveToGame);
        right.add(openEn);
        right.add(export);
        right.add(saveButton);
        p.add(right, BorderLayout.EAST);
        return p;
    }

    private JComponent buildTable() {
        table = new JTable(model);
        table.setFont(Ui.font(Font.PLAIN, 13));
        table.setRowHeight(30);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setFont(Ui.font(Font.BOLD, 13));
        table.getTableHeader().setReorderingAllowed(false);
        table.setDefaultRenderer(Object.class, new RowRenderer());
        table.getColumnModel().getColumn(0).setPreferredWidth(190);
        table.getColumnModel().getColumn(1).setPreferredWidth(420);
        table.getColumnModel().getColumn(2).setPreferredWidth(420);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) onRowSelected();
        });
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) focusEditor();
            }
        });
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "focusEditor");
        table.getActionMap().put("focusEditor", action(this::focusEditor));

        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createLineBorder(Ui.BORDER));
        sp.getViewport().setBackground(Color.WHITE);
        return sp;
    }

    private JComponent buildEditor() {
        JPanel card = new JPanel(new BorderLayout(0, 8));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Ui.BORDER),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));

        JPanel top = new JPanel(new BorderLayout(12, 0));
        top.setOpaque(false);
        keyLabel = Ui.label(" ", Font.BOLD, 13, Ui.MUTED);
        problemLabel = Ui.label(" ", Font.PLAIN, 13, Ui.ERROR);
        problemLabel.setHorizontalAlignment(JLabel.RIGHT);
        top.add(keyLabel, BorderLayout.WEST);
        top.add(problemLabel, BorderLayout.CENTER);
        card.add(top, BorderLayout.NORTH);

        JPanel cols = new JPanel(new GridLayout(1, 2, 14, 0));
        cols.setOpaque(false);
        enArea = area(false);
        trArea = area(true);
        trArea.getDocument().addDocumentListener(onChange(this::onTranslationEdited));
        cols.add(column(Ui.label("English", Font.BOLD, 13, Ui.TEXT), enArea));
        trTitle = Ui.label(" ", Font.BOLD, 13, Ui.BLUE);
        cols.add(column(trTitle, trArea));
        card.add(cols, BorderLayout.CENTER);

        JPanel nav = new JPanel(new BorderLayout());
        nav.setOpaque(false);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        Ui.Button prev = new Ui.Button(Ui.t("◀  Précédent", "◀  Previous"), false);
        prev.setToolTipText("Alt+↑");
        prev.onClick(() -> move(-1));
        Ui.Button next = new Ui.Button(Ui.t("Suivant  ▶", "Next  ▶"), false);
        next.setToolTipText("Alt+↓");
        next.onClick(() -> move(1));
        Ui.Button nextTodo = new Ui.Button(Ui.t("Prochain à traduire", "Next to translate"), true);
        nextTodo.setToolTipText("Ctrl+Entrée / Ctrl+Enter");
        nextTodo.onClick(this::nextTodo);
        buttons.add(prev);
        buttons.add(next);
        buttons.add(nextTodo);
        nav.add(buttons, BorderLayout.WEST);
        nav.add(Ui.label(Ui.t("Entrée = nouvelle ligne · Ctrl+Entrée = suivant · Ctrl+S = enregistrer",
                "Enter = new line · Ctrl+Enter = next · Ctrl+S = save"), Font.PLAIN, 12, Ui.MUTED), BorderLayout.EAST);
        card.add(nav, BorderLayout.SOUTH);
        card.setMinimumSize(new Dimension(200, 190));
        return card;
    }

    private static JTextArea area(boolean editable) {
        JTextArea a = new JTextArea(4, 20);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setEditable(editable);
        a.setFont(Ui.font(Font.PLAIN, 15));
        a.setForeground(Ui.TEXT);
        a.setBackground(editable ? Color.WHITE : new Color(0xF7F9FD));
        a.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        return a;
    }

    private static JComponent column(JLabel title, JTextArea a) {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setOpaque(false);
        p.add(title, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(a);
        sp.setBorder(BorderFactory.createLineBorder(a.isEditable() ? Ui.BLUE_LIGHT : Ui.BORDER));
        p.add(sp, BorderLayout.CENTER);
        return p;
    }

    private void bindKeys(JComponent root) {
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK), "save", this::saveToGame);
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK), "search", () -> {
            search.requestFocusInWindow();
            search.selectAll();
        });
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK), "nextTodo", this::nextTodo);
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, KeyEvent.ALT_DOWN_MASK), "next", () -> move(1));
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_UP, KeyEvent.ALT_DOWN_MASK), "prev", () -> move(-1));
        // la zone de texte garde la main sur ces raccourcis
        trArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK), "nextTodo");
        trArea.getActionMap().put("nextTodo", action(this::nextTodo));
    }

    private static void bind(JComponent c, KeyStroke ks, String name, Runnable r) {
        c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ks, name);
        c.getActionMap().put(name, action(r));
    }

    private static AbstractAction action(Runnable r) {
        return new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { r.run(); }
        };
    }

    private static DocumentListener onChange(Runnable r) {
        return new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { r.run(); }
            @Override public void removeUpdate(DocumentEvent e) { r.run(); }
            @Override public void changedUpdate(DocumentEvent e) { r.run(); }
        };
    }

    // ------------------------------------------------------------ chargement

    private void askGameDir() {
        int r = JOptionPane.showConfirmDialog(frame,
                Ui.t("Le dossier de Lurkers n'a pas été trouvé.\nVeux-tu le choisir ? (sinon, ouvre un fichier anglais à la main)",
                        "The Lurkers folder was not found.\nDo you want to pick it? (otherwise, open an English file manually)"),
                "Lurkers Translator", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (r != JOptionPane.YES_OPTION) return;
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle(Ui.t("Dossier du jeu (celui qui contient lurkers.exe)", "Game folder (the one with lurkers.exe)"));
        if (fc.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        File d = fc.getSelectedFile();
        if (Sources.looksLikeGame(d)) {
            gameDir = d;
        } else {
            JOptionPane.showMessageDialog(frame, Ui.t("Ce dossier ne contient pas lurkers.exe.", "This folder has no lurkers.exe."),
                    "Lurkers Translator", JOptionPane.WARNING_MESSAGE);
        }
    }

    /** (Re)lit le texte anglais, puis la traduction de la langue choisie. */
    private void reloadEnglish() {
        englishJar = Sources.englishJar(gameDir);
        String text = null;
        try {
            if (englishFile != null) {
                text = Sources.readFile(englishFile);
                englishLabel = englishFile.getName();
            } else if (englishJar != null) {
                text = Sources.readFromJar(englishJar, Sources.BASE);
                String v = Sources.buildVersion(englishJar);
                englishLabel = englishJar.getName() + (v != null ? "  ·  build " + v : "");
            }
        } catch (IOException e) {
            text = null;
        }
        template = text == null ? null : PropFile.parse(text);
        fillLanguages();
        reloadTranslation();
    }

    private void fillLanguages() {
        loading = true;
        try {
            String keep = lang != null ? lang.code : defaultLanguage();
            List<Lang> list = new ArrayList<>();
            for (String[] k : KNOWN) list.add(new Lang(k[0], k[1]));
            for (String code : Sources.existingLanguages(gameDir, englishJar)) {
                if (findLang(list, code) == null) list.add(new Lang(code, displayName(code)));
            }
            if (findLang(list, keep) == null) list.add(new Lang(keep, displayName(keep)));
            langBox.removeAllItems();
            for (Lang l : list) langBox.addItem(l);
            langBox.addItem(OTHER);
            lang = findLang(list, keep);
            langBox.setSelectedItem(lang);
        } finally {
            loading = false;
        }
    }

    private static String defaultLanguage() {
        Locale l = Locale.getDefault();
        if ("en".equals(l.getLanguage()) || l.getLanguage().isEmpty()) return "fr";
        String withCountry = l.getLanguage() + "_" + l.getCountry();
        for (String[] k : KNOWN) if (k[0].equals(withCountry)) return withCountry;
        return l.getLanguage();
    }

    private static Lang findLang(List<Lang> list, String code) {
        for (Lang l : list) if (l.code.equals(code)) return l;
        return null;
    }

    private static String displayName(String code) {
        String[] p = code.split("_");
        Locale l = p.length > 1 ? new Locale(p[0], p[1]) : new Locale(p[0]);
        String n = l.getDisplayName(l);
        return n.isEmpty() ? code : Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    private void onLanguagePicked() {
        if (loading) return;
        Lang picked = (Lang) langBox.getSelectedItem();
        if (picked == null || picked == lang) return;
        if (picked == OTHER) {
            String code = JOptionPane.showInputDialog(frame,
                    Ui.t("Code de la langue (ex. : fr, es, pt_BR) :", "Language code (e.g. fr, es, pt_BR):"),
                    "Lurkers Translator", JOptionPane.QUESTION_MESSAGE);
            loading = true;
            langBox.setSelectedItem(lang);
            loading = false;
            if (code == null) return;
            code = code.trim();
            if (code.contains("_")) code = code.substring(0, code.indexOf('_')).toLowerCase() + code.substring(code.indexOf('_')).toUpperCase();
            else code = code.toLowerCase();
            if (!Sources.LANG_CODE.matcher(code).matches() || code.equals("en")) {
                JOptionPane.showMessageDialog(frame, Ui.t("Code de langue invalide.", "Invalid language code."));
                return;
            }
            if (!confirmDiscard()) return;
            lang = new Lang(code, displayName(code));
            fillLanguages();
            reloadTranslation();
            return;
        }
        if (!confirmDiscard()) {
            loading = true;
            langBox.setSelectedItem(lang);
            loading = false;
            return;
        }
        lang = picked;
        reloadTranslation();
    }

    private void reloadTranslation() {
        items.clear();
        commentedTr = new LinkedHashMap<>();
        int obsolete = 0;
        String trSource = null;
        if (template != null) {
            Map<String, String> tr = Collections.emptyMap();
            try {
                File f = gameDir != null ? Sources.target(gameDir, lang.code) : null;
                String text = null;
                if (f != null && f.isFile()) {
                    text = Sources.readFile(f);
                    trSource = "i18n/" + f.getName();
                } else {
                    for (File jar : new File[]{gameDir != null ? new File(gameDir, "desktop-1.0-obf.jar") : null, englishJar}) {
                        if (jar == null || !jar.isFile()) continue;
                        text = Sources.readFromJar(jar, "language_" + lang.code + ".properties");
                        if (text != null) {
                            trSource = jar.getName();
                            break;
                        }
                    }
                }
                if (text != null) {
                    PropFile pf = PropFile.parse(text);
                    tr = pf.values();
                    commentedTr = pf.commentedValues();
                }
            } catch (IOException e) {
                setStatus(Ui.t("Impossible de lire la traduction : ", "Could not read the translation: ") + e.getMessage(), Ui.ERROR);
            }
            String section = "";
            for (PropFile.Line l : template.lines) {
                if (l.kind == PropFile.Kind.COMMENT) {
                    String s = PropFile.sectionName(l.raw);
                    if (s != null) section = s;
                } else if (l.kind == PropFile.Kind.ENTRY) {
                    String v = tr.get(l.key);
                    items.add(new Item(section, l.key, l.value, v == null ? "" : v));
                }
            }
            Map<String, String> en = template.values();
            for (String k : tr.keySet()) if (!en.containsKey(k)) obsolete++;
        }
        dirty = false;
        trTitle.setText(lang.name);
        table.getColumnModel().getColumn(2).setHeaderValue(lang.name);
        table.getTableHeader().repaint();
        sourceLabel.setText(template == null
                ? Ui.t("Texte anglais introuvable : clique sur « Ouvrir un fichier anglais… »",
                "English text not found: click “Open English file…”")
                : Ui.t("Anglais : ", "English: ") + englishLabel
                + (gameDir != null ? "   ·   " + Ui.t("Jeu : ", "Game: ") + gameDir.getName() : ""));
        sourceLabel.setToolTipText(gameDir != null ? gameDir.getAbsolutePath() : null);
        refilter();
        updateProgress();
        updateTitle();
        if (template == null) {
            setStatus(Ui.t("Aucun texte chargé.", "Nothing loaded."), Ui.WARN);
        } else {
            String msg = trSource != null
                    ? Ui.t("Traduction chargée depuis ", "Translation loaded from ") + trSource + "."
                    : Ui.t("Nouvelle traduction : tout est à traduire.", "New translation: everything is left to translate.");
            if (obsolete > 0) {
                msg += "  " + obsolete + Ui.t(" texte(s) n'existent plus dans la version anglaise (ils seront retirés à l'enregistrement).",
                        " text(s) no longer exist in the English version (removed when saving).");
            }
            setStatus(msg, Ui.MUTED);
        }
        if (!rows.isEmpty()) {
            int first = firstIndex(i -> i.state() == State.TODO || i.state() == State.WARN);
            select(first >= 0 ? first : firstIndex(i -> true));
        } else {
            showItem(null);
        }
    }

    private void chooseEnglishFile() {
        JFileChooser fc = new JFileChooser(englishFile != null ? englishFile.getParentFile() : null);
        fc.setFileFilter(new FileNameExtensionFilter("language.properties", "properties"));
        if (fc.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        if (!confirmDiscard()) return;
        englishFile = fc.getSelectedFile();
        reloadEnglish();
    }

    // ------------------------------------------------------------ liste

    interface ItemTest { boolean test(Item i); }

    private void refilter() {
        Item keep = current;
        String q = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        int f = filter == null ? 0 : filter.getSelectedIndex();
        rows.clear();
        String lastSection = null;
        for (Item i : items) {
            State s = i.state();
            if (f == 1 && s != State.TODO) continue;
            if (f == 2 && s != State.WARN) continue;
            if (!q.isEmpty() && !(i.key.toLowerCase(Locale.ROOT).contains(q)
                    || PropFile.toDisplay(i.en).toLowerCase(Locale.ROOT).contains(q)
                    || PropFile.toDisplay(i.tr).toLowerCase(Locale.ROOT).contains(q))) continue;
            if (f == 0 && q.isEmpty() && !i.section.isEmpty() && !i.section.equals(lastSection)) rows.add(i.section);
            lastSection = i.section;
            rows.add(i);
        }
        model.fireTableDataChanged();
        if (keep != null && rows.contains(keep)) select(rows.indexOf(keep));
    }

    private int firstIndex(ItemTest t) {
        for (int r = 0; r < rows.size(); r++) {
            Object o = rows.get(r);
            if (o instanceof Item && t.test((Item) o)) return r;
        }
        return -1;
    }

    private void select(int row) {
        if (row < 0 || row >= rows.size()) return;
        table.getSelectionModel().setSelectionInterval(row, row);
        table.scrollRectToVisible(table.getCellRect(row, 0, true));
    }

    private void onRowSelected() {
        int r = table.getSelectedRow();
        if (r < 0 || r >= rows.size()) return;
        Object o = rows.get(r);
        if (o instanceof Item) {
            showItem((Item) o);
        } else if (r + 1 < rows.size()) {
            select(r + 1); // une section n'est pas éditable : on passe au texte suivant
        }
    }

    private void showItem(Item i) {
        current = i;
        loading = true;
        try {
            if (i == null) {
                keyLabel.setText(" ");
                enArea.setText("");
                trArea.setText("");
                trArea.setEnabled(false);
            } else {
                keyLabel.setText(i.key + (i.section.isEmpty() ? "" : "   ·   " + i.section));
                enArea.setText(i.en.isEmpty() ? Ui.t("(vide : c'est un séparateur, laisse-le vide)",
                        "(empty: this is a separator, leave it empty)") : PropFile.toDisplay(i.en));
                enArea.setCaretPosition(0);
                trArea.setEnabled(!i.en.isEmpty() || !i.tr.isEmpty());
                trArea.setText(PropFile.toDisplay(i.tr));
                trArea.setCaretPosition(trArea.getDocument().getLength());
            }
        } finally {
            loading = false;
        }
        updateProblem();
    }

    private void onTranslationEdited() {
        if (loading || current == null) return;
        String raw = PropFile.toRaw(trArea.getText());
        if (raw.equals(current.tr)) return;
        current.tr = raw;
        dirty = true;
        int r = rows.indexOf(current);
        if (r >= 0) model.fireTableRowsUpdated(r, r);
        updateProblem();
        updateProgress();
        updateTitle();
    }

    private void updateProblem() {
        if (current == null) {
            problemLabel.setText(" ");
            return;
        }
        String p = current.problem();
        String n = current.note();
        if (p != null) {
            problemLabel.setForeground(Ui.ERROR);
            problemLabel.setText("⚠ " + p);
        } else if (n != null) {
            problemLabel.setForeground(Ui.WARN);
            problemLabel.setText(n);
        } else if (current.state() == State.DONE) {
            problemLabel.setForeground(Ui.OK);
            problemLabel.setText("✓ " + Ui.t("Traduit", "Translated"));
        } else {
            problemLabel.setText(" ");
        }
    }

    private void move(int dir) {
        int r = table.getSelectedRow();
        for (int k = r + dir; k >= 0 && k < rows.size(); k += dir) {
            if (rows.get(k) instanceof Item) {
                select(k);
                focusEditor();
                return;
            }
        }
    }

    private void nextTodo() {
        int start = Math.max(table.getSelectedRow(), -1);
        for (int n = 1; n <= rows.size(); n++) {
            int k = (start + n) % rows.size();
            Object o = rows.get(k);
            if (o instanceof Item && o != current) {
                State s = ((Item) o).state();
                if (s == State.TODO || s == State.WARN) {
                    select(k);
                    focusEditor();
                    return;
                }
            }
        }
        setStatus(Ui.t("Plus rien à traduire dans cette liste. Bravo !", "Nothing left to translate in this list. Well done!"), Ui.OK);
    }

    private void focusEditor() {
        if (trArea.isEnabled()) trArea.requestFocusInWindow();
    }

    private void updateProgress() {
        int total = 0, done = 0, warn = 0;
        for (Item i : items) {
            if (i.en.isEmpty()) continue;
            total++;
            State s = i.state();
            if (s == State.DONE) done++;
            if (s == State.WARN) {
                done++;
                warn++;
            }
        }
        int pct = total == 0 ? 0 : (int) Math.floor(100.0 * done / total);
        progressLabel.setText(done + " / " + total + "  ·  " + pct + " %"
                + (warn > 0 ? "  ·  ⚠ " + warn : ""));
        bar.set(total == 0 ? 0 : (double) done / total);
    }

    private void updateTitle() {
        frame.setTitle((dirty ? "• " : "") + "Lurkers Translator " + VERSION + (lang != null ? " — " + lang.name : ""));
    }

    private void setStatus(String s, Color c) {
        statusLabel.setForeground(c);
        statusLabel.setText(s);
    }

    // ------------------------------------------------------------ enregistrement

    /** Fichier final : même ordre, mêmes sections et mêmes lignes vides que le fichier anglais. */
    String buildOutput() {
        StringBuilder sb = new StringBuilder();
        String nl = template.newline;
        int idx = 0;
        for (PropFile.Line l : template.lines) {
            switch (l.kind) {
                case BLANK:
                    sb.append(nl);
                    break;
                case COMMENT: {
                    String k = PropFile.commentedKey(l.raw);
                    if (k != null && commentedTr.containsKey(k)) sb.append('#').append(k).append('=').append(commentedTr.get(k));
                    else sb.append(l.raw);
                    sb.append(nl);
                    break;
                }
                case ENTRY: {
                    Item i = items.get(idx++);
                    // pas encore traduit : on n'écrit rien, le jeu affichera l'anglais
                    if (!i.tr.isEmpty()) sb.append(i.key).append('=').append(i.tr).append(nl);
                    else if (i.en.isEmpty()) sb.append(i.key).append('=').append(nl);
                    break;
                }
            }
        }
        return sb.toString();
    }

    private boolean checkBeforeSave() {
        if (template == null) {
            setStatus(Ui.t("Rien à enregistrer : aucun texte anglais chargé.", "Nothing to save: no English text loaded."), Ui.WARN);
            return false;
        }
        int warn = 0;
        for (Item i : items) if (i.state() == State.WARN) warn++;
        if (warn == 0) return true;
        int r = JOptionPane.showConfirmDialog(frame,
                Ui.t(warn + " texte(s) ont un problème (filtre « Problèmes » pour les voir).\nEnregistrer quand même ?",
                        warn + " text(s) have a problem (use the “Problems” filter to see them).\nSave anyway?"),
                "Lurkers Translator", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        return r == JOptionPane.YES_OPTION;
    }

    private void saveToGame() {
        if (!checkBeforeSave()) return;
        if (gameDir == null) {
            askGameDir();
            if (gameDir == null) {
                export();
                return;
            }
            fillLanguages();
        }
        File target = Sources.target(gameDir, lang.code);
        try {
            write(target, true);
            dirty = false;
            updateTitle();
            setStatus(Ui.t("Enregistré dans i18n/" + target.getName() + ". Le jeu l'utilise tout seul si Windows est en "
                            + lang.name + " (relance le jeu pour voir).",
                    "Saved to i18n/" + target.getName() + ". The game uses it automatically when Windows is set to "
                            + lang.name + " (restart the game to see it)."), Ui.OK);
        } catch (IOException e) {
            setStatus(Ui.t("Impossible d'écrire dans le dossier du jeu (" + e.getMessage()
                            + "). Lance l'outil en administrateur, ou utilise « Exporter… ».",
                    "Could not write to the game folder (" + e.getMessage()
                            + "). Run the tool as administrator, or use “Export…”."), Ui.ERROR);
        }
    }

    private void export() {
        if (!checkBeforeSave()) return;
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("language_" + lang.code + ".properties"));
        if (fc.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        try {
            write(f, false);
            setStatus(Ui.t("Exporté : ", "Exported: ") + f.getAbsolutePath(), Ui.OK);
        } catch (IOException e) {
            setStatus(Ui.t("Échec de l'export : ", "Export failed: ") + e.getMessage(), Ui.ERROR);
        }
    }

    /** Écrit en UTF-8 (sans BOM). Garde une copie .bak de l'ancien fichier. */
    void write(File f, boolean backup) throws IOException {
        File dir = f.getAbsoluteFile().getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IOException(dir.getPath());
        if (backup && f.isFile()) {
            Files.copy(f.toPath(), new File(f.getPath() + ".bak").toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        File tmp = new File(f.getPath() + ".tmp");
        Files.write(tmp.toPath(), buildOutput().getBytes(StandardCharsets.UTF_8));
        Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private boolean confirmDiscard() {
        if (!dirty) return true;
        int r = JOptionPane.showConfirmDialog(frame,
                Ui.t("Enregistrer les changements dans le jeu ?", "Save your changes to the game?"),
                "Lurkers Translator", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (r == JOptionPane.CANCEL_OPTION || r == JOptionPane.CLOSED_OPTION) return false;
        if (r == JOptionPane.YES_OPTION) {
            saveToGame();
            return !dirty;
        }
        return true;
    }

    // ------------------------------------------------------------ composants

    private final class RowModel extends AbstractTableModel {
        @Override public int getRowCount() { return rows.size(); }

        @Override public int getColumnCount() { return 3; }

        @Override public String getColumnName(int c) {
            return c == 0 ? Ui.t("Clé", "Key") : c == 1 ? "English" : (lang != null ? lang.name : "");
        }

        @Override public Object getValueAt(int r, int c) {
            Object o = rows.get(r);
            if (!(o instanceof Item)) return c == 0 ? o : "";
            Item i = (Item) o;
            if (c == 0) return i.key;
            String s = c == 1 ? i.en : i.tr;
            return PropFile.toDisplay(s).replace("\n", "  ⏎  ");
        }
    }

    private final class RowRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
            super.getTableCellRendererComponent(t, v, false, false, r, c);
            setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            setToolTipText(null);
            Object o = rows.get(r);
            if (!(o instanceof Item)) {
                setBackground(Ui.ROW_SECTION);
                setForeground(Ui.BLUE_DARK);
                setFont(Ui.font(Font.BOLD, 13));
                return this;
            }
            Item i = (Item) o;
            State s = i.state();
            Color bg = s == State.TODO ? Ui.ROW_TODO : s == State.WARN ? Ui.ROW_WARN : Color.WHITE;
            setBackground(sel ? Ui.ROW_SELECTED : bg);
            setFont(Ui.font(Font.PLAIN, c == 0 ? 12 : 13));
            setForeground(c == 0 || s == State.EMPTY ? Ui.MUTED : Ui.TEXT);
            if (c == 2 && s == State.TODO) {
                setForeground(Ui.WARN);
                setText(Ui.t("à traduire", "to translate"));
            } else if (c == 2 && s == State.EMPTY) {
                setText(Ui.t("(vide)", "(empty)"));
            }
            if (c != 0 && v != null && !v.toString().isEmpty()) setToolTipText(tooltip(c == 1 ? i.en : i.tr));
            if (s == State.WARN && c == 2) setToolTipText(i.problem());
            return this;
        }
    }

    private static String tooltip(String raw) {
        String s = PropFile.toDisplay(raw).replace("&", "&amp;").replace("<", "&lt;").replace("\n", "<br>");
        return "<html><div style='width:360px'>" + s + "</div></html>";
    }

    /** Champ de recherche avec un texte d'aide grisé. */
    private static final class SearchField extends JTextField {
        private final String hint;

        SearchField(String hint) {
            this.hint = hint;
            setFont(Ui.font(Font.PLAIN, 13));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Ui.BORDER),
                    BorderFactory.createEmptyBorder(0, 10, 0, 10)));
        }

        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            if (!getText().isEmpty()) return;
            Graphics2D g = (Graphics2D) g0.create();
            Ui.smooth(g);
            g.setColor(Ui.MUTED);
            g.setFont(getFont());
            int y = (getHeight() - g.getFontMetrics().getHeight()) / 2 + g.getFontMetrics().getAscent();
            g.drawString(hint, getInsets().left, y);
            g.dispose();
        }
    }

    /** Petite barre de progression blanche sur le bandeau bleu. */
    private static final class Bar extends JComponent {
        private double v;

        Bar() { setPreferredSize(new Dimension(180, 10)); }

        void set(double x) {
            v = Math.max(0, Math.min(1, x));
            repaint();
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Ui.smooth(g);
            int h = 8, y = (getHeight() - h) / 2;
            g.setColor(new Color(255, 255, 255, 70));
            g.fillRoundRect(0, y, getWidth(), h, h, h);
            g.setColor(Color.WHITE);
            g.fillRoundRect(0, y, (int) Math.round(getWidth() * v), h, h, h);
            g.dispose();
        }
    }

    /** Icône : bulle bleue avec « Aa ». */
    private static java.awt.Image icon() {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Ui.smooth(g);
        g.setPaint(new java.awt.GradientPaint(0, 0, Ui.BLUE_DARK, 64, 64, Ui.BLUE_LIGHT));
        g.fillRoundRect(2, 2, 60, 60, 18, 18);
        g.setColor(Color.WHITE);
        g.setFont(Ui.font(Font.BOLD, 28));
        java.awt.FontMetrics fm = g.getFontMetrics();
        g.drawString("Aa", (64 - fm.stringWidth("Aa")) / 2, (64 - fm.getHeight()) / 2 + fm.getAscent());
        g.dispose();
        return img;
    }
}
