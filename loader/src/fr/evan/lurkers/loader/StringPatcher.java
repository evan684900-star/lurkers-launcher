package fr.evan.lurkers.loader;

import java.io.ByteArrayOutputStream;
import java.lang.instrument.ClassFileTransformer;
import java.nio.charset.StandardCharsets;
import java.security.ProtectionDomain;
import java.util.Map;

/**
 * Remplace des textes écrits en dur dans le code du jeu (boutons du menu, etc.).
 *
 * Seules les constantes texte UTF-8 du "constant pool" sont réécrites : le code lui-même
 * n'est pas touché et les numéros des constantes ne bougent pas, donc c'est sans risque
 * pour la structure de la classe. On ne remplace que les textes qui correspondent
 * EXACTEMENT, et seulement dans les paquets choisis (l'interface du jeu).
 */
final class StringPatcher implements ClassFileTransformer {
    private final Map<String, String> replacements;
    private final String[] packagePrefixes;
    private String currentClass;

    StringPatcher(Map<String, String> replacements, String[] packagePrefixes) {
        this.replacements = replacements;
        this.packagePrefixes = packagePrefixes;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> redefined,
                            ProtectionDomain pd, byte[] bytes) {
        if (className == null || replacements.isEmpty()) return null;
        boolean wanted = false;
        for (String p : packagePrefixes) if (className.startsWith(p)) { wanted = true; break; }
        if (!wanted) return null;
        try {
            currentClass = className;
            byte[] patched = patch(bytes);
            return patched; // null si rien n'a changé
        } catch (Throwable t) {
            Log.error("traduction impossible pour " + className + " (classe laissée telle quelle)", t);
            return null;
        }
    }

    private byte[] patch(byte[] d) {
        if (d.length < 10 || (d[0] & 0xff) != 0xCA || (d[1] & 0xff) != 0xFE) return null;
        int count = u2(d, 8);
        ByteArrayOutputStream out = new ByteArrayOutputStream(d.length + 256);
        out.write(d, 0, 10);
        int i = 10;
        int changed = 0;
        for (int idx = 1; idx < count; idx++) {
            int tag = d[i] & 0xff;
            int start = i;
            switch (tag) {
                case 1: { // Utf8
                    int len = u2(d, i + 1);
                    String s = decodeModifiedUtf8(d, i + 3, len);
                    String rep = s == null ? null : replacements.get(s);
                    i += 3 + len;
                    if (rep != null) {
                        byte[] nb = encodeModifiedUtf8(rep);
                        out.write(1);
                        out.write((nb.length >> 8) & 0xff);
                        out.write(nb.length & 0xff);
                        out.write(nb, 0, nb.length);
                        changed++;
                        continue;
                    }
                    break;
                }
                case 3: case 4: case 9: case 10: case 11: case 12: case 17: case 18: i += 5; break;
                case 5: case 6: i += 9; idx++; break;
                case 7: case 8: case 16: case 19: case 20: i += 3; break;
                case 15: i += 4; break;
                default: throw new IllegalStateException("tag inconnu " + tag);
            }
            out.write(d, start, i - start);
        }
        if (changed == 0) return null;
        out.write(d, i, d.length - i);
        Log.info("Traduit : " + changed + " texte(s) dans " + currentClass);
        return out.toByteArray();
    }

    private static int u2(byte[] d, int o) { return ((d[o] & 0xff) << 8) | (d[o + 1] & 0xff); }

    /** Les classes Java stockent le texte en "UTF-8 modifié" ; on décode les cas courants. */
    private static String decodeModifiedUtf8(byte[] d, int off, int len) {
        for (int k = off; k < off + len; k++) if (d[k] == 0) return null;
        return new String(d, off, len, StandardCharsets.UTF_8);
    }

    private static byte[] encodeModifiedUtf8(String s) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            if (c >= 0x01 && c <= 0x7F) o.write(c);
            else if (c <= 0x7FF) { o.write(0xC0 | (c >> 6)); o.write(0x80 | (c & 0x3F)); }
            else { o.write(0xE0 | (c >> 12)); o.write(0x80 | ((c >> 6) & 0x3F)); o.write(0x80 | (c & 0x3F)); }
        }
        return o.toByteArray();
    }
}
