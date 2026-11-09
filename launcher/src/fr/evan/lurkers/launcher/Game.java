package fr.evan.lurkers.launcher;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Tout ce que le launcher fait sur les fichiers du jeu. Aucune modification du .jar du jeu. */
final class Game {
    static final String AGENT_ARG = "-javaagent:modloader/lurkers-modloader.jar";

    final File dir;

    Game(File dir) { this.dir = dir; }

    File exe() { return new File(dir, "lurkers.exe"); }
    File json() { return new File(dir, "lurkers.json"); }
    File gameJar() { return new File(dir, "desktop-1.0-obf.jar"); }
    File launcherDir() { return new File(dir, "launcher"); }
    File modloaderDir() { return new File(dir, "modloader"); }
    File modsDir() { return new File(modloaderDir(), "mods"); }
    File gameLog() { return new File(launcherDir(), "jeu.log"); }
    File frSource() { return new File(launcherDir(), "fr/language_fr.properties"); }
    File frTarget() { return new File(dir, "i18n/language_fr.properties"); }

    static boolean looksLikeGame(File d) {
        return d != null && new File(d, "lurkers.exe").isFile() && new File(d, "lurkers.json").isFile();
    }

    /** « 223.7 » lu dans les notes de version du jeu, ou null. */
    String buildVersion() {
        if (!gameJar().isFile()) return null;
        try (ZipFile z = new ZipFile(gameJar())) {
            ZipEntry e = z.getEntry("misc/release_notes.json");
            if (e == null) return null;
            String s = new String(readAll(z.getInputStream(e)), StandardCharsets.UTF_8);
            Matcher m = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"").matcher(s);
            return m.find() ? m.group(1) : null;
        } catch (IOException ex) {
            return null;
        }
    }

    // ------------------------------------------------------------ traduction

    boolean frAvailable() { return frSource().isFile(); }

    boolean frEnabled() { return frTarget().isFile(); }

    void setFr(boolean on) throws IOException {
        if (on) {
            frTarget().getParentFile().mkdirs();
            Files.copy(frSource().toPath(), frTarget().toPath(), StandardCopyOption.REPLACE_EXISTING);
        } else if (frTarget().isFile()) {
            Files.delete(frTarget().toPath());
        }
    }

    // ------------------------------------------------------------ modloader (lurkers.json)

    boolean modloaderInstalled() { return new File(modloaderDir(), "lurkers-modloader.jar").isFile(); }

    boolean modloaderEnabled() throws IOException {
        return readVmArgs().contains(AGENT_ARG);
    }

    void setModloader(boolean on) throws IOException {
        File backup = new File(launcherDir(), "lurkers.json.original");
        if (!backup.isFile()) Files.copy(json().toPath(), backup.toPath());
        List<String> args = readVmArgs();
        args.remove(AGENT_ARG);
        if (on) args.add(AGENT_ARG);
        writeVmArgs(args);
    }

    private static final Pattern VM_ARGS = Pattern.compile("(\"vmArgs\"\\s*:\\s*\\[)(.*?)(\\])", Pattern.DOTALL);
    private static final Pattern JSON_STRING = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

    private List<String> readVmArgs() throws IOException {
        String s = new String(Files.readAllBytes(json().toPath()), StandardCharsets.UTF_8);
        Matcher m = VM_ARGS.matcher(s);
        List<String> out = new ArrayList<>();
        if (!m.find()) return out;
        Matcher sm = JSON_STRING.matcher(m.group(2));
        while (sm.find()) out.add(sm.group(1).replace("\\\"", "\"").replace("\\\\", "\\"));
        return out;
    }

    private void writeVmArgs(List<String> args) throws IOException {
        String s = new String(Files.readAllBytes(json().toPath()), StandardCharsets.UTF_8);
        StringBuilder arr = new StringBuilder("\n");
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i).replace("\\", "\\\\").replace("\"", "\\\"");
            arr.append("    \"").append(a).append('"').append(i < args.size() - 1 ? ",\n" : "\n");
        }
        arr.append("  ");
        Matcher m = VM_ARGS.matcher(s);
        String result;
        if (m.find()) {
            result = s.substring(0, m.start()) + m.group(1) + arr + m.group(3) + s.substring(m.end());
        } else {
            int close = s.lastIndexOf('}');
            if (close < 0) throw new IOException("lurkers.json illisible");
            String before = s.substring(0, close).replaceAll("\\s+$", "");
            result = before + ",\n  \"vmArgs\": [" + arr + "]\n}\n";
        }
        // écriture sûre : fichier temporaire puis remplacement
        File tmp = new File(dir, "lurkers.json.tmp");
        Files.write(tmp.toPath(), result.getBytes(StandardCharsets.UTF_8));
        Files.move(tmp.toPath(), json().toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    // ------------------------------------------------------------ mods

    static final class Mod {
        final File file;
        Mod(File f) { file = f; }
        boolean enabled() { return file.getName().toLowerCase().endsWith(".jar"); }
        String name() {
            String n = file.getName();
            n = n.replaceAll("(?i)\\.jar(\\.disabled)?$", "");
            return n.isEmpty() ? file.getName() : Character.toUpperCase(n.charAt(0)) + n.substring(1);
        }
    }

    List<Mod> mods() {
        List<Mod> out = new ArrayList<>();
        File[] fs = modsDir().listFiles((d, n) -> n.toLowerCase().endsWith(".jar") || n.toLowerCase().endsWith(".jar.disabled"));
        if (fs == null) return out;
        Arrays.sort(fs, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File f : fs) out.add(new Mod(f));
        return out;
    }

    void setModEnabled(Mod m, boolean on) throws IOException {
        String n = m.file.getName();
        String target = on ? n.replaceAll("(?i)\\.disabled$", "") : (n.toLowerCase().endsWith(".disabled") ? n : n + ".disabled");
        if (!target.equals(n)) Files.move(m.file.toPath(), new File(m.file.getParentFile(), target).toPath());
    }

    // ------------------------------------------------------------ lancement

    void launch() throws IOException {
        // La sortie du jeu va dans un fichier : sans ça, le jeu peut se bloquer quand le tampon
        // de sortie est plein, et on garde une trace en cas de plantage.
        File log = gameLog();
        log.getParentFile().mkdirs();
        new ProcessBuilder(exe().getAbsolutePath())
                .directory(dir)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.to(log))
                .start();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try (InputStream i = in) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int r;
            while ((r = i.read(b)) > 0) o.write(b, 0, r);
            return o.toByteArray();
        }
    }
}
