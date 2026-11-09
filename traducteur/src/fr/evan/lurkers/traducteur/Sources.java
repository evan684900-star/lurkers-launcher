package fr.evan.lurkers.traducteur;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Où trouver les fichiers de langue. Le .jar du jeu est seulement LU (jamais
 * modifié) ; la traduction est écrite dans <jeu>/i18n/, que le jeu lit avant
 * son propre .jar.
 */
final class Sources {
    private Sources() {}

    static final String BASE = "language.properties";
    static final Pattern LANG_FILE = Pattern.compile("language_([A-Za-z]{2,3}(?:_[A-Za-z]{2})?)\\.properties");
    static final Pattern LANG_CODE = Pattern.compile("[a-z]{2,3}(_[A-Z]{2})?");

    static boolean looksLikeGame(File d) {
        return d != null && new File(d, "lurkers.exe").isFile() && new File(d, "lurkers.json").isFile();
    }

    /** Dossier du jeu : argument, dossier du .jar de l'outil (ou ses parents), dossier courant, Steam. */
    static File findGameDir(String[] args) {
        if (args.length > 0 && looksLikeGame(new File(args[0]))) return new File(args[0]).getAbsoluteFile();
        try {
            File jar = new File(Sources.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            for (File d = jar.getParentFile(); d != null; d = d.getParentFile()) {
                if (looksLikeGame(d)) return d;
            }
        } catch (Exception ignored) {
        }
        File cwd = new File(".").getAbsoluteFile();
        if (looksLikeGame(cwd)) return cwd;
        for (String p : new String[]{
                "C:\\Program Files (x86)\\Steam\\steamapps\\common\\Lurkers Demo",
                "C:\\Program Files (x86)\\Steam\\steamapps\\common\\Lurkers",
                "C:\\Program Files\\Steam\\steamapps\\common\\Lurkers Demo",
                "C:\\Program Files\\Steam\\steamapps\\common\\Lurkers"}) {
            File d = new File(p);
            if (looksLikeGame(d)) return d;
        }
        return null;
    }

    static File i18nDir(File gameDir) { return new File(gameDir, "i18n"); }

    static File target(File gameDir, String code) {
        return new File(i18nDir(gameDir), "language_" + code + ".properties");
    }

    /**
     * Le .jar où lire le texte anglais. Si la copie d'origine existe et a la
     * même version que le .jar actuel, on la préfère : le .jar actuel a pu être
     * modifié à la main. Après une mise à jour Steam, le .jar actuel est plus
     * récent et c'est lui qu'on prend.
     */
    static File englishJar(File gameDir) {
        if (gameDir == null) return null;
        File main = new File(gameDir, "desktop-1.0-obf.jar");
        File orig = new File(gameDir, "desktop-1.0-obf-original.jar");
        if (!main.isFile()) return orig.isFile() ? orig : anyGameJar(gameDir);
        if (!orig.isFile()) return main;
        String vm = buildVersion(main), vo = buildVersion(orig);
        if (vm != null && vo != null && compareVersions(vm, vo) > 0) return main;
        return orig;
    }

    private static File anyGameJar(File gameDir) {
        File[] jars = gameDir.listFiles((d, n) -> n.toLowerCase().endsWith(".jar"));
        if (jars == null) return null;
        for (File j : jars) if (findEntry(j, BASE) != null) return j;
        return null;
    }

    /** Version du jeu lue dans ses notes de version (« 223.7 »), ou null. */
    static String buildVersion(File jar) {
        try (ZipFile z = new ZipFile(jar)) {
            ZipEntry e = z.getEntry("misc/release_notes.json");
            if (e == null) return null;
            String s = new String(readAll(z.getInputStream(e)), StandardCharsets.UTF_8);
            Matcher m = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"").matcher(s);
            return m.find() ? m.group(1) : null;
        } catch (IOException ex) {
            return null;
        }
    }

    static int compareVersions(String a, String b) {
        String[] x = a.split("\\D+"), y = b.split("\\D+");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            long p = i < x.length && !x[i].isEmpty() ? Long.parseLong(x[i]) : 0;
            long q = i < y.length && !y[i].isEmpty() ? Long.parseLong(y[i]) : 0;
            if (p != q) return Long.compare(p, q);
        }
        return 0;
    }

    /** Chemin d'un fichier de langue dans le .jar (« i18n/language.properties » en général), ou null. */
    static String findEntry(File jar, String fileName) {
        if (jar == null || !jar.isFile()) return null;
        try (ZipFile z = new ZipFile(jar)) {
            if (z.getEntry("i18n/" + fileName) != null) return "i18n/" + fileName;
            Enumeration<? extends ZipEntry> en = z.entries();
            while (en.hasMoreElements()) {
                String n = en.nextElement().getName();
                if (n.equals(fileName) || n.endsWith("/" + fileName)) return n;
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    static String readFromJar(File jar, String fileName) throws IOException {
        String entry = findEntry(jar, fileName);
        if (entry == null) return null;
        try (ZipFile z = new ZipFile(jar)) {
            return new String(readAll(z.getInputStream(z.getEntry(entry))), StandardCharsets.UTF_8);
        }
    }

    static String readFile(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    /** Codes de langue déjà présents (dans le .jar et dans <jeu>/i18n). */
    static List<String> existingLanguages(File gameDir, File jar) {
        TreeSet<String> set = new TreeSet<>();
        if (jar != null && jar.isFile()) {
            try (ZipFile z = new ZipFile(jar)) {
                Enumeration<? extends ZipEntry> en = z.entries();
                while (en.hasMoreElements()) {
                    String n = en.nextElement().getName();
                    Matcher m = LANG_FILE.matcher(n.substring(n.lastIndexOf('/') + 1));
                    if (m.matches()) set.add(m.group(1));
                }
            } catch (IOException ignored) {
            }
        }
        if (gameDir != null) {
            String[] names = i18nDir(gameDir).list();
            if (names != null) for (String n : names) {
                Matcher m = LANG_FILE.matcher(n);
                if (m.matches()) set.add(m.group(1));
            }
        }
        return new ArrayList<>(set);
    }

    static byte[] readAll(InputStream in) throws IOException {
        try (InputStream is = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int r; (r = is.read(buf)) > 0; ) out.write(buf, 0, r);
            return out.toByteArray();
        }
    }
}
