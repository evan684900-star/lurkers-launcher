package fr.evan.lurkers.core;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.function.Consumer;

/** Journal du cœur : renvoie les lignes au journal du modloader (classe du chargeur système). */
final class CoreLog {
    static volatile Consumer<String> sink = System.out::println;

    static void info(String msg) {
        try { sink.accept(msg); } catch (Throwable ignored) {}
    }

    static void error(String msg, Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        info("ERREUR " + msg + "\n" + sw);
    }
}
