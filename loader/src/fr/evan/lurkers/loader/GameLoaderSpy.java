package fr.evan.lurkers.loader;

import java.io.File;
import java.lang.instrument.ClassFileTransformer;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.ProtectionDomain;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Ne modifie rien : repère le ClassLoader qui charge libGDX (celui du jeu), puis démarre
 * le cœur du modloader avec ce ClassLoader comme parent.
 */
final class GameLoaderSpy implements ClassFileTransformer {
    private final File coreJar;
    private final File base;
    private final AtomicBoolean started = new AtomicBoolean();

    GameLoaderSpy(File coreJar, File base) {
        this.coreJar = coreJar;
        this.base = base;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> redefined,
                            ProtectionDomain pd, byte[] bytes) {
        if (loader == null || className == null || !className.startsWith("com/badlogic/gdx/")) return null;
        if (!started.compareAndSet(false, true)) return null;
        Thread t = new Thread(() -> startCore(loader), "Modloader-Core");
        t.setDaemon(true);
        t.start();
        return null;
    }

    private void startCore(ClassLoader gameLoader) {
        try {
            Log.info("ClassLoader du jeu trouvé : " + gameLoader.getClass().getName());
            URLClassLoader coreLoader = new URLClassLoader(new URL[]{coreJar.toURI().toURL()}, gameLoader);
            Class<?> core = Class.forName("fr.evan.lurkers.core.Core", true, coreLoader);
            Method start = core.getMethod("start", File.class, Consumer.class);
            Consumer<String> log = Log::info;
            start.invoke(null, base, log);
        } catch (Throwable t) {
            Log.error("démarrage du cœur du modloader (le jeu continue sans mods)", t);
        }
    }
}
