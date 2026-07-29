package com.kadamitas.warlockery.audit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class DocumentationStructureTest {
    private static final Path ROOT = Path.of("");

    @Test
    void readmeIsAConciseProjectLandingPage() throws IOException {
        final String readme = Files.readString(ROOT.resolve("README.md"));
        assertTrue(readme.contains("## Installation"));
        assertTrue(readme.contains("## Repository structure"));
        assertTrue(readme.contains("FEATURES.md"));
        assertTrue(readme.contains("DEV.md"));
        assertFalse(readme.contains("## Ritual catalog"));
        assertFalse(readme.contains("runGameTestServer"));
    }

    @Test
    void developerAndFeatureDetailsHaveDedicatedDocuments() throws IOException {
        final String developer = Files.readString(ROOT.resolve("DEV.md"));
        final String features = Files.readString(ROOT.resolve("FEATURES.md"));
        assertTrue(developer.contains("## Build from source"));
        assertTrue(developer.contains("## Tests"));
        assertTrue(developer.contains("## Adding game content"));
        assertTrue(developer.contains("## Networking and client code"));
        assertTrue(developer.contains("## Compatibility"));
        assertTrue(developer.contains("## Troubleshooting"));
        assertFalse(developer.contains("Wiki verification"));
        assertFalse(developer.contains("Verification summary"));
        assertTrue(features.contains("## Ritual catalog"));
        assertTrue(features.contains("## Item descriptions"));
        assertTrue(features.contains("## Other ritual-like interactions"));
    }

    @Test
    void temporaryAuditAndMachineWritingPunctuationAreAbsent() throws IOException {
        assertFalse(Files.exists(ROOT.resolve("docs/CODE_REUSE_AUDIT.md")));
        assertFalse(Files.exists(ROOT.resolve("docs/WIKI_PARITY_STATUS.md")));
        assertFalse(Files.exists(ROOT.resolve("docs/WIKI_AUDIT_WITCHCRAFT_BREW.md")));
        assertFalse(Files.exists(ROOT.resolve("docs/WIKI_AUDIT_RESOURCE_UTILITY_MOB.md")));
        for (final String file : new String[] {"README.md", "DEV.md", "FEATURES.md"}) {
            final String text = Files.readString(ROOT.resolve(file));
            assertFalse(text.contains("—"), file);
            assertFalse(text.contains("–"), file);
        }
    }
}
