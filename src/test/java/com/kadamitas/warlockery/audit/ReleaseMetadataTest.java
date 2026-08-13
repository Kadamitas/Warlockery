package com.kadamitas.warlockery.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

final class ReleaseMetadataTest {
    private static final Path ROOT = Path.of("");
    private static final Pattern GRADLE_VERSION = Pattern.compile("(?m)^version = '([^']+)'$");

    @Test
    void stableVersionMatchesUpdateFeedAndChangelog() throws IOException {
        final String build = read("build.gradle");
        final var matcher = GRADLE_VERSION.matcher(build);
        assertTrue(matcher.find());
        final String version = matcher.group(1);
        assertEquals("13.1.0-LlaGuiT0-26.2.0.45", version);

        final JsonObject update = JsonParser.parseString(read("update.json")).getAsJsonObject();
        final JsonObject promotions = update.getAsJsonObject("promos");
        assertEquals("1.3.0", promotions.get("26.2-latest").getAsString());
        assertEquals("1.3.0", promotions.get("26.2-recommended").getAsString());
        assertTrue(update.getAsJsonObject("26.2").has("1.3.0"));

        final String changelog = read("changelog.txt");
        assertTrue(changelog.startsWith("Warlockery " + version));
        assertFalse(changelog.contains("alpha"));
        assertTrue(changelog.contains("NeoForge 26.2.0.45-beta"));
    }

    @Test
    void modMetadataPublishesLicenseSupportAndRepositoryLinks() throws IOException {
        final String metadata = read("src/main/resources/META-INF/neoforge.mods.toml");
        assertTrue(metadata.contains("license=\"MIT\""));
        assertTrue(metadata.contains("version=\"${mod_version}\""));
        assertTrue(metadata.contains("issueTrackerURL=\"https://github.com/Kadamitas/Warlockery/issues\""));
        assertTrue(metadata.contains("displayURL=\"https://github.com/Kadamitas/Warlockery\""));
        assertTrue(metadata.contains("updateJSONURL=\"https://raw.githubusercontent.com/Kadamitas/Warlockery/neoforge-main/update.json\""));
        assertTrue(metadata.contains("logoFile=\"warlockery-icon.png\""));
        assertTrue(metadata.contains("logoBlur=false"));
        assertTrue(metadata.contains("[features.warlockery]"));
        assertTrue(metadata.contains("javaVersion=\"[25,)\""));
        assertTrue(metadata.contains("modId=\"neoforge\""));
        assertTrue(metadata.contains("versionRange=\"[26.2.0.45-beta,26.2.0.46-beta)\""));
        assertTrue(metadata.contains("modId=\"jei\""));
        assertTrue(Pattern.compile("(?s)modId=\"jei\".*?type=\"optional\".*?side=\"CLIENT\"")
            .matcher(metadata)
            .find());
        assertTrue(read("LICENSE").startsWith("MIT License"));
    }

    @Test
    void releaseArchiveCarriesReproducibleMetadataAndLegalFiles() throws IOException {
        final String build = read("build.gradle");
        assertTrue(build.contains("preserveFileTimestamps = false"));
        assertTrue(build.contains("reproducibleFileOrder = true"));
        assertTrue(build.contains("'Implementation-Version': project.version"));
        assertTrue(build.contains("tasks.named('processResources', ProcessResources)"));
        assertTrue(build.contains("exclude 'assets/warlockery/icon.png'"));
        assertTrue(build.contains("rename { 'warlockery-icon.png' }"));
        assertTrue(build.contains("LICENSE-Warlockery.txt"));
        assertTrue(build.contains("CHANGELOG-Warlockery.txt"));
        assertTrue(build.contains("tasks.withType(Jar).configureEach"));
        assertTrue(build.contains("abstract class ReleaseBundleTask extends DefaultTask"));
        assertTrue(build.contains("tasks.register('releaseBundle', ReleaseBundleTask)"));
        assertTrue(build.contains("MessageDigest.getInstance('SHA-256')"));
        assertTrue(build.contains("release/neoforge/${project.version}"));
        assertTrue(build.contains("expand mod_version: project.version"));

        final String gradleProperties = read("gradle.properties");
        assertTrue(gradleProperties.contains("org.gradle.configuration-cache=false"));

        final String wrapper = read("gradle/wrapper/gradle-wrapper.properties");
        assertTrue(wrapper.contains("distributionSha256Sum=553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746"));
    }

    private static String read(final String relativePath) throws IOException {
        return Files.readString(ROOT.resolve(relativePath));
    }
}
