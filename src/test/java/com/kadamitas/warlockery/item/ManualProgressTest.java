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
    void observationsBeginsWithNamiTheBloodAudienceAndTheFirstVampireLevel() {
        final ManualProfile observations = profile("vampirebook");
        final ItemStack book = bookStack();

        assertEquals(3, ManualProgress.unlockedSectionCount(observations, book));
        assertEquals(9, ManualProgress.requiredTornPages(observations, book));
        assertEquals(List.of("nami", "blood_audience", "vampire_level_1"),
            ManualProgress.visibleSections(observations, book));
        assertEquals(List.of("nami", "blood_audience", "vampire_level_1"),
            ManualView.from(observations, book).sections());
    }

    @Test
    void eachTornPageRevealsExactlyOneNextChapter() {
        final ManualProfile observations = profile("vampirebook");
        final ItemStack book = bookStack();
        final ItemStack pages = new ItemStack(Holder.direct(Items.PAPER), 10);

        for (int level = 2; level <= 10; level++) {
            final ManualProgress.RevealResult reveal = insertPage(observations, book, pages, false);
            assertEquals(ManualProgress.RevealStatus.REVEALED, reveal.status());
            assertEquals("vampire_level_" + level, reveal.section().orElseThrow());
            assertEquals(level + 2, ManualProgress.unlockedSectionCount(observations, book));
            assertEquals(10 - level, ManualProgress.requiredTornPages(observations, book));
            assertEquals(11 - level, pages.getCount());
        }

        assertEquals(observations.sections(), ManualProgress.visibleSections(observations, book));
        assertEquals(0, ManualProgress.requiredTornPages(observations, book));
        assertEquals(1, pages.getCount());

        final ManualProgress.RevealResult complete = insertPage(observations, book, pages, false);
        assertEquals(ManualProgress.RevealStatus.COMPLETE, complete.status());
        assertTrue(complete.section().isEmpty());
        assertEquals(observations.sections(), ManualProgress.visibleSections(observations, book));
        assertEquals(1, pages.getCount());
    }

    @Test
    void onlyTornPagesCanAdvanceObservationsAndCreativeInsertionPreservesThem() {
        final ManualProfile observations = profile("vampirebook");
        final ItemStack book = bookStack();
        final ItemStack fakePage = new ItemStack(Holder.direct(Items.PAPER));
        final ManualProgress.RevealResult rejected = ManualProgress.insertTornPage(
            observations,
            book,
            profile("ingredient_book_biomes"),
            fakePage,
            false
        );

        assertEquals(ManualProgress.RevealStatus.UNSUPPORTED, rejected.status());
        assertEquals(List.of("nami", "blood_audience", "vampire_level_1"),
            ManualProgress.visibleSections(observations, book));
        assertEquals(1, fakePage.getCount());

        final ItemStack creativePage = new ItemStack(Holder.direct(Items.PAPER));
        assertEquals(ManualProgress.RevealStatus.REVEALED,
            insertPage(observations, book, creativePage, true).status());
        assertEquals(1, creativePage.getCount());
    }

    @Test
    void ordinaryManualsExposeEveryChapterAndRejectTornPageProgression() {
        final ManualProfile codex = profile("cauldronbook");
        final ItemStack book = bookStack();

        assertEquals(codex.sections(), ManualProgress.visibleSections(codex, book));
        assertEquals(0, ManualProgress.requiredTornPages(codex, book));
        assertEquals(ManualProgress.RevealStatus.UNSUPPORTED,
            insertPage(codex, book, new ItemStack(Holder.direct(Items.PAPER)), false).status());
        assertEquals(codex.sections(), ManualProgress.visibleSections(codex, book));
    }

    @Test
    void tornPageUseIsTheOnlyProductionCallSiteThatCanRevealAChapter() throws Exception {
        final var insertionMethod = ManualProgress.class.getDeclaredMethod(
            "insertTornPage",
            ManualProfile.class,
            ItemStack.class,
            ManualProfile.class,
            ItemStack.class,
            boolean.class
        );
        assertFalse(Modifier.isPublic(insertionMethod.getModifiers()));

        try (var files = Files.walk(MAIN_JAVA)) {
            final List<Path> callSites = files
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> read(path).contains("ManualProgress.insertTornPage("))
                .toList();
            assertEquals(List.of(MAIN_JAVA.resolve(
                "com/kadamitas/warlockery/item/ManualItem.java"
            )), callSites);
        }

        final String manualItem = read(MAIN_JAVA.resolve("com/kadamitas/warlockery/item/ManualItem.java"));
        assertTrue(manualItem.contains("ManualProgress.isTornPage(profile)"));
        assertTrue(manualItem.contains("ManualProgress.insertTornPage("));
    }

    private static ManualProfile profile(final String id) {
        return ManualProfile.find(id).orElseThrow();
    }

    private static ItemStack bookStack() {
        return new ItemStack(Holder.direct(Items.BOOK));
    }

    private static ManualProgress.RevealResult insertPage(
        final ManualProfile observations,
        final ItemStack book,
        final ItemStack page,
        final boolean infiniteMaterials
    ) {
        return ManualProgress.insertTornPage(
            observations,
            book,
            profile("ingredient_vbook_page"),
            page,
            infiniteMaterials
        );
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }
}
