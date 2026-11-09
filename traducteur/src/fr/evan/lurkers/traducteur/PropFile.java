package fr.evan.lurkers.traducteur;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fichier de langue du jeu (format .properties de libGDX), lu ligne par ligne
 * pour garder l'ordre, les sections et les lignes vides. Les textes sont gardés
 * tels qu'ils sont écrits dans le fichier (avec les \n), pour réécrire
 * exactement la même chose.
 */
final class PropFile {
    enum Kind { BLANK, COMMENT, ENTRY }

    static final class Line {
        final Kind kind;
        final String raw;
        final String key;
        final String value;

        Line(Kind kind, String raw, String key, String value) {
            this.kind = kind;
            this.raw = raw;
            this.key = key;
            this.value = value;
        }
    }

    private static final Pattern COMMENTED_ENTRY = Pattern.compile("^[#!]\\s*([A-Za-z0-9_.\\-]+)\\s*[=:](.*)$");

    final List<Line> lines;
    final String newline;

    private PropFile(List<Line> lines, String newline) {
        this.lines = Collections.unmodifiableList(lines);
        this.newline = newline;
    }

    static PropFile parse(String text) {
        if (text.startsWith("﻿")) text = text.substring(1);
        String nl = text.contains("\r\n") ? "\r\n" : "\n";
        List<Line> out = new ArrayList<>();
        String[] parts = text.split("\r?\n", -1);
        int n = parts.length;
        if (n > 0 && parts[n - 1].isEmpty()) n--; // dernière ligne vide = fin de fichier
        for (int i = 0; i < n; i++) out.add(parseLine(parts[i]));
        return new PropFile(out, nl);
    }

    private static Line parseLine(String raw) {
        String t = raw.trim();
        if (t.isEmpty()) return new Line(Kind.BLANK, raw, null, null);
        if (t.startsWith("#") || t.startsWith("!")) return new Line(Kind.COMMENT, raw, null, null);
        String s = stripLeading(raw);
        int sep = -1;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') { i++; continue; }
            if (c == '=' || c == ':') { sep = i; break; }
        }
        if (sep < 0) return new Line(Kind.ENTRY, raw, s.trim(), "");
        String key = s.substring(0, sep).trim();
        String value = stripLeading(s.substring(sep + 1));
        return new Line(Kind.ENTRY, raw, key, value);
    }

    private static String stripLeading(String s) {
        int i = 0;
        while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '\t' || s.charAt(i) == '\f')) i++;
        return s.substring(i);
    }

    /** clé → texte, dans l'ordre du fichier. */
    Map<String, String> values() {
        Map<String, String> m = new LinkedHashMap<>();
        for (Line l : lines) if (l.kind == Kind.ENTRY) m.put(l.key, l.value);
        return m;
    }

    /** Lignes mises en commentaire du type « #clé=texte » (variantes gardées par le dev). */
    Map<String, String> commentedValues() {
        Map<String, String> m = new LinkedHashMap<>();
        for (Line l : lines) {
            if (l.kind != Kind.COMMENT) continue;
            Matcher mt = COMMENTED_ENTRY.matcher(l.raw.trim());
            if (mt.matches()) m.put(mt.group(1), stripLeading(mt.group(2)));
        }
        return m;
    }

    static String commentedKey(String rawComment) {
        Matcher mt = COMMENTED_ENTRY.matcher(rawComment.trim());
        return mt.matches() ? mt.group(1) : null;
    }

    /** Nom de section pour un commentaire « # Chat », sinon null. */
    static String sectionName(String rawComment) {
        if (commentedKey(rawComment) != null) return null;
        String s = rawComment.trim().replaceFirst("^[#!]+", "").trim();
        return s.isEmpty() ? null : s;
    }

    // ------------------------------------------------------------ affichage <-> fichier

    /** Texte du fichier → texte affiché : « \n » devient un vrai retour à la ligne. */
    static String toDisplay(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\\' && i + 1 < raw.length()) {
                char d = raw.charAt(i + 1);
                if (d == 'n') { sb.append('\n'); i++; continue; }
                if (d == ' ') { sb.append(' '); i++; continue; }
                sb.append(c).append(d);
                i++;
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /** Texte affiché → texte du fichier : un retour à la ligne devient « \n ». */
    static String toRaw(String display) {
        String s = display.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n') sb.append("\\n");
            else sb.append(c);
        }
        // un espace en tête serait supprimé par le jeu : on le protège
        if (sb.length() > 0 && sb.charAt(0) == ' ') sb.insert(0, '\\');
        return sb.toString();
    }

    // ------------------------------------------------------------ vérifications

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)");

    static List<String> placeholders(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        Matcher m = PLACEHOLDER.matcher(raw);
        while (m.find()) out.add("{" + m.group(1) + "}");
        Collections.sort(out);
        return out;
    }

    static int lineBreaks(String raw) {
        if (raw == null) return 0;
        int n = 0;
        for (int i = raw.indexOf("\\n"); i >= 0; i = raw.indexOf("\\n", i + 2)) n++;
        return n;
    }
}
