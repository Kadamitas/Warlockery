package com.kadamitas.warlockery.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class DollShelfAndAltarAttachmentTest {
    private static final Path ITEM_TAGS = Path.of(
        "src", "main", "resources", "data", "warlockery", "tags", "item"
    );

    @Test
    void shelfAcceptsSupportedContentsUntilFull() {
        assertTrue(DollShelfRules.accepts(true, 0));
        assertTrue(DollShelfRules.accepts(true, DollShelfRules.CAPACITY - 1));
        assertFalse(DollShelfRules.accepts(true, DollShelfRules.CAPACITY));
        assertFalse(DollShelfRules.accepts(false, 0));
    }

    @Test
    void shelfContentTagDelegatesToDollsAndSympatheticContainers() throws IOException {
        final String tag = Files.readString(ITEM_TAGS.resolve("doll_shelf_contents.json"));
        assertTrue(tag.contains("#warlockery:dolls"));
        assertTrue(tag.contains("#warlockery:sympathetic_containers"));
    }

    @Test
    void altarAttachmentDecisionReportsEveryFailureAndSuccess() {
        assertEquals("unsupported", AltarAttachmentRules.evaluate(false, false, 0).diagnostic());
        assertEquals("duplicate", AltarAttachmentRules.evaluate(true, true, 0).diagnostic());
        assertEquals(
            "full",
            AltarAttachmentRules.evaluate(true, false, AltarAttachmentRules.CAPACITY).diagnostic()
        );
        assertTrue(AltarAttachmentRules.evaluate(true, false, AltarAttachmentRules.CAPACITY - 1).accepted());
    }

    @Test
    void altarRemovesTheMostRecentlyPlacedAttachment() {
        assertEquals(3, AltarAttachmentRules.lastOccupiedSlot(List.of(true, false, true, true)));
        assertEquals(0, AltarAttachmentRules.lastOccupiedSlot(List.of(true, false, false, false)));
        assertEquals(-1, AltarAttachmentRules.lastOccupiedSlot(List.of(false, false, false, false)));
    }

    @Test
    void altarAttachmentTagIncludesEveryBuiltInAttachmentFamily() throws IOException {
        final String tag = Files.readString(ITEM_TAGS.resolve("altar_attachments.json"));
        assertTrue(tag.contains("#warlockery:altar_range_foci"));
        assertTrue(tag.contains("#warlockery:altar_upgrades/candelabra"));
        assertTrue(tag.contains("#warlockery:altar_upgrades/chalice"));
        assertTrue(tag.contains("#warlockery:altar_upgrades/pentacle"));
    }
}
