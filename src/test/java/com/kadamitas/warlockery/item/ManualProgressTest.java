package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class ManualProgressTest {
    private static final Path MAIN_JAVA = Path.of("src", "main", "java");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void observationsBeginsWithOnlyTheInitiationChapter() {
        final ManualProfile observations = profile("vampirebook");
        final ItemStack book = bookStack();

        assertEquals(1, ManualProgress.unlockedSectionCount(observations, book));
        assertEquals(List.of("initiation"), ManualProgress.visibleSections(observations, book));
        assertEquals(List.of("initiation"), ManualView.from(observations, book).sections());
    }

    @Test
    void eachTornPageRevealsExactlyOneNextChapter() {
        final ManualProfile observations = profile("vampirebook");
        final ItemStack book = bookStack();

        final ManualProgress.RevealResult first = ManualProgress.revealNext(observations, book);
        assertEquals(ManualProgress.RevealStatus.REVEALED, first.status());
        assertEquals("blood", first.section().orElseThrow());
        assertEquals(List.of("initiation", "blood"), ManualProgress.visibleSections(observations, book));

        final ManualProgress.RevealResult second = ManualProgress.revealNext(observations, book);
        assertEquals(ManualProgress.RevealStatus.REVEALED, second.status());
        assertEquals("weaknesses", second.section().orElseThrow());
        assertEquals(observations.sections(), ManualProgress.visibleSections(observations, book));

        final ManualProgress.RevealResult complete = ManualProgress.revealNext(observations, book);
        assertEquals(ManualProgress.RevealStatus.COMPLETE, complete.status());
        assertTrue(complete.section().isEmpty());
        assertEquals(observations.sections(), ManualProgress.visibleSections(observations, book));
    }

    @Test
    void ordinaryManualsExposeEveryChapterAndRejectTornPageProgression() {
        final ManualProfile codex = profile("cauldronbook");
        final ItemStack book = bookStack();

        assertEquals(codex.sections(), ManualProgress.visibleSections(codex, book));
        assertEquals(ManualProgress.RevealStatus.UNSUPPORTED, ManualProgress.revealNext(codex, book).status());
        assertEquals(codex.sections(), ManualProgress.visibleSections(codex, book));
    }

    @Test
    void tornPageUseIsTheOnlyProductionCallSiteThatCanRevealAChapter() throws Exception {
        final var revealMethod = ManualProgress.class.getDeclaredMethod(
            "revealNext",
            ManualProfile.class,
            ItemStack.class
        );
        assertFalse(Modifier.isPublic(revealMethod.getModifiers()));

        try (var files = Files.walk(MAIN_JAVA)) {
            final List<Path> callSites = files
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> read(path).contains("ManualProgress.revealNext("))
                .toList();
            assertEquals(List.of(MAIN_JAVA.resolve(
                "com/kadamitas/warlockery/item/ManualItem.java"
            )), callSites);
        }

        final String manualItem = read(MAIN_JAVA.resolve("com/kadamitas/warlockery/item/ManualItem.java"));
        assertTrue(manualItem.contains("ManualProgress.isTornPage(profile)"));
        assertTrue(manualItem.contains("page.shrink(1)"));
    }

    private static ManualProfile profile(final String id) {
        return ManualProfile.find(id).orElseThrow();
    }

    private static ItemStack bookStack() {
        return new ItemStack(Holder.direct(Items.BOOK));
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }
}
