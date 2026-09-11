package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

final class RitualKnifeNamingTest {
    private static final Pattern OLD_NAME = Pattern.compile(
        "arthana|arthany|アルタナ|아르타나|артан[аы]|阿尔萨[纳娜]|阿爾薩[納娜]", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    @Test
    void everyLocaleUsesTheRitualKnifeNameWithoutTheOldAlias() throws Exception {
        final List<String> aliases = new ArrayList<>();
        try (var files = Files.list(Path.of("src/main/resources/assets/warlockery/lang"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                final var language = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                language.entrySet().forEach(entry -> {
                    if (OLD_NAME.matcher(entry.getValue().getAsString()).find()) aliases.add(file.getFileName() + ":" + entry.getKey());
                });
                assertEquals(language.get("item.warlockery.ritual_knife").getAsString(),
                    language.get("manual.warlockery.circles.arthana.title").getAsString(), file.toString());
            }
        }
        assertTrue(aliases.isEmpty(), "Old knife names remain visible: " + aliases);
    }

    @Test
    void publicProseOmitsTheAliasWhileTechnicalCompatibilityIdsRemainValid() throws Exception {
        final List<Path> documents = new ArrayList<>();
        try (var files = Files.walk(Path.of("docs"))) {
            documents.addAll(files.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".md")).toList());
        }
        if (Files.isRegularFile(Path.of("README.md"))) documents.add(Path.of("README.md"));
        for (Path file : documents) {
            final String prose = Files.readString(file).replaceAll("(?s)```.*?```", "").replaceAll("`[^`]*`", "");
            assertFalse(OLD_NAME.matcher(prose).find(), file.toString());
        }
    }

    @Test
    void knifeGuideSeparatesTheOptionalAttachmentFromRitualActivation() throws Exception {
        final var language = JsonParser.parseString(Files.readString(Path.of(
            "src/main/resources/assets/warlockery/lang/en_us.json"))).getAsJsonObject();
        final String body = language.get("manual.warlockery.circles.arthana").getAsString();
        assertFalse(OLD_NAME.matcher(body).find());
        assertTrue(body.contains("optional"));
        assertTrue(body.contains("Arcane Focus"));
        assertTrue(body.contains("golden ritual heart"));
    }
}
