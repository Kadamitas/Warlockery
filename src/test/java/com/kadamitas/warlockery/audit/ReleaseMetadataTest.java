package com.kadamitas.warlockery.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ReleaseMetadataTest {
    private static final Path ROOT = Path.of("");

    @Test
    void stableVersionMatchesUpdateFeedAndChangelog() throws IOException {
        final String version = property("mod_version");
        assertEquals("1.2.0", version);

        final JsonObject update = JsonParser.parseString(read("update.json")).getAsJsonObject();
        final JsonObject promotions = update.getAsJsonObject("promos");
        assertEquals(version, promotions.get("26.2-latest").getAsString());
        assertEquals(version, promotions.get("26.2-recommended").getAsString());
        assertTrue(update.getAsJsonObject("26.2").has(version));

        final String changelog = read("changelog.txt");
        assertTrue(changelog.startsWith("Warlockery " + version));
        assertFalse(changelog.contains("alpha"));
    }

    @Test
    void modMetadataPublishesLicenseSupportAndRepositoryLinks() throws IOException {
        final JsonObject metadata = JsonParser.parseString(read("src/main/resources/fabric.mod.json")).getAsJsonObject();
        assertEquals(1, metadata.get("schemaVersion").getAsInt());
        assertEquals("warlockery", metadata.get("id").getAsString());
        assertEquals("${version}", metadata.get("version").getAsString());
        assertEquals("MIT", metadata.get("license").getAsString());
        assertEquals("assets/warlockery/icon.png", metadata.get("icon").getAsString());
        assertEquals("https://github.com/Kadamitas/Warlockery", metadata
            .getAsJsonObject("contact").get("homepage").getAsString());
        assertEquals("https://github.com/Kadamitas/Warlockery/issues", metadata
            .getAsJsonObject("contact").get("issues").getAsString());
        assertEquals("com.kadamitas.warlockery.Warlockery", metadata
            .getAsJsonObject("entrypoints").getAsJsonArray("main").get(0).getAsString());
        assertEquals("com.kadamitas.warlockery.client.WarlockeryClient", metadata
            .getAsJsonObject("entrypoints").getAsJsonArray("client").get(0).getAsString());
        final JsonObject dependencies = metadata.getAsJsonObject("depends");
        assertEquals(">=" + property("loader_version"), dependencies.get("fabricloader").getAsString());
        assertEquals(">=" + property("fabric_api_version"), dependencies.get("fabric-api").getAsString());
        assertEquals("~" + property("minecraft_version"), dependencies.get("minecraft").getAsString());
        assertEquals(">=25", dependencies.get("java").getAsString());
        assertEquals("*", metadata.getAsJsonObject("suggests").get("jei").getAsString());
        assertFalse(Files.exists(ROOT.resolve("src/main/resources/META-INF/mods.toml")));
        assertTrue(read("LICENSE").startsWith("MIT License"));
    }

    @Test
    void releaseArchiveCarriesReproducibleMetadataAndLegalFiles() throws IOException {
        final String build = read("build.gradle");
        assertTrue(build.contains("preserveFileTimestamps = false"));
        assertTrue(build.contains("reproducibleFileOrder = true"));
        assertTrue(build.contains("'Implementation-Version': project.version"));
        assertTrue(build.contains("tasks.named('processResources', ProcessResources)"));
        assertTrue(build.contains("filesMatching('fabric.mod.json')"));
        assertTrue(build.contains("id 'net.fabricmc.fabric-loom' version \"${loom_version}\""));
        assertTrue(build.contains("net.fabricmc:fabric-loader:${project.loader_version}"));
        assertTrue(build.contains("net.fabricmc.fabric-api:fabric-api:${project.fabric_api_version}"));
        assertTrue(build.contains("mezz.jei:jei-26.2-fabric-api"));
        assertTrue(build.contains("archivesName = project.archives_base_name"));
        assertTrue(build.contains("LICENSE-Warlockery.txt"));
        assertTrue(build.contains("CHANGELOG-Warlockery.txt"));
        assertTrue(build.contains("tasks.withType(AbstractArchiveTask).configureEach"));
        assertTrue(build.contains("abstract class ReleaseBundleTask extends DefaultTask"));
        assertTrue(build.contains("tasks.register('releaseBundle', ReleaseBundleTask)"));
        assertTrue(build.contains("MessageDigest.getInstance('SHA-256')"));
        assertTrue(build.contains("release/${project.version}"));

        final String gradleProperties = read("gradle.properties");
        assertTrue(gradleProperties.contains("org.gradle.configuration-cache=false"));
        assertEquals("warlockery-fabric", property("archives_base_name"));
        assertEquals("1.17.17", property("loom_version"));
        assertEquals("0.19.3", property("loader_version"));
        assertEquals("0.155.2+26.2", property("fabric_api_version"));

        final String wrapper = read("gradle/wrapper/gradle-wrapper.properties");
        assertTrue(wrapper.contains("gradle-9.5.1-bin.zip"));
        assertTrue(wrapper.contains("distributionSha256Sum=bafc141b619ad6350fd975fc903156dd5c151998cc8b058e8c1044ab5f7b031f"));
    }

    private static String property(final String name) throws IOException {
        return read("gradle.properties").lines()
            .map(String::trim)
            .filter(line -> !line.isEmpty() && !line.startsWith("#"))
            .map(line -> line.split("=", 2))
            .filter(parts -> parts.length == 2 && parts[0].equals(name))
            .map(parts -> parts[1])
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Missing Gradle property " + name));
    }

    private static String read(final String relativePath) throws IOException {
        return Files.readString(ROOT.resolve(relativePath));
    }
}
