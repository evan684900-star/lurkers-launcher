package fr.evan.lurkers.loader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

/** Journal simple dans modloader/modloader.log (recréé à chaque lancement). */
final class Log {
    private static PrintWriter out;

    static synchronized void init(File file) {
        try {
            out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file, false), StandardCharsets.UTF_8), true);
        } catch (Exception e) {
            out = null;
        }
    }

    static synchronized void info(String msg) {
        String line = new SimpleDateFormat("HH:mm:ss").format(new Date()) + "  " + msg;
        if (out != null) out.println(line);
        System.out.println("[Modloader] " + msg);
    }

    static void error(String msg, Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        info("ERREUR " + msg + "\n" + sw);
    }
}
