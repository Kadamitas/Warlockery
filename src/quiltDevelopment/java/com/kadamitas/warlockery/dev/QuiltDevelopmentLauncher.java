package com.kadamitas.warlockery.dev;

/** Adapts Loom development settings before Quilt initializes, including IDE runs. */
public final class QuiltDevelopmentLauncher {
    private QuiltDevelopmentLauncher() {}

    public static void main(String[] args) throws ReflectiveOperationException {
        for (String name : System.getProperties().stringPropertyNames()) {
            if (name.startsWith("fabric.") && !name.startsWith("fabric.dli.")) {
                System.setProperty("loader." + name.substring("fabric.".length()), System.getProperty(name));
            }
        }
        String environment = System.getProperty("warlockery.quiltEnvironment");
        if (!"client".equals(environment) && !"server".equals(environment)) {
            throw new IllegalStateException("Missing or invalid Loom launch environment: " + environment);
        }
        String entrypoint = "org.quiltmc.loader.impl.launch.knot.Knot"
            + (environment.equals("client") ? "Client" : "Server");
        Class.forName(entrypoint).getMethod("main", String[].class).invoke(null, (Object) args);
    }
}

