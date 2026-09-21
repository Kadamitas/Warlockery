package com.kadamitas.warlockery;

import net.fabricmc.api.EnvType;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.quiltmc.loader.impl.launch.knot.Knot;

/** Runs unit fixtures through the same Quilt transformed classes as the development game. */
public final class QuiltLauncherSessionListener implements LauncherSessionListener {
    private final ClassLoader quiltClasses;
    private ClassLoader previousClasses;

    public QuiltLauncherSessionListener() {
        System.setProperty("loader.development", "true");
        System.setProperty("loader.unitTest", "true");
        final Thread thread = Thread.currentThread();
        final ClassLoader original = thread.getContextClassLoader();
        try {
            final String gameDirectory = System.getProperty("warlockery.quiltUnitGameDir");
            if (gameDirectory == null || gameDirectory.isBlank()) {
                throw new IllegalStateException("Missing isolated Quilt unit-test game directory");
            }
            quiltClasses = new Knot(EnvType.CLIENT).init(new String[] {"--gameDir", gameDirectory});
        } finally {
            thread.setContextClassLoader(original);
        }
    }

    @Override
    public void launcherSessionOpened(final LauncherSession session) {
        previousClasses = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(quiltClasses);
    }

    @Override
    public void launcherSessionClosed(final LauncherSession session) {
        Thread.currentThread().setContextClassLoader(previousClasses);
    }
}
